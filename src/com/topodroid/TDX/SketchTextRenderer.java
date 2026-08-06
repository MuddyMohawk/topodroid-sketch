/* @file SketchTextRenderer.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Thread-safe layout and rendering helpers for Sketch text objects
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

final class SketchTextRenderer
{
  private SketchTextRenderer() { }

  static Paint newPaint( SketchTextStyle style )
  {
    Paint paint = new Paint();
    configurePaint( paint, style );
    return paint;
  }

  static void configurePaint( Paint paint, SketchTextStyle style )
  {
    if ( paint == null ) return;
    SketchTextStyle resolved_style = ( style == null ) ? SketchTextStyle.defaultStyle() : style;
    SketchFontRegistry.ResolvedFont font =
      SketchFontRegistry.resolve( resolved_style.fontId(), resolved_style.bold(), resolved_style.italic() );
    paint.reset();
    paint.setFlags( Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG );
    paint.setStyle( Paint.Style.FILL );
    paint.setTypeface( font.typeface );
    paint.setFakeBoldText( font.fakeBold );
    paint.setTextSkewX( font.skewX );
    paint.setUnderlineText( resolved_style.underline() );
    paint.setColor( resolved_style.color() );
  }

  static void drawScene( Canvas canvas, Matrix matrix, float cx, float cy, float orientation,
                         SketchTextStyle style, SketchTextLayoutSnapshot layout,
                         float line_height_pixels, int xor_color )
  {
    if ( canvas == null || matrix == null || layout == null || line_height_pixels <= 0.0f ) return;
    float radians = (float)Math.toRadians( orientation );
    float[] points = {
      cx, cy,
      cx + (float)Math.cos( radians ), cy + (float)Math.sin( radians )
    };
    matrix.mapPoints( points );
    float angle = (float)Math.toDegrees( Math.atan2( points[3] - points[1], points[2] - points[0] ) );
    drawAt( canvas, points[0], points[1], angle, style, layout, line_height_pixels, xor_color );
  }

  static void drawAt( Canvas canvas, float anchor_x, float anchor_y, float angle,
                      SketchTextStyle style, SketchTextLayoutSnapshot layout,
                      float line_height_pixels, int xor_color )
  {
    if ( canvas == null || layout == null || line_height_pixels <= 0.0f ) return;
    Paint paint = newPaint( style );
    drawAt( canvas, anchor_x, anchor_y, angle, style, layout, line_height_pixels, xor_color, paint );
  }

  static void drawAt( Canvas canvas, float anchor_x, float anchor_y, float angle,
                      SketchTextStyle style, SketchTextLayoutSnapshot layout,
                      float line_height_pixels, int xor_color, Paint paint )
  {
    if ( canvas == null || layout == null || paint == null || line_height_pixels <= 0.0f ) return;
    paint.setTextSize( layout.paintSizePerLineHeight * line_height_pixels );
    int color = style == null ? SketchTextStyle.DEFAULT_COLOR : style.color();
    paint.setColor( xor_color > 0 ? BrushManager.xorColor( color ) : color );

    int save = canvas.save();
    try {
      canvas.translate( anchor_x, anchor_y );
      canvas.rotate( angle );
      for ( int i = 0; i < layout.lines.length; ++i ) {
        float x = layout.lineOffset( i ) * line_height_pixels;
        float y = i * line_height_pixels;
        canvas.drawText( layout.lines[i], x, y, paint );
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  static RectF localBounds( SketchTextLayoutSnapshot layout, float line_height )
  {
    if ( layout == null || ! ( line_height > 0.0f ) ) return new RectF();
    return new RectF(
      layout.left() * line_height,
      layout.top() * line_height,
      layout.right() * line_height,
      layout.bottom() * line_height
    );
  }

  static float footprintScale( SketchTextStyle style )
  {
    return ( style == null ) ? 1.0f : SketchBrushRenderer.footprintScale( style.lineWeight() );
  }

  static RectF rotatedBounds( float cx, float cy, float orientation, RectF local )
  {
    if ( local == null ) return new RectF( cx, cy, cx, cy );
    float radians = (float)Math.toRadians( orientation );
    float cosine = (float)Math.cos( radians );
    float sine = (float)Math.sin( radians );
    float[] corners = {
      local.left, local.top,
      local.right, local.top,
      local.right, local.bottom,
      local.left, local.bottom
    };
    RectF bounds = new RectF( Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE );
    for ( int i = 0; i < corners.length; i += 2 ) {
      float x = cx + corners[i] * cosine - corners[i + 1] * sine;
      float y = cy + corners[i] * sine + corners[i + 1] * cosine;
      if ( x < bounds.left ) bounds.left = x;
      if ( x > bounds.right ) bounds.right = x;
      if ( y < bounds.top ) bounds.top = y;
      if ( y > bounds.bottom ) bounds.bottom = y;
    }
    return bounds;
  }

  static boolean hitTest( float x, float y, float cx, float cy, float orientation,
                          RectF local, float touch_slop )
  {
    if ( local == null ) return false;
    float radians = (float)Math.toRadians( -orientation );
    float cosine = (float)Math.cos( radians );
    float sine = (float)Math.sin( radians );
    float dx = x - cx;
    float dy = y - cy;
    float local_x = dx * cosine - dy * sine;
    float local_y = dx * sine + dy * cosine;
    return local_x >= local.left - touch_slop
        && local_x <= local.right + touch_slop
        && local_y >= local.top - touch_slop
        && local_y <= local.bottom + touch_slop;
  }
}
