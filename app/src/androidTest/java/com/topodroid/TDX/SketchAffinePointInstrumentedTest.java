package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.types.PointScale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class SketchAffinePointInstrumentedTest
{
  private static final int TDR_VERSION_WITH_STYLE_OPTIONS = 602055;

  private Context mContext;
  private Context mPreviousContext;
  private int mBoulder;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    mBoulder = BrushManager.getPointIndexByThName( "boulder" );
    assertTrue( "Missing boulder point", mBoulder >= 0 );
  }

  @After public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test public void symbolCapabilities_createMetadataOnlyForNewPlacements()
  {
    assertTrue( BrushManager.isPointAffine( mBoulder ) );
    assertEquals( "breakdown", BrushManager.pointDefaultOccludeGroup( mBoulder ) );

    DrawingPointPath placed = new DrawingPointPath( mBoulder, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    assertTrue( placed.hasSketchAffineTransform() );
    assertTrue( placed.getOptions().contains( SketchPrivateOptions.OPTION_AFFINE ) );

    DrawingPointPath legacy = new DrawingPointPath( mBoulder, 10.0f, 20.0f, PointScale.SCALE_M,
                                                    null, "-foo bar", 0 );
    assertFalse( legacy.hasSketchAffineTransform() );
    assertEquals( "-foo bar", legacy.getOptions() );
  }

  @Test public void codec_rejectsInvalidAndFutureTokensWithoutRewritingThem()
  {
    assertNull( SketchAffineTransformCodec.fromOptions( affine( "v=1,m00=1,m01=0,m10=0,m11=0" ) ) );
    assertNull( SketchAffineTransformCodec.fromOptions( affine( "v=1,m00=-1,m01=0,m10=0,m11=1" ) ) );
    assertNull( SketchAffineTransformCodec.fromOptions( affine( "v=1,m00=NaN,m01=0,m10=0,m11=1" ) ) );
    assertNull( SketchAffineTransformCodec.fromOptions( affine( "v=1,m00=1,m00=2,m01=0,m10=0,m11=1" ) ) );
    String future = affine( "v=2,m00=1,m01=0,m10=0,m11=1" ) + " -foo bar";
    assertNull( SketchAffineTransformCodec.fromOptions( future ) );

    DrawingPointPath point = new DrawingPointPath( mBoulder, 0.0f, 0.0f, PointScale.SCALE_M,
                                                   null, future, 0 );
    assertFalse( point.hasSketchAffineTransform() );
    assertEquals( future, point.getOptions() );
    assertFalse( point.getExportOptions().contains( SketchPrivateOptions.OPTION_AFFINE ) );
    assertTrue( point.getExportOptions().contains( "-foo bar" ) );
  }

  @Test public void affinePath_hasRealBoundsAndSurvivesTdrRoundTrip() throws Exception
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, 120.0f, 80.0f, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( SketchBrushStyle.of( 2.0f, 1.30f, 1.0f ) );
    SketchAffineTransform transform = SketchAffineTransform.create( 1.8f, 0.45f, 0.20f, 0.75f );
    assertNotNull( transform );
    assertTrue( point.setSketchAffineTransform( transform ) );

    RectF pathBounds = new RectF();
    point.mPath.computeBounds( pathBounds, true );
    assertTrue( point.width() > 5.0f );
    assertTrue( point.height() > 3.0f );
    assertTrue( point.left < pathBounds.left );
    assertTrue( point.right > pathBounds.right );
    assertTrue( point.top < pathBounds.top );
    assertTrue( point.bottom > pathBounds.bottom );

    DrawingPointPath loaded = roundTrip( point );
    assertTrue( loaded.hasSketchAffineTransform() );
    assertTransformBitsEqual( transform, loaded.getSketchAffineTransform() );
    assertEquals( point.getOptions(), loaded.getOptions() );
    assertEquals( point.mOrientation, loaded.mOrientation, 0.0001 );
    assertEquals( point.getScale(), loaded.getScale() );
    assertEquals( 1.30f, loaded.getSketchPointScaleValue(), 0.0001f );
  }

  @Test public void scrapAffine_leftMultipliesStoredShapeAndRecomputesBounds()
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, 20.0f, 30.0f, PointScale.SCALE_M, 0 );
    SketchAffineTransform original = SketchAffineTransform.create( 1.5f, 0.25f, 0.1f, 0.9f );
    assertTrue( point.setSketchAffineTransform( original ) );
    float[] values = { 0.8f, -0.2f, 7.0f, 0.3f, 1.1f, -4.0f, 0.0f, 0.0f, 1.0f };
    Matrix matrix = new Matrix();
    matrix.setValues( values );

    point.affineTransformBy( values, matrix );

    SketchAffineTransform expected = original.leftMultiply( 0.8f, -0.2f, 0.3f, 1.1f );
    assertNotNull( expected );
    assertTransformBitsEqual( expected, point.getSketchAffineTransform() );
    assertEquals( 17.0f, point.cx, 0.0001f );
    assertEquals( 35.0f, point.cy, 0.0001f );
    assertTrue( point.width() > 1.0f );
    assertTrue( point.height() > 1.0f );
  }

  @Test public void brushWeight_scalesFootprintWithoutChangingStoredShapeOrInkUniformity()
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, 80.0f, 70.0f, PointScale.SCALE_M, 0 );
    SketchAffineTransform shape = SketchAffineTransform.create( 1.6f, 0.55f, 0.15f, 0.8f );
    assertTrue( point.setSketchAffineTransform( shape ) );
    point.setSketchBrushStyle( SketchBrushStyle.of(
        SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, 1.0f, 1.0f ) );
    float standard_footprint = point.getSketchAffineFootprintScale();
    RectF standard_bounds = new RectF();
    point.mPath.computeBounds( standard_bounds, true );
    Paint standard_paint = point.sketchPointPaintForOcclusion();
    assertNotNull( standard_paint );
    assertEquals( "Breakdown structure should use full same-weight line ink",
                  SketchBrushRenderer.sceneUnit( point.getSketchBrushStyle() ),
                  standard_paint.getStrokeWidth(), 0.0001f );
    Paint standard_detail = point.sketchDetailPaintForOcclusion();
    assertNotNull( standard_detail );
    assertEquals( "Breakdown shading should remain one quarter of the structural ink",
                  0.25f, standard_detail.getStrokeWidth() / standard_paint.getStrokeWidth(), 0.0001f );

    point.setSketchBrushStyle( SketchBrushStyle.of(
        SketchBrushStyle.DEFAULT_WEIGHT_THICK, 1.0f, 1.0f ) );
    assertTransformBitsEqual( shape, point.getSketchAffineTransform() );
    float expected_ratio = SketchBrushStyle.DEFAULT_WEIGHT_THICK
                         / SketchBrushStyle.DEFAULT_WEIGHT_STANDARD;
    assertEquals( expected_ratio, point.getSketchAffineFootprintScale() / standard_footprint, 0.0001f );
    RectF thick_bounds = new RectF();
    point.mPath.computeBounds( thick_bounds, true );
    assertEquals( expected_ratio, thick_bounds.width() / standard_bounds.width(), 0.001f );
    assertEquals( expected_ratio, thick_bounds.height() / standard_bounds.height(), 0.001f );
    Paint thick_paint = point.sketchPointPaintForOcclusion();
    assertNotNull( thick_paint );
    assertEquals( expected_ratio, thick_paint.getStrokeWidth() / standard_paint.getStrokeWidth(), 0.0001f );
    assertEquals( Paint.Style.STROKE, thick_paint.getStyle() );
  }

  @Test public void privateOptionExportStripsAffineAndOcclusionTokens()
  {
    String options = "-foo bar " + affine( "v=1,m00=1,m01=0,m10=0,m11=1" )
                   + " -tdx-occlude v=1,g=breakdown -baz qux";
    assertEquals( "-foo bar -baz qux", SketchPrivateOptions.stripAll( options ) );

    DrawingPointPath point = new DrawingPointPath( mBoulder, 10.0f, 20.0f, PointScale.SCALE_M,
                                                    null, options, 0 );
    String therion = point.toTherion();
    assertFalse( therion.contains( "-tdx-" ) );
    assertTrue( therion.contains( "-foo bar" ) );
    assertTrue( therion.contains( "-baz qux" ) );

    StringWriter writer = new StringWriter();
    PrintWriter printer = new PrintWriter( writer );
    point.toTCsurvey( printer, "survey", "cave", "branch", null );
    printer.flush();
    String tcsx = writer.toString();
    assertFalse( tcsx.contains( "-tdx-" ) );
    assertTrue( tcsx.contains( "-foo bar" ) );
    assertTrue( tcsx.contains( "-baz qux" ) );
  }

  @Test public void filledHitTesting_selectsOverlappingAffinePointsNewestFirst()
  {
    DrawingPointPath older = new DrawingPointPath( mBoulder, 50.0f, 50.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath newer = new DrawingPointPath( mBoulder, 50.0f, 50.0f, PointScale.SCALE_M, 0 );
    assertTrue( older.hitSketchAffineSilhouette( 50.0f, 50.0f, 0.0f ) );
    assertFalse( older.hitSketchAffineSilhouette( 500.0f, 500.0f, 0.0f ) );
    Scrap scrap = new Scrap( 0, "affine-hit-order" );
    scrap.addCommand( older );
    scrap.addCommand( newer );

    SelectionSet selected = scrap.getItemsAt( 50.0f, 50.0f, 1.0f, Drawing.FILTER_POINT,
        false, false, false, null, new Selection(), 1.0f );

    assertNotNull( selected.mHotItem );
    assertEquals( newer, selected.mHotItem.mItem );
    assertTrue( selected.size() >= 2 );
  }

  @Test public void gizmoDrags_scaleShearRotateAndClampWithoutAddingUndoCommands()
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, 80.0f, 70.0f, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );
    PointF corner = SketchAffineGizmo.handlePoint( point, SketchAffineGizmo.CORNER_NE, 1.0f );
    assertEquals( SketchAffineGizmo.CORNER_NE,
                  SketchAffineGizmo.hitHandle( point, corner.x, corner.y, 1.0f ) );

    Scrap scrap = new Scrap( 0, "affine-gizmo" );
    scrap.addCommand( point );
    int commandCount = scrap.mCurrentStack.size();

    SketchAffineGizmo.Drag cornerDrag = SketchAffineGizmo.beginDrag(
        point, SketchAffineGizmo.CORNER_NE, corner.x, corner.y, 1.0f );
    PointF enlarged = new PointF( corner.x + 30.0f, corner.y - 15.0f );
    assertTrue( cornerDrag.update( enlarged.x, enlarged.y ) );
    SketchAffineTransform scaled = point.getSketchAffineTransform();
    assertTrue( scaled.m00 > 1.2f );
    assertTrue( scaled.m11 > 0.4f );
    assertTrue( scaled.determinant() > 0.0f );

    PointF shearStart = SketchAffineGizmo.handlePoint( point, SketchAffineGizmo.SHEAR_X, 1.0f );
    SketchAffineGizmo.Drag shearDrag = SketchAffineGizmo.beginDrag(
        point, SketchAffineGizmo.SHEAR_X, shearStart.x, shearStart.y, 1.0f );
    PointF shearTarget = new PointF( shearStart.x + 25.0f, shearStart.y );
    assertTrue( shearDrag.update( shearTarget.x, shearTarget.y ) );
    SketchAffineTransform sheared = point.getSketchAffineTransform();
    assertTrue( Math.abs( sheared.m01 ) > 0.05f );
    assertTrue( sheared.determinant() > 0.0f );

    SketchAffineGizmo.Drag rotate = SketchAffineGizmo.beginDrag(
        point, SketchAffineGizmo.ROTATE, point.cx + 20.0f, point.cy, 1.0f );
    assertTrue( rotate.update( point.cx, point.cy + 20.0f ) );
    SketchAffineTransform rotated = point.getSketchAffineTransform();
    assertTrue( rotated.determinant() > 0.0f );

    PointF east = SketchAffineGizmo.handlePoint( point, SketchAffineGizmo.EDGE_E, 1.0f );
    SketchAffineGizmo.Drag clamp = SketchAffineGizmo.beginDrag(
        point, SketchAffineGizmo.EDGE_E, east.x, east.y, 1.0f );
    PointF crossed = new PointF( point.cx - 500.0f, point.cy );
    assertTrue( clamp.update( crossed.x, crossed.y ) );
    SketchAffineTransform clamped = point.getSketchAffineTransform();
    assertTrue( clamped.determinant() > 0.0f );
    assertTrue( Math.hypot( clamped.m00, clamped.m10 ) >= SketchAffineTransform.MIN_AXIS_SCALE );

    assertEquals( commandCount, scrap.mCurrentStack.size() );
    scrap.undo();
    assertFalse( scrap.mCurrentStack.contains( point ) );
    scrap.redo();
    assertTrue( scrap.mCurrentStack.contains( point ) );
    assertTransformBitsEqual( clamped, point.getSketchAffineTransform() );
  }

  @Test public void gizmo_usesDistinctFixedScreenColorsForScaleShearAndRotation()
  {
    DrawingPointPath point = new DrawingPointPath( mBoulder, 140.0f, 120.0f, PointScale.SCALE_M, 0 );
    Bitmap bitmap = Bitmap.createBitmap( 300, 260, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.TRANSPARENT );
    SketchAffineGizmo.draw( new Canvas( bitmap ), new Matrix(), 1.0f, point );
    assertTrue( "Scale handles were not drawn in cyan", countColor( bitmap, 0xff00d8ff ) > 20 );
    assertTrue( "Shear handles were not drawn in orange", countColor( bitmap, 0xffffa22b ) > 20 );
    assertTrue( "Rotation handle was not drawn in magenta", countColor( bitmap, 0xffff4fd8 ) > 10 );
    bitmap.recycle();
  }

  private static String affine( String value )
  {
    return SketchPrivateOptions.OPTION_AFFINE + " " + value;
  }

  private static int countColor( Bitmap bitmap, int color )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( bitmap.getPixel( x, y ) == color ) ++count;
      }
    }
    return count;
  }

  private static void assertTransformBitsEqual( SketchAffineTransform expected, SketchAffineTransform actual )
  {
    assertNotNull( actual );
    assertEquals( Float.floatToIntBits( expected.m00 ), Float.floatToIntBits( actual.m00 ) );
    assertEquals( Float.floatToIntBits( expected.m01 ), Float.floatToIntBits( actual.m01 ) );
    assertEquals( Float.floatToIntBits( expected.m10 ), Float.floatToIntBits( actual.m10 ) );
    assertEquals( Float.floatToIntBits( expected.m11 ), Float.floatToIntBits( actual.m11 ) );
  }

  private static DrawingPointPath roundTrip( DrawingPointPath point ) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    point.toDataStream( output, 0 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded = DrawingPointPath.loadDataStream( TDR_VERSION_WITH_STYLE_OPTIONS, input, 0.0f, 0.0f );
    assertNotNull( loaded );
    return loaded;
  }
}
