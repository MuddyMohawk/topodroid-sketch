package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.RectF;

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
  private static final String[] DEFAULT_LINE_NAMES_RIGHT_TO_LEFT = {
    "wall", "pit", "chimney", "flowstone", "dashed", "dotted", "section", "user",
    "wall", "pit", "chimney", "flowstone", "dashed", "dotted", "section", "user"
  };

  private static final String[] DEFAULT_POINT_NAMES_RIGHT_TO_LEFT = {
    "blocks", "boulder", "stalagmite", "stalactite", "bedrock", "slope", "clay", "sand",
    "blocks", "boulder", "stalagmite", "stalactite", "bedrock", "slope", "clay", "sand"
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
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );

    TDSetting.mToolbarUpdate = TDSetting.TOOLBAR_UPDATE_MANUAL;
    TDSetting.mToolbarSlots = 8;
    TDSetting.mToolbarRows = 2;
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
  public void freshManualToolbar_usesDefaultLineAndPointRows()
  {
    assertDefaultToolbarSeed();
  }

  @Test
  public void defaultSymbolInstall_includesCurrentSketchPackSymbols()
  {
    int pit = BrushManager.getLineIndexByThName( SymbolLibrary.PIT );
    int flowstone = BrushManager.getLineIndexByThName( SymbolLibrary.FLOWSTONE );
    int wall = BrushManager.getLineIndexByThName( SymbolLibrary.WALL );
    int clay = BrushManager.getPointIndexByThName( SymbolLibrary.CLAY );
    int bedrock = BrushManager.getPointIndexByThName( SymbolLibrary.BEDROCK );
    int boulder = BrushManager.getPointIndexByThName( "boulder" );
    int waterFlow = BrushManager.getLineIndexByThName( SymbolLibrary.WATER_FLOW );

    assertTrue( "Missing wall line", wall >= 0 );
    assertTrue( "Missing sketch pit line", pit >= 0 );
    assertTrue( "Missing sketch flowstone line", flowstone >= 0 );
    assertTrue( "Missing sketch clay point", clay >= 0 );
    assertTrue( "Missing sketch bedrock point", bedrock >= 0 );
    assertTrue( "Missing sketch boulder point", boulder >= 0 );
    assertTrue( "Missing sketch water-flow line", waterFlow >= 0 );
    for ( int k = 0; k < BrushManager.getLineLibSize(); ++k ) {
      assertTrue( "Line should be enabled by default: " + BrushManager.getLineThName( k ),
                  BrushManager.getLineByIndex( k ).isEnabled() );
    }
    assertEquals( "pit", BrushManager.getLineName( pit ) );
    assertEquals( "flowstone", BrushManager.getLineName( flowstone ) );
    assertEquals( "clay", BrushManager.getPointName( clay ) );
    assertEquals( "bedrock", BrushManager.getPointName( bedrock ) );
    assertEquals( 0xffffffff, BrushManager.getLineColor( wall ) );

    Symbol flowstoneSymbol = BrushManager.getLineByThName( SymbolLibrary.FLOWSTONE );
    RectF previewBounds = new RectF();
    RectF scaledPreviewBounds = new RectF();
    flowstoneSymbol.getPath().computeBounds( previewBounds, true );
    flowstoneSymbol.getScaledPath().computeBounds( scaledPreviewBounds, true );
    assertTrue( "Flowstone preview should use visible stroked sketch arcs", previewBounds.height() > 1.0f );
    assertTrue( "Flowstone scaled preview should be substantially larger than the raw preview",
                scaledPreviewBounds.height() > previewBounds.height() * 1.5f );
    assertTrue( "Flowstone scaled preview should keep the pattern proportions",
                scaledPreviewBounds.width() > previewBounds.width() * 1.5f );
    assertEquals( "Flowstone scaled preview should stay vertically centered",
                  previewBounds.centerY(), scaledPreviewBounds.centerY(), 0.001f );
    assertTrue( "Flowstone preview should not use the legacy path effect",
                flowstoneSymbol.getPreviewPaint().getPathEffect() == null );
    assertEquals( "Flowstone preview should remain a stroked sketch stamp",
                  Paint.Style.STROKE, flowstoneSymbol.getPreviewPaint().getStyle() );

    Symbol ceilingChannelSymbol = BrushManager.getLineByThName( SymbolLibrary.CEILING_MEANDER );
    RectF ceilingChannelBounds = new RectF();
    ceilingChannelSymbol.getPath().computeBounds( ceilingChannelBounds, true );
    assertTrue( "Ceiling channel preview should use visible carrier and hachure geometry",
                ceilingChannelBounds.height() > 1.0f );
    assertTrue( "Ceiling channel preview should not use the legacy path effect",
                ceilingChannelSymbol.getPreviewPaint().getPathEffect() == null );
    assertEquals( "Ceiling channel preview should remain filled sketch geometry",
                  Paint.Style.FILL, ceilingChannelSymbol.getPreviewPaint().getStyle() );

    Symbol dashedSymbol = BrushManager.getLineByThName( "dashed" );
    Symbol dottedSymbol = BrushManager.getLineByThName( "dotted" );
    Symbol userSymbol = BrushManager.getLineByThName( SymbolLibrary.USER );
    Symbol wallSymbol = BrushManager.getLineByThName( SymbolLibrary.WALL );
    Symbol sectionSymbol = BrushManager.getLineByThName( SymbolLibrary.SECTION );
    assertTrue( "Dashed preview stroke should be larger than sketch stroke",
                dashedSymbol.getPreviewPaint().getStrokeWidth() > dashedSymbol.getPaint().getStrokeWidth() );
    assertTrue( "Dotted preview stroke should be larger than sketch stroke",
                dottedSymbol.getPreviewPaint().getStrokeWidth() > dottedSymbol.getPaint().getStrokeWidth() );
    assertTrue( "User preview stroke should be larger than sketch stroke",
                userSymbol.getPreviewPaint().getStrokeWidth() > userSymbol.getPaint().getStrokeWidth() );
    assertTrue( "Wall preview stroke should be larger than sketch stroke",
                wallSymbol.getPreviewPaint().getStrokeWidth() > wallSymbol.getPaint().getStrokeWidth() );
    assertTrue( "Section preview stroke should be larger than sketch stroke",
                sectionSymbol.getPreviewPaint().getStrokeWidth() > sectionSymbol.getPaint().getStrokeWidth() );
    assertTrue( "Dashed preview should keep a dash effect",
                dashedSymbol.getPreviewPaint().getPathEffect() != null );
    assertTrue( "Dotted preview should keep a dash effect",
                dottedSymbol.getPreviewPaint().getPathEffect() != null );
  }

  @Test
  public void manualToolbarSeed_overwritesPriorSymbolsAndLocks()
  {
    ItemDrawer.mToolbarLine[0][0] = ItemDrawer.mToolbarLine[0][3];
    ItemDrawer.mToolbarPoint[1][0] = ItemDrawer.mToolbarPoint[1][3];
    ItemDrawer.setToolbarRowLock( 0, ItemDrawer.TOOLBAR_LOCK_UNLOCKED );
    ItemDrawer.setToolbarRowLock( 1, SymbolType.LINE );
    ItemDrawer.setToolbarCurrentType( SymbolType.LINE );

    ItemDrawer.loadManualToolbarSymbols( null );

    assertDefaultToolbarSeed();
  }

  private void assertDefaultToolbarSeed()
  {
    assertEquals( 2, ItemDrawer.getToolbarRowCount() );
    assertEquals( 8, ItemDrawer.getToolbarSlotCount() );
    assertEquals( SymbolType.LINE, ItemDrawer.getToolbarRowLock( 0 ) );
    assertTrue( ! ItemDrawer.isToolbarRowLocked( 1 ) );
    assertEquals( SymbolType.LINE, ItemDrawer.getToolbarDisplayType( 0 ) );
    assertEquals( SymbolType.POINT, ItemDrawer.getToolbarDisplayType( 1 ) );
    for ( int slot = 0; slot < DEFAULT_LINE_NAMES_RIGHT_TO_LEFT.length; ++slot ) {
      int storageSlot = ItemDrawer.NR_RECENT - slot - 1;
      assertEquals( DEFAULT_LINE_NAMES_RIGHT_TO_LEFT[slot], ItemDrawer.mToolbarLine[0][storageSlot].getThName() );
    }
    for ( int slot = 0; slot < ItemDrawer.getToolbarSlotCount(); ++slot ) {
      int storageSlot = ItemDrawer.getToolbarSlotCount() - slot - 1;
      assertEquals( DEFAULT_LINE_NAMES_RIGHT_TO_LEFT[slot], ItemDrawer.mToolbarLine[0][storageSlot].getThName() );
    }
    for ( int slot = 0; slot < DEFAULT_POINT_NAMES_RIGHT_TO_LEFT.length; ++slot ) {
      int storageSlot = ItemDrawer.NR_RECENT - slot - 1;
      assertEquals( DEFAULT_POINT_NAMES_RIGHT_TO_LEFT[slot], ItemDrawer.mToolbarPoint[1][storageSlot].getThName() );
    }
    for ( int slot = 0; slot < ItemDrawer.getToolbarSlotCount(); ++slot ) {
      int storageSlot = ItemDrawer.getToolbarSlotCount() - slot - 1;
      assertEquals( DEFAULT_POINT_NAMES_RIGHT_TO_LEFT[slot], ItemDrawer.mToolbarPoint[1][storageSlot].getThName() );
    }
  }

  @Test
  public void manualRows_copyRowZeroDefaultsAndRemainAvailableWhenHidden()
  {
    TDSetting.mToolbarRows = 3;
    assertEquals( 3, ItemDrawer.getToolbarRowCount() );
    for ( int slot = 0; slot < ItemDrawer.getToolbarSlotCount(); ++slot ) {
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
  public void manualRows_displayAboveRowZeroAsTheyAreAdded()
  {
    TDSetting.mToolbarRows = 3;

    assertEquals( 2, ItemDrawer.getToolbarRowForViewIndex( 0 ) );
    assertEquals( 1, ItemDrawer.getToolbarRowForViewIndex( 1 ) );
    assertEquals( 0, ItemDrawer.getToolbarRowForViewIndex( 2 ) );
    assertEquals( 2, ItemDrawer.getToolbarViewIndexForRow( 0 ) );
    assertEquals( 1, ItemDrawer.getToolbarViewIndexForRow( 1 ) );
    assertEquals( 0, ItemDrawer.getToolbarViewIndexForRow( 2 ) );
  }

  @Test
  public void rowLock_controlsDisplayedTypeOnlyForThatRow()
  {
    TDSetting.mToolbarRows = 3;
    ItemDrawer.setToolbarRowLock( 0, ItemDrawer.TOOLBAR_LOCK_UNLOCKED );
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
  public void activeToolbarSelection_tracksRowTypeAndSlotWithNormalizedReloadValues()
  {
    TestDrawer drawer = newTestDrawer();

    drawer.activateSlot( 1, SymbolType.POINT, 2 );

    assertEquals( 1, ItemDrawer.getToolbarActiveRow() );
    assertEquals( SymbolType.POINT, ItemDrawer.getToolbarActiveType() );
    assertEquals( 2, ItemDrawer.mToolbarActiveSlot[1] );
    assertEquals( 2, ItemDrawer.rowFromString( "2" ) );
    assertEquals( -1, ItemDrawer.rowFromString( "-1" ) );
    assertEquals( -1, ItemDrawer.rowFromString( "999" ) );
    assertEquals( SymbolType.LINE, ItemDrawer.typeFromString( "line", SymbolType.UNDEF ) );
    assertEquals( SymbolType.POINT, ItemDrawer.typeFromString( "bogus", SymbolType.POINT ) );
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
