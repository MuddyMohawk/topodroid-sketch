package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ToolbarRowsInstrumentedTest
{
  private static final String[] DEFAULT_LINE_NAMES = {
    "water-flow", "ceiling-meander", "floor-meander", "pit", "chimney",
    "user-fine", "user-standard", "user-thick"
  };

  private int mPreviousToolbarUpdate;
  private int mPreviousToolbarSlots;
  private int mPreviousToolbarRows;
  private Context mPreviousContext;

  @Before
  public void setUp()
  {
    mPreviousToolbarUpdate = TDSetting.mToolbarUpdate;
    mPreviousToolbarSlots  = TDSetting.mToolbarSlots;
    mPreviousToolbarRows   = TDSetting.mToolbarRows;
    mPreviousContext = TDInstance.context;

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    TopoDroidApp.installSymbols( true );
    SketchLineSymbolManager.ensureLineSymbols();
    SketchLineSymbolManager.syncPrefsFromSymbolFiles();
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );

    TDSetting.mToolbarUpdate = TDSetting.TOOLBAR_UPDATE_MANUAL;
    TDSetting.mToolbarSlots = 8;
    TDSetting.mToolbarRows = 1;
    ItemDrawer.loadManualToolbarSymbols( null );
  }

  @After
  public void tearDown()
  {
    TDSetting.mToolbarUpdate = mPreviousToolbarUpdate;
    TDSetting.mToolbarSlots  = mPreviousToolbarSlots;
    TDSetting.mToolbarRows   = mPreviousToolbarRows;
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void freshManualToolbar_keeps029DefaultsInRowZero()
  {
    assertEquals( 1, ItemDrawer.getToolbarRowCount() );
    assertEquals( 8, ItemDrawer.getToolbarSlotCount() );
    for ( int slot = 0; slot < DEFAULT_LINE_NAMES.length; ++slot ) {
      assertEquals( DEFAULT_LINE_NAMES[slot], ItemDrawer.mToolbarLine[0][slot].getThName() );
    }
  }

  @Test
  public void manualRows_copyRowZeroDefaultsAndRemainAvailableWhenHidden()
  {
    TDSetting.mToolbarRows = 3;
    assertEquals( 3, ItemDrawer.getToolbarRowCount() );
    for ( int slot = 0; slot < DEFAULT_LINE_NAMES.length; ++slot ) {
      assertEquals( ItemDrawer.mToolbarLine[0][slot].getFullThName(), ItemDrawer.mToolbarLine[1][slot].getFullThName() );
      assertEquals( ItemDrawer.mToolbarLine[0][slot].getFullThName(), ItemDrawer.mToolbarLine[2][slot].getFullThName() );
    }

    Symbol hiddenRowSymbol = ItemDrawer.mToolbarLine[2][3];
    TDSetting.mToolbarRows = 1;
    assertEquals( 1, ItemDrawer.getToolbarRowCount() );
    TDSetting.mToolbarRows = 3;
    assertSame( hiddenRowSymbol, ItemDrawer.mToolbarLine[2][3] );
  }

  @Test
  public void rowLock_controlsDisplayedTypeOnlyForThatRow()
  {
    TDSetting.mToolbarRows = 3;
    ItemDrawer.setToolbarCurrentType( SymbolType.POINT );
    ItemDrawer.setToolbarRowLock( 1, SymbolType.AREA );
    assertEquals( SymbolType.POINT, ItemDrawer.getToolbarDisplayType( 0 ) );
    assertEquals( SymbolType.AREA, ItemDrawer.getToolbarDisplayType( 1 ) );

    ItemDrawer.setToolbarCurrentType( SymbolType.LINE );
    assertEquals( SymbolType.LINE, ItemDrawer.getToolbarDisplayType( 0 ) );
    assertEquals( SymbolType.AREA, ItemDrawer.getToolbarDisplayType( 1 ) );

    ItemDrawer.setToolbarRowLock( 1, ItemDrawer.TOOLBAR_LOCK_UNLOCKED );
    assertEquals( SymbolType.LINE, ItemDrawer.getToolbarDisplayType( 1 ) );
  }

  @Test
  public void pickerLockCallbacks_updateLockAndTabImmediately()
  {
    TestDrawer drawer = newTestDrawer();
    drawer.itemPickerLockChanged( 2, true, SymbolType.LINE );
    assertEquals( SymbolType.LINE, ItemDrawer.getToolbarRowLock( 2 ) );

    drawer.itemPickerTypeChanged( 2, SymbolType.AREA );
    assertEquals( SymbolType.AREA, ItemDrawer.getToolbarRowLock( 2 ) );

    drawer.itemPickerLockChanged( 2, false, SymbolType.AREA );
    assertTrue( ! ItemDrawer.isToolbarRowLocked( 2 ) );
  }

  @Test
  public void replacingDuplicateSymbol_swapsOnlyWithinSameRowAndType()
  {
    TestDrawer drawer = newTestDrawer();
    Symbol first = ItemDrawer.mToolbarLine[0][0];
    Symbol second = ItemDrawer.mToolbarLine[0][1];
    int secondIndex = BrushManager.getLineIndex( second );
    assertTrue( secondIndex >= 0 );

    drawer.activateSlot( 0, SymbolType.LINE, 0 );
    int replacedSlot = drawer.replaceSymbol( 0, SymbolType.LINE, secondIndex );

    assertEquals( 0, replacedSlot );
    assertSame( second, ItemDrawer.mToolbarLine[0][0] );
    assertSame( first, ItemDrawer.mToolbarLine[0][1] );
    assertEquals( first.getFullThName(), ItemDrawer.mToolbarLine[1][0].getFullThName() );
    assertEquals( second.getFullThName(), ItemDrawer.mToolbarLine[1][1].getFullThName() );
  }

  @Test
  public void legacyRecentModes_keepSixSlotSingleRowBehavior()
  {
    TDSetting.mToolbarUpdate = TDSetting.TOOLBAR_UPDATE_OLDEST;
    TDSetting.mToolbarRows = 8;
    TDSetting.mToolbarSlots = 16;

    assertEquals( 1, ItemDrawer.getToolbarRowCount() );
    assertEquals( ItemDrawer.NR_LEGACY_RECENT, ItemDrawer.getToolbarSlotCount() );
  }

  private TestDrawer newTestDrawer()
  {
    final TestDrawer[] drawer = new TestDrawer[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync( new Runnable() {
      @Override
      public void run()
      {
        drawer[0] = new TestDrawer();
      }
    } );
    return drawer[0];
  }

  private static class TestDrawer extends ItemDrawer
  {
    void activateSlot( int row, int type, int slot )
    {
      setActiveToolbarSlot( row, type, slot );
    }

    int replaceSymbol( int row, int type, int index )
    {
      return replaceManualToolbarSymbol( row, type, index );
    }
  }
}
