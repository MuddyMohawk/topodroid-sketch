package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.topodroid.types.BlockType;
import com.topodroid.types.LegType;

import org.junit.Test;

public class BacksightLegTypeRegressionTest
{
  @Test public void persistedIds_matchVanillaTopoDroid()
  {
    assertEquals( 0, LegType.NORMAL );
    assertEquals( 1, LegType.EXTRA );
    assertEquals( 2, LegType.XSPLAY );
    assertEquals( 3, LegType.BACK );
    assertEquals( 4, LegType.HSPLAY );
    assertEquals( 5, LegType.VSPLAY );
    assertEquals( 6, LegType.SCAN );
    assertEquals( 7, LegType.XSCAN );
    assertEquals( 8, LegType.HSCAN );
    assertEquals( 9, LegType.VSCAN );
    assertEquals( 10, LegType.BLUNDER );
  }

  @Test public void persistedVanillaBackLeg_isRenderable()
  {
    DBlock block = new DBlock();
    block.setBlockName( "RD1", "A15BC", true );

    block.setBlockTypeFromLegType( 3 );

    assertEquals( BlockType.BACK_LEG, block.getBlockType() );
    assertEquals( BlockType.mTypeColor[ BlockType.BACK_LEG ], block.getColorByType() );
  }

  @Test public void persistedTypes_mapToVanillaBlockTypes()
  {
    assertLegBlockPair( LegType.EXTRA,   BlockType.SEC_LEG );
    assertLegBlockPair( LegType.XSPLAY,  BlockType.X_SPLAY );
    assertLegBlockPair( LegType.BACK,    BlockType.BACK_LEG );
    assertLegBlockPair( LegType.HSPLAY,  BlockType.H_SPLAY );
    assertLegBlockPair( LegType.VSPLAY,  BlockType.V_SPLAY );
    assertLegBlockPair( LegType.SCAN,    BlockType.SCAN );
    assertLegBlockPair( LegType.XSCAN,   BlockType.XSCAN );
    assertLegBlockPair( LegType.HSCAN,   BlockType.HSCAN );
    assertLegBlockPair( LegType.VSCAN,   BlockType.VSCAN );
    assertLegBlockPair( LegType.BLUNDER, BlockType.BLUNDER );

    assertEquals( LegType.NORMAL, LegType.BlockToLeg[ BlockType.SPLAY ] );
    assertEquals( BlockType.INVALID, BlockType.LegToBlock[ 11 ] );
  }

  @Test public void labels_matchVanillaPersistedIds()
  {
    assertEquals( "X", LegType.getString( LegType.XSPLAY ) );
    assertEquals( "b", LegType.getString( LegType.BACK ) );
    assertNull( LegType.getString( LegType.BLUNDER ) );
    assertNull( LegType.getString( 11 ) );
  }

  @Test public void splayCycle_usesNormalForPlainSplays()
  {
    assertEquals( LegType.XSPLAY, LegType.nextSplayClass( LegType.NORMAL ) );
    assertEquals( LegType.HSPLAY, LegType.nextSplayClass( LegType.XSPLAY ) );
    assertEquals( LegType.VSPLAY, LegType.nextSplayClass( LegType.HSPLAY ) );
    assertEquals( LegType.NORMAL, LegType.nextSplayClass( LegType.VSPLAY ) );
    assertEquals( LegType.INVALID, LegType.nextSplayClass( LegType.BACK ) );
  }

  @Test public void legacySketchBackId_isNotTranslatedOrAllowedToCrashRendering()
  {
    DBlock block = new DBlock();
    block.setBlockName( "RD1", "A15BC", false );

    block.setBlockTypeFromLegType( 11 );

    assertEquals( BlockType.MAIN_LEG, block.getBlockType() );
    assertEquals( BlockType.mTypeColor[ BlockType.MAIN_LEG ], block.getColorByType() );
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
