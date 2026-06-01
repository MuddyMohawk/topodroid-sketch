/* @file LineSymbolEffect.java
 *
 * @brief TopoDroid drawing: rigid line-symbol effect renderer
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.ArrayList;

class LineSymbolEffect
{
  private static final float MIN_ADVANCE = 0.5f;
  private static final float TANGENT_EPS = 1.0e-6f;
  private static final float CARRIER_SAMPLE_STEP = 2.0f;
  private static final float FIT_EPS = 1.0e-3f;

  static class Carrier
  {
    final float y0;
    final float y1;

    Carrier( float y0, float y1 )
    {
      if ( y0 <= y1 ) {
        this.y0 = y0;
        this.y1 = y1;
      } else {
        this.y0 = y1;
        this.y1 = y0;
      }
    }
  }

  private final Path mPath;
  private final Path mRevPath;
  private final Path mFixedPath;
  private final Path mRevFixedPath;
  private final RectF mBounds;
  private final RectF mRevBounds;
  private final RectF mFixedBounds;
  private final RectF mRevFixedBounds;
  private final float mAdvance;
  private final float mFixedAdvance;
  private final float[] mDash;
  private final float[] mFixedDash;
  private boolean mHasSketchEffect;
  private boolean mHasSketchStamp;
  private Path mSketchPath;
  private Path mSketchRevPath;
  private Path mSketchFixedPath;
  private Path mSketchRevFixedPath;
  private RectF mSketchBounds;
  private RectF mSketchRevBounds;
  private RectF mSketchFixedBounds;
  private RectF mSketchRevFixedBounds;
  private float mSketchAnchor;
  private float mSketchRevAnchor;
  private float mSketchFixedAnchor;
  private float mSketchRevFixedAnchor;
  private Carrier[] mCarriers;
  private Carrier[] mRevCarriers;
  private Carrier[] mFixedCarriers;
  private Carrier[] mRevFixedCarriers;

  LineSymbolEffect( Path path, Path rev_path, float advance, float[] dash )
  {
    mPath = copyPath( path );
    mRevPath = copyPath( rev_path );
    mFixedPath = scaledPath( mPath, SymbolLine.FIXED_PATTERN_SCALE );
    mRevFixedPath = scaledPath( mRevPath, SymbolLine.FIXED_PATTERN_SCALE );

    mBounds = boundsOf( mPath );
    mRevBounds = boundsOf( mRevPath );
    mFixedBounds = boundsOf( mFixedPath );
    mRevFixedBounds = boundsOf( mRevFixedPath );

    mAdvance = sanitizeAdvance( advance, mBounds );
    mFixedAdvance = sanitizeAdvance( mAdvance * SymbolLine.FIXED_PATTERN_SCALE, mFixedBounds );
    mDash = cloneDash( dash );
    mFixedDash = scaledDash( dash, SymbolLine.FIXED_PATTERN_SCALE );
    mHasSketchEffect = false;
    mHasSketchStamp = false;
    mSketchAnchor = 0;
    mSketchRevAnchor = 0;
    mSketchFixedAnchor = 0;
    mSketchRevFixedAnchor = 0;
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers )
  {
    mHasSketchEffect = true;
    mSketchPath = copyPath( path );
    mSketchRevPath = copyPath( rev_path );
    mSketchFixedPath = scaledPath( mSketchPath, SymbolLine.FIXED_PATTERN_SCALE );
    mSketchRevFixedPath = scaledPath( mSketchRevPath, SymbolLine.FIXED_PATTERN_SCALE );

    mSketchBounds = boundsOf( mSketchPath );
    mSketchRevBounds = boundsOf( mSketchRevPath );
    mSketchFixedBounds = boundsOf( mSketchFixedPath );
    mSketchRevFixedBounds = boundsOf( mSketchRevFixedPath );
    mHasSketchStamp = ! mSketchBounds.isEmpty() || ! mSketchRevBounds.isEmpty();
    mSketchAnchor = stampAnchor( mSketchBounds );
    mSketchRevAnchor = stampAnchor( mSketchRevBounds );
    mSketchFixedAnchor = stampAnchor( mSketchFixedBounds );
    mSketchRevFixedAnchor = stampAnchor( mSketchRevFixedBounds );

    mCarriers = makeCarriers( carriers, 1.0f, false );
    mRevCarriers = makeCarriers( carriers, 1.0f, true );
    mFixedCarriers = makeCarriers( carriers, SymbolLine.FIXED_PATTERN_SCALE, false );
    mRevFixedCarriers = makeCarriers( carriers, SymbolLine.FIXED_PATTERN_SCALE, true );
  }

  boolean draw( Canvas canvas, Path line_path, Paint paint, boolean reversed, boolean fixed_density )
  {
    if ( canvas == null || line_path == null || paint == null ) return false;

    Path pattern = getPatternPath( reversed, fixed_density );
    RectF pattern_bounds = getPatternBounds( reversed, fixed_density );
    float anchor = getPatternAnchor( reversed, fixed_density );
    Carrier[] carriers = getCarriers( reversed, fixed_density );
    float advance = fixed_density ? mFixedAdvance : mAdvance;
    float[] dash = fixed_density ? mFixedDash : mDash;
    if ( advance <= 0 ) return false;

    Paint draw_paint = new Paint( paint );
    draw_paint.setPathEffect( null );
    // The effect path already encodes the stamp thickness; the line paint
    // stroke width is only the carrier width used by Android PathEffect.
    draw_paint.setStyle( Paint.Style.FILL );
    draw_paint.setStrokeWidth( 0 );

    Rect clip = new Rect();
    RectF clip_bounds = new RectF();
    boolean has_clip = canvas.getClipBounds( clip );
    if ( has_clip ) clip_bounds.set( clip );

    PathMeasure measure = new PathMeasure( line_path, false );
    Matrix matrix = new Matrix();
    Path stamp = new Path();
    RectF stamp_bounds = new RectF();
    float[] pos = new float[2];
    float[] tan = new float[2];
    float pad = 2.0f;
    boolean drew = false;

    do {
      float length = measure.getLength();
      if ( length <= 0 ) continue;
      float dash_cycle = dashCycle( dash );
      if ( carriers != null && carriers.length > 0 ) {
        if ( dash_cycle > 0 ) {
          drew |= drawDashedCarriers( canvas, measure, length, dash, dash_cycle, carriers,
                                      draw_paint, has_clip, clip_bounds );
        } else {
          for ( int c = 0; c < carriers.length; ++c ) {
            drew |= drawCarrierSegment( canvas, measure, 0, length, carriers[c], draw_paint, has_clip, clip_bounds );
          }
        }
      }
      if ( mHasSketchEffect && ! mHasSketchStamp ) continue;
      if ( dash_cycle > 0 ) {
        drew |= drawDashedContour( canvas, measure, length, dash, dash_cycle, pattern, pattern_bounds,
                                   draw_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad, advance, anchor );
      } else {
        drew |= drawStampSegment( canvas, measure, 0, length, advance, pattern, pattern_bounds,
                                  draw_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad, anchor );
      }
    } while ( measure.nextContour() );

    return drew;
  }

  private Path getPatternPath( boolean reversed, boolean fixed_density )
  {
    if ( mHasSketchEffect ) {
      if ( fixed_density ) return reversed ? mSketchRevFixedPath : mSketchFixedPath;
      return reversed ? mSketchRevPath : mSketchPath;
    }
    if ( fixed_density ) return reversed ? mRevFixedPath : mFixedPath;
    return reversed ? mRevPath : mPath;
  }

  private RectF getPatternBounds( boolean reversed, boolean fixed_density )
  {
    if ( mHasSketchEffect ) {
      if ( fixed_density ) return reversed ? mSketchRevFixedBounds : mSketchFixedBounds;
      return reversed ? mSketchRevBounds : mSketchBounds;
    }
    if ( fixed_density ) return reversed ? mRevFixedBounds : mFixedBounds;
    return reversed ? mRevBounds : mBounds;
  }

  private Carrier[] getCarriers( boolean reversed, boolean fixed_density )
  {
    if ( ! mHasSketchEffect ) return null;
    if ( fixed_density ) return reversed ? mRevFixedCarriers : mFixedCarriers;
    return reversed ? mRevCarriers : mCarriers;
  }

  private float getPatternAnchor( boolean reversed, boolean fixed_density )
  {
    if ( ! mHasSketchEffect ) return 0;
    if ( fixed_density ) return reversed ? mSketchRevFixedAnchor : mSketchFixedAnchor;
    return reversed ? mSketchRevAnchor : mSketchAnchor;
  }

  private static Path copyPath( Path path )
  {
    return ( path == null ) ? new Path() : new Path( path );
  }

  private static Path scaledPath( Path path, float scale )
  {
    Path ret = copyPath( path );
    Matrix matrix = new Matrix();
    matrix.setScale( scale, scale );
    ret.transform( matrix );
    return ret;
  }

  private static RectF boundsOf( Path path )
  {
    RectF bounds = new RectF();
    if ( path != null ) path.computeBounds( bounds, true );
    return bounds;
  }

  private static float stampAnchor( RectF bounds )
  {
    return ( bounds == null || bounds.isEmpty() ) ? 0 : bounds.centerX();
  }

  private static float sanitizeAdvance( float advance, RectF bounds )
  {
    if ( advance > MIN_ADVANCE ) return advance;
    if ( bounds != null && bounds.width() > MIN_ADVANCE ) return bounds.width();
    return MIN_ADVANCE;
  }

  private static float[] cloneDash( float[] dash )
  {
    if ( dash == null || dash.length == 0 ) return null;
    float[] ret = new float[dash.length];
    System.arraycopy( dash, 0, ret, 0, dash.length );
    return ret;
  }

  private static float[] scaledDash( float[] dash, float scale )
  {
    if ( dash == null || dash.length == 0 ) return null;
    float[] ret = new float[dash.length];
    for ( int k = 0; k < dash.length; ++k ) ret[k] = Math.max( 0, dash[k] * scale );
    return ret;
  }

  private static Carrier[] makeCarriers( ArrayList< Carrier > carriers, float scale, boolean reversed )
  {
    if ( carriers == null || carriers.size() == 0 ) return null;
    Carrier[] ret = new Carrier[carriers.size()];
    for ( int k = 0; k < carriers.size(); ++k ) {
      Carrier carrier = carriers.get(k);
      if ( reversed ) {
        ret[k] = new Carrier( -carrier.y1 * scale, -carrier.y0 * scale );
      } else {
        ret[k] = new Carrier( carrier.y0 * scale, carrier.y1 * scale );
      }
    }
    return ret;
  }

  private static boolean drawDashedContour( Canvas canvas, PathMeasure measure, float length, float[] dash, float dash_cycle,
                                            Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                            RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                            float pad, float advance, float anchor )
  {
    boolean drew = false;

    for ( float cycle_start = 0; cycle_start < length; cycle_start += dash_cycle ) {
      float offset = cycle_start;
      for ( int k = 0; k < dash.length && offset < length; ++k ) {
        float interval = Math.max( 0, dash[k] );
        float next = offset + interval;
        if ( interval > 0 && ( k % 2 ) == 0 ) {
          drew |= drawStampSegment( canvas, measure, offset, Math.min( next, length ), advance, pattern, pattern_bounds,
                                    draw_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad, anchor, true );
        }
        offset = next;
      }
    }

    return drew;
  }

  private static boolean drawStampSegment( Canvas canvas, PathMeasure measure, float start, float end, float advance,
                                           Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                           RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                           float pad, float anchor )
  {
    return drawStampSegment( canvas, measure, start, end, advance, pattern, pattern_bounds, draw_paint, matrix, stamp,
                             stamp_bounds, pos, tan, has_clip, clip_bounds, pad, anchor, false );
  }

  private static boolean drawStampSegment( Canvas canvas, PathMeasure measure, float start, float end, float advance,
                                           Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                           RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                           float pad, float anchor, boolean fit_repeats )
  {
    boolean drew = false;
    boolean first = true;
    for ( float distance = start; distance < end; distance += advance ) {
      if ( fit_repeats && ! first && distance + advance > end + FIT_EPS ) break;
      float target = distance + anchor;
      if ( target < start ) target = start;
      if ( target > end ) break;
      if ( drawStamp( canvas, measure, target, anchor, pattern, pattern_bounds, draw_paint, matrix, stamp, stamp_bounds,
                      pos, tan, has_clip, clip_bounds, pad ) ) drew = true;
      first = false;
    }
    return drew;
  }

  private static boolean drawDashedCarriers( Canvas canvas, PathMeasure measure, float length, float[] dash, float dash_cycle,
                                             Carrier[] carriers, Paint draw_paint, boolean has_clip, RectF clip_bounds )
  {
    boolean drew = false;

    for ( float cycle_start = 0; cycle_start < length; cycle_start += dash_cycle ) {
      float offset = cycle_start;
      for ( int k = 0; k < dash.length && offset < length; ++k ) {
        float interval = Math.max( 0, dash[k] );
        float next = offset + interval;
        if ( interval > 0 && ( k % 2 ) == 0 ) {
          for ( int c = 0; c < carriers.length; ++c ) {
            drew |= drawCarrierSegment( canvas, measure, offset, Math.min( next, length ), carriers[c],
                                        draw_paint, has_clip, clip_bounds );
          }
        }
        offset = next;
      }
    }

    return drew;
  }

  private static boolean drawCarrierSegment( Canvas canvas, PathMeasure measure, float start, float end, Carrier carrier,
                                             Paint draw_paint, boolean has_clip, RectF clip_bounds )
  {
    if ( carrier == null || end <= start || carrier.y1 <= carrier.y0 ) return false;

    int count = Math.max( 2, (int)Math.ceil( ( end - start ) / CARRIER_SAMPLE_STEP ) + 1 );
    float[] x0 = new float[count];
    float[] y0 = new float[count];
    float[] x1 = new float[count];
    float[] y1 = new float[count];
    float[] pos = new float[2];
    float[] tan = new float[2];
    int points = 0;

    for ( int k = 0; k < count; ++k ) {
      float distance = ( k == count - 1 ) ? end : Math.min( end, start + k * CARRIER_SAMPLE_STEP );
      if ( ! measure.getPosTan( distance, pos, tan ) ) continue;
      float mag = (float)Math.sqrt( tan[0] * tan[0] + tan[1] * tan[1] );
      if ( mag < TANGENT_EPS ) continue;
      float nx = -tan[1] / mag;
      float ny =  tan[0] / mag;
      x0[points] = pos[0] + nx * carrier.y0;
      y0[points] = pos[1] + ny * carrier.y0;
      x1[points] = pos[0] + nx * carrier.y1;
      y1[points] = pos[1] + ny * carrier.y1;
      ++points;
    }

    if ( points < 2 ) return false;

    Path ribbon = new Path();
    ribbon.moveTo( x0[0], y0[0] );
    for ( int k = 1; k < points; ++k ) ribbon.lineTo( x0[k], y0[k] );
    for ( int k = points - 1; k >= 0; --k ) ribbon.lineTo( x1[k], y1[k] );
    ribbon.close();

    RectF bounds = new RectF();
    ribbon.computeBounds( bounds, true );
    bounds.inset( -2.0f, -2.0f );
    if ( has_clip && ! intersects( bounds, clip_bounds ) ) return false;

    canvas.drawPath( ribbon, draw_paint );
    return true;
  }

  private static boolean drawStamp( Canvas canvas, PathMeasure measure, float distance, float anchor,
                                    Path pattern, RectF pattern_bounds,
                                    Paint draw_paint, Matrix matrix, Path stamp, RectF stamp_bounds, float[] pos, float[] tan,
                                    boolean has_clip, RectF clip_bounds, float pad )
  {
    if ( ! measure.getPosTan( distance, pos, tan ) ) return false;
    if ( Math.abs( tan[0] ) < TANGENT_EPS && Math.abs( tan[1] ) < TANGENT_EPS ) return false;

    matrix.reset();
    matrix.setTranslate( -anchor, 0 );
    matrix.postRotate( (float)Math.toDegrees( Math.atan2( tan[1], tan[0] ) ) );
    matrix.postTranslate( pos[0], pos[1] );

    stamp_bounds.set( pattern_bounds );
    matrix.mapRect( stamp_bounds );
    stamp_bounds.inset( -pad, -pad );
    if ( has_clip && ! intersects( stamp_bounds, clip_bounds ) ) return false;

    stamp.reset();
    stamp.addPath( pattern, matrix );
    canvas.drawPath( stamp, draw_paint );
    return true;
  }

  private static float dashCycle( float[] dash )
  {
    if ( dash == null || dash.length == 0 ) return 0;
    float cycle = 0;
    for ( int k = 0; k < dash.length; ++k ) {
      cycle += Math.max( 0, dash[k] );
    }
    return cycle;
  }

  private static boolean intersects( RectF a, RectF b )
  {
    return a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom;
  }
}
