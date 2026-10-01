package com.topodroid.TDX;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.num.TDNum;
import com.topodroid.types.BlockType;
import com.topodroid.types.PlotType;
import com.topodroid.types.PointScale;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith( AndroidJUnit4.class )
public class SectionRenderingInstrumentedTest
{
  private DBlock block( long id, String from, String to, float length, float bearing )
  {
    DBlock block = new DBlock( from, to, length, bearing, 0, 0, 1,
      to.isEmpty() ? BlockType.SPLAY : BlockType.MAIN_LEG, 0 );
    block.mId = id; return block;
  }

  private void references( DrawingCommandManager manager, List<DrawingPath> paths )
  {
    manager.newReferences();
    for ( DrawingPath path : paths ) {
      if ( path instanceof DrawingSplayPath ) manager.addTmpSplayPath( (DrawingSplayPath)path, false );
      else if ( path instanceof DrawingStationName ) manager.addTmpStation( (DrawingStationName)path, false );
      else manager.addTmpLegPath( path, false );
    }
    manager.commitReferences();
  }

  private int ink( Bitmap bitmap )
  {
    assertNotNull( bitmap ); int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      if ( ( bitmap.getPixel( x, y ) >>> 24 ) != 0 ) ++count;
    }
    bitmap.recycle(); return count;
  }

  private int liveSplayInk( DrawingCommandManager manager, DrawingStationSplay filters )
  {
    Bitmap bitmap = Bitmap.createBitmap( 240, 240, Bitmap.Config.ARGB_8888 );
    manager.executeAll( new Canvas( bitmap ), 1, filters, false, new Matrix(), 1, new RectF( 0, 0, 240, 240 ), false );
    return ink( bitmap );
  }

  @Test public void liveSectionAndPngUseSectionChoicesInsteadOfParentSplayVisibility() throws Exception
  {
    boolean exitPolicy = TopoDroidApp.sayDialogR();
    int previousDisplayMode = DrawingCommandManager.getDisplayMode();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> TopoDroidApp.setSayDialogR( false ) );
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try {
      InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
        try {
          DrawingWindow window = new DrawingWindow();
          Field data = DrawingWindow.class.getDeclaredField( "mApp_mData" ); data.setAccessible( true ); data.set( window, TopoDroidApp.mData );
          Method factory = DrawingWindow.class.getDeclaredMethod( "makeSectionReferencePaths", PlotInfo.class, SectionReferenceBuilder.class,
            int.class, float.class, float.class ); factory.setAccessible( true );
          List<DBlock> blocks = Arrays.asList( block( 1, "A", "B", 10, 0 ), block( 2, "A", "", 3, 90 ) );
          PlotInfo plot = new PlotInfo(); plot.type = (int)PlotType.PLOT_X_SECTION; plot.start = "A"; plot.view = "";
          SectionReferenceSelection choices = SectionReferenceRepository.defaults( plot, blocks );
          TDNum num = new TDNum( blocks, "A", 0, null );
          DrawingCommandManager editor = new DrawingCommandManager( DrawingSurface.DRAWING_SECTION, "live-splays" );
          DrawingStationSplay parentFilters = new DrawingStationSplay();
          // Disable every other reference so visible pixels can only come from splays.
          // Both opening a section and its References dialog omit DISPLAY_SPLAY.
          DrawingCommandManager.setDisplayMode( DisplayMode.DISPLAY_NONE );
          SketchPngExportOptions options = new SketchPngExportOptions( false, false, true, false, false, false, true, false, 0.05f, "test" );
          for ( int step = 0; step < 4; ++step ) {
            if ( step == 1 ) choices.setSplays( "A", false );
            if ( step == 2 ) { choices.setSplays( "A", true ); parentFilters.hideStationSplays( "A" ); }
            if ( step == 3 ) choices.setStation( "A", false );
            SectionReferenceBuilder geometry = new SectionReferenceBuilder( plot, blocks, num, choices, 60, 70 );
            @SuppressWarnings("unchecked") List<DrawingPath> paths = (List<DrawingPath>)factory.invoke( window, plot, geometry, 0, 0f, 0f );
            references( editor, paths );
            if ( step == 0 || step == 2 ) {
              assertEquals( 1, editor.getSplays().size() );
              assertTrue( "Checked splays missing from live section at step " + step, liveSplayInk( editor, parentFilters ) > 0 );
              assertTrue( "Checked splays missing from PNG at step " + step, ink( editor.renderExportBitmap( parentFilters, options ) ) > 0 );
            } else {
              assertEquals( 0, editor.getSplays().size() );
              assertEquals( 0, liveSplayInk( editor, parentFilters ) );
              assertEquals( 0, ink( editor.renderExportBitmap( parentFilters, options ) ) );
            }
          }
          choices.setStation( "A", true );
          @SuppressWarnings("unchecked") List<DrawingPath> paths = (List<DrawingPath>)factory.invoke( window, plot,
            new SectionReferenceBuilder( plot, blocks, num, choices, 60, 70 ), 0, 0f, 0f );
          DrawingCommandManager plan = new DrawingCommandManager( DrawingSurface.DRAWING_PLAN, "parent-splays" ); references( plan, paths );
          DrawingCommandManager.setDisplayMode( DisplayMode.DISPLAY_SPLAY );
          assertEquals( 0, liveSplayInk( plan, parentFilters ) );
          assertEquals( 0, ink( plan.renderExportBitmap( parentFilters, options ) ) );
          parentFilters.showStationSplays( "A" );
          assertTrue( liveSplayInk( plan, parentFilters ) > 0 );
          assertTrue( ink( plan.renderExportBitmap( parentFilters, options ) ) > 0 );
        } catch ( Throwable e ) { failure.set( e ); }
      } );
      if ( failure.get() != null ) throw new AssertionError( failure.get() );
    } finally {
      DrawingCommandManager.setDisplayMode( previousDisplayMode );
      TopoDroidApp.setSayDialogR( exitPolicy );
    }
  }

  @Test public void sharedReferencesMatchPlacedCoordinatesAndPngRespectsEmptySelection() throws Exception
  {
    boolean exitPolicy = TopoDroidApp.sayDialogR();
    try ( ActivityScenario<MainWindow> scenario = ActivityScenario.launch( MainWindow.class ) ) {
      scenario.onActivity( activity -> TopoDroidApp.setSayDialogR( false ) );
    }
    AtomicReference<Throwable> failure = new AtomicReference<>();
    try {
      InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
        try {
          DrawingWindow window = new DrawingWindow();
          Field data = DrawingWindow.class.getDeclaredField( "mApp_mData" ); data.setAccessible( true ); data.set( window, TopoDroidApp.mData );
          Method factory = DrawingWindow.class.getDeclaredMethod( "makeSectionReferencePaths", PlotInfo.class, SectionReferenceBuilder.class,
            int.class, float.class, float.class ); factory.setAccessible( true );
          List<DBlock> blocks = Arrays.asList( block( 1, "A", "B", 10, 0 ), block( 2, "A", "C", 2, 90 ),
            block( 3, "C", "D", 10, 0 ), block( 4, "A", "", 3, 90 ), block( 5, "C", "", 3, 90 ) );
          PlotInfo plot = new PlotInfo(); plot.type = (int)PlotType.PLOT_SECTION; plot.start = "A"; plot.view = "B";
          plot.azimuth = 45; plot.intercept = 0.5f;
          TDNum num = new TDNum( blocks, "A", 0, null );
          SectionReferenceSelection choices = new SectionReferenceSelection(); choices.setStation( "A", true ); choices.setStation( "C", true );
          choices.setSplays( "C", false );
          SectionReferenceBuilder geometry = new SectionReferenceBuilder( plot, blocks, num, choices, 60, 70 );
          @SuppressWarnings("unchecked") List<DrawingPath> open = (List<DrawingPath>)factory.invoke( window, plot, geometry, 0, 0f, 0f );
          @SuppressWarnings("unchecked") List<DrawingPath> placed = (List<DrawingPath>)factory.invoke( window, plot, geometry, 0, 123f, -45f );
          assertEquals( open.size(), placed.size() );
          for ( int i = 0; i < open.size(); ++i ) {
            DrawingPath first = open.get( i ), second = placed.get( i );
            assertEquals( first.getClass(), second.getClass() );
            assertEquals( first.cx + 123, second.cx, 0.001f ); assertEquals( first.cy - 45, second.cy, 0.001f );
            if ( first.mBlock != null ) assertEquals( first.mBlock.mId, second.mBlock.mId );
            if ( first instanceof DrawingStationName ) assertEquals( ((DrawingStationName)first).getName(), ((DrawingStationName)second).getName() );
          }
          DrawingCommandManager editor = new DrawingCommandManager( DrawingSurface.DRAWING_SECTION, "test-section" );
          references( editor, open );
          assertEquals( 2, editor.getStations().size() ); assertEquals( 1, editor.getSplays().size() );
          assertTrue( editor.getCommands().isEmpty() ); // Intersection dots belong to references, not saved sketch commands.
          SketchPngExportOptions options = new SketchPngExportOptions( true, true, true, false, false, false, true, false, 0.05f, "test" );
          assertTrue( ink( editor.renderExportBitmap( null, options ) ) > 0 );
          DrawingCommandManager parent = new DrawingCommandManager( DrawingSurface.DRAWING_PLAN, "test-plan" );
          DrawingPointPath point = new DrawingPointPath( 0, 200, 200, PointScale.SCALE_M, 0 );
          RectF box = new RectF( -500, -500, 1000, 1000 );
          parent.addXSectionOutlinePath( new DrawingOutlinePath( "test-section", point, box, null, new ArrayList<>(), placed, 0 ) );
          assertTrue( ink( parent.renderExportBitmap( null, options ) ) > 0 );
          choices.setStation( "A", false ); choices.setStation( "C", false );
          SectionReferenceBuilder emptyGeometry = new SectionReferenceBuilder( plot, blocks, num, choices, 60, 70 );
          @SuppressWarnings("unchecked") List<DrawingPath> empty = (List<DrawingPath>)factory.invoke( window, plot, emptyGeometry, 0, 0f, 0f );
          references( editor, empty );
          assertTrue( editor.getCommands().isEmpty() ); assertEquals( 0, ink( editor.renderExportBitmap( null, options ) ) );
          DrawingCommandManager emptyParent = new DrawingCommandManager( DrawingSurface.DRAWING_PLAN, "test-empty-plan" );
          emptyParent.addXSectionOutlinePath( new DrawingOutlinePath( "test-section", point, box, null, new ArrayList<>(), empty, 0 ) );
          assertEquals( 0, ink( emptyParent.renderExportBitmap( null, options ) ) );
        } catch ( Throwable e ) { failure.set( e ); }
      } );
      if ( failure.get() != null ) throw new AssertionError( failure.get() );
    } finally { TopoDroidApp.setSayDialogR( exitPolicy ); }
  }
}
