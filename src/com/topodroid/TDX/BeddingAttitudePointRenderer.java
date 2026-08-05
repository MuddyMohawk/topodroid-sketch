/* @file BeddingAttitudePointRenderer.java
 *
 * @brief Dynamic, convention-following bedding strike-and-dip renderer
 */
package com.topodroid.TDX;

import com.topodroid.geo.BeddingAttitude;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

final class BeddingAttitudePointRenderer implements SpecialPointRenderer
{
  private static final float TEXT_LINE_HEIGHT = 7.0f;
  private static final float HORIZONTAL_RADIUS = 5.0f;

  @Override public void draw( DrawingSemanticPointPath point, Canvas canvas, int xor_color )
  {
    if ( point == null || canvas == null
        || ! ( point.specialState() instanceof BeddingAttitudePointState ) ) return;
    BeddingAttitudePointState state = (BeddingAttitudePointState)point.specialState();
    BeddingAttitude attitude = state.attitude();
    Paint source = point.specialPointPaint();
    if ( source == null || attitude == null ) return;
    float scale = Math.max( 0.01f, point.specialPointScale()
      * SpecialPointSizing.BEDDING_ATTITUDE_SCALE );
    drawState( canvas, point.cx, point.cy, source, state, scale, xor_color );
  }

  static void drawState( Canvas canvas, float cx, float cy, Paint source,
                         BeddingAttitudePointState state, float scale, int xor_color )
  {
    if ( canvas == null || source == null || state == null ) return;
    BeddingAttitude attitude = state.attitude();
    if ( attitude == null ) return;
    Paint ink = xor_color > 0 ? DrawingPath.xorPaint( source, xor_color ) : new Paint( source );
    ink.setStyle( Paint.Style.STROKE );
    ink.setStrokeCap( Paint.Cap.ROUND );
    ink.setStrokeJoin( Paint.Join.ROUND );

    scale = Math.max( 0.01f, scale );
    if ( state.viewKind == BeddingAttitudePointState.ViewKind.PLAN ) {
      drawPlan( cx, cy, canvas, ink, state, attitude, scale );
    } else if ( state.traceValid && Double.isFinite( state.canvasTraceAngleDegrees ) ) {
      drawProfile( cx, cy, canvas, ink, state, attitude, scale );
    } else {
      drawUnavailableProfile( cx, cy, canvas, ink, scale );
    }
  }

  private static void drawPlan( float cx, float cy, Canvas canvas, Paint ink,
                         BeddingAttitudePointState state, BeddingAttitude attitude, float scale )
  {
    BeddingAttitude.Kind glyph_kind = state.planGlyphKind();
    if ( glyph_kind == BeddingAttitude.Kind.HORIZONTAL ) {
      float radius = HORIZONTAL_RADIUS * scale;
      canvas.drawCircle( cx, cy, radius, ink );
      canvas.drawLine( cx - radius, cy, cx + radius, cy, ink );
      canvas.drawLine( cx, cy - radius, cx, cy + radius, ink );
      return;
    }

    double strike = Math.toRadians( attitude.strikeRhrDegrees );
    float sx = (float)Math.sin( strike );
    float sy = (float)-Math.cos( strike );
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( cx - sx * half, cy - sy * half,
                     cx + sx * half, cy + sy * half, ink );

    float dx;
    float dy;
    if ( glyph_kind == BeddingAttitude.Kind.VERTICAL ) {
      dx = -sy;
      dy = sx;
      float tick = 0.5f * SpecialPointSizing.BEDDING_DIP_TICK_LENGTH * scale;
      canvas.drawLine( cx - dx * tick, cy - dy * tick,
                       cx + dx * tick, cy + dy * tick, ink );
      return;
    } else {
      double direction = Math.toRadians( attitude.dipDirectionDegrees );
      dx = (float)Math.sin( direction );
      dy = (float)-Math.cos( direction );
      float tick = SpecialPointSizing.BEDDING_DIP_TICK_LENGTH * scale;
      canvas.drawLine( cx, cy, cx + dx * tick, cy + dy * tick, ink );
    }
    drawDipText( cx, cy, canvas, ink, state, attitude, dx, dy, scale, false );
  }

  private static void drawProfile( float cx, float cy, Canvas canvas, Paint ink,
                            BeddingAttitudePointState state, BeddingAttitude attitude, float scale )
  {
    double angle = Math.toRadians( state.canvasTraceAngleDegrees );
    float dx = (float)Math.cos( angle );
    float dy = (float)Math.sin( angle );
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( cx - dx * half, cy - dy * half,
                     cx + dx * half, cy + dy * half, ink );

    float side_x = -dy;
    float side_y = dx;
    if ( side_y < 0.0f ) { side_x = -side_x; side_y = -side_y; }
    drawDipText( cx, cy, canvas, ink, state, attitude, side_x, side_y, scale, true );
  }

  private static void drawUnavailableProfile( float cx, float cy, Canvas canvas,
                                       Paint ink, float scale )
  {
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( cx - half, cy, cx + half, cy, ink );
    float mark = 2.5f * scale;
    canvas.drawLine( cx - mark, cy - mark, cx + mark, cy + mark, ink );
    canvas.drawLine( cx - mark, cy + mark, cx + mark, cy - mark, ink );
  }

  private static void drawDipText( float cx, float cy, Canvas canvas, Paint ink,
                            BeddingAttitudePointState state, BeddingAttitude attitude,
                            float side_x, float side_y, float scale, boolean identify_dip )
  {
    String value = ( identify_dip ? "d" : "" )
      + Integer.toString( (int)Math.round( attitude.dipDegrees ) );
    SketchTextStyle style = SketchTextStyle.of( state.fontId(), SketchTextStyle.SizeMode.AUTO_GRID,
      1.0f, state.bold(), state.italic(), state.underline(),
      SketchTextStyle.Alignment.CENTER, ink.getColor() );
    SketchTextLayoutSnapshot layout = SketchTextLayoutSnapshot.create( value, style );
    float line_height = TEXT_LINE_HEIGHT * scale * state.textScalePercent() / 100.0f;
    float offset = SpecialPointSizing.BEDDING_TEXT_OFFSET * scale;
    float anchor_x = cx + side_x * offset;
    float anchor_y = cy + side_y * offset
      - 0.5f * ( layout.ascent + layout.descent ) * line_height;
    SketchTextRenderer.drawAt( canvas, anchor_x, anchor_y, 0.0f, style, layout, line_height, 0 );
  }

  @Override public RectF sceneBounds( DrawingSemanticPointPath point )
  {
    float scale = Math.max( 0.01f, point.specialPointScale()
      * SpecialPointSizing.BEDDING_ATTITUDE_SCALE );
    BeddingAttitudePointState state = point.specialState() instanceof BeddingAttitudePointState
      ? (BeddingAttitudePointState)point.specialState() : null;
    float radius = contentRadius( state, scale );
    Paint paint = point.specialPointPaint();
    if ( paint != null ) radius += 0.5f * Math.abs( paint.getStrokeWidth() );
    return new RectF( point.cx - radius, point.cy - radius,
                      point.cx + radius, point.cy + radius );
  }

  /** Conservative centre-to-edge distance used by both scene invalidation and
   *  the dialog preview. Everything drawn by this renderer scales linearly. */
  static float contentRadius( BeddingAttitudePointState state, float scale )
  {
    scale = Math.max( 0.01f, scale );
    BeddingAttitude attitude = state == null ? null : state.attitude();
    float radius = Math.max( HORIZONTAL_RADIUS, SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH ) * scale;
    boolean has_text = state != null && attitude != null
      && ( state.viewKind != BeddingAttitudePointState.ViewKind.PLAN
           ? state.traceValid : state.planGlyphKind() == BeddingAttitude.Kind.INCLINED );
    if ( has_text ) {
      String value = ( state.viewKind == BeddingAttitudePointState.ViewKind.PLAN ? "" : "d" )
        + Integer.toString( (int)Math.round( attitude.dipDegrees ) );
      SketchTextStyle style = SketchTextStyle.of( state.fontId(), SketchTextStyle.SizeMode.AUTO_GRID,
        1.0f, state.bold(), state.italic(), state.underline(),
        SketchTextStyle.Alignment.CENTER, SketchTextStyle.DEFAULT_COLOR );
      SketchTextLayoutSnapshot layout = SketchTextLayoutSnapshot.create( value, style );
      float line_height = TEXT_LINE_HEIGHT * scale * state.textScalePercent() / 100.0f;
      float text_half_width = 0.5f * layout.maximumWidth * line_height;
      float text_half_height = Math.max( Math.abs( layout.ascent ), Math.abs( layout.descent ) )
        * line_height;
      radius = Math.max( radius, SpecialPointSizing.BEDDING_TEXT_OFFSET * scale
        + Math.max( text_half_width, text_half_height ) );
    }
    return radius;
  }
}
