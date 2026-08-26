package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.topodroid.dev.cavway.CavwayConst;

import org.junit.Test;

public class CavwayBacksightFlagPolicyTest
{
  @Test public void disabled_keepsCavwayBacksightLabelOnly()
  {
    long input = cavwayFlag( CavwayConst.FLAG_BACKSIGHT );

    long result = CavwayBacksightFlagPolicy.apply( input, false );

    assertEquals( input, result );
    assertFalse( DBlock.isBackshot( result ) );
  }

  @Test public void enabled_addsTopoDroidBackshotAndKeepsCavwayLabel()
  {
    long result = CavwayBacksightFlagPolicy.apply(
      cavwayFlag( CavwayConst.FLAG_BACKSIGHT ), true );

    assertTrue( DBlock.isBackshot( result ) );
    assertEquals( CavwayConst.FLAG_BACKSIGHT, DBlock.cavwayFlag( result ) );
  }

  @Test public void enabled_doesNotMapOtherCavwayLabels()
  {
    int[] labels = {
      CavwayConst.FLAG_NONE,
      CavwayConst.FLAG_GENERIC,
      CavwayConst.FLAG_RIDGE,
      CavwayConst.FLAG_FEATURE
    };

    for ( int label : labels ) {
      long input = cavwayFlag( label );
      long result = CavwayBacksightFlagPolicy.apply( input, true );
      assertEquals( input, result );
      assertFalse( DBlock.isBackshot( result ) );
    }
  }

  @Test public void enabled_preservesExistingTopoDroidBits()
  {
    long input = cavwayFlag( CavwayConst.FLAG_BACKSIGHT )
      | DBlock.FLAG_NO_PLAN | DBlock.FLAG_TAMPERED;

    long result = CavwayBacksightFlagPolicy.apply( input, true );

    assertTrue( DBlock.isBackshot( result ) );
    assertTrue( DBlock.isNoPlan( result ) );
    assertTrue( DBlock.isTampered( result ) );
    assertEquals( CavwayConst.FLAG_BACKSIGHT, DBlock.cavwayFlag( result ) );
  }

  private static long cavwayFlag( int label )
  {
    return ((long)label) << 16;
  }
}
