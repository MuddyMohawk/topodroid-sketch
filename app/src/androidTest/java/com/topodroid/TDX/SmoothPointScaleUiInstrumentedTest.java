package com.topodroid.TDX;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class SmoothPointScaleUiInstrumentedTest
{
  private static final String SURVEY = "smooth_point_scale_case";
  private static final String PLOT = "smooth_point_scale_plot";

  @Test
  public void drawingWindow_dragPlacementAndEditScaleUseExactPointScale() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "smooth_point_scale" );
    try {
      support.prepareForPhysicalCompatCase();
      support.launchMainWindowOnAnyDevice();
      support.deleteGeneratedSurveyAndArtifacts( SURVEY );
      support.createSurveyAndOpenShots( SURVEY, "Scale Test Team", "1", "smooth point scale regression" );
      support.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
      support.openNewPlotFromShotWindow( PLOT, "1" );
      support.enterDrawMode();

      support.dragPlaceOrdinaryPointWithActiveStyle( false, 0.35, 0.55, 300.0f, 0.0f );
      support.assertLatestPointBrushScale( 2.0f, 0.02f );
      support.assertLatestPointOrientation( 0.0f, 0.01f );

      support.dragPlaceOrdinaryPointWithActiveStyle( true, 0.35, 0.55, 150.0f, -150.0f );
      support.assertLatestPointBrushScale( SketchPointScale.scaleFromDragDistance( 212.13203f ), 0.02f );
      support.assertLatestPointOrientation( 45.0f, 1.0f );

      support.setLatestPointEditScaleProgress( SketchPointScale.editProgressFromScale( 1.60f ) );
      support.assertLatestPointBrushScale( 1.60f, 0.03f );
    } finally {
      support.finish();
    }
  }
}
