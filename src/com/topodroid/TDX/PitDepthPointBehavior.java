/* @file PitDepthPointBehavior.java
 *
 * @brief Survey-aware behavior for the boxed pit-depth annotation
 */
package com.topodroid.TDX;

import org.json.JSONException;
import org.json.JSONObject;

final class PitDepthPointBehavior implements SpecialPointBehavior
{
  static final String THERION_NAME = "pit-depth";
  static final String FULL_THERION_NAME = "u:pit-depth";
  static final String BEHAVIOR_ID = "pit-depth";
  static final float BASE_FOOTPRINT_SCALE = SpecialPointSizing.COMPACT_FRAME_SCALE;
  private static final int STATE_VERSION = 1;
  private static final SpecialPointRenderer RENDERER =
    new FramedTextPointRenderer( FramedTextPointRenderer.FrameShape.RECTANGLE, BASE_FOOTPRINT_SCALE,
      FramedTextPointRenderer.SingleRowPolicy.WIDEN_FROM_SQUARE );

  @Override public String therionName() { return THERION_NAME; }
  @Override public String fullTherionName() { return FULL_THERION_NAME; }
  @Override public String behaviorId() { return BEHAVIOR_ID; }
  @Override public int stateVersion() { return STATE_VERSION; }
  @Override public SpecialPointRenderer renderer() { return RENDERER; }

  @Override public SpecialPointState defaultState()
  {
    return PitDepthPointState.defaultState();
  }

  @Override public SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException
  {
    if ( version != STATE_VERSION || payload == null ) return null;
    return new PitDepthPointState(
      payload.optString( "font", SketchFontRegistry.FONT_DEFAULT ),
      payload.optBoolean( "bold", false ),
      payload.optBoolean( "italic", false ),
      payload.optBoolean( "underline", false ),
      payload.optInt( "textScalePercent", PitDepthPointState.DEFAULT_TEXT_SCALE )
    );
  }

  @Override public JSONObject encodeState( SpecialPointState state ) throws JSONException
  {
    if ( ! ( state instanceof PitDepthPointState ) ) {
      throw new JSONException( "Wrong pit-depth state" );
    }
    PitDepthPointState pit = (PitDepthPointState)state;
    JSONObject json = new JSONObject();
    json.put( "font", pit.fontId() );
    json.put( "bold", pit.bold() );
    json.put( "italic", pit.italic() );
    json.put( "underline", pit.underline() );
    json.put( "textScalePercent", pit.textScalePercent() );
    return json;
  }

  @Override public SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                                    DrawingSemanticPointPath point )
  {
    PitDepthPointState state = (PitDepthPointState)point.specialState();
    state = state.withTypography( context.lastTextStyle() );
    point.setPointText( initialValue( context.nearestStationLrud( point.cx, point.cy ),
                                     context.surveyLengthUnit() ) );
    point.setSpecialState( state, true );
    context.pointChanged( point );
    return SpecialPointPlacementAction.OPEN_EDITOR;
  }

  @Override public SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                                        DrawingSemanticPointPath point )
  {
    return new PitDepthPointEditorController( parent, point );
  }

  @Override public void preparePreview( DrawingSemanticPointPath point )
  {
    point.setPointText( "10" );
  }

  static String initialValue( StationLrudResult lrud, float survey_length_unit )
  {
    return ( lrud != null && lrud.hasDown )
      ? SpecialPointValueFormatter.halfUnit( lrud.down * survey_length_unit ) : "";
  }
}
