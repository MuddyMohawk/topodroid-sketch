package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
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
    assertEquals( 100, ToolsetCatalog.all().size() );
    try ( ActivityScenario< ToolbarEditorActivity > scenario = ActivityScenario.launch( ToolbarEditorActivity.class ) ) {
      scenario.onActivity( activity -> {
        ArrayList< String > labels = new ArrayList<>();
        ArrayList< EditText > searches = new ArrayList<>();
        collectText( activity.getWindow().getDecorView(), labels, searches );

        assertTrue( labels.contains( "Toolbars" ) );
        assertTrue( labels.contains( "Passages" ) );
        assertTrue( labels.contains( "Toolbar rows" ) );
        assertTrue( labels.contains( "ON CANVAS" ) );
        assertTrue( labels.contains( "CONFIGURED · NOT ON CANVAS" ) );
        assertTrue( labels.contains( "QUICK SWITCHER" ) );
        assertTrue( labels.contains( "Q" ) );
        assertEquals( 1, searches.size() );
        assertEquals( "Search 100 symbols", searches.get( 0 ).getHint().toString() );
      } );
      writeScreenshotArtifact();
    }
  }

  @Test
  public void rowHandleDragReordersCanvasRowsAcrossSlotTargets()
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
        Point source = new Point( bBounds.centerX(), bBounds.centerY() );
        Point target = new Point( aBounds.centerX() + 150, aBounds.centerY() );
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
}
