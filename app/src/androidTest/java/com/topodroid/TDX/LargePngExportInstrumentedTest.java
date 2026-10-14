package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/** Exercise the real export path beyond older Android's software clipPath limit. */
@RunWith( AndroidJUnit4.class )
public class LargePngExportInstrumentedTest
{
  private static final float[] POSITIONS = { 10, 700, 1400, 1600, 1700, 2200 };
  private Context mPreviousContext;
  private int mPreviousWithLevels;
  private float mPreviousLineThickness;
  private boolean mPreviousAreaOverlapDarken;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mPreviousWithLevels = TDSetting.mWithLevels;
    mPreviousLineThickness = TDSetting.mLineThickness;
    mPreviousAreaOverlapDarken = TDSetting.mAreaOverlapDarken;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( context );
    TDSetting.mWithLevels = 0;
    TDSetting.mLineThickness = 1;
    TDSetting.mAreaOverlapDarken = true;
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadAreaLibrary( context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
  }

  @After public void tearDown()
  {
    TDSetting.mWithLevels = mPreviousWithLevels;
    TDSetting.mLineThickness = mPreviousLineThickness;
    TDSetting.mAreaOverlapDarken = mPreviousAreaOverlapDarken;
    TDInstance.context = mPreviousContext;
  }

  @Test public void wideClayExport_retainsAllWeightsAndBackgrounds()
  {
    for ( float weight : new float[] { 1, 2, 5 } ) {
      for ( boolean transparent : new boolean[] { true, false } ) {
        ArrayList< DrawingPath > paths = patches( SymbolLibrary.CLAY, weight, false );
        Bitmap bitmap = export( paths, transparent );
        try {
          assertEquals( 22610, bitmap.getWidth() ); // Ensure no resolution fallback hid the bug.
          assertEquals( 710, bitmap.getHeight() );
          assertEquals( transparent, bitmap.hasAlpha() );
          for ( float x : POSITIONS ) {
            assertWindowMatches( "Clay weight " + weight + ", transparent " + transparent + ", x " + x,
                bitmap, paths, transparent, Math.round( (x + 21) * 10 ), 310, 200, 200 );
          }
        } finally {
          bitmap.recycle();
        }
      }
    }
  }

  @Test public void tallExport_retainsPatternedAndPlainFills()
  {
    for ( String name : new String[] { SymbolLibrary.CLAY, SymbolLibrary.WATER,
                                      SymbolLibrary.SUMP, SymbolLibrary.BEDROCK, SymbolLibrary.USER } ) {
      ArrayList< DrawingPath > paths = patches( name, 2, true );
      Bitmap bitmap = export( paths, true );
      try {
        assertEquals( 710, bitmap.getWidth() );
        assertEquals( 22610, bitmap.getHeight() );
        for ( float y : POSITIONS ) {
          assertWindowMatches( name + ", y " + y, bitmap, paths, true,
              310, Math.round( (y + 21) * 10 ), 200, 200 );
        }
      } finally {
        bitmap.recycle();
      }
    }
  }

  @Test public void tileEdges_preserveOverlappingAreasFadesAndLines()
  {
    for ( boolean vertical : new boolean[] { false, true } ) {
      for ( boolean transparent : new boolean[] { true, false } ) {
        ArrayList< DrawingPath > paths = patches( SymbolLibrary.CLAY, 2, vertical );
        int type = BrushManager.getAreaIndexByThName( SymbolLibrary.CLAY );
        // These overlap across output pixel 20480, beyond the old clipping limit.
        paths.add( rectangle( type, vertical ? 10 : 2020, vertical ? 2020 : 10, 40, 2 ) );
        paths.add( rectangle( type, vertical ? 15 : 2035, vertical ? 2035 : 15, 40, 2 ) );
        DrawingLinePath line = new DrawingLinePath( BrushManager.getLineIndexByThName( "wall" ), 0 );
        line.addStartPoint( vertical ? 30 : 2010, vertical ? 2010 : 30 );
        line.addPoint( vertical ? 30 : 2080, vertical ? 2080 : 30 );
        line.retracePath();
        paths.add( line );
        Bitmap bitmap = export( paths, transparent );
        try {
          assertWindowMatches( "Tile seam, vertical " + vertical + ", transparent " + transparent,
              bitmap, paths, transparent, vertical ? 280 : 20300, vertical ? 20300 : 280,
              vertical ? 480 : 800, vertical ? 800 : 480 );
          for ( int pixel = 20479; pixel <= 20481; ++pixel ) {
            assertEquals( "Gap in the line across a tile edge", Color.WHITE,
                bitmap.getPixel( vertical ? 510 : pixel, vertical ? pixel : 510 ) );
          }
        } finally {
          bitmap.recycle();
        }
      }
    }
  }

  @Test public void largeEncodedPng_preservesTheRestoredFills() throws Exception
  {
    ArrayList< DrawingPath > paths = patches( SymbolLibrary.CLAY, 2, false );
    for ( boolean transparent : new boolean[] { true, false } ) {
      Bitmap bitmap = export( paths, transparent );
      Bitmap decoded = null;
      try {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue( PngFastWriter.write( bitmap, output ) );
        decoded = BitmapFactory.decodeByteArray( output.toByteArray(), 0, output.size() );
        assertNotNull( decoded );
        assertEquals( bitmap.getWidth(), decoded.getWidth() );
        assertEquals( bitmap.getHeight(), decoded.getHeight() );
        assertWindowMatches( "Encoded PNG, transparent " + transparent, decoded, paths, transparent,
            22210, 310, 200, 200 );
      } finally {
        bitmap.recycle();
        if ( decoded != null ) decoded.recycle();
      }
    }
  }

  private static ArrayList< DrawingPath > patches( String name, float weight, boolean vertical )
  {
    int type = BrushManager.getAreaIndexByThName( name );
    assertTrue( "Missing area " + name, type >= 0 );
    ArrayList< DrawingPath > paths = new ArrayList<>();
    for ( float position : POSITIONS ) {
      paths.add( rectangle( type, vertical ? 10 : position, vertical ? position : 10, 20, weight ) );
    }
    return paths;
  }

  private static DrawingAreaPath rectangle( int type, float x, float y, float side, float weight )
  {
    DrawingAreaPath area = new DrawingAreaPath( type, 1, "large-png", false, 0 );
    area.addStartPoint( x, y );
    area.addPoint( x + side, y );
    area.addPoint( x + side, y + side );
    area.addPoint( x, y + side );
    area.closePath();
    area.setSketchBrushStyle( SketchBrushStyle.of( weight, 1, 1 ) );
    return area;
  }

  private static Bitmap export( ArrayList< DrawingPath > paths, boolean transparent )
  {
    DrawingCommandManager manager = new DrawingCommandManager( DrawingSurface.DRAWING_PLAN, "large-png" );
    for ( DrawingPath path : paths ) manager.addCommand( path );
    SketchPngExportOptions options = new SketchPngExportOptions(
        false, false, false, false, false, false, transparent, false, 1, "large-png.png" );
    Bitmap bitmap = manager.renderExportBitmap( null, options );
    assertNotNull( bitmap );
    return bitmap;
  }

  private static void assertWindowMatches( String message, Bitmap actual, ArrayList< DrawingPath > paths,
                                           boolean transparent, int left, int top, int width, int height )
  {
    // Render the same full scene directly onto a small raster device at this
    // window's origin. It cannot hit the large-device clipPath limit, and keeps
    // the original unions, overlaps and world-anchored pattern phase as the oracle.
    Bitmap expected = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    try {
      if ( ! transparent ) expected.eraseColor( Color.BLACK );
      Matrix matrix = new Matrix();
      matrix.setScale( 10, 10 );
      matrix.postTranslate( 210 - left, 210 - top );
      Scrap scrap = new Scrap( 0, "large-png-reference" );
      for ( DrawingPath path : paths ) scrap.addCommand( path );
      scrap.drawAll( new Canvas( expected ), matrix, 0.1f, new RectF( -21, -21, 2300, 2300 ), false );
      int[] reference = new int[width * height];
      int[] exported = new int[reference.length];
      expected.getPixels( reference, 0, width, 0, 0, width, height );
      actual.getPixels( exported, 0, width, left, top, width, height );
      boolean hasInk = false;
      for ( int color : reference ) {
        if ( transparent ? Color.alpha( color ) > 0 : ( color & 0x00ffffff ) != 0 ) {
          hasInk = true;
          break;
        }
      }
      assertTrue( message + ": empty reference window", hasInk );
      // Rebasing the same world geometry onto another raster origin can round
      // antialias coverage slightly differently. Compare premultiplied channels
      // so low-alpha color unpremultiplication does not amplify that rounding.
      long squaredError = 0;
      for ( int i = 0; i < reference.length; ++i ) {
        int expectedAlpha = Color.alpha( reference[i] );
        int actualAlpha = Color.alpha( exported[i] );
        int error = expectedAlpha - actualAlpha;
        squaredError += error * error;
        for ( int shift = 0; shift <= 16; shift += 8 ) {
          int expectedChannel = (((reference[i] >>> shift) & 255) * expectedAlpha + 127) / 255;
          int actualChannel = (((exported[i] >>> shift) & 255) * actualAlpha + 127) / 255;
          error = expectedChannel - actualChannel;
          squaredError += error * error;
        }
      }
      double rmsError = Math.sqrt( squaredError / (4.0 * reference.length) );
      System.out.println( message + ": pixel RMS error " + rmsError );
      assertTrue( message + ": pixel RMS error " + rmsError, rmsError <= 1.0 );
    } finally {
      expected.recycle();
    }
  }
}
