package com.topodroid.TDX;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

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

  @Test
  public void drawingWindow_movableQuickSwitcherAndToolbarEditorButtonsOpen() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_TOOLBAR_TOGGLE ) );
    mSupport.launchMainWindow();
    assertTrue( ToolsetRepository.saveProfile( TopoDroidApp.mData, ToolsetProfile.freshDefault() ) );
    mSupport.createSurveyAndOpenShots( SURVEY_TOOLBAR_TOGGLE, "Toolbar Test Team", "1", "toolbar action buttons" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
    mSupport.assertDefaultSketchToolbarVisible();

    UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
    UiObject2 quickSwitcher = device.wait( Until.findObject( By.desc( "Quick Switcher" ) ), 3000 );
    assertNotNull( quickSwitcher );
    quickSwitcher.click();
    assertNotNull( device.wait( Until.findObject( By.text( "Quick switcher" ) ), 3000 ) );
    device.pressBack();

    UiObject2 editor = device.wait( Until.findObject( By.desc( "Edit toolbars" ) ), 3000 );
    assertNotNull( editor );
    editor.click();
    assertNotNull( device.wait( Until.findObject( By.text( "Toolbars" ) ), 3000 ) );
  }
}
