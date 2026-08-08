/* @file CaverPointRenderer.java
 *
 * @brief True-scale bitmap renderer for the caver special point
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

final class CaverPointRenderer implements SpecialPointRenderer
{
  // Ratios of the cropped, non-transparent artwork. They keep selection and
  // preview bounds correct even if Android cannot decode a resource.
  static final float MAN_ASPECT = 490.0f / 1272.0f;
  static final float WOMAN_ASPECT = 430.0f / 1237.0f;

  private static final Object ARTWORK_LOCK = new Object();
  private static volatile Artwork sMan;
  private static volatile Artwork sWoman;

  private static final ColorMatrixColorFilter INVERT_FILTER = new ColorMatrixColorFilter(
    new ColorMatrix( new float[] {
      -1, 0, 0, 0, 255,
      0, -1, 0, 0, 255,
      0, 0, -1, 0, 255,
      0, 0, 0, 1, 0
    } ) );

  private static final ThreadLocal< Paint > DRAW_PAINT = new ThreadLocal< Paint >() {
    @Override protected Paint initialValue()
    {
      return new Paint( Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG );
    }
  };
  private static final ThreadLocal< RectF > DRAW_RECT = new ThreadLocal< RectF >() {
    @Override protected RectF initialValue() { return new RectF(); }
  };

  static final class Prepared
  {
    final CaverPointState.Variant variant;
    final Artwork artwork;

    Prepared( CaverPointState.Variant variant, Artwork artwork )
    {
      this.variant = variant;
      this.artwork = artwork;
    }
  }

  private static final class Artwork
  {
    final Bitmap bitmap;
    final float aspect;

    Artwork( Bitmap bitmap, float fallback_aspect )
    {
      this.bitmap = bitmap;
      this.aspect = bitmap != null && bitmap.getHeight() > 0
        ? (float)bitmap.getWidth() / bitmap.getHeight() : fallback_aspect;
    }
  }

  static Prepared prepare( CaverPointState state )
  {
    CaverPointState.Variant variant = state == null ? CaverPointState.Variant.MAN : state.variant;
    return new Prepared( variant, artwork( variant ) );
  }

  @Override public void draw( DrawingSemanticPointPath point, Canvas canvas, int xor_color )
  {
    if ( point == null || canvas == null || ! ( point.specialState() instanceof CaverPointState ) ) return;
    CaverPointState state = (CaverPointState)point.specialState();
    Prepared prepared = prepared( point, state );
    if ( prepared.artwork.bitmap == null || prepared.artwork.bitmap.isRecycled() ) return;

    float height = sceneHeight( state );
    float width = height * prepared.artwork.aspect;
    RectF destination = DRAW_RECT.get();
    destination.set( point.cx - 0.5f * width, point.cy - height,
                     point.cx + 0.5f * width, point.cy );

    Paint paint = DRAW_PAINT.get();
    Paint source = point.specialPointPaint();
    paint.setAlpha( source == null ? 255 : source.getAlpha() );
    paint.setColorFilter( xor_color > 0 ? INVERT_FILTER : null );
    canvas.drawBitmap( prepared.artwork.bitmap, null, destination, paint );
  }

  @Override public RectF sceneBounds( DrawingSemanticPointPath point )
  {
    if ( point == null || ! ( point.specialState() instanceof CaverPointState ) ) return null;
    CaverPointState state = (CaverPointState)point.specialState();
    Prepared prepared = prepared( point, state );
    float height = sceneHeight( state );
    float half_width = 0.5f * height * prepared.artwork.aspect;
    return new RectF( point.cx - half_width, point.cy - height,
                      point.cx + half_width, point.cy );
  }

  private static float sceneHeight( CaverPointState state )
  {
    double meters = state == null ? CaverPointState.DEFAULT_HEIGHT_METERS : state.heightMeters;
    return (float)( meters * DrawingUtil.SCALE_FIX );
  }

  private static Prepared prepared( DrawingSemanticPointPath point, CaverPointState state )
  {
    Object value = point.preparedSpecialState();
    if ( value instanceof Prepared && ((Prepared)value).variant == state.variant ) return (Prepared)value;
    return prepare( state );
  }

  private static Artwork artwork( CaverPointState.Variant variant )
  {
    if ( variant == CaverPointState.Variant.WOMAN ) {
      Artwork cached = sWoman;
      if ( cached != null ) return cached;
      synchronized ( ARTWORK_LOCK ) {
        if ( sWoman == null ) sWoman = load( R.drawable.caver_woman, WOMAN_ASPECT );
        return sWoman;
      }
    }
    Artwork cached = sMan;
    if ( cached != null ) return cached;
    synchronized ( ARTWORK_LOCK ) {
      if ( sMan == null ) sMan = load( R.drawable.caver_man, MAN_ASPECT );
      return sMan;
    }
  }

  private static Artwork load( int resource, float fallback_aspect )
  {
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inScaled = false;
      return new Artwork( BitmapFactory.decodeResource( TDInstance.getResources(), resource, options ),
                          fallback_aspect );
    } catch ( RuntimeException e ) {
      TDLog.e( "Caver artwork decode failed: " + e.getMessage() );
      return new Artwork( null, fallback_aspect );
    }
  }
}
