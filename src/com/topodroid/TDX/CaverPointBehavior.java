/* @file CaverPointBehavior.java
 *
 * @brief Survey-aware behavior for a true-scale caver silhouette
 */
package com.topodroid.TDX;

import org.json.JSONException;
import org.json.JSONObject;

final class CaverPointBehavior implements SpecialPointBehavior
{
  static final String THERION_NAME = "caver";
  static final String FULL_THERION_NAME = "u:caver";
  static final String BEHAVIOR_ID = "caver-silhouette";
  private static final int STATE_VERSION = 1;
  private static final CaverPointRenderer RENDERER = new CaverPointRenderer();

  @Override public String therionName() { return THERION_NAME; }
  @Override public String fullTherionName() { return FULL_THERION_NAME; }
  @Override public String behaviorId() { return BEHAVIOR_ID; }
  @Override public int stateVersion() { return STATE_VERSION; }
  @Override public SpecialPointRenderer renderer() { return RENDERER; }

  @Override public SpecialPointState defaultState() { return CaverPointState.defaultState(); }

  @Override public SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException
  {
    if ( version != STATE_VERSION || payload == null ) return null;
    return new CaverPointState(
      CaverPointState.Variant.fromPersistedName( payload.optString( "variant", "man" ) ),
      payload.optDouble( "heightMeters", CaverPointState.DEFAULT_HEIGHT_METERS ) );
  }

  @Override public JSONObject encodeState( SpecialPointState state ) throws JSONException
  {
    if ( ! ( state instanceof CaverPointState ) ) throw new JSONException( "Wrong caver state" );
    CaverPointState caver = (CaverPointState)state;
    JSONObject json = new JSONObject();
    json.put( "variant", caver.variant.persistedName );
    json.put( "heightMeters", caver.heightMeters );
    return json;
  }

  @Override public SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                                    DrawingSemanticPointPath point )
  {
    if ( point != null && point.specialState() instanceof CaverPointState ) {
      point.setSpecialState( point.specialState(), true );
      if ( context != null ) context.pointChanged( point );
    }
    return SpecialPointPlacementAction.NONE;
  }

  @Override public Object prepareState( DrawingSemanticPointPath point, SpecialPointState state )
  {
    return state instanceof CaverPointState ? CaverPointRenderer.prepare( (CaverPointState)state ) : null;
  }

  @Override public SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                                        DrawingSemanticPointPath point )
  {
    return new CaverPointEditorController( parent, point );
  }

  @Override public void preparePreview( DrawingSemanticPointPath point ) { }
}
