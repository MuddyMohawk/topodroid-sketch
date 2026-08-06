/* @file SpecialPointBehavior.java
 *
 * @brief Pluggable lifecycle for a survey-aware point symbol
 */
package com.topodroid.TDX;

import android.app.Dialog;

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

  /** Prepare immutable, draw-ready state outside the render path. */
  default Object prepareState( DrawingSemanticPointPath point, SpecialPointState state ) { return null; }

  /** True when the picker should show the symbol file's authored glyph. */
  default boolean previewUsesAuthoredGlyph() { return false; }

  /** True when dynamic ink already expresses absolute scene directions and
   *  must not receive the legacy landscape symbol-quarter-turn. */
  default boolean rendersAbsoluteSceneDirections() { return false; }

  SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                       DrawingSemanticPointPath point );

  /** Optional full-screen/dedicated editor for composite special points. */
  default Dialog createDedicatedEditor( DrawingWindow parent, DrawingSemanticPointPath point,
                                        boolean initial_placement ) { return null; }

  void preparePreview( DrawingSemanticPointPath point );
}
