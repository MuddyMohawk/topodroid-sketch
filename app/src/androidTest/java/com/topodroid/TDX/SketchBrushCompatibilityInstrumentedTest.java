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
public class SketchBrushCompatibilityInstrumentedTest
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
    SketchLineSymbolManager.ensureLineSymbols();
    SketchLineSymbolManager.syncPrefsFromSymbolFiles();
    BrushManager.reloadPointLibrary( mContext, mContext.getResources() );
    BrushManager.reloadLineLibrary( mContext.getResources() );
  }

  @After
  public void tearDown()
  {
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void dataStreamRoundTrip_preservesLineAndPointBrushOptions() throws Exception
  {
    SketchBrushStyle lineStyle = SketchBrushStyle.of( 5.0f, 1.0f, 0.75f, 0x123456 );
    SketchBrushStyle pointStyle = SketchBrushStyle.of( 2.0f, 1.4f, 0.5f, 0xabcdef );

    DrawingLinePath loadedLine = roundTripLine( styledLine( lineStyle ) );
    DrawingPointPath loadedPoint = roundTripPoint( styledPoint( pointStyle ) );

    assertStyle( loadedLine.getOptions(), 5.0f, 1.0f, 0.75f, 0x123456 );
    assertStyle( loadedPoint.getOptions(), 2.0f, 1.4f, 0.5f, 0xabcdef );
  }

  @Test
  public void structuredExports_stripPrivateBrushOptions()
  {
    DrawingLinePath line = styledLine( SketchBrushStyle.of( 5.0f, 1.0f, 0.75f, 0x123456 ) );
    DrawingPointPath point = styledPoint( SketchBrushStyle.of( 2.0f, 1.4f, 0.5f, 0xabcdef ) );

    String therion = line.toTherion() + point.toTherion();
    assertNoPrivateBrushOption( therion );
    assertTrue( therion.contains( "-id line-probe" ) );
    assertTrue( therion.contains( "-id point-probe" ) );

    StringWriter writer = new StringWriter();
    PrintWriter printer = new PrintWriter( writer );
    line.toTCsurvey( printer, "survey", "cave", "branch", null );
    point.toTCsurvey( printer, "survey", "cave", "branch", null );
    printer.flush();
    String tcsx = writer.toString();

    assertNoPrivateBrushOption( tcsx );
    assertTrue( tcsx.contains( "-id line-probe" ) );
    assertTrue( tcsx.contains( "-id point-probe" ) );
  }

  @Test
  public void splitUndoAndRedo_keepCapturedBrushStyle()
  {
    SketchBrushStyle style = SketchBrushStyle.of( 5.0f, 1.0f, 1.0f );
    DrawingLinePath line = multiPointLine( style );
    DrawingLinePath first = new DrawingLinePath( line.lineType(), 0 );
    DrawingLinePath second = new DrawingLinePath( line.lineType(), 0 );

    assertTrue( line.splitAt( line.first().mNext, first, second, false ) );
    assertStyle( first.getOptions(), 5.0f, 1.0f, 1.0f, null );
    assertStyle( second.getOptions(), 5.0f, 1.0f, 1.0f, null );

    Scrap scrap = new Scrap( 0, "brush-compat" );
    scrap.addCommand( first );
    scrap.undo();
    assertFalse( scrap.mCurrentStack.contains( first ) );
    scrap.redo();
    assertTrue( scrap.mCurrentStack.contains( first ) );
    assertStyle( first.getOptions(), 5.0f, 1.0f, 1.0f, null );
  }

  private DrawingLinePath roundTripLine( DrawingLinePath line ) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    line.toDataStream( output, 0 );
    output.flush();

    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'L', input.read() );
    DrawingLinePath loaded = DrawingLinePath.loadDataStream( TDR_VERSION_WITH_STYLE_OPTIONS, input, 0.0f, 0.0f );
    assertNotNull( loaded );
    return loaded;
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

  private DrawingLinePath styledLine( SketchBrushStyle style )
  {
    DrawingLinePath line = multiPointLine( style );
    line.setOptions( SketchBrushStyleCodec.storeInOptions( "-id line-probe", style ) );
    return line;
  }

  private DrawingLinePath multiPointLine( SketchBrushStyle style )
  {
    int lineType = BrushManager.getLineIndexByThName( SymbolLibrary.USER );
    assertTrue( "Missing user line", lineType >= 0 );
    DrawingLinePath line = new DrawingLinePath( lineType, 0 );
    line.addStartPoint( 0.0f, 0.0f );
    line.addPoint( 10.0f, 0.0f );
    line.addPoint( 20.0f, 5.0f );
    line.addPoint( 30.0f, 5.0f );
    line.computeUnitNormal();
    line.setSketchBrushStyle( style );
    return line;
  }

  private DrawingPointPath styledPoint( SketchBrushStyle style )
  {
    int pointType = BrushManager.getPointIndexByThName( SymbolLibrary.CLAY );
    assertTrue( "Missing clay point", pointType >= 0 );
    DrawingPointPath point = new DrawingPointPath( pointType, 12.0f, 24.0f, PointScale.SCALE_M, 0 );
    point.setOptions( "-id point-probe" );
    point.setSketchBrushStyle( style );
    return point;
  }

  private void assertNoPrivateBrushOption( String text )
  {
    assertFalse( text, text.contains( "tdx-brush" ) );
  }

  private void assertStyle( String options, float weight, float pointScale, float opacity, Integer color )
  {
    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( options );
    assertNotNull( "Missing brush style in options: " + options, parsed );
    assertEquals( weight, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( pointScale, parsed.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( opacity, parsed.opacityOr( 0.0f ), 0.0001f );
    if ( color == null ) {
      assertFalse( parsed.hasColor() );
    } else {
      assertTrue( parsed.hasColor() );
      assertEquals( color.intValue() & 0x00ffffff, parsed.colorOr( 0 ) );
    }
  }
}
