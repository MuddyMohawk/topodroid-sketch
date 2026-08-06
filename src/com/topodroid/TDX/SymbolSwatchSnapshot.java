/* @file SymbolSwatchSnapshot.java
 *
 * @brief Immutable production-rendered swatch used by composite legend layouts
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;
import com.topodroid.prefs.TDSetting;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

final class SymbolSwatchSnapshot
{
  private static final int WIDTH = 256;
  private static final int HEIGHT = 144;
  private static final ColorMatrixColorFilter INVERT_FILTER = new ColorMatrixColorFilter(
    new ColorMatrix( new float[] {
      -1, 0, 0, 0, 255,
      0, -1, 0, 0, 255,
      0, 0, -1, 0, 255,
      0, 0, 0, 1, 0
    } ) );
  private static final ThreadLocal< Paint > DRAW_PAINT = new ThreadLocal< Paint >() {
    @Override protected Paint initialValue() {
      return new Paint( Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG );
    }
  };

  private final Bitmap mBitmap;
  private final float mOrientation;
  private final float mDisplayScale;

  private SymbolSwatchSnapshot( Bitmap bitmap, float orientation, float display_scale )
  {
    mBitmap = bitmap;
    mOrientation = orientation;
    mDisplayScale = display_scale;
  }

  static SymbolSwatchSnapshot create( TitleLegendPointState.Row row,
                                      TitleLegendPointState.SymbolResolver resolver )
  {
    if ( row == null || row.kind == TitleLegendPointState.Kind.CUSTOM || resolver == null ) return null;
    int index = resolver.libraryIndex( row.kind, row.thName );
    if ( index < 0 ) return null;
    int type;
    SymbolInterface symbol;
    if ( row.kind == TitleLegendPointState.Kind.LINE ) {
      type = SymbolType.LINE;
      symbol = BrushManager.getLineByIndex( index );
    } else if ( row.kind == TitleLegendPointState.Kind.AREA ) {
      type = SymbolType.AREA;
      symbol = BrushManager.getAreaByIndex( index );
    } else {
      type = SymbolType.POINT;
      symbol = BrushManager.getPointByIndex( index );
    }
    SketchBrushStyle style = previewStyle( row );
    SymbolPreviewRenderer renderer = SymbolPreviewRenderer.create( type, index, symbol, 1.0f,
      style, row.preview.orientation, row.preview.textValue );
    if ( renderer == null ) return null;
    Bitmap mutable = Bitmap.createBitmap( WIDTH, HEIGHT, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( mutable );
    renderer.draw( canvas, new RectF( 0.0f, 0.0f, WIDTH, HEIGHT ) );
    Bitmap immutable = mutable.copy( Bitmap.Config.ARGB_8888, false );
    if ( immutable != mutable ) mutable.recycle();
    return new SymbolSwatchSnapshot( immutable, 0.0f,
      row.kind == TitleLegendPointState.Kind.POINT ? previewPointScale( row ) : 1.0f );
  }

  static SketchBrushStyle previewStyle( TitleLegendPointState.Row row )
  {
    SketchBrushStyle standard = TDSetting.getSketchStyle( TDSetting.SKETCH_STYLE_STANDARD );
    if ( standard == null ) standard = SketchBrushStyle.of(
      SketchBrushStyle.DEFAULT_WEIGHT_STANDARD, SketchBrushStyle.DEFAULT_POINT_SCALE,
      SketchBrushStyle.DEFAULT_OPACITY );
    float weight = row != null && row.preview.weightMode == TitleLegendPointState.WeightMode.CUSTOM
      ? row.preview.customWeight : standard.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD );
    float opacity = standard.opacityOr( SketchBrushStyle.DEFAULT_OPACITY );
    return standard.hasColor()
      ? SketchBrushStyle.of( weight, 1.0f, opacity, standard.colorOr( 0xffffff ) )
      : SketchBrushStyle.of( weight, 1.0f, opacity );
  }

  static float previewPointScale( TitleLegendPointState.Row row )
  {
    SketchBrushStyle standard = TDSetting.getSketchStyle( TDSetting.SKETCH_STYLE_STANDARD );
    float configured = standard == null ? 1.0f
      : standard.pointScaleOr( SketchBrushStyle.DEFAULT_POINT_SCALE );
    return configured * ( row == null ? 1.0f : row.preview.pointScale );
  }

  void draw( Canvas canvas, RectF destination, int xor_color )
  {
    if ( canvas == null || destination == null || mBitmap == null || mBitmap.isRecycled() ) return;
    Paint paint = DRAW_PAINT.get();
    paint.setColorFilter( xor_color > 0 ? INVERT_FILTER : null );
    int save = canvas.save();
    try {
      canvas.clipRect( destination );
      if ( mDisplayScale != 1.0f ) {
        canvas.scale( mDisplayScale, mDisplayScale, destination.centerX(), destination.centerY() );
      }
      if ( mOrientation != 0.0f ) canvas.rotate( mOrientation, destination.centerX(), destination.centerY() );
      canvas.drawBitmap( mBitmap, null, destination, paint );
    } finally {
      canvas.restoreToCount( save );
    }
  }
}
