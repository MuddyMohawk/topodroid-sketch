/* @file ToolsetProfile.java
 *
 * Versioned, mixed-type toolbar profile model.
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ToolsetProfile
{
  static final int ROW_COUNT = 8;
  static final int ROW_CAPACITY = 16;
  static final int QUICK_CAPACITY = 12;
  static final int MIN_VISIBLE_SLOTS = 4;
  static final int MAX_VISIBLE_SLOTS = 16;
  static final int DEFAULT_VISIBLE_SLOTS = 8;
  static final String DEFAULT_ID = "default";

  static final class Slot
  {
    final int mType;
    final String mFullThName;

    Slot( int type, String fullThName )
    {
      mType = isType( type ) ? type : SymbolType.UNDEF;
      mFullThName = ( fullThName == null ) ? null : fullThName.trim();
    }

    boolean isValid() { return isType( mType ) && mFullThName != null && mFullThName.length() > 0; }

    Slot copy() { return isValid() ? new Slot( mType, mFullThName ) : null; }

    JSONObject toJson() throws JSONException
    {
      JSONObject json = new JSONObject();
      json.put( "type", typeName( mType ) );
      json.put( "name", mFullThName );
      return json;
    }

    static Slot fromJson( JSONObject json )
    {
      if ( json == null ) return null;
      Slot slot = new Slot( typeFromName( json.optString( "type", "" ) ), json.optString( "name", null ) );
      return slot.isValid() ? slot : null;
    }

    @Override public boolean equals( Object other )
    {
      if ( this == other ) return true;
      if ( ! ( other instanceof Slot ) ) return false;
      Slot slot = (Slot)other;
      return mType == slot.mType && mFullThName.equals( slot.mFullThName );
    }

    @Override public int hashCode() { return 31 * mType + mFullThName.hashCode(); }
  }

  final String mId;
  String mName;
  int mVisibleSlots;
  final Slot[][] mRows = new Slot[ ROW_COUNT ][ ROW_CAPACITY ];
  final Slot[] mQuick = new Slot[ QUICK_CAPACITY ];
  final ArrayList< Integer > mOnCanvas = new ArrayList<>();

  ToolsetProfile( String id, String name )
  {
    mId = ( id == null || id.trim().length() == 0 ) ? DEFAULT_ID : id.trim();
    mName = ( name == null || name.trim().length() == 0 ) ? "Default" : name.trim();
    mVisibleSlots = DEFAULT_VISIBLE_SLOTS;
    mOnCanvas.add( 0 );
  }

  boolean isDefault() { return DEFAULT_ID.equals( mId ); }

  ToolsetProfile copy()
  {
    ToolsetProfile copy = new ToolsetProfile( mId, mName );
    copy.mVisibleSlots = mVisibleSlots;
    copy.mOnCanvas.clear();
    copy.mOnCanvas.addAll( mOnCanvas );
    for ( int row = 0; row < ROW_COUNT; ++row ) {
      for ( int slot = 0; slot < ROW_CAPACITY; ++slot ) copy.mRows[row][slot] = copyOf( mRows[row][slot] );
    }
    for ( int slot = 0; slot < QUICK_CAPACITY; ++slot ) copy.mQuick[slot] = copyOf( mQuick[slot] );
    copy.ensureInvariants();
    return copy;
  }

  List< Integer > onCanvasRows() { return Collections.unmodifiableList( mOnCanvas ); }

  boolean isOnCanvas( int row ) { return mOnCanvas.contains( row ); }

  void setVisibleSlots( int count )
  {
    mVisibleSlots = Math.max( MIN_VISIBLE_SLOTS, Math.min( MAX_VISIBLE_SLOTS, count ) );
  }

  void moveRowToCanvasEnd( int row )
  {
    if ( row < 0 || row >= ROW_COUNT || mOnCanvas.contains( row ) ) return;
    mOnCanvas.add( row );
  }

  boolean moveRowOffCanvas( int row )
  {
    if ( mOnCanvas.size() <= 1 ) return false;
    return mOnCanvas.remove( Integer.valueOf( row ) );
  }

  void moveCanvasRow( int from, int to )
  {
    if ( from < 0 || from >= mOnCanvas.size() ) return;
    int bounded = Math.max( 0, Math.min( mOnCanvas.size() - 1, to ) );
    Integer row = mOnCanvas.remove( from );
    mOnCanvas.add( bounded, row );
  }

  JSONObject toJson() throws JSONException
  {
    JSONObject json = new JSONObject();
    json.put( "id", mId );
    json.put( "name", mName );
    json.put( "slotCount", mVisibleSlots );
    JSONArray canvas = new JSONArray();
    for ( Integer row : mOnCanvas ) canvas.put( rowName( row ) );
    json.put( "onCanvas", canvas );
    JSONObject rows = new JSONObject();
    for ( int row = 0; row < ROW_COUNT; ++row ) rows.put( rowName( row ), slotsToJson( mRows[row] ) );
    json.put( "rows", rows );
    json.put( "quick", slotsToJson( mQuick ) );
    return json;
  }

  static ToolsetProfile fromJson( JSONObject json )
  {
    if ( json == null ) return null;
    String id = json.optString( "id", null );
    if ( id == null || id.trim().length() == 0 ) return null;
    ToolsetProfile profile = new ToolsetProfile( id, json.optString( "name", "Profile" ) );
    profile.setVisibleSlots( json.optInt( "slotCount", DEFAULT_VISIBLE_SLOTS ) );
    profile.mOnCanvas.clear();
    JSONArray canvas = json.optJSONArray( "onCanvas" );
    if ( canvas != null ) {
      for ( int k = 0; k < canvas.length(); ++k ) {
        int row = rowFromName( canvas.optString( k, "" ) );
        if ( row >= 0 && ! profile.mOnCanvas.contains( row ) ) profile.mOnCanvas.add( row );
      }
    }
    JSONObject rows = json.optJSONObject( "rows" );
    if ( rows != null ) {
      for ( int row = 0; row < ROW_COUNT; ++row ) slotsFromJson( rows.optJSONArray( rowName( row ) ), profile.mRows[row] );
    }
    slotsFromJson( json.optJSONArray( "quick" ), profile.mQuick );
    profile.ensureInvariants();
    return profile;
  }

  static ToolsetProfile freshDefault()
  {
    ToolsetProfile profile = new ToolsetProfile( DEFAULT_ID, "Default" );
    profile.mOnCanvas.clear();
    profile.mOnCanvas.add( 0 );
    profile.mOnCanvas.add( 1 );
    profile.mRows[0] = slots(
      line( SymbolLibrary.WALL ), line( SymbolLibrary.USER ), line( SymbolLibrary.PIT ), line( SymbolLibrary.CHIMNEY ),
      line( SymbolLibrary.SLOPE ), line( "dashed" ), line( "dotted" ), line( SymbolLibrary.WATER_FLOW )
    );
    profile.mRows[1] = slots(
      point( SymbolLibrary.BLOCKS ), point( "boulder" ), point( SymbolLibrary.PEBBLES ), point( SymbolLibrary.SAND ),
      area( SymbolLibrary.CLAY ), area( SymbolLibrary.WATER ), point( SymbolLibrary.STALACTITE ), point( SymbolLibrary.STALAGMITE )
    );
    Slot[] quick = {
      point( SymbolLibrary.LABEL ), point( SymbolLibrary.STATION ), line( SymbolLibrary.SECTION ),
      point( "passage-height" ), point( "pit-depth" ), point( SymbolLibrary.CONTINUATION ),
      point( SymbolLibrary.AIR_DRAUGHT ), point( "anchor" )
    };
    System.arraycopy( quick, 0, profile.mQuick, 0, quick.length );
    return profile;
  }

  static String rowName( int row ) { return String.valueOf( (char)( 'A' + row ) ); }

  static int rowFromName( String name )
  {
    if ( name == null || name.length() != 1 ) return -1;
    int row = Character.toUpperCase( name.charAt( 0 ) ) - 'A';
    return ( row >= 0 && row < ROW_COUNT ) ? row : -1;
  }

  private void ensureInvariants()
  {
    setVisibleSlots( mVisibleSlots );
    for ( int k = mOnCanvas.size() - 1; k >= 0; --k ) {
      int row = mOnCanvas.get( k );
      if ( row < 0 || row >= ROW_COUNT || mOnCanvas.indexOf( row ) != k ) mOnCanvas.remove( k );
    }
    if ( mOnCanvas.isEmpty() ) mOnCanvas.add( 0 );
  }

  private static Slot copyOf( Slot slot ) { return slot == null ? null : slot.copy(); }

  private static JSONArray slotsToJson( Slot[] slots ) throws JSONException
  {
    JSONArray json = new JSONArray();
    for ( Slot slot : slots ) json.put( slot == null ? JSONObject.NULL : slot.toJson() );
    return json;
  }

  private static void slotsFromJson( JSONArray json, Slot[] target )
  {
    if ( json == null ) return;
    int count = Math.min( target.length, json.length() );
    for ( int k = 0; k < count; ++k ) target[k] = Slot.fromJson( json.optJSONObject( k ) );
  }

  private static Slot[] slots( Slot... initial )
  {
    Slot[] slots = new Slot[ ROW_CAPACITY ];
    System.arraycopy( initial, 0, slots, 0, Math.min( initial.length, slots.length ) );
    return slots;
  }

  private static Slot point( String name ) { return new Slot( SymbolType.POINT, name ); }
  private static Slot line( String name ) { return new Slot( SymbolType.LINE, name ); }
  private static Slot area( String name ) { return new Slot( SymbolType.AREA, name ); }

  static boolean isType( int type ) { return type == SymbolType.POINT || type == SymbolType.LINE || type == SymbolType.AREA; }

  static String typeName( int type )
  {
    if ( type == SymbolType.POINT ) return "point";
    if ( type == SymbolType.LINE ) return "line";
    if ( type == SymbolType.AREA ) return "area";
    return "";
  }

  static int typeFromName( String type )
  {
    if ( "point".equals( type ) ) return SymbolType.POINT;
    if ( "line".equals( type ) ) return SymbolType.LINE;
    if ( "area".equals( type ) ) return SymbolType.AREA;
    return SymbolType.UNDEF;
  }
}
