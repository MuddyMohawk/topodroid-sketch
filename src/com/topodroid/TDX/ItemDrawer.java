/* @file ItemDrawer.java
 *
 * @author marco corvi
 * @date oct 2014
 *
 * @brief TopoDroid label adder interface
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.util.TDUtil;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;
import com.topodroid.types.PointScale;

import android.app.Activity;

import android.graphics.pdf.PdfDocument.PageInfo;
import android.graphics.RectF;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

abstract class ItemDrawer extends Activity
{
  static final int POINT_MAX = 32678;
  static final int PDF_MARGIN = 40;

  protected Activity mActivity = null;

  int mCurrentPoint = -1;
  int mCurrentLine  = -1;
  int mCurrentArea  = -1;
  protected int mPointScale;
  protected int mLinePointStep = 1;

  protected int mSymbol = SymbolType.LINE; // kind of symbol being drawn

  // -----------------------------------------------------------
  static final int NR_RECENT = TDSetting.TOOLBAR_SLOTS_MAX; // max toolbar capacity
  static final int NR_LEGACY_RECENT = 6;
  static final int NR_TOOLBAR_ROWS = TDSetting.TOOLBAR_ROWS_MAX;
  static final int TOOLBAR_LOCK_UNLOCKED = SymbolType.UNDEF;
  static final String KEY_TOOLBAR_POINTS = "toolbar_points";
  static final String KEY_TOOLBAR_LINES  = "toolbar_lines";
  static final String KEY_TOOLBAR_AREAS  = "toolbar_areas";
  static final String KEY_TOOLBAR_ROW_PREFIX = "toolbar_row_";
  static final String KEY_TOOLBAR_ROW_POINTS = "_points";
  static final String KEY_TOOLBAR_ROW_LINES  = "_lines";
  static final String KEY_TOOLBAR_ROW_AREAS  = "_areas";
  static final String KEY_TOOLBAR_ROW_LOCK   = "_lock";
  static final String KEY_TOOLBAR_ROW_SLOT   = "_slot";
  static final String KEY_TOOLBAR_ACTIVE_ROW  = "toolbar_active_row";
  static final String KEY_TOOLBAR_ACTIVE_TYPE = "toolbar_active_type";
  static final String KEY_TOOLBAR_CURRENT_TYPE = "toolbar_current_type";
  static final String KEY_TOOLBAR_SEED = "toolbar_seed_version";
  // Bump only when existing saved toolbars should be intentionally reseeded.
  static final int TOOLBAR_SEED_VERSION = 2;

  private static final String[] DEFAULT_ROW0_LINES = {
    SymbolLibrary.WALL, SymbolLibrary.USER, SymbolLibrary.PIT,
    SymbolLibrary.CHIMNEY, SymbolLibrary.FLOWSTONE
  };

  private static final String[] DEFAULT_ROW1_POINTS = {
    SymbolLibrary.SAND, SymbolLibrary.CLAY, SymbolLibrary.BEDROCK,
    SymbolLibrary.SLOPE
  };

  static Symbol[][] mToolbarPoint = new Symbol[ NR_TOOLBAR_ROWS ][ NR_RECENT ];
  static Symbol[][] mToolbarLine  = new Symbol[ NR_TOOLBAR_ROWS ][ NR_RECENT ];
  static Symbol[][] mToolbarArea  = new Symbol[ NR_TOOLBAR_ROWS ][ NR_RECENT ];
  static int[] mToolbarLock = new int[ NR_TOOLBAR_ROWS ];
  static int[] mToolbarActiveSlot = new int[ NR_TOOLBAR_ROWS ];
  static int mToolbarActiveRow = -1;
  static int mToolbarActiveType = SymbolType.UNDEF;
  static int mToolbarCurrentType = SymbolType.POINT;
  static Symbol[] mRecentPoint = mToolbarPoint[0];
  static Symbol[] mRecentLine  = mToolbarLine[0];
  static Symbol[] mRecentArea  = mToolbarArea[0];
  static int[] mRecentPointAge = makeAgeArray();
  static int[] mRecentLineAge  = makeAgeArray();
  static int[] mRecentAreaAge  = makeAgeArray();
  static Symbol[] mRecentTools = mRecentLine;
  static float mRecentDimX;
  static float mRecentDimY;
  protected int mActiveToolbarRow = 0;
  protected int mActiveToolbarType = SymbolType.LINE;
  protected int mActiveToolbarSlot = 0;

  private static int[] makeAgeArray()
  {
    int[] ages = new int[ NR_RECENT ];
    for ( int k = 0; k < NR_RECENT; ++k ) ages[k] = NR_RECENT - k;
    return ages;
  }

  static boolean isManualToolbar()
  {
    return TDSetting.mToolbarUpdate == TDSetting.TOOLBAR_UPDATE_MANUAL;
  }

  static int getToolbarSlotCount()
  {
    if ( isManualToolbar() ) {
      if ( TDSetting.mToolbarSlots < TDSetting.TOOLBAR_SLOTS_MIN ) return TDSetting.TOOLBAR_SLOTS_MIN;
      if ( TDSetting.mToolbarSlots > TDSetting.TOOLBAR_SLOTS_MAX ) return TDSetting.TOOLBAR_SLOTS_MAX;
      return TDSetting.mToolbarSlots;
    }
    return NR_LEGACY_RECENT;
  }

  static int getToolbarRowCount()
  {
    if ( ! isManualToolbar() ) return 1;
    if ( TDSetting.mToolbarRows < TDSetting.TOOLBAR_ROWS_MIN ) return TDSetting.TOOLBAR_ROWS_MIN;
    if ( TDSetting.mToolbarRows > TDSetting.TOOLBAR_ROWS_MAX ) return TDSetting.TOOLBAR_ROWS_MAX;
    return TDSetting.mToolbarRows;
  }

  static int getToolbarRowForViewIndex( int viewIndex )
  {
    int rows = getToolbarRowCount();
    if ( viewIndex < 0 ) return rows - 1;
    if ( viewIndex >= rows ) return 0;
    return rows - viewIndex - 1;
  }

  static int getToolbarViewIndexForRow( int row )
  {
    int rows = getToolbarRowCount();
    row = normalizeToolbarRow( row );
    if ( row >= rows ) return 0;
    return rows - row - 1;
  }

  static int normalizeToolbarRow( int row )
  {
    if ( row < 0 ) return 0;
    if ( row >= NR_TOOLBAR_ROWS ) return NR_TOOLBAR_ROWS - 1;
    return row;
  }

  static boolean isToolbarType( int type )
  {
    return type == SymbolType.POINT || type == SymbolType.LINE || type == SymbolType.AREA;
  }

  static int nextToolbarType( int type )
  {
    if ( type == SymbolType.POINT ) return SymbolType.LINE;
    if ( type == SymbolType.LINE  ) return SymbolType.AREA;
    return SymbolType.POINT;
  }

  static int getToolbarDisplayType( int row )
  {
    row = normalizeToolbarRow( row );
    return isToolbarType( mToolbarLock[row] ) ? mToolbarLock[row] : mToolbarCurrentType;
  }

  static boolean isToolbarRowLocked( int row )
  {
    row = normalizeToolbarRow( row );
    return isToolbarType( mToolbarLock[row] );
  }

  static int getToolbarRowLock( int row )
  {
    row = normalizeToolbarRow( row );
    return mToolbarLock[row];
  }

  static void setToolbarRowLock( int row, int type )
  {
    row = normalizeToolbarRow( row );
    mToolbarLock[row] = isToolbarType( type ) ? type : TOOLBAR_LOCK_UNLOCKED;
  }

  static void setToolbarCurrentType( int type )
  {
    if ( isToolbarType( type ) ) mToolbarCurrentType = type;
  }

  static int getToolbarActiveRow()
  {
    return mToolbarActiveRow;
  }

  static int getToolbarActiveType()
  {
    return mToolbarActiveType;
  }

  void setPointScale( int scale )
  {
    if ( scale >= PointScale.SCALE_XS && scale <= PointScale.SCALE_XL ) mPointScale = scale;
  }

  int getPointScale() { return mPointScale; }

  // --------------------------------------------------------------
  // MOST RECENT SYMBOLS
  // recent symbols are stored with their filenames
  //
  // update of the "recent" arrays is done either with symbol index, or with symbol itself
  // load and save is done using a string of symbol filenames (separated by space)

  /** update the array of recent points
   * @param point  index of the point in the point library
   * @note section point is excluded from the "recent points" toolbar
   */
  static void updateRecentPoint( int point )
  {
    if ( BrushManager.isPointSection( point ) ) return;
    if ( BrushManager.isPointPicture( point ) ) return;
    updateRecent( BrushManager.getPointByIndex( point ), mRecentPoint, mRecentPointAge );
  }

  /** update the array of recent lines
   * @param line  index of the line in the line library
   */
  static void updateRecentLine( int line )
  {
    updateRecent( BrushManager.getLineByIndex( line ), mRecentLine, mRecentLineAge );
  }

  /** update the array of recent areas
   * @param area  index of the area in the area library
   */
  static void updateRecentArea( int area )
  {
    // TDLog.v("update recent area: idx " + area + " " + BrushManager.getAreaByIndex( area ).getThName() );
    updateRecent( BrushManager.getAreaByIndex( area ), mRecentArea, mRecentAreaAge );
  }

  /** update the array of recent points
   * @param point  point symbol
   * @note section point is excluded from the "recent points" toolbar
   */
  static void updateRecentPoint( Symbol point ) 
  {
    if ( point.isSection() || point.isPicture() ) return;
    updateRecent( point, mRecentPoint, mRecentPointAge );
  }

  /** update the array of recent lines
   * @param line  line symbol
   */
  static void updateRecentLine( Symbol line ) { updateRecent( line, mRecentLine, mRecentLineAge ); }

  /** update the array of recent areas
   * @param area  area symbol
   */
  static void updateRecentArea( Symbol area ) { updateRecent( area, mRecentArea, mRecentAreaAge ); }

  /** set to null disabled recent symbols
   */
  static void resetRecentSymbols()
  {
    // TDLog.v("reset recent symbols ..."); // ENABLED_LIST
    for ( int row=0; row<NR_TOOLBAR_ROWS; ++row ) {
      for ( int k=0; k<NR_RECENT; ++k ) {
        if ( mToolbarPoint[row][k] != null && ! BrushManager.isPointEnabled( mToolbarPoint[row][k].getThName() ) ) mToolbarPoint[row][k] = null;
        if ( mToolbarLine[row][k]  != null && ! BrushManager.isLineEnabled(  mToolbarLine[row][k].getThName()  ) ) mToolbarLine[row][k]  = null;
        if ( mToolbarArea[row][k]  != null ) {
          boolean enabled = BrushManager.isAreaEnabled( mToolbarArea[row][k].getThName() );
          // TDLog.v("area " + mToolbarArea[row][k].getThName() + " enabled " + enabled ); // ENABLED_LIST
          if ( ! enabled ) mToolbarArea[row][k]  = null;
        }
      }
    }
  }

  /** refresh line recent tools after the line library has been reloaded
   */
  static void refreshRecentLineSymbols()
  {
    if ( isManualToolbar() ) {
      for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) setRecentList( refreshRecentSymbols( SymbolType.LINE, mToolbarLine[row] ), mToolbarLine[row], row == 0 ? mRecentLineAge : null );
    } else {
      setRecentLineList( refreshRecentSymbols( SymbolType.LINE, mRecentLine ) );
    }
  }

  /** refresh point recent tools after the point library has been reloaded
   */
  static void refreshRecentPointSymbols()
  {
    if ( isManualToolbar() ) {
      for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) setRecentList( refreshRecentSymbols( SymbolType.POINT, mToolbarPoint[row] ), mToolbarPoint[row], row == 0 ? mRecentPointAge : null );
    } else {
      setRecentList( refreshRecentSymbols( SymbolType.POINT, mRecentPoint ), mRecentPoint, mRecentPointAge );
    }
  }

  /** refresh area recent tools after the area library has been reloaded
   */
  static void refreshRecentAreaSymbols()
  {
    if ( isManualToolbar() ) {
      for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) setRecentList( refreshRecentSymbols( SymbolType.AREA, mToolbarArea[row] ), mToolbarArea[row], row == 0 ? mRecentAreaAge : null );
    } else {
      setRecentList( refreshRecentSymbols( SymbolType.AREA, mRecentArea ), mRecentArea, mRecentAreaAge );
    }
  }

  /** prepend line symbols to the recent-line toolbar, preserving unique items
   * @param lines   symbols to prepend
   */
  static void prependRecentLines( Symbol[] lines )
  {
    int limit = NR_LEGACY_RECENT;
    ArrayList< Symbol > merged = new ArrayList<>();
    if ( lines != null ) {
      for ( Symbol line : lines ) {
        if ( line == null ) continue;
        if ( hasRecentSymbol( merged, line.getFullThName() ) ) continue;
        merged.add( line );
        if ( merged.size() >= limit ) break;
      }
    }
    for ( int k = 0; k < limit && merged.size() < limit; ++k ) {
      Symbol line = mRecentLine[k];
      if ( line == null ) continue;
      if ( hasRecentSymbol( merged, line.getFullThName() ) ) continue;
      merged.add( line );
    }
    setRecentLineList( merged );
  }

  /** serialize recent line symbols for the configuration table
   * @return serialized recent line names
   */
  static String serializeRecentLines()
  {
    return serializeSymbols( mRecentLine, NR_LEGACY_RECENT, true );
  }

  private static void setRecentLineList( ArrayList< Symbol > lines )
  {
    setRecentList( lines, mRecentLine, mRecentLineAge );
  }

  private static void setRecentList( ArrayList< Symbol > list, Symbol[] symbols, int[] ages )
  {
    int nr = ( list == null ) ? 0 : list.size();
    for ( int k = 0; k < NR_RECENT; ++k ) {
      symbols[k] = ( k < nr ) ? list.get( k ) : null;
      if ( ages != null ) ages[k] = NR_RECENT - k;
    }
  }

  private static ArrayList< Symbol > refreshRecentSymbols( int type, Symbol[] oldSymbols )
  {
    ArrayList< Symbol > refreshed = new ArrayList<>();
    for ( int k = 0; k < NR_RECENT; ++k ) {
      Symbol oldSymbol = oldSymbols[k];
      if ( oldSymbol == null ) continue;
      Symbol current = getSymbolByThName( type, oldSymbol.getFullThName() );
      if ( current == null || ! current.isEnabled() ) continue;
      if ( hasRecentSymbol( refreshed, current.getFullThName() ) ) continue;
      refreshed.add( current );
    }
    return refreshed;
  }

  private static Symbol getSymbolByThName( int type, String thName )
  {
    switch ( type ) {
      case SymbolType.POINT: return BrushManager.getPointByThName( thName );
      case SymbolType.LINE:  return BrushManager.getLineByThName( thName );
      case SymbolType.AREA:  return BrushManager.getAreaByThName( thName );
    }
    return null;
  }

  private static boolean hasRecentSymbol( ArrayList< Symbol > symbols, String fullThName )
  {
    if ( fullThName == null ) return false;
    for ( Symbol symbol : symbols ) {
      if ( symbol != null && fullThName.equals( symbol.getFullThName() ) ) return true;
    }
    return false;
  }

  static void loadManualToolbarSymbols( DataHelper data )
  {
    mToolbarActiveRow = -1;
    mToolbarActiveType = SymbolType.UNDEF;
    mToolbarCurrentType = SymbolType.POINT;
    if ( needsToolbarSeed( data ) ) {
      if ( canSeedToolbar() ) seedManualToolbarSymbols( data );
      return;
    }

    mToolbarActiveRow = rowFromString( data == null ? null : data.getValue( KEY_TOOLBAR_ACTIVE_ROW ) );
    mToolbarActiveType = typeFromString( data == null ? null : data.getValue( KEY_TOOLBAR_ACTIVE_TYPE ), SymbolType.UNDEF );
    mToolbarCurrentType = typeFromString( data == null ? null : data.getValue( KEY_TOOLBAR_CURRENT_TYPE ), SymbolType.POINT );
    if ( mToolbarActiveRow < 0 || ! isToolbarType( mToolbarActiveType ) ) {
      mToolbarActiveRow = -1;
      mToolbarActiveType = SymbolType.UNDEF;
    }

    for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) {
      mToolbarLock[row] = lockFromString( data == null ? null : data.getValue( rowKey( row, KEY_TOOLBAR_ROW_LOCK ) ), row );
      mToolbarActiveSlot[row] = slotFromString( data == null ? null : data.getValue( rowKey( row, KEY_TOOLBAR_ROW_SLOT ) ) );
    }

    loadManualToolbarList( SymbolType.POINT, mToolbarPoint[0], mRecentPointAge, getToolbarRowNames( data, 0, SymbolType.POINT ) );
    loadManualToolbarList( SymbolType.LINE,  mToolbarLine[0],  mRecentLineAge,  getToolbarRowNames( data, 0, SymbolType.LINE  ) );
    loadManualToolbarList( SymbolType.AREA,  mToolbarArea[0],  mRecentAreaAge,  getToolbarRowNames( data, 0, SymbolType.AREA  ) );

    for ( int row = 1; row < NR_TOOLBAR_ROWS; ++row ) {
      loadOrCopyManualToolbarList( SymbolType.POINT, mToolbarPoint[row], mToolbarPoint[0], getToolbarRowNames( data, row, SymbolType.POINT ) );
      loadOrCopyManualToolbarList( SymbolType.LINE,  mToolbarLine[row],  mToolbarLine[0],  getToolbarRowNames( data, row, SymbolType.LINE  ) );
      loadOrCopyManualToolbarList( SymbolType.AREA,  mToolbarArea[row],  mToolbarArea[0],  getToolbarRowNames( data, row, SymbolType.AREA  ) );
    }
  }

  private static boolean needsToolbarSeed( DataHelper data )
  {
    if ( data == null ) return true;
    String value = data.getValue( KEY_TOOLBAR_SEED );
    int version = 0;
    if ( value != null ) {
      try { version = Integer.parseInt( value.trim() ); } catch ( NumberFormatException e ) { version = 0; }
    }
    return version < TOOLBAR_SEED_VERSION;
  }

  private static boolean canSeedToolbar()
  {
    return BrushManager.getPointLibSize() > 0 && BrushManager.getLineLibSize() > 0;
  }

  private static void seedManualToolbarSymbols( DataHelper data )
  {
    mToolbarCurrentType = SymbolType.POINT;
    mToolbarActiveRow = 1;
    mToolbarActiveType = SymbolType.POINT;
    for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) {
      mToolbarLock[row] = ( row == 0 ) ? SymbolType.LINE : TOOLBAR_LOCK_UNLOCKED;
      mToolbarActiveSlot[row] = 0;
    }

    seedToolbarList( SymbolType.POINT, mToolbarPoint[0], mRecentPointAge, DEFAULT_ROW1_POINTS, data );
    seedToolbarList( SymbolType.LINE,  mToolbarLine[0],  mRecentLineAge,  DEFAULT_ROW0_LINES,  data );
    loadManualToolbarList( SymbolType.AREA, mToolbarArea[0], mRecentAreaAge, null );

    seedToolbarList( SymbolType.POINT, mToolbarPoint[1], null, DEFAULT_ROW1_POINTS, data );
    copyToolbarList( SymbolType.LINE,  mToolbarLine[0], mToolbarLine[1] );
    copyToolbarList( SymbolType.AREA,  mToolbarArea[0], mToolbarArea[1] );

    for ( int row = 2; row < NR_TOOLBAR_ROWS; ++row ) {
      copyToolbarList( SymbolType.POINT, mToolbarPoint[0], mToolbarPoint[row] );
      copyToolbarList( SymbolType.LINE,  mToolbarLine[0],  mToolbarLine[row]  );
      copyToolbarList( SymbolType.AREA,  mToolbarArea[0],  mToolbarArea[row]  );
    }

    if ( data != null ) {
      saveManualToolbarSymbols( data );
      data.setValue( KEY_TOOLBAR_SEED, Integer.toString( TOOLBAR_SEED_VERSION ) );
    }
  }

  static void saveManualToolbarSymbols( DataHelper data )
  {
    if ( data == null ) return;
    data.setValue( KEY_TOOLBAR_ACTIVE_ROW, Integer.toString( mToolbarActiveRow ) );
    data.setValue( KEY_TOOLBAR_ACTIVE_TYPE, lockToString( mToolbarActiveType ) );
    data.setValue( KEY_TOOLBAR_CURRENT_TYPE, lockToString( mToolbarCurrentType ) );
    for ( int row = 0; row < NR_TOOLBAR_ROWS; ++row ) {
      data.setValue( rowKey( row, KEY_TOOLBAR_ROW_POINTS ), serializeSymbols( mToolbarPoint[row], NR_RECENT, false ) );
      data.setValue( rowKey( row, KEY_TOOLBAR_ROW_LINES  ), serializeSymbols( mToolbarLine[row],  NR_RECENT, false ) );
      data.setValue( rowKey( row, KEY_TOOLBAR_ROW_AREAS  ), serializeSymbols( mToolbarArea[row],  NR_RECENT, false ) );
      data.setValue( rowKey( row, KEY_TOOLBAR_ROW_LOCK   ), lockToString( mToolbarLock[row] ) );
      data.setValue( rowKey( row, KEY_TOOLBAR_ROW_SLOT   ), Integer.toString( mToolbarActiveSlot[row] ) );
    }
    data.setValue( KEY_TOOLBAR_POINTS, serializeSymbols( mRecentPoint, NR_RECENT, false ) );
    data.setValue( KEY_TOOLBAR_LINES,  serializeSymbols( mRecentLine,  NR_RECENT, false ) );
    data.setValue( KEY_TOOLBAR_AREAS,  serializeSymbols( mRecentArea,  NR_RECENT, false ) );
  }

  private static void loadOrCopyManualToolbarList( int type, Symbol[] symbols, Symbol[] source, String names )
  {
    if ( hasToolbarNames( names ) ) {
      loadManualToolbarList( type, symbols, null, names );
    } else {
      copyToolbarList( type, source, symbols );
    }
  }

  private static void loadManualToolbarList( int type, Symbol[] symbols, int[] ages, String names )
  {
    clearToolbarList( symbols, ages );

    int index = 0;
    if ( hasToolbarNames( names ) ) {
      String[] vals = names.trim().split( "\\s+" );
      for ( String name : vals ) {
        if ( name.length() == 0 ) continue;
        Symbol symbol = getSymbolByThName( type, name );
        if ( symbol == null || ! symbol.isEnabled() ) continue;
        if ( hasSymbol( symbols, symbol, index ) ) continue;
        symbols[index++] = symbol;
        if ( index >= NR_RECENT ) break;
      }
    }
    fillToolbarList( type, symbols, index );
  }

  private static void seedToolbarList( int type, Symbol[] symbols, int[] ages, String[] names, DataHelper data )
  {
    clearToolbarList( symbols, ages );

    int index = 0;
    for ( String name : names ) {
      Symbol symbol = getSymbolByThName( type, name );
      if ( symbol == null ) continue;
      enableDefaultToolbarSymbol( type, symbol, data );
      if ( hasSymbol( symbols, symbol, index ) ) continue;
      symbols[index++] = symbol;
      if ( index >= NR_RECENT ) break;
    }
    fillToolbarList( type, symbols, index );
  }

  private static void copyToolbarList( int type, Symbol[] source, Symbol[] symbols )
  {
    clearToolbarList( symbols, null );
    int index = 0;
    if ( source != null ) {
      for ( int k = 0; k < NR_RECENT && index < NR_RECENT; ++k ) {
        Symbol symbol = source[k];
        if ( symbol == null || ! symbol.isEnabled() ) continue;
        if ( type == SymbolType.POINT && ( symbol.isSection() || symbol.isPicture() ) ) continue;
        if ( hasSymbol( symbols, symbol, index ) ) continue;
        symbols[index++] = symbol;
      }
    }
    fillToolbarList( type, symbols, index );
  }

  private static void clearToolbarList( Symbol[] symbols, int[] ages )
  {
    for ( int k = 0; k < NR_RECENT; ++k ) {
      symbols[k] = null;
      if ( ages != null ) ages[k] = NR_RECENT - k;
    }
  }

  private static String getToolbarRowNames( DataHelper data, int row, int type )
  {
    if ( data == null ) return null;
    String names = data.getValue( rowKey( row, toolbarRowSuffix( type ) ) );
    if ( hasToolbarNames( names ) ) return names;
    if ( row == 0 ) return data.getValue( legacyToolbarKey( type ) );
    return null;
  }

  private static boolean hasToolbarNames( String names )
  {
    return names != null && names.trim().length() > 0;
  }

  private static String rowKey( int row, String suffix )
  {
    return KEY_TOOLBAR_ROW_PREFIX + normalizeToolbarRow( row ) + suffix;
  }

  private static String toolbarRowSuffix( int type )
  {
    switch ( type ) {
      case SymbolType.POINT: return KEY_TOOLBAR_ROW_POINTS;
      case SymbolType.LINE:  return KEY_TOOLBAR_ROW_LINES;
      case SymbolType.AREA:  return KEY_TOOLBAR_ROW_AREAS;
    }
    return KEY_TOOLBAR_ROW_LINES;
  }

  private static String legacyToolbarKey( int type )
  {
    switch ( type ) {
      case SymbolType.POINT: return KEY_TOOLBAR_POINTS;
      case SymbolType.LINE:  return KEY_TOOLBAR_LINES;
      case SymbolType.AREA:  return KEY_TOOLBAR_AREAS;
    }
    return KEY_TOOLBAR_LINES;
  }

  private static int slotFromString( String value )
  {
    int slot = 0;
    if ( value != null ) {
      try { slot = Integer.parseInt( value ); } catch ( NumberFormatException e ) { slot = 0; }
    }
    if ( slot < 0 ) return 0;
    if ( slot >= NR_RECENT ) return NR_RECENT - 1;
    return slot;
  }

  static int rowFromString( String value )
  {
    int row = -1;
    if ( value != null ) {
      try { row = Integer.parseInt( value.trim() ); } catch ( NumberFormatException e ) { row = -1; }
    }
    if ( row < 0 || row >= NR_TOOLBAR_ROWS ) return -1;
    return row;
  }

  static int typeFromString( String value, int defaultType )
  {
    if ( value == null ) return defaultType;
    String type = value.trim();
    if ( "point".equals( type ) ) return SymbolType.POINT;
    if ( "line".equals( type ) ) return SymbolType.LINE;
    if ( "area".equals( type ) ) return SymbolType.AREA;
    return defaultType;
  }

  private static int lockFromString( String value, int row )
  {
    if ( value == null ) return normalizeToolbarRow( row ) == 0 ? SymbolType.LINE : TOOLBAR_LOCK_UNLOCKED;
    return typeFromString( value, TOOLBAR_LOCK_UNLOCKED );
  }

  private static String lockToString( int lock )
  {
    switch ( lock ) {
      case SymbolType.POINT: return "point";
      case SymbolType.LINE:  return "line";
      case SymbolType.AREA:  return "area";
    }
    return "unlocked";
  }

  private static void enableDefaultToolbarSymbol( int type, Symbol symbol, DataHelper data )
  {
    if ( symbol == null ) return;
    symbol.setEnabled( true );
    symbol.setConfigEnabled( true );
    if ( data != null ) data.setSymbolEnabled( toolbarConfigPrefix( type ) + symbol.getThName(), true );
  }

  private static String toolbarConfigPrefix( int type )
  {
    switch ( type ) {
      case SymbolType.POINT: return "p_";
      case SymbolType.LINE:  return "l_";
      case SymbolType.AREA:  return "a_";
    }
    return "";
  }

  private static void fillToolbarList( int type, Symbol[] symbols, int index )
  {
    int size = getLibrarySize( type );
    for ( int k = 0; k < size && index < NR_RECENT; ++k ) {
      Symbol symbol = getSymbolByIndex( type, k );
      if ( symbol == null || ! symbol.isEnabled() ) continue;
      if ( type == SymbolType.POINT && ( symbol.isSection() || symbol.isPicture() ) ) continue;
      if ( hasSymbol( symbols, symbol, index ) ) continue;
      symbols[index++] = symbol;
    }
  }

  private static int getLibrarySize( int type )
  {
    switch ( type ) {
      case SymbolType.POINT: return BrushManager.getPointLibSize();
      case SymbolType.LINE:  return BrushManager.getLineLibSize();
      case SymbolType.AREA:  return BrushManager.getAreaLibSize();
    }
    return 0;
  }

  private static Symbol getSymbolByIndex( int type, int index )
  {
    switch ( type ) {
      case SymbolType.POINT: return BrushManager.getPointByIndex( index );
      case SymbolType.LINE:  return BrushManager.getLineByIndex( index );
      case SymbolType.AREA:  return BrushManager.getAreaByIndex( index );
    }
    return null;
  }

  private static boolean hasSymbol( Symbol[] symbols, Symbol symbol, int limit )
  {
    if ( symbol == null ) return false;
    String fullThName = symbol.getFullThName();
    for ( int k = 0; k < limit && k < symbols.length; ++k ) {
      Symbol current = symbols[k];
      if ( current != null && fullThName != null && fullThName.equals( current.getFullThName() ) ) return true;
    }
    return false;
  }

  private static String serializeSymbols( Symbol[] symbols, int limit, boolean reverse )
  {
    StringBuilder sb = new StringBuilder();
    if ( reverse ) {
      for ( int k = limit - 1; k >= 0; --k ) appendSymbolName( sb, symbols[k] );
    } else {
      for ( int k = 0; k < limit; ++k ) appendSymbolName( sb, symbols[k] );
    }
    return sb.toString();
  }

  private static void appendSymbolName( StringBuilder sb, Symbol symbol )
  {
    if ( symbol == null ) return;
    if ( sb.length() > 0 ) sb.append( " " );
    sb.append( symbol.getThName() );
  }

  // DEBUG
  // static void printSymbols( Symbol[] symbols, int[] ages )
  // {
  //   TDLog.v("Symbols " + symbols[0].getFullThName() + " " + symbols[1].getFullThName() + " " + symbols[2].getFullThName() + " "
  //                      + symbols[3].getFullThName() + " " + symbols[4].getFullThName() + " " + symbols[5].getFullThName() );
  //   TDLog.v("Ages " + ages[0] + " " + ages[1] + " " + ages[2] + " " + ages[3] + " " + ages[4] + " " + ages[5] );
  // }

  /** update a set of recent symbols
   * @param symbol    symbol to update
   * @param symbols   set of recent symbols
   * @param ages      set of recent symbols ages
   * @note used by RecentSymbolsTask
   */
  static void updateRecent( Symbol symbol, Symbol[] symbols, int[] ages )
  {
    // printSymbols( symbols, ages );
    if ( symbol == null ) return;
    if ( isManualToolbar() ) return;
    int limit = NR_LEGACY_RECENT;
    if ( TDSetting.mToolbarUpdate == 1 ) { // 1 put new symbol in front
      int k0 = 0;
      for ( ; k0 < limit; ++k0 ) {
        if ( ( symbols[k0] == null ) || ( symbols[k0].getFullThName().equals( symbol.getFullThName() ) ) ) break;
      }
      if ( k0 == limit ) --k0;
      for ( int k = k0; k>0; --k ) {
        symbols[k] = symbols[k-1];
        ages[k] = ages[k-1];
      }
      symbols[0] = symbol;
      updateAge( 0, ages, limit );
    } else if ( TDSetting.mToolbarUpdate == 2 ) { // 2 put new symbol in front - drop the oldest
      int k0=0;
      for ( ; k0 < limit; ++k0 ) {
        if ( ( symbols[k0] == null ) || ( symbols[k0].getFullThName().equals( symbol.getFullThName() ) ) ) {
          for ( int k=k0; k>0; --k) {
            symbols[k] = symbols[k-1];
            ages[k] = ages[k-1];
          }
          symbols[0] = symbol;
          updateAge( 0, ages, limit );
          // TDLog.v("Found at " + k0 + " new ages " + ages[0] + " " + ages[1] + " " + ages[2] + " " + ages[3] + " " + ages[4] + " " + ages[5] );
          return;
        }
      }
      k0 = 0; // index min age
      for ( int k=1; k<limit; ++k ) {
        if ( ages[k] <= ages[k0] ) k0 = k;
      }
      for ( int k=k0; k>0; --k) {
        symbols[k] = symbols[k-1];
        ages[k] = ages[k-1];
      }
      symbols[0] = symbol;
      updateAge( 0, ages, limit );
      // TDLog.v("Oldest at " + k0 + " new ages " + ages[0] + " " + ages[1] + " " + ages[2] + " " + ages[3] + " " + ages[4] + " " + ages[5] );
    } else { // 0: replace oldest
      int kmin = 0;
      for ( int k=0; k<limit; ++k ) {
        if ( symbol == symbols[k] ) {
          updateAge( k, ages, limit );
          return;
        } else if ( ages[k] < ages[kmin] ) {
          kmin = k;
        }
      }
      symbols[kmin] = symbol;
      updateAge( kmin, ages, limit );
    }
  }

  /** update a recent symbol age
   * @param kk   index of the recent symbol
   * @param ages      set of recent symbols ages
   */
  static void updateAge( int kk, int[] ages )
  {
    updateAge( kk, ages, isManualToolbar() ? getToolbarSlotCount() : NR_LEGACY_RECENT );
  }

  private static void updateAge( int kk, int[] ages, int limit )
  {
    // TDLog.v("AGE kk " + kk );
    int amax = ages[kk];
    for ( int k=0; k<limit; ++k ) {
      if ( k != kk && ages[kk] < ages[k] ) { 
        if ( amax < ages[k] ) amax = ages[k];
        -- ages[k];
      }
    }
    ages[kk] = amax;
    // TDLog.v("AGE " + ages[0] + " " + ages[1] + " " + ages[2] + " " + ages[3] + " " + ages[4] + " " + ages[5] );
  }

  /** load the recent symbols sets from the database
   * @param db   database helper
   * @note recent symbols are stored with their th_names
   */
  protected void loadRecentSymbols( DataHelper db )
  {
    ( new RecentSymbolsTask( this, this, db, RecentSymbolsTask.LOAD ) ).execute();

  }

  /** save the recent symbols sets to the database
   * @param db   database helper
   */
  protected void saveRecentSymbols( DataHelper db )
  {
    ( new RecentSymbolsTask( this, this, db, RecentSymbolsTask.SAVE ) ).execute();
  }

  // ----------------------------------------------------------------------
  // TOOL SELECTION

  public void itemPickerSelected( int type, int index )
  {
    itemPickerSelected( type, index, 0 );
  }

  public void itemPickerSelected( int type, int index, int row )
  {
    if ( isManualToolbar() ) {
      if ( ! canSelectToolbarSymbol( type, index ) ) return;
      row = normalizeToolbarRow( row );
      int slot = replaceManualToolbarSymbol( row, type, index );
      if ( slot < 0 ) return;
      if ( ! isToolbarRowLocked( row ) ) setToolbarCurrentType( type );
      selectSymbolByType( type, index, false );
      setActiveToolbarSlot( row, type, slot );
      setBtnRecent( type );
    } else {
      selectSymbolByType( type, index, true );
    }
  }

  public void itemPickerLockChanged( int row, boolean locked, int type )
  {
    if ( ! isManualToolbar() ) return;
    setToolbarRowLock( row, locked ? type : TOOLBAR_LOCK_UNLOCKED );
    onToolbarRowStateChanged( normalizeToolbarRow( row ) );
  }

  public void itemPickerTypeChanged( int row, int type )
  {
    if ( ! isManualToolbar() ) return;
    row = normalizeToolbarRow( row );
    if ( isToolbarRowLocked( row ) ) {
      setToolbarRowLock( row, type );
      onToolbarRowStateChanged( row );
    }
  }

  private boolean canSelectToolbarSymbol( int type, int index )
  {
    if ( index < 0 ) return false;
    switch ( type ) {
      case SymbolType.POINT:
        return ! forbidPointSection( index ) && ! forbidPointPicture( index );
      case SymbolType.LINE:
        return ! forbidLineSection( index );
      case SymbolType.AREA:
        return true;
    }
    return false;
  }

  private void selectSymbolByType( int type, int index, boolean update_recent )
  {
    switch ( type ) {
      case SymbolType.POINT:
        pointSelected( index, update_recent );
        break;
      case SymbolType.LINE:
        lineSelected( index, update_recent );
        break;
      case SymbolType.AREA:
        areaSelected( index, update_recent );
        break;
    }
  }

  protected void setActiveToolbarSlot( int type, int slot )
  {
    setActiveToolbarSlot( 0, type, slot );
  }

  protected void setActiveToolbarSlot( int row, int type, int slot )
  {
    int count = getToolbarSlotCount();
    if ( slot < 0 || slot >= count ) return;
    row = normalizeToolbarRow( row );
    if ( ! isToolbarType( type ) ) return;
    mActiveToolbarRow = row;
    mActiveToolbarType = type;
    mActiveToolbarSlot = slot;
    mToolbarActiveRow = row;
    mToolbarActiveType = type;
    mToolbarActiveSlot[row] = slot;
  }

  protected int replaceManualToolbarSymbol( int type, int index )
  {
    return replaceManualToolbarSymbol( 0, type, index );
  }

  protected int replaceManualToolbarSymbol( int row, int type, int index )
  {
    Symbol symbol = getSymbolByIndex( type, index );
    Symbol[] symbols = getToolbarSymbols( row, type );
    if ( symbol == null || symbols == null ) return -1;

    int count = getToolbarSlotCount();
    row = normalizeToolbarRow( row );
    int active = mToolbarActiveSlot[row];
    int slot = ( active >= 0 && active < count ) ? active : 0;
    int duplicate = findToolbarSymbol( symbols, symbol, count );
    if ( duplicate >= 0 && duplicate != slot ) {
      symbols[duplicate] = symbols[slot];
    }
    symbols[slot] = symbol;
    return slot;
  }

  static Symbol[] getToolbarSymbols( int row, int type )
  {
    row = normalizeToolbarRow( row );
    switch ( type ) {
      case SymbolType.POINT: return mToolbarPoint[row];
      case SymbolType.LINE:  return mToolbarLine[row];
      case SymbolType.AREA:  return mToolbarArea[row];
    }
    return null;
  }

  private static Symbol[] getToolbarSymbols( int type )
  {
    return getToolbarSymbols( 0, type );
  }

  protected void onToolbarRowStateChanged( int row )
  {
    // Overridden by DrawingWindow.
  }

  private static int findToolbarSymbol( Symbol[] symbols, Symbol symbol, int limit )
  {
    if ( symbol == null ) return -1;
    String fullThName = symbol.getFullThName();
    for ( int k = 0; k < limit && k < symbols.length; ++k ) {
      Symbol current = symbols[k];
      if ( current != null && fullThName != null && fullThName.equals( current.getFullThName() ) ) return k;
    }
    return -1;
  }

  /** react to the selection of an area symbol
   * @param k      symbol index
   * @param update_recent whether to update the recent symbols set
   */
  public void areaSelected( int k, boolean update_recent ) 
  {
    // TDLog.v("Item drawer point selected: " + k + " update " + update_recent );
    mSymbol = SymbolType.AREA;
    if ( k >= 0 && k < BrushManager.getAreaLibSize() ) {
      mCurrentArea = k;
      if ( TDSetting.mWithLevels > 0 ) {
        if ( ! DrawingLevel.isVisible( BrushManager.getAreaLevel( k ) ) ) {
          mCurrentArea = 0; // BrushManager.mAreaLib.mAreaUserIndex;
        }
      }
    }
    setTheTitle();
    if ( update_recent ) {
      updateRecentArea( mCurrentArea );
      setBtnRecent( SymbolType.AREA );
    }
    mLinePointStep = TDSetting.mLineType;
  }

  /** react to the selection of a line symbol
   * @param k      symbol index
   * @param update_recent whether to update the recent symbols set
   */
  public void lineSelected( int k, boolean update_recent ) 
  {
    // TDLog.v("Item drawer line selected " + k + " update recent " + update_recent );
    mSymbol = SymbolType.LINE;
    if ( k >= 0 && k < BrushManager.getLineLibSize() ) {
      mCurrentLine = k;
      if ( TDSetting.mWithLevels > 0 ) {
        if ( ! DrawingLevel.isVisible( BrushManager.getLineLevel( k ) ) ) {
          // TDLog.v("Item drawer line selected " + k + " is not visible");
          mCurrentLine = 0; // BrushManager.mLineLib.mLineUserIndex;
        }
      }
    }
    setTheTitle();
    if ( update_recent ) {
      // TDLog.v("Item drawer update recent: current line " + mCurrentLine );
      updateRecentLine( mCurrentLine );
      setBtnRecent( SymbolType.LINE );
    }
    mLinePointStep = BrushManager.getLineStyleX( mCurrentLine );
    if ( mLinePointStep != POINT_MAX ) mLinePointStep *= TDSetting.mLineType;
  }

  /** react to the selection of a point symbol
   * @param p      symbol index
   * @param update_recent whether to update the recent symbols set
   */
  public void pointSelected( int p, boolean update_recent )
  {
    mSymbol = SymbolType.POINT;
    // TDLog.v("Item drawer point selected: " + p + " update " + update_recent );
    if ( p >= 0 && p < BrushManager.getPointLibSize() ) {
      mCurrentPoint = p;
      if ( TDSetting.mWithLevels > 0 ) {
        if ( ! DrawingLevel.isVisible( BrushManager.getPointLevel( p ) ) ) {
          mCurrentPoint = 0; // BrushManager.mPointLib.mPointUserIndex;
        }
      }
    }
    setTheTitle();
    if ( update_recent ) {
      updateRecentPoint( mCurrentPoint );
      setBtnRecent( SymbolType.POINT );
    }
  }

  /** set the recent symbols button - empty, to be overridden
   * @param symbol   symbol index
   */
  public void setBtnRecent( int symbol ) { }

  /** set the window tithe - empty, to be overridden
   */
  public void setTheTitle() { }

  // public boolean setCurrentPoint( int k, boolean update_recent ) { }
  // public boolean setCurrentLine( int k, boolean update_recent ) { }
  // public boolean setCurrentArea( int k, boolean update_recent ) { }

  /** react to a notified that recent symbols are loaded
   */
  public void onRecentSymbolsLoaded() { } 

  /** @return whether the point of the given index is "pictutre" and is forbidden
   * @param i   point index
   * @note overridden by DrawingWindow
   */
  public boolean forbidPointPicture( int i ) { return false; }

  /** @return whether the point of the given index is "section" and is forbidden
   * @param i   point index
   * @note overridden by DrawingWindow
   */
  public boolean forbidPointSection( int i ) { return false; }

  /** @return whether the line of the given index is "section" and is forbidden
   * @param j   line index
   * @note overridden by DrawingWindow
   */
  public boolean forbidLineSection( int j ) { return false; }


  protected PageInfo getPdfPage( DrawingCommandManager manager )
  {
    float scale = TDSetting.mToPdf;
    RectF bnds = manager.getBitmapBounds( scale );
    bnds = new  RectF((bnds.left   - PDF_MARGIN * scale),
                      (bnds.top    - PDF_MARGIN * scale),
                      (bnds.right  + PDF_MARGIN * scale),
                      (bnds.bottom + PDF_MARGIN * scale)); // HBX
    int zw = (int)(bnds.right - bnds.left); // margin 40 + 80 6.1.76 HBX
    int zh = (int)(bnds.bottom - bnds.top); // HBX
    // TDLog.v( "rect " + bnds.right + " " + bnds.left + " == " + bnds.bottom + " " + bnds.top + " W " + zw + " H " + zh );
    PageInfo.Builder builder = new PageInfo.Builder( zw, zh, 1 ); // API_19
    return builder.create(); // API_19
  }

  private long mScreenshotTime = 0;

  protected void takeScreenshot( DrawingSurface drawing_surface )
  {
    long millis = TDUtil.time();
    if ( millis < mScreenshotTime ) return;
    mScreenshotTime = millis + 1500;
    try {
      // create bitmap screen capture
      // View v1 = getWindow().getDecorView().getRootView();
      // v1.setDrawingCacheEnabled(true);
      // Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
      // v1.setDrawingCacheEnabled(false);

      Bitmap bitmap = Bitmap.createBitmap( (int)(TopoDroidApp.mDisplayWidth), (int)(TopoDroidApp.mDisplayHeight), Bitmap.Config.ARGB_4444 );
      Canvas canvas = new Canvas( bitmap );
      if ( drawing_surface.drawCanvas( canvas ) ) {
        String now = TDUtil.currentDateTimeFull();
        String path = TDPath.getOutFile( now + ".png" );
        File imageFile = new File(path);
        FileOutputStream outputStream = new FileOutputStream(imageFile);
        int quality = 100;
        // bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, outputStream);
        outputStream.flush();
        outputStream.close();
        TDToast.make( String.format( getResources().getString( R.string.screenshot_saved ), path ) );
      } else {
        TDLog.e( "failed drawing canvas" );
      }
    } catch (Throwable e) {
      // Several error may come out with file handling or DOM
      e.printStackTrace();
      TDToast.makeWarn( R.string.screenshot_failed );
    }
  }


}
