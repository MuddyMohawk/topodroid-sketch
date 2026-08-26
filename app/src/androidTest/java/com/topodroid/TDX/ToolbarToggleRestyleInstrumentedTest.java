package com.topodroid.TDX;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ToolbarToggleRestyleInstrumentedTest
{
  private static final String SURVEY_TOOLBAR_TOGGLE = "toolbar_toggle_restyle_case";
  private static final String PLOT_NAME = "1";

  private VisualTestSupport mSupport;

  @Before
  public void setUp()
  {
    mSupport = new VisualTestSupport( "toolbar_toggle_restyle" );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @Test
  public void drawingWindow_presetAndStyleRowsUseSketchToggleColors() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_TOOLBAR_TOGGLE ) );
    mSupport.launchMainWindow();
    mSupport.createSurveyAndOpenShots( SURVEY_TOOLBAR_TOGGLE, "Toolbar Test Team", "1", "toolbar toggle colors" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();

    mSupport.assertSketchToggleBarColors( 1, 2 );
    mSupport.tapPresetButton( 2 );
    mSupport.tapStyleButton( 3 );
    mSupport.assertSketchToggleBarColors( 2, 3 );
  }

  @Test
  public void drawingWindow_toolbarSettingsButtonsOpenMatchingPreferencePages() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_TOOLBAR_TOGGLE ) );
    mSupport.launchMainWindow();
    mSupport.createSurveyAndOpenShots( SURVEY_TOOLBAR_TOGGLE, "Toolbar Test Team", "1", "toolbar settings shortcuts" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();

    mSupport.assertDefaultSketchToolbarVisible();
    mSupport.tapPresetSettingsButton();
    mSupport.assertPreferencePageVisible( R.string.title_settings_presets );

    mSupport.pressBackToDrawingWindow();
    mSupport.tapStyleSettingsButton();
    mSupport.assertPreferencePageVisible( R.string.title_settings_styles );
  }
}
