package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class StationNameOrderTest
{
  @Test public void naturalOrder_handlesNumericAndMixedStationNames()
  {
    ArrayList< String > names = new ArrayList<>( Arrays.asList(
      "A10", "2", "1.10", "A2", "10", "1.2", "1", "A1" ) );

    Collections.sort( names, StationNameOrder::compare );

    assertEquals( Arrays.asList( "1", "1.2", "1.10", "2", "10", "A1", "A2", "A10" ), names );
  }

  @Test public void naturalOrder_isStableForEqualAndLeadingZeroValues()
  {
    assertEquals( 0, StationNameOrder.compare( "A12", "A12" ) );
    ArrayList< String > names = new ArrayList<>( Arrays.asList( "A002", "A02", "A2" ) );
    Collections.sort( names, StationNameOrder::compare );
    assertEquals( Arrays.asList( "A2", "A02", "A002" ), names );
  }
}
