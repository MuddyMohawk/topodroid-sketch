/* Local per-survey tool choices. Slot references never become drawing metadata. */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;
import com.topodroid.util.TDLog;

import org.json.JSONException;
import org.json.JSONObject;

final class SymbolSketchPreferences
{
  private SymbolSketchPreferences() { }

  static String key( long surveyId ) { return "sketch_symbol_preferences:" + surveyId; }

  static final class Choice
  {
    final int style;
    final int preset;

    Choice( int style, int preset ) { this.style = style; this.preset = preset; }

    int resolveStyle( int slots, int current )
    {
      if ( style > 0 && style <= slots ) return style;
      return slots >= TDSetting.SKETCH_STYLE_STANDARD ? TDSetting.SKETCH_STYLE_STANDARD : current;
    }

    int resolvePreset( int slots, int current )
    {
      return preset > 0 && preset <= slots ? preset : current;
    }
  }

  /** Immutable owner and choices captured before a gesture or insertion dialog. */
  static final class Use
  {
    final long surveyId;
    final int type;
    final String name;
    final int style;
    final int preset;
    final boolean learnStyle;
    final boolean learnPreset;

    Use( long surveyId, int type, String name, int style, int preset,
         boolean learnStyle, boolean learnPreset )
    {
      this.surveyId = surveyId;
      this.type = type;
      this.name = name;
      this.style = style;
      this.preset = preset;
      this.learnStyle = learnStyle;
      this.learnPreset = learnPreset;
    }
  }

  static Choice load( DataHelper data, long surveyId, int type, String name )
  {
    return decodeChoice( read( data, surveyId ), type, name );
  }

  static Choice decodeChoice( JSONObject root, int type, String name )
  {
    if ( root == null || name == null || name.length() == 0
      || ( type != SymbolType.POINT && type != SymbolType.LINE && type != SymbolType.AREA ) ) return new Choice( 0, 0 );
    JSONObject symbols = root.optJSONObject( Integer.toString( type ) );
    JSONObject choice = symbols == null || name == null ? null : symbols.optJSONObject( name );
    return new Choice( slot( choice, "style", TDSetting.SKETCH_STYLE_MAX ),
                       slot( choice, "preset", TDSetting.SKETCH_PRESET_MAX ) );
  }

  private static int slot( JSONObject choice, String field, int max )
  {
    Object value = choice == null ? null : choice.opt( field );
    if ( ! ( value instanceof Integer || value instanceof Long ) ) return 0;
    long id = ((Number)value).longValue();
    return id >= 1 && id <= max ? (int)id : 0;
  }

  static void rememberStyle( DataHelper data, Use use, int style )
  {
    if ( use != null && use.learnStyle && TDSetting.mRememberSymbolStyle ) {
      update( data, use, style, 0, false );
    }
  }

  static void rememberPreset( DataHelper data, Use use, int preset )
  {
    if ( use != null && use.learnPreset && TDSetting.mRememberSymbolPreset ) {
      update( data, use, 0, preset, false );
    }
  }

  static void rememberMissing( DataHelper data, Use use )
  {
    if ( use == null ) return;
    update( data, use,
      use.learnStyle && TDSetting.mRememberSymbolStyle ? use.style : 0,
      use.learnPreset && TDSetting.mRememberSymbolPreset ? use.preset : 0, true );
  }

  private static void update( DataHelper data, Use use, int style, int preset, boolean onlyMissing )
  {
    if ( data == null || use.surveyId < 0 || use.name == null || use.name.length() == 0
      || ( use.type != SymbolType.POINT && use.type != SymbolType.LINE && use.type != SymbolType.AREA ) ) return;
    if ( style < 1 || style > TDSetting.SKETCH_STYLE_MAX ) style = 0;
    if ( preset < 1 || preset > TDSetting.SKETCH_PRESET_MAX ) preset = 0;
    if ( style == 0 && preset == 0 ) return;
    // Read again on each write: another sketch window or a later manual tap may have taught this symbol.
    JSONObject root = read( data, use.surveyId );
    Choice previous = decodeChoice( root, use.type, use.name );
    if ( onlyMissing ) {
      if ( previous.style != 0 ) style = 0;
      if ( previous.preset != 0 ) preset = 0;
    }
    if ( ( style == 0 || style == previous.style ) && ( preset == 0 || preset == previous.preset ) ) return;
    try {
      String type = Integer.toString( use.type );
      JSONObject symbols = root.optJSONObject( type );
      if ( symbols == null ) { symbols = new JSONObject(); root.put( type, symbols ); }
      JSONObject choice = symbols.optJSONObject( use.name );
      if ( choice == null ) { choice = new JSONObject(); symbols.put( use.name, choice ); }
      if ( style != 0 ) choice.put( "style", style );
      if ( preset != 0 ) choice.put( "preset", preset );
      data.setValue( key( use.surveyId ), root.toString() );
    } catch ( JSONException e ) {
      TDLog.e( "Symbol choices could not be saved: " + e.getMessage() );
    }
  }

  private static JSONObject read( DataHelper data, long surveyId )
  {
    String stored = data == null || surveyId < 0 ? null : data.getValue( key( surveyId ) );
    if ( stored != null ) {
      try { return new JSONObject( stored ); }
      catch ( JSONException e ) { TDLog.e( "Invalid symbol choices ignored: " + e.getMessage() ); }
    }
    return new JSONObject();
  }
}
