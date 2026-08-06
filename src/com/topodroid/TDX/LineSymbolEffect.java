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
  private static final int   ENVELOPE_NONE = 0;
  private static final int   ENVELOPE_COSINE = 1;
  private static final int   TERMINAL_NONE = 0;
  private static final int   TERMINAL_END = 1;

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
  private boolean mHasGapStamp;
  private boolean mHasTerminalStamp;
  private boolean mSketchStroke;
  private Path mSketchPath;
  private Path mSketchRevPath;
  private Path mGapPath;
  private Path mGapRevPath;
  private Path mTerminalPath;
  private Path mTerminalRevPath;
  private Carrier[] mCarriers;
  private Carrier[] mRevCarriers;
  private int mEnvelopeType;
  private float mEnvelopeDefault;
  private float mEnvelopeMin;
  private float mEnvelopeMax;
  private int mTerminalPlacement;
  private float mTerminalInset;

  /** pattern data pre-scaled to a given scene unit (= ink thickness) */
  private static class ScaledPattern
  {
    final Path path;
    final RectF bounds;
    final float anchor;
    final Path gapPath;
    final RectF gapBounds;
    final float gapAnchor;
    final Path terminalPath;
    final RectF terminalBounds;
    final Carrier[] carriers;
    final float advance;
    final float[] dash;
    final float terminalInset;

    ScaledPattern( Path path, Path gap_path, Path terminal_path, Carrier[] carriers,
                   float advance, float[] dash, boolean stamp_anchor, float terminal_inset )
    {
      this.path = path;
      this.bounds = boundsOf( path );
      this.anchor = stamp_anchor ? stampAnchor( this.bounds ) : 0;
      this.gapPath = gap_path;
      this.gapBounds = boundsOf( gap_path );
      this.gapAnchor = stampAnchor( this.gapBounds );
      this.terminalPath = terminal_path;
      this.terminalBounds = boundsOf( terminal_path );
      this.carriers = carriers;
      this.advance = advance;
      this.dash = dash;
      this.terminalInset = terminal_inset;
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
    mHasGapStamp = false;
    mHasTerminalStamp = false;
    mSketchStroke = false;
    mEnvelopeType = ENVELOPE_NONE;
    mEnvelopeDefault = 1.0f;
    mEnvelopeMin = 1.0f;
    mEnvelopeMax = 1.0f;
    mTerminalPlacement = TERMINAL_NONE;
    mTerminalInset = 0.0f;
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers )
  {
    setSketchEffect( path, rev_path, carriers, false, false );
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers, boolean stroke_stamp )
  {
    setSketchEffect( path, rev_path, carriers, stroke_stamp, false );
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers,
                        boolean stroke_stamp, boolean terminal_end )
  {
    setSketchEffect( path, rev_path, carriers, stroke_stamp, terminal_end, 0.0f );
  }

  void setSketchEffect( Path path, Path rev_path, ArrayList< Carrier > carriers,
                        boolean stroke_stamp, boolean terminal_end, float terminal_inset )
  {
    setSketchEffect( path, rev_path, new Path(), new Path(), new Path(), new Path(),
                     carriers, stroke_stamp, terminal_end, terminal_inset );
  }

  void setSketchEffect( Path path, Path rev_path, Path gap_path, Path gap_rev_path,
                        Path terminal_path, Path terminal_rev_path,
                        ArrayList< Carrier > carriers, boolean stroke_stamp,
                        boolean terminal_end, float terminal_inset )
  {
    mHasSketchEffect = true;
    mSketchStroke = stroke_stamp;
    boolean explicit_terminal = ! boundsOf( terminal_path ).isEmpty() || ! boundsOf( terminal_rev_path ).isEmpty();
    if ( terminal_end && ! explicit_terminal ) {
      mSketchPath = new Path();
      mSketchRevPath = new Path();
      mTerminalPath = copyPath( path );
      mTerminalRevPath = copyPath( rev_path );
    } else {
      mSketchPath = copyPath( path );
      mSketchRevPath = copyPath( rev_path );
      mTerminalPath = terminal_end ? copyPath( terminal_path ) : new Path();
      mTerminalRevPath = terminal_end ? copyPath( terminal_rev_path ) : new Path();
    }
    mGapPath = copyPath( gap_path );
    mGapRevPath = copyPath( gap_rev_path );
    mHasSketchStamp = ! boundsOf( mSketchPath ).isEmpty() || ! boundsOf( mSketchRevPath ).isEmpty();
    mHasGapStamp = ! boundsOf( mGapPath ).isEmpty() || ! boundsOf( mGapRevPath ).isEmpty();
    mHasTerminalStamp = ! boundsOf( mTerminalPath ).isEmpty() || ! boundsOf( mTerminalRevPath ).isEmpty();
    mCarriers = makeCarriers( carriers, false );
    mRevCarriers = makeCarriers( carriers, true );
    mTerminalPlacement = terminal_end ? TERMINAL_END : TERMINAL_NONE;
    mTerminalInset = terminal_end ? Math.max( 0.0f, terminal_inset ) : 0.0f;
    mScaledCache.clear();
  }

  /** Configure a smooth symmetric length envelope for repeated sketch stamps. */
  void setCosineEnvelope( float default_peak, float min_peak, float max_peak )
  {
    if ( ! isFinitePositive( min_peak ) ) min_peak = 1.0f;
    if ( ! isFinitePositive( max_peak ) ) max_peak = min_peak;
    if ( max_peak < min_peak ) {
      float swap = min_peak;
      min_peak = max_peak;
      max_peak = swap;
    }
    mEnvelopeType = ENVELOPE_COSINE;
    mEnvelopeMin = min_peak;
    mEnvelopeMax = max_peak;
    mEnvelopeDefault = clampFinite( default_peak, min_peak, max_peak, min_peak );
  }

  boolean hasEnvelope() { return mEnvelopeType != ENVELOPE_NONE; }

  boolean hasSketchEffect() { return mHasSketchEffect; }

  boolean hasTerminalEnd() { return mTerminalPlacement == TERMINAL_END; }

  float terminalInset() { return mTerminalInset; }

  float envelopeDefault() { return mEnvelopeDefault; }

  float envelopeMin() { return mEnvelopeMin; }

  float envelopeMax() { return mEnvelopeMax; }

  /** Clamp a placement override to the limits declared by the symbol. */
  float resolveEnvelopePeak( float peak )
  {
    if ( ! hasEnvelope() ) return 1.0f;
    return clampFinite( peak, mEnvelopeMin, mEnvelopeMax, mEnvelopeDefault );
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
    return draw( canvas, line_path, paint, reversed, 1.0f, mEnvelopeDefault );
  }

  /** @param pixel_size  scene units per screen pixel (sampling quality hint only)
   */
  boolean draw( Canvas canvas, Path line_path, Paint paint, boolean reversed, float pixel_size )
  {
    return draw( canvas, line_path, paint, reversed, pixel_size, mEnvelopeDefault );
  }

  /** @param envelope_peak placement-specific peak multiplier for an optional stamp envelope
   */
  boolean draw( Canvas canvas, Path line_path, Paint paint, boolean reversed, float pixel_size, float envelope_peak )
  {
    if ( canvas == null || line_path == null || paint == null ) return false;

    float unit = paint.getStrokeWidth();
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    if ( sp.advance <= 0 ) return false;
    float peak = resolveEnvelopePeak( envelope_peak );
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
      float carrier_start = 0.0f;
      float carrier_end = length;
      if ( mTerminalPlacement == TERMINAL_END && sp.terminalInset > 0.0f ) {
        float inset = Math.min( length, sp.terminalInset );
        if ( reversed ) carrier_start = inset; else carrier_end = length - inset;
      }
      if ( sp.carriers != null && sp.carriers.length > 0 ) {
        if ( dash_cycle > 0 ) {
          drew |= drawDashedCarriers( canvas, measure, length, sp.dash, dash_cycle, sp.carriers,
                                      carrier_paint, has_clip, clip_bounds, sample_step,
                                      carrier_start, carrier_end );
        } else {
          for ( int c = 0; c < sp.carriers.length; ++c ) {
            drew |= drawCarrierSegment( canvas, measure, carrier_start, carrier_end, sp.carriers[c],
                                        carrier_paint, has_clip, clip_bounds, sample_step );
          }
        }
      }
      boolean repeat_stamp = mHasSketchEffect ? mHasSketchStamp : ! sp.bounds.isEmpty();
      if ( repeat_stamp && dash_cycle > 0 ) {
        drew |= drawDashedContour( canvas, measure, length, sp.dash, dash_cycle, sp.path, sp.bounds,
                                   stamp_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad,
                                   sp.advance, sp.anchor, mEnvelopeType, peak );
      } else if ( repeat_stamp ) {
        drew |= drawStampSegment( canvas, measure, 0, length, sp.advance, sp.path, sp.bounds,
                                  stamp_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad,
                                  sp.anchor, length, mEnvelopeType, peak );
      }
      if ( mHasGapStamp && dash_cycle > 0 ) {
        drew |= drawGapContour( canvas, measure, length, sp.dash, dash_cycle, sp.gapPath, sp.gapBounds,
                                stamp_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds,
                                pad, sp.gapAnchor, carrier_start, carrier_end );
      }
      if ( mHasTerminalStamp ) {
        drew |= drawTerminalStamp( canvas, measure, length, sp.terminalPath, sp.terminalBounds, stamp_paint,
                                   matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds,
                                   pad, reversed );
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
    return samplePatternBounds( unit, reversed, mEnvelopeDefault );
  }

  /** @return bounds of one scaled stamp/carrier unit at a placement peak. */
  RectF samplePatternBounds( float unit, boolean reversed, float envelope_peak )
  {
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    RectF bounds = new RectF( sp.bounds );
    if ( hasEnvelope() && ! bounds.isEmpty() ) {
      float peak = resolveEnvelopePeak( envelope_peak );
      bounds.top *= peak;
      bounds.bottom *= peak;
      if ( bounds.top > bounds.bottom ) {
        float swap = bounds.top;
        bounds.top = bounds.bottom;
        bounds.bottom = swap;
      }
    }
    unionBounds( bounds, sp.gapBounds );
    unionBounds( bounds, sp.terminalBounds );
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

  /** @return conservative radial padding around a line path for effect-aware culling. */
  float samplePatternRadius( float unit, boolean reversed, float envelope_peak )
  {
    if ( ! ( unit > MIN_UNIT ) || Float.isNaN( unit ) || Float.isInfinite( unit ) ) unit = 1.0f;
    ScaledPattern sp = scaled( unit, reversed );
    RectF bounds = samplePatternBounds( unit, reversed, envelope_peak );
    float dx = Math.max( Math.abs( bounds.left ), Math.abs( bounds.right ) );
    float dy = Math.max( Math.abs( bounds.top ), Math.abs( bounds.bottom ) );
    return (float)Math.sqrt( dx * dx + dy * dy );
  }

  private ScaledPattern scaled( float unit, boolean reversed )
  {
    long key = ( ( (long)Float.floatToIntBits( unit ) ) << 1 ) | ( reversed ? 1L : 0L );
    ScaledPattern sp = mScaledCache.get( key );
    if ( sp != null ) return sp;

    Path raw_path;
    Path raw_gap_path = new Path();
    Path raw_terminal_path = new Path();
    boolean stamp_anchor;
    if ( mHasSketchEffect ) {
      raw_path = reversed ? mSketchRevPath : mSketchPath;
      raw_gap_path = reversed ? mGapRevPath : mGapPath;
      raw_terminal_path = reversed ? mTerminalRevPath : mTerminalPath;
      stamp_anchor = true;
    } else {
      raw_path = reversed ? mRevPath : mPath;
      stamp_anchor = false;
    }
    Carrier[] raw_carriers = mHasSketchEffect ? ( reversed ? mRevCarriers : mCarriers ) : null;

    sp = new ScaledPattern( scaledPath( raw_path, unit ),
                            scaledPath( raw_gap_path, unit ),
                            scaledPath( raw_terminal_path, unit ),
                            scaledCarriers( raw_carriers, unit ),
                            mAdvance * unit,
                            scaledDash( mDash, unit ),
                            stamp_anchor,
                            mTerminalInset * unit );
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

  private static void unionBounds( RectF into, RectF extra )
  {
    if ( extra == null || extra.isEmpty() ) return;
    if ( into.isEmpty() ) into.set( extra ); else into.union( extra );
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
                                            float pad, float advance, float anchor, int envelope_type, float envelope_peak )
  {
    boolean drew = false;

    for ( float cycle_start = 0; cycle_start < length; cycle_start += dash_cycle ) {
      float offset = cycle_start;
      for ( int k = 0; k < dash.length && offset < length; ++k ) {
        float interval = Math.max( 0, dash[k] );
        float next = offset + interval;
        if ( interval > 0 && ( k % 2 ) == 0 ) {
          drew |= drawStampSegment( canvas, measure, offset, Math.min( next, length ), advance, pattern, pattern_bounds,
                                    draw_paint, matrix, stamp, stamp_bounds, pos, tan, has_clip, clip_bounds, pad,
                                    anchor, length, envelope_type, envelope_peak, true );
        }
        offset = next;
      }
    }

    return drew;
  }

  /** Draw one rigid stamp centered in every complete dash-off interval. */
  private static boolean drawGapContour( Canvas canvas, PathMeasure measure, float length, float[] dash, float dash_cycle,
                                         Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                         RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                         float pad, float anchor, float usable_start, float usable_end )
  {
    boolean drew = false;
    for ( float cycle_start = 0; cycle_start < length; cycle_start += dash_cycle ) {
      float offset = cycle_start;
      for ( int k = 0; k < dash.length && offset < length; ++k ) {
        float interval = Math.max( 0, dash[k] );
        float next = Math.min( length, offset + interval );
        if ( interval > 0 && ( k % 2 ) == 1
            && offset >= usable_start - FIT_EPS && next <= usable_end + FIT_EPS
            && next - offset >= interval - FIT_EPS ) {
          float target = 0.5f * ( offset + next );
          drew |= drawStamp( canvas, measure, target, anchor, pattern, pattern_bounds,
                             draw_paint, matrix, stamp, stamp_bounds, pos, tan,
                             has_clip, clip_bounds, pad, 1.0f );
        }
        offset += interval;
      }
    }
    return drew;
  }

  private static boolean drawStampSegment( Canvas canvas, PathMeasure measure, float start, float end, float advance,
                                           Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                           RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                           float pad, float anchor, float contour_length, int envelope_type, float envelope_peak )
  {
    return drawStampSegment( canvas, measure, start, end, advance, pattern, pattern_bounds, draw_paint, matrix, stamp,
                             stamp_bounds, pos, tan, has_clip, clip_bounds, pad, anchor, contour_length,
                             envelope_type, envelope_peak, false );
  }

  private static boolean drawStampSegment( Canvas canvas, PathMeasure measure, float start, float end, float advance,
                                           Path pattern, RectF pattern_bounds, Paint draw_paint, Matrix matrix, Path stamp,
                                           RectF stamp_bounds, float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                           float pad, float anchor, float contour_length, int envelope_type, float envelope_peak,
                                           boolean fit_repeats )
  {
    boolean drew = false;
    boolean first = true;
    boolean single_short_stamp = end > start && end - start < advance;
    for ( float distance = start; distance < end; distance += advance ) {
      if ( fit_repeats && ! first && distance + advance > end + FIT_EPS ) break;
      float target = single_short_stamp ? 0.5f * ( start + end ) : distance + anchor;
      if ( target < start ) target = start;
      if ( target > end ) break;
      float normal_scale = envelopeScale( envelope_type, envelope_peak, target, contour_length );
      if ( drawStamp( canvas, measure, target, anchor, pattern, pattern_bounds, draw_paint, matrix, stamp, stamp_bounds,
                      pos, tan, has_clip, clip_bounds, pad, normal_scale ) ) drew = true;
      first = false;
      if ( single_short_stamp ) break;
    }
    return drew;
  }

  private static boolean drawDashedCarriers( Canvas canvas, PathMeasure measure, float length, float[] dash, float dash_cycle,
                                             Carrier[] carriers, Paint draw_paint, boolean has_clip, RectF clip_bounds,
                                             float sample_step, float usable_start, float usable_end )
  {
    boolean drew = false;

    for ( float cycle_start = 0; cycle_start < length; cycle_start += dash_cycle ) {
      float offset = cycle_start;
      for ( int k = 0; k < dash.length && offset < length; ++k ) {
        float interval = Math.max( 0, dash[k] );
        float next = offset + interval;
        if ( interval > 0 && ( k % 2 ) == 0 ) {
          float segment_start = Math.max( offset, usable_start );
          float segment_end = Math.min( Math.min( next, length ), usable_end );
          for ( int c = 0; c < carriers.length; ++c ) {
            drew |= drawCarrierSegment( canvas, measure, segment_start, segment_end, carriers[c],
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
                                    boolean has_clip, RectF clip_bounds, float pad, float normal_scale )
  {
    return drawOrientedStamp( canvas, measure, distance, anchor, pattern, pattern_bounds,
                              draw_paint, matrix, stamp, stamp_bounds, pos, tan,
                              has_clip, clip_bounds, pad, normal_scale, false );
  }

  /** Draw one stamp with its local origin at the terminating point of the contour.
   *  Reversing a placement changes both the endpoint and the direction of its tangent.
   */
  private static boolean drawTerminalStamp( Canvas canvas, PathMeasure measure, float length,
                                            Path pattern, RectF pattern_bounds, Paint draw_paint,
                                            Matrix matrix, Path stamp, RectF stamp_bounds, float[] pos, float[] tan,
                                            boolean has_clip, RectF clip_bounds, float pad, boolean reversed )
  {
    float distance = reversed ? 0.0f : length;
    return drawOrientedStamp( canvas, measure, distance, 0.0f, pattern, pattern_bounds,
                              draw_paint, matrix, stamp, stamp_bounds, pos, tan,
                              has_clip, clip_bounds, pad, 1.0f, reversed );
  }

  private static boolean drawOrientedStamp( Canvas canvas, PathMeasure measure, float distance, float anchor,
                                            Path pattern, RectF pattern_bounds,
                                            Paint draw_paint, Matrix matrix, Path stamp, RectF stamp_bounds,
                                            float[] pos, float[] tan, boolean has_clip, RectF clip_bounds,
                                            float pad, float normal_scale, boolean reverse_tangent )
  {
    if ( ! measure.getPosTan( distance, pos, tan ) ) return false;
    if ( Math.abs( tan[0] ) < TANGENT_EPS && Math.abs( tan[1] ) < TANGENT_EPS ) return false;
    if ( reverse_tangent ) {
      tan[0] = -tan[0];
      tan[1] = -tan[1];
    }

    matrix.reset();
    matrix.setTranslate( -anchor, 0 );
    matrix.postScale( 1.0f, normal_scale );
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

  private static float envelopeScale( int envelope_type, float peak, float distance, float contour_length )
  {
    if ( envelope_type != ENVELOPE_COSINE || peak <= 1.0f || contour_length <= 0.0f ) return 1.0f;
    float t = Math.max( 0.0f, Math.min( 1.0f, distance / contour_length ) );
    return 1.0f + ( peak - 1.0f ) * 0.5f
        * ( 1.0f - (float)Math.cos( 2.0 * Math.PI * t ) );
  }

  private static boolean isFinitePositive( float value )
  {
    return value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value );
  }

  private static float clampFinite( float value, float min, float max, float fallback )
  {
    if ( Float.isNaN( value ) || Float.isInfinite( value ) ) value = fallback;
    return Math.max( min, Math.min( max, value ) );
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
