package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.topodroid.types.BlockType;
import com.topodroid.types.LegType;

import org.junit.Test;

public class BacksightLegTypeRegressionTest
{
  @Test public void persistedBackLegFromRedDirtArchive_isRenderable()
  {
    DBlock block = new DBlock();
    block.setBlockName( "RD1", "A15BC", true );

    block.setBlockTypeFromLegType( LegType.BACK );

    assertEquals( BlockType.BACK_LEG, block.getBlockType() );
    assertEquals( BlockType.mTypeColor[ BlockType.BACK_LEG ], block.getColorByType() );
  }

  @Test public void everyPersistedSpecialLegType_mapsBackToItsBlockType()
  {
    assertLegBlockPair( LegType.EXTRA,   BlockType.SEC_LEG );
    assertLegBlockPair( LegType.SPLAY,   BlockType.SPLAY );
    assertLegBlockPair( LegType.XSPLAY,  BlockType.X_SPLAY );
    assertLegBlockPair( LegType.HSPLAY,  BlockType.H_SPLAY );
    assertLegBlockPair( LegType.VSPLAY,  BlockType.V_SPLAY );
    assertLegBlockPair( LegType.SCAN,    BlockType.SCAN );
    assertLegBlockPair( LegType.XSCAN,   BlockType.XSCAN );
    assertLegBlockPair( LegType.HSCAN,   BlockType.HSCAN );
    assertLegBlockPair( LegType.VSCAN,   BlockType.VSCAN );
    assertLegBlockPair( LegType.BLUNDER, BlockType.BLUNDER );
    assertLegBlockPair( LegType.BACK,    BlockType.BACK_LEG );
  }

  @Test public void everyDefinedLegType_hasAStringRepresentation()
  {
    for ( int legType = LegType.NORMAL; legType <= LegType.BACK; ++legType ) {
      String label = LegType.getString( legType );
      if ( label == null ) {
        throw new AssertionError( "Missing string representation for leg type " + legType );
      }
    }
    assertEquals( "b", LegType.getString( LegType.BACK ) );
  }

  @Test public void splayCycle_acceptsBothPlainSplayRepresentations()
  {
    assertEquals( LegType.XSPLAY, LegType.nextSplayClass( LegType.NORMAL ) );
    assertEquals( LegType.XSPLAY, LegType.nextSplayClass( LegType.SPLAY ) );
    assertEquals( LegType.HSPLAY, LegType.nextSplayClass( LegType.XSPLAY ) );
    assertEquals( LegType.VSPLAY, LegType.nextSplayClass( LegType.HSPLAY ) );
    assertEquals( LegType.NORMAL, LegType.nextSplayClass( LegType.VSPLAY ) );
  }

  private static void assertLegBlockPair( int legType, int blockType )
  {
    assertEquals( "leg-to-block mapping for " + legType,
      blockType, BlockType.LegToBlock[ legType ] );
    assertEquals( "block-to-leg mapping for " + blockType,
      legType, LegType.BlockToLeg[ blockType ] );
    assertTrue( "renderable color for block type " + blockType,
      blockType >= 0 && blockType < BlockType.mTypeColor.length );
  }
}
