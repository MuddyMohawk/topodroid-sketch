/* @file FramedTextTypographyEditor.java
 *
 * @brief Shared font, emphasis, and relative-size controls for framed text points
 */
package com.topodroid.TDX;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

final class FramedTextTypographyEditor
{
  private Spinner mFont;
  private CheckBox mBold;
  private CheckBox mItalic;
  private CheckBox mUnderline;
  private SeekBar mTextScale;
  private TextView mTextScaleValue;
  private int mMinimumScale;

  void bind( DrawingWindow parent, View root, FramedTextPointState state,
             int minimum_scale, int maximum_scale )
  {
    if ( parent == null || root == null || state == null ) return;
    mMinimumScale = minimum_scale;
    mFont = (Spinner)root.findViewById( R.id.special_text_font );
    mBold = (CheckBox)root.findViewById( R.id.special_text_bold );
    mItalic = (CheckBox)root.findViewById( R.id.special_text_italic );
    mUnderline = (CheckBox)root.findViewById( R.id.special_text_underline );
    mTextScale = (SeekBar)root.findViewById( R.id.special_text_scale );
    mTextScaleValue = (TextView)root.findViewById( R.id.special_text_scale_value );
    if ( mFont == null || mBold == null || mItalic == null || mUnderline == null
        || mTextScale == null || mTextScaleValue == null ) return;

    ArrayAdapter< String > fonts = new ArrayAdapter<>(
      parent, android.R.layout.simple_spinner_item, SketchFontRegistry.fontLabels() );
    fonts.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
    mFont.setAdapter( fonts );
    mFont.setSelection( SketchFontRegistry.indexOf( state.fontId() ) );
    mBold.setChecked( state.bold() );
    mItalic.setChecked( state.italic() );
    mUnderline.setChecked( state.underline() );

    int bounded_maximum = Math.max( minimum_scale, maximum_scale );
    int percent = Math.max( minimum_scale, Math.min( bounded_maximum, state.textScalePercent() ) );
    mTextScale.setMax( bounded_maximum - minimum_scale );
    mTextScale.setProgress( percent - minimum_scale );
    updateScaleLabel( percent );
    mTextScale.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar seek_bar, int value, boolean from_user )
      {
        updateScaleLabel( mMinimumScale + value );
      }
      @Override public void onStartTrackingTouch( SeekBar seek_bar ) { }
      @Override public void onStopTrackingTouch( SeekBar seek_bar ) { }
    } );
  }

  boolean isBound() { return mFont != null && mTextScale != null; }
  String fontId() { return SketchFontRegistry.fontIdAt( mFont.getSelectedItemPosition() ); }
  boolean bold() { return mBold.isChecked(); }
  boolean italic() { return mItalic.isChecked(); }
  boolean underline() { return mUnderline.isChecked(); }
  int textScalePercent() { return mMinimumScale + mTextScale.getProgress(); }

  void rememberAsTextDefault( DrawingWindow parent )
  {
    if ( parent == null || ! isBound() ) return;
    SketchTextStyle defaults = parent.loadTextObjectDefault()
      .withFontId( fontId() )
      .withEmphasis( bold(), italic(), underline() );
    parent.rememberTextObjectDefault( defaults );
  }

  private void updateScaleLabel( int percent )
  {
    if ( mTextScaleValue != null ) mTextScaleValue.setText( percent + "%" );
  }
}
