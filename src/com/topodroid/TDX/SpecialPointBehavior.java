/* @file SpecialPointBehavior.java
 *
 * @brief Pluggable lifecycle for a survey-aware point symbol
 */
package com.topodroid.TDX;

import org.json.JSONException;
import org.json.JSONObject;

interface SpecialPointBehavior
{
  String therionName();

  default String fullTherionName() { return therionName(); }

  String behaviorId();

  int stateVersion();

  SpecialPointState defaultState();

  SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException;

  JSONObject encodeState( SpecialPointState state ) throws JSONException;

  SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                   DrawingSemanticPointPath point );

  SpecialPointRenderer renderer();

  SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                       DrawingSemanticPointPath point );

  void preparePreview( DrawingSemanticPointPath point );
}
