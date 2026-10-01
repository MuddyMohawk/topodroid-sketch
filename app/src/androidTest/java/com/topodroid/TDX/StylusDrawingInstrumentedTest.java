package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.graphics.Paint;
import android.view.MotionEvent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.types.SymbolType;
import com.topodroid.math.Point2D;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;

/** Exercise the real drawing listeners with in-memory surfaces; no survey is opened or saved. */
@RunWith( AndroidJUnit4.class )
public class StylusDrawingInstrumentedTest
{
  private static class BareDrawing extends DrawingWindow {
    @Override public void checkZoomBtnsCtrl() { }
    @Override public void setTheTitle() { }
  }
  private static class BareSketch extends SketchWindow {
    @Override public void checkZoomBtnsCtrl() { }
    @Override public void setTheTitle() { }
  }

  private static Field field( Object target, String name ) throws ReflectiveOperationException
  {
    for ( Class<?> type = target.getClass(); type != null; type = type.getSuperclass() ) {
      try { Field result = type.getDeclaredField( name ); result.setAccessible( true ); return result; }
      catch ( NoSuchFieldException ignored ) { }
    }
    throw new NoSuchFieldException( name );
  }
  private static void set( Object target, String name, Object value ) throws ReflectiveOperationException { field( target, name ).set( target, value ); }
  private static Object get( Object target, String name ) throws ReflectiveOperationException { return field( target, name ).get( target ); }

  private static void mixedStroke( StylusInputFilter filter, StylusInputFilter.Dispatch listener )
  {
    MotionEvent down = StylusInputFilterInstrumentedTest.event( MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS );
    filter.touch( down, true, event -> true ); down.recycle(); // Listener fixture starts with a pending stroke.
    MotionEvent join = StylusInputFilterInstrumentedTest.event( MotionEvent.ACTION_POINTER_DOWN | (1 << 8),
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    filter.touch( join, true, listener ); join.recycle();
    MotionEvent move = StylusInputFilterInstrumentedTest.event( MotionEvent.ACTION_MOVE,
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    move.offsetLocation( 1000, 2000 );
    filter.touch( move, true, listener ); move.recycle();
    MotionEvent leave = StylusInputFilterInstrumentedTest.event( MotionEvent.ACTION_POINTER_UP | (1 << 8),
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_FINGER );
    leave.offsetLocation( 1000, 2000 );
    filter.touch( leave, true, listener ); leave.recycle();
  }

  @Test public void drawingKeepsFastPenStrokeAndDiscardsCanceledPlacement()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      try {
        BareDrawing drawing = new BareDrawing();
        DrawingSurface surface = new DrawingSurface( InstrumentationRegistry.getInstrumentation().getTargetContext(), null );
        set( surface, "commandManager", new DrawingCommandManager( 0, "stylus-input-test" ) );
        surface.makePreviewPath( DrawingPath.DRAWING_PATH_LINE, new Paint() );
        DrawingLinePath path = new DrawingLinePath( 0, 0 ); path.addStartPoint( 40, 50 );
        set( drawing, "mDrawingSurface", surface ); set( drawing, "mCurrentLinePath", path );
        set( drawing, "mCurrentBrush", new DrawingPenBrush() );
        set( drawing, "mMode", DrawingWindow.MODE_DRAW ); set( drawing, "mSymbol", SymbolType.LINE );
        set( drawing, "mSaveX", 40f ); set( drawing, "mSaveY", 50f );
        StylusInputFilter filter = new StylusInputFilter();
        StylusInputFilter.Dispatch listener = event -> drawing.onTouch( surface, event );
        mixedStroke( filter, listener );
        assertEquals( 2, path.size() ); assertEquals( DrawingWindow.MODE_MOVE, get( drawing, "mTouchMode" ) );
        surface.setLiveItem( new DrawingPointPath( 0, 10, 20, 0, 0 ) );
        set( drawing, "mPointerDown", true );
        filter.cancel( listener );
        assertNull( get( drawing, "mCurrentLinePath" ) ); assertNull( get( surface, "mLiveItem" ) );
        assertFalse( (Boolean)get( drawing, "mPointerDown" ) );
        assertEquals( DrawingWindow.MODE_MOVE, get( drawing, "mTouchMode" ) );
      } catch ( ReflectiveOperationException e ) { throw new AssertionError( e ); }
    } );
  }

  @Test public void legacySketchKeepsFastPenStrokeAndClearsCanceledPreview()
  {
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      try {
        BareSketch sketch = new BareSketch();
        SketchSurface surface = new SketchSurface( InstrumentationRegistry.getInstrumentation().getTargetContext(), null );
        surface.setParent( sketch ); surface.startCurrentPath(); surface.addPointToCurrentPath( new Point2D( 40, 50 ) );
        set( sketch, "mSketchSurface", surface ); set( sketch, "mMode", SketchWindow.MODE_DRAW );
        set( sketch, "mSaveX", 40f ); set( sketch, "mSaveY", 50f );
        StylusInputFilter filter = new StylusInputFilter();
        StylusInputFilter.Dispatch listener = event -> sketch.onTouch( surface, event );
        mixedStroke( filter, listener );
        assertEquals( 2, surface.getCurrentPath().size() ); assertEquals( SketchWindow.MODE_MOVE, get( sketch, "mTouchMode" ) );
        set( sketch, "mPointerDown", true ); filter.cancel( listener );
        assertNull( surface.getCurrentPath() ); assertFalse( (Boolean)get( sketch, "mPointerDown" ) );
      } catch ( ReflectiveOperationException e ) { throw new AssertionError( e ); }
    } );
  }
}
