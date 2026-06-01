/* @file SketchLineSymbolManager.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief TopoDroid sketch line symbols
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.prefs.TDSetting;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Locale;

class SketchLineSymbolManager
{
  // Keep the stored filenames, th_names, and preference keys stable for compatibility.
  static final String LEGACY_TH_NAME_FINE     = "u:user-fine";
  static final String LEGACY_TH_NAME_STANDARD = "u:user-standard";
  static final String LEGACY_TH_NAME_THICK    = "u:user-thick";

  static final String LEGACY_FILE_FINE     = "user-fine";
  static final String LEGACY_FILE_STANDARD = "user-standard";
  static final String LEGACY_FILE_THICK    = "user-thick";

  private static final String DB_KEY_RECENTS_SEEDED = "personal_sketch_lines_seeded";

  private static final String[] TH_NAMES = {
    LEGACY_TH_NAME_FINE,
    LEGACY_TH_NAME_STANDARD,
    LEGACY_TH_NAME_THICK
  };

  private static final String[] FILENAMES = {
    LEGACY_FILE_FINE,
    LEGACY_FILE_STANDARD,
    LEGACY_FILE_THICK
  };

  private static final String[] DISPLAY_NAMES = {
    "Sketch_Fine",
    "Sketch_Standard",
    "Sketch_Thick"
  };

  private static final String[] LEGACY_PREF_KEYS = {
    "DISTOX_USER_LINE_FINE_WIDTH",
    "DISTOX_USER_LINE_STANDARD_WIDTH",
    "DISTOX_USER_LINE_THICK_WIDTH"
  };

  private static final String[] LEGACY_COLOR_PREF_KEYS = {
    "DISTOX_USER_LINE_FINE_COLOR",
    "DISTOX_USER_LINE_STANDARD_COLOR",
    "DISTOX_USER_LINE_THICK_COLOR"
  };

  private static final float[] DEFAULT_WIDTHS = {
    TDSetting.DEFAULT_USER_LINE_FINE_WIDTH,
    TDSetting.DEFAULT_USER_LINE_STANDARD_WIDTH,
    TDSetting.DEFAULT_USER_LINE_THICK_WIDTH
  };

  private static final String[] RECENT_TH_NAMES = {
    LEGACY_FILE_FINE,
    LEGACY_FILE_STANDARD,
    LEGACY_FILE_THICK
  };

  private SketchLineSymbolManager() { }

  static void ensureLineSymbols()
  {
    ensureLineSymbol( 0, TDSetting.mUserLineFineWidth, TDSetting.mUserLineFineColor );
    ensureLineSymbol( 1, TDSetting.mUserLineStandardWidth, TDSetting.mUserLineStandardColor );
    ensureLineSymbol( 2, TDSetting.mUserLineThickWidth, TDSetting.mUserLineThickColor );
    ensureLineSymbolsEnabled();
  }

  static void writeLineSymbolFromSettings( int index )
  {
    if ( index < 0 || index >= FILENAMES.length ) return;
    float[] widths = {
      TDSetting.mUserLineFineWidth,
      TDSetting.mUserLineStandardWidth,
      TDSetting.mUserLineThickWidth
    };
    int[] colors = {
      TDSetting.mUserLineFineColor,
      TDSetting.mUserLineStandardColor,
      TDSetting.mUserLineThickColor
    };
    writeLineSymbol( index, widths[index], colors[index] );
    ensureLineSymbolsEnabled();
  }

  static void onLineLibraryLoaded()
  {
    ensureLoadedSymbolsEnabled();
    seedRecentLinesIfNeeded();
  }

  static void syncPrefsFromSymbolFiles()
  {
    if ( TDInstance.context == null ) return;

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( TDInstance.context );
    float[] widths = {
      TDSetting.mUserLineFineWidth,
      TDSetting.mUserLineStandardWidth,
      TDSetting.mUserLineThickWidth
    };
    int[] colors = {
      TDSetting.mUserLineFineColor,
      TDSetting.mUserLineStandardColor,
      TDSetting.mUserLineThickColor
    };

    for ( int k = 0; k < FILENAMES.length; ++k ) {
      File file = TDPath.getLineFile( FILENAMES[k] );
      Float width = readWidth( file );
      if ( width != null ) {
        float value = ( width.floatValue() > 0 ) ? width.floatValue() : DEFAULT_WIDTHS[k];
        widths[k] = value;
        TDSetting.setPreference( prefs, LEGACY_PREF_KEYS[k], Float.toString( value ) );
      }

      Integer color = readColor( file );
      if ( color != null ) {
        colors[k] = color.intValue() & 0x00ffffff;
        TDSetting.setPreference( prefs, LEGACY_COLOR_PREF_KEYS[k], Integer.toString( colors[k] ) );
      }
    }

    TDSetting.mUserLineFineWidth     = widths[0];
    TDSetting.mUserLineStandardWidth = widths[1];
    TDSetting.mUserLineThickWidth    = widths[2];
    TDSetting.mUserLineFineColor     = colors[0];
    TDSetting.mUserLineStandardColor = colors[1];
    TDSetting.mUserLineThickColor    = colors[2];
  }

  static String[] getLineFilenames()
  {
    return FILENAMES.clone();
  }

  private static void ensureLineSymbol( int index, float width, int color )
  {
    File file = TDPath.getLineFile( FILENAMES[index] );
    if ( file.exists() ) return;
    writeLineSymbol( index, width, color );
  }

  private static void writeLineSymbol( int index, float width, int color )
  {
    PrintWriter pw = null;
    try {
      File file = TDPath.getLineFile( FILENAMES[index] );
      File parent = file.getParentFile();
      if ( parent != null && ! parent.exists() && ! parent.mkdirs() ) {
        TDLog.e( "sketch line mkdir error: " + parent.getAbsolutePath() );
        return;
      }
      FileWriter fw = new FileWriter( file );
      pw = new PrintWriter( fw );
      pw.format( Locale.US, "symbol line\n" );
      pw.format( Locale.US, "name %s\n", DISPLAY_NAMES[index] );
      pw.format( Locale.US, "th_name %s\n", TH_NAMES[index] );
      pw.format( Locale.US, "group %s\n", SymbolLibrary.USER );
      pw.format( Locale.US, "color 0x%06x 0xff\n", color & 0x00ffffff );
      pw.format( Locale.US, "width %.4f\n", width );
      pw.format( Locale.US, "level %d\n", DrawingLevel.LEVEL_USER );
      pw.format( Locale.US, "roundtrip %d\n", Symbol.W2D_DETAIL_SHP );
      pw.format( Locale.US, "endsymbol\n" );
    } catch ( IOException e ) {
      TDLog.e( "sketch line write error: " + e.getMessage() );
    } finally {
      if ( pw != null ) pw.close();
    }
  }

  private static void ensureLineSymbolsEnabled()
  {
    if ( TopoDroidApp.mData == null ) return;
    for ( String thName : TH_NAMES ) {
      TopoDroidApp.mData.setSymbolEnabled( "l_" + Symbol.deprefix_u( thName ), true );
    }
  }

  private static void ensureLoadedSymbolsEnabled()
  {
    for ( String thName : TH_NAMES ) {
      Symbol symbol = BrushManager.getLineByThName( thName );
      if ( symbol != null ) symbol.setEnabled( true );
    }
  }

  private static void seedRecentLinesIfNeeded()
  {
    if ( TopoDroidApp.mData == null ) return;
    String seeded = TopoDroidApp.mData.getValue( DB_KEY_RECENTS_SEEDED );
    if ( "1".equals( seeded ) ) return;

    Symbol[] sketchLines = new Symbol[ RECENT_TH_NAMES.length ];
    for ( int k = 0; k < RECENT_TH_NAMES.length; ++k ) {
      sketchLines[k] = BrushManager.getLineByThName( RECENT_TH_NAMES[k] );
    }
    ItemDrawer.prependRecentLines( sketchLines );

    String recentLines = ItemDrawer.serializeRecentLines();
    if ( recentLines.length() > 0 ) {
      TopoDroidApp.mData.setValue( "recent_lines", recentLines );
      TopoDroidApp.mData.setValue( DB_KEY_RECENTS_SEEDED, "1" );
    }
  }

  private static Float readWidth( File file )
  {
    if ( file == null || ! file.exists() ) return null;

    BufferedReader br = null;
    try {
      FileInputStream fis = new FileInputStream( file );
      br = new BufferedReader( new InputStreamReader( fis, "UTF-8" ) );
      String line;
      while ( ( line = br.readLine() ) != null ) {
        line = line.trim();
        if ( ! line.startsWith( "width" ) ) continue;
        String[] vals = line.split( "\\s+" );
        if ( vals.length > 1 ) return Float.parseFloat( vals[1] );
      }
    } catch ( IOException e ) {
      TDLog.e( "sketch line read error: " + e.getMessage() );
    } catch ( NumberFormatException e ) {
      TDLog.e( "sketch line width parse error: " + e.getMessage() );
    } finally {
      if ( br != null ) {
        try {
          br.close();
        } catch ( IOException e ) {
          TDLog.e( "sketch line close error: " + e.getMessage() );
        }
      }
    }
    return null;
  }

  private static Integer readColor( File file )
  {
    if ( file == null || ! file.exists() ) return null;

    BufferedReader br = null;
    try {
      FileInputStream fis = new FileInputStream( file );
      br = new BufferedReader( new InputStreamReader( fis, "UTF-8" ) );
      String line;
      while ( ( line = br.readLine() ) != null ) {
        line = line.trim();
        if ( ! line.startsWith( "color" ) ) continue;
        String[] vals = line.split( "\\s+" );
        if ( vals.length > 1 ) return Integer.decode( vals[1] ) & 0x00ffffff;
      }
    } catch ( IOException e ) {
      TDLog.e( "sketch line read error: " + e.getMessage() );
    } catch ( NumberFormatException e ) {
      TDLog.e( "sketch line color parse error: " + e.getMessage() );
    } finally {
      if ( br != null ) {
        try {
          br.close();
        } catch ( IOException e ) {
          TDLog.e( "sketch line close error: " + e.getMessage() );
        }
      }
    }
    return null;
  }
}
