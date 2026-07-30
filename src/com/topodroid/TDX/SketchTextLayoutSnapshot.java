/* @file SketchTextLayoutSnapshot.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Immutable normalized layout metrics for a Sketch text object
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.graphics.Paint;

final class SketchTextLayoutSnapshot
{
  private static final float REFERENCE_TEXT_SIZE = 1000.0f;

  final String text;
  final SketchTextStyle style;
  final int fontGeneration;
  final String[] lines;
  final float[] lineWidths;
  final float maximumWidth;
  final float ascent;
  final float descent;
  final float paintSizePerLineHeight;

  private SketchTextLayoutSnapshot( String source, SketchTextStyle text_style, int font_generation,
                                    String[] text_lines, float[] widths, float maximum_width,
                                    float normalized_ascent, float normalized_descent,
                                    float paint_size_per_line_height )
  {
    text = source;
    style = text_style;
    fontGeneration = font_generation;
    lines = text_lines;
    lineWidths = widths;
    maximumWidth = maximum_width;
    ascent = normalized_ascent;
    descent = normalized_descent;
    paintSizePerLineHeight = paint_size_per_line_height;
  }

  static SketchTextLayoutSnapshot create( String text, SketchTextStyle style )
  {
    String source = ( text == null ) ? "" : text;
    SketchTextStyle resolved_style = ( style == null ) ? SketchTextStyle.defaultStyle() : style;
    String[] lines = source.split( "\\n", -1 );
    if ( lines.length == 0 ) lines = new String[] { "" };

    Paint paint = SketchTextRenderer.newPaint( resolved_style );
    paint.setTextSize( REFERENCE_TEXT_SIZE );
    float spacing = paint.getFontSpacing();
    if ( ! ( spacing > 0.0f ) || Float.isNaN( spacing ) || Float.isInfinite( spacing ) ) spacing = REFERENCE_TEXT_SIZE;
    float inverse_spacing = 1.0f / spacing;

    float[] widths = new float[ lines.length ];
    float maximum = 0.0f;
    for ( int i = 0; i < lines.length; ++i ) {
      widths[i] = paint.measureText( lines[i] ) * inverse_spacing;
      if ( widths[i] > maximum ) maximum = widths[i];
    }

    Paint.FontMetrics metrics = paint.getFontMetrics();
    return new SketchTextLayoutSnapshot(
      source,
      resolved_style,
      SketchFontRegistry.generation(),
      lines,
      widths,
      maximum,
      metrics.ascent * inverse_spacing,
      metrics.descent * inverse_spacing,
      REFERENCE_TEXT_SIZE * inverse_spacing
    );
  }

  boolean matches( String source, SketchTextStyle text_style )
  {
    String normalized = ( source == null ) ? "" : source;
    SketchTextStyle resolved_style = ( text_style == null ) ? SketchTextStyle.defaultStyle() : text_style;
    return fontGeneration == SketchFontRegistry.generation()
        && text.equals( normalized )
        && style.equals( resolved_style );
  }

  float lineOffset( int index )
  {
    float width = lineWidths[index];
    if ( style.alignment() == SketchTextStyle.Alignment.CENTER ) return -0.5f * width;
    if ( style.alignment() == SketchTextStyle.Alignment.RIGHT ) return -width;
    return 0.0f;
  }

  float left()
  {
    if ( style.alignment() == SketchTextStyle.Alignment.CENTER ) return -0.5f * maximumWidth;
    if ( style.alignment() == SketchTextStyle.Alignment.RIGHT ) return -maximumWidth;
    return 0.0f;
  }

  float right()
  {
    if ( style.alignment() == SketchTextStyle.Alignment.CENTER ) return 0.5f * maximumWidth;
    if ( style.alignment() == SketchTextStyle.Alignment.RIGHT ) return 0.0f;
    return maximumWidth;
  }

  float top() { return ascent; }
  float bottom() { return Math.max( 0, lines.length - 1 ) + descent; }
}
