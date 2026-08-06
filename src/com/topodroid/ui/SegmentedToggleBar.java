/* @file SegmentedToggleBar.java
 *
 * @brief Reusable two-slot segmented control for modern editor surfaces
 */
package com.topodroid.ui;

import com.topodroid.util.TDColor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;

public class SegmentedToggleBar extends LinearLayout
{
  public interface OnSelectionChangedListener { void onSelectionChanged( int index ); }

  private final RadioButton[] mSegments = new RadioButton[2];
  private int mSelected = 0;
  private OnSelectionChangedListener mListener;

  public SegmentedToggleBar( Context context ) { this( context, null ); }

  public SegmentedToggleBar( Context context, AttributeSet attrs )
  {
    super( context, attrs );
    setOrientation( HORIZONTAL );
    setPadding( dp( 2 ), dp( 2 ), dp( 2 ), dp( 2 ) );
    for ( int i = 0; i < mSegments.length; ++i ) {
      final int index = i;
      RadioButton segment = new RadioButton( context );
      segment.setButtonDrawable( null );
      segment.setGravity( Gravity.CENTER );
      segment.setTextSize( 15.0f );
      segment.setMinHeight( dp( 48 ) );
      segment.setClickable( true );
      segment.setFocusable( true );
      segment.setOnClickListener( new OnClickListener() {
        @Override public void onClick( View view ) { setSelectedIndex( index, true ); }
      } );
      addView( segment, new LayoutParams( 0, LayoutParams.WRAP_CONTENT, 1.0f ) );
      mSegments[i] = segment;
    }
    updateAppearance();
  }

  public void setLabels( CharSequence left, CharSequence right )
  {
    mSegments[0].setText( left );
    mSegments[1].setText( right );
    mSegments[0].setContentDescription( left );
    mSegments[1].setContentDescription( right );
  }

  public void setOnSelectionChangedListener( OnSelectionChangedListener listener )
  {
    mListener = listener;
  }

  public void setSelectedIndex( int index ) { setSelectedIndex( index, false ); }

  public int selectedIndex() { return mSelected; }

  private void setSelectedIndex( int index, boolean notify )
  {
    int selected = index <= 0 ? 0 : 1;
    if ( selected == mSelected ) return;
    mSelected = selected;
    updateAppearance();
    if ( notify && mListener != null ) mListener.onSelectionChanged( mSelected );
  }

  private void updateAppearance()
  {
    GradientDrawable rail = new GradientDrawable();
    rail.setColor( 0xff252932 );
    rail.setCornerRadius( dp( 10 ) );
    rail.setStroke( dp( 1 ), 0xff4d5360 );
    setBackground( rail );
    for ( int i = 0; i < mSegments.length; ++i ) {
      boolean selected = i == mSelected;
      GradientDrawable background = new GradientDrawable();
      background.setColor( selected ? TDColor.SKETCH_TOGGLE_ON : Color.TRANSPARENT );
      background.setCornerRadius( dp( 8 ) );
      mSegments[i].setBackground( background );
      mSegments[i].setTextColor( selected ? TDColor.SKETCH_TOGGLE_ON_TEXT : TDColor.SKETCH_TOGGLE_OFF_TEXT );
      mSegments[i].setChecked( selected );
      mSegments[i].setSelected( selected );
    }
  }

  private int dp( int value )
  {
    return Math.round( value * getResources().getDisplayMetrics().density );
  }
}
