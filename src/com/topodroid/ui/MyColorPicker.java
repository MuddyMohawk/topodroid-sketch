/* MyColorPicker.java
 *
 * @author marco corvi
 * @date apr 2026
 *
 * @brief Shared HSV color picker with preset swatches
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.ui;

import com.topodroid.util.TDColor;
import com.topodroid.TDX.R;

import android.os.Bundle;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;

public class MyColorPicker extends MyDialog
                           implements View.OnClickListener
{
  private static final int GRID_COLUMNS         = 8;
  private static final int SAT_VALUE_HEIGHT_DP  = 200;
  private static final int HUE_HEIGHT_DP        = 32;
  private static final int PICKER_CORNER_DP     = 8;
  private static final int PICKER_BORDER_DP     = 1;
  private static final int SWATCH_MIN_HEIGHT_DP = 28;
  private static final int SWATCH_MARGIN_DP     = 4;
  private static final int MARKER_RADIUS_DP     = 10;

  private interface HueChangeListener {
    void onHueChanged( float hue );
  }

  private interface SaturationValueChangeListener {
    void onSaturationValueChanged( float saturation, float value );
  }

  public interface IColorChanged {
    void colorChanged( int color );
  }

  private Button mBtnOk;
  private Button mBtnClear;
  private Button mBtnClose;

  private final IColorChanged mListener;
  private final float[] mHsv = new float[3];

  private int mSelectedColor;

  private View mPreviewSwatch;
  private GridLayout mGridLayout;
  private LinearLayout mHueLayout;
  private LinearLayout mSatValueLayout;
  private HueSliderView mHueSliderView;
  private SaturationValueView mSatValueView;

  private static class HueSliderView extends View
  {
    private final HueChangeListener mListener;
    private final Paint mGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkerOuter   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkerInner   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mCornerRadius;
    private final float mMarkerRadius;
    private float mHue;

    HueSliderView( Context context, float hue, HueChangeListener listener )
    {
      super( context );
      mHue = hue;
      mListener = listener;
      mCornerRadius = dp( context, PICKER_CORNER_DP );
      mMarkerRadius = dp( context, MARKER_RADIUS_DP );

      mBorderPaint.setStyle( Paint.Style.STROKE );
      mBorderPaint.setStrokeWidth( dp( context, PICKER_BORDER_DP ) );
      mBorderPaint.setColor( 0xff555555 );

      mMarkerOuter.setStyle( Paint.Style.STROKE );
      mMarkerOuter.setStrokeWidth( dp( context, 3 ) );
      mMarkerOuter.setColor( TDColor.WHITE );

      mMarkerInner.setStyle( Paint.Style.STROKE );
      mMarkerInner.setStrokeWidth( dp( context, 1 ) );
      mMarkerInner.setColor( TDColor.BLACK );
    }

    void setHue( float hue )
    {
      mHue = clampHue( hue );
      invalidate();
    }

    @Override
    protected void onSizeChanged( int w, int h, int oldw, int oldh )
    {
      super.onSizeChanged( w, h, oldw, oldh );
      mRect.set( 0, 0, w, h );
      mGradientPaint.setShader( new LinearGradient(
          0, 0, w, 0,
          new int[] {
            0xffff0000,
            0xffffff00,
            0xff00ff00,
            0xff00ffff,
            0xff0000ff,
            0xffff00ff,
            0xffff0000
          },
          null,
          Shader.TileMode.CLAMP
      ) );
    }

    @Override
    protected void onDraw( Canvas canvas )
    {
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mGradientPaint );
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mBorderPaint );

      float width = Math.max( 1, getWidth() );
      float cx = (mHue / 360.0f) * width;
      if ( cx >= width ) cx = width - 1;
      float cy = getHeight() * 0.5f;
      canvas.drawCircle( cx, cy, mMarkerRadius, mMarkerOuter );
      canvas.drawCircle( cx, cy, mMarkerRadius - dp( getContext(), 2 ), mMarkerInner );
    }

    @Override
    public boolean onTouchEvent( MotionEvent event )
    {
      switch ( event.getAction() ) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
          updateHueFromTouch( event.getX() );
          return true;
        case MotionEvent.ACTION_UP:
          updateHueFromTouch( event.getX() );
          performClick();
          return true;
        default:
          return super.onTouchEvent( event );
      }
    }

    @Override
    public boolean performClick()
    {
      super.performClick();
      return true;
    }

    private void updateHueFromTouch( float x )
    {
      float width = Math.max( 1, getWidth() );
      float unit = clamp01( x / width );
      float hue = 360.0f * unit;
      if ( hue >= 360.0f ) hue = 359.999f;
      if ( hue != mHue ) {
        mHue = hue;
        invalidate();
        if ( mListener != null ) mListener.onHueChanged( mHue );
      }
    }
  }

  private static class SaturationValueView extends View
  {
    private final SaturationValueChangeListener mListener;
    private final Paint mBasePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSatPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mValuePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkerOuter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMarkerInner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final float mCornerRadius;
    private final float mMarkerRadius;
    private float mHue;
    private float mSaturation;
    private float mValue;

    SaturationValueView( Context context, float hue, float saturation, float value, SaturationValueChangeListener listener )
    {
      super( context );
      mHue = clampHue( hue );
      mSaturation = clamp01( saturation );
      mValue = clamp01( value );
      mListener = listener;
      mCornerRadius = dp( context, PICKER_CORNER_DP );
      mMarkerRadius = dp( context, MARKER_RADIUS_DP );

      mBorderPaint.setStyle( Paint.Style.STROKE );
      mBorderPaint.setStrokeWidth( dp( context, PICKER_BORDER_DP ) );
      mBorderPaint.setColor( 0xff555555 );

      mMarkerOuter.setStyle( Paint.Style.STROKE );
      mMarkerOuter.setStrokeWidth( dp( context, 3 ) );
      mMarkerOuter.setColor( TDColor.WHITE );

      mMarkerInner.setStyle( Paint.Style.STROKE );
      mMarkerInner.setStrokeWidth( dp( context, 1 ) );
      mMarkerInner.setColor( TDColor.BLACK );
    }

    void setColorState( float hue, float saturation, float value )
    {
      mHue = clampHue( hue );
      mSaturation = clamp01( saturation );
      mValue = clamp01( value );
      updateBaseColor();
      invalidate();
    }

    @Override
    protected void onSizeChanged( int w, int h, int oldw, int oldh )
    {
      super.onSizeChanged( w, h, oldw, oldh );
      mRect.set( 0, 0, w, h );
      mSatPaint.setShader( new LinearGradient(
          0, 0, w, 0,
          0xffffffff,
          0x00ffffff,
          Shader.TileMode.CLAMP
      ) );
      mValuePaint.setShader( new LinearGradient(
          0, 0, 0, h,
          0x00000000,
          0xff000000,
          Shader.TileMode.CLAMP
      ) );
      updateBaseColor();
    }

    @Override
    protected void onDraw( Canvas canvas )
    {
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mBasePaint );
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mSatPaint );
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mValuePaint );
      canvas.drawRoundRect( mRect, mCornerRadius, mCornerRadius, mBorderPaint );

      float width = Math.max( 1, getWidth() );
      float height = Math.max( 1, getHeight() );
      float cx = mSaturation * width;
      float cy = (1.0f - mValue) * height;
      if ( cx >= width ) cx = width - 1;
      if ( cy >= height ) cy = height - 1;
      canvas.drawCircle( cx, cy, mMarkerRadius, mMarkerOuter );
      canvas.drawCircle( cx, cy, mMarkerRadius - dp( getContext(), 2 ), mMarkerInner );
    }

    @Override
    public boolean onTouchEvent( MotionEvent event )
    {
      switch ( event.getAction() ) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_MOVE:
          updateFromTouch( event.getX(), event.getY() );
          return true;
        case MotionEvent.ACTION_UP:
          updateFromTouch( event.getX(), event.getY() );
          performClick();
          return true;
        default:
          return super.onTouchEvent( event );
      }
    }

    @Override
    public boolean performClick()
    {
      super.performClick();
      return true;
    }

    private void updateBaseColor()
    {
      mBasePaint.setColor( Color.HSVToColor( new float[] { mHue, 1.0f, 1.0f } ) );
    }

    private void updateFromTouch( float x, float y )
    {
      float width = Math.max( 1, getWidth() );
      float height = Math.max( 1, getHeight() );
      float saturation = clamp01( x / width );
      float value = clamp01( 1.0f - (y / height) );
      if ( saturation != mSaturation || value != mValue ) {
        mSaturation = saturation;
        mValue = value;
        invalidate();
        if ( mListener != null ) mListener.onSaturationValueChanged( mSaturation, mValue );
      }
    }
  }

  private static class MyColorCell extends View
  {
    private final int mColor;

    MyColorCell( Context context, int color, OnClickListener listener )
    {
      super( context );
      mColor = color;
      setOnClickListener( listener );
      setClickable( true );
      setBackgroundDrawable( createSwatchDrawable( context, color ) );
    }

    int getColor() { return mColor; }
  }

  public MyColorPicker( Context context, IColorChanged listener, int initialColor)
  {
    super( context, null, 0 ); // 0 no help
    mListener = listener;
    mSelectedColor = sanitizeColor( initialColor );
    Color.colorToHSV( mSelectedColor, mHsv );
  }

  @Override
  protected void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    initLayout( R.layout.my_color_picker, R.string.title_color_picker );

    mBtnOk    = (Button) findViewById( R.id.btn_ok );
    mBtnClear = (Button) findViewById( R.id.btn_clear );
    mBtnClose = (Button) findViewById( R.id.btn_close );
    mBtnOk.setOnClickListener( this );
    mBtnClear.setOnClickListener( this );
    mBtnClose.setOnClickListener( this );

    mPreviewSwatch = findViewById( R.id.color_preview );
    mSatValueLayout = (LinearLayout) findViewById( R.id.sv_layout );
    mHueLayout = (LinearLayout) findViewById( R.id.hue_layout );
    mGridLayout = (GridLayout) findViewById( R.id.grid_layout );

    mSatValueView = new SaturationValueView( getContext(), mHsv[0], mHsv[1], mHsv[2],
      new SaturationValueChangeListener() {
        @Override
        public void onSaturationValueChanged( float saturation, float value )
        {
          mHsv[1] = saturation;
          mHsv[2] = value;
          updateSelectedColorFromHsv();
        }
      }
    );
    mHueSliderView = new HueSliderView( getContext(), mHsv[0],
      new HueChangeListener() {
        @Override
        public void onHueChanged( float hue )
        {
          mHsv[0] = hue;
          mSatValueView.setColorState( mHsv[0], mHsv[1], mHsv[2] );
          updateSelectedColorFromHsv();
        }
      }
    );

    addPickerViews();
    buildPresetGrid();
    syncPickerState();
  }

  @Override
  public void onClick( View v )
  {
    if ( v.getId() == R.id.btn_ok ) {
      mListener.colorChanged( mSelectedColor );
      dismiss();
    } else if ( v.getId() == R.id.btn_clear ) {
      mListener.colorChanged( 0 );
      dismiss();
    } else if ( v.getId() == R.id.btn_close ) {
      dismiss();
    } else if ( v instanceof MyColorCell ) {
      MyColorCell cell = (MyColorCell)v;
      setSelectedColor( cell.getColor() );
    }
  }

  private void addPickerViews()
  {
    LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp( SAT_VALUE_HEIGHT_DP )
    );
    LinearLayout.LayoutParams hueParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp( HUE_HEIGHT_DP )
    );

    mSatValueLayout.removeAllViews();
    mSatValueLayout.addView( mSatValueView, svParams );

    mHueLayout.removeAllViews();
    mHueLayout.addView( mHueSliderView, hueParams );
  }

  private void buildPresetGrid()
  {
    mGridLayout.removeAllViews();
    mGridLayout.setColumnCount( GRID_COLUMNS );
    mGridLayout.setRowCount( (TDColor.mTDColors.length + GRID_COLUMNS - 1) / GRID_COLUMNS );

    int margin = dp( SWATCH_MARGIN_DP );
    int displayWidth = getContext().getResources().getDisplayMetrics().widthPixels;
    int availableWidth = displayWidth - dp( 40 ) - 2 * margin * GRID_COLUMNS;
    int cellWidth = Math.max( dp( 28 ), availableWidth / GRID_COLUMNS );
    int cellHeight = Math.max( dp( SWATCH_MIN_HEIGHT_DP ), cellWidth / 2 );

    for ( int color : TDColor.mTDColors ) {
      MyColorCell cell = new MyColorCell( mContext, color, this );
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
      lp.width = cellWidth;
      lp.height = cellHeight;
      lp.setMargins( margin, margin, margin, margin );
      mGridLayout.addView( cell, lp );
    }
  }

  private void setSelectedColor( int color )
  {
    mSelectedColor = sanitizeColor( color );
    Color.colorToHSV( mSelectedColor, mHsv );
    syncPickerState();
  }

  private void syncPickerState()
  {
    updatePreviewSwatch();
    if ( mHueSliderView != null ) mHueSliderView.setHue( mHsv[0] );
    if ( mSatValueView != null ) mSatValueView.setColorState( mHsv[0], mHsv[1], mHsv[2] );
  }

  private void updateSelectedColorFromHsv()
  {
    mSelectedColor = Color.HSVToColor( mHsv );
    updatePreviewSwatch();
  }

  private void updatePreviewSwatch()
  {
    mPreviewSwatch.setBackgroundDrawable( createSwatchDrawable( getContext(), mSelectedColor ) );
  }

  private static int sanitizeColor( int color )
  {
    if ( color == 0 ) return TDColor.WHITE;
    return 0xff000000 | ( color & 0x00ffffff );
  }

  private static GradientDrawable createSwatchDrawable( Context context, int color )
  {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape( GradientDrawable.RECTANGLE );
    drawable.setCornerRadius( dp( context, PICKER_CORNER_DP ) );
    drawable.setColor( 0xff000000 | ( color & 0x00ffffff ) );
    drawable.setStroke( dp( context, PICKER_BORDER_DP ), strokeColor( color ) );
    return drawable;
  }

  private static int strokeColor( int color )
  {
    int rgb = 0xff000000 | ( color & 0x00ffffff );
    return isLightColor( rgb ) ? 0xff444444 : 0xffdddddd;
  }

  private static boolean isLightColor( int color )
  {
    int r = Color.red( color );
    int g = Color.green( color );
    int b = Color.blue( color );
    int luma = (299 * r + 587 * g + 114 * b) / 1000;
    return luma >= 186;
  }

  private static float clampHue( float hue )
  {
    if ( hue < 0.0f ) return 0.0f;
    if ( hue >= 360.0f ) return 359.999f;
    return hue;
  }

  private static float clamp01( float value )
  {
    if ( value < 0.0f ) return 0.0f;
    if ( value > 1.0f ) return 1.0f;
    return value;
  }

  private int dp( int value )
  {
    return dp( getContext(), value );
  }

  private static int dp( Context context, int value )
  {
    float density = context.getResources().getDisplayMetrics().density;
    return Math.max( 1, Math.round( density * value ) );
  }
}
