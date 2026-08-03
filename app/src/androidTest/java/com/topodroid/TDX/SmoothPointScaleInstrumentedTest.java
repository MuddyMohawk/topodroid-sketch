package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

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
public class SmoothPointScaleInstrumentedTest
{
  private static final int TDR_VERSION_WITH_STYLE_OPTIONS = 602055;

  private Context mContext;
  private Context mPreviousContext;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
  }

  @After
  public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void scaleMapper_preservesLegacyAnchorsAndContinuesPastXl()
  {
    assertEquals( 0.50f, SketchPointScale.scaleFromDragDistance( 0.0f ), 0.0001f );
    assertEquals( 0.72f, SketchPointScale.scaleFromDragDistance( 75.0f ), 0.0001f );
    assertEquals( 1.00f, SketchPointScale.scaleFromDragDistance( 150.0f ), 0.0001f );
    assertEquals( 1.41f, SketchPointScale.scaleFromDragDistance( 225.0f ), 0.0001f );
    assertEquals( 2.00f, SketchPointScale.scaleFromDragDistance( 300.0f ), 0.0001f );
    assertTrue( SketchPointScale.scaleFromDragDistance( 375.0f ) > 2.0f );

    assertEquals( SketchPointScale.scaleFromDragDistance( 225.0f ),
                  SketchPointScale.scaleFromAffinePlacementDragDistance( 75.0f ), 0.0001f );
    assertEquals( SketchPointScale.scaleFromDragDistance( 450.0f ),
                  SketchPointScale.scaleFromAffinePlacementDragDistance( 150.0f ), 0.0001f );

    assertEquals( 1.0f, SketchPointScale.normalize( Float.NaN ), 0.0001f );
    assertEquals( 1.0f, SketchPointScale.normalize( -4.0f ), 0.0001f );
    assertEquals( PointScale.SCALE_L, SketchPointScale.nearestLegacyScale( 1.60f ) );
  }

  @Test
  public void exactPointScale_preservesOtherBrushFieldsAndNearestLegacyBucket()
  {
    DrawingPointPath point = styledClayPoint( SketchBrushStyle.of( 5.0f, 1.0f, 0.75f, 0x123456 ) );

    assertTrue( point.setExactPointScale( 1.60f ) );

    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( point.getOptions() );
    assertNotNull( parsed );
    assertEquals( 5.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 1.60f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 0.75f, parsed.opacityOr( 0.0f ), 0.0001f );
    assertEquals( 0x123456, parsed.colorOr( 0 ) );
    assertEquals( PointScale.SCALE_L, point.getScale() );

    point.setScale( PointScale.SCALE_XL );
    parsed = SketchBrushStyleCodec.fromOptions( point.getOptions() );
    assertNotNull( parsed );
    assertEquals( 5.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 2.0f, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( 0.75f, parsed.opacityOr( 0.0f ), 0.0001f );
    assertEquals( 0x123456, parsed.colorOr( 0 ) );
  }

  @Test
  public void exactPointScale_roundTripPreservesPrivateScaleAndExportsLegacyFallback() throws Exception
  {
    DrawingPointPath point = styledClayPoint( SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );
    assertTrue( point.setExactPointScale( 1.60f ) );

    DrawingPointPath loaded = roundTripPoint( point );
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( loaded.getOptions() );
    assertNotNull( parsed );
    assertEquals( 1.60f, parsed.pointScaleOr( 0.0f ), 0.0001f );

    String therion = loaded.toTherion();
    assertFalse( therion, therion.contains( "tdx-brush" ) );
    assertTrue( therion, therion.contains( "-scale l" ) );

    StringWriter writer = new StringWriter();
    PrintWriter printer = new PrintWriter( writer );
    loaded.toTCsurvey( printer, "survey", "cave", "branch", null );
    printer.flush();
    String tcsx = writer.toString();
    assertFalse( tcsx, tcsx.contains( "tdx-brush" ) );
    assertTrue( tcsx, tcsx.contains( "scale=\"1\"" ) );
  }

  private DrawingPointPath styledClayPoint( SketchBrushStyle style )
  {
    int pointType = BrushManager.getPointIndexByThName( SymbolLibrary.CLAY );
    assertTrue( "Missing clay point", pointType >= 0 );
    DrawingPointPath point = new DrawingPointPath( pointType, 12.0f, 24.0f, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( style );
    return point;
  }

  private DrawingPointPath roundTripPoint( DrawingPointPath point ) throws Exception
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
