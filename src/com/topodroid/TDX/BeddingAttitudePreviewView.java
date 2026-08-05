/* @file BeddingAttitudePreviewView.java
 *
 * @brief Transactional in-dialog preview of a bedding-attitude glyph
 */
package com.topodroid.TDX;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public final class BeddingAttitudePreviewView extends View
{
  private final Paint mPaint = new Paint( Paint.ANTI_ALIAS_FLAG );
  private BeddingAttitudePointState mState;

  public BeddingAttitudePreviewView( Context context )
  {
    this( context, null );
  }

  public BeddingAttitudePreviewView( Context context, AttributeSet attributes )
  {
    super( context, attributes );
    mPaint.setStyle( Paint.Style.STROKE );
    mPaint.setStrokeWidth( 1.5f * getResources().getDisplayMetrics().density );
  }

  void setState( BeddingAttitudePointState state )
  {
    mState = state;
    invalidate();
  }

  void setInkColor( int color )
  {
    mPaint.setColor( color );
    invalidate();
  }

  @Override protected void onDraw( Canvas canvas )
  {
    super.onDraw( canvas );
    if ( mState == null ) return;
    float available = Math.max( 1.0f, Math.min( getWidth(), getHeight() ) );
    float margin = 2.0f * getResources().getDisplayMetrics().density
      + 0.5f * Math.abs( mPaint.getStrokeWidth() );
    float unit_radius = BeddingAttitudePointRenderer.contentRadius( mState, 1.0f );
    float scale = Math.max( 0.01f, ( available - 2.0f * margin )
      / ( 2.0f * Math.max( 1.0f, unit_radius ) ) );
    BeddingAttitudePointRenderer.drawState( canvas, 0.5f * getWidth(), 0.5f * getHeight(),
      mPaint, mState, scale, 0 );
  }
}
