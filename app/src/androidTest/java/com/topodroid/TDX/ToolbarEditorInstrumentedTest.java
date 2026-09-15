package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class ToolbarEditorInstrumentedTest
{
  @Before
  public void setUp()
  {
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TDInstance.setContext( context.getApplicationContext() );
    if ( TopoDroidApp.mData == null ) TopoDroidApp.mData = new DataHelper( context );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    BrushManager.reloadAreaLibrary( context.getResources() );
  }

  @Test
  public void editorRendersCategoryBrowserRowsAndPinnedQuickZone()
  {
    assertEquals( 101, ToolsetCatalog.all().size() );
    try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
      scenario.onActivity( activity -> {
        ArrayList< String > labels = new ArrayList<>();
        ArrayList< EditText > searches = new ArrayList<>();
        collectText( activity.getWindow().getDecorView(), labels, searches );

        assertTrue( ( activity.getWindow().getDecorView().getSystemUiVisibility()
          & View.SYSTEM_UI_FLAG_FULLSCREEN ) != 0 );
        assertTrue( labels.contains( "Toolbars" ) );
        assertTrue( labels.contains( "Passages" ) );
        assertNotNull( findTextViewContaining( activity.getWindow().getDecorView(), "Other" ) );
        assertTrue( labels.contains( "Toolbar rows" ) );
        assertTrue( labels.contains( "ENABLED · DRAG ≡ TO REORDER · TAP SLOT TO SELECT · TAP OR DRAG SYMBOLS" ) );
        assertTrue( labels.contains( "DISABLED · TAP LETTER TO ENABLE/DISABLE ROW" ) );
        assertTrue( labels.contains( "QUICK SWITCHER" ) );
        assertTrue( labels.contains( "Q" ) );
        assertTrue( labels.contains( "✕" ) );
        int ink = activity.getResources().getColor( R.color.toolbar_ink );
        assertEquals( ink, findTextView( activity.getWindow().getDecorView(), "ENABLED · DRAG ≡ TO REORDER · TAP SLOT TO SELECT · TAP OR DRAG SYMBOLS" ).getCurrentTextColor() );
        assertEquals( ink, findTextView( activity.getWindow().getDecorView(), "DISABLED · TAP LETTER TO ENABLE/DISABLE ROW" ).getCurrentTextColor() );
        assertEquals( 1, searches.size() );
        assertEquals( "Search 101 symbols", searches.get( 0 ).getHint().toString() );
        assertConsistentGridSpacing( activity );
      } );
      writeScreenshotArtifact();
    }
  }

  @Test
  public void rowHandleStraightDownDragReordersCanvasRows()
  {
    DataHelper data = TopoDroidApp.mData;
    ToolsetProfile savedDefault = ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID );
    String savedSelection = ToolsetRepository.activeProfileId( data, TDInstance.sid );
    try {
      assertTrue( ToolsetRepository.saveProfile( data, ToolsetProfile.freshDefault() ) );
      assertTrue( ToolsetRepository.selectProfile( data, TDInstance.sid, ToolsetProfile.DEFAULT_ID ) );
      try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
        UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
        UiObject2 rowA = device.wait( Until.findObject( By.desc( "Drag row A" ) ), 3000 );
        UiObject2 rowB = device.wait( Until.findObject( By.desc( "Drag row B" ) ), 3000 );
        assertNotNull( rowA );
        assertNotNull( rowB );
        Rect aBounds = rowA.getVisibleBounds();
        Rect bBounds = rowB.getVisibleBounds();
        Point source = new Point( aBounds.centerX(), aBounds.centerY() );
        Point target = new Point( aBounds.centerX(), bBounds.centerY() );
        assertTrue( device.drag( source.x, source.y, target.x, target.y, 80 ) );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep( 400 );
        assertEquals( Integer.valueOf( 1 ), ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID ).onCanvasRows().get( 0 ) );
      }
    } finally {
      ToolsetRepository.saveProfile( data, savedDefault );
      ToolsetRepository.selectProfile( data, TDInstance.sid, savedSelection );
    }
  }

  @Test
  public void draggingFilledToolbarSlotSwapsSymbols()
  {
    DataHelper data = TopoDroidApp.mData;
    ToolsetProfile savedDefault = ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID );
    String savedSelection = ToolsetRepository.activeProfileId( data, TDInstance.sid );
    try {
      assertTrue( ToolsetRepository.saveProfile( data, ToolsetProfile.freshDefault() ) );
      assertTrue( ToolsetRepository.selectProfile( data, TDInstance.sid, ToolsetProfile.DEFAULT_ID ) );
      try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
        UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
        UiObject2 wall = device.wait( Until.findObject( By.desc( "wall, slot 1" ) ), 3000 );
        UiObject2 pit = device.wait( Until.findObject( By.desc( "pit, slot 3" ) ), 3000 );
        assertNotNull( wall );
        assertNotNull( pit );
        Rect wallBounds = wall.getVisibleBounds();
        Rect pitBounds = pit.getVisibleBounds();
        Point source = new Point( wallBounds.centerX(), wallBounds.centerY() );
        Point target = new Point( pitBounds.centerX(), pitBounds.centerY() );
        longPressDrag( source, target );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep( 400 );
        ToolsetProfile stored = ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID );
        assertEquals( SymbolLibrary.PIT, stored.mRows[0][0].mFullThName );
        assertEquals( SymbolLibrary.WALL, stored.mRows[0][2].mFullThName );
      }
    } finally {
      ToolsetRepository.saveProfile( data, savedDefault );
      ToolsetRepository.selectProfile( data, TDInstance.sid, savedSelection );
    }
  }

  @Test
  public void slotSelectionAndPlacementUpdateExistingViewsInPlace()
  {
    DataHelper data = TopoDroidApp.mData;
    ToolsetProfile savedDefault = ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID );
    String savedSelection = ToolsetRepository.activeProfileId( data, TDInstance.sid );
    AtomicReference< View > slotReference = new AtomicReference<>();
    AtomicReference< View > rowHandleReference = new AtomicReference<>();
    try {
      assertTrue( ToolsetRepository.saveProfile( data, ToolsetProfile.freshDefault() ) );
      assertTrue( ToolsetRepository.selectProfile( data, TDInstance.sid, ToolsetProfile.DEFAULT_ID ) );
      try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
        scenario.onActivity( activity -> {
          View root = activity.getWindow().getDecorView();
          View slot = findByDescription( root, "wall, slot 1" );
          View handle = findByDescription( root, "Drag row A" );
          assertNotNull( slot );
          assertNotNull( handle );
          slotReference.set( slot );
          rowHandleReference.set( handle );
        } );
        UiDevice device = UiDevice.getInstance( InstrumentationRegistry.getInstrumentation() );
        UiObject2 slot = device.wait( Until.findObject( By.desc( "wall, slot 1" ) ), 3000 );
        UiObject2 symbol = device.wait( Until.findObject( By.desc( "ceiling channel, ln" ) ), 3000 );
        assertNotNull( slot );
        assertNotNull( symbol );
        slot.click();
        symbol.click();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity( activity -> {
          View root = activity.getWindow().getDecorView();
          assertSame( slotReference.get(), findByDescription( root, "ceiling channel, slot 1" ) );
          assertSame( rowHandleReference.get(), findByDescription( root, "Drag row A" ) );
        } );
        SystemClock.sleep( 250 );
        assertEquals( "ceiling-meander", ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID ).mRows[0][0].mFullThName );
      }
    } finally {
      ToolsetRepository.saveProfile( data, savedDefault );
      ToolsetRepository.selectProfile( data, TDInstance.sid, savedSelection );
    }
  }

  @Test
  public void quickSwitcherCanBePlacedInARegularToolbarSlot()
  {
    DataHelper data = TopoDroidApp.mData;
    ToolsetProfile savedDefault = ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID );
    String savedSelection = ToolsetRepository.activeProfileId( data, TDInstance.sid );
    try {
      assertTrue( ToolsetRepository.saveProfile( data, ToolsetProfile.freshDefault() ) );
      assertTrue( ToolsetRepository.selectProfile( data, TDInstance.sid, ToolsetProfile.DEFAULT_ID ) );
      try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
        scenario.onActivity( activity -> {
          ArrayList< String > labels = new ArrayList<>();
          ArrayList< EditText > searches = new ArrayList<>();
          collectText( activity.getWindow().getDecorView(), labels, searches );
          assertEquals( 1, searches.size() );
          searches.get( 0 ).setText( "quick switcher" );
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity( activity -> {
          View root = activity.getWindow().getDecorView();
          View slot = findByDescription( root, "wall, slot 1" );
          View quickSwitcher = findByDescription( root, "Quick Switcher, tool" );
          assertNotNull( slot );
          assertNotNull( quickSwitcher );
          assertTrue( slot.performClick() );
          assertTrue( quickSwitcher.performClick() );
        } );
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        scenario.onActivity( activity -> assertNotNull( findByDescription( activity.getWindow().getDecorView(), "Quick Switcher, slot 1" ) ) );
        SystemClock.sleep( 250 );
        assertTrue( ToolsetProfile.isQuickSwitcher( ToolsetRepository.profile( data, ToolsetProfile.DEFAULT_ID ).mRows[0][0] ) );
      }
    } finally {
      ToolsetRepository.saveProfile( data, savedDefault );
      ToolsetRepository.selectProfile( data, TDInstance.sid, savedSelection );
    }
  }

  private static void longPressDrag( Point source, Point target )
  {
    UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    long downTime = SystemClock.uptimeMillis();
    sendPointer( automation, downTime, MotionEvent.ACTION_DOWN, source.x, source.y );
    SystemClock.sleep( ViewConfiguration.getLongPressTimeout() + 100 );
    final int steps = 16;
    for ( int step = 1; step <= steps; ++step ) {
      float fraction = step / (float)steps;
      float x = source.x + ( target.x - source.x ) * fraction;
      float y = source.y + ( target.y - source.y ) * fraction;
      sendPointer( automation, downTime, MotionEvent.ACTION_MOVE, x, y );
      SystemClock.sleep( 16 );
    }
    sendPointer( automation, downTime, MotionEvent.ACTION_UP, target.x, target.y );
  }

  private static void sendPointer( UiAutomation automation, long downTime, int action, float x, float y )
  {
    MotionEvent event = MotionEvent.obtain( downTime, SystemClock.uptimeMillis(), action, x, y, 0 );
    assertTrue( automation.injectInputEvent( event, true ) );
    event.recycle();
  }

  private static void writeScreenshotArtifact()
  {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    SystemClock.sleep( 750 );
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File directory = context.getExternalFilesDir( "test-artifacts" );
    assertTrue( directory != null && ( directory.isDirectory() || directory.mkdirs() ) );
    File target = new File( directory, "toolbar-editor.png" );
    Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    assertTrue( screenshot != null );
    try ( FileOutputStream output = new FileOutputStream( target ) ) {
      assertTrue( screenshot.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    } catch ( Exception e ) {
      throw new AssertionError( "Unable to write toolbar editor screenshot", e );
    } finally {
      screenshot.recycle();
    }
  }

  private static void assertConsistentGridSpacing( ToolbarEditorActivity activity )
  {
    View root = activity.getWindow().getDecorView();
    int gap = activity.getResources().getDimensionPixelSize( R.dimen.toolbar_grid_gap );

    TextView all = findTextView( root, "ALL" );
    TextView point = findTextView( root, "PT" );
    assertNotNull( all );
    assertNotNull( point );
    assertEquals( "Type-filter grid gap", gap, point.getLeft() - all.getRight() );

    TextView passages = findTextViewContaining( root, "Passages" );
    TextView speleothems = findTextViewContaining( root, "Speleothems" );
    assertNotNull( passages );
    assertNotNull( speleothems );
    assertEquals( "Category grid gap", gap, speleothems.getLeft() - passages.getRight() );

    View browserSymbol = findByDescription( root, "wall, ln" );
    assertNotNull( browserSymbol );
    assertTrue( browserSymbol.getParent() instanceof ViewGroup );
    ViewGroup browserRow = (ViewGroup)browserSymbol.getParent();
    assertTrue( "Browser row needs peer symbols", browserRow.getChildCount() > 1 );
    assertEquals( "Browser-symbol horizontal grid gap", gap,
      browserRow.getChildAt( 1 ).getLeft() - browserRow.getChildAt( 0 ).getRight() );
    assertEquals( "Browser-symbol vertical grid gap", gap, browserRow.getPaddingTop() );

    View toolbarSlot = findByDescription( root, "wall, slot 1" );
    assertNotNull( toolbarSlot );
    assertTrue( toolbarSlot.getParent() instanceof ViewGroup );
    ViewGroup toolbarSlots = (ViewGroup)toolbarSlot.getParent();
    assertTrue( "Toolbar row needs peer slots", toolbarSlots.getChildCount() > 1 );
    assertEquals( "Toolbar-slot horizontal grid gap", gap,
      toolbarSlots.getChildAt( 1 ).getLeft() - toolbarSlots.getChildAt( 0 ).getRight() );
    assertTrue( toolbarSlots.getParent() instanceof View );
    View toolbarScroller = (View)toolbarSlots.getParent();
    assertTrue( toolbarScroller.getParent() instanceof ViewGroup );
    ViewGroup toolbarRow = (ViewGroup)toolbarScroller.getParent();
    assertEquals( "Toolbar-row vertical grid gap", gap, toolbarRow.getPaddingTop() );
  }

  private static void collectText( View view, ArrayList< String > labels, ArrayList< EditText > searches )
  {
    if ( view instanceof TextView ) {
      CharSequence text = ( (TextView)view ).getText();
      if ( text != null && text.length() > 0 ) labels.add( text.toString() );
      if ( view instanceof EditText ) searches.add( (EditText)view );
    }
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) collectText( group.getChildAt( index ), labels, searches );
    }
  }

  private static View findByDescription( View view, String description )
  {
    CharSequence current = view.getContentDescription();
    if ( current != null && description.contentEquals( current ) ) return view;
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) {
        View found = findByDescription( group.getChildAt( index ), description );
        if ( found != null ) return found;
      }
    }
    return null;
  }

  private static TextView findTextView( View view, String text )
  {
    if ( view instanceof TextView && text.contentEquals( ( (TextView)view ).getText() ) ) return (TextView)view;
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) {
        TextView found = findTextView( group.getChildAt( index ), text );
        if ( found != null ) return found;
      }
    }
    return null;
  }

  private static TextView findTextViewContaining( View view, String text )
  {
    if ( view instanceof TextView && ( (TextView)view ).getText().toString().contains( text ) ) return (TextView)view;
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) {
        TextView found = findTextViewContaining( group.getChildAt( index ), text );
        if ( found != null ) return found;
      }
    }
    return null;
  }
}
