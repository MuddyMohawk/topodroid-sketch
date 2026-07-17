/* @file AreaPatternRenderer.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch patterned-area group renderer: world-aligned stripes + boundary fade
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

/** Stateless renderer for stripe-patterned areas.
 *
 * All areas of one patterned symbol render as a single union region per scrap: the
 * stripes anchor to absolute scene coordinates (multiples of the stripe period along
 * the pattern normal from the scene origin) so overlapping or appended areas always
 * tile seamlessly, overlaps never double-blend, and the optional fade erases stripe
 * alpha toward the union boundary (interior holes included). Patterned areas draw no
 * per-area border.
 *
 * WORLD-SPACE INK: everything draws in scene coordinates under canvas.concat(matrix);
 * stripe metrics are ink units * TDSetting.inkUnit(). Callers invoke this concurrently
 * from the render, scene-cache and export threads: there is no instance or static
 * mutable state - all scratch is call-stack-confined.
 */
class AreaPatternRenderer
{
  private static final float EPS = 1.0e-4f;
  // fade ramp steps: depth band j of FADE_PASSES keeps j/FADE_PASSES of the stripe alpha
  private static final int FADE_PASSES = 12;
  private static final PorterDuffXfermode FADE_ERASE_XFERMODE = new PorterDuffXfermode( PorterDuff.Mode.DST_OUT );

  private AreaPatternRenderer() { } // static-only

  /** render one patterned-symbol group as a single union region
   * @param canvas    canvas (scene->target transform NOT yet applied)
   * @param matrix    scene->target transform
   * @param bbox      scene-space clipping rectangle (null = no culling)
   * @param pattern   stripe parameters of the symbol
   * @param members   areas of this symbol to merge
   * @param with_xor  whether stripe colors are xor-ed (inverted-colors rendering)
   */
  static void drawGroup( Canvas canvas, Matrix matrix, RectF bbox, AreaLinePattern pattern,
                         ArrayList< DrawingAreaPath > members, boolean with_xor )
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
      drawRegion( canvas, matrix, bbox, pattern, union, with_xor );
    } else {
      // degraded but deterministic: per-member regions keep the stripes aligned (overlaps
      // re-draw identical pixels); only the fade doubles up along interior shared edges
      TDLog.e( "area pattern union failed - drawing members separately" );
      for ( DrawingAreaPath member : members ) {
        Path p = new Path( member.mPath );
        p.close();
        drawRegion( canvas, matrix, bbox, pattern, p, with_xor );
      }
    }
  }

  /** stripe-fill one merged region: stripes clipped to the region, then the boundary fade
   */
  private static void drawRegion( Canvas canvas, Matrix matrix, RectF bbox, AreaLinePattern pattern,
                                  Path region, boolean with_xor )
  {
    float ink     = TDSetting.inkUnit();
    float stroke  = positive( pattern.mWidthScale   * ink, AreaLinePattern.DEFAULT_WIDTH   * ink );
    float spacing = positive( pattern.mSpacingScale * ink, AreaLinePattern.DEFAULT_SPACING * ink );
    float fade    = ( pattern.mFadeScale > 0 )? pattern.mFadeScale * ink : 0f;

    RectF clip = new RectF();
    region.computeBounds( clip, true );
    if ( bbox != null ) { // cull to the viewport, padded so the fade band survives at its edges
      RectF view = new RectF( bbox );
      view.inset( -(fade + stroke), -(fade + stroke) );
      if ( ! clip.intersect( view ) ) return;
    }
    if ( clip.width() <= EPS || clip.height() <= EPS ) return;
    float margin = Math.max( stroke, spacing );
    clip.inset( -margin, -margin );

    int save = canvas.save();
    try {
      canvas.concat( matrix );
      RectF layer_rect = new RectF( clip );
      layer_rect.inset( -fade, -fade );
      // the group needs its own layer: stripes composite as one unit and the DST_OUT
      // fade strokes must only erase this group's ink, never what is already below
      int layer = canvas.saveLayer( layer_rect, null );
      drawStripes( canvas, region, clip, pattern, stroke, spacing, with_xor );
      drawBoundaryFade( canvas, region, fade );
      canvas.restoreToCount( layer );
    } finally {
      canvas.restoreToCount( save );
    }
  }

  /** draw the world-anchored parallel stripes clipped to the region
   */
  private static void drawStripes( Canvas canvas, Path region, RectF clip, AreaLinePattern pattern,
                                   float stroke, float spacing, boolean with_xor )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeWidth( stroke );
    paint.setColor( with_xor ? BrushManager.xorColor( pattern.mColor ) : pattern.mColor );

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

  /** erase stripe alpha toward the region boundary with FADE_PASSES-1 concentric
   * DST_OUT strokes: pass j (width 2*fade*j/K, alpha 255/(j+1)) erases depths up to
   * fade*j/K, so the depth band ((j-1)/K, j/K]*fade keeps PROD_{k=j..K-1} k/(k+1) = j/K
   * of the stripe alpha - a linear ramp from ~0 at the boundary to full at depth fade.
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
