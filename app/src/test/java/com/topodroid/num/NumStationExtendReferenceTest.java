package com.topodroid.num;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NumStationExtendReferenceTest
{
  @Test public void resolver_exposesProductionLegAndExtendDirection()
  {
    NumStation station = new NumStation( "A" );
    station.addAzimuth( 0.0f, 1.0f );
    station.setAzimuths();

    NumStation.ExtendReference north = station.resolveExtendReference( 20.0f );
    assertTrue( north.valid );
    assertEquals( 0.0f, north.bearingDegrees, 0.001f );
    assertEquals( 1.0f, north.extendSign, 0.001f );
    assertEquals( (float)Math.cos( Math.toRadians( 20.0 ) ), north.cosine, 0.001f );
    assertFalse( north.ambiguous );

    NumStation.ExtendReference south = station.resolveExtendReference( 200.0f );
    assertTrue( south.valid );
    assertEquals( 180.0f, south.bearingDegrees, 0.001f );
    assertEquals( -1.0f, south.extendSign, 0.001f );
  }

  @Test public void resolver_marksBranchingStationAmbiguous()
  {
    NumStation station = new NumStation( "J" );
    station.addAzimuth( 0.0f, 1.0f );
    station.addAzimuth( 90.0f, 1.0f );
    station.setAzimuths();
    NumStation.ExtendReference reference = station.resolveExtendReference( 15.0f );
    assertTrue( reference.valid );
    assertTrue( reference.ambiguous );
    assertEquals( 0.0f, reference.bearingDegrees, 0.001f );
  }
}
