/* @file SpecialPointEnvelope.java
 *
 * @brief Versioned private-option envelope for survey-aware point state
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

final class SpecialPointEnvelope
{
  static final int ENVELOPE_VERSION = 1;

  static final class Decoded
  {
    final int envelopeVersion;
    final String behaviorId;
    final int stateVersion;
    final JSONObject payload;

    Decoded( int envelope_version, String behavior_id, int state_version, JSONObject data )
    {
      envelopeVersion = envelope_version;
      behaviorId = behavior_id;
      stateVersion = state_version;
      payload = data;
    }
  }

  private SpecialPointEnvelope() { }

  static Decoded fromOptions( String options )
  {
    String encoded = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_SPECIAL );
    if ( encoded == null || encoded.length() == 0 ) return null;
    try {
      byte[] bytes = Base64.decode( encoded, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING );
      JSONObject json = new JSONObject( new String( bytes, StandardCharsets.UTF_8 ) );
      return new Decoded(
        json.optInt( "envelope", -1 ),
        json.optString( "behavior", "" ),
        json.optInt( "stateVersion", -1 ),
        json.optJSONObject( "data" )
      );
    } catch ( IllegalArgumentException | JSONException e ) {
      TDLog.e( "Special point state decode failed: " + e.getMessage() );
      return null;
    }
  }

  static String store( String options, SpecialPointBehavior behavior, SpecialPointState state )
  {
    if ( behavior == null || state == null ) {
      return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_SPECIAL );
    }
    try {
      return encodeOptions( options, behavior, state );
    } catch ( JSONException | IllegalArgumentException e ) {
      TDLog.e( "Special point state encode failed: " + e.getMessage() );
      return options;
    }
  }

  static String encodeOptions( String options, SpecialPointBehavior behavior,
                               SpecialPointState state ) throws JSONException
  {
    if ( behavior == null || state == null ) {
      return SketchPrivateOptions.stripOption( options, SketchPrivateOptions.OPTION_SPECIAL );
    }
    JSONObject json = new JSONObject();
    json.put( "envelope", ENVELOPE_VERSION );
    json.put( "behavior", behavior.behaviorId() );
    json.put( "stateVersion", behavior.stateVersion() );
    json.put( "data", behavior.encodeState( state ) );
    String encoded = Base64.encodeToString(
      json.toString().getBytes( StandardCharsets.UTF_8 ),
      Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING );
    return SketchPrivateOptions.storeOption( options, SketchPrivateOptions.OPTION_SPECIAL, encoded );
  }
}
