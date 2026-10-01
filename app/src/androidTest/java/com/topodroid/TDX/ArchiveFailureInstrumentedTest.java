package com.topodroid.TDX;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.util.TDVersion;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith( AndroidJUnit4.class )
public class ArchiveFailureInstrumentedTest
{
  private static final String SURVEY = "archive_failure_contract";

  private VisualTestSupport mSupport;
  private TopoDroidApp mApp;

  @Before public void setUp() throws Exception
  {
    mSupport = new VisualTestSupport( "archive_failure_contract" );
    mSupport.prepareForCase( VisualTestSupport.allSurveyNames( SURVEY ) );
    mSupport.launchMainWindow();
    mApp = (TopoDroidApp)InstrumentationRegistry.getInstrumentation()
      .getTargetContext().getApplicationContext();
  }

  @After public void tearDown()
  {
    if ( mSupport != null ) {
      mSupport.deleteGeneratedSurveyAndArtifacts( SURVEY );
      mSupport.finish();
    }
  }

  @Test public void validManifestWithoutSql_isRejectedWithoutPartialSurvey() throws Exception
  {
    LinkedHashMap< String, byte[] > entries = new LinkedHashMap<>();
    entries.put( "manifest", manifestBytes() );
    byte[] archive = zip( entries );

    assertTrue( Archiver.getOkManifest( mApp, input( archive ) ) >= 0 );
    assertTrue( "Archive without survey.sql reported success", Archiver.unArchive( mApp, input( archive ) ) < 0 );
    assertFalse( "Failed import left a survey row", TopoDroidApp.mData.hasSurveyName( SURVEY ) );
  }

  @Test public void malformedSql_isRejectedWithoutPartialSurvey() throws Exception
  {
    LinkedHashMap< String, byte[] > entries = new LinkedHashMap<>();
    entries.put( "manifest", manifestBytes() );
    entries.put( "survey.sql", "not a survey database dump\n".getBytes( StandardCharsets.UTF_8 ) );
    byte[] archive = zip( entries );

    assertTrue( Archiver.getOkManifest( mApp, input( archive ) ) >= 0 );
    assertTrue( "Malformed survey.sql reported success", Archiver.unArchive( mApp, input( archive ) ) < 0 );
    assertFalse( "Malformed SQL left a survey row", TopoDroidApp.mData.hasSurveyName( SURVEY ) );
  }

  @Test public void traversalEntry_isRejectedWithoutWritingSurvey() throws Exception
  {
    LinkedHashMap< String, byte[] > entries = new LinkedHashMap<>();
    entries.put( "manifest", manifestBytes() );
    entries.put( "../survey.sql", "payload".getBytes( StandardCharsets.UTF_8 ) );
    byte[] archive = zip( entries );

    assertTrue( Archiver.getOkManifest( mApp, input( archive ) ) >= 0 );
    assertTrue( "Traversal archive reported success", Archiver.unArchive( mApp, input( archive ) ) < 0 );
    assertFalse( "Traversal archive left a survey row", TopoDroidApp.mData.hasSurveyName( SURVEY ) );
  }

  @Test public void fileImportWithoutSql_isRejectedWithoutPartialSurvey() throws Exception
  {
    LinkedHashMap< String, byte[] > entries = new LinkedHashMap<>();
    entries.put( "manifest", manifestBytes() );
    byte[] archive = zip( entries );
    File archiveFile = new File(
      InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),
      "archive-failure-contract.zip" );

    try {
      try ( FileOutputStream output = new FileOutputStream( archiveFile ) ) {
        output.write( archive );
      }
      assertTrue( "File import without survey.sql reported success",
        new Archiver().unArchive( mApp, archiveFile.getAbsolutePath(), false ) < 0 );
      assertFalse( "Failed file import left a survey row", TopoDroidApp.mData.hasSurveyName( SURVEY ) );
    } finally {
      archiveFile.delete();
    }
  }

  private static byte[] manifestBytes()
  {
    String manifest = TDVersion.string() + " " + TDVersion.compatCode() + "\n"
      + TDVersion.DB_VERSION + "\n" + SURVEY + "\n2026.09.30\n";
    return manifest.getBytes( StandardCharsets.UTF_8 );
  }

  private static ByteArrayInputStream input( byte[] bytes )
  {
    return new ByteArrayInputStream( bytes );
  }

  private static byte[] zip( LinkedHashMap< String, byte[] > entries ) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream( bytes );
    for ( Map.Entry< String, byte[] > entry : entries.entrySet() ) {
      zip.putNextEntry( new ZipEntry( entry.getKey() ) );
      zip.write( entry.getValue() );
      zip.closeEntry();
    }
    zip.close();
    return bytes.toByteArray();
  }
}
