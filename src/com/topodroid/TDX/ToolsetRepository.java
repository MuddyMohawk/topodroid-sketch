/* @file ToolsetRepository.java
 *
 * Global shared profile definitions plus per-survey active selection.
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;

final class ToolsetRepository
{
  private static final int SCHEMA = 1;
  private static final String KEY_PROFILES = "sketch_profiles_v1";
  private static final String KEY_SURVEY_PREFIX = "sketch_profile_survey_";
  private static final Object LOCK = new Object();

  private static boolean sLoaded;
  private static final ArrayList< ToolsetProfile > sProfiles = new ArrayList<>();

  private ToolsetRepository() { }

  static ArrayList< ToolsetProfile > profiles( DataHelper data )
  {
    synchronized ( LOCK ) {
      ensureLoaded( data );
      ArrayList< ToolsetProfile > copy = new ArrayList<>();
      for ( ToolsetProfile profile : sProfiles ) copy.add( profile.copy() );
      return copy;
    }
  }

  static ToolsetProfile profile( DataHelper data, String id )
  {
    synchronized ( LOCK ) {
      ensureLoaded( data );
      ToolsetProfile profile = find( id );
      return ( profile == null ? find( ToolsetProfile.DEFAULT_ID ) : profile ).copy();
    }
  }

  static ToolsetProfile activeProfile( DataHelper data, long surveyId )
  {
    synchronized ( LOCK ) {
      ensureLoaded( data );
      String id = data == null ? null : data.getValue( selectionKey( surveyId ) );
      ToolsetProfile profile = find( id );
      if ( profile == null ) profile = find( ToolsetProfile.DEFAULT_ID );
      return profile.copy();
    }
  }

  static String activeProfileId( DataHelper data, long surveyId )
  {
    return activeProfile( data, surveyId ).mId;
  }

  static boolean selectProfile( DataHelper data, long surveyId, String id )
  {
    synchronized ( LOCK ) {
      ensureLoaded( data );
      ToolsetProfile profile = find( id );
      if ( profile == null || data == null ) return false;
      data.setValue( selectionKey( surveyId ), profile.mId );
      return profile.mId.equals( data.getValue( selectionKey( surveyId ) ) );
    }
  }

  static boolean saveProfile( DataHelper data, ToolsetProfile profile )
  {
    if ( profile == null ) return false;
    synchronized ( LOCK ) {
      ensureLoaded( data );
      int index = indexOf( profile.mId );
      ToolsetProfile previous = index < 0 ? null : sProfiles.get( index );
      if ( index < 0 ) sProfiles.add( profile.copy() ); else sProfiles.set( index, profile.copy() );
      if ( write( data ) ) return true;
      if ( index < 0 ) sProfiles.remove( sProfiles.size() - 1 ); else sProfiles.set( index, previous );
      return false;
    }
  }

  static ToolsetProfile duplicate( DataHelper data, ToolsetProfile source, String name )
  {
    if ( source == null || ! validNewName( data, name ) ) return null;
    ToolsetProfile duplicate = new ToolsetProfile( UUID.randomUUID().toString(), name.trim() );
    duplicate.mVisibleSlots = source.mVisibleSlots;
    duplicate.mOnCanvas.clear();
    duplicate.mOnCanvas.addAll( source.mOnCanvas );
    for ( int row = 0; row < ToolsetProfile.ROW_COUNT; ++row ) {
      for ( int slot = 0; slot < ToolsetProfile.ROW_CAPACITY; ++slot ) {
        ToolsetProfile.Slot value = source.mRows[row][slot];
        duplicate.mRows[row][slot] = value == null ? null : value.copy();
      }
    }
    for ( int slot = 0; slot < ToolsetProfile.QUICK_CAPACITY; ++slot ) {
      ToolsetProfile.Slot value = source.mQuick[slot];
      duplicate.mQuick[slot] = value == null ? null : value.copy();
    }
    return saveProfile( data, duplicate ) ? duplicate.copy() : null;
  }

  static boolean deleteProfile( DataHelper data, String id )
  {
    synchronized ( LOCK ) {
      ensureLoaded( data );
      if ( ToolsetProfile.DEFAULT_ID.equals( id ) ) return false;
      int index = indexOf( id );
      if ( index < 0 ) return false;
      ToolsetProfile removed = sProfiles.remove( index );
      if ( write( data ) ) return true;
      sProfiles.add( index, removed );
      return false;
    }
  }

  static boolean validNewName( DataHelper data, String name )
  {
    if ( name == null ) return false;
    String value = name.trim();
    if ( value.length() < 1 || value.length() > 40 ) return false;
    synchronized ( LOCK ) {
      ensureLoaded( data );
      for ( ToolsetProfile profile : sProfiles ) if ( profile.mName.equalsIgnoreCase( value ) ) return false;
    }
    return true;
  }

  private static void ensureLoaded( DataHelper data )
  {
    if ( sLoaded ) return;
    sLoaded = true;
    sProfiles.clear();
    String stored = data == null ? null : data.getValue( KEY_PROFILES );
    if ( stored != null ) {
      try {
        JSONObject root = new JSONObject( stored );
        if ( root.optInt( "schema", 0 ) == SCHEMA ) {
          JSONArray profiles = root.optJSONArray( "profiles" );
          if ( profiles != null ) {
            for ( int k = 0; k < profiles.length(); ++k ) {
              ToolsetProfile profile = ToolsetProfile.fromJson( profiles.optJSONObject( k ) );
              if ( profile != null && indexOf( profile.mId ) < 0 ) sProfiles.add( profile );
            }
          }
        }
      } catch ( JSONException e ) {
        TDLog.e( "Toolset profile JSON ignored: " + e.getMessage() );
      }
    }
    if ( find( ToolsetProfile.DEFAULT_ID ) == null ) sProfiles.add( 0, ToolsetProfile.freshDefault() );
  }

  private static boolean write( DataHelper data )
  {
    if ( data == null ) return false;
    try {
      JSONObject root = new JSONObject();
      root.put( "schema", SCHEMA );
      JSONArray profiles = new JSONArray();
      for ( ToolsetProfile profile : sProfiles ) profiles.put( profile.toJson() );
      root.put( "profiles", profiles );
      String value = root.toString();
      data.setValue( KEY_PROFILES, value );
      return value.equals( data.getValue( KEY_PROFILES ) );
    } catch ( JSONException e ) {
      TDLog.e( "Toolset profile save failed: " + e.getMessage() );
      return false;
    }
  }

  private static ToolsetProfile find( String id )
  {
    if ( id == null ) return null;
    for ( ToolsetProfile profile : sProfiles ) if ( id.equals( profile.mId ) ) return profile;
    return null;
  }

  private static int indexOf( String id )
  {
    if ( id == null ) return -1;
    for ( int k = 0; k < sProfiles.size(); ++k ) if ( id.equals( sProfiles.get( k ).mId ) ) return k;
    return -1;
  }

  private static String selectionKey( long surveyId ) { return KEY_SURVEY_PREFIX + Math.max( 0, surveyId ); }
}
