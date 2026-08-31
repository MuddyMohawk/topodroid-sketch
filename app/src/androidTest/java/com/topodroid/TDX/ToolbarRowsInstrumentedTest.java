package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.SymbolType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ToolbarRowsInstrumentedTest
{
  private Context mPreviousContext;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );
  }

  @After
  public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void freshProfileHasTwoMixedCanvasRowsAndPinnedQuickTools()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();

    assertEquals( 8, profile.mVisibleSlots );
    assertEquals( 2, profile.mOnCanvas.size() );
    assertEquals( Integer.valueOf( 0 ), profile.mOnCanvas.get( 0 ) );
    assertEquals( Integer.valueOf( 1 ), profile.mOnCanvas.get( 1 ) );
    assertSlot( profile.mRows[0][0], SymbolType.LINE, SymbolLibrary.WALL );
    assertSlot( profile.mRows[0][1], SymbolType.LINE, SymbolLibrary.USER );
    assertSlot( profile.mRows[1][0], SymbolType.POINT, SymbolLibrary.BLOCKS );
    assertSlot( profile.mRows[1][4], SymbolType.AREA, SymbolLibrary.CLAY );
    assertSlot( profile.mRows[1][5], SymbolType.AREA, SymbolLibrary.WATER );
    assertSlot( profile.mQuick[0], SymbolType.POINT, SymbolLibrary.LABEL );
    assertSlot( profile.mQuick[1], SymbolType.POINT, SymbolLibrary.STATION );
    assertSlot( profile.mQuick[2], SymbolType.LINE, SymbolLibrary.SECTION );
  }

  @Test
  public void installedPackResolvesEveryFreshProfileReference()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    for ( int row = 0; row < ToolsetProfile.ROW_COUNT; ++row ) {
      for ( int slot = 0; slot < ToolsetProfile.ROW_CAPACITY; ++slot ) {
        ToolsetProfile.Slot ref = profile.mRows[row][slot];
        if ( ref != null ) assertNotNull( "Missing row symbol " + ref.mFullThName, ToolsetCatalog.resolve( ref ) );
      }
    }
    for ( ToolsetProfile.Slot ref : profile.mQuick ) {
      if ( ref != null ) assertNotNull( "Missing quick symbol " + ref.mFullThName, ToolsetCatalog.resolve( ref ) );
    }
  }

  @Test
  public void canvasVisibilityDoesNotDestroyConfiguredRows()
  {
    ToolsetProfile profile = ToolsetProfile.freshDefault();
    profile.mRows[7][15] = new ToolsetProfile.Slot( SymbolType.LINE, SymbolLibrary.WALL );
    profile.moveRowToCanvasEnd( 7 );
    assertTrue( profile.moveRowOffCanvas( 7 ) );
    assertSlot( profile.mRows[7][15], SymbolType.LINE, SymbolLibrary.WALL );
    assertEquals( 2, profile.mOnCanvas.size() );
  }

  private static void assertSlot( ToolsetProfile.Slot slot, int type, String name )
  {
    assertNotNull( slot );
    assertEquals( type, slot.mType );
    assertEquals( name, slot.mFullThName );
  }
}
