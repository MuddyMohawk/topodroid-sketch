/* @file SpecialPointRegistry.java
 *
 * @brief Registry of survey-aware point behaviors, keyed by stable Therion name
 */
package com.topodroid.TDX;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class SpecialPointRegistry
{
  private static final Map< String, SpecialPointBehavior > BY_THERION;
  private static final Map< String, SpecialPointBehavior > BY_ID;

  static {
    HashMap< String, SpecialPointBehavior > therion = new HashMap<>();
    HashMap< String, SpecialPointBehavior > ids = new HashMap<>();
    register( therion, ids, new CeilingHeightPointBehavior() );
    register( therion, ids, new PitDepthPointBehavior() );
    register( therion, ids, new BeddingAttitudePointBehavior() );
    register( therion, ids, new TitleLegendPointBehavior() );
    register( therion, ids, new CaverPointBehavior() );
    BY_THERION = Collections.unmodifiableMap( therion );
    BY_ID = Collections.unmodifiableMap( ids );
  }

  private SpecialPointRegistry() { }

  static SpecialPointBehavior forTherionName( String therion_name )
  {
    return ( therion_name == null ) ? null : BY_THERION.get( therion_name );
  }

  static SpecialPointBehavior forBehaviorId( String behavior_id )
  {
    return ( behavior_id == null ) ? null : BY_ID.get( behavior_id );
  }

  static boolean isSpecial( String therion_name )
  {
    return forTherionName( therion_name ) != null;
  }

  private static void register( Map< String, SpecialPointBehavior > therion,
                                Map< String, SpecialPointBehavior > ids,
                                SpecialPointBehavior behavior )
  {
    if ( behavior == null ) return;
    String name = behavior.therionName();
    String id = behavior.behaviorId();
    if ( name == null || name.length() == 0 || id == null || id.length() == 0 ) {
      throw new IllegalArgumentException( "Special point behaviors require stable names" );
    }
    if ( therion.put( name, behavior ) != null || ids.put( id, behavior ) != null ) {
      throw new IllegalStateException( "Duplicate special point behavior " + id );
    }
  }
}
