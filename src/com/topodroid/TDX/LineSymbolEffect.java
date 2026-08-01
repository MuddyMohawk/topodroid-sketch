/* @file LineSymbolEffect.java
 *
 * @brief TopoDroid drawing: line-symbol pattern renderer (world-space ink model)
 *
 * Patterns (stamps, carriers, dashes, advance) are authored in LINE-WIDTH UNITS:
 * a value of 1 equals one ink thickness. At draw time the whole pattern is scaled
 * by the paint stroke width (scene units), so the pattern magnifies uniformly with
 * the placement weight, and with zoom / export scale via the canvas transform.
 * A "carrier 0 1" ribbon is therefore exactly as thick as a plain line of the
 * same weight.
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
import java.util.HashMap;

class LineSymbolEffect
{
  private static final float MIN_ADVANCE = 0.05f;   // [line-width units]
  private static final float TANGENT_EPS = 1.0e-6f;
  private static final float FIT_EPS = 1.0e-3f;
  private static final float MIN_UNIT = 1.0e-3f;    // [scene units]
  private static final int   MAX_CACHE = 16;
  private static final int   MAX_CARRIER_SAMPLES = 4096;

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

  // raw pattern, in line-width units
  private final Path mPath;
  private final Path mRevPath;
  private final float mAdvance;
  private final float[] mDash;
  private boolean mHasSketchEffect;
  private boolean mHasSketchStamp;
  private boolean mSketchStroke;
  private Path mSketchPath;
  private Path mSketchRevPath;
  private Carrier[] mCarriers;
  private Carrier[] mRevCarriers;

  /** pattern data pre-scaled to a given scene unit (= ink thickness) */
  private static class ScaledPattern
  {
    final Path path;
    final RectF bounds;
    final float anchor;
    final Carrier[] carriers;
    final float advance;
    final float[] dash;

    ScaledPattern( Path path, Carrier[] carriers, float advance, float[] dash, boolean stamp_anchor )
    {
      this.path = path;
      this.bounds = boundsOf( path );
      this.anchor = stamp_anchor ? stampAnchor( this.bounds ) : 0;
      this.carriers = carriers;
      this.advance = advance;
      this.dash = dash;
    }
  }

  private final HashMap< Long, ScaledPattern > mScaledCache = new HashMap<>();

  /** @param path      forward pattern path [line-width units]
   *  @param rev_path  reversed pattern path
   *  @param advance   pattern repeat distance [line-width units]
   *  @param dash      optional dash intervals [line-width units]
   */
  LineSymbolEffect( Path path, Path rev_path, float advance, float[] dash )
  {
    mPath = copyPath( path );
    mRevPath = copyPath( rev_path );
    mAdvance = sanitizeAdvance( advance, boundsOf( mPath ) );
    mDash = cloneDash( dash );
    mHasSketchEffect = false;
    mHasSketchStamp = false;
    mSketchStroke = false;
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers )
  {
    setSketchEffect( path, rev_path, carriers, false );
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers, boolean stroke_stamp )
  {
    mHasSketchEffect = true;
    mSketchStroke = stroke_stamp;
    mSketchPath = copyPath( path );
    mSketchRevPath = copyPath( rev_path );
    mHasSketchStamp = ! boundsOf( mSketchPath ).isEmpty() || ! boundsOf( mSketchRevPath ).isEmpty();
    mCarriers = makeCarriers( carriers, false );
    mRevCarriers = makeCarriers( carriers, true );
    mScaledCache.clear();
  }

  /** draw the pattern along a scene-space line
   * @param canvas     canvas, already carrying the scene->screen transform
   * @param line_path  line path in scene coordinates
   * @param paint      ink paint; getStrokeWidth() [scene units] is the pattern unit
   * @param reversed   whether the line is reversed
   * @return true if something was drawn
   */
  boolean draw( Canvas canvas, Path line_path, Paint paint, boolean reversed )
  {
    return draw( canvas, line_path, paint, reversed, 1.0f );
  }

  /** @param pixel_size  scene units per screen pixel (sampling quality hint only)
   */
  boolean draw( Canvas canvas, Path line_path, Paint paint, boolean reversed, float pixel_size )
  {
    if ( canvas == null || line_path == null || paint == null ) return false;

    float unit = paint.getStrokeWidth();
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    if ( sp.advance <= 0 ) return false;
    if ( ! ( pixel_size > 0 ) || Float.isNaN( pixel_size ) || Float.isInfinite( pixel_size ) ) pixel_size = 1.0f;

    Paint carrier_paint = new Paint( paint );
    carrier_paint.setPathEffect( null );
    // Carrier ribbons encode their own thickness; stroked sketch stamps use
    // the active line paint width.
    carrier_paint.setStyle( Paint.Style.FILL );
    carrier_paint.setStrokeWidth( 0 );

    Paint stamp_paint = new Paint( paint );
    stamp_paint.setPathEffect( null );
    if ( mHasSketchEffect && mSketchStroke ) {
      stamp_paint.setStyle( Paint.Style.STROKE );
      stamp_paint.setStrokeCap( Paint.Cap.ROUND );
      stamp_paint.setStrokeJoin( Paint.Join.ROUND );
    } else {
      stamp_paint.setStyle( Paint.Style.FILL );
      stamp_paint.setStrokeWidth( 0 );
    }

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
    float pad = 2.0f * Math.max( 1.0f, unit ) + Math.max( 0.0f, stamp_paint.getStrokeWidth() );
    float sample_step = Math.max( 1.5f * pixel_size, 0.25f * unit );
    boolean drew = false;

    do {
      float length = measure.getLength();
      if ( length <= 0 ) continue;
      float dash_cycle = dashCycle( sp.dash );
      if ( sp.carriers != null && sp.carriers.length > 0 ) {
        if ( dash_cycle > 0 ) {
          drew |= drawDashedCarriers( canvas, measure, length, sp.dash, dash_cycle, sp.carriers,
                                      carrier_paint, has_clip, clip_bounds, sample_step );
        } else {
          for ( int c = 0; c < sp.carriers.length; ++c ) {
            drew |= drawCarrierSegment( canvas, measure, 0, length, sp.carriers[c], carrier_paint, has_clip, clip_bounds, sample_step );
          }
        }
      }
      if ( mHasSketchEffect && ! mHasSketchStamp ) continue;
      if ( dash_cycle > 0 ) {
        drew |= drawDashedContour( canvas, measure, length, sp.dash, dash_cycle, sp.path, sp.bounds,
                                   stamp_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad, sp.advance, sp.anchor );
      } else {
        drew |= drawStampSegment( canvas, measure, 0, length, sp.advance, sp.path, sp.bounds,
                                  stamp_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad, sp.anchor );
      }
    } while ( measure.nextContour() );

    return drew;
  }

  /** @return the dominant repeat length for a straight sample [scene units]. */
  float sampleRepeatLength( float unit, boolean reversed )
  {
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    return Math.max( sp.advance, dashCycle( sp.dash ) );
  }

  /** @return bounds of one scaled stamp/carrier unit, for sizing a render probe. */
  RectF samplePatternBounds( float unit, boolean reversed )
  {
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    RectF bounds = new RectF( sp.bounds );
    if ( sp.carriers != null ) {
      for ( Carrier carrier : sp.carriers ) {
        RectF carrier_bounds = new RectF( 0.0f, carrier.y0, Math.max( unit, sp.advance ), carrier.y1 );
        if ( bounds.isEmpty() ) bounds.set( carrier_bounds ); else bounds.union( carrier_bounds );
      }
    }
    if ( bounds.isEmpty() ) bounds.set( 0.0f, -0.5f * unit, Math.max( unit, sp.advance ), 0.5f * unit );
    if ( mSketchStroke ) bounds.inset( -0.5f * unit, -0.5f * unit );
    return bounds;
  }

  private ScaledPattern scaled( float unit, boolean reversed )
  {
    long key = ( ( (long)Float.floatToIntBits( unit ) ) << 1 ) | ( reversed ? 1L : 0L );
    ScaledPattern sp = mScaledCache.get( key );
    if ( sp != null ) return sp;

    Path raw_path;
    boolean stamp_anchor;
    if ( mHasSketchEffect ) {
      raw_path = reversed ? mSketchRevPath : mSketchPath;
      stamp_anchor = true;
    } else {
      raw_path = reversed ? mRevPath : mPath;
      stamp_anchor = false;
    }
    Carrier[] raw_carriers = mHasSketchEffect ? ( reversed ? mRevCarriers : mCarriers ) : null;

    sp = new ScaledPattern( scaledPath( raw_path, unit ),
                            scaledCarriers( raw_carriers, unit ),
                            mAdvance * unit,
                            scaledDash( mDash, unit ),
                            stamp_anchor );
    if ( mScaledCache.size() >= MAX_CACHE ) mScaledCache.clear();
    mScaledCache.put( key, sp );
    return sp;
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

  private static Carrier[] scaledCarriers( Carrier[] carriers, float scale )
  {
    if ( carriers == null || carriers.length == 0 ) return null;
    Carrier[] ret = new Carrier[carriers.length];
    for ( int k = 0; k < carriers.length; ++k ) ret[k] = new Carrier( carriers[k].y0 * scale, carriers[k].y1 * scale );
    return ret;
  }

  private static Carrier[] makeCarriers( ArrayList< Carrier > carriers, boolean reversed )
  {
    if ( carriers == null || carriers.size() == 0 ) return null;
    Carrier[] ret = new Carrier[carriers.size()];
    for ( int k = 0; k < carriers.size(); ++k ) {
      Carrier carrier = carriers.get(k);
      if ( reversed ) {
        ret[k] = new Carrier( -carrier.y1, -carrier.y0 );
      } else {
        ret[k] = new Carrier( carrier.y0, carrier.y1 );
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
                                             Carrier[] carriers, Paint draw_paint, boolean has_clip, RectF clip_bounds,
                                             float sample_step )
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
                                        draw_paint, has_clip, clip_bounds, sample_step );
          }
        }
        offset = next;
      }
    }

    return drew;
  }

  private static boolean drawCarrierSegment( Canvas canvas, PathMeasure measure, float start, float end, Carrier carrier,
                                             Paint draw_paint, boolean has_clip, RectF clip_bounds, float sample_step )
  {
    if ( carrier == null || end <= start || carrier.y1 <= carrier.y0 ) return false;

    int count = Math.max( 2, (int)Math.ceil( ( end - start ) / sample_step ) + 1 );
    if ( count > MAX_CARRIER_SAMPLES ) count = MAX_CARRIER_SAMPLES;
    float step = ( end - start ) / ( count - 1 );
    float[] x0 = new float[count];
    float[] y0 = new float[count];
    float[] x1 = new float[count];
    float[] y1 = new float[count];
    float[] pos = new float[2];
    float[] tan = new float[2];
    int points = 0;

    for ( int k = 0; k < count; ++k ) {
      float distance = ( k == count - 1 ) ? end : start + k * step;
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
