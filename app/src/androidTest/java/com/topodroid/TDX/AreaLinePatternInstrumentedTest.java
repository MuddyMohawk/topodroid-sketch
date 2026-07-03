package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.zip.ZipFile;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class AreaLinePatternInstrumentedTest
{
  private Context mContext;
  private Context mPreviousContext;
  private boolean mPreviousAreaOverlapDarken;
  private int mPreviousWithLevels;
  private float mPreviousLineThickness;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mPreviousAreaOverlapDarken = TDSetting.mAreaOverlapDarken;
    mPreviousWithLevels = TDSetting.mWithLevels;
    mPreviousLineThickness = TDSetting.mLineThickness;

    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadAreaLibrary( mContext.getResources() );

    TDSetting.mAreaOverlapDarken = false;
    TDSetting.mWithLevels = 0;
    TDSetting.mLineThickness = 1.0f;
  }

  @After
  public void tearDown()
  {
    TDSetting.mAreaOverlapDarken = mPreviousAreaOverlapDarken;
    TDSetting.mWithLevels = mPreviousWithLevels;
    TDSetting.mLineThickness = mPreviousLineThickness;
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void parserAcceptsWaterPattern()
  {
    AreaLinePattern pattern = AreaLinePattern.parse(
        "parallel angle -35 color 0x0099ff 0x99 width 1.0 spacing 6.0 anchor world overlap union".split( " " ), 0 );

    assertNotNull( pattern );
    assertEquals( -35.0f, pattern.mAngle, 0.001f );
    assertEquals( 0x990099ff, pattern.mColor );
    assertEquals( 1.0f, pattern.mWidthScale, 0.001f );
    assertEquals( 6.0f, pattern.mSpacingScale, 0.001f );
    assertTrue( pattern.isParallelWorldUnion() );
  }

  @Test
  public void parserRejectsUnsupportedPattern()
  {
    assertEquals( null, AreaLinePattern.parse( "crosshatch angle -35".split( " " ), 0 ) );
    assertEquals( null, AreaLinePattern.parse( "parallel anchor local".split( " " ), 0 ) );
  }

  @Test
  public void fileBackedWaterHasLinePattern()
  {
    int water = waterAreaType();
    AreaLinePattern pattern = BrushManager.getAreaLinePattern( water );

    assertNotNull( pattern );
    assertTrue( pattern.isParallelWorldUnion() );
  }

  @Test
  public void dataStreamRoundTrip_preservesAreaBrushOptions() throws Exception
  {
    DrawingAreaPath loaded = roundTripArea( rectangleArea( 20.0f, 20.0f, 120.0f, 120.0f,
        SketchBrushStyle.of( 4.0f, 1.0f, 0.75f, 0x123456 ) ) );

    SketchBrushStyle parsed = SketchBrushStyleCodec.fromOptions( loaded.getOptions() );
    assertNotNull( parsed );
    assertEquals( 4.0f, parsed.weightOr( 0.0f ), 0.0001f );
    assertEquals( 0.75f, parsed.opacityOr( 0.0f ), 0.0001f );
    assertTrue( parsed.hasColor() );
    assertEquals( 0x123456, parsed.colorOr( 0 ) );
  }

  @Test
  public void structuredExports_stripPrivateAreaBrushOptions()
  {
    DrawingAreaPath area = rectangleArea( 20.0f, 20.0f, 120.0f, 120.0f,
        SketchBrushStyle.of( 4.0f, 1.0f, 0.75f, 0x123456 ) );

    StringWriter writer = new StringWriter();
    PrintWriter printer = new PrintWriter( writer );
    area.toTCsurvey( printer, "survey", "cave", "branch", null );
    printer.flush();

    assertFalse( writer.toString(), writer.toString().contains( "tdx-brush" ) );
  }

  @Test
  public void symbolZipExportIncludesWaterAreaAtImporterPath() throws Exception
  {
    File zip = TDPath.getTmpFile( "area-pattern-test-areas.zip" );
    try {
      Method method = Archiver.class.getDeclaredMethod( "compressSymbols", File.class, SymbolLibrary.class, String.class );
      method.setAccessible( true );
      Boolean ok = (Boolean)method.invoke( new Archiver(), zip, BrushManager.getAreaLib(), TDPath.getSymbolAreaDirname() );
      assertTrue( ok.booleanValue() );

      ZipFile nested = new ZipFile( zip );
      try {
        assertNotNull( "areas.zip should contain water", nested.getEntry( "water" ) );
        assertEquals( "areas.zip entries should be plain filenames", null, nested.getEntry( "area/water" ) );
      } finally {
        nested.close();
      }
    } finally {
      com.topodroid.util.TDFile.deleteFile( zip );
    }
  }

  @Test
  public void overlappingWaterAreasDrawOnlyOnce()
  {
    DrawingAreaPath one = rectangleArea( 20.0f, 20.0f, 180.0f, 180.0f,
        SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );
    DrawingAreaPath duplicate = rectangleArea( 20.0f, 20.0f, 180.0f, 180.0f,
        SketchBrushStyle.of( 2.0f, 1.0f, 1.0f ) );

    long single = interiorChecksum( render( one ), 45, 45, 155, 155 );
    Scrap scrap = new Scrap( 0, "area-pattern-overlap" );
    scrap.addCommand( one );
    scrap.addCommand( duplicate );
    long doubled = interiorChecksum( render( scrap ), 45, 45, 155, 155 );

    assertEquals( "Duplicate water hatch should not darken or redraw interior lines", single, doubled );
  }

  @Test
  public void activeStyleScalesHatchSpacingAndWidth()
  {
    Bitmap thin = render( rectangleArea( 20.0f, 20.0f, 230.0f, 230.0f,
        SketchBrushStyle.of( 1.0f, 1.0f, 1.0f ) ) );
    Bitmap thick = render( rectangleArea( 20.0f, 20.0f, 230.0f, 230.0f,
        SketchBrushStyle.of( 4.0f, 1.0f, 1.0f ) ) );

    RunStats thinStats = scanRow( thin, 120 );
    RunStats thickStats = scanRow( thick, 120 );

    assertTrue( "Thicker style should create fewer hatch repeats because spacing scales",
        thickStats.runs < thinStats.runs );
    assertTrue( "Thicker style should create wider hatch strokes",
        thickStats.maxRun > thinStats.maxRun );
  }

  private DrawingAreaPath roundTripArea( DrawingAreaPath area ) throws Exception
  {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    area.toDataStream( output, 0 );
    output.flush();

    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'A', input.read() );
    DrawingAreaPath loaded = DrawingAreaPath.loadDataStream( DrawingAreaPath.AREA_OPTIONS_VERSION, input, 0.0f, 0.0f );
    assertNotNull( loaded );
    return loaded;
  }

  private DrawingAreaPath rectangleArea( float left, float top, float right, float bottom, SketchBrushStyle style )
  {
    DrawingAreaPath area = new DrawingAreaPath( waterAreaType(), 1, "a", false, 0 );
    area.setSketchBrushStyle( style );
    area.addStartPoint( left, top );
    area.addPoint( right, top );
    area.addPoint( right, bottom );
    area.addPoint( left, bottom );
    area.closePath();
    return area;
  }

  private int waterAreaType()
  {
    int water = BrushManager.getAreaIndexByThName( SymbolLibrary.WATER );
    assertTrue( "Missing water area", water >= 0 );
    return water;
  }

  private Bitmap render( DrawingAreaPath area )
  {
    Scrap scrap = new Scrap( 0, "area-pattern-render" );
    scrap.addCommand( area );
    return render( scrap );
  }

  private Bitmap render( Scrap scrap )
  {
    Bitmap bitmap = Bitmap.createBitmap( 260, 260, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    scrap.drawAll( canvas, new Matrix(), 1.0f, new RectF( 0.0f, 0.0f, 260.0f, 260.0f ) );
    return bitmap;
  }

  private long interiorChecksum( Bitmap bitmap, int left, int top, int right, int bottom )
  {
    long sum = 0;
    for ( int y = top; y < bottom; ++y ) {
      for ( int x = left; x < right; ++x ) {
        sum = 31 * sum + bitmap.getPixel( x, y );
      }
    }
    return sum;
  }

  private RunStats scanRow( Bitmap bitmap, int y )
  {
    int runs = 0;
    int run = 0;
    int maxRun = 0;
    for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      if ( bitmap.getPixel( x, y ) != Color.BLACK ) {
        if ( run == 0 ) ++runs;
        ++run;
        if ( run > maxRun ) maxRun = run;
      } else {
        run = 0;
      }
    }
    return new RunStats( runs, maxRun );
  }

  private static class RunStats
  {
    final int runs;
    final int maxRun;

    RunStats( int runs, int maxRun )
    {
      this.runs = runs;
      this.maxRun = maxRun;
    }
  }
}
