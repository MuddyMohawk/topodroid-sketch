/* @file TitleLegendLayout.java
 *
 * @brief Immutable, measured and draw-ready Title Sheet/Legend layout
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TitleLegendLayout
{
  static final float MAX_LABEL_WIDTH_LINES = 18.0f;

  static final class PreparedRow
  {
    final TitleLegendPointState.Row state;
    final String displayLabel;
    final SketchTextLayoutSnapshot text;
    final SymbolSwatchSnapshot swatch;
    final RectF box;
    final float labelX;
    final float labelBaseline;
    final boolean unresolved;

    PreparedRow( TitleLegendPointState.Row state, String label, SketchTextLayoutSnapshot text,
                 SymbolSwatchSnapshot swatch, RectF box, float label_x,
                 float label_baseline, boolean unresolved )
    {
      this.state = state;
      displayLabel = label;
      this.text = text;
      this.swatch = swatch;
      this.box = new RectF( box );
      labelX = label_x;
      labelBaseline = label_baseline;
      this.unresolved = unresolved;
    }
  }

  final String objectId;
  final SketchTextStyle textStyle;
  final SketchTextStyle headingStyle;
  final SketchTextLayoutSnapshot heading;
  final float headingX;
  final float headingBaseline;
  final float lineHeight;
  final RectF localBounds;
  final List< PreparedRow > rows;
  final TitleLegendPointState.Capacity capacity;

  private TitleLegendLayout( TitleLegendPointState state, SketchTextStyle heading_style,
                             SketchTextLayoutSnapshot heading, float heading_x,
                             float heading_baseline, float line_height, RectF local_bounds,
                             List< PreparedRow > rows )
  {
    objectId = state.objectId;
    textStyle = state.textStyle;
    headingStyle = heading_style;
    this.heading = heading;
    headingX = heading_x;
    headingBaseline = heading_baseline;
    lineHeight = line_height;
    localBounds = new RectF( local_bounds );
    this.rows = Collections.unmodifiableList( new ArrayList<>( rows ) );
    capacity = state.capacity();
  }

  static TitleLegendLayout prepare( DrawingSemanticPointPath point, TitleLegendPointState state )
  {
    TitleLegendPointState.SymbolResolver resolver = new InstalledSymbolResolver();
    float point_scale = point == null ? 1.0f : Math.max( 0.1f, point.specialPointScale() );
    point_scale *= state.legendScale;
    float line_height = worldLineHeightScene( state.textStyle ) * point_scale;
    line_height = Math.max( 1.0f, line_height );

    SketchTextStyle heading_style = state.textStyle.withSize( state.textStyle.sizeMode(),
      state.textStyle.height() * 1.15f ).withEmphasis( true, state.textStyle.italic(), state.textStyle.underline() );
    String heading_text = "Legend";
    try {
      if ( TDInstance.context != null ) heading_text = TDInstance.getResourceString( R.string.title_legend_heading );
    } catch ( RuntimeException e ) {
      // Resource access can be unavailable in local model tests; English is the source-locale fallback.
    }
    SketchTextLayoutSnapshot heading = SketchTextLayoutSnapshot.create( heading_text, heading_style );
    float heading_line_height = line_height * 1.15f;
    RectF heading_bounds = SketchTextRenderer.localBounds( heading, heading_line_height );

    float padding = 0.40f * line_height;
    float heading_x = padding - heading_bounds.left;
    float heading_baseline = padding - heading_bounds.top;
    float rows_top = heading_baseline + heading_bounds.bottom + 0.60f * line_height;
    float box_width = 3.8f * line_height;
    float box_height = 2.05f * line_height;
    float box_gap = 0.60f * line_height;
    float row_gap = 0.30f * line_height;
    float column_gap = 1.1f * line_height;

    ArrayList< RowMeasure > measured = new ArrayList<>();
    for ( TitleLegendPointState.Row row : state.rows ) {
      String label = row.displayLabel( resolver );
      String wrapped = wrap( label, state.textStyle, MAX_LABEL_WIDTH_LINES );
      SketchTextLayoutSnapshot text = SketchTextLayoutSnapshot.create( wrapped, state.textStyle );
      float text_height = ( text.bottom() - text.top() ) * line_height;
      float row_height = Math.max( box_height, text_height ) + row_gap;
      float label_width = Math.min( MAX_LABEL_WIDTH_LINES, text.maximumWidth ) * line_height;
      SymbolSwatchSnapshot swatch = SymbolSwatchSnapshot.create( row, resolver );
      measured.add( new RowMeasure( row, label, text, swatch, row_height, text_height, label_width,
                                    row.kind != TitleLegendPointState.Kind.CUSTOM && swatch == null ) );
    }

    TitleLegendPointState.Capacity capacity = state.capacity();
    float x = padding;
    float maximum_bottom = rows_top;
    int row_index = 0;
    ArrayList< PreparedRow > prepared = new ArrayList<>();
    for ( int column = 0; column < capacity.renderedColumns; ++column ) {
      int count = capacity.rowsInColumn( column );
      float column_label_width = 0.0f;
      for ( int i = 0; i < count; ++i ) {
        column_label_width = Math.max( column_label_width, measured.get( row_index + i ).labelWidth );
      }
      float y = rows_top;
      for ( int i = 0; i < count; ++i ) {
        RowMeasure item = measured.get( row_index++ );
        float content_height = item.rowHeight - row_gap;
        float box_top = y + ( content_height - box_height ) * 0.5f;
        RectF box = new RectF( x, box_top, x + box_width, box_top + box_height );
        float label_left = box.right + box_gap;
        float label_x = label_left;
        if ( state.textStyle.alignment() == SketchTextStyle.Alignment.CENTER ) {
          label_x += 0.5f * column_label_width;
        } else if ( state.textStyle.alignment() == SketchTextStyle.Alignment.RIGHT ) {
          label_x += column_label_width;
        }
        float label_baseline = y + ( content_height - item.textHeight ) * 0.5f
          - item.text.top() * line_height;
        prepared.add( new PreparedRow( item.state, item.displayLabel, item.text, item.swatch,
                                       box, label_x, label_baseline, item.unresolved ) );
        y += item.rowHeight;
      }
      maximum_bottom = Math.max( maximum_bottom, y );
      x += box_width + box_gap + column_label_width;
      if ( column + 1 < capacity.renderedColumns ) x += column_gap;
    }
    float width = capacity.renderedColumns == 0
      ? Math.max( heading_bounds.width() + 2.0f * padding, 8.0f * line_height )
      : x + padding;
    width = Math.max( width, heading_bounds.width() + 2.0f * padding );
    float height = Math.max( maximum_bottom + padding,
                             heading_baseline + heading_bounds.bottom + padding );
    return new TitleLegendLayout( state, heading_style, heading, heading_x, heading_baseline,
                                  line_height, new RectF( 0.0f, 0.0f, width, height ), prepared );
  }

  private static float worldLineHeightScene( SketchTextStyle style )
  {
    if ( style == null ) style = SketchTextStyle.defaultStyle();
    float metres;
    if ( style.sizeMode() == SketchTextStyle.SizeMode.AUTO_GRID ) {
      metres = TDSetting.mUnitGrid * style.height();
    } else if ( style.sizeMode() == SketchTextStyle.SizeMode.WORLD ) {
      metres = style.height();
    } else {
      // A composite map object must have stable world geometry; use one grid unit
      // when a legacy SCREEN style reaches it.
      metres = TDSetting.mUnitGrid * Math.max( 0.25f, style.height() / SketchTextStyle.DEFAULT_SCREEN_HEIGHT );
    }
    return metres * DrawingUtil.SCALE_FIX * SketchTextRenderer.footprintScale( style );
  }

  private static String wrap( String source, SketchTextStyle style, float maximum_width )
  {
    String normalized = source == null ? "" : source.replace( "\r\n", "\n" ).replace( '\r', '\n' );
    Paint paint = SketchTextRenderer.newPaint( style );
    paint.setTextSize( 1000.0f );
    float spacing = paint.getFontSpacing();
    if ( ! ( spacing > 0.0f ) ) spacing = 1000.0f;
    float pixel_limit = maximum_width * spacing;
    StringBuilder output = new StringBuilder();
    String[] paragraphs = normalized.split( "\n", -1 );
    for ( int paragraph_index = 0; paragraph_index < paragraphs.length; ++paragraph_index ) {
      if ( paragraph_index > 0 ) output.append( '\n' );
      String paragraph = paragraphs[ paragraph_index ];
      if ( paragraph.length() == 0 ) continue;
      StringBuilder line = new StringBuilder();
      String[] words = paragraph.trim().split( "\\s+" );
      for ( String word : words ) {
        if ( word.length() == 0 ) continue;
        String candidate = line.length() == 0 ? word : line.toString() + " " + word;
        if ( paint.measureText( candidate ) <= pixel_limit ) {
          line.setLength( 0 );
          line.append( candidate );
          continue;
        }
        if ( line.length() > 0 ) {
          output.append( line ).append( '\n' );
          line.setLength( 0 );
        }
        appendBrokenWord( output, line, word, paint, pixel_limit );
      }
      output.append( line );
    }
    return output.toString();
  }

  private static void appendBrokenWord( StringBuilder output, StringBuilder line, String word,
                                        Paint paint, float pixel_limit )
  {
    int offset = 0;
    while ( offset < word.length() ) {
      int next = offset;
      int best = offset;
      while ( next < word.length() ) {
        next += Character.charCount( word.codePointAt( next ) );
        if ( paint.measureText( word, offset, next ) <= pixel_limit ) best = next;
        else break;
      }
      if ( best == offset ) best = offset + Character.charCount( word.codePointAt( offset ) );
      String part = word.substring( offset, best );
      if ( best < word.length() ) output.append( part ).append( '\n' );
      else line.append( part );
      offset = best;
    }
  }

  private static final class RowMeasure
  {
    final TitleLegendPointState.Row state;
    final String displayLabel;
    final SketchTextLayoutSnapshot text;
    final SymbolSwatchSnapshot swatch;
    final float rowHeight;
    final float textHeight;
    final float labelWidth;
    final boolean unresolved;

    RowMeasure( TitleLegendPointState.Row state, String display_label,
                SketchTextLayoutSnapshot text, SymbolSwatchSnapshot swatch,
                float row_height, float text_height, float label_width, boolean unresolved )
    {
      this.state = state;
      displayLabel = display_label;
      this.text = text;
      this.swatch = swatch;
      rowHeight = row_height;
      textHeight = text_height;
      labelWidth = label_width;
      this.unresolved = unresolved;
    }
  }

  static final class InstalledSymbolResolver implements TitleLegendPointState.SymbolResolver
  {
    @Override public String displayName( TitleLegendPointState.Kind kind, String th_name )
    {
      int index = libraryIndex( kind, th_name );
      if ( index < 0 ) return null;
      if ( kind == TitleLegendPointState.Kind.LINE ) return BrushManager.getLineName( index );
      if ( kind == TitleLegendPointState.Kind.AREA ) return BrushManager.getAreaName( index );
      return BrushManager.getPointName( index );
    }

    @Override public int libraryIndex( TitleLegendPointState.Kind kind, String th_name )
    {
      if ( th_name == null ) return -1;
      if ( kind == TitleLegendPointState.Kind.LINE ) return BrushManager.getLineIndexByThName( th_name );
      if ( kind == TitleLegendPointState.Kind.AREA ) return BrushManager.getAreaIndexByThName( th_name );
      if ( kind == TitleLegendPointState.Kind.POINT ) return BrushManager.getPointIndexByThName( th_name );
      return -1;
    }
  }
}
