package com.topodroid.TDX;

import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.types.PlotType;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.*;
import static org.junit.Assert.*;

/** Uses only uniquely named synthetic surveys; never clears the user's survey set. */
@RunWith( AndroidJUnit4.class )
public class SectionArchiveInstrumentedTest
{
  @Test public void nativeZipRoundTripsBothImportPathsAndToleratesMissingOrInvalidMetadata() throws Exception
  {
    boolean exitPolicy = TopoDroidApp.sayDialogR();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> TopoDroidApp.setSayDialogR( false ) );
    }
    TopoDroidApp app = (TopoDroidApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    DataHelper data = TopoDroidApp.mData;
    long oldSid = TDInstance.sid;
    String oldSurvey = TDInstance.survey;
    String name = "section_zip_" + System.nanoTime();
    File file = new File( app.getCacheDir(), name + ".zip" );
    long sid = data.setSurvey( name, 0 );
    try {
      long pid = data.insertPlot( sid, -1, "xx0", PlotType.PLOT_SECTION, 0, "A", "B", 0, 0, 1, 45, 0, "", "", 0 );
      SectionReferenceSelection choices = new SectionReferenceSelection();
      choices.setStation( "C", true ); choices.setSplays( "C", false ); choices.showOutOfPlane = false;
      assertTrue( SectionReferenceRepository.save( data, sid, pid, choices ) );
      TDInstance.sid = sid; TDInstance.survey = name;
      TDPath.setSurveyPaths( name );
      assertTrue( new Archiver().archiveSurvey( app, Uri.fromFile( file ) ) );
      Map<String,byte[]> entries = new LinkedHashMap<>();
      try ( ZipInputStream zip = new ZipInputStream( new FileInputStream( file ) ) ) {
        ZipEntry entry;
        while ( ( entry = zip.getNextEntry() ) != null ) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          byte[] buffer = new byte[4096]; int count;
          while ( ( count = zip.read( buffer ) ) != -1 ) bytes.write( buffer, 0, count );
          entries.put( entry.getName(), bytes.toByteArray() );
        }
      }
      assertTrue( entries.containsKey( SectionReferenceRepository.ARCHIVE_ENTRY ) );
      byte[] metadata = entries.remove( SectionReferenceRepository.ARCHIVE_ENTRY );
      // Put metadata before SQL to verify that restoration is independent of entry order.
      for ( int mode = 0; mode < 5; ++mode ) {
        data.doDeleteSurvey( sid );
        try ( ZipOutputStream zip = new ZipOutputStream( new FileOutputStream( file ) ) ) {
          if ( mode != 2 ) {
            zip.putNextEntry( new ZipEntry( SectionReferenceRepository.ARCHIVE_ENTRY ) );
            zip.write( mode < 2 ? metadata : ( mode == 3 ? "{invalid" : "{\"version\":99,\"sections\":{}}" ).getBytes( StandardCharsets.UTF_8 ) );
            zip.closeEntry();
          }
          for ( Map.Entry<String,byte[]> entry : entries.entrySet() ) {
            zip.putNextEntry( new ZipEntry( entry.getKey() ) ); zip.write( entry.getValue() ); zip.closeEntry();
          }
        }
        int result;
        if ( mode == 1 ) {
          // Legacy force imports require the destination survey paths to be set by their caller.
          TDPath.setSurveyPaths( name );
          result = new Archiver().unArchive( app, file.getAbsolutePath(), true );
        }
        else {
          try ( FileInputStream input = new FileInputStream( file ) ) { assertTrue( Archiver.getOkManifest( app, input ) >= 0 ); }
          try ( FileInputStream input = new FileInputStream( file ) ) { result = Archiver.unArchive( app, input ); }
        }
        assertTrue( "Survey import failed in mode " + mode + ": " + result, result >= 0 );
        sid = data.getSurveyIdFromName( name ); assertTrue( sid > 0 );
        PlotInfo plot = data.getPlotInfo( sid, "xx0" ); assertNotNull( plot );
        SectionReferenceSelection restored = SectionReferenceRepository.load( data, sid, plot, java.util.Collections.emptyList() );
        if ( mode < 2 ) {
          assertEquals( choices.stations, restored.stations ); assertEquals( choices.hiddenSplays, restored.hiddenSplays );
          assertFalse( restored.showOutOfPlane );
        } else {
          assertTrue( restored.includes( "A" ) ); assertTrue( restored.includes( "B" ) );
          assertTrue( restored.showOutOfPlane ); assertNull( data.getValue( SectionReferenceRepository.key( sid, plot.id ) ) );
        }
      }
    } finally {
      data.doDeleteSurvey( sid );
      file.delete();
      TDInstance.sid = oldSid; TDInstance.survey = oldSurvey; TDPath.setSurveyPaths( oldSurvey );
      TopoDroidApp.setSayDialogR( exitPolicy );
    }
  }
}
