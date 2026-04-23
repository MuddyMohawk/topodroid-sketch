package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class VisualGoldenInstrumentedTest
{
  private static final String SURVEY_SKETCH  = "visual_sketch_case";
  private static final String SURVEY_ZIP     = "visual_zip_case";
  private static final String SURVEY_PNG     = "visual_png_case";
  private static final String SURVEY_COMPASS = "visual_compass_case";
  private static final String PLOT_NAME      = "1";
  private static final String PNG_EXPORT_FILENAME = "visual_png_export.png";

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
  public void createSurvey_addShots_createSketch_drawProfilesAndSketchLines_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_SKETCH );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();
    mSupport.captureAndAssertScreen( "sketch_screen.png" );
  }

  @Test
  public void exportZip_includesSketchLineSymbols_and_importRoundTripsThroughPicker() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_ZIP );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();

    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_ZIP );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.setCheckboxChecked( R.id.zip_symbols, true );
    mSupport.setZipSymbolsExportEnabled( true );
    mSupport.tapView( R.id.button_ok );

    File zipFile = mSupport.waitForFile( mSupport.getZipFile( SURVEY_ZIP ), VisualTestSupport.FILE_TIMEOUT_MS );
    mSupport.assertZipContainsSketchLineSymbols( zipFile );
    File importZip = mSupport.copyFileToDownloads( zipFile );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_delete ) );
    mSupport.confirmAlertOk();
    mSupport.relaunchMainWindow();

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapView( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_ZIP );

    mSupport.openSurveyFromMainList( SURVEY_ZIP );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.setCanonicalToolbarState();
    mSupport.captureAndAssertScreen( "zip_roundtrip_screen.png" );
  }

  @Test
  public void exportPng_matchesGolden() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyAndOpenSketch( SURVEY_PNG );
    drawCanonicalSketch();
    mSupport.setCanonicalToolbarState();

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_EXPORT_FILENAME );
    mSupport.tapView( R.id.button_ok );

    File pngFile = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_PNG, PNG_EXPORT_FILENAME ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "PNG export was not created", pngFile.exists() );
    mSupport.assertPngFileMatchesGolden( pngFile, "export_png.png" );
  }

  @Test
  public void exportCompass_matchesFixture() throws Exception
  {
    mSupport.prepareForCase(
      VisualTestSupport.allSurveyNames( SURVEY_SKETCH, SURVEY_ZIP, SURVEY_PNG, SURVEY_COMPASS )
    );
    mSupport.launchMainWindow();
    createCanonicalSurveyWithShots( SURVEY_COMPASS );
    mSupport.pressBackToMainWindow();

    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_COMPASS );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "Compass" );
    mSupport.tapView( R.id.button_ok );

    File datFile = mSupport.waitForFile(
      mSupport.getCompassExportFile( SURVEY_COMPASS ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "Compass export was not created", datFile.exists() );
    mSupport.assertTextFileMatchesGolden( datFile, "export_compass.dat", VisualTestSupport.COMPASS_NORMALIZER );
  }

  private void createCanonicalSurveyWithShots( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Visual Test Team", "1", "visual regression" );
    addCanonicalShots();
  }

  private void createCanonicalSurveyAndOpenSketch( String surveyName )
  {
    createCanonicalSurveyWithShots( surveyName );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private void addCanonicalShots()
  {
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", false );
    mSupport.addManualShot( "2", "4", "5.0", "180.0", "0.0", true );
  }

  private void drawCanonicalSketch()
  {
    mSupport.enterDrawMode();

    mSupport.tapProfileButton( R.id.button_profile_1 );
    mSupport.clickRecentLineButton( 1 );
    mSupport.drawStrokeNormalized( 0.16, 0.20, 0.42, 0.22, 40 );

    mSupport.tapProfileButton( R.id.button_profile_2 );
    mSupport.clickRecentLineButton( 1 );
    mSupport.drawStrokeNormalized( 0.56, 0.22, 0.84, 0.28, 40 );

    mSupport.tapProfileButton( R.id.button_profile_1 );
    mSupport.clickRecentLineButton( 0 );
    mSupport.drawStrokeNormalized( 0.14, 0.44, 0.36, 0.52, 36 );

    mSupport.clickRecentLineButton( 1 );
    mSupport.drawStrokeNormalized( 0.46, 0.46, 0.78, 0.60, 38 );

    mSupport.clickRecentLineButton( 2 );
    mSupport.drawStrokeNormalized( 0.22, 0.72, 0.80, 0.76, 44 );
  }
}
