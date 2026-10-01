/* @file SymbolLineLibrary.java
 *
 * @author marco corvi
 * @date dec 2012
 *
 * @brief TopoDroid drawing: line symbol library
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.util.TDFile;
import com.topodroid.util.TDLocale;

// import java.util.Locale;
// import java.util.ArrayList;
// import java.util.TreeSet;

import java.io.File; // external app files

import android.graphics.Paint;
import android.content.res.Resources;

public class SymbolLineLibrary extends SymbolLibrary
{
  int mLineUserIndex; // PRIVATE
  int mLineWallIndex;
  int mLineSlopeIndex;
  int mLineSectionIndex;

  SymbolLineLibrary( Resources res )
  {
    super( "l_" );
    mLineUserIndex    = -1;
    mLineWallIndex    = -1;
    mLineSlopeIndex   = -1;
    mLineSectionIndex = -1;
    loadUserLines();
    initIndices();
  }

  void initIndices()
  {
    // makeEnabledList();
    makeConfigList();
    makeSpecialIndices();
  }

  // int size() { return mLine.size(); }

  boolean isStyleStraight( int k ) { return ( k < 0 || k >= size() ) || ((SymbolLine)mSymbols.get(k)).mStyleStraight; }

  boolean isClosed( int k ) { return k >= 0 && k < size() && ((SymbolLine)mSymbols.get(k)).mClosed; }

  int getStyleX( int k ) { return ( k < 0 || k >= size() )? 1 : ((SymbolLine)mSymbols.get(k)).mStyleX; }

  // String getLineGroup( int k ) { return ( k < 0 || k >= size() )? null : ((SymbolLine)mSymbols.get(k)).mGroup; }

  boolean isWall( int k ) { return k >= 0 && k < size() && WALL.equals(((SymbolLine)mSymbols.get(k)).mGroup); }

  boolean hasEffect( int k ) { return k >= 0  && k < size() && ((SymbolLine)mSymbols.get(k)).mHasEffect; }

  LineSymbolEffect getLineEffect( int k )
  {
    if ( k < 0 || k >= size() ) return null;
    return ((SymbolLine)mSymbols.get(k)).mLineEffect;
  }

  boolean hasPathEffect( int k )
  {
    return hasEffect( k );
  }

  /** @return dash intervals [line-width units] of the symbol, or null */
  float[] getLineDashBase( int k )
  {
    if ( k < 0 || k >= size() ) return null;
    return ((SymbolLine)mSymbols.get(k)).getDashBase();
  }

  Paint getLinePaint( int k, boolean reversed )
  {
    if ( k < 0 || k >= size() ) return BrushManager.errorPaint;
    SymbolLine s = (SymbolLine)mSymbols.get(k);
    return reversed ? s.mRevPaint : s.mPaint;
  }

  // ========================================================================

  void loadUserLines()
  {
    String locale = "name-" + TDLocale.getLocaleCode(); // TopoDroidApp.mLocale.toString().substring(0,2);
    String iso = "ISO-8859-1";
    // String iso = "UTF-8";
    // if ( locale.equals( "name-es" ) ) iso = "ISO-8859-1";

    File dir = TDFile.getPrivateDir( TDPath.getSymbolLineDirname() );
    if ( dir.exists() ) {
      for ( String name : RequiredLineSymbols.names() ) loadRequiredLine( dir, name, locale, "UTF-8" );
      int systemNr = mSymbols.size();
      File[] files = dir.listFiles();
      if ( files == null ) return;
      for ( File file : files ) {
        String fname = file.getName();
        if ( RequiredLineSymbols.isRequired( fname ) ) continue;

        SymbolLine symbol = new SymbolLine( file.getPath(), fname, locale, iso );
        if ( symbol.isThName( null ) ) {
          TDLog.e( "Line Symbol with null ThName: " + fname );
          continue;
        }
        if ( RequiredLineSymbols.isRequired( symbol.getThName() ) ) {
          TDLog.e( "Required line symbol " + symbol.getThName() + " must use its reserved filename" );
        } else if ( ! hasSymbolByThName( symbol.getThName() ) ) {
          addSymbol( symbol );
          String thname = symbol.getThName();
          String name = mPrefix + thname;
          boolean enable = SymbolDefaultPolicy.isLineEnabled( thname );
          if ( TopoDroidApp.mData != null ) {
            if ( ! TopoDroidApp.mData.hasSymbolName( name ) ) {
              TopoDroidApp.mData.setSymbolEnabled( name, enable ); // CONFIG_ENABLE
            } else {
              enable = TopoDroidApp.mData.getSymbolEnabled( name );
            }
          }
          symbol.setEnabled( enable );
        } else {
          TDLog.e( "Line Symbol " + symbol.getThName() + " already in library" );
          // TDLog.v( "line " + symbol.getThname() + " already in library" );
        }
      }
      // mSymbolNr = mSymbols.size();
      sortSymbolByName( systemNr );
    } else {
      if ( ! dir.mkdirs( ) ) TDLog.e( "mkdir error" );
    }
  }

  /** Load required workflow lines first, recovering an invalid or missing file from the packaged symbol set. */
  private void loadRequiredLine( File dir, String fname, String locale, String iso )
  {
    File file = new File( dir, fname );
    SymbolLine symbol = readRequiredLine( file, fname, locale, iso );
    if ( symbol == null ) {
      TopoDroidApp.installRequiredLineSymbol( fname, true );
      symbol = readRequiredLine( file, fname, locale, iso );
    }
    if ( symbol == null ) {
      TDLog.e( "Cannot load required line symbol " + fname );
      return;
    }

    addSymbol( symbol );
    symbol.setEnabled( true );
    if ( TopoDroidApp.mData != null ) TopoDroidApp.mData.setSymbolEnabled( mPrefix + fname, true );
  }

  private SymbolLine readRequiredLine( File file, String fname, String locale, String iso )
  {
    if ( ! file.exists() || ! file.isFile() ) return null;
    SymbolLine symbol = new SymbolLine( file.getPath(), fname, locale, iso );
    if ( ! fname.equals( Symbol.deprefix_u( symbol.getThName() ) ) ) {
      TDLog.e( "Required line file " + fname + " has the wrong th_name" );
      return null;
    }
    String error = RequiredLineSymbols.semanticError( symbol );
    if ( error != null ) {
      TDLog.e( "Required line file " + fname + ": " + error );
      return null;
    }
    return symbol;
  }

  /* LOAD_MISSING - APP_SAVE SYMBOLS
  // thname  symbol th-name
  boolean tryLoadMissingLine( String thname )
  {
    String locale = "name-" + Locale.getDefault().toString().substring(0,2);
    String iso = "ISO-8859-1";
    // String iso = "UTF-8";
    // if ( locale.equals( "name-es" ) ) iso = "ISO-8859-1";

    if ( isSymbolEnabled( thname ) ) return true;
    Symbol symbol = getSymbolByThName( thname );
    if ( symbol == null ) {
      String filename = Symbol.deprefix_u( thname );
      // TDLog.v( "load missing line " + thname + " filename " + filename );
      File file = TDFile.getFile( TDPath.getSymbolSaveLinePath( filename ) );
      if ( ! file.exists() ) return false;
      symbol = new SymbolLine( file.getPath(), file.getName(), locale, iso );
      addSymbol( symbol );
    // } else {
    //   // TDLog.v( "enabling missing line " + thname );
    }
    // if ( symbol == null ) return false; // ALWAYS false

    symbol.setEnabled( true ); // TopoDroidApp.mData.isSymbolEnabled( "a_" + symbol.getThname() ) );
    
    makeEnabledList();
    return true;
  }
  */
  
  // @Override
  // protected void makeEnabledList()
  // {
  //   // mLine.clear();
  //   super.makeEnabledList(); // CONFIG_ENABLE
  //   makeSpecialIndices();
  // }

  @Override
  protected void makeSpecialIndices()
  {    
    mLineUserIndex    = getSymbolIndexByThName( USER );
    mLineWallIndex    = getSymbolIndexByThName( WALL );
    mLineSlopeIndex   = getSymbolIndexByThName( SLOPE );
    mLineSectionIndex = getSymbolIndexByThName( SECTION );
  }

  void makeEnabledListFromPalette( SymbolsPalette palette, boolean clear )
  {
    makeEnabledListFromStrings( palette.mPaletteLine, clear ); 
  }

  // ArrayList< String > getSymbolNamesNoSection()
  // {
  //   ArrayList< String > ret = new ArrayList<>();
  //   for ( int k = 0; k < mSymbols.size(); ++ k ) {
  //     if ( k != mTypeSection ) ret.add( s.getName() );
  //   }
  //   return ret;
  // }

}    
