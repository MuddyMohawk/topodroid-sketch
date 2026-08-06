/* @file TitleLegendPointBehavior.java
 *
 * @brief Special-point lifecycle for the Title Sheet and Legend object
 */
package com.topodroid.TDX;

import android.app.Dialog;
import android.app.AlertDialog;

import org.json.JSONException;
import org.json.JSONObject;

public final class TitleLegendPointBehavior implements SpecialPointBehavior
{
  static final String THERION_NAME = "title-legend";
  static final String FULL_THERION_NAME = "u:title-legend";
  static final String BEHAVIOR_ID = "title-legend";
  private static final int STATE_VERSION = 1;
  private static final SpecialPointRenderer RENDERER = new TitleLegendPointRenderer();

  static final class NewerState implements SpecialPointState
  {
    final int version;
    NewerState( int version ) { this.version = version; }
  }

  @Override public String therionName() { return THERION_NAME; }
  @Override public String fullTherionName() { return FULL_THERION_NAME; }
  @Override public String behaviorId() { return BEHAVIOR_ID; }
  @Override public int stateVersion() { return STATE_VERSION; }
  @Override public SpecialPointRenderer renderer() { return RENDERER; }

  @Override public SpecialPointState defaultState() { return TitleLegendPointState.empty(); }

  @Override public SpecialPointState decodeState( int version, JSONObject payload ) throws JSONException
  {
    if ( version > STATE_VERSION ) return new NewerState( version );
    if ( version < 1 || payload == null ) return null;
    return TitleLegendPointState.fromJson( payload );
  }

  @Override public JSONObject encodeState( SpecialPointState state ) throws JSONException
  {
    if ( ! ( state instanceof TitleLegendPointState ) ) throw new JSONException( "Wrong title/legend state" );
    return ( (TitleLegendPointState)state ).toJson();
  }

  @Override public SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context,
                                                                    DrawingSemanticPointPath point )
  {
    TitleLegendPointState state = TitleLegendPointState.initial(
      context.plotSymbolUsageSnapshot(), context.lastTextStyle() );
    point.setSpecialState( state, true );
    context.pointChanged( point );
    return SpecialPointPlacementAction.OPEN_DEDICATED_EDITOR;
  }

  @Override public Object prepareState( DrawingSemanticPointPath point, SpecialPointState state )
  {
    return state instanceof TitleLegendPointState
      ? TitleLegendLayout.prepare( point, (TitleLegendPointState)state ) : null;
  }

  @Override public boolean previewUsesAuthoredGlyph() { return true; }

  @Override public SpecialPointEditorController createEditorController( DrawingWindow parent,
                                                                        DrawingSemanticPointPath point )
  {
    return null;
  }

  @Override public Dialog createDedicatedEditor( DrawingWindow parent, DrawingSemanticPointPath point,
                                                 boolean initial_placement )
  {
    if ( point.specialState() instanceof NewerState ) {
      NewerState newer = (NewerState)point.specialState();
      return new AlertDialog.Builder( parent )
        .setTitle( R.string.title_legend_dialog_title )
        .setMessage( parent.getString( R.string.title_legend_newer_version, newer.version ) )
        .setPositiveButton( android.R.string.ok, null )
        .create();
    }
    return new DrawingTitleLegendDialog( parent, point, initial_placement );
  }

  @Override public void preparePreview( DrawingSemanticPointPath point ) { }

  static boolean isTitleLegendName( String name )
  {
    return THERION_NAME.equals( name ) || FULL_THERION_NAME.equals( name );
  }

  public static boolean isTitleLegend( DrawingPointPath point )
  {
    return point != null && isTitleLegendName( point.getFullThName() );
  }
}
