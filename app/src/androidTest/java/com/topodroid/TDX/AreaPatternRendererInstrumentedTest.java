package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/** Raster-level invariants for the programmatic bedrock area pattern. */
@RunWith( AndroidJUnit4.class )
public class AreaPatternRendererInstrumentedTest
{
  private static final int WIDTH = 640;
  private static final int HEIGHT = 480;
  private static final float SCALE = 4.0f;
  private static final AreaLinePattern BEDROCK = AreaLinePattern.bedrock(
      0.0f, 0xcc888888, 0.85f, 17.0f, 48.0f, 0.0f );

  @Test
  public void bedrockPattern_isWorldAnchoredIrregularAndHardClipped() throws Exception
  {
    DrawingAreaPath first = rectangle( 15.0f, 15.0f, 145.0f, 105.0f );
    DrawingAreaPath second = rectangle( 55.0f, 35.0f, 155.0f, 115.0f );
    Bitmap a = renderTransparent( first );
    Bitmap b = renderTransparent( second );

    // The same world-space overlap must contain the same pixels even though the two
    // independently rendered area bounds start at different coordinates.
    // Stay clear of both independently antialiased clip edges; the pattern itself is
    // identical there too, but clip coverage is intentionally region-shape-dependent.
    int overlapLeft = Math.round( 55.0f * SCALE ) + 8;
    int overlapTop = Math.round( 35.0f * SCALE ) + 8;
    int overlapRight = Math.round( 145.0f * SCALE ) - 8;
    int overlapBottom = Math.round( 105.0f * SCALE ) - 8;
    for ( int y = overlapTop; y < overlapBottom; ++y ) {
      for ( int x = overlapLeft; x < overlapRight; ++x ) {
        // Independently bounded saveLayers can quantize the outer antialias fringe by
        // a few alpha levels, so compare visible ink occupancy rather than exact fringe alpha.
        assertEquals( "Bedrock pixels drifted from the absolute world grid at " + x + "," + y,
                      Color.alpha( a.getPixel( x, y ) ) > 96,
                      Color.alpha( b.getPixel( x, y ) ) > 96 );
      }
    }

    int left = Math.round( 15.0f * SCALE );
    int top = Math.round( 15.0f * SCALE );
    int right = Math.round( 145.0f * SCALE );
    int bottom = Math.round( 105.0f * SCALE );
    int edgeInk = 0;
    for ( int y = 0; y < HEIGHT; ++y ) {
      for ( int x = 0; x < WIDTH; ++x ) {
        int alpha = Color.alpha( a.getPixel( x, y ) );
        if ( x < left - 2 || x > right + 2 || y < top - 2 || y > bottom + 2 ) {
          assertEquals( "Bedrock ink escaped the area clip at " + x + "," + y, 0, alpha );
        }
        if ( alpha > 96 && ( x == left || x == left + 1 || x == right - 1 || x == right ) ) {
          ++edgeInk;
        }
      }
    }
    assertTrue( "Expected bedding lines to be visibly truncated at the left/right area edge", edgeInk > 6 );

    // Horizontal bedding lines cover most of the rectangle width. Their screen-space
    // gaps must not all match: the authored course stamp deliberately varies thickness.
    ArrayList< Integer > rows = new ArrayList<>();
    for ( int y = top + 3; y < bottom - 3; ++y ) {
      int ink = 0;
      for ( int x = left + 3; x < right - 3; ++x ) {
        if ( Color.alpha( a.getPixel( x, y ) ) > 96 ) ++ink;
      }
      if ( ink > ( right - left ) / 2 && ( rows.isEmpty() || y - rows.get( rows.size()-1 ) > 2 ) ) {
        rows.add( y );
      }
    }
    assertTrue( "Too few bedrock courses rendered", rows.size() >= 6 );
    boolean varied = false;
    int firstGap = rows.get( 1 ) - rows.get( 0 );
    for ( int k = 2; k < rows.size(); ++k ) {
      if ( Math.abs( ( rows.get( k ) - rows.get( k-1 ) ) - firstGap ) >= 3 ) {
        varied = true;
        break;
      }
    }
    assertTrue( "Bedrock courses unexpectedly have uniform thickness", varied );

    writeVisualProof();
    a.recycle();
    b.recycle();
  }

  private static Bitmap renderTransparent( DrawingAreaPath area )
  {
    Bitmap bitmap = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.TRANSPARENT );
    draw( new Canvas( bitmap ), area, new RectF( 0.0f, 0.0f, WIDTH / SCALE, HEIGHT / SCALE ) );
    return bitmap;
  }

  private static void draw( Canvas canvas, DrawingAreaPath area, RectF bbox )
  {
    Matrix matrix = new Matrix();
    matrix.setScale( SCALE, SCALE );
    ArrayList< DrawingAreaPath > members = new ArrayList<>();
    members.add( area );
    AreaPatternRenderer.drawGroup( canvas, matrix, bbox, BEDROCK, members, false, 1.0f );
  }

  private static DrawingAreaPath rectangle( float left, float top, float right, float bottom )
  {
    DrawingAreaPath area = new DrawingAreaPath( 0, 1, "bedrock-test", true, 0 );
    area.addStartPoint( left, top );
    area.addPoint( right, top );
    area.addPoint( right, bottom );
    area.addPoint( left, bottom );
    area.closePath();
    return area;
  }

  private static void writeVisualProof() throws Exception
  {
    Bitmap bitmap = Bitmap.createBitmap( 800, 600, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.WHITE );
    DrawingAreaPath area = new DrawingAreaPath( 0, 1, "bedrock-proof", true, 0 );
    area.addStartPoint( 22.0f, 18.0f );
    area.addPoint( 176.0f, 27.0f );
    area.addPoint( 186.0f, 64.0f );
    area.addPoint( 165.0f, 126.0f );
    area.addPoint( 106.0f, 142.0f );
    area.addPoint( 37.0f, 131.0f );
    area.addPoint( 13.0f, 82.0f );
    area.closePath();
    draw( new Canvas( bitmap ), area, new RectF( 0.0f, 0.0f, 200.0f, 150.0f ) );

    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    File dir = context.getExternalFilesDir( "test-artifacts" );
    assertTrue( "No external artifact directory", dir != null );
    assertTrue( "Could not create external artifact directory", dir.exists() || dir.mkdirs() );
    File file = new File( dir, "bedrock-area-pattern.png" );
    FileOutputStream output = new FileOutputStream( file );
    try {
      assertTrue( "Could not encode bedrock visual proof", bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    } finally {
      output.close();
      bitmap.recycle();
    }
  }
}
