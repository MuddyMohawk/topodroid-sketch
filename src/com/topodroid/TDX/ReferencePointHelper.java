/* @file ReferencePointHelper.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief Helpers for sketch reference-image underlays
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDFile;
import com.topodroid.util.TDLog;
import com.topodroid.util.TDsafUri;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

class ReferencePointHelper
{
  static final String OPTION_REF_SRC   = "-refsrc";
  static final String OPTION_REF_W     = "-refw";
  static final String OPTION_REF_H     = "-refh";
  static final String OPTION_REF_ALPHA = "-refalpha";
  static final String OPTION_REF_SHOW  = "-refshow";

  static final int HANDLE_NONE      = 0;
  static final int HANDLE_MOVE      = 1;
  static final int HANDLE_SCALE_NW  = 2;
  static final int HANDLE_SCALE_NE  = 3;
  static final int HANDLE_SCALE_SE  = 4;
  static final int HANDLE_SCALE_SW  = 5;
  static final int HANDLE_ROTATE    = 6;

  static final float DEFAULT_ALPHA = 0.60f;
  static final float MIN_SIZE = 20.0f;
  static final float ROTATE_HANDLE_GAP = 48.0f;

  static class ImportedImage
  {
    final String mSourceName;
    final int mPixelWidth;
    final int mPixelHeight;

    ImportedImage( String source_name, int pixel_width, int pixel_height )
    {
      mSourceName = source_name;
      mPixelWidth = pixel_width;
      mPixelHeight = pixel_height;
    }
  }

  static boolean isReference( DrawingPath path )
  {
    return path instanceof DrawingReferencePath;
  }

  static boolean isReference( DrawingPointPath point )
  {
    return point instanceof DrawingReferencePath;
  }

  static String getSourceName( DrawingPointPath point )
  {
    return ( point == null ) ? null : point.getOption( OPTION_REF_SRC );
  }

  static float getSceneWidth( DrawingPointPath point )
  {
    return getOptionFloat( point, OPTION_REF_W, 0.0f );
  }

  static float getSceneHeight( DrawingPointPath point )
  {
    return getOptionFloat( point, OPTION_REF_H, 0.0f );
  }

  static float getAlpha( DrawingPointPath point )
  {
    float value = getOptionFloat( point, OPTION_REF_ALPHA, DEFAULT_ALPHA * 100.0f );
    if ( value > 1.0f ) value /= 100.0f;
    if ( value < 0.0f ) value = 0.0f;
    if ( value > 1.0f ) value = 1.0f;
    return value;
  }

  static int getAlphaPercent( DrawingPointPath point )
  {
    return Math.round( getAlpha( point ) * 100.0f );
  }

  static boolean isVisible( DrawingPointPath point )
  {
    if ( point == null ) return false;
    String value = point.getOption( OPTION_REF_SHOW );
    return value == null || ! "0".equals( value );
  }

  static void setSourceName( DrawingPointPath point, String source_name )
  {
    if ( point == null ) return;
    if ( source_name == null || source_name.length() == 0 ) {
      point.removeOption( OPTION_REF_SRC );
    } else {
      point.setOption( OPTION_REF_SRC, source_name );
    }
  }

  static void setSceneSize( DrawingPointPath point, float width, float height )
  {
    if ( point == null ) return;
    point.setOption( OPTION_REF_W, formatFloat( Math.max( MIN_SIZE, width ) ) );
    point.setOption( OPTION_REF_H, formatFloat( Math.max( MIN_SIZE, height ) ) );
  }

  static void setAlpha( DrawingPointPath point, float alpha )
  {
    if ( point == null ) return;
    if ( alpha < 0.0f ) alpha = 0.0f;
    if ( alpha > 1.0f ) alpha = 1.0f;
    point.setOption( OPTION_REF_ALPHA, Integer.toString( Math.round( alpha * 100.0f ) ) );
  }

  static void setVisible( DrawingPointPath point, boolean visible )
  {
    if ( point == null ) return;
    point.setOption( OPTION_REF_SHOW, visible ? "1" : "0" );
  }

  static RectF getSceneBounds( DrawingPointPath point )
  {
    float width = getSceneWidth( point );
    float height = getSceneHeight( point );
    PointF[] corners = getCorners( point.cx, point.cy, width, height, point.mOrientation );
    RectF bounds = new RectF( corners[0].x, corners[0].y, corners[0].x, corners[0].y );
    for ( int k = 1; k < corners.length; ++k ) {
      if ( corners[k].x < bounds.left ) bounds.left = corners[k].x;
      if ( corners[k].x > bounds.right ) bounds.right = corners[k].x;
      if ( corners[k].y < bounds.top ) bounds.top = corners[k].y;
      if ( corners[k].y > bounds.bottom ) bounds.bottom = corners[k].y;
    }
    return bounds;
  }

  static PointF[] getCorners( float cx, float cy, float width, float height, double angle )
  {
    PointF[] corners = new PointF[4];
    corners[0] = new PointF();
    corners[1] = new PointF();
    corners[2] = new PointF();
    corners[3] = new PointF();
    getCorners( cx, cy, width, height, angle, corners );
    return corners;
  }

  static void getCorners( float cx, float cy, float width, float height, double angle, PointF[] corners )
  {
    if ( corners == null || corners.length < 4 ) return;
    for ( int k = 0; k < 4; ++k ) {
      if ( corners[k] == null ) corners[k] = new PointF();
    }
    float half_w = width / 2.0f;
    float half_h = height / 2.0f;
    setLocalPoint( corners[0], cx, cy, -half_w, -half_h, angle );
    setLocalPoint( corners[1], cx, cy,  half_w, -half_h, angle );
    setLocalPoint( corners[2], cx, cy,  half_w,  half_h, angle );
    setLocalPoint( corners[3], cx, cy, -half_w,  half_h, angle );
  }

  static PointF getSelectionPoint( DrawingReferencePath path, int role, float u, float v )
  {
    PointF point = new PointF();
    getSelectionPoint( path, role, u, v, point );
    return point;
  }

  static void getSelectionPoint( DrawingReferencePath path, int role, float u, float v, PointF point )
  {
    if ( point == null ) return;
    if ( role == HANDLE_ROTATE ) {
      float local_y = - path.getSceneHeight() / 2.0f - ROTATE_HANDLE_GAP;
      setLocalPoint( point, path.cx, path.cy, 0.0f, local_y, path.mOrientation );
      return;
    }
    float local_x = u * path.getSceneWidth();
    float local_y = v * path.getSceneHeight();
    setLocalPoint( point, path.cx, path.cy, local_x, local_y, path.mOrientation );
  }

  static PointF fitSceneSize( int pixel_width, int pixel_height, float max_width, float max_height )
  {
    if ( pixel_width <= 0 || pixel_height <= 0 ) {
      return new PointF( Math.max( MIN_SIZE, max_width ), Math.max( MIN_SIZE, max_height ) );
    }
    float width = max_width;
    float height = width * pixel_height / (float)pixel_width;
    if ( height > max_height ) {
      height = max_height;
      width = height * pixel_width / (float)pixel_height;
    }
    if ( width < MIN_SIZE ) {
      width = MIN_SIZE;
      height = width * pixel_height / (float)pixel_width;
    }
    if ( height < MIN_SIZE ) {
      height = MIN_SIZE;
      width = height * pixel_width / (float)pixel_height;
    }
    return new PointF( width, height );
  }

  static ImportedImage importImage( Uri uri )
  {
    if ( uri == null || TDInstance.survey == null ) return null;
    String ext = normalizeImageExtension( uri );
    if ( ext == null ) return null;

    String source_name = UUID.randomUUID().toString().replace( "-", "" ) + ext;
    String path = getSurveyAssetPath( source_name );
    if ( path == null ) return null;

    byte[] buffer = new byte[8192];
    ContentResolver resolver = TDInstance.getContentResolver();
    try {
      InputStream input = resolver.openInputStream( uri );
      if ( input == null ) return null;
      FileOutputStream output = TDFile.getFileOutputStream( path );
      if ( output == null ) {
        input.close();
        return null;
      }
      try {
        int read;
        while ( ( read = input.read( buffer ) ) >= 0 ) {
          if ( read > 0 ) output.write( buffer, 0, read );
        }
      } finally {
        try { output.close(); } catch ( IOException e ) { TDLog.e( e.getMessage() ); }
        try { input.close(); } catch ( IOException e ) { TDLog.e( e.getMessage() ); }
      }
    } catch ( IOException e ) {
      TDLog.e( "reference image copy failed " + e.getMessage() );
      TDFile.deleteFile( path );
      return null;
    }

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile( path, options );
    if ( options.outWidth <= 0 || options.outHeight <= 0 ) {
      TDFile.deleteFile( path );
      return null;
    }
    return new ImportedImage( source_name, options.outWidth, options.outHeight );
  }

  static void deleteOwnedAsset( DrawingPointPath point )
  {
    deleteAssetByName( getSourceName( point ) );
  }

  static void deleteAssetByName( String source_name )
  {
    String path = getSurveyAssetPath( source_name );
    if ( path != null ) TDFile.deleteFile( path );
  }

  static String getSurveyAssetPath( String source_name )
  {
    if ( source_name == null || source_name.length() == 0 || TDInstance.survey == null ) return null;
    String lower = source_name.toLowerCase( Locale.US );
    if ( lower.endsWith( ".png" ) ) return TDPath.getSurveyPngFile( TDInstance.survey, source_name.substring( 0, source_name.length() - 4 ) );
    if ( lower.endsWith( ".jpg" ) ) return TDPath.getSurveyJpgFile( TDInstance.survey, source_name.substring( 0, source_name.length() - 4 ) );
    return null;
  }

  private static float getOptionFloat( DrawingPointPath point, String key, float def_value )
  {
    if ( point == null ) return def_value;
    String value = point.getOption( key );
    if ( value == null ) return def_value;
    try {
      return Float.parseFloat( value );
    } catch ( NumberFormatException e ) {
      return def_value;
    }
  }

  private static String formatFloat( float value )
  {
    return String.format( Locale.US, "%.2f", value );
  }

  private static PointF getLocalPoint( float cx, float cy, float local_x, float local_y, double angle )
  {
    PointF point = new PointF();
    setLocalPoint( point, cx, cy, local_x, local_y, angle );
    return point;
  }

  private static void setLocalPoint( PointF point, float cx, float cy, float local_x, float local_y, double angle )
  {
    if ( point == null ) return;
    double rad = angle * Math.PI / 180.0;
    float cos = (float)Math.cos( rad );
    float sin = (float)Math.sin( rad );
    point.x = cx + local_x * cos - local_y * sin;
    point.y = cy + local_x * sin + local_y * cos;
  }

  private static String normalizeImageExtension( Uri uri )
  {
    String ext = extensionFromMime( TDsafUri.getDocumentType( uri ) );
    if ( ext != null ) return ext;

    ext = extensionFromName( getDisplayName( uri ) );
    if ( ext != null ) return ext;

    ext = extensionFromName( uri.getLastPathSegment() );
    if ( ext != null ) return ext;

    String mime = TDsafUri.getDocumentType( uri );
    if ( mime != null && mime.toLowerCase( Locale.US ).startsWith( "image/" ) ) return ".png";
    return null;
  }

  private static String extensionFromMime( String mime )
  {
    if ( mime == null ) return null;
    mime = mime.toLowerCase( Locale.US );
    if ( mime.contains( "png" ) ) return ".png";
    if ( mime.contains( "jpeg" ) || mime.contains( "jpg" ) ) return ".jpg";
    return null;
  }

  private static String extensionFromName( String name )
  {
    if ( name == null ) return null;
    String lower = name.toLowerCase( Locale.US );
    if ( lower.endsWith( ".png" ) ) return ".png";
    if ( lower.endsWith( ".jpg" ) || lower.endsWith( ".jpeg" ) ) return ".jpg";
    return null;
  }

  private static String getDisplayName( Uri uri )
  {
    Cursor cursor = null;
    try {
      cursor = TDInstance.getContentResolver().query( uri,
        new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null );
      if ( cursor != null && cursor.moveToFirst() ) {
        int column = cursor.getColumnIndex( OpenableColumns.DISPLAY_NAME );
        if ( column >= 0 ) return cursor.getString( column );
      }
    } catch ( Exception e ) {
      TDLog.e( "reference image name failed " + e.getMessage() );
    } finally {
      if ( cursor != null ) cursor.close();
    }
    return null;
  }
}
