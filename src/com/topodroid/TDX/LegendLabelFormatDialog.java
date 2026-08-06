/* @file LegendLabelFormatDialog.java
 *
 * @brief Shared-label typography editor for legend rows
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyColorPicker;
import com.topodroid.ui.MyDialog;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

final class LegendLabelFormatDialog extends MyDialog implements MyColorPicker.IColorChanged
{
  interface Listener { void onStyleChanged( SketchTextStyle style ); }

  private final SketchTextStyle mInitial;
  private final Listener mListener;
  private Spinner mFont;
  private Spinner mSizeMode;
  private EditText mSize;
  private EditText mWeight;
  private CheckBox mBold;
  private CheckBox mItalic;
  private CheckBox mUnderline;
  private Button mColorButton;
  private SeekBar mOpacity;
  private SketchTextPreviewView mPreview;
  private int mColor;

  LegendLabelFormatDialog( android.content.Context context, SketchTextStyle style, Listener listener )
  {
    super( context, null, 0 );
    mInitial = style == null ? SketchTextStyle.defaultStyle() : style;
    mListener = listener;
    mColor = mInitial.color();
  }

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    LinearLayout root = new LinearLayout( getContext() );
    root.setOrientation( LinearLayout.VERTICAL );
    root.setPadding( dp( 16 ), dp( 12 ), dp( 16 ), dp( 10 ) );
    root.setBackgroundColor( 0xff17191e );

    mPreview = new SketchTextPreviewView( getContext() );
    root.addView( mPreview,
      new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, dp( 92 ) ) );

    mFont = spinner( root, R.string.text_font, SketchFontRegistry.fontLabels(),
      Math.max( 0, SketchFontRegistry.indexOf( mInitial.fontId() ) ) );
    mSizeMode = spinner( root, R.string.ceiling_text_size,
      new String[] { getContext().getString( R.string.text_size_auto_grid ),
                     getContext().getString( R.string.text_size_world ) },
      mInitial.sizeMode() == SketchTextStyle.SizeMode.WORLD ? 1 : 0 );
    mSize = field( root, R.string.ceiling_text_size, Float.toString( mInitial.height() ) );
    mWeight = field( root, R.string.text_line_weight, Float.toString( mInitial.lineWeight() ) );
    mBold = check( root, R.string.text_bold, mInitial.bold() );
    mItalic = check( root, R.string.text_italic, mInitial.italic() );
    mUnderline = check( root, R.string.text_underline, mInitial.underline() );

    mColorButton = new Button( getContext() );
    mColorButton.setText( R.string.text_color );
    mColorButton.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { new MyColorPicker( getContext(), LegendLabelFormatDialog.this, mColor ).show(); }
    } );
    root.addView( mColorButton );
    mOpacity = new SeekBar( getContext() );
    mOpacity.setMax( 255 );
    mOpacity.setProgress( Color.alpha( mColor ) );
    root.addView( mOpacity );
    updateColorButton();
    installLivePreviewListeners();
    updatePreview();

    LinearLayout footer = new LinearLayout( getContext() );
    Button cancel = new Button( getContext() );
    cancel.setText( R.string.button_cancel );
    Button save = new Button( getContext() );
    save.setText( R.string.button_save );
    footer.addView( cancel, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    footer.addView( save, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    root.addView( footer );
    cancel.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { dismiss(); }
    } );
    save.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { save(); }
    } );
    initLayout( root, R.string.title_legend_label_format );
    setCanceledOnTouchOutside( false );
  }

  @Override public void colorChanged( int color )
  {
    mColor = Color.argb( Color.alpha( mColor ), Color.red( color ), Color.green( color ), Color.blue( color ) );
    updateColorButton();
    updatePreview();
  }

  private void save()
  {
    try {
      float size = Float.parseFloat( mSize.getText().toString() );
      float weight = Float.parseFloat( mWeight.getText().toString() );
      if ( ! Float.isFinite( size ) || size <= 0.0f
          || ! Float.isFinite( weight ) || weight <= 0.0f ) throw new NumberFormatException();
      SketchTextStyle.SizeMode size_mode = mSizeMode.getSelectedItemPosition() == 1
        ? SketchTextStyle.SizeMode.WORLD : SketchTextStyle.SizeMode.AUTO_GRID;
      int color = Color.argb( mOpacity.getProgress(), Color.red( mColor ), Color.green( mColor ), Color.blue( mColor ) );
      String font = SketchFontRegistry.fontIdAt( mFont.getSelectedItemPosition() );
      SketchTextStyle style = SketchTextStyle.of( font, size_mode, size, weight,
        mBold.isChecked(), mItalic.isChecked(), mUnderline.isChecked(),
        SketchTextStyle.Alignment.LEFT, color );
      if ( mListener != null ) mListener.onStyleChanged( style );
      dismiss();
    } catch ( NumberFormatException e ) {
      Toast.makeText( getContext(), R.string.illegal_value, Toast.LENGTH_LONG ).show();
    }
  }

  private void installLivePreviewListeners()
  {
    AdapterView.OnItemSelectedListener selected = new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected( AdapterView<?> parent, View view, int position, long id ) {
        updatePreview();
      }
      @Override public void onNothingSelected( AdapterView<?> parent ) { }
    };
    mFont.setOnItemSelectedListener( selected );
    mSizeMode.setOnItemSelectedListener( selected );
    TextWatcher watcher = new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence s, int start, int before, int count ) { updatePreview(); }
      @Override public void afterTextChanged( Editable editable ) { }
    };
    mSize.addTextChangedListener( watcher );
    mWeight.addTextChangedListener( watcher );
    CompoundButton.OnCheckedChangeListener checked = new CompoundButton.OnCheckedChangeListener() {
      @Override public void onCheckedChanged( CompoundButton button, boolean value ) { updatePreview(); }
    };
    mBold.setOnCheckedChangeListener( checked );
    mItalic.setOnCheckedChangeListener( checked );
    mUnderline.setOnCheckedChangeListener( checked );
    mOpacity.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar seek, int progress, boolean from_user ) { updatePreview(); }
      @Override public void onStartTrackingTouch( SeekBar seek ) { }
      @Override public void onStopTrackingTouch( SeekBar seek ) { }
    } );
  }

  private void updatePreview()
  {
    if ( mPreview == null || mFont == null || mSizeMode == null || mSize == null || mWeight == null
        || mBold == null || mItalic == null || mUnderline == null || mOpacity == null ) return;
    float size = parsePositive( mSize, mInitial.height() );
    float weight = parsePositive( mWeight, mInitial.lineWeight() );
    SketchTextStyle.SizeMode mode = mSizeMode.getSelectedItemPosition() == 1
      ? SketchTextStyle.SizeMode.WORLD : SketchTextStyle.SizeMode.AUTO_GRID;
    int color = Color.argb( mOpacity.getProgress(), Color.red( mColor ), Color.green( mColor ), Color.blue( mColor ) );
    SketchTextStyle style = SketchTextStyle.of(
      SketchFontRegistry.fontIdAt( mFont.getSelectedItemPosition() ), mode, size, weight,
      mBold.isChecked(), mItalic.isChecked(), mUnderline.isChecked(),
      SketchTextStyle.Alignment.LEFT, color );
    mPreview.setPreview( getContext().getString( R.string.title_legend_preview_sample ), style );
  }

  private static float parsePositive( EditText field, float fallback )
  {
    try {
      float value = Float.parseFloat( field.getText().toString() );
      return value > 0.0f && Float.isFinite( value ) ? value : fallback;
    } catch ( NumberFormatException e ) {
      return fallback;
    }
  }

  private Spinner spinner( LinearLayout root, int label, String[] values, int selected )
  {
    addLabel( root, label );
    Spinner spinner = new Spinner( getContext() );
    spinner.setAdapter( new ArrayAdapter<>( getContext(), android.R.layout.simple_spinner_dropdown_item, values ) );
    spinner.setSelection( Math.max( 0, Math.min( values.length - 1, selected ) ) );
    root.addView( spinner );
    return spinner;
  }

  private EditText field( LinearLayout root, int label, String value )
  {
    addLabel( root, label );
    EditText field = new EditText( getContext() );
    field.setSingleLine( true );
    field.setTextColor( Color.WHITE );
    field.setText( value );
    root.addView( field );
    return field;
  }

  private CheckBox check( LinearLayout root, int label, boolean checked )
  {
    CheckBox check = new CheckBox( getContext() );
    check.setText( label );
    check.setTextColor( Color.WHITE );
    check.setChecked( checked );
    root.addView( check );
    return check;
  }

  private void addLabel( LinearLayout root, int label )
  {
    TextView view = new TextView( getContext() );
    view.setText( label );
    view.setTextColor( 0xffc8ccd4 );
    view.setPadding( 0, dp( 7 ), 0, 0 );
    root.addView( view );
  }

  private void updateColorButton()
  {
    if ( mColorButton == null ) return;
    int opaque = Color.rgb( Color.red( mColor ), Color.green( mColor ), Color.blue( mColor ) );
    mColorButton.setBackgroundColor( opaque );
    int brightness = Color.red( opaque ) * 299 + Color.green( opaque ) * 587 + Color.blue( opaque ) * 114;
    mColorButton.setTextColor( brightness > 186000 ? Color.BLACK : Color.WHITE );
  }

  private int dp( int value ) { return Math.round( value * getContext().getResources().getDisplayMetrics().density ); }
}
