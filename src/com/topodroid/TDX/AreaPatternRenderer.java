/* @file AreaPatternRenderer.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch world-aligned area patterns with optional boundary fade
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.prefs.TDSetting;

import java.util.ArrayList;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Region;

/** Stateless renderer for line-patterned areas (parallel stripes / crosshatch / broken dashes / bedrock courses).
 *
 * All areas with one patterned symbol and matching brush style render as a single
 * union region per scrap: the
 * ink anchors to absolute scene coordinates (stripes at multiples of the period along
 * the pattern normal from the scene origin; dashes and bedrock on decorated repeating
 * grids) so overlapping or appended areas always tile seamlessly, overlaps never
 * double-blend, and the optional fade erases ink alpha toward the union boundary
 * (interior holes included). Patterned areas draw no per-area border.
 *
 * WORLD-SPACE INK: everything draws in scene coordinates under canvas.concat(matrix);
 * metrics are ink units * TDSetting.inkUnit(), scaled by the group's brush weight
 * relative to the standard weight. Callers invoke this concurrently from the render,
 * scene-cache and export threads: there is no instance or static mutable state - all
 * scratch is call-stack-confined, and the dash stamp is a fixed table indexed by the
 * world cell so every thread/frame/build paints identical pixels.
 */
class AreaPatternRenderer
{
  private static final float EPS = 1.0e-4f;
  // fade ramp steps: depth band j of FADE_PASSES keeps j/FADE_PASSES of the stripe alpha
  private static final int FADE_PASSES = 12;
  private static final PorterDuffXfermode FADE_ERASE_XFERMODE = new PorterDuffXfermode( PorterDuff.Mode.DST_OUT );

  // DASH STAMP: a fixed 4-row x 4-slot tile decorating the dash grid, authored after
  // the hand-drawn NSS mud fill. Rows stay perfectly horizontal; each stamp row shifts
  // its slots by a fixed phase so no vertical columns line up, and each slot carries a
  // small offset and length variation. Semi-random but repeating: the eye reads one
  // consistent symbol and the density stays guaranteed (unlike free jitter).
  private static final float[] STAMP_SHIFT = { 0.00f, 0.50f, 0.18f, 0.68f }; // per row [period]
  private static final float[][] STAMP_DU = { // per (row, slot) center offset [period]
    {  0.04f, -0.06f,  0.02f, -0.03f },
    { -0.05f,  0.03f, -0.02f,  0.06f },
    {  0.01f,  0.05f, -0.06f,  0.02f },
    { -0.03f, -0.01f,  0.04f, -0.05f },
  };
  private static final float[][] STAMP_LEN = { // per (row, slot) length factor [dash]
    { 1.05f, 0.80f, 1.20f, 0.90f },
    { 0.85f, 1.10f, 0.95f, 1.15f },
    { 1.15f, 0.90f, 1.05f, 0.80f },
    { 0.95f, 1.20f, 0.85f, 1.10f },
  };
  private static final int STAMP_ROWS = STAMP_SHIFT.length;
  private static final int STAMP_SLOTS = STAMP_DU[0].length;

  // BEDROCK STAMP: six irregular-height courses totaling exactly six nominal row
  // spacings. Each course has a different joint phase, small per-slot displacement,
  // and a subtle joint skew. The fixed world-indexed table gives the hand-drawn,
  // irregular block rhythm of the reference while keeping separate areas seamless.
  private static final float[] BEDROCK_ROW_V = {
    0.00f, 0.74f, 1.97f, 2.88f, 4.23f, 5.08f, 6.00f
  }; // cumulative course boundaries [spacing]
  private static final float[] BEDROCK_JOINT_SHIFT = {
    0.04f, 0.55f, 0.18f, 0.72f, 0.34f, 0.86f
  }; // per-course phase [period]
  private static final float[][] BEDROCK_JOINT_DU = {
    {  0.10f, -0.14f,  0.12f, -0.05f },
    { -0.12f,  0.13f, -0.08f,  0.09f },
    {  0.14f, -0.04f, -0.13f,  0.07f },
    { -0.09f,  0.11f,  0.05f, -0.14f },
    {  0.12f, -0.11f,  0.09f, -0.03f },
    { -0.13f,  0.06f, -0.09f,  0.13f },
  }; // joint displacement [period]
  private static final float[][] BEDROCK_JOINT_SKEW = {
    { -0.03f,  0.04f, -0.02f,  0.01f },
    {  0.02f, -0.04f,  0.03f, -0.01f },
    {  0.04f, -0.02f,  0.01f, -0.03f },
    { -0.01f,  0.03f, -0.04f,  0.02f },
    { -0.04f,  0.01f,  0.02f, -0.03f },
    {  0.03f, -0.01f, -0.02f,  0.04f },
  }; // bottom-minus-top joint offset [period]
  private static final int BEDROCK_ROWS = BEDROCK_JOINT_SHIFT.length;
  private static final int BEDROCK_SLOTS = BEDROCK_JOINT_DU[0].length;

  private AreaPatternRenderer() { } // static-only

  /** @return the pattern scale for a brush style: the raw weight ratio (weight/standard)
   * spreads too far for area fills (Thin 0.5x .. Thick 2.5x), so compress the ladder to
   * Thin 0.7x, Standard exactly 1x (the symbol file authors the standard look), Thick 2x;
   * piecewise-linear and monotonic in between and beyond
   */
  static float patternWeightScale( SketchBrushStyle style )
  {
    float r = SketchBrushRenderer.effectScale( style ); // weight / standard, 1 when unset
    if ( r == 1.0f )  return 1.0f;                      // unstyled/standard: bit-exact base metrics
    if ( r <= 0.5f )  return r * 1.4f;                  // thin (0.5) -> 0.7, below shrinks proportionally
    if ( r <= 1.0f )  return 0.7f + ( r - 0.5f ) * 0.6f;          // 0.5..1.0 -> 0.7..1.0
    return 1.0f + ( r - 1.0f ) * ( 2.0f / 3.0f );                 // 1.0..2.5 -> 1.0..2.0 (thick), onward linearly
  }

  /** render one patterned-symbol group as a single union region
   * @param canvas    canvas (scene->target transform NOT yet applied)
   * @param matrix    scene->target transform
   * @param bbox      scene-space clipping rectangle (null = no culling)
   * @param pattern      stripe parameters of the symbol
   * @param members      areas of this symbol (and weight class) to merge
   * @param with_xor     whether stripe colors are xor-ed (inverted-colors rendering)
   * @param weight_scale group brush weight relative to the standard weight (1 = standard)
   * @param ink_color    resolved group ARGB after style color/opacity overrides
   */
  static void drawGroup( Canvas canvas, Matrix matrix, RectF bbox, AreaLinePattern pattern,
                         ArrayList< DrawingAreaPath > members, boolean with_xor, float weight_scale,
                         int ink_color )
  {
    drawGroup( canvas, matrix, bbox, pattern, members, null, with_xor, weight_scale, ink_color );
  }

  /** render one patterned-symbol group while hard-clipping ink beneath replacement areas */
  static void drawGroup( Canvas canvas, Matrix matrix, RectF bbox, AreaLinePattern pattern,
                         ArrayList< DrawingAreaPath > members, ArrayList< DrawingAreaPath > exclusions,
                         boolean with_xor, float weight_scale, int ink_color )
  {
    if ( canvas == null || matrix == null || pattern == null || members == null || members.isEmpty() ) return;
    Path union = new Path();
    boolean ok = true;
    for ( DrawingAreaPath member : members ) {
      Path p = new Path( member.mPath );
      p.close();
      if ( union.isEmpty() ) {
        union.set( p );
      } else {
        try {
          ok = union.op( p, Path.Op.UNION );
        } catch ( RuntimeException e ) {
          ok = false;
        }
        if ( ! ok ) break;
      }
    }
    if ( ok ) {
      drawRegion( canvas, matrix, bbox, pattern, union, exclusions, with_xor, weight_scale, ink_color );
    } else {
      // degraded but deterministic: per-member regions keep the stripes aligned (overlaps
      // re-draw identical pixels); only the fade doubles up along interior shared edges
      TDLog.e( "area pattern union failed - drawing members separately" );
      for ( DrawingAreaPath member : members ) {
        Path p = new Path( member.mPath );
        p.close();
        drawRegion( canvas, matrix, bbox, pattern, p, exclusions, with_xor, weight_scale, ink_color );
      }
    }
  }

  /** ink-fill one merged region: pattern clipped to the region, then the boundary fade
   */
  private static void drawRegion( Canvas canvas, Matrix matrix, RectF bbox, AreaLinePattern pattern,
                                  Path region, ArrayList< DrawingAreaPath > exclusions,
                                  boolean with_xor, float weight_scale, int ink_color )
  {
    // one scene unit per ink unit, scaled by the group's brush weight: Thin/Standard/
    // Thick coarsen or tighten the whole pattern together with the line work
    float ink     = TDSetting.inkUnit() * positive( weight_scale, 1f );
    float stroke  = positive( pattern.mWidthScale   * ink, AreaLinePattern.DEFAULT_WIDTH   * ink );
    float spacing = positive( pattern.mSpacingScale * ink, AreaLinePattern.DEFAULT_SPACING * ink );
    float fade    = ( pattern.mFadeScale > 0 )? pattern.mFadeScale * ink : 0f;

    // margin = how far a mark can reach beyond the clip rect: stripes span it via their
    // half-diagonal, dashes via the stamp shift/offset plus half the longest segment
    float margin;
    if ( pattern.mType == AreaLinePattern.TYPE_DASHES || pattern.mType == AreaLinePattern.TYPE_BEDROCK ) {
      float dash   = positive( pattern.mDashScale   * ink, AreaLinePattern.DEFAULT_DASH   * ink );
      float period = positive( pattern.mPeriodScale * ink, AreaLinePattern.DEFAULT_PERIOD * ink );
      margin = period + Math.max( ( pattern.mType == AreaLinePattern.TYPE_DASHES )? dash : spacing, stroke );
    } else {
      margin = Math.max( stroke, spacing );
    }

    RectF clip = new RectF();
    region.computeBounds( clip, true );
    if ( bbox != null ) { // cull to the viewport, padded so the fade band survives at its edges
      RectF view = new RectF( bbox );
      view.inset( -(fade + margin), -(fade + margin) );
      if ( ! clip.intersect( view ) ) return;
    }
    if ( clip.width() <= EPS || clip.height() <= EPS ) return;
    clip.inset( -margin, -margin );

    int save = canvas.save();
    try {
      canvas.concat( matrix );
      RectF layer_rect = new RectF( clip );
      layer_rect.inset( -fade, -fade );
      // the group needs its own layer: the ink composites as one unit and the DST_OUT
      // fade strokes must only erase this group's ink, never what is already below
      int layer = canvas.saveLayer( layer_rect, null );
      int ink_clip = canvas.save();
      try {
        if ( exclusions != null ) {
          for ( DrawingAreaPath exclusion : exclusions ) {
            if ( exclusion == null ) continue;
            Path mask = new Path( exclusion.mPath );
            mask.close();
            canvas.clipPath( mask, Region.Op.DIFFERENCE );
          }
        }
        if ( pattern.mType == AreaLinePattern.TYPE_DASHES ) {
          drawDashes( canvas, region, clip, pattern, ink, stroke, with_xor, ink_color );
        } else if ( pattern.mType == AreaLinePattern.TYPE_BEDROCK ) {
          drawBedrock( canvas, region, clip, pattern, ink, stroke, with_xor, ink_color );
        } else if ( pattern.mType == AreaLinePattern.TYPE_CROSSHATCH ) {
          drawCrosshatch( canvas, region, clip, pattern, stroke, spacing, with_xor, ink_color );
        } else {
          drawStripes( canvas, region, clip, pattern, stroke, spacing, with_xor, ink_color );
        }
      } finally {
        canvas.restoreToCount( ink_clip );
      }
      drawBoundaryFade( canvas, region, fade );
      canvas.restoreToCount( layer );
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** draw the world-anchored parallel stripes clipped to the region
   */
  private static void drawStripes( Canvas canvas, Path region, RectF clip, AreaLinePattern pattern,
                                   float stroke, float spacing, boolean with_xor, int ink_color )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke );
    paint.setColor( with_xor ? BrushManager.xorColor( ink_color ) : ink_color );

    float radians = (float)Math.toRadians( pattern.mAngle );
    float dx = (float)Math.cos( radians );
    float dy = (float)Math.sin( radians );
    float nx = -dy;
    float ny = dx;

    // stripe k lies on the absolute scene line { P : P.n = k*spacing }; the stripes
    // crossing the clip rect come from projecting its corners onto the normal
    float p1 = clip.left  * nx + clip.top    * ny;
    float p2 = clip.right * nx + clip.top    * ny;
    float p3 = clip.left  * nx + clip.bottom * ny;
    float p4 = clip.right * nx + clip.bottom * ny;
    float pmin = Math.min( Math.min( p1, p2 ), Math.min( p3, p4 ) );
    float pmax = Math.max( Math.max( p1, p2 ), Math.max( p3, p4 ) );
    int first = (int)Math.floor( pmin / spacing ) - 1;
    int last  = (int)Math.ceil ( pmax / spacing ) + 1;

    // each segment centers on the clip rect's projection ALONG the stripe direction:
    // centering on the origin's perpendicular foot instead (the reverted 2026-07-03
    // attempt) missed regions lying far from the origin along the stripe direction
    float t0   = clip.centerX() * dx + clip.centerY() * dy;
    float half = 0.5f * (float)Math.hypot( clip.width(), clip.height() );

    int save = canvas.save();
    try {
      canvas.clipPath( region );
      for ( int k = first; k <= last; ++k ) {
        float cx = nx * ( k * spacing ) + dx * t0;
        float cy = ny * ( k * spacing ) + dy * t0;
        canvas.drawLine( cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half, paint );
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** draw both mirrored stripe families in one paint operation so their crossings keep
   * the pattern's authored alpha instead of accumulating two translucent strokes
   */
  private static void drawCrosshatch( Canvas canvas, Path region, RectF clip, AreaLinePattern pattern,
                                     float stroke, float spacing, boolean with_xor, int ink_color )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke );
    paint.setColor( with_xor ? BrushManager.xorColor( ink_color ) : ink_color );

    Path ink = new Path();
    appendStripes( ink, clip, pattern.mAngle, spacing );
    appendStripes( ink, clip, -pattern.mAngle, spacing );

    int save = canvas.save();
    try {
      canvas.clipPath( region );
      canvas.drawPath( ink, paint );
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** append world-anchored stripe segments for one angle to a shared path */
  private static void appendStripes( Path ink, RectF clip, float angle, float spacing )
  {
    float radians = (float)Math.toRadians( angle );
    float dx = (float)Math.cos( radians );
    float dy = (float)Math.sin( radians );
    float nx = -dy;
    float ny = dx;

    float p1 = clip.left  * nx + clip.top    * ny;
    float p2 = clip.right * nx + clip.top    * ny;
    float p3 = clip.left  * nx + clip.bottom * ny;
    float p4 = clip.right * nx + clip.bottom * ny;
    float pmin = Math.min( Math.min( p1, p2 ), Math.min( p3, p4 ) );
    float pmax = Math.max( Math.max( p1, p2 ), Math.max( p3, p4 ) );
    int first = (int)Math.floor( pmin / spacing ) - 1;
    int last  = (int)Math.ceil ( pmax / spacing ) + 1;
    float t0   = clip.centerX() * dx + clip.centerY() * dy;
    float half = 0.5f * (float)Math.hypot( clip.width(), clip.height() );

    for ( int k = first; k <= last; ++k ) {
      float cx = nx * ( k * spacing ) + dx * t0;
      float cy = ny * ( k * spacing ) + dy * t0;
      ink.moveTo( cx - dx * half, cy - dy * half );
      ink.lineTo( cx + dx * half, cy + dy * half );
    }
  }

  /** draw broken dashes in horizontal rows on a world-anchored grid clipped to the region.
   *
   * The grid lives in the rotated frame (u along the dash direction, v perpendicular):
   * row i sits exactly at v = (i+.5)*spacing (dashes line up horizontally), and slot j
   * of that row centers near (j+.5+shift+du)*period with shift/du/length taken from the
   * repeating stamp tile indexed by (i mod rows, j mod slots). Because the indices come
   * from absolute world coordinates, overlapping/appended clay areas reproduce identical
   * dashes and the render is order- and frame-independent.
   */
  private static void drawDashes( Canvas canvas, Path region, RectF clip, AreaLinePattern pattern,
                                  float ink, float stroke, boolean with_xor, int ink_color )
  {
    float spacing = positive( pattern.mSpacingScale * ink, AreaLinePattern.DEFAULT_SPACING * ink );
    float period  = positive( pattern.mPeriodScale  * ink, AreaLinePattern.DEFAULT_PERIOD  * ink );
    float dash    = positive( pattern.mDashScale    * ink, AreaLinePattern.DEFAULT_DASH    * ink );

    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke );
    paint.setColor( with_xor ? BrushManager.xorColor( ink_color ) : ink_color );

    float radians = (float)Math.toRadians( pattern.mAngle );
    float dx = (float)Math.cos( radians );
    float dy = (float)Math.sin( radians );
    float nx = -dy;
    float ny = dx;

    // rotated-frame extent of the clip rect: project the four corners onto d (u) and n (v)
    float u1 = clip.left  * dx + clip.top    * dy;
    float u2 = clip.right * dx + clip.top    * dy;
    float u3 = clip.left  * dx + clip.bottom * dy;
    float u4 = clip.right * dx + clip.bottom * dy;
    float v1 = clip.left  * nx + clip.top    * ny;
    float v2 = clip.right * nx + clip.top    * ny;
    float v3 = clip.left  * nx + clip.bottom * ny;
    float v4 = clip.right * nx + clip.bottom * ny;
    float uMin = Math.min( Math.min( u1, u2 ), Math.min( u3, u4 ) );
    float uMax = Math.max( Math.max( u1, u2 ), Math.max( u3, u4 ) );
    float vMin = Math.min( Math.min( v1, v2 ), Math.min( v3, v4 ) );
    float vMax = Math.max( Math.max( v1, v2 ), Math.max( v3, v4 ) );

    int j0 = (int)Math.floor( uMin / period ) - 1;
    int j1 = (int)Math.ceil ( uMax / period ) + 1;
    int i0 = (int)Math.floor( vMin / spacing ) - 1;
    int i1 = (int)Math.ceil ( vMax / spacing ) + 1;

    int save = canvas.save();
    try {
      canvas.clipPath( region );
      for ( int i = i0; i <= i1; ++i ) {
        int r = Math.floorMod( i, STAMP_ROWS );
        float vc = ( i + 0.5f ) * spacing; // whole row on one horizontal line
        for ( int j = j0; j <= j1; ++j ) {
          int c = Math.floorMod( j, STAMP_SLOTS );
          float uc  = ( j + 0.5f + STAMP_SHIFT[r] + STAMP_DU[r][c] ) * period;
          float len = dash * STAMP_LEN[r][c];
          float cx = uc * dx + vc * nx;
          float cy = uc * dy + vc * ny;
          float hx = dx * len * 0.5f;
          float hy = dy * len * 0.5f;
          canvas.drawLine( cx - hx, cy - hy, cx + hx, cy + hy, paint );
        }
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** draw irregular bedrock courses on a world-anchored grid clipped to the region.
   *
   * The rotated frame uses u along the bedding plane and v across it. Horizontal course
   * boundaries repeat every six nominal spacings; joints span one course, with a fixed
   * stagger/offset/skew table indexed by absolute course and block slots. Because every
   * coordinate is derived from world u/v rather than the area's bounds, adjoining areas
   * show one continuous pattern. A zero fade leaves Canvas.clipPath as the hard cutoff,
   * intentionally breaking partial blocks at the surveyed area edge.
   */
  private static void drawBedrock( Canvas canvas, Path region, RectF clip, AreaLinePattern pattern,
                                   float ink, float stroke, boolean with_xor, int ink_color )
  {
    float spacing = positive( pattern.mSpacingScale * ink, AreaLinePattern.DEFAULT_SPACING * ink );
    float period  = positive( pattern.mPeriodScale  * ink, AreaLinePattern.DEFAULT_PERIOD  * ink );

    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke );
    paint.setColor( with_xor ? BrushManager.xorColor( ink_color ) : ink_color );

    float radians = (float)Math.toRadians( pattern.mAngle );
    float dx = (float)Math.cos( radians );
    float dy = (float)Math.sin( radians );
    float nx = -dy;
    float ny = dx;

    float u1 = clip.left  * dx + clip.top    * dy;
    float u2 = clip.right * dx + clip.top    * dy;
    float u3 = clip.left  * dx + clip.bottom * dy;
    float u4 = clip.right * dx + clip.bottom * dy;
    float v1 = clip.left  * nx + clip.top    * ny;
    float v2 = clip.right * nx + clip.top    * ny;
    float v3 = clip.left  * nx + clip.bottom * ny;
    float v4 = clip.right * nx + clip.bottom * ny;
    float uMin = Math.min( Math.min( u1, u2 ), Math.min( u3, u4 ) );
    float uMax = Math.max( Math.max( u1, u2 ), Math.max( u3, u4 ) );
    float vMin = Math.min( Math.min( v1, v2 ), Math.min( v3, v4 ) );
    float vMax = Math.max( Math.max( v1, v2 ), Math.max( v3, v4 ) );

    float repeatV = BEDROCK_ROW_V[BEDROCK_ROW_V.length - 1] * spacing;
    int tile0 = (int)Math.floor( vMin / repeatV ) - 1;
    int tile1 = (int)Math.ceil ( vMax / repeatV ) + 1;
    int j0 = (int)Math.floor( uMin / period ) - 2;
    int j1 = (int)Math.ceil ( uMax / period ) + 2;
    float lineUMin = uMin - period;
    float lineUMax = uMax + period;

    int save = canvas.save();
    try {
      canvas.clipPath( region );
      for ( int tile = tile0; tile <= tile1; ++tile ) {
        float tileV = tile * repeatV;
        for ( int row = 0; row < BEDROCK_ROWS; ++row ) {
          float topV = tileV + BEDROCK_ROW_V[row] * spacing;
          float bottomV = tileV + BEDROCK_ROW_V[row+1] * spacing;

          // Draw each course boundary once. The next repeat's row zero supplies the
          // final boundary of the previous six-course tile, avoiding alpha doubling.
          canvas.drawLine( lineUMin * dx + topV * nx, lineUMin * dy + topV * ny,
                           lineUMax * dx + topV * nx, lineUMax * dy + topV * ny, paint );

          for ( int j = j0; j <= j1; ++j ) {
            int slot = Math.floorMod( j, BEDROCK_SLOTS );
            float centerU = ( j + BEDROCK_JOINT_SHIFT[row] + BEDROCK_JOINT_DU[row][slot] ) * period;
            float skew = BEDROCK_JOINT_SKEW[row][slot] * period;
            float topU = centerU - 0.5f * skew;
            float bottomU = centerU + 0.5f * skew;
            canvas.drawLine( topU * dx + topV * nx, topU * dy + topV * ny,
                             bottomU * dx + bottomV * nx, bottomU * dy + bottomV * ny, paint );
          }
        }
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** erase ink alpha toward the region boundary with FADE_PASSES-1 concentric
   * DST_OUT strokes: pass j (width 2*fade*j/K, alpha 255/(j+1)) erases depths up to
   * fade*j/K, so the depth band ((j-1)/K, j/K]*fade keeps PROD_{k=j..K-1} k/(k+1) = j/K
   * of the ink alpha - a linear ramp from ~0 at the boundary to full at depth fade.
   * Outside the region the group layer is transparent, so no clip is needed.
   */
  private static void drawBoundaryFade( Canvas canvas, Path region, float fade )
  {
    if ( fade <= EPS ) return;
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setColor( 0xff000000 );
    paint.setXfermode( FADE_ERASE_XFERMODE );
    for ( int j = 1; j < FADE_PASSES; ++j ) {
      paint.setStrokeWidth( 2f * fade * j / FADE_PASSES );
      paint.setAlpha( Math.round( 255f / ( j + 1 ) ) );
      canvas.drawPath( region, paint );
    }
  }

  private static float positive( float value, float fallback )
  {
    return ( value > EPS && ! Float.isNaN( value ) && ! Float.isInfinite( value ) )? value : fallback;
  }
}
