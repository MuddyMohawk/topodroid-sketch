/* @file TitleLegendPointRenderer.java
 *
 * @brief Canvas renderer for the Title Sheet and Legend composite point
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

final class TitleLegendPointRenderer implements SpecialPointRenderer
{
  private static final ThreadLocal< Scratch > SCRATCH = new ThreadLocal< Scratch >() {
    @Override protected Scratch initialValue() { return new Scratch(); }
  };

  private static final class Scratch
  {
    final Paint boxPaint = new Paint( Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG );
    final Paint headingPaint = new Paint();
    final Paint textPaint = new Paint();
    final RectF swatch = new RectF();
  }

  @Override public void draw( DrawingSemanticPointPath point, Canvas canvas, int xor_color )
  {
    if ( point.specialState() instanceof TitleLegendPointBehavior.NewerState ) {
      drawNewerMarker( point, canvas, xor_color );
      return;
    }
    TitleLegendLayout layout = layout( point );
    if ( layout == null ) return;
    Scratch scratch = SCRATCH.get();
    float ox = point.cx;
    float oy = point.cy;
    Paint box_paint = scratch.boxPaint;
    box_paint.setStyle( Paint.Style.STROKE );
    box_paint.setStrokeWidth( Math.max( 0.5f, TDSetting.inkUnit() ) );
    int color = layout.textStyle.color();
    if ( xor_color > 0 ) color = BrushManager.xorColor( color );
    box_paint.setColor( color );

    SketchTextRenderer.configurePaint( scratch.headingPaint, layout.headingStyle );
    SketchTextRenderer.drawAt( canvas, ox + layout.headingX, oy + layout.headingBaseline, 0.0f,
      layout.headingStyle, layout.heading, layout.lineHeight * 1.15f, xor_color, scratch.headingPaint );
    SketchTextRenderer.configurePaint( scratch.textPaint, layout.textStyle );
    for ( TitleLegendLayout.PreparedRow row : layout.rows ) {
      RectF box = row.box;
      float left = ox + box.left;
      float top = oy + box.top;
      float right = ox + box.right;
      float bottom = oy + box.bottom;
      canvas.drawRect( left, top, right, bottom, box_paint );
      RectF swatch_rect = scratch.swatch;
      swatch_rect.set( left, top, right, bottom );
      float inset = 0.16f * layout.lineHeight;
      swatch_rect.inset( inset, inset );
      if ( row.swatch != null ) {
        row.swatch.draw( canvas, swatch_rect, xor_color );
      } else if ( row.unresolved ) {
        canvas.drawLine( swatch_rect.left, swatch_rect.top, swatch_rect.right, swatch_rect.bottom, box_paint );
        canvas.drawLine( swatch_rect.right, swatch_rect.top, swatch_rect.left, swatch_rect.bottom, box_paint );
      }
      SketchTextRenderer.drawAt( canvas, ox + row.labelX, oy + row.labelBaseline, 0.0f,
        layout.textStyle, row.text, layout.lineHeight, xor_color, scratch.textPaint );
    }
  }

  @Override public RectF sceneBounds( DrawingSemanticPointPath point )
  {
    if ( point.specialState() instanceof TitleLegendPointBehavior.NewerState ) {
      return new RectF( point.cx, point.cy, point.cx + 92.0f, point.cy + 34.0f );
    }
    TitleLegendLayout layout = layout( point );
    if ( layout == null ) return new RectF( point.cx, point.cy, point.cx + 1.0f, point.cy + 1.0f );
    return new RectF( point.cx + layout.localBounds.left, point.cy + layout.localBounds.top,
                      point.cx + layout.localBounds.right, point.cy + layout.localBounds.bottom );
  }

  private static void drawNewerMarker( DrawingSemanticPointPath point, Canvas canvas, int xor_color )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeWidth( Math.max( 0.5f, TDSetting.inkUnit() ) );
    int color = xor_color > 0 ? BrushManager.xorColor( 0xffffb65c ) : 0xffffb65c;
    paint.setColor( color );
    canvas.drawRect( point.cx, point.cy, point.cx + 92.0f, point.cy + 34.0f, paint );
    canvas.drawLine( point.cx, point.cy, point.cx + 92.0f, point.cy + 34.0f, paint );
    paint.setStyle( Paint.Style.FILL );
    paint.setTextSize( 9.0f );
    canvas.drawText( "Legend: newer version", point.cx + 4.0f, point.cy + 30.0f, paint );
  }

  private static TitleLegendLayout layout( DrawingSemanticPointPath point )
  {
    Object prepared = point.preparedSpecialState();
    return prepared instanceof TitleLegendLayout ? (TitleLegendLayout)prepared : null;
  }
}
