/* @file SymbolPreviewButton.java
 *
 * @brief Clickable canvas-accurate symbol preview
 * --------------------------------------------------------
 * Copyright This software is distributed under GPL-3.0 or later
 * See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.ui.ItemButton;
import com.topodroid.types.SymbolType;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;

/** ItemButton-compatible host for production-rendered symbol previews. */
public class SymbolPreviewButton extends ItemButton
{
  private static final float FIXED_BOX_HEIGHT_DP = 48.0f;
  private static final float FIXED_LINE_BOX_WIDTH_DP = 96.0f;
  private static final float FIXED_SQUARE_BOX_WIDTH_DP = 48.0f;

  private SymbolPreviewRenderer mRenderer;

  public SymbolPreviewButton( Context context )
  {
    super( context );
    initPreview();
  }

  public SymbolPreviewButton( Context context, AttributeSet attrs )
  {
    super( context, attrs );
    initPreview();
  }

  public SymbolPreviewButton( Context context, AttributeSet attrs, int style )
  {
    super( context, attrs, style );
    initPreview();
  }

  private void initPreview()
  {
    setPadding( 0, 0, 0, 0 );
    setMinimumWidth( 0 );
    setMinimumHeight( 0 );
    setMinWidth( 0 );
    setMinHeight( 0 );
  }

  void bind( int symbol_type, int library_index, SymbolInterface symbol )
  {
    float density = getResources().getDisplayMetrics().density;
    mRenderer = SymbolPreviewRenderer.create( symbol_type, library_index, symbol, density );
    setContentDescription( symbol == null ? null : symbol.getName() );
    invalidate();
  }

  /** Fixed readable dimensions for picker, palette, and symbol-management rows. */
  static int fixedBoxWidthPx( Context context, int symbol_type )
  {
    float width = symbol_type == SymbolType.LINE ? FIXED_LINE_BOX_WIDTH_DP : FIXED_SQUARE_BOX_WIDTH_DP;
    return Math.round( width * context.getResources().getDisplayMetrics().density );
  }

  static int fixedBoxHeightPx( Context context )
  {
    return Math.round( FIXED_BOX_HEIGHT_DP * context.getResources().getDisplayMetrics().density );
  }

  @Override public void onDraw( Canvas canvas )
  {
    if ( mRenderer == null || canvas == null ) return;
    mRenderer.draw( canvas, new RectF( 0.0f, 0.0f, getWidth(), getHeight() ) );
  }
}
