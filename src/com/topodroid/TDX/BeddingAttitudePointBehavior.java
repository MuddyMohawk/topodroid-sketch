/* @file BeddingAttitudePointBehavior.java
 *
 * @brief Survey-aware behavior for a geological bedding attitude point
 */
package com.topodroid.TDX;

import com.topodroid.geo.BeddingAttitude;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BeddingAttitudePointBehavior implements SpecialPointBehavior
{
  static final String THERION_NAME = "bedding-attitude";
  static final String FULL_THERION_NAME = "u:bedding-attitude";
  static final String BEHAVIOR_ID = "bedding-attitude";
  private static final int STATE_VERSION = 1;
  private static final SpecialPointRenderer RENDERER = new BeddingAttitudePointRenderer();

  @Override public String therionName() { return THERION_NAME; }
  @Override public String fullTherionName() { return FULL_THERION_NAME; }
  @Override public String behaviorId() { return BEHAVIOR_ID; }
  @Override public int stateVersion() { return STATE_VERSION; }
  @Override public SpecialPointRenderer renderer() { return RENDERER; }
  @Override public boolean rendersAbsoluteSceneDirections() { return true; }
  @Override public SpecialPointState defaultState() { return BeddingAttitudePointState.defaultState(); }

  @Override public SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException
  {
    if ( version != STATE_VERSION || payload == null ) return null;
    return new BeddingAttitudePointState(
      payload.optBoolean( "configured", false ),
      enumValue( BeddingAttitudePointState.Mode.class, payload.optString( "mode" ),
        BeddingAttitudePointState.Mode.MANUAL ),
      payload.optDouble( "normalE", 0.0 ), payload.optDouble( "normalN", 0.0 ),
      payload.optDouble( "normalU", 1.0 ), payload.optString( "station", "" ),
      longArray( payload.optJSONArray( "shotIds" ) ),
      doubleArray( payload.optJSONArray( "sourceLengthsMeters" ) ),
      doubleArray( payload.optJSONArray( "sourceBearingsDegrees" ) ),
      doubleArray( payload.optJSONArray( "sourceClinosDegrees" ) ),
      payload.optString( "azimuthReference", "SURVEY_MAGNETIC" ),
      payload.optDouble( "declinationDegrees", 0.0 ), payload.optString( "model", "" ),
      finiteOrNaN( payload, "sigmaDistanceMeters" ),
      finiteOrNaN( payload, "sigmaBearingDegrees" ),
      finiteOrNaN( payload, "sigmaClinoDegrees" ),
      finiteOrNaN( payload, "surfaceScatterMeters" ),
      finiteOrNaN( payload, "bearingCosineFloor" ),
      payload.optString( "quality", "" ), stringArray( payload.optJSONArray( "issues" ) ),
      finiteOrNaN( payload, "region68DipMin" ), finiteOrNaN( payload, "region68DipMax" ),
      finiteOrNaN( payload, "region68DirectionStart" ),
      finiteOrNaN( payload, "region68DirectionEnd" ),
      payload.optBoolean( "region68WrapsNorth", false ),
      payload.optString( "region68Status", "UNAVAILABLE" ),
      finiteOrNaN( payload, "region95DipMin" ), finiteOrNaN( payload, "region95DipMax" ),
      finiteOrNaN( payload, "region95DirectionStart" ),
      finiteOrNaN( payload, "region95DirectionEnd" ),
      payload.optBoolean( "region95WrapsNorth", false ),
      payload.optString( "region95Status", "UNAVAILABLE" ),
      enumValue( BeddingAttitudePointState.ViewKind.class, payload.optString( "view" ),
        BeddingAttitudePointState.ViewKind.UNSUPPORTED ),
      payload.optBoolean( "traceValid", false ), finiteOrNaN( payload, "traceAngle" ),
      finiteOrNaN( payload, "apparentDip" ), finiteOrNaN( payload, "extendedBearing" ),
      finiteOrNaN( payload, "extendedSign" ), payload.optBoolean( "extendedAmbiguous", false ),
      payload.optString( "font", SketchFontRegistry.FONT_DEFAULT ),
      payload.optBoolean( "bold", false ), payload.optBoolean( "italic", false ),
      payload.optBoolean( "underline", false ),
      payload.optInt( "textScalePercent", BeddingAttitudePointState.DEFAULT_TEXT_SCALE ) );
  }

  @Override public JSONObject encodeState( SpecialPointState state ) throws JSONException
  {
    if ( ! ( state instanceof BeddingAttitudePointState ) ) {
      throw new JSONException( "Wrong bedding-attitude state" );
    }
    BeddingAttitudePointState bedding = (BeddingAttitudePointState)state;
    JSONObject json = new JSONObject();
    json.put( "configured", bedding.configured );
    json.put( "mode", bedding.mode.name() );
    json.put( "normalE", bedding.normalEast );
    json.put( "normalN", bedding.normalNorth );
    json.put( "normalU", bedding.normalUp );
    json.put( "station", bedding.stationName );
    json.put( "shotIds", jsonArray( bedding.sourceShotIds ) );
    json.put( "sourceLengthsMeters", jsonArray( bedding.sourceLengthsMeters ) );
    json.put( "sourceBearingsDegrees", jsonArray( bedding.sourceBearingsDegrees ) );
    json.put( "sourceClinosDegrees", jsonArray( bedding.sourceClinosDegrees ) );
    json.put( "azimuthReference", bedding.azimuthReference );
    json.put( "declinationDegrees", bedding.declinationDegrees );
    json.put( "model", bedding.measurementModelId );
    putFinite( json, "sigmaDistanceMeters", bedding.sigmaDistanceMeters );
    putFinite( json, "sigmaBearingDegrees", bedding.sigmaBearingDegrees );
    putFinite( json, "sigmaClinoDegrees", bedding.sigmaClinoDegrees );
    putFinite( json, "surfaceScatterMeters", bedding.surfaceScatterMeters );
    putFinite( json, "bearingCosineFloor", bedding.bearingCosineFloor );
    json.put( "quality", bedding.fitQuality );
    json.put( "issues", jsonArray( bedding.fitIssues ) );
    putFinite( json, "region68DipMin", bedding.region68DipMinimum );
    putFinite( json, "region68DipMax", bedding.region68DipMaximum );
    putFinite( json, "region68DirectionStart", bedding.region68DirectionStart );
    putFinite( json, "region68DirectionEnd", bedding.region68DirectionEnd );
    json.put( "region68WrapsNorth", bedding.region68DirectionWrapsNorth );
    json.put( "region68Status", bedding.region68Status );
    putFinite( json, "region95DipMin", bedding.region95DipMinimum );
    putFinite( json, "region95DipMax", bedding.region95DipMaximum );
    putFinite( json, "region95DirectionStart", bedding.region95DirectionStart );
    putFinite( json, "region95DirectionEnd", bedding.region95DirectionEnd );
    json.put( "region95WrapsNorth", bedding.region95DirectionWrapsNorth );
    json.put( "region95Status", bedding.region95Status );
    json.put( "view", bedding.viewKind.name() );
    json.put( "traceValid", bedding.traceValid );
    putFinite( json, "traceAngle", bedding.canvasTraceAngleDegrees );
    putFinite( json, "apparentDip", bedding.apparentDipDegrees );
    putFinite( json, "extendedBearing", bedding.extendedReferenceBearingDegrees );
    putFinite( json, "extendedSign", bedding.extendedExtendSign );
    json.put( "extendedAmbiguous", bedding.extendedReferenceAmbiguous );
    json.put( "font", bedding.fontId() );
    json.put( "bold", bedding.bold() );
    json.put( "italic", bedding.italic() );
    json.put( "underline", bedding.underline() );
    json.put( "textScalePercent", bedding.textScalePercent() );
    return json;
  }

  @Override public SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                                    DrawingSemanticPointPath point )
  {
    if ( ! context.beddingViewSupported() ) {
      context.rejectBeddingPlacement( point );
      return SpecialPointPlacementAction.NONE;
    }
    BeddingAttitudePointState state = (BeddingAttitudePointState)point.specialState();
    BeddingSurveyContext survey = context.nearestBeddingSurvey( point.cx, point.cy );
    state = state.withTypography( context.lastTextStyle() );
    state = context.projectBeddingState( state, survey.stationName );
    point.setPointText( Integer.toString( (int)Math.round( state.attitude().dipDegrees ) ) );
    point.setSpecialState( state, true );
    context.pointChanged( point );
    return SpecialPointPlacementAction.OPEN_EDITOR;
  }

  @Override public SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                                        DrawingSemanticPointPath point )
  {
    return new BeddingAttitudePointEditorController( parent, point );
  }

  @Override public void preparePreview( DrawingSemanticPointPath point )
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 90.0, 60.0 );
    BeddingAttitudePointState state = BeddingAttitudePointState.manual( true, attitude, "",
      BeddingAttitudePointState.ViewKind.PLAN, false, Double.NaN, Double.NaN,
      Double.NaN, Double.NaN, false, 0.0, SketchTextStyle.defaultStyle(),
      BeddingAttitudePointState.DEFAULT_TEXT_SCALE );
    point.setSpecialState( state, false );
    point.setPointText( "60" );
  }

  private static void putFinite( JSONObject json, String name, double value ) throws JSONException
  {
    if ( Double.isFinite( value ) ) json.put( name, value );
  }

  private static double finiteOrNaN( JSONObject json, String name )
  {
    double value = json.optDouble( name, Double.NaN );
    return Double.isFinite( value ) ? value : Double.NaN;
  }

  private static JSONArray jsonArray( long[] values )
  {
    JSONArray array = new JSONArray();
    if ( values != null ) for ( long value : values ) array.put( value );
    return array;
  }

  private static JSONArray jsonArray( String[] values )
  {
    JSONArray array = new JSONArray();
    if ( values != null ) for ( String value : values ) array.put( value );
    return array;
  }

  private static JSONArray jsonArray( double[] values ) throws JSONException
  {
    JSONArray array = new JSONArray();
    if ( values != null ) for ( double value : values ) array.put( value );
    return array;
  }

  private static long[] longArray( JSONArray array )
  {
    if ( array == null ) return new long[0];
    long[] values = new long[ array.length() ];
    for ( int i = 0; i < values.length; ++i ) values[i] = array.optLong( i );
    return values;
  }

  private static String[] stringArray( JSONArray array )
  {
    if ( array == null ) return new String[0];
    String[] values = new String[ array.length() ];
    for ( int i = 0; i < values.length; ++i ) values[i] = array.optString( i, "" );
    return values;
  }

  private static double[] doubleArray( JSONArray array )
  {
    if ( array == null ) return new double[0];
    double[] values = new double[ array.length() ];
    for ( int i = 0; i < values.length; ++i ) values[i] = array.optDouble( i, Double.NaN );
    return values;
  }

  private static < T extends Enum< T > > T enumValue( Class< T > type, String name, T fallback )
  {
    try { return Enum.valueOf( type, name ); } catch ( IllegalArgumentException e ) { return fallback; }
  }
}
