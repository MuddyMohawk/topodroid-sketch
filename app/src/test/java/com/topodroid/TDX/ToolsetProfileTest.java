package com.topodroid.TDX;

import com.topodroid.types.SymbolType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToolsetProfileTest
{
  @Test public void freshDefault_isMixedAndFieldReady()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    assertEquals( ToolsetProfile.DEFAULT_ID, profile.mId );
    assertEquals( 8, profile.mVisibleSlots );
    assertEquals( 2, profile.mOnCanvas.size() );
    assertEquals( Integer.valueOf( 0 ), profile.mOnCanvas.get( 0 ) );
    assertEquals( Integer.valueOf( 1 ), profile.mOnCanvas.get( 1 ) );
    assertEquals( SymbolType.LINE, profile.mRows[0][0].mType );
    assertEquals( SymbolLibrary.WALL, profile.mRows[0][0].mFullThName );
    assertEquals( SymbolType.POINT, profile.mRows[1][0].mType );
    assertEquals( SymbolType.AREA, profile.mRows[1][4].mType );
    assertEquals( SymbolLibrary.STATION, profile.mQuick[1].mFullThName );
    assertNull( profile.mRows[2][0] );
  }

  @Test public void reducingSlotCount_preservesTrailingSlots()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    ToolsetProfile.Slot trailing = profile.mRows[0][7];
    profile.setVisibleSlots( 4 );
    assertEquals( 4, profile.mVisibleSlots );
    assertEquals( trailing, profile.mRows[0][7] );
    profile.setVisibleSlots( 16 );
    assertEquals( trailing, profile.mRows[0][7] );
  }

  @Test public void atLeastOneRow_remainsOnCanvas()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    assertTrue( profile.moveRowOffCanvas( 1 ) );
    assertFalse( profile.moveRowOffCanvas( 0 ) );
    assertEquals( 1, profile.mOnCanvas.size() );
  }

  @Test public void copy_isDeepAndKeepsPermanentRowIdentity()
  {
    ToolsetProfile source = ToolsetProfile.freshDefault();
    ToolsetProfile copy = source.copy();
    copy.mRows[0][0] = null;
    copy.moveCanvasRow( 0, 1 );
    assertEquals( SymbolLibrary.WALL, source.mRows[0][0].mFullThName );
    assertEquals( Integer.valueOf( 0 ), source.mOnCanvas.get( 0 ) );
    assertEquals( Integer.valueOf( 1 ), copy.mOnCanvas.get( 0 ) );
  }

  @Test public void searchNormalization_handlesPunctuationAndAccents()
  {
    assertEquals( "cave pearl audio flow", ToolsetCatalog.normalize( "Cave-pearl, Áudio_flow" ) );
  }
}
