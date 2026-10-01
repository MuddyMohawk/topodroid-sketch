package com.topodroid.prefs;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.TDX.StylusOnlyInputController;
import com.topodroid.util.TDFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
public class StylusPreferenceInstrumentedTest
{
  private static byte[] read( File file ) throws IOException
  {
    try ( FileInputStream input = new FileInputStream( file ); ByteArrayOutputStream output = new ByteArrayOutputStream() ) {
      byte[] buffer = new byte[4096]; int count;
      while ( ( count = input.read( buffer ) ) >= 0 ) output.write( buffer, 0, count );
      return output.toByteArray();
    }
  }
  private static void write( File file, byte[] bytes ) throws IOException
  {
    try ( FileOutputStream output = new FileOutputStream( file ) ) { output.write( bytes ); }
  }

  /** Also run alone after seeding the transient boolean true in a stopped app process. */
  @Test public void freshProcessAndStoredThresholdCannotEnableProtection()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( context );
      assertFalse( StylusOnlyInputController.isEnabled() );
      assertFalse( prefs.getBoolean( StylusOnlyInputController.PREFERENCE, true ) );
      String old = prefs.getString( "DISTOX_STYLUS_SIZE", null );
      try {
        prefs.edit().putString( "DISTOX_STYLUS_SIZE", "100" ).commit();
        TDSetting.loadSecondaryPreferences( new TDPrefHelper( context ) );
        assertFalse( StylusOnlyInputController.isEnabled() );
        assertEquals( "100", prefs.getString( "DISTOX_STYLUS_SIZE", null ) );
      } finally {
        if ( old == null ) prefs.edit().remove( "DISTOX_STYLUS_SIZE" ).commit();
        else prefs.edit().putString( "DISTOX_STYLUS_SIZE", old ).commit();
      }
    } );
  }

  @Test public void settingsImportAndExportNeverContainOrEnableEitherStylusPreference()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( context );
      File imported = TDFile.getSettingsFile(), exported = new File( context.getCacheDir(), "stylus-settings-export-test.txt" );
      byte[] saved = null;
      try {
        if ( imported.exists() ) saved = read( imported );
        write( imported, ( "TopoDroid settings\nF DISTOX_STYLUS_SIZE 100\nB "
            + StylusOnlyInputController.PREFERENCE + " true\n" ).getBytes( "UTF-8" ) );
        assertTrue( TDSetting.importSettings( context, null, prefs, -1 ) );
        assertFalse( StylusOnlyInputController.isEnabled() );
        StylusOnlyInputController.setEnabled( true );
        assertTrue( TDSetting.exportSettings( context, Uri.fromFile( exported ), prefs, -1 ) );
        String text = new String( read( exported ), "UTF-8" );
        assertFalse( text.contains( "DISTOX_STYLUS_SIZE" ) );
        assertFalse( text.contains( StylusOnlyInputController.PREFERENCE ) );
      } catch ( IOException e ) { throw new AssertionError( e ); }
      finally {
        StylusOnlyInputController.setEnabled( false );
        try { if ( saved != null ) write( imported, saved ); else imported.delete(); }
        catch ( IOException e ) { throw new AssertionError( e ); }
        exported.delete();
      }
    } );
  }

  @Test public void replacementIsBasicAndBothModesAreExcludedFromSettingsInterchange()
  {
    boolean found = false;
    for ( TDPrefKey key : TDPrefKey.mMain ) {
      if ( StylusOnlyInputController.PREFERENCE.equals( key.key ) ) {
        found = true;
        assertEquals( 0, key.level );
        assertEquals( "false", key.dflt );
      }
    }
    assertTrue( found );
    assertFalse( TDPrefKey.checkKeyGroup( "DISTOX_STYLUS_SIZE", -1 ) );
    assertFalse( TDPrefKey.checkKeyGroup( StylusOnlyInputController.PREFERENCE, -1 ) );
    assertEquals( "DISTOX_BACKUP_NUMBER", TDPrefKey.mGeekPlot[2].key );
    assertEquals( "DISTOX_BACKUP_INTERVAL", TDPrefKey.mGeekPlot[3].key );
    assertEquals( "DISTOX_SAVED_STATIONS", TDPrefKey.mGeekPlot[4].key );
    assertEquals( "DISTOX_TOOLBAR_UPDATE", TDPrefKey.mGeekPlot[11].key );
  }

  @Test public void remainingAdvancedSettingsStillUpdateTheirOwnValues()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
      TDPrefHelper helper = new TDPrefHelper( context );
      int number = TDSetting.mBackupNumber, interval = TDSetting.mBackupInterval, oblique = TDSetting.mObliqueMax;
      boolean stations = TDSetting.mSavedStations, slanted = TDSetting.mSlantXSection;
      try {
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_BACKUP_NUMBER", "7" );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_BACKUP_INTERVAL", "123" );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_SAVED_STATIONS", "true" );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_SLANT_XSECTION", "true" );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_OBLIQUE_PROJECTED", "45" );
        assertEquals( 7, TDSetting.mBackupNumber );
        assertEquals( 123, TDSetting.mBackupInterval );
        assertTrue( TDSetting.mSavedStations ); assertTrue( TDSetting.mSlantXSection );
        assertEquals( 45, TDSetting.mObliqueMax );
      } finally {
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_BACKUP_NUMBER", Integer.toString( number ) );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_BACKUP_INTERVAL", Integer.toString( interval ) );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_SAVED_STATIONS", Boolean.toString( stations ) );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_SLANT_XSECTION", Boolean.toString( slanted ) );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_GEEK_PLOT, "DISTOX_OBLIQUE_PROJECTED", Integer.toString( oblique ) );
      }
    } );
  }
}
