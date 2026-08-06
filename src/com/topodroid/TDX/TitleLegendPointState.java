/* @file TitleLegendPointState.java
 *
 * @brief Immutable presentation state for the Title Sheet and Legend point
 */
package com.topodroid.TDX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TitleLegendPointState implements SpecialPointState
{
  static final int MAX_ROWS = 120;
  static final int MAX_TOMBSTONES = 240;
  static final int MAX_TEXT_CODEPOINTS = 256;
  static final int MAX_ID_CODEPOINTS = 64;
  static final int MIN_SHAPE = 1;
  static final int MAX_SHAPE = 120;
  static final float MIN_LEGEND_SCALE = 0.50f;
  static final float MAX_LEGEND_SCALE = 2.00f;

  enum Kind { POINT, LINE, AREA, CUSTOM }
  enum WeightMode { STANDARD, CUSTOM }

  static final class Preview
  {
    final WeightMode weightMode;
    final float customWeight;
    final float pointScale;
    final float orientation;
    final String textValue;
    final JSONObject specialPayload;

    Preview( WeightMode weight_mode, float custom_weight, float point_scale,
             float orientation, String text_value, JSONObject special_payload )
    {
      weightMode = weight_mode == null ? WeightMode.STANDARD : weight_mode;
      customWeight = finiteInRange( custom_weight, 0.05f, 20.0f,
                                    SketchBrushStyle.DEFAULT_WEIGHT_STANDARD );
      pointScale = finiteInRange( point_scale, 0.1f, 10.0f, 1.0f );
      this.orientation = normalizeOrientation( orientation );
      textValue = normalizeText( text_value, MAX_TEXT_CODEPOINTS );
      specialPayload = special_payload == null ? null : copyJson( special_payload );
    }

    static Preview standard() { return new Preview( WeightMode.STANDARD, 1.0f, 1.0f, 0.0f, null, null ); }

    Preview withWeight( WeightMode mode, float weight )
    {
      return new Preview( mode, weight, pointScale, orientation, textValue, specialPayload );
    }

    Preview withPointOptions( float scale, float angle, String value )
    {
      return new Preview( weightMode, customWeight, scale, angle, value, specialPayload );
    }
  }

  static final class Row
  {
    final String rowId;
    final Kind kind;
    final String thName;
    final String fallbackLabel;
    final String userLabel;
    final Preview preview;

    Row( String row_id, Kind kind, String th_name, String fallback_label,
         String user_label, Preview preview )
    {
      rowId = normalizeId( row_id );
      this.kind = kind == null ? Kind.CUSTOM : kind;
      thName = this.kind == Kind.CUSTOM ? null : normalizeText( th_name, MAX_TEXT_CODEPOINTS );
      fallbackLabel = normalizeText( fallback_label, MAX_TEXT_CODEPOINTS );
      userLabel = user_label == null ? null : normalizeText( user_label, MAX_TEXT_CODEPOINTS );
      this.preview = preview == null ? Preview.standard() : preview;
      if ( this.kind != Kind.CUSTOM && ( thName == null || thName.length() == 0 ) ) {
        throw new IllegalArgumentException( "Canonical legend rows require a symbol name" );
      }
    }

    static Row custom()
    {
      return new Row( newId(), Kind.CUSTOM, null, "Custom symbol", null, Preview.standard() );
    }

    static Row fromEntry( PlotSymbolUsageSnapshot.Entry entry )
    {
      return new Row( newId(), fromUsageKind( entry.kind ), entry.thName,
                      entry.displayName, null, Preview.standard() );
    }

    String identity()
    {
      return kind == Kind.CUSTOM ? null : kind.name() + ":" + thName;
    }

    String displayLabel( SymbolResolver resolver )
    {
      if ( userLabel != null ) return userLabel;
      String live = resolver == null ? null : resolver.displayName( kind, thName );
      if ( live != null && live.length() > 0 ) return live;
      if ( fallbackLabel != null && fallbackLabel.length() > 0 ) return fallbackLabel;
      return kind == Kind.CUSTOM ? "Custom symbol" : thName;
    }

    Row withLabel( String label )
    {
      return new Row( rowId, kind, thName, fallbackLabel, label, preview );
    }

    Row withPreview( Preview value )
    {
      return new Row( rowId, kind, thName, fallbackLabel, userLabel, value );
    }
  }

  interface SymbolResolver
  {
    String displayName( Kind kind, String th_name );
    int libraryIndex( Kind kind, String th_name );
  }

  static final class Capacity
  {
    final int entryCount;
    final int requestedColumns;
    final int rowsPerColumn;
    final int requiredColumns;
    final int renderedColumns;
    final int shortfall;
    final boolean expanded;

    Capacity( int count, int requested, int rows )
    {
      entryCount = Math.max( 0, count );
      requestedColumns = clamp( requested, MIN_SHAPE, MAX_SHAPE );
      rowsPerColumn = clamp( rows, MIN_SHAPE, MAX_SHAPE );
      requiredColumns = entryCount == 0 ? 0 : ceilDiv( entryCount, rowsPerColumn );
      renderedColumns = entryCount == 0 ? 0
        : Math.max( Math.min( requestedColumns, entryCount ), requiredColumns );
      expanded = renderedColumns > requestedColumns;
      shortfall = Math.max( 0, entryCount - requestedColumns * rowsPerColumn );
    }

    int rowsInColumn( int column )
    {
      if ( column < 0 || column >= renderedColumns ) return 0;
      int base = entryCount / renderedColumns;
      int extra = entryCount % renderedColumns;
      return base + ( column < extra ? 1 : 0 );
    }
  }

  final String objectId;
  final boolean titleSheetEnabled;
  final boolean legendEnabled;
  final int requestedColumns;
  final int rowsPerColumn;
  final float legendScale;
  final SketchTextStyle textStyle;
  final List< Row > rows;
  final Set< String > dismissed;

  TitleLegendPointState( String object_id, boolean title_sheet_enabled, boolean legend_enabled,
                         int requested_columns, int rows_per_column,
                         SketchTextStyle text_style, List< Row > rows, Set< String > dismissed )
  {
    this( object_id, title_sheet_enabled, legend_enabled, requested_columns, rows_per_column,
          1.0f, text_style, rows, dismissed );
  }

  TitleLegendPointState( String object_id, boolean title_sheet_enabled, boolean legend_enabled,
                         int requested_columns, int rows_per_column, float legend_scale,
                         SketchTextStyle text_style, List< Row > rows, Set< String > dismissed )
  {
    objectId = normalizeId( object_id );
    titleSheetEnabled = false; // Reserved until the title sheet has renderable v1 content.
    legendEnabled = true;      // V1 must never create an invisible, unselectable object.
    requestedColumns = clamp( requested_columns, MIN_SHAPE, MAX_SHAPE );
    rowsPerColumn = clamp( rows_per_column, MIN_SHAPE, MAX_SHAPE );
    legendScale = finiteInRange( legend_scale, MIN_LEGEND_SCALE, MAX_LEGEND_SCALE, 1.0f );
    SketchTextStyle resolved_style = text_style == null ? SketchTextStyle.defaultStyle() : text_style;
    if ( resolved_style.sizeMode() == SketchTextStyle.SizeMode.SCREEN ) {
      resolved_style = resolved_style.withSize( SketchTextStyle.SizeMode.AUTO_GRID,
        SketchTextStyle.DEFAULT_AUTO_GRID_MULTIPLIER );
    }
    textStyle = resolved_style.withAlignment( SketchTextStyle.Alignment.LEFT );
    if ( rows != null && rows.size() > MAX_ROWS ) throw new IllegalArgumentException( "Too many legend rows" );
    ArrayList< Row > row_copy = new ArrayList<>();
    if ( rows != null ) for ( Row row : rows ) if ( row != null ) row_copy.add( row );
    this.rows = Collections.unmodifiableList( row_copy );
    if ( dismissed != null && dismissed.size() > MAX_TOMBSTONES ) {
      throw new IllegalArgumentException( "Too many dismissed legend symbols" );
    }
    HashSet< String > dismissed_copy = new HashSet<>();
    if ( dismissed != null ) {
      for ( String identity : dismissed ) {
        String safe = normalizeText( identity, MAX_TEXT_CODEPOINTS );
        if ( safe != null && safe.length() > 0 ) dismissed_copy.add( safe );
      }
    }
    this.dismissed = Collections.unmodifiableSet( dismissed_copy );
  }

  static TitleLegendPointState initial( PlotSymbolUsageSnapshot usage, SketchTextStyle style )
  {
    ArrayList< Row > rows = new ArrayList<>();
    if ( usage != null ) for ( PlotSymbolUsageSnapshot.Entry entry : usage.entries() ) rows.add( Row.fromEntry( entry ) );
    int rows_per_column = Math.max( 1, ceilDiv( rows.size(), 2 ) );
    return new TitleLegendPointState( newId(), false, true, 2, rows_per_column,
                                      style, rows, Collections.< String >emptySet() );
  }

  static TitleLegendPointState empty()
  {
    return initial( PlotSymbolUsageSnapshot.empty(), SketchTextStyle.defaultStyle() );
  }

  Capacity capacity() { return new Capacity( rows.size(), requestedColumns, rowsPerColumn ); }

  TitleLegendPointState withRows( List< Row > value, Set< String > tombstones )
  {
    return copy( requestedColumns, rowsPerColumn, textStyle, value, tombstones );
  }

  TitleLegendPointState withShape( int columns, int rows_per_column )
  {
    return copy( columns, rows_per_column, textStyle, rows, dismissed );
  }

  TitleLegendPointState withTextStyle( SketchTextStyle style )
  {
    return copy( requestedColumns, rowsPerColumn, style, rows, dismissed );
  }

  TitleLegendPointState withLegendScale( float scale )
  {
    return new TitleLegendPointState( objectId, false, true, requestedColumns, rowsPerColumn,
                                      scale, textStyle, rows, dismissed );
  }

  TitleLegendPointState rescan( PlotSymbolUsageSnapshot usage )
  {
    if ( usage == null ) return this;
    ArrayList< Row > merged = new ArrayList<>( rows );
    HashSet< String > present = new HashSet<>();
    for ( Row row : rows ) if ( row.identity() != null ) present.add( row.identity() );
    for ( PlotSymbolUsageSnapshot.Entry entry : usage.entries() ) {
      String identity = fromUsageKind( entry.kind ).name() + ":" + entry.thName;
      if ( ! present.contains( identity ) && ! dismissed.contains( identity ) && merged.size() < MAX_ROWS ) {
        merged.add( Row.fromEntry( entry ) );
        present.add( identity );
      }
    }
    return withRows( merged, dismissed );
  }

  JSONObject toJson() throws JSONException
  {
    JSONObject root = new JSONObject();
    root.put( "objectId", objectId );
    root.put( "title", false );
    root.put( "legend", true );
    root.put( "columns", requestedColumns );
    root.put( "rowsPerColumn", rowsPerColumn );
    root.put( "legendScale", legendScale );
    root.put( "textStyle", SketchTextStyleCodec.encode( textStyle ) );
    JSONArray encoded_rows = new JSONArray();
    for ( Row row : rows ) {
      JSONObject json = new JSONObject();
      json.put( "id", row.rowId );
      json.put( "kind", row.kind.name() );
      if ( row.thName != null ) json.put( "thName", row.thName );
      if ( row.fallbackLabel != null ) json.put( "fallbackLabel", row.fallbackLabel );
      if ( row.userLabel != null ) json.put( "userLabel", row.userLabel );
      JSONObject preview = new JSONObject();
      preview.put( "weightMode", row.preview.weightMode.name() );
      preview.put( "weight", row.preview.customWeight );
      preview.put( "scale", row.preview.pointScale );
      preview.put( "orientation", row.preview.orientation );
      if ( row.preview.textValue != null ) preview.put( "text", row.preview.textValue );
      if ( row.preview.specialPayload != null ) preview.put( "special", row.preview.specialPayload );
      json.put( "preview", preview );
      encoded_rows.put( json );
    }
    root.put( "rows", encoded_rows );
    JSONArray encoded_dismissed = new JSONArray();
    for ( String identity : dismissed ) encoded_dismissed.put( identity );
    root.put( "dismissed", encoded_dismissed );
    return root;
  }

  static TitleLegendPointState fromJson( JSONObject root ) throws JSONException
  {
    if ( root == null ) throw new JSONException( "Missing legend state" );
    JSONArray encoded_rows = root.optJSONArray( "rows" );
    int count = encoded_rows == null ? 0 : encoded_rows.length();
    if ( count > MAX_ROWS ) throw new JSONException( "Legend row limit exceeded" );
    ArrayList< Row > rows = new ArrayList<>();
    for ( int i = 0; i < count; ++i ) {
      JSONObject json = encoded_rows.optJSONObject( i );
      if ( json == null ) throw new JSONException( "Invalid legend row" );
      Kind kind;
      try { kind = Kind.valueOf( json.optString( "kind", "CUSTOM" ) ); }
      catch ( IllegalArgumentException e ) { throw new JSONException( "Unsupported legend row kind" ); }
      JSONObject preview_json = json.optJSONObject( "preview" );
      WeightMode weight_mode = WeightMode.STANDARD;
      if ( preview_json != null ) {
        try { weight_mode = WeightMode.valueOf( preview_json.optString( "weightMode", "STANDARD" ) ); }
        catch ( IllegalArgumentException e ) { weight_mode = WeightMode.STANDARD; }
      }
      Preview preview = new Preview( weight_mode,
        checkedNumber( preview_json, "weight", 1.0f, 0.05f, 20.0f ),
        checkedNumber( preview_json, "scale", 1.0f, 0.1f, 10.0f ),
        checkedNumber( preview_json, "orientation", 0.0f, -360000.0f, 360000.0f ),
        preview_json == null ? null : checkedNullableText( preview_json, "text", MAX_TEXT_CODEPOINTS ),
        preview_json == null ? null : preview_json.optJSONObject( "special" ) );
      try {
        rows.add( new Row( checkedText( json.optString( "id", "" ), MAX_ID_CODEPOINTS, "row id" ),
                           kind,
                           checkedNullableText( json, "thName", MAX_TEXT_CODEPOINTS ),
                           checkedNullableText( json, "fallbackLabel", MAX_TEXT_CODEPOINTS ),
                           checkedNullableText( json, "userLabel", MAX_TEXT_CODEPOINTS ), preview ) );
      } catch ( IllegalArgumentException e ) {
        throw new JSONException( e.getMessage() );
      }
    }
    JSONArray encoded_dismissed = root.optJSONArray( "dismissed" );
    int dismissed_count = encoded_dismissed == null ? 0 : encoded_dismissed.length();
    if ( dismissed_count > MAX_TOMBSTONES ) throw new JSONException( "Legend tombstone limit exceeded" );
    HashSet< String > dismissed = new HashSet<>();
    for ( int i = 0; i < dismissed_count; ++i ) {
      dismissed.add( checkedText( encoded_dismissed.optString( i, "" ),
                                  MAX_TEXT_CODEPOINTS, "dismissed identity" ) );
    }
    SketchTextStyle style = SketchTextStyleCodec.decode( root.optString( "textStyle", "" ) );
    int columns = root.optInt( "columns", 2 );
    int rows_per_column = root.optInt( "rowsPerColumn", 1 );
    float legend_scale = checkedNumber( root, "legendScale", 1.0f,
                                        MIN_LEGEND_SCALE, MAX_LEGEND_SCALE );
    if ( columns < MIN_SHAPE || columns > MAX_SHAPE
        || rows_per_column < MIN_SHAPE || rows_per_column > MAX_SHAPE ) {
      throw new JSONException( "Legend shape is out of range" );
    }
    try {
      return new TitleLegendPointState(
        checkedText( root.optString( "objectId", "" ), MAX_ID_CODEPOINTS, "object id" ),
        false, true, columns, rows_per_column, legend_scale,
        style, rows, dismissed );
    } catch ( IllegalArgumentException e ) {
      throw new JSONException( e.getMessage() );
    }
  }

  private TitleLegendPointState copy( int columns, int rows_per_column, SketchTextStyle style,
                                      List< Row > value, Set< String > tombstones )
  {
    return new TitleLegendPointState( objectId, false, true, columns, rows_per_column,
                                      legendScale, style, value, tombstones );
  }

  private static Kind fromUsageKind( PlotSymbolUsageSnapshot.Kind kind )
  {
    if ( kind == PlotSymbolUsageSnapshot.Kind.LINE ) return Kind.LINE;
    if ( kind == PlotSymbolUsageSnapshot.Kind.AREA ) return Kind.AREA;
    return Kind.POINT;
  }

  static PlotSymbolUsageSnapshot.Kind toUsageKind( Kind kind )
  {
    if ( kind == Kind.LINE ) return PlotSymbolUsageSnapshot.Kind.LINE;
    if ( kind == Kind.AREA ) return PlotSymbolUsageSnapshot.Kind.AREA;
    return PlotSymbolUsageSnapshot.Kind.POINT;
  }

  private static int ceilDiv( int value, int divisor )
  {
    return value <= 0 ? 0 : ( value + divisor - 1 ) / divisor;
  }

  private static int clamp( int value, int minimum, int maximum )
  {
    return Math.max( minimum, Math.min( maximum, value ) );
  }

  private static float finiteInRange( float value, float minimum, float maximum, float fallback )
  {
    if ( Float.isNaN( value ) || Float.isInfinite( value ) ) return fallback;
    return Math.max( minimum, Math.min( maximum, value ) );
  }

  private static float normalizeOrientation( float value )
  {
    if ( Float.isNaN( value ) || Float.isInfinite( value ) ) return 0.0f;
    value %= 360.0f;
    return value < 0.0f ? value + 360.0f : value;
  }

  private static String normalizeText( String value, int limit )
  {
    if ( value == null ) return null;
    String normalized = value.replace( "\r\n", "\n" ).replace( '\r', '\n' );
    if ( normalized.codePointCount( 0, normalized.length() ) > limit ) {
      int end = normalized.offsetByCodePoints( 0, limit );
      normalized = normalized.substring( 0, end );
    }
    return normalized;
  }

  private static String normalizeId( String value )
  {
    String normalized = normalizeText( value, MAX_ID_CODEPOINTS );
    return normalized == null || normalized.length() == 0 ? newId() : normalized;
  }

  private static String checkedNullableText( JSONObject json, String key, int limit ) throws JSONException
  {
    return json.has( key ) && ! json.isNull( key )
      ? checkedText( json.optString( key, "" ), limit, key ) : null;
  }

  private static String checkedText( String value, int limit, String field ) throws JSONException
  {
    String normalized = value == null ? "" : value.replace( "\r\n", "\n" ).replace( '\r', '\n' );
    if ( normalized.codePointCount( 0, normalized.length() ) > limit ) {
      throw new JSONException( "Legend " + field + " is too long" );
    }
    return normalized;
  }

  private static float checkedNumber( JSONObject json, String key, float fallback,
                                      float minimum, float maximum ) throws JSONException
  {
    if ( json == null || ! json.has( key ) ) return fallback;
    double value = json.optDouble( key, Double.NaN );
    if ( Double.isNaN( value ) || Double.isInfinite( value ) || value < minimum || value > maximum ) {
      throw new JSONException( "Legend " + key + " is out of range" );
    }
    return (float)value;
  }

  private static String newId() { return UUID.randomUUID().toString(); }

  private static JSONObject copyJson( JSONObject source )
  {
    try { return new JSONObject( source.toString() ); }
    catch ( JSONException e ) { return null; }
  }
}
