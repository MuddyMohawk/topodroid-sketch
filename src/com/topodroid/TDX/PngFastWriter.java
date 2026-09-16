/* @file PngFastWriter.java
 *
 * @author MuddyMohawk
 * @date sep 2026
 *
 * @brief Fast, lossless PNG writer for large sketch exports
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import android.graphics.Bitmap;

/** A deliberately simple PNG encoder optimized for the very large, mostly
 *  line-art bitmaps produced by sketch export. Android's Bitmap PNG encoder
 *  uses a fixed compression policy and ignores its quality argument. This
 *  writer uses zlib's fast lossless mode and streams scanlines, trading a
 *  somewhat larger file for substantially less export time.
 */
final class PngFastWriter
{
  private static final byte[] PNG_SIGNATURE = {
    (byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };
  private static final byte[] IHDR = { 0x49, 0x48, 0x44, 0x52 };
  private static final byte[] IDAT = { 0x49, 0x44, 0x41, 0x54 };
  private static final byte[] IEND = { 0x49, 0x45, 0x4e, 0x44 };
  private static final int IDAT_CHUNK_SIZE = 128 * 1024;
  private static final int PIXEL_BATCH_BYTES = 1024 * 1024;

  private PngFastWriter() {}

  /** Writes bitmap as an 8-bit RGB or RGBA PNG without closing output. */
  static boolean write( Bitmap bitmap, OutputStream output ) throws IOException
  {
    if ( bitmap == null || output == null || bitmap.isRecycled() ) return false;
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    if ( width <= 0 || height <= 0 ) return false;

    boolean alpha = bitmap.hasAlpha();
    int channels = alpha ? 4 : 3;
    long row_size = 1L + (long)width * channels;
    if ( row_size > Integer.MAX_VALUE ) return false;

    DataOutputStream png = new DataOutputStream( output );
    png.write( PNG_SIGNATURE );
    writeHeader( png, width, height, alpha );

    Deflater deflater = new Deflater( Deflater.BEST_SPEED );
    deflater.setStrategy( Deflater.FILTERED );
    IdatOutputStream idat = new IdatOutputStream( png );
    DeflaterOutputStream compressed = new DeflaterOutputStream( idat, deflater, IDAT_CHUNK_SIZE );
    try {
      int rows_per_batch = Math.min( height, Math.max( 1, PIXEL_BATCH_BYTES / Math.max( 1, width * 4 ) ) );
      int[] pixels = new int[ width * rows_per_batch ];
      byte[] row = new byte[ (int)row_size ];
      row[0] = 1; // PNG Sub filter: especially effective for blank canvas spans

      for ( int top = 0; top < height; top += rows_per_batch ) {
        int rows = Math.min( rows_per_batch, height - top );
        bitmap.getPixels( pixels, 0, width, 0, top, width, rows );
        for ( int y = 0; y < rows; ++y ) {
          packRow( pixels, y * width, width, alpha, row );
          applySubFilter( row, channels );
          compressed.write( row );
        }
      }
      compressed.finish();
      idat.finish();
      writeChunk( png, IEND, null, 0 );
      return true;
    } finally {
      deflater.end();
    }
  }

  private static void writeHeader( DataOutputStream png, int width, int height, boolean alpha ) throws IOException
  {
    byte[] header = new byte[13];
    putInt( header, 0, width );
    putInt( header, 4, height );
    header[8] = 8;                 // bit depth
    header[9] = (byte)( alpha ? 6 : 2 ); // RGBA or RGB
    // compression, filter and interlace methods remain zero
    writeChunk( png, IHDR, header, header.length );
  }

  private static void packRow( int[] pixels, int offset, int width, boolean alpha, byte[] row )
  {
    int dst = 1;
    for ( int x = 0; x < width; ++x ) {
      int color = pixels[offset + x];
      row[dst++] = (byte)( color >>> 16 );
      row[dst++] = (byte)( color >>> 8 );
      row[dst++] = (byte)color;
      if ( alpha ) row[dst++] = (byte)( color >>> 24 );
    }
  }

  /** Filter in reverse so the preceding unfiltered byte is still available. */
  private static void applySubFilter( byte[] row, int channels )
  {
    for ( int i = row.length - 1; i > channels; --i ) {
      row[i] = (byte)( row[i] - row[i - channels] );
    }
  }

  private static void putInt( byte[] bytes, int offset, int value )
  {
    bytes[offset]     = (byte)( value >>> 24 );
    bytes[offset + 1] = (byte)( value >>> 16 );
    bytes[offset + 2] = (byte)( value >>> 8 );
    bytes[offset + 3] = (byte)value;
  }

  private static void writeChunk( DataOutputStream png, byte[] type, byte[] data, int length ) throws IOException
  {
    png.writeInt( length );
    png.write( type );
    if ( length > 0 ) png.write( data, 0, length );
    CRC32 crc = new CRC32();
    crc.update( type );
    if ( length > 0 ) crc.update( data, 0, length );
    png.writeInt( (int)crc.getValue() );
  }

  /** Turns one zlib stream into a sequence of bounded PNG IDAT chunks. */
  private static final class IdatOutputStream extends OutputStream
  {
    private final DataOutputStream mPng;
    private final byte[] mBuffer = new byte[IDAT_CHUNK_SIZE];
    private int mCount = 0;

    IdatOutputStream( DataOutputStream png )
    {
      mPng = png;
    }

    @Override
    public void write( int value ) throws IOException
    {
      if ( mCount == mBuffer.length ) flushChunk();
      mBuffer[mCount++] = (byte)value;
    }

    @Override
    public void write( byte[] bytes, int offset, int length ) throws IOException
    {
      while ( length > 0 ) {
        if ( mCount == mBuffer.length ) flushChunk();
        int copy = Math.min( length, mBuffer.length - mCount );
        System.arraycopy( bytes, offset, mBuffer, mCount, copy );
        mCount += copy;
        offset += copy;
        length -= copy;
      }
    }

    void finish() throws IOException
    {
      if ( mCount > 0 ) flushChunk();
    }

    private void flushChunk() throws IOException
    {
      writeChunk( mPng, IDAT, mBuffer, mCount );
      mCount = 0;
    }
  }
}
