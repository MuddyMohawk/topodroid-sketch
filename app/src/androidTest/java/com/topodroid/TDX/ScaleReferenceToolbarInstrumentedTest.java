package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.topodroid.prefs.TDSetting;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ScaleReferenceToolbarInstrumentedTest
{
  private static final String SURVEY = "scale_reference_toolbar_case";
  private static final String PLOT = "scale_reference_toolbar_plot";

  @Test
  public void liveScaleReference_tracksMeasuredBottomToolHeight() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "scale_reference_toolbar" );
    float savedSize = TDSetting.mItemButtonSize;
    try {
      support.prepareForPhysicalCompatCase();
      support.launchMainWindowOnAnyDevice();
      support.deleteGeneratedSurveyAndArtifacts( SURVEY );
      support.createSurveyAndOpenShots( SURVEY, "Scale Reference Team", "1", "toolbar obstruction regression" );
      support.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
      support.openNewPlotFromShotWindow( PLOT, "1" );
      support.enterDrawMode();

      support.configureDrawingToolbarForTest( 1, 2.5f );
      support.assertScaleReferenceClearsBottomTools( true );

      support.configureDrawingToolbarForTest( 8, 2.5f );
      support.assertScaleReferenceClearsBottomTools( true );

      support.configureDrawingToolbarForTest( 1, 1.5f );
      float[] compact = support.assertSketchToggleLabelsFit();
      support.assertScaleReferenceClearsBottomTools( true );

      support.configureDrawingToolbarForTest( 1, 5.0f );
      float[] expanded = support.assertSketchToggleLabelsFit();
      assertTrue( "Preset/style text did not grow with available toolbar space: compact="
          + compact[0] + "px in " + compact[1] + "x" + compact[2] + " (row " + compact[3] + ")"
          + ", expanded=" + expanded[0] + "px in " + expanded[1] + "x" + expanded[2]
          + " (row " + expanded[3] + ")",
        expanded[0] > compact[0] + 1.0f );
      support.assertScaleReferenceClearsBottomTools( true );

      support.configureDrawingToolbarForTest( 1, 2.5f );
      support.dragPlaceOrdinaryPointWithActiveStyle( false, 0.35, 0.35, 100.0f, 0.0f );
      support.showLatestPointScaleToolbarForTest();
      support.assertScaleReferenceClearsBottomTools( true );

      support.enterMoveMode();
      support.assertScaleReferenceClearsBottomTools( false );
    } finally {
      TDSetting.mItemButtonSize = savedSize;
      support.finish();
    }
  }
}
