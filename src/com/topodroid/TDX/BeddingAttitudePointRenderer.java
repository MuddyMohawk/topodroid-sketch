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
    Paint ink = xor_color > 0 ? DrawingPath.xorPaint( source, xor_color ) : new Paint( source );
    ink.setStyle( Paint.Style.STROKE );
    ink.setStrokeCap( Paint.Cap.ROUND );
    ink.setStrokeJoin( Paint.Join.ROUND );

    float scale = Math.max( 0.01f, point.specialPointScale()
      * SpecialPointSizing.BEDDING_ATTITUDE_SCALE );
    if ( state.viewKind == BeddingAttitudePointState.ViewKind.PLAN ) {
      drawPlan( point, canvas, ink, state, attitude, scale );
    } else if ( state.traceValid && Double.isFinite( state.canvasTraceAngleDegrees ) ) {
      drawProfile( point, canvas, ink, state, attitude, scale );
    } else {
      drawUnavailableProfile( point, canvas, ink, scale );
    }
  }

  private void drawPlan( DrawingSemanticPointPath point, Canvas canvas, Paint ink,
                         BeddingAttitudePointState state, BeddingAttitude attitude, float scale )
  {
    if ( attitude.kind == BeddingAttitude.Kind.HORIZONTAL ) {
      float radius = HORIZONTAL_RADIUS * scale;
      canvas.drawCircle( point.cx, point.cy, radius, ink );
      canvas.drawLine( point.cx - radius, point.cy, point.cx + radius, point.cy, ink );
      canvas.drawLine( point.cx, point.cy - radius, point.cx, point.cy + radius, ink );
      return;
    }

    double strike = Math.toRadians( attitude.strikeRhrDegrees );
    float sx = (float)Math.sin( strike );
    float sy = (float)-Math.cos( strike );
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( point.cx - sx * half, point.cy - sy * half,
                     point.cx + sx * half, point.cy + sy * half, ink );

    float dx;
    float dy;
    if ( attitude.kind == BeddingAttitude.Kind.VERTICAL ) {
      dx = -sy;
      dy = sx;
      float tick = 0.5f * SpecialPointSizing.BEDDING_DIP_TICK_LENGTH * scale;
      canvas.drawLine( point.cx - dx * tick, point.cy - dy * tick,
                       point.cx + dx * tick, point.cy + dy * tick, ink );
      return;
    } else {
      double direction = Math.toRadians( attitude.dipDirectionDegrees );
      dx = (float)Math.sin( direction );
      dy = (float)-Math.cos( direction );
      float tick = SpecialPointSizing.BEDDING_DIP_TICK_LENGTH * scale;
      canvas.drawLine( point.cx, point.cy, point.cx + dx * tick, point.cy + dy * tick, ink );
    }
    drawDipText( point, canvas, ink, state, attitude, dx, dy, scale, false );
  }

  private void drawProfile( DrawingSemanticPointPath point, Canvas canvas, Paint ink,
                            BeddingAttitudePointState state, BeddingAttitude attitude, float scale )
  {
    double angle = Math.toRadians( state.canvasTraceAngleDegrees );
    float dx = (float)Math.cos( angle );
    float dy = (float)Math.sin( angle );
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( point.cx - dx * half, point.cy - dy * half,
                     point.cx + dx * half, point.cy + dy * half, ink );

    float side_x = -dy;
    float side_y = dx;
    if ( side_y < 0.0f ) { side_x = -side_x; side_y = -side_y; }
    drawDipText( point, canvas, ink, state, attitude, side_x, side_y, scale, true );
  }

  private void drawUnavailableProfile( DrawingSemanticPointPath point, Canvas canvas,
                                       Paint ink, float scale )
  {
    float half = SpecialPointSizing.BEDDING_STRIKE_HALF_LENGTH * scale;
    canvas.drawLine( point.cx - half, point.cy, point.cx + half, point.cy, ink );
    float mark = 2.5f * scale;
    canvas.drawLine( point.cx - mark, point.cy - mark, point.cx + mark, point.cy + mark, ink );
    canvas.drawLine( point.cx - mark, point.cy + mark, point.cx + mark, point.cy - mark, ink );
  }

  private void drawDipText( DrawingSemanticPointPath point, Canvas canvas, Paint ink,
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
    float anchor_x = point.cx + side_x * offset;
    float anchor_y = point.cy + side_y * offset
      - 0.5f * ( layout.ascent + layout.descent ) * line_height;
    SketchTextRenderer.drawAt( canvas, anchor_x, anchor_y, 0.0f, style, layout, line_height, 0 );
  }

  @Override public RectF sceneBounds( DrawingSemanticPointPath point )
  {
    float scale = Math.max( 0.01f, point.specialPointScale()
      * SpecialPointSizing.BEDDING_ATTITUDE_SCALE );
    float radius = ( SpecialPointSizing.BEDDING_TEXT_OFFSET + 10.0f ) * scale;
    Paint paint = point.specialPointPaint();
    if ( paint != null ) radius += 0.5f * Math.abs( paint.getStrokeWidth() );
    return new RectF( point.cx - radius, point.cy - radius,
                      point.cx + radius, point.cy + radius );
  }
}
