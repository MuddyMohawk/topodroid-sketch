/* @file DrawingTextDialog.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Create and edit a full Sketch text object
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;
import com.topodroid.ui.MyColorPicker;
import com.topodroid.ui.MyDialog;
import com.topodroid.ui.MyOrientationWidget;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

class DrawingTextDialog extends MyDialog
                        implements View.OnClickListener, MyColorPicker.IColorChanged
{
  private final DrawingWindow mParent;
  private final DrawingLabelPath mLabel;
  private final float mX;
  private final float mY;
  private final boolean mEditing;

  private SketchTextStyle mInitialStyle;
  private int mInitialFontIndex;
  private boolean mFontChoiceChanged;
  private SketchTextStyle.SizeMode mSizeMode;
  private int mColor;
  private boolean mChangingMode;

  private EditText mText;
  private Spinner mFont;
  private CheckBox mBold;
  private CheckBox mItalic;
  private CheckBox mUnderline;
  private RadioButton mAlignLeft;
  private RadioButton mAlignCenter;
  private RadioButton mAlignRight;
  private CheckBox mAutoSize;
  private CheckBox mScaleWithSketch;
  private EditText mSize;
  private TextView mSizeUnit;
  private EditText mLineWeight;
  private Button mColorButton;
  private SeekBar mOpacity;
  private EditText mOptions;
  private SketchTextPreviewView mPreview;
  private MyOrientationWidget mOrientationWidget;

  private CheckBox mCBbase;
  private CheckBox mCBfloor;
  private CheckBox mCBfill;
  private CheckBox mCBceil;
  private CheckBox mCBarti;

  DrawingTextDialog( Context context, DrawingWindow parent, float x, float y )
  {
    super( context, null, R.string.DrawingLabelDialog );
    mParent = parent;
    mLabel = null;
    mX = x;
    mY = y;
    mEditing = false;
  }

  DrawingTextDialog( Context context, DrawingWindow parent, DrawingLabelPath label )
  {
    super( context, null, R.string.DrawingLabelDialog );
    mParent = parent;
    mLabel = label;
    mX = label.cx;
    mY = label.cy;
    mEditing = true;
  }

  @Override
  protected void onCreate( Bundle saved_instance_state )
  {
    super.onCreate( saved_instance_state );
    initLayout( R.layout.drawing_label_dialog, R.string.label_title );

    bindViews();
    mInitialStyle = mEditing ? mLabel.getTextStyleForEditor() : mParent.loadTextObjectDefault();
    if ( mInitialStyle == null ) mInitialStyle = SketchTextStyle.defaultStyle();
    mSizeMode = mInitialStyle.sizeMode();
    mColor = mInitialStyle.color();

    ArrayAdapter<String> adapter = new ArrayAdapter<>(
      mContext, android.R.layout.simple_spinner_item, SketchFontRegistry.fontLabels() );
    adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
    mFont.setAdapter( adapter );

    populateInitialValues();
    attachListeners();
    updateModeControls();
    updatePreview();
  }

  private void bindViews()
  {
    mText = (EditText)findViewById( R.id.label_text );
    mFont = (Spinner)findViewById( R.id.text_font );
    mBold = (CheckBox)findViewById( R.id.text_bold );
    mItalic = (CheckBox)findViewById( R.id.text_italic );
    mUnderline = (CheckBox)findViewById( R.id.text_underline );
    mAlignLeft = (RadioButton)findViewById( R.id.text_align_left );
    mAlignCenter = (RadioButton)findViewById( R.id.text_align_center );
    mAlignRight = (RadioButton)findViewById( R.id.text_align_right );
    mAutoSize = (CheckBox)findViewById( R.id.text_auto_size );
    mScaleWithSketch = (CheckBox)findViewById( R.id.text_scale_with_sketch );
    mSize = (EditText)findViewById( R.id.text_size );
    mSizeUnit = (TextView)findViewById( R.id.text_size_unit );
    mLineWeight = (EditText)findViewById( R.id.text_line_weight );
    mColorButton = (Button)findViewById( R.id.text_color );
    mOpacity = (SeekBar)findViewById( R.id.text_opacity );
    mOptions = (EditText)findViewById( R.id.text_options );
    mPreview = (SketchTextPreviewView)findViewById( R.id.text_preview );

    ((Button)findViewById( R.id.label_ok )).setOnClickListener( this );
    ((Button)findViewById( R.id.label_cancel )).setOnClickListener( this );
    mColorButton.setOnClickListener( this );
  }

  private void populateInitialValues()
  {
    mText.setText( mEditing ? mLabel.getPointText() : "" );
    mInitialFontIndex = SketchFontRegistry.indexOf( mInitialStyle.fontId() );
    mFont.setSelection( mInitialFontIndex );
    mBold.setChecked( mInitialStyle.bold() );
    mItalic.setChecked( mInitialStyle.italic() );
    mUnderline.setChecked( mInitialStyle.underline() );
    if ( mInitialStyle.alignment() == SketchTextStyle.Alignment.CENTER ) {
      mAlignCenter.setChecked( true );
    } else if ( mInitialStyle.alignment() == SketchTextStyle.Alignment.RIGHT ) {
      mAlignRight.setChecked( true );
    } else {
      mAlignLeft.setChecked( true );
    }
    float displayed_height = ( mInitialStyle.sizeMode() == SketchTextStyle.SizeMode.WORLD )
      ? mInitialStyle.height() * TDSetting.mUnitLength
      : mInitialStyle.height();
    mSize.setText( formatSize( displayed_height ) );
    mLineWeight.setText( formatSize( mInitialStyle.lineWeight() ) );
    mOpacity.setProgress( Color.alpha( mColor ) );
    updateColorButton();
    mOptions.setText( mEditing ? mLabel.getPublicOptions() : "" );

    mOrientationWidget = new MyOrientationWidget(
      this, true, mEditing ? mLabel.mOrientation : mParent.textObjectDefaultOrientation() );

    if ( TDSetting.mWithLevels > 1 ) {
      initializeLevels( mEditing ? mLabel.mLevel : DrawingLevel.LEVEL_DEFAULT );
    } else {
      ((LinearLayout)findViewById( R.id.layer_layout )).setVisibility( View.GONE );
    }
  }

  private void attachListeners()
  {
    TextWatcher watcher = new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence text, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence text, int start, int before, int count ) { updatePreview(); }
      @Override public void afterTextChanged( Editable text ) { }
    };
    mText.addTextChangedListener( watcher );
    mSize.addTextChangedListener( watcher );
    mLineWeight.addTextChangedListener( watcher );

    AdapterView.OnItemSelectedListener font_listener = new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected( AdapterView<?> parent, View view, int position, long id )
      {
        if ( position != mInitialFontIndex ) mFontChoiceChanged = true;
        updatePreview();
      }
      @Override public void onNothingSelected( AdapterView<?> parent ) { }
    };
    mFont.setOnItemSelectedListener( font_listener );

    CompoundButton.OnCheckedChangeListener style_listener =
      ( button, checked ) -> updatePreview();
    mBold.setOnCheckedChangeListener( style_listener );
    mItalic.setOnCheckedChangeListener( style_listener );
    mUnderline.setOnCheckedChangeListener( style_listener );
    mAlignLeft.setOnCheckedChangeListener( style_listener );
    mAlignCenter.setOnCheckedChangeListener( style_listener );
    mAlignRight.setOnCheckedChangeListener( style_listener );

    mAutoSize.setOnCheckedChangeListener( ( button, checked ) -> {
      if ( mChangingMode ) return;
      switchSizeMode( checked ? SketchTextStyle.SizeMode.AUTO_GRID
                              : ( mScaleWithSketch.isChecked()
                                  ? SketchTextStyle.SizeMode.WORLD
                                  : SketchTextStyle.SizeMode.SCREEN ) );
    } );
    mScaleWithSketch.setOnCheckedChangeListener( ( button, checked ) -> {
      if ( mChangingMode || mAutoSize.isChecked() ) return;
      switchSizeMode( checked ? SketchTextStyle.SizeMode.WORLD : SketchTextStyle.SizeMode.SCREEN );
    } );
    mOpacity.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar bar, int progress, boolean from_user )
      {
        mColor = ( mColor & 0x00ffffff ) | ( progress << 24 );
        updatePreview();
      }
      @Override public void onStartTrackingTouch( SeekBar bar ) { }
      @Override public void onStopTrackingTouch( SeekBar bar ) { }
    } );
  }

  private void switchSizeMode( SketchTextStyle.SizeMode target )
  {
    if ( target == null || target == mSizeMode ) {
      updateModeControls();
      return;
    }
    float value = parseSize( mSize.getText().toString(), fallbackDisplayHeight( mSizeMode ) );
    float world_metres;
    if ( mSizeMode == SketchTextStyle.SizeMode.AUTO_GRID ) {
      world_metres = value * Math.max( 0.0001f, TDSetting.mUnitGrid );
    } else if ( mSizeMode == SketchTextStyle.SizeMode.WORLD ) {
      world_metres = value / Math.max( 0.0001f, TDSetting.mUnitLength );
    } else {
      float zoom = Math.max( 0.0001f, mParent.zoom() );
      world_metres = value * TopoDroidApp.getDisplayDensity() / zoom / DrawingUtil.SCALE_FIX;
    }

    if ( target == SketchTextStyle.SizeMode.AUTO_GRID ) {
      value = world_metres / Math.max( 0.0001f, TDSetting.mUnitGrid );
    } else if ( target == SketchTextStyle.SizeMode.WORLD ) {
      value = world_metres * TDSetting.mUnitLength;
    } else {
      float zoom = Math.max( 0.0001f, mParent.zoom() );
      value = world_metres * DrawingUtil.SCALE_FIX * zoom / TopoDroidApp.getDisplayDensity();
    }
    mSizeMode = target;
    mSize.setText( formatSize( value ) );
    updateModeControls();
    updatePreview();
  }

  private void updateModeControls()
  {
    mChangingMode = true;
    mAutoSize.setChecked( mSizeMode == SketchTextStyle.SizeMode.AUTO_GRID );
    mScaleWithSketch.setChecked( mSizeMode != SketchTextStyle.SizeMode.SCREEN );
    mScaleWithSketch.setEnabled( mSizeMode != SketchTextStyle.SizeMode.AUTO_GRID );
    if ( mSizeMode == SketchTextStyle.SizeMode.AUTO_GRID ) {
      mSizeUnit.setText( R.string.text_unit_grid );
    } else if ( mSizeMode == SketchTextStyle.SizeMode.WORLD ) {
      mSizeUnit.setText( TDSetting.mUnitLengthStr );
    } else {
      mSizeUnit.setText( R.string.text_unit_screen );
    }
    mChangingMode = false;
  }

  private SketchTextStyle styleFromControls( boolean validate )
  {
    float fallback = fallbackDisplayHeight( mSizeMode );
    float strict_size = parseSizeStrict( mSize.getText().toString() );
    if ( validate && ! ( strict_size > 0.0f ) ) {
      mSize.setError( resString( R.string.text_invalid_size ) );
      return null;
    }
    float size = ( strict_size > 0.0f ) ? strict_size : fallback;
    if ( mSizeMode == SketchTextStyle.SizeMode.WORLD ) {
      size /= Math.max( 0.0001f, TDSetting.mUnitLength );
    }
    float strict_weight = parseSizeStrict( mLineWeight.getText().toString() );
    if ( validate && ! ( strict_weight > 0.0f ) ) {
      mLineWeight.setError( resString( R.string.text_invalid_weight ) );
      return null;
    }
    float line_weight = ( strict_weight > 0.0f )
      ? strict_weight
      : mInitialStyle.lineWeight();

    SketchTextStyle.Alignment alignment = SketchTextStyle.Alignment.LEFT;
    if ( mAlignCenter.isChecked() ) alignment = SketchTextStyle.Alignment.CENTER;
    else if ( mAlignRight.isChecked() ) alignment = SketchTextStyle.Alignment.RIGHT;

    String font_id = SketchFontRegistry.fontIdAt( mFont.getSelectedItemPosition() );
    if ( ! mFontChoiceChanged
        && mInitialFontIndex == 0
        && ! SketchFontRegistry.FONT_DEFAULT.equals( mInitialStyle.fontId() ) ) {
      font_id = mInitialStyle.fontId();
    }
    return mInitialStyle
      .withFontId( font_id )
      .withSize( mSizeMode, size )
      .withLineWeight( line_weight )
      .withEmphasis( mBold.isChecked(), mItalic.isChecked(), mUnderline.isChecked() )
      .withAlignment( alignment )
      .withColor( mColor );
  }

  private float fallbackDisplayHeight( SketchTextStyle.SizeMode mode )
  {
    if ( mInitialStyle.sizeMode() == mode ) {
      return ( mode == SketchTextStyle.SizeMode.WORLD )
        ? mInitialStyle.height() * TDSetting.mUnitLength
        : mInitialStyle.height();
    }
    if ( mode == SketchTextStyle.SizeMode.SCREEN ) return SketchTextStyle.DEFAULT_SCREEN_HEIGHT;
    if ( mode == SketchTextStyle.SizeMode.WORLD ) {
      return TDSetting.mUnitGrid * TDSetting.mUnitLength;
    }
    return SketchTextStyle.DEFAULT_AUTO_GRID_MULTIPLIER;
  }

  private void updatePreview()
  {
    if ( mPreview == null || mInitialStyle == null ) return;
    SketchTextStyle style = styleFromControls( false );
    if ( style != null ) mPreview.setPreview( mText.getText().toString(), style );
  }

  @Override
  public void colorChanged( int color )
  {
    mColor = ( mColor & 0xff000000 ) | ( color & 0x00ffffff );
    updateColorButton();
    updatePreview();
  }

  private void updateColorButton()
  {
    int opaque_color = 0xff000000 | ( mColor & 0x00ffffff );
    mColorButton.setBackgroundColor( opaque_color );
    int brightness = 299 * Color.red( opaque_color )
                   + 587 * Color.green( opaque_color )
                   + 114 * Color.blue( opaque_color );
    mColorButton.setTextColor( ( brightness >= 186000 ) ? Color.BLACK : Color.WHITE );
  }

  @Override
  public void onClick( View view )
  {
    int id = view.getId();
    if ( id == R.id.text_color ) {
      new MyColorPicker( mContext, this, mColor ).show();
      return;
    }
    if ( id == R.id.label_cancel ) {
      dismiss();
      return;
    }
    if ( id != R.id.label_ok ) return;

    String text = SketchTextInput.normalizeLineEndings( mText.getText().toString() );
    if ( ! SketchTextInput.hasVisibleText( text ) ) {
      mText.setError( resString( R.string.text_missing ) );
      return;
    }
    if ( ! SketchTextInput.fitsModifiedUtf( text ) ) {
      mText.setError( resString( R.string.text_too_long ) );
      return;
    }
    SketchTextStyle style = styleFromControls( true );
    if ( style == null ) return;

    String public_options = mOptions.getText().toString().trim();
    String serialized_options = SketchTextStyleCodec.storeInOptions( public_options, style );
    if ( ! SketchTextInput.fitsModifiedUtf( serialized_options ) ) {
      mOptions.setError( resString( R.string.text_too_long ) );
      return;
    }

    boolean formatting_changed = ! style.formattingEquals( mInitialStyle );
    if ( mEditing ) {
      boolean make_explicit = mLabel.hasExplicitTextStyle() || formatting_changed;
      mParent.updateTextObject( mLabel, text, style, make_explicit,
                                mOrientationWidget.mOrient, getLevel(), public_options );
    } else {
      mParent.addLabel( text, mX, mY, getLevel(), style, public_options,
                        mOrientationWidget.mOrient );
    }
    if ( formatting_changed ) mParent.rememberTextObjectDefault( style );
    dismiss();
  }

  private void initializeLevels( int level )
  {
    mCBbase = (CheckBox)findViewById( R.id.cb_layer_base );
    mCBfloor = (CheckBox)findViewById( R.id.cb_layer_floor );
    mCBfill = (CheckBox)findViewById( R.id.cb_layer_fill );
    mCBceil = (CheckBox)findViewById( R.id.cb_layer_ceil );
    mCBarti = (CheckBox)findViewById( R.id.cb_layer_arti );
    mCBbase.setChecked( ( level & DrawingLevel.LEVEL_BASE ) != 0 );
    mCBfloor.setChecked( ( level & DrawingLevel.LEVEL_FLOOR ) != 0 );
    mCBfill.setChecked( ( level & DrawingLevel.LEVEL_FILL ) != 0 );
    mCBceil.setChecked( ( level & DrawingLevel.LEVEL_CEIL ) != 0 );
    mCBarti.setChecked( ( level & DrawingLevel.LEVEL_ARTI ) != 0 );
  }

  private int getLevel()
  {
    if ( TDSetting.mWithLevels < 2 ) {
      return mEditing ? mLabel.mLevel : DrawingLevel.LEVEL_DEFAULT;
    }
    int level = 0;
    if ( mCBbase.isChecked() ) level |= DrawingLevel.LEVEL_BASE;
    if ( mCBfloor.isChecked() ) level |= DrawingLevel.LEVEL_FLOOR;
    if ( mCBfill.isChecked() ) level |= DrawingLevel.LEVEL_FILL;
    if ( mCBceil.isChecked() ) level |= DrawingLevel.LEVEL_CEIL;
    if ( mCBarti.isChecked() ) level |= DrawingLevel.LEVEL_ARTI;
    return level;
  }

  private static float parseSize( String text, float fallback )
  {
    float value = parseSizeStrict( text );
    return ( value > 0.0f ) ? value : fallback;
  }

  private static float parseSizeStrict( String text )
  {
    try {
      float value = Float.parseFloat( text.trim() );
      if ( value > 0.0f && ! Float.isNaN( value ) && ! Float.isInfinite( value ) ) return value;
    } catch ( NumberFormatException e ) {
      // The caller either uses the last valid value for preview/conversion or reports an error on save.
    }
    return Float.NaN;
  }

  private static String formatSize( float value )
  {
    return Float.toString( value );
  }
}
