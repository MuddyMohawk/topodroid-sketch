/* @file SpecialPointPlacementContext.java
 *
 * @brief Narrow access to drawing/survey services used by special-point behaviors
 */
package com.topodroid.TDX;

final class SpecialPointPlacementContext
{
  private final DrawingWindow mParent;

  SpecialPointPlacementContext( DrawingWindow parent )
  {
    mParent = parent;
  }

  StationLrudResult nearestStationLrud( float scene_x, float scene_y )
  {
    return ( mParent == null ) ? new StationLrudResult()
                               : mParent.computeNearestStationLrud( scene_x, scene_y );
  }

  SketchTextStyle lastTextStyle()
  {
    return ( mParent == null ) ? SketchTextStyle.defaultStyle() : mParent.loadTextObjectDefault();
  }

  PlotSymbolUsageSnapshot plotSymbolUsageSnapshot()
  {
    return mParent == null ? PlotSymbolUsageSnapshot.empty() : mParent.computePlotSymbolUsageSnapshot();
  }

  BeddingSurveyContext nearestBeddingSurvey( float scene_x, float scene_y )
  {
    return mParent == null ? BeddingSurveyContext.empty()
      : mParent.computeNearestBeddingSurvey( scene_x, scene_y );
  }

  BeddingAttitudePointState projectBeddingState( BeddingAttitudePointState state, String station )
  {
    if ( state == null || mParent == null ) return state;
    return state.withStationAndProjection( station,
      mParent.computeBeddingProjection( state.attitude(), station ) );
  }

  boolean beddingViewSupported()
  {
    return mParent != null && mParent.isBeddingViewSupported();
  }

  void rejectBeddingPlacement( DrawingSemanticPointPath point )
  {
    if ( mParent != null ) mParent.rejectUnsupportedBeddingPoint( point );
  }

  float surveyLengthUnit()
  {
    return com.topodroid.prefs.TDSetting.mUnitLength;
  }

  void pointChanged( DrawingSemanticPointPath point )
  {
    if ( mParent != null ) mParent.updatePointObject( point );
  }
}
