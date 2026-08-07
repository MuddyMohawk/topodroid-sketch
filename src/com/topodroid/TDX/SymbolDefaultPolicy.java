/* @file SymbolDefaultPolicy.java
 *
 * @brief Default inclusion policy for the main drawing-symbol palette
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

/** Defaults used only when a symbol has no saved enabled/disabled preference. */
final class SymbolDefaultPolicy
{
  private static final String[] EXCLUDED_POINTS = {
    "anastomosis",
    "anthodites",
    "aragonite",
    "archeo-excavation",
    "boxwork",
    "calcite-spar",
    "cave-pearl",
    "conulite",
    "folia",
    "gypsum-chandelier",
    "gypsum-dripholes",
    "gypsum-hair",
    "gypsum-needles",
    "gypsum-rim-vent",
    "subaqueous-helictites",
    "mammalaries-cave-clouds",
    "moonmilk",
    "pool-spar",
    "raft-cone",
    "rusticles"
  };

  private static final String[] EXCLUDED_LINES = {
    "pool-fingers"
  };

  private SymbolDefaultPolicy() { }

  static boolean isPointEnabled( String th_name )
  {
    return th_name != null && ! contains( EXCLUDED_POINTS, th_name );
  }

  static boolean isLineEnabled( String th_name )
  {
    return th_name != null && ! contains( EXCLUDED_LINES, th_name );
  }

  static boolean isAreaEnabled( String th_name )
  {
    return th_name != null;
  }

  private static boolean contains( String[] names, String th_name )
  {
    for ( String name : names ) if ( name.equals( th_name ) ) return true;
    return false;
  }
}
