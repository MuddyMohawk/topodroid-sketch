package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.PointF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.ui.MotionEventWrap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises affine handles through DrawingWindow's real post-selection MODE_SHIFT path. */
@RunWith( AndroidJUnit4.class )
@LargeTest
public class BreakdownRockWindowInteractionInstrumentedTest
{
  private static final String SURVEY_NAME = "breakdown_gizmo_shift_case";
  private static final String PLOT_NAME = "1";

  private VisualTestSupport mSupport;

  @Before public void setUp()
  {
    mSupport = new VisualTestSupport( "breakdown_gizmo_shift" );
  }

  @After public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @Test public void placementSensitivityAndSelectedStateShearHandleWorkThroughDrawingWindow() throws Exception
  {
    mSupport.prepareForPhysicalCompatCase();
    mSupport.launchMainWindowOnAnyDevice();
    mSupport.deleteGeneratedSurveyAndArtifacts( SURVEY_NAME );
    mSupport.createSurveyAndOpenShots( SURVEY_NAME, "Breakdown Test Team", "1", "affine gizmo interaction" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();

    final Throwable[] error = new Throwable[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      try {
        DrawingWindow window = TopoDroidApp.mDrawingWindow;
        assertNotNull( "No active DrawingWindow", window );
        DrawingSurface surface = (DrawingSurface)getField( window, "mDrawingSurface" );
        assertNotNull( "DrawingWindow has no drawing surface", surface );
        DrawingCommandManager manager = surface.getManager( window.getPlotType() );
        assertNotNull( "DrawingWindow has no command manager", manager );

        int boulder = BrushManager.getPointIndexByThName( "boulder" );
        assertTrue( "Missing boulder symbol", boulder >= 0 );
        assertTrue( "Boulder symbol is not affine", BrushManager.isPointAffine( boulder ) );

        float zoom = getFloatField( window, "mZoom" );
        PointF offset = (PointF)getField( window, "mOffset" );
        boolean landscape = getBooleanField( window, "mLandscape" );
        assertNotNull( "DrawingWindow has no view offset", offset );
        assertTrue( "Drawing surface has not been laid out", surface.getWidth() > 0 && surface.getHeight() > 0 );

        float center_canvas_x = surface.getWidth() * 0.50f;
        float center_canvas_y = surface.getHeight() * 0.48f;
        float center_touch_x = center_canvas_x / zoom - offset.x;
        float center_touch_y = center_canvas_y / zoom - offset.y;
        float point_x = landscape ? -center_touch_y : center_touch_x;
        float point_y = landscape ? center_touch_x : center_touch_y;

        int before_placement_count = manager.getCommands().size();
        float placement_drag_px = 150.0f;
        float target_canvas_x = center_canvas_x + placement_drag_px;
        float target_canvas_y = center_canvas_y;
        float target_touch_x = target_canvas_x / zoom - offset.x;
        float target_touch_y = target_canvas_y / zoom - offset.y;

        window.pointSelected( boulder, true );
        invoke( window, "onTouchDown",
                new Class<?>[] { float.class, float.class, float.class, float.class },
                center_canvas_x, center_canvas_y, center_touch_x, center_touch_y );
        invoke( window, "onTouchMove",
                new Class<?>[] { float.class, float.class, float.class, float.class, MotionEventWrap.class },
                target_canvas_x, target_canvas_y, target_touch_x, target_touch_y, null );
        invoke( window, "onTouchUp",
                new Class<?>[] { float.class, float.class, float.class, float.class },
                target_canvas_x, target_canvas_y, target_touch_x, target_touch_y );

        assertEquals( "Initial placement should commit exactly one rock",
                      before_placement_count + 1, manager.getCommands().size() );
        DrawingPointPath point = latestPointOfType( manager, boulder );
        assertNotNull( "The placement drag did not create a boulder", point );
        assertNotNull( "The placed boulder has no affine transform", point.getSketchAffineTransform() );
        assertEquals( "Affine placement should use the more responsive drag scale",
                      SketchPointScale.scaleFromAffinePlacementDragDistance( placement_drag_px ),
                      point.getSketchAffineTransform().closestUniformScale(), 0.001f );
        assertEquals( "Placement should remain anchored at stylus-down", point_x, point.cx, 0.0001f );
        assertEquals( "Placement should remain anchored at stylus-down", point_y, point.cy, 0.0001f );
        int command_count = manager.getCommands().size();

        invoke( window, "setMode", new Class<?>[] { int.class }, DrawingWindow.MODE_EDIT );
        invoke( window, "doSelectAt",
                new Class<?>[] { float.class, float.class, float.class },
                center_touch_x, center_touch_y, 40.0f );

        assertEquals( "Selecting a point should enter the normal selected-object state",
                      DrawingWindow.MODE_SHIFT, getIntField( window, "mMode" ) );
        SelectionPoint selected = surface.hotItem();
        assertNotNull( "The placed breakdown rock was not selected", selected );
        assertTrue( "The selected item is not the placed breakdown rock", selected.mItem == point );

        SketchAffineTransform before = point.getSketchAffineTransform();
        assertNotNull( "The placed breakdown rock has no affine transform", before );
        PointF handle = SketchAffineGizmo.handlePoint( point, SketchAffineGizmo.SHEAR_X, zoom );
        float delta = 32.0f / Math.max( zoom, 0.0001f );
        float local_x_length = (float)Math.hypot( before.m00, before.m10 );
        PointF target = new PointF( handle.x + delta * before.m00 / local_x_length,
                                    handle.y + delta * before.m10 / local_x_length );
        PointF handle_touch = toTouchScene( handle, landscape );
        PointF target_touch = toTouchScene( target, landscape );
        PointF handle_canvas = toCanvas( handle_touch, offset, zoom );
        PointF target_canvas = toCanvas( target_touch, offset, zoom );

        invoke( window, "onTouchDown",
                new Class<?>[] { float.class, float.class, float.class, float.class },
                handle_canvas.x, handle_canvas.y, handle_touch.x, handle_touch.y );
        assertNotNull( "The visible shear handle did not start a gizmo drag in MODE_SHIFT",
                       getField( window, "mAffineGizmoDrag" ) );
        assertFalse( "A handle drag was misclassified as a canvas drag",
                     getBooleanField( window, "mShiftMove" ) );

        invoke( window, "onTouchMove",
                new Class<?>[] { float.class, float.class, float.class, float.class, MotionEventWrap.class },
                target_canvas.x, target_canvas.y, target_touch.x, target_touch.y, null );
        invoke( window, "onTouchUp",
                new Class<?>[] { float.class, float.class, float.class, float.class },
                target_canvas.x, target_canvas.y, target_touch.x, target_touch.y );

        SketchAffineTransform after = point.getSketchAffineTransform();
        assertNotNull( "Shear drag removed the affine transform", after );
        float matrix_change = Math.abs( after.m00 - before.m00 )
                            + Math.abs( after.m01 - before.m01 )
                            + Math.abs( after.m10 - before.m10 )
                            + Math.abs( after.m11 - before.m11 );
        assertTrue( "Dragging the shear handle did not change the oriented rock shape",
                    matrix_change > 0.01f );
        assertEquals( "A transform drag must not add an undo command", command_count, manager.getCommands().size() );
        assertEquals( "A handle drag must keep the rock selected", DrawingWindow.MODE_SHIFT,
                      getIntField( window, "mMode" ) );
        assertTrue( "A handle drag unexpectedly changed stack selection", surface.hotItem().mItem == point );
        assertEquals( "Shearing must not move the rock center", point_x, point.cx, 0.0001f );
        assertEquals( "Shearing must not move the rock center", point_y, point.cy, 0.0001f );
        assertTrue( "The gizmo drag was not cleared on stylus-up",
                    getField( window, "mAffineGizmoDrag" ) == null );
      } catch ( Throwable t ) {
        error[0] = t.getCause() != null ? t.getCause() : t;
      }
    } );
    if ( error[0] != null ) throw new AssertionError( "Breakdown gizmo interaction failed", error[0] );
  }

  private static PointF toTouchScene( PointF point, boolean landscape )
  {
    return landscape ? new PointF( point.y, -point.x ) : new PointF( point.x, point.y );
  }

  private static PointF toCanvas( PointF point, PointF offset, float zoom )
  {
    return new PointF( ( point.x + offset.x ) * zoom, ( point.y + offset.y ) * zoom );
  }

  private static DrawingPointPath latestPointOfType( DrawingCommandManager manager, int point_type )
  {
    DrawingPointPath latest = null;
    for ( DrawingPath path : manager.getCommands() ) {
      if ( path instanceof DrawingPointPath && ((DrawingPointPath)path).mPointType == point_type ) {
        latest = (DrawingPointPath)path;
      }
    }
    return latest;
  }

  private static Object getField( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.get( target );
  }

  private static float getFloatField( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.getFloat( target );
  }

  private static int getIntField( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.getInt( target );
  }

  private static boolean getBooleanField( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.getBoolean( target );
  }

  private static Object invoke( Object target, String name, Class<?>[] types, Object... arguments ) throws Exception
  {
    Method method = target.getClass().getDeclaredMethod( name, types );
    method.setAccessible( true );
    return method.invoke( target, arguments );
  }
}
