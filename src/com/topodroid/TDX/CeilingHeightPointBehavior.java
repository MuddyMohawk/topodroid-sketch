/* @file CeilingHeightPointBehavior.java
 *
 * @brief Survey-aware behavior for the standard passage-height point
 */
package com.topodroid.TDX;

import com.topodroid.types.PointScale;

import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

final class CeilingHeightPointBehavior implements SpecialPointBehavior
{
  static final String THERION_NAME = "passage-height";
  static final String BEHAVIOR_ID = "ceiling-height";
  static final float BASE_FOOTPRINT_SCALE = SketchPointScale.legacyScaleValue( PointScale.SCALE_S );
  private static final int STATE_VERSION = 1;
  private static final SpecialPointRenderer RENDERER =
    new FramedTextPointRenderer( FramedTextPointRenderer.FrameShape.OVAL, BASE_FOOTPRINT_SCALE );

  @Override public String therionName() { return THERION_NAME; }
  @Override public String behaviorId() { return BEHAVIOR_ID; }
  @Override public int stateVersion() { return STATE_VERSION; }
  @Override public SpecialPointRenderer renderer() { return RENDERER; }

  @Override public SpecialPointState defaultState()
  {
    return CeilingHeightPointState.defaultState();
  }

  @Override public SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException
  {
    if ( version != STATE_VERSION || payload == null ) return null;
    return new CeilingHeightPointState(
      payload.optBoolean( "waterEnabled", false ),
      payload.optString( "waterDepth", "" ),
      payload.optString( "font", SketchFontRegistry.FONT_DEFAULT ),
      payload.optBoolean( "bold", false ),
      payload.optBoolean( "italic", false ),
      payload.optBoolean( "underline", false ),
      payload.optInt( "textScalePercent", CeilingHeightPointState.DEFAULT_TEXT_SCALE )
    );
  }

  @Override public JSONObject encodeState( SpecialPointState state ) throws JSONException
  {
    if ( ! ( state instanceof CeilingHeightPointState ) ) {
      throw new JSONException( "Wrong ceiling-height state" );
    }
    CeilingHeightPointState ceiling = (CeilingHeightPointState)state;
    JSONObject json = new JSONObject();
    json.put( "waterEnabled", ceiling.waterEnabled );
    json.put( "waterDepth", ceiling.waterDepth );
    json.put( "font", ceiling.fontId() );
    json.put( "bold", ceiling.bold() );
    json.put( "italic", ceiling.italic() );
    json.put( "underline", ceiling.underline() );
    json.put( "textScalePercent", ceiling.textScalePercent() );
    return json;
  }

  @Override public SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                                    DrawingSemanticPointPath point )
  {
    CeilingHeightPointState state = (CeilingHeightPointState)point.specialState();
    state = state.withTypography( context.lastTextStyle() );
    StationLrudResult lrud = context.nearestStationLrud( point.cx, point.cy );
    String value = ( lrud.hasUp && lrud.hasDown )
      ? formatInitialHeight( ( lrud.up + lrud.down ) * context.surveyLengthUnit() ) : "";
    point.setPointText( value );
    point.setSpecialState( state, true );
    context.pointChanged( point );
    return SpecialPointPlacementAction.OPEN_EDITOR;
  }

  @Override public SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                                        DrawingSemanticPointPath point )
  {
    return new CeilingHeightPointEditorController( parent, point );
  }

  @Override public void preparePreview( DrawingSemanticPointPath point )
  {
    point.setPointText( "10" );
  }

  static String formatInitialHeight( float value )
  {
    float rounded = Math.round( Math.max( 0.0f, value ) * 2.0f ) / 2.0f;
    if ( Math.abs( rounded - Math.round( rounded ) ) < 0.001f ) {
      return String.format( Locale.US, "%d", Math.round( rounded ) );
    }
    return String.format( Locale.US, "%.1f", rounded );
  }
}
