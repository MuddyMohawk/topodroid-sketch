/* @file LegendEditorSwatchView.java
 *
 * @brief Lightweight production-symbol preview used by legend editor rows
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

final class LegendEditorSwatchView extends View
{
  private SymbolPreviewRenderer mRenderer;
  private float mOrientation;
  private float mDisplayScale = 1.0f;
  private boolean mUnavailable;
  private final RectF mContent = new RectF();
  private final Paint mMissingPaint = new Paint( Paint.ANTI_ALIAS_FLAG );

  LegendEditorSwatchView( Context context ) { this( context, null ); }
  LegendEditorSwatchView( Context context, AttributeSet attrs )
  {
    super( context, attrs );
    mMissingPaint.setColor( 0xffd88b8b );
    mMissingPaint.setStyle( Paint.Style.STROKE );
    mMissingPaint.setStrokeWidth( Math.max( 1.0f, getResources().getDisplayMetrics().density ) );
  }

  void setRow( TitleLegendPointState.Row row )
  {
    mRenderer = null;
    mOrientation = 0.0f;
    mDisplayScale = 1.0f;
    mUnavailable = false;
    if ( row != null && row.kind != TitleLegendPointState.Kind.CUSTOM ) {
      TitleLegendLayout.InstalledSymbolResolver resolver = new TitleLegendLayout.InstalledSymbolResolver();
      int index = resolver.libraryIndex( row.kind, row.thName );
      if ( index >= 0 ) {
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
          mOrientation = 0.0f;
          mDisplayScale = SymbolSwatchSnapshot.previewPointScale( row );
        }
        mRenderer = SymbolPreviewRenderer.create( type, index, symbol,
          getResources().getDisplayMetrics().density, SymbolSwatchSnapshot.previewStyle( row ),
          row.preview.orientation, row.preview.textValue );
      }
      mUnavailable = mRenderer == null;
    }
    invalidate();
  }

  @Override protected void onDraw( Canvas canvas )
  {
    super.onDraw( canvas );
    float inset = Math.max( 2.0f, getResources().getDisplayMetrics().density * 3.0f );
    mContent.set( inset, inset, getWidth() - inset, getHeight() - inset );
    if ( mRenderer != null ) {
      int save = canvas.save();
      try {
        canvas.clipRect( mContent );
        if ( mDisplayScale != 1.0f ) {
          canvas.scale( mDisplayScale, mDisplayScale, mContent.centerX(), mContent.centerY() );
        }
        if ( mOrientation != 0.0f ) canvas.rotate( mOrientation, mContent.centerX(), mContent.centerY() );
        mRenderer.draw( canvas, mContent );
      } finally {
        canvas.restoreToCount( save );
      }
    } else if ( mUnavailable ) {
      canvas.drawLine( mContent.left, mContent.top, mContent.right, mContent.bottom, mMissingPaint );
      canvas.drawLine( mContent.right, mContent.top, mContent.left, mContent.bottom, mMissingPaint );
    }
  }
}
