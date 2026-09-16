package com.topodroid.TDX;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Locale;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class PngFastWriterInstrumentedTest
{
  @Test
  public void encodedPng_preservesTransparentAndOpaquePixels() throws Exception
  {
    assertRoundTrip( makeSample( 257, 193, true ) );
    assertRoundTrip( makeSample( 257, 193, false ) );
  }

  @Test
  public void benchmarkLineArt_reportsFastAndPlatformTimes() throws Exception
  {
    Bitmap transparent = makeSample( 4096, 4096, true );
    Bitmap opaque = makeSample( 4096, 4096, false );
    benchmark( "transparent", transparent );
    benchmark( "opaque", opaque );
    transparent.recycle();
    opaque.recycle();
  }

  private static Bitmap makeSample( int width, int height, boolean alpha )
  {
    Bitmap bitmap = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( bitmap );
    if ( ! alpha ) {
      canvas.drawColor( Color.BLACK );
      bitmap.setHasAlpha( false );
    }
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStrokeWidth( Math.max( 1f, width / 512f ) );
    paint.setColor( alpha ? 0x80f04020 : 0xfff04020 );
    for ( int y = 7; y < height; y += Math.max( 11, height / 31 ) ) {
      canvas.drawLine( 3, y, width - 4, Math.min( height - 1, y + height / 17f ), paint );
    }
    paint.setColor( 0xff40c0f0 );
    canvas.drawCircle( width * 0.67f, height * 0.42f, Math.min( width, height ) * 0.13f, paint );
    return bitmap;
  }

  private static void assertRoundTrip( Bitmap source ) throws Exception
  {
    ByteArrayOutputStream fastOutput = new ByteArrayOutputStream();
    assertTrue( PngFastWriter.write( source, fastOutput ) );
    Bitmap decoded = BitmapFactory.decodeByteArray( fastOutput.toByteArray(), 0, fastOutput.size() );
    ByteArrayOutputStream platformOutput = new ByteArrayOutputStream();
    assertTrue( source.compress( Bitmap.CompressFormat.PNG, 100, platformOutput ) );
    Bitmap platform = BitmapFactory.decodeByteArray( platformOutput.toByteArray(), 0, platformOutput.size() );
    assertTrue( decoded != null && platform != null );
    assertTrue( source.getWidth() == decoded.getWidth() && source.getWidth() == platform.getWidth() );
    assertTrue( source.getHeight() == decoded.getHeight() && source.getHeight() == platform.getHeight() );
    int[] expected = new int[source.getWidth() * source.getHeight()];
    int[] actual = new int[expected.length];
    platform.getPixels( expected, 0, platform.getWidth(), 0, 0, platform.getWidth(), platform.getHeight() );
    decoded.getPixels( actual, 0, decoded.getWidth(), 0, 0, decoded.getWidth(), decoded.getHeight() );
    assertArrayEquals( expected, actual );
    source.recycle();
    decoded.recycle();
    platform.recycle();
  }

  private static void benchmark( String name, Bitmap bitmap ) throws Exception
  {
    CountingOutputStream warmup = new CountingOutputStream();
    PngFastWriter.write( bitmap, warmup );

    CountingOutputStream platformOutput = new CountingOutputStream();
    long start = SystemClock.elapsedRealtimeNanos();
    assertTrue( bitmap.compress( Bitmap.CompressFormat.PNG, 100, platformOutput ) );
    long platformNs = SystemClock.elapsedRealtimeNanos() - start;

    CountingOutputStream fastOutput = new CountingOutputStream();
    start = SystemClock.elapsedRealtimeNanos();
    assertTrue( PngFastWriter.write( bitmap, fastOutput ) );
    long fastNs = SystemClock.elapsedRealtimeNanos() - start;

    System.out.println( String.format( Locale.US,
      "PNG_PERF %s %dx%d platform %.1f ms/%d bytes fast %.1f ms/%d bytes speedup %.2fx",
      name, bitmap.getWidth(), bitmap.getHeight(), platformNs / 1e6, platformOutput.mCount,
      fastNs / 1e6, fastOutput.mCount, (double)platformNs / fastNs ) );
  }

  private static final class CountingOutputStream extends OutputStream
  {
    long mCount = 0;

    @Override public void write( int value ) { ++mCount; }
    @Override public void write( byte[] bytes, int offset, int length ) { mCount += length; }
  }
}
