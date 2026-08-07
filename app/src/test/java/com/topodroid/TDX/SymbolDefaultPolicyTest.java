package com.topodroid.TDX;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SymbolDefaultPolicyTest
{
  @Test public void requestedPoints_areExcludedByDefault()
  {
    String[] excluded = {
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

    for ( String th_name : excluded ) {
      assertFalse( th_name, SymbolDefaultPolicy.isPointEnabled( th_name ) );
    }
  }

  @Test public void everyOtherPoint_isIncludedByDefault()
  {
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "angular-block" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "bedding-attitude" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "calcite-rafts" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "helictite" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "leaf-litter-organic" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "passage-height" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "vegetation" ) );
    assertTrue( SymbolDefaultPolicy.isPointEnabled( "future-symbol" ) );
  }

  @Test public void onlyRequestedLine_isExcludedByDefault()
  {
    assertFalse( SymbolDefaultPolicy.isLineEnabled( "pool-fingers" ) );
    assertTrue( SymbolDefaultPolicy.isLineEnabled( "pit" ) );
    assertTrue( SymbolDefaultPolicy.isLineEnabled( "future-line" ) );
  }

  @Test public void everyArea_isIncludedByDefault()
  {
    assertTrue( SymbolDefaultPolicy.isAreaEnabled( "water" ) );
    assertTrue( SymbolDefaultPolicy.isAreaEnabled( "future-area" ) );
  }

  @Test public void invalidNames_areNeverIncluded()
  {
    assertFalse( SymbolDefaultPolicy.isPointEnabled( null ) );
    assertFalse( SymbolDefaultPolicy.isLineEnabled( null ) );
    assertFalse( SymbolDefaultPolicy.isAreaEnabled( null ) );
  }
}
