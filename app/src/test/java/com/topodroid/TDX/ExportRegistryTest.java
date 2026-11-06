package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.EXCHANGE})
public class ExportRegistryTest
{
  @Test public void everyRegisteredSurveyExportHasFormatFilenameAndMimeType()
  {
    for ( int position = 0; position < TDConst.mSurveyExportTypes.length; ++position ) {
      String type = TDConst.mSurveyExportTypes[position];
      int format = TDConst.surveyFormatIndex( type );
      String filename = TDConst.getSurveyFilename( position, "contract" );
      assertNotEquals( type, TDConst.SURVEY_FORMAT_NONE, format );
      assertNotNull( type, filename );
      assertTrue( type, filename.startsWith( "contract." ) );
      assertEquals( type, format, TDConst.getIndexFromExtension( extension( filename ) ) );
      assertNotNull( type, TDConst.getMimeType( format ) );
    }
  }

  @Test public void everyRegisteredPlotExportHasFormatFilenameExtensionAndMimeType()
  {
    for ( int position = 0; position < TDConst.mPlotExportTypes.length; ++position ) {
      String type = TDConst.mPlotExportTypes[position];
      int format = TDConst.plotExportIndex( type );
      String filename = TDConst.getPlotFilename( position, "contract" );
      assertNotEquals( type, TDConst.SURVEY_FORMAT_NONE, format );
      assertNotNull( type, filename );
      assertEquals( type, extension( filename ), TDConst.plotExportExt( type ) );
      assertEquals( type, format, TDConst.getIndexFromExtension( extension( filename ) ) );
      assertNotNull( type, TDConst.getMimeType( format ) );
    }
  }

  @Test public void surveyChoicesPreserveEnabledFormatsGeographicRestrictionsAndRegistryPositions()
  {
    boolean[] previous = TDConst.mSurveyExportEnable.clone();
    try {
      Arrays.fill( TDConst.mSurveyExportEnable, true );
      String[] expected = { "ZIP", "Compass", "cSurvey", "Polygon", "Survex", "Topo",
        "TopoRobot", "Walls", "WinKarst", "CSV", "DXF", "KML", "GPX", "Shapefile" };
      assertArrayEquals( expected, TDConst.surveyExportTypes( true ) );
      assertArrayEquals( Arrays.copyOf( expected, 11 ), TDConst.surveyExportTypes( false ) );
      ExportDialogFormats choices = ExportDialogFormats.survey( TDConst.surveyExportTypes( true ) );
      for ( int k = 0; k < choices.types.length; ++k ) {
        assertEquals( choices.position( k ), TDConst.surveyIndex( k ) );
        String filename = TDConst.getSurveyFilename( choices.position( k ), "contract" );
        assertEquals( choices.types[k], choices.format( k ), TDConst.getIndexFromExtension( extension( filename ) ) );
      }
      TDConst.mSurveyExportEnable[TDConst.SURVEY_POS_WALLS] = false;
      TDConst.mSurveyExportEnable[TDConst.SURVEY_POS_CSURVEY] = false;
      String[] visible = TDConst.surveyExportTypes( true );
      assertFalse( Arrays.asList( visible ).contains( "Walls" ) );
      assertFalse( Arrays.asList( visible ).contains( "cSurvey" ) );
      assertFalse( Arrays.asList( visible ).contains( "Therion" ) );
      assertFalse( Arrays.asList( visible ).contains( "VisualTopo" ) );
    } finally {
      System.arraycopy( previous, 0, TDConst.mSurveyExportEnable, 0, previous.length );
    }
  }

  @Test public void filteredSketchAndOverviewChoicesKeepTheirOriginalFilenames()
  {
    ExportDialogFormats plot = ExportDialogFormats.plot( TDConst.mPlotExportTypes, false );
    ExportDialogFormats overview = ExportDialogFormats.plot( TDConst.mOverviewExportTypes, true );
    assertArrayEquals( new String[] { "cSurvey", "DXF", "SVG", "Shapefile", "PDF", "XVI", "PNG" }, plot.types );
    assertArrayEquals( new String[] { "DXF", "SVG", "Shapefile", "PDF", "XVI" }, overview.types );
    for ( int k = 0; k < plot.types.length; ++k ) {
      String filename = TDConst.getPlotFilename( plot.position( k ), "contract" );
      assertEquals( plot.types[k], plot.format( k ), TDConst.getIndexFromExtension( extension( filename ) ) );
      assertEquals( extension( filename ), TDConst.plotExportExt( plot.types[k] ) );
    }
    for ( int k = 0; k < overview.types.length; ++k ) {
      String filename = TDConst.getOverviewFilename( overview.position( k ), "contract" );
      assertEquals( overview.types[k], overview.format( k ), TDConst.getIndexFromExtension( extension( filename ) ) );
      assertEquals( overview.format( k ), TDConst.overviewExportIndex( overview.types[k] ) );
    }
  }

  @Test public void defaultsUseVisibleFormatIdsAndSafeFallbacks()
  {
    ExportDialogFormats survey = ExportDialogFormats.survey( new String[] { "ZIP", "Survex", "CSV", "Therion", "VisualTopo" } );
    int csv = survey.selection( TDConst.SURVEY_FORMAT_CSV, TDConst.SURVEY_FORMAT_ZIP );
    assertEquals( TDConst.SURVEY_POS_CSV, survey.position( csv ) );
    assertEquals( "contract.csv", TDConst.getSurveyFilename( survey.position( csv ), "contract" ) );
    assertEquals( "ZIP", survey.types[survey.selection( TDConst.SURVEY_FORMAT_TH, TDConst.SURVEY_FORMAT_ZIP )] );
    assertEquals( "ZIP", survey.types[survey.selection( TDConst.SURVEY_FORMAT_TRO, TDConst.SURVEY_FORMAT_ZIP )] );
    assertEquals( "ZIP", survey.types[survey.selection( TDConst.SURVEY_FORMAT_SRV, TDConst.SURVEY_FORMAT_ZIP )] );

    ExportDialogFormats plot = ExportDialogFormats.plot( TDConst.mPlotExportTypes, false );
    assertEquals( "PNG", plot.types[plot.selection( TDConst.SURVEY_FORMAT_TH2, TDConst.SURVEY_FORMAT_PNG )] );
    assertEquals( "PNG", plot.types[plot.selection( TDConst.SURVEY_FORMAT_NONE, TDConst.SURVEY_FORMAT_PNG )] );
    assertEquals( "SVG", plot.types[plot.selection( TDConst.SURVEY_FORMAT_SVG, TDConst.SURVEY_FORMAT_PNG )] );

    ExportDialogFormats overview = ExportDialogFormats.plot( TDConst.mOverviewExportTypes, true );
    int svg = overview.selection( TDConst.SURVEY_FORMAT_SVG, TDConst.SURVEY_FORMAT_NONE );
    assertEquals( TDConst.OVERVIEW_POS_SVG, overview.position( svg ) );
    assertEquals( "DXF", overview.types[overview.selection( TDConst.SURVEY_FORMAT_PNG, TDConst.SURVEY_FORMAT_NONE )] );
  }

  @Test public void managerSurvexKeepsItsExtensionAfterTherionIsHidden()
  {
    ExportDialogFormats manager = ExportDialogFormats.survey( new String[] { "Therion", "Survex" } );
    assertArrayEquals( new String[] { "Survex" }, manager.types );
    int selected = manager.selection( TDConst.SURVEY_FORMAT_NONE, TDConst.SURVEY_FORMAT_NONE );
    assertEquals( "contract.svx", TDConst.getSurveyFilename( manager.position( selected ), "contract" ) );
    assertEquals( TDConst.SURVEY_FORMAT_SVX, manager.format( selected ) );
  }

  @Test public void supportedFormatsAreScopedToSurveyDataOrSketches()
  {
    for ( int format : TDConst.mSurveyExportIndex ) {
      boolean supported = format == TDConst.SURVEY_FORMAT_ZIP || format == TDConst.SURVEY_FORMAT_DAT || format == TDConst.SURVEY_FORMAT_CSV;
      assertEquals( supported, ExportDialogFormats.surveySupported( format ) );
    }
    for ( int format : TDConst.mPlotExportIndex ) {
      assertEquals( format == TDConst.SURVEY_FORMAT_PNG, ExportDialogFormats.plotSupported( format ) );
    }
    assertEquals( TDConst.SURVEY_FORMAT_TH, TDConst.surveyFormatIndex( "Therion" ) );
    assertEquals( TDConst.SURVEY_FORMAT_TRO, TDConst.surveyFormatIndex( "VisualTopo" ) );
    assertEquals( TDConst.SURVEY_FORMAT_TH2, TDConst.plotExportIndex( "Therion" ) );
  }

  private static String extension( String filename )
  {
    return filename.substring( filename.lastIndexOf( '.' ) + 1 );
  }
}
