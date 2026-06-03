package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
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
public class ReferenceImageInstrumentedTest
{
  private static final String SURVEY_VISIBLE = "visual_reference_visible_case";
  private static final String SURVEY_HIDDEN  = "visual_reference_hidden_case";
  private static final String SURVEY_SCREEN  = "visual_reference_screen_case";
  private static final String SURVEY_SCALE   = "visual_reference_scale_case";
  private static final String SURVEY_ERASE_SAFE = "visual_reference_erase_safe_case";
  private static final String SURVEY_ERASE_ENABLED = "visual_reference_erase_enabled_case";
  private static final String SURVEY_ZIP     = "visual_reference_zip_case";
  private static final String PLOT_NAME      = "1";

  private static final String PNG_VISIBLE_EXPORT = "reference_visible_export.png";
  private static final String PNG_HIDDEN_EXPORT  = "reference_hidden_export.png";
  private static final String SCREEN_VISIBLE_CAPTURE = "reference_visible_screen.png";

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
  public void exportPng_includesVisibleReferenceImage() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_VISIBLE );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-visible.png", false );
    mSupport.insertReferenceFromFile( reference, 0.22, 0.22 );
    mSupport.transformFirstReference( 1.35f, 22.0, 1.0f, true, 40.0f, -20.0f );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_VISIBLE_EXPORT );
    mSupport.tapView( R.id.button_ok );

    File pngFile = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_VISIBLE, PNG_VISIBLE_EXPORT ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "PNG export was not created", pngFile.exists() );
    mSupport.assertBitmapContainsColor( pngFile, 0xffff00ff, 30, 80 );
    mSupport.assertBitmapContainsColor( pngFile, 0xff00ffff, 30, 80 );
    mSupport.assertBitmapContainsColor( pngFile, 0xffffff00, 30, 80 );
  }

  @Test
  public void exportPng_omitsHiddenReferenceImage() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_HIDDEN );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-hidden.png", false );
    mSupport.insertReferenceFromFile( reference, 0.22, 0.22 );
    mSupport.transformFirstReference( 1.35f, 22.0, 1.0f, false, 40.0f, -20.0f );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "PNG" );
    mSupport.replaceTextInField( R.id.png_filename, PNG_HIDDEN_EXPORT );
    mSupport.tapView( R.id.button_ok );

    File pngFile = mSupport.waitForFile(
      mSupport.getPngExportFile( SURVEY_HIDDEN, PNG_HIDDEN_EXPORT ),
      VisualTestSupport.FILE_TIMEOUT_MS
    );
    assertTrue( "PNG export was not created", pngFile.exists() );
    mSupport.assertBitmapDoesNotContainColor( pngFile, 0xffff00ff, 30, 0 );
    mSupport.assertBitmapDoesNotContainColor( pngFile, 0xff00ffff, 30, 0 );
    mSupport.assertBitmapDoesNotContainColor( pngFile, 0xffffff00, 30, 0 );
  }

  @Test
  public void zipRoundTrip_restoresReferenceMetadataAndAsset() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_ZIP );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-zip.jpg", true );
    mSupport.insertReferenceFromFile( reference, 0.24, 0.24 );
    mSupport.transformFirstReference( 1.20f, 33.0, 0.75f, true, 55.0f, -15.0f );
    VisualTestSupport.ReferenceSnapshot before = mSupport.getFirstReferenceSnapshot();

    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( SURVEY_ZIP );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.tapView( R.id.button_ok );

    File zipFile = mSupport.waitForFile( mSupport.getZipFile( SURVEY_ZIP ), VisualTestSupport.FILE_TIMEOUT_MS );
    File importZip = mSupport.copyFileToDownloads( zipFile );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_delete ) );
    mSupport.confirmAlertOk();
    mSupport.relaunchMainWindow();
    mSupport.waitForSurveyAbsentInDatabase( SURVEY_ZIP );

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapView( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY_ZIP );

    mSupport.openSurveyFromMainList( SURVEY_ZIP );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    VisualTestSupport.ReferenceSnapshot after = mSupport.getFirstReferenceSnapshot();

    assertEquals( "Reference source name changed across ZIP round-trip", before.sourceName, after.sourceName );
    assertEquals( "Reference alpha changed across ZIP round-trip", before.alphaPercent, after.alphaPercent );
    assertEquals( "Reference visibility changed across ZIP round-trip", before.visible, after.visible );
    assertEquals( "Reference width changed across ZIP round-trip", before.sceneWidth, after.sceneWidth, 0.5f );
    assertEquals( "Reference height changed across ZIP round-trip", before.sceneHeight, after.sceneHeight, 0.5f );
    assertEquals( "Reference orientation changed across ZIP round-trip", before.orientation, after.orientation, 0.5 );
    assertTrue( "Reference asset was not restored to the survey photo directory",
      mSupport.getSurveyPhotoFile( SURVEY_ZIP, after.sourceName ).exists() );
  }

  @Test
  public void liveScreen_showsVisibleReferenceImage() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_SCREEN );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-screen.png", false );
    mSupport.insertReferenceFromFile( reference, 0.22, 0.22 );
    mSupport.transformFirstReference( 1.35f, 22.0, 1.0f, true, 40.0f, -20.0f );

    File screenFile = mSupport.captureScreen( SCREEN_VISIBLE_CAPTURE );
    assertTrue( "Screen capture was not created", screenFile.exists() );
    mSupport.assertBitmapContainsColor( screenFile, 0xffff00ff, 30, 80 );
    mSupport.assertBitmapContainsColor( screenFile, 0xff00ffff, 30, 80 );
    mSupport.assertBitmapContainsColor( screenFile, 0xffffff00, 30, 80 );
  }

  @Test
  public void cornerHandleDrag_scalesReferenceImage() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_SCALE );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-scale.png", false );
    mSupport.insertReferenceFromFile( reference, 0.24, 0.24 );
    VisualTestSupport.ReferenceSnapshot before = mSupport.getFirstReferenceSnapshot();

    mSupport.dragFirstReferenceHandle( ReferencePointHelper.HANDLE_SCALE_SE, 45.0f, 35.0f );
    VisualTestSupport.ReferenceSnapshot after = mSupport.getFirstReferenceSnapshot();

    assertTrue( "Corner handle drag did not increase reference width", after.sceneWidth > before.sceneWidth + 5.0f );
    assertTrue( "Corner handle drag did not increase reference height", after.sceneHeight > before.sceneHeight + 5.0f );
    assertEquals( "Corner handle drag should not rotate the reference", before.orientation, after.orientation, 0.01 );
  }

  @Test
  public void eraser_keepsProtectedReferenceWhileRemovingLine() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_ERASE_SAFE );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-erase-safe.png", false );
    mSupport.setReferenceEraseEnabled( false );
    mSupport.insertReferenceFromFile( reference, 0.24, 0.24 );
    mSupport.addStraightLineAcrossFirstReference();

    int referenceCountBefore = mSupport.countReferencePoints();
    int lineCountBefore = mSupport.countSketchLinePaths();
    mSupport.eraseAtFirstReferenceCenter( Drawing.FILTER_ALL, 60.0f );

    assertEquals( "Protected reference image was erased", referenceCountBefore, mSupport.countReferencePoints() );
    assertTrue( "Line over the protected reference was not erased", mSupport.countSketchLinePaths() < lineCountBefore );
  }

  @Test
  public void eraser_canDeleteReferenceWhenEnabled() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY_VISIBLE, SURVEY_HIDDEN, SURVEY_SCREEN, SURVEY_SCALE, SURVEY_ERASE_SAFE, SURVEY_ERASE_ENABLED, SURVEY_ZIP ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch( SURVEY_ERASE_ENABLED );

    File reference = mSupport.createReferenceFixtureInDownloads( "reference-erase-enabled.png", false );
    mSupport.setReferenceEraseEnabled( true );
    mSupport.insertReferenceFromFile( reference, 0.24, 0.24 );
    assertEquals( "Reference image was not inserted", 1, mSupport.countReferencePoints() );

    mSupport.eraseAtFirstReferenceCenter( Drawing.FILTER_ALL, 60.0f );

    assertEquals( "Reference image should be erasable when the preference is enabled", 0, mSupport.countReferencePoints() );
  }

  private void createSurveyAndOpenSketch( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Visual Test Team", "1", "reference regression" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }
}
