package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SurveyShotPairingTest
{
  @Test public void recognizesOnlyReversedNamedBackLeg()
  {
    assertTrue( SurveyShotPairing.isNamedBacksightPair( "A", "B", "B", "A", true ) );
    assertFalse( SurveyShotPairing.isNamedBacksightPair( "A", "B", "B", "A", false ) );
    assertFalse( SurveyShotPairing.isNamedBacksightPair( "A", "B", "A", "B", true ) );
  }

  @Test public void normalizesReverseAnglesIntoForwardDirection()
  {
    assertEquals( 90.0f, SurveyShotPairing.forwardBearing( 270.0f ), 0.0001f );
    assertEquals( 5.0f, SurveyShotPairing.forwardBearing( 185.0f ), 0.0001f );
    assertEquals( 12.5f, SurveyShotPairing.forwardClino( -12.5f ), 0.0001f );
  }
}
