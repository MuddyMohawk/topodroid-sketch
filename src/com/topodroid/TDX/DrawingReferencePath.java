/* @file DrawingReferencePath.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief TopoDroid sketch reference-image point
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import java.io.PrintWriter;

class DrawingReferencePath extends DrawingPointPath
{
  private static final int MAX_BITMAP_DIM = 4096;

  private final Matrix mImageMatrix = new Matrix();
  private final Paint mBitmapPaint = new Paint( Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG );
  private final PointF[] mCorners = new PointF[] { new PointF(), new PointF(), new PointF(), new PointF() };
  private Bitmap mBitmap = null;
  private String mBitmapSource = null;

  DrawingReferencePath( int type, float x, float y, int scale, int scrap )
  {
    super( type, x, y, scale, scrap );
    refreshBounds();
  }

  DrawingReferencePath( int type, float x, float y, int scale, String text, String options, int scrap )
  {
    super( type, x, y, scale, text, options, scrap );
    refreshBounds();
  }

  String getSourceName()
  {
    return ReferencePointHelper.getSourceName( this );
  }

  float getSceneWidth()
  {
    return ReferencePointHelper.getSceneWidth( this );
  }

  float getSceneHeight()
  {
    return ReferencePointHelper.getSceneHeight( this );
  }

  float getAlpha()
  {
    return ReferencePointHelper.getAlpha( this );
  }

  int getAlphaPercent()
  {
    return ReferencePointHelper.getAlphaPercent( this );
  }

  boolean isReferenceVisible()
  {
    return ReferencePointHelper.isVisible( this );
  }

  void setSceneSize( float width, float height )
  {
    ReferencePointHelper.setSceneSize( this, width, height );
    refreshBounds();
  }

  void setReferenceVisible( boolean visible )
  {
    ReferencePointHelper.setVisible( this, visible );
  }

  void setReferenceAlpha( float alpha )
  {
    ReferencePointHelper.setAlpha( this, alpha );
  }

  void setSourceName( String source_name )
  {
    ReferencePointHelper.setSourceName( this, source_name );
    recycleBitmap();
  }

  void replaceSource( String source_name, int pixel_width, int pixel_height, float max_scene_width, float max_scene_height )
  {
    setSourceName( source_name );
    PointF size = ReferencePointHelper.fitSceneSize( pixel_width, pixel_height, max_scene_width, max_scene_height );
    setSceneSize( size.x, size.y );
  }

  RectF getReferenceBounds()
  {
    return new RectF( left, top, right, bottom );
  }

  boolean intersectsEraseDisk( float x, float y, float radius )
  {
    if ( radius < 0.0f ) radius = 0.0f;
    float width = getSceneWidth();
    float height = getSceneHeight();
    if ( width <= 0.0f || height <= 0.0f ) return false;

    double rad = Math.toRadians( mOrientation );
    float cos = (float)Math.cos( rad );
    float sin = (float)Math.sin( rad );
    float dx = x - cx;
    float dy = y - cy;
    float local_x =   dx * cos + dy * sin;
    float local_y = - dx * sin + dy * cos;
    float qx = Math.abs( local_x ) - width / 2.0f;
    float qy = Math.abs( local_y ) - height / 2.0f;
    float outside_x = Math.max( qx, 0.0f );
    float outside_y = Math.max( qy, 0.0f );
    return outside_x * outside_x + outside_y * outside_y <= radius * radius;
  }

  void copyOutlinePath( Path outline )
  {
    if ( outline == null ) return;
    ReferencePointHelper.getCorners( cx, cy, getSceneWidth(), getSceneHeight(), mOrientation, mCorners );
    outline.rewind();
    outline.moveTo( mCorners[0].x, mCorners[0].y );
    outline.lineTo( mCorners[1].x, mCorners[1].y );
    outline.lineTo( mCorners[2].x, mCorners[2].y );
    outline.lineTo( mCorners[3].x, mCorners[3].y );
    outline.close();
  }

  @Override
  void shiftBy( float dx, float dy )
  {
    super.shiftBy( dx, dy );
    refreshBounds();
  }

  @Override
  void scaleBy( float z, Matrix m )
  {
    float width = getSceneWidth();
    float height = getSceneHeight();
    super.scaleBy( z, m );
    ReferencePointHelper.setSceneSize( this, width * z, height * z );
    refreshBounds();
  }

  @Override
  void affineTransformBy( float[] mm, Matrix m )
  {
    float width = getSceneWidth();
    float height = getSceneHeight();
    PointF center = mapPoint( mm, cx, cy );
    PointF x_axis = mapPoint( mm,
      cx + (float)( Math.cos( Math.toRadians( mOrientation ) ) * width / 2.0 ),
      cy + (float)( Math.sin( Math.toRadians( mOrientation ) ) * width / 2.0 )
    );
    PointF y_axis = mapPoint( mm,
      cx - (float)( Math.sin( Math.toRadians( mOrientation ) ) * height / 2.0 ),
      cy + (float)( Math.cos( Math.toRadians( mOrientation ) ) * height / 2.0 )
    );

    super.affineTransformBy( mm, m );

    float ux = x_axis.x - center.x;
    float uy = x_axis.y - center.y;
    float vx = y_axis.x - center.x;
    float vy = y_axis.y - center.y;
    float new_width = 2.0f * (float)Math.sqrt( ux * ux + uy * uy );
    float new_height = 2.0f * (float)Math.sqrt( vx * vx + vy * vy );
    setOrientation( Math.atan2( uy, ux ) * 180.0 / Math.PI );
    ReferencePointHelper.setSceneSize( this,
      Math.max( ReferencePointHelper.MIN_SIZE, new_width ),
      Math.max( ReferencePointHelper.MIN_SIZE, new_height )
    );
    refreshBounds();
  }

  @Override
  void setOrientation( double angle )
  {
    super.setOrientation( angle );
    refreshBounds();
  }

  @Override
  public void computeBounds( RectF bound, boolean b )
  {
    bound.set( left, top, right, bottom );
  }

  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox )
  {
    // Reference anchors stay hidden in the normal sketch pass.
  }

  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
  {
    // Reference anchors stay hidden in the normal sketch pass.
  }

  void drawUnderlay( Canvas canvas, Matrix matrix, RectF bbox )
  {
    if ( canvas == null || matrix == null || ! isReferenceVisible() ) return;
    if ( bbox != null && ( right < bbox.left || left > bbox.right || bottom < bbox.top || top > bbox.bottom ) ) return;

    Bitmap bitmap = getBitmap();
    if ( bitmap == null ) return;

    float width = getSceneWidth();
    float height = getSceneHeight();
    if ( width <= 0.0f || height <= 0.0f ) return;

    mImageMatrix.reset();
    mImageMatrix.postScale( width / bitmap.getWidth(), height / bitmap.getHeight() );
    mImageMatrix.postTranslate( - width / 2.0f, - height / 2.0f );
    mImageMatrix.postRotate( (float)mOrientation );
    mImageMatrix.postTranslate( cx, cy );
    mImageMatrix.postConcat( matrix );

    mBitmapPaint.setAlpha( Math.round( 255.0f * getAlpha() ) );
    canvas.drawBitmap( bitmap, mImageMatrix, mBitmapPaint );
  }

  void applyHandleDrag( SelectionPoint handle, float dx, float dy )
  {
    if ( handle == null ) return;
    switch ( handle.getHandleRole() ) {
      case ReferencePointHelper.HANDLE_MOVE:
        shiftBy( dx, dy );
        break;
      case ReferencePointHelper.HANDLE_SCALE_NW:
      case ReferencePointHelper.HANDLE_SCALE_NE:
      case ReferencePointHelper.HANDLE_SCALE_SE:
      case ReferencePointHelper.HANDLE_SCALE_SW:
        scaleFromCorner( handle, dx, dy );
        break;
      case ReferencePointHelper.HANDLE_ROTATE:
        rotateFromHandle( handle, dx, dy );
        break;
      default:
        shiftBy( dx, dy );
        break;
    }
  }

  @Override
  String toTherion()
  {
    // Reference images are screen-only underlays and intentionally skip structured exports.
    return null;
  }

  @Override
  void toTCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind )
  {
    // Reference images are screen-only underlays and intentionally skip structured exports.
  }

  @Override
  void toTCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind, String extra, PlotInfo section )
  {
    // Reference images are screen-only underlays and intentionally skip structured exports.
  }

  @Override
  void toCave3D( PrintWriter pw, int type, DrawingCommandManager cmd, com.topodroid.num.TDNum num )
  {
    // Reference images are screen-only underlays and intentionally skip structured exports.
  }

  @Override
  void toCave3D( PrintWriter pw, int type, com.topodroid.math.TDVector V1, com.topodroid.math.TDVector V2 )
  {
    // Reference images are screen-only underlays and intentionally skip structured exports.
  }

  void deleteOwnedAsset()
  {
    ReferencePointHelper.deleteOwnedAsset( this );
  }

  void primeBitmap()
  {
    getBitmap();
  }

  private void refreshBounds()
  {
    ReferencePointHelper.getCorners( cx, cy, getSceneWidth(), getSceneHeight(), mOrientation, mCorners );
    left = right = mCorners[0].x;
    top = bottom = mCorners[0].y;
    for ( int k = 1; k < mCorners.length; ++k ) {
      if ( mCorners[k].x < left ) left = mCorners[k].x;
      if ( mCorners[k].x > right ) right = mCorners[k].x;
      if ( mCorners[k].y < top ) top = mCorners[k].y;
      if ( mCorners[k].y > bottom ) bottom = mCorners[k].y;
    }
  }

  private void scaleFromCorner( SelectionPoint handle, float dx, float dy )
  {
    if ( handle.mPoint == null ) return;
    float vx = handle.mPoint.x - cx;
    float vy = handle.mPoint.y - cy;
    float len0 = (float)Math.sqrt( vx * vx + vy * vy );
    if ( len0 < 0.01f ) return;
    float nx = handle.mPoint.x + dx - cx;
    float ny = handle.mPoint.y + dy - cy;
    float len1 = (float)Math.sqrt( nx * nx + ny * ny );
    if ( len1 < 0.01f ) return;
    float factor = len1 / len0;
    setSceneSize( Math.max( ReferencePointHelper.MIN_SIZE, getSceneWidth() * factor ),
                  Math.max( ReferencePointHelper.MIN_SIZE, getSceneHeight() * factor ) );
  }

  private void rotateFromHandle( SelectionPoint handle, float dx, float dy )
  {
    if ( handle.mPoint == null ) return;
    float ax0 = handle.mPoint.x - cx;
    float ay0 = handle.mPoint.y - cy;
    float ax1 = handle.mPoint.x + dx - cx;
    float ay1 = handle.mPoint.y + dy - cy;
    double ang0 = Math.atan2( ay0, ax0 );
    double ang1 = Math.atan2( ay1, ax1 );
    setOrientation( mOrientation + ( ang1 - ang0 ) * 180.0 / Math.PI );
  }

  private Bitmap getBitmap()
  {
    String source_name = getSourceName();
    if ( source_name == null ) return null;
    if ( mBitmap != null && source_name.equals( mBitmapSource ) && ! mBitmap.isRecycled() ) return mBitmap;

    recycleBitmap();
    String path = ReferencePointHelper.getSurveyAssetPath( source_name );
    if ( path == null ) return null;

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile( path, options );
    if ( options.outWidth <= 0 || options.outHeight <= 0 ) return null;

    int sample = 1;
    while ( options.outWidth / sample > MAX_BITMAP_DIM || options.outHeight / sample > MAX_BITMAP_DIM ) {
      sample *= 2;
    }
    options.inJustDecodeBounds = false;
    options.inSampleSize = sample;
    try {
      mBitmap = BitmapFactory.decodeFile( path, options );
      mBitmapSource = ( mBitmap == null ) ? null : source_name;
    } catch ( OutOfMemoryError e ) {
      TDLog.e( "reference image oom " + e.getMessage() );
      mBitmap = null;
      mBitmapSource = null;
    }
    return mBitmap;
  }

  private void recycleBitmap()
  {
    if ( mBitmap != null && ! mBitmap.isRecycled() ) mBitmap.recycle();
    mBitmap = null;
    mBitmapSource = null;
  }

  private PointF mapPoint( float[] mm, float x, float y )
  {
    return new PointF( mm[0] * x + mm[1] * y + mm[2],
                       mm[3] * x + mm[4] * y + mm[5] );
  }
}
