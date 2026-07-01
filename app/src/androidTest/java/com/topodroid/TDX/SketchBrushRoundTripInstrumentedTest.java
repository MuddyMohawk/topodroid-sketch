package com.topodroid.TDX;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class SketchBrushRoundTripInstrumentedTest
{
  private static final String SURVEY = "brush_roundtrip_phase7_case";
  private static final String PLOT_NAME = "1";

  private VisualTestSupport mSupport;

  @Before
  public void setUp()
  {
    mSupport = new VisualTestSupport( "brush_roundtrip_phase7" );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @Test
  public void sketchZipRoundTrip_preservesStyledLineAndPointOptions() throws Exception
  {
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY ) );
    mSupport.launchMainWindow();
    createSurveyAndOpenSketch();

    mSupport.assertStyleBarVisible( "Thin", "Standard", "Thick" );
    mSupport.tapStyleButton( 3 );
    mSupport.clickRecentLineByThName( SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD );
    mSupport.drawCurveStrokeNormalized( 0.25, 0.40, 0.72, 0.44, 0.07, 12, 14 );
    mSupport.assertLatestLineBrushWeight( 5.0f );

    mSupport.addOrdinaryPointWithActiveStyle( 80.0f, 120.0f );
    mSupport.assertLatestPointBrushWeight( 5.0f );

    File importZip = exportCopyDeleteAndImportZip();

    assertTrue( "Imported ZIP missing: " + importZip.getAbsolutePath(), importZip.exists() );
    mSupport.openSurveyFromMainList( SURVEY );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.assertLatestLineBrushWeight( 5.0f );
    mSupport.assertLatestPointBrushWeight( 5.0f );
  }

  private void createSurveyAndOpenSketch()
  {
    mSupport.createSurveyAndOpenShots( SURVEY, "Brush Round Trip Team", "1", "brush style metadata round trip" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private File exportCopyDeleteAndImportZip() throws Exception
  {
    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( SURVEY );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.setCheckboxChecked( R.id.zip_symbols, true );
    mSupport.setZipSymbolsExportEnabled( true );
    mSupport.tapView( R.id.button_ok );

    File zipFile = mSupport.waitForFile( mSupport.getZipFile( SURVEY ), VisualTestSupport.FILE_TIMEOUT_MS );
    mSupport.assertZipContainsSketchLineSymbols( zipFile );
    File importZip = mSupport.copyFileToDownloads( zipFile );

    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_delete ) );
    mSupport.confirmAlertOk();
    mSupport.relaunchMainWindow();
    mSupport.waitForSurveyAbsentInDatabase( SURVEY );

    mSupport.openMainImportDialogFromToolbar();
    mSupport.tapView( R.id.button_ok );
    mSupport.pickDocumentByFileName( importZip.getName() );
    mSupport.waitForSurveyOnMainList( SURVEY );
    return importZip;
  }
}
