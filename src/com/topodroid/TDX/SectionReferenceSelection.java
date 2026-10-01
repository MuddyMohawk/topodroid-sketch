package com.topodroid.TDX;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** A section's reference choices; independent of app-wide display preferences. */
final class SectionReferenceSelection
{
  final Set<String> stations = new LinkedHashSet<>();
  final Set<String> hiddenSplays = new LinkedHashSet<>();
  boolean showOutOfPlane = true;

  SectionReferenceSelection() { }

  SectionReferenceSelection( SectionReferenceSelection other )
  {
    stations.addAll( other.stations );
    hiddenSplays.addAll( other.hiddenSplays );
    showOutOfPlane = other.showOutOfPlane;
  }

  boolean includes( String station ) { return stations.contains( station ); }
  boolean includesLeg( String from, String to ) { return includes( from ) || includes( to ); }
  boolean includesSplays( String station ) { return includes( station ) && ! hiddenSplays.contains( station ); }

  void setStation( String station, boolean checked )
  {
    if ( checked ) stations.add( station ); else stations.remove( station );
  }

  void setSplays( String station, boolean checked )
  {
    if ( checked ) hiddenSplays.remove( station ); else hiddenSplays.add( station );
  }

  static boolean isOutOfPlane( float angle, float threshold ) { return Math.abs( angle ) > threshold; }

  void rename( java.util.Map<String,String> names )
  {
    renameSet( stations, names );
    renameSet( hiddenSplays, names );
  }

  private static void renameSet( Set<String> target, java.util.Map<String,String> names )
  {
    Collection<String> previous = new java.util.ArrayList<>( target );
    target.clear();
    for ( String station : previous ) target.add( names.containsKey( station ) ? names.get( station ) : station );
  }
}
