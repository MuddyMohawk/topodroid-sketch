/* @file CeilingHeightPointEditorController.java
 *
 * @brief Point-dialog controls for ceiling height and optional water depth
 */
package com.topodroid.TDX;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

final class CeilingHeightPointEditorController implements SpecialPointEditorController
{
  private final DrawingWindow mParent;
  private final DrawingSemanticPointPath mPoint;
  private CheckBox mWaterEnabled;
  private EditText mWaterDepth;
  private Spinner mFont;
  private CheckBox mBold;
  private CheckBox mItalic;
  private CheckBox mUnderline;
  private SeekBar mTextScale;
  private TextView mTextScaleValue;

  CeilingHeightPointEditorController( DrawingWindow parent, DrawingSemanticPointPath point )
  {
    mParent = parent;
    mPoint = point;
  }

  @Override public void bind( LinearLayout container, EditText primary_text )
  {
    if ( container == null || primary_text == null
        || ! ( mPoint.specialState() instanceof CeilingHeightPointState ) ) return;
    CeilingHeightPointState state = (CeilingHeightPointState)mPoint.specialState();
    primary_text.setHint( R.string.ceiling_height );
    primary_text.setSingleLine( true );
    primary_text.setInputType( InputType.TYPE_CLASS_TEXT );

    View root = LayoutInflater.from( mParent ).inflate( R.layout.drawing_ceiling_height_editor, container, false );
    container.addView( root );
    mWaterEnabled = (CheckBox)root.findViewById( R.id.ceiling_water_enabled );
    mWaterDepth = (EditText)root.findViewById( R.id.ceiling_water_depth );
    mFont = (Spinner)root.findViewById( R.id.ceiling_text_font );
    mBold = (CheckBox)root.findViewById( R.id.ceiling_text_bold );
    mItalic = (CheckBox)root.findViewById( R.id.ceiling_text_italic );
    mUnderline = (CheckBox)root.findViewById( R.id.ceiling_text_underline );
    mTextScale = (SeekBar)root.findViewById( R.id.ceiling_text_scale );
    mTextScaleValue = (TextView)root.findViewById( R.id.ceiling_text_scale_value );

    ArrayAdapter< String > fonts = new ArrayAdapter<>(
      mParent, android.R.layout.simple_spinner_item, SketchFontRegistry.fontLabels() );
    fonts.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
    mFont.setAdapter( fonts );
    mFont.setSelection( SketchFontRegistry.indexOf( state.fontId() ) );
    mBold.setChecked( state.bold() );
    mItalic.setChecked( state.italic() );
    mUnderline.setChecked( state.underline() );
    mWaterEnabled.setChecked( state.waterEnabled );
    mWaterDepth.setText( state.waterDepth );
    showWaterField( state.waterEnabled, false );

    int progress = state.textScalePercent() - CeilingHeightPointState.MIN_TEXT_SCALE;
    mTextScale.setMax( CeilingHeightPointState.MAX_TEXT_SCALE - CeilingHeightPointState.MIN_TEXT_SCALE );
    mTextScale.setProgress( progress );
    updateScaleLabel( state.textScalePercent() );
    mTextScale.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar seek_bar, int value, boolean from_user )
      {
        updateScaleLabel( CeilingHeightPointState.MIN_TEXT_SCALE + value );
      }
      @Override public void onStartTrackingTouch( SeekBar seek_bar ) { }
      @Override public void onStopTrackingTouch( SeekBar seek_bar ) { }
    } );
    mWaterEnabled.setOnClickListener( view -> showWaterField( mWaterEnabled.isChecked(), true ) );
  }

  @Override public void apply()
  {
    if ( mWaterEnabled == null ) return;
    String font_id = SketchFontRegistry.fontIdAt( mFont.getSelectedItemPosition() );
    int scale = CeilingHeightPointState.MIN_TEXT_SCALE + mTextScale.getProgress();
    CeilingHeightPointState state = new CeilingHeightPointState(
      mWaterEnabled.isChecked(), mWaterDepth.getText().toString(), font_id,
      mBold.isChecked(), mItalic.isChecked(), mUnderline.isChecked(), scale );
    mPoint.setSpecialState( state, true );

    SketchTextStyle defaults = mParent.loadTextObjectDefault()
      .withFontId( font_id )
      .withEmphasis( state.bold(), state.italic(), state.underline() );
    mParent.rememberTextObjectDefault( defaults );
  }

  private void showWaterField( boolean visible, boolean focus )
  {
    if ( mWaterDepth == null ) return;
    mWaterDepth.setVisibility( visible ? View.VISIBLE : View.GONE );
    if ( visible && focus ) {
      mWaterDepth.requestFocus();
      mWaterDepth.setSelection( 0, mWaterDepth.length() );
      mWaterDepth.post( () -> {
        InputMethodManager keyboard = (InputMethodManager)mParent.getSystemService( Context.INPUT_METHOD_SERVICE );
        if ( keyboard != null ) keyboard.showSoftInput( mWaterDepth, InputMethodManager.SHOW_IMPLICIT );
      } );
    }
  }

  private void updateScaleLabel( int percent )
  {
    if ( mTextScaleValue != null ) mTextScaleValue.setText( percent + "%" );
  }
}
