/* @file FramedTextPointRenderer.java
 *
 * @brief Reusable oval/rectangle renderer for special points with centered values
 */
package com.topodroid.TDX;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class FramedTextPointRenderer implements SpecialPointRenderer
{
  enum FrameShape { OVAL, RECTANGLE }
  enum SingleRowPolicy { NATURAL, EQUAL_SIDES, WIDEN_FROM_SQUARE }

  private static final float MIN_WIDTH = 12.0f;
  private static final float SINGLE_MIN_HEIGHT = 16.0f;
  private static final float MULTI_MIN_HEIGHT = 30.0f;
  private static final float TEXT_LINE_HEIGHT = 7.0f;
  private static final float HORIZONTAL_PADDING = 1.25f;
  private static final float VERTICAL_PADDING = 2.5f;
  private static final float ROW_CENTER = 5.5f;
  private static final float WAVE_HALF_WIDTH = 4.0f;

  private final FrameShape mShape;
  private final float mBaseScale;
  private final SingleRowPolicy mSingleRowPolicy;

  FramedTextPointRenderer( FrameShape shape )
  {
    this( shape, 1.0f, SingleRowPolicy.NATURAL );
  }

  FramedTextPointRenderer( FrameShape shape, float base_scale )
  {
    this( shape, base_scale, SingleRowPolicy.NATURAL );
  }

  FramedTextPointRenderer( FrameShape shape, float base_scale, SingleRowPolicy single_row_policy )
  {
    mShape = ( shape == null ) ? FrameShape.OVAL : shape;
    mBaseScale = ( base_scale > 0.0f && Float.isFinite( base_scale ) ) ? base_scale : 1.0f;
    mSingleRowPolicy = ( single_row_policy == null ) ? SingleRowPolicy.NATURAL : single_row_policy;
  }

  @Override public void draw( DrawingSemanticPointPath point, Canvas canvas, int xor_color )
  {
    Layout layout = layout( point );
    if ( layout == null ) return;
    Paint source = point.specialPointPaint();
    if ( source == null ) return;
    Paint ink = ( xor_color > 0 ) ? DrawingPath.xorPaint( source, xor_color ) : new Paint( source );
    ink.setStyle( Paint.Style.STROKE );
    ink.setStrokeJoin( Paint.Join.ROUND );
    ink.setStrokeCap( Paint.Cap.ROUND );

    RectF frame = new RectF( point.cx - layout.halfWidth, point.cy - layout.halfHeight,
                             point.cx + layout.halfWidth, point.cy + layout.halfHeight );
    if ( mShape == FrameShape.RECTANGLE ) {
      canvas.drawRect( frame, ink );
    } else {
      canvas.drawOval( frame, ink );
    }

    if ( layout.state.separator() == FramedTextPointState.Separator.WAVE ) {
      float wave = WAVE_HALF_WIDTH * layout.pointScale;
      float amplitude = 1.0f * layout.pointScale;
      Path path = new Path();
      path.moveTo( point.cx - wave, point.cy );
      path.cubicTo( point.cx - 0.67f * wave, point.cy - amplitude,
                    point.cx - 0.33f * wave, point.cy - amplitude,
                    point.cx, point.cy );
      path.cubicTo( point.cx + 0.33f * wave, point.cy + amplitude,
                    point.cx + 0.67f * wave, point.cy + amplitude,
                    point.cx + wave, point.cy );
      canvas.drawPath( path, ink );
    }

    int color = ink.getColor();
    for ( int i = 0; i < layout.rows.length; ++i ) {
      SketchTextLayoutSnapshot text_layout = layout.textLayouts[i];
      Paint text = SketchTextRenderer.newPaint( layout.textStyle );
      text.setColor( color );
      text.setTextSize( text_layout.paintSizePerLineHeight * layout.lineHeight );
      // Carry the active sketch line weight into the glyph ink as well as the frame. The
      // modest outline preserves the selected font and emphasis while keeping thin/standard/
      // thick symbols visually consistent with walls and user lines of the same weight.
      text.setStyle( Paint.Style.FILL_AND_STROKE );
      text.setStrokeWidth( 0.35f * Math.abs( ink.getStrokeWidth() ) );
      text.setStrokeJoin( Paint.Join.ROUND );
      float x = point.cx - 0.5f * text_layout.lineWidths[0] * layout.lineHeight;
      float baseline = point.cy + layout.rowCenters[i]
        - 0.5f * ( text_layout.ascent + text_layout.descent ) * layout.lineHeight;
      canvas.drawText( layout.rows[i], x, baseline, text );
    }
  }

  @Override public RectF sceneBounds( DrawingSemanticPointPath point )
  {
    Layout layout = layout( point );
    if ( layout == null ) return new RectF( point.cx, point.cy, point.cx + 1.0f, point.cy + 1.0f );
    Paint paint = point.specialPointPaint();
    float stroke = ( paint == null ) ? 0.0f : Math.abs( paint.getStrokeWidth() );
    float inset = 0.5f * stroke;
    return new RectF( point.cx - layout.halfWidth - inset, point.cy - layout.halfHeight - inset,
                      point.cx + layout.halfWidth + inset, point.cy + layout.halfHeight + inset );
  }

  private Layout layout( DrawingSemanticPointPath point )
  {
    if ( point == null || ! ( point.specialState() instanceof FramedTextPointState ) ) return null;
    FramedTextPointState state = (FramedTextPointState)point.specialState();
    String[] rows = state.displayRows( point.getPointText() );
    if ( rows == null || rows.length == 0 ) rows = new String[] { "" };
    for ( int i = 0; i < rows.length; ++i ) if ( rows[i] == null ) rows[i] = "";

    Paint point_paint = point.specialPointPaint();
    int color = ( point_paint == null ) ? SketchTextStyle.DEFAULT_COLOR : point_paint.getColor();
    SketchTextStyle style = SketchTextStyle.of(
      state.fontId(), SketchTextStyle.SizeMode.AUTO_GRID, 1.0f,
      state.bold(), state.italic(), state.underline(), SketchTextStyle.Alignment.CENTER, color );

    float point_scale = Math.max( 0.01f, point.specialPointScale() * mBaseScale );
    float text_scale = Math.max( 50, Math.min( 200, state.textScalePercent() ) ) / 100.0f;
    float line_height = TEXT_LINE_HEIGHT * point_scale * text_scale;
    float[] centers = new float[ rows.length ];
    if ( rows.length == 1 ) {
      centers[0] = 0.0f;
    } else {
      float spacing = 2.0f * ROW_CENTER * point_scale / Math.max( 1, rows.length - 1 );
      for ( int i = 0; i < rows.length; ++i ) centers[i] = -ROW_CENTER * point_scale + i * spacing;
    }

    SketchTextLayoutSnapshot[] layouts = new SketchTextLayoutSnapshot[ rows.length ];
    float half_width = 0.5f * MIN_WIDTH * point_scale;
    float content_top = Float.POSITIVE_INFINITY;
    float content_bottom = Float.NEGATIVE_INFINITY;
    for ( int i = 0; i < rows.length; ++i ) {
      layouts[i] = SketchTextLayoutSnapshot.create( rows[i], style );
      half_width = Math.max( half_width,
        0.5f * layouts[i].maximumWidth * line_height + HORIZONTAL_PADDING * point_scale );
      float baseline = centers[i] - 0.5f * ( layouts[i].ascent + layouts[i].descent ) * line_height;
      content_top = Math.min( content_top, baseline + layouts[i].ascent * line_height );
      content_bottom = Math.max( content_bottom, baseline + layouts[i].descent * line_height );
    }
    float min_half_height = 0.5f * ( rows.length > 1 ? MULTI_MIN_HEIGHT : SINGLE_MIN_HEIGHT ) * point_scale;
    float half_height = Math.max( min_half_height,
      Math.max( -content_top, content_bottom ) + VERTICAL_PADDING * point_scale );
    if ( rows.length == 1 ) {
      if ( mSingleRowPolicy == SingleRowPolicy.EQUAL_SIDES ) {
        // Circular framed values grow equally in both directions for unusually long text.
        float radius = Math.max( half_width, half_height );
        half_width = radius;
        half_height = radius;
      } else if ( mSingleRowPolicy == SingleRowPolicy.WIDEN_FROM_SQUARE ) {
        // Boxed values begin square but can widen into a rectangle for free-form annotations.
        half_width = Math.max( half_width, half_height );
      }
    }
    return new Layout( state, rows, layouts, style, centers, point_scale, line_height, half_width, half_height );
  }

  private static final class Layout
  {
    final FramedTextPointState state;
    final String[] rows;
    final SketchTextLayoutSnapshot[] textLayouts;
    final SketchTextStyle textStyle;
    final float[] rowCenters;
    final float pointScale;
    final float lineHeight;
    final float halfWidth;
    final float halfHeight;

    Layout( FramedTextPointState special_state, String[] display_rows,
            SketchTextLayoutSnapshot[] text_layouts, SketchTextStyle text_style,
            float[] row_centers, float point_scale, float line_height,
            float half_width, float half_height )
    {
      state = special_state;
      rows = display_rows;
      textLayouts = text_layouts;
      textStyle = text_style;
      rowCenters = row_centers;
      pointScale = point_scale;
      lineHeight = line_height;
      halfWidth = half_width;
      halfHeight = half_height;
    }
  }
}
