package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExportRegistryTest
{
  @Test public void everyVisibleSurveyExportHasFormatFilenameAndMimeType()
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

  @Test public void everyVisiblePlotExportHasFormatFilenameExtensionAndMimeType()
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

  private static String extension( String filename )
  {
    return filename.substring( filename.lastIndexOf( '.' ) + 1 );
  }
}
