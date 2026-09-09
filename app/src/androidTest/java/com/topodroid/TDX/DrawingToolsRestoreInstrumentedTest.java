package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.util.TDFile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith( AndroidJUnit4.class )
public class DrawingToolsRestoreInstrumentedTest
{
  private TopoDroidApp mApp;
  private DataHelper mData;

  @Before public void setUp()
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    assertTrue( context instanceof TopoDroidApp );
    mApp = (TopoDroidApp)context;
    TDInstance.setContext( context );
    if ( TopoDroidApp.mData == null ) TopoDroidApp.mData = new DataHelper( context );
    mData = TopoDroidApp.mData;
    mApp.restoreDrawingTools();
  }

  @After public void tearDown()
  {
    mApp.restoreDrawingTools();
  }

  @Test public void restoreRemovesCustomFilesAndResetsAllDrawingToolState() throws Exception
  {
    writeSentinel( "point", "custom-point" );
    writeSentinel( "line", "custom-line" );
    writeSentinel( "area", "custom-area" );
    mData.setValue( "p_custom-point", "1" );
    mData.setValue( "l_custom-line", "1" );
    mData.setValue( "a_custom-area", "1" );
    mData.setValue( "recent_points", "custom-point" );
    mData.setValue( "toolbar_row_0_points", "custom-point" );

    ToolsetProfile duplicate = ToolsetRepository.duplicate( mData,
      ToolsetRepository.profile( mData, ToolsetProfile.DEFAULT_ID ), "Restore fixture" );
    assertTrue( duplicate != null );
    assertTrue( ToolsetRepository.selectProfile( mData, 1234567L, duplicate.mId ) );

    mApp.restoreDrawingTools();

    assertNull( mData.getValue( "p_custom-point" ) );
    assertNull( mData.getValue( "l_custom-line" ) );
    assertNull( mData.getValue( "a_custom-area" ) );
    assertNull( mData.getValue( "recent_points" ) );
    assertEquals( Integer.toString( ItemDrawer.TOOLBAR_SEED_VERSION ),
                  mData.getValue( ItemDrawer.KEY_TOOLBAR_SEED ) );
    assertEquals( 1, ToolsetRepository.profiles( mData ).size() );
    assertEquals( ToolsetProfile.DEFAULT_ID, ToolsetRepository.activeProfileId( mData, 1234567L ) );
    assertEquals( 101, ToolsetCatalog.all().size() );

    Map< String, Set< String > > packaged = packagedFiles();
    assertEquals( packaged.get( "point" ), installedFiles( TDPath.getPointDir() ) );
    assertEquals( packaged.get( "line" ), installedFiles( TDFile.getPrivateDir( "line" ) ) );
    assertEquals( packaged.get( "area" ), installedFiles( TDFile.getPrivateDir( "area" ) ) );
  }

  @Test public void archiveSymbolBundleNamesAreRecognizedAndOnlyThoseNamesAreIgnored()
  {
    assertTrue( Archiver.isEmbeddedSymbolBundle( "points.zip" ) );
    assertTrue( Archiver.isEmbeddedSymbolBundle( "lines.zip" ) );
    assertTrue( Archiver.isEmbeddedSymbolBundle( "areas.zip" ) );
    assertFalse( Archiver.isEmbeddedSymbolBundle( "survey.zip" ) );
    assertFalse( Archiver.isEmbeddedSymbolBundle( "folder/lines.zip" ) );
    assertFalse( Archiver.isEmbeddedSymbolBundle( null ) );
  }

  private static void writeSentinel( String type, String name ) throws Exception
  {
    FileOutputStream output = new FileOutputStream( TDFile.getPrivateFile( type, name ) );
    try {
      output.write( ( "symbol\nname " + name + "\nth_name u:" + name + "\nendsymbol\n" ).getBytes( "UTF-8" ) );
    } finally {
      output.close();
    }
  }

  private Map< String, Set< String > > packagedFiles() throws Exception
  {
    Map< String, Set< String > > files = new HashMap<>();
    files.put( "point", new HashSet< String >() );
    files.put( "line", new HashSet< String >() );
    files.put( "area", new HashSet< String >() );
    ZipInputStream zip = new ZipInputStream(
      mApp.getResources().openRawResource( R.raw.symbols_topodroid_sketch ) );
    try {
      ZipEntry entry;
      while ( ( entry = zip.getNextEntry() ) != null ) {
        if ( entry.isDirectory() ) continue;
        String[] parts = entry.getName().split( "/" );
        if ( parts.length == 3 && files.containsKey( parts[1] ) ) files.get( parts[1] ).add( parts[2] );
      }
    } finally {
      zip.close();
    }
    return files;
  }

  private static Set< String > installedFiles( File directory )
  {
    String[] names = directory.list();
    assertTrue( names != null );
    return new HashSet<>( Arrays.asList( names ) );
  }
}
