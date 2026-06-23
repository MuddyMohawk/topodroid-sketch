package com.topodroid.TDX;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ToolbarSelectionInstrumentedTest
{
  private static final String SURVEY_TOOLBAR = "toolbar_selection_case";
  private static final String PLOT_NAME = "1";

  private VisualTestSupport mSupport;

  @Rule
  public final TestName mTestName = new TestName();

  @Before
  public void setUp()
  {
    mSupport = new VisualTestSupport( mTestName.getMethodName() );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @Test
  public void selectedSketchLineSurvivesReturningThroughSurveyDataScreen() throws Exception
  {
    mSupport.prepareForPhysicalCompatCase();
    mSupport.launchMainWindowOnAnyDevice();
    mSupport.deleteGeneratedSurveyAndArtifacts( SURVEY_TOOLBAR );
    mSupport.createSurveyAndOpenShots( SURVEY_TOOLBAR, "Toolbar Test Team", "1", "toolbar selection regression" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();

    mSupport.clickRecentLineByThName( SketchLineSymbolManager.LEGACY_TH_NAME_THICK );
    mSupport.waitForCurrentLineThName( SketchLineSymbolManager.LEGACY_TH_NAME_THICK );

    mSupport.pressBackToShotWindow();
    mSupport.waitForToolbarConfigValue( ItemDrawer.KEY_TOOLBAR_ACTIVE_TYPE, "line" );

    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.enterDrawMode();

    mSupport.waitForCurrentLineThName( SketchLineSymbolManager.LEGACY_TH_NAME_THICK );
  }
}
