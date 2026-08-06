/* @file LegendRowEditDialog.java
 *
 * @brief Curated per-row symbol preview editor
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import com.topodroid.prefs.TDSetting;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

final class LegendRowEditDialog extends MyDialog
{
  interface Listener { void onRowChanged( TitleLegendPointState.Row row ); }

  private final TitleLegendPointState.Row mRow;
  private final Listener mListener;
  private static final float MIN_WEIGHT = 0.05f;
  private static final float WEIGHT_STEP = 0.01f;
  private static final int MAX_WEIGHT_PROGRESS = 1995;

  private LegendEditorSwatchView mSwatch;
  private SeekBar mWeight;
  private TextView mWeightValue;
  private EditText mScale;
  private EditText mOrientation;
  private EditText mValue;
  private boolean mUseStandard;
  private boolean mUpdating;

  LegendRowEditDialog( android.content.Context context, TitleLegendPointState.Row row, Listener listener )
  {
    super( context, null, 0 );
    mRow = row;
    mListener = listener;
  }

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    LinearLayout root = new LinearLayout( getContext() );
    root.setOrientation( LinearLayout.VERTICAL );
    root.setPadding( dp( 16 ), dp( 12 ), dp( 16 ), dp( 10 ) );
    root.setBackgroundColor( 0xff17191e );

    mSwatch = new LegendEditorSwatchView( getContext() );
    mSwatch.setRow( mRow );
    root.addView( mSwatch, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, dp( 96 ) ) );

    addLabel( root, R.string.text_line_weight );
    LinearLayout weight_row = new LinearLayout( getContext() );
    weight_row.setOrientation( LinearLayout.HORIZONTAL );
    weight_row.setGravity( android.view.Gravity.CENTER_VERTICAL );
    mWeight = new SeekBar( getContext() );
    mWeight.setMax( MAX_WEIGHT_PROGRESS );
    mWeightValue = new TextView( getContext() );
    mWeightValue.setTextColor( Color.WHITE );
    mWeightValue.setGravity( android.view.Gravity.CENTER );
    weight_row.addView( mWeight, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    weight_row.addView( mWeightValue, new LinearLayout.LayoutParams( dp( 116 ), ViewGroup.LayoutParams.WRAP_CONTENT ) );
    root.addView( weight_row );

    LinearLayout weight_actions = new LinearLayout( getContext() );
    Button standard = new Button( getContext() );
    standard.setText( R.string.title_legend_use_standard );
    standard.setAllCaps( false );
    Button reset = new Button( getContext() );
    reset.setText( R.string.title_legend_reset_preview );
    reset.setAllCaps( false );
    weight_actions.addView( standard, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    weight_actions.addView( reset, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    root.addView( weight_actions );

    mUseStandard = mRow.preview.weightMode == TitleLegendPointState.WeightMode.STANDARD;
    setWeightProgress( mUseStandard ? standardWeight() : mRow.preview.customWeight );
    mWeight.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar seek, int progress, boolean from_user ) {
        if ( from_user ) mUseStandard = Math.abs( weightFromProgress() - standardWeight() ) < WEIGHT_STEP * 0.51f;
        updateWeightLabel();
        updatePreview();
      }
      @Override public void onStartTrackingTouch( SeekBar seek ) { }
      @Override public void onStopTrackingTouch( SeekBar seek ) { }
    } );
    standard.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) {
        mUseStandard = true;
        setWeightProgress( standardWeight() );
        updatePreview();
      }
    } );
    reset.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) {
        mUseStandard = true;
        setWeightProgress( standardWeight() );
        mScale.setText( "1" );
        mOrientation.setText( "0" );
        mValue.setText( "" );
        updatePreview();
      }
    } );

    mScale = addField( root, R.string.title_legend_point_scale, Float.toString( mRow.preview.pointScale ) );
    mOrientation = addField( root, R.string.title_legend_orientation, Float.toString( mRow.preview.orientation ) );
    mValue = addField( root, R.string.title_legend_preview_value,
      mRow.preview.textValue == null ? "" : mRow.preview.textValue );
    mValue.setFilters( new InputFilter[] {
      SketchTextInput.codePointLengthFilter( TitleLegendPointState.MAX_TEXT_CODEPOINTS ) } );
    boolean point = mRow.kind == TitleLegendPointState.Kind.POINT;
    mScale.setEnabled( point );
    int point_index = point ? new TitleLegendLayout.InstalledSymbolResolver().libraryIndex( mRow.kind, mRow.thName ) : -1;
    mOrientation.setEnabled( point && point_index >= 0 && BrushManager.isPointOrientable( point_index ) );
    mValue.setEnabled( point && SpecialPointRegistry.isSpecial( Symbol.deprefix_u( mRow.thName ) ) );
    TextWatcher preview_watcher = new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence s, int start, int before, int count ) { updatePreview(); }
      @Override public void afterTextChanged( Editable editable ) { }
    };
    mScale.addTextChangedListener( preview_watcher );
    mOrientation.addTextChangedListener( preview_watcher );
    mValue.addTextChangedListener( preview_watcher );
    updateWeightLabel();
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
    initLayout( root, R.string.title_legend_edit_row );
    setCanceledOnTouchOutside( false );
  }

  private void save()
  {
    try {
      float scale = Float.parseFloat( mScale.getText().toString() );
      float orientation = Float.parseFloat( mOrientation.getText().toString() );
      if ( ! Float.isFinite( scale ) || scale < 0.1f || scale > 10.0f
          || ! Float.isFinite( orientation ) ) throw new NumberFormatException();
      TitleLegendPointState.WeightMode mode = mUseStandard
        ? TitleLegendPointState.WeightMode.STANDARD : TitleLegendPointState.WeightMode.CUSTOM;
      TitleLegendPointState.Preview preview = new TitleLegendPointState.Preview(
        mode, weightFromProgress(), scale, orientation, previewTextValue(), mRow.preview.specialPayload );
      if ( mListener != null ) mListener.onRowChanged( mRow.withPreview( preview ) );
      dismiss();
    } catch ( NumberFormatException e ) {
      Toast.makeText( getContext(), R.string.illegal_value, Toast.LENGTH_LONG ).show();
    }
  }

  private void updatePreview()
  {
    if ( mUpdating || mSwatch == null || mScale == null || mOrientation == null || mValue == null ) return;
    float scale = parseOr( mScale, mRow.preview.pointScale );
    float orientation = parseOr( mOrientation, mRow.preview.orientation );
    TitleLegendPointState.Preview preview = new TitleLegendPointState.Preview(
      mUseStandard ? TitleLegendPointState.WeightMode.STANDARD : TitleLegendPointState.WeightMode.CUSTOM,
      weightFromProgress(), scale, orientation, previewTextValue(), mRow.preview.specialPayload );
    mSwatch.setRow( mRow.withPreview( preview ) );
  }

  private float standardWeight()
  {
    SketchBrushStyle standard = TDSetting.getSketchStyle( TDSetting.SKETCH_STYLE_STANDARD );
    return standard == null ? SketchBrushStyle.DEFAULT_WEIGHT_STANDARD
      : standard.weightOr( SketchBrushStyle.DEFAULT_WEIGHT_STANDARD );
  }

  private void setWeightProgress( float weight )
  {
    mUpdating = true;
    int progress = Math.round( ( Math.max( MIN_WEIGHT, Math.min( 20.0f, weight ) ) - MIN_WEIGHT ) / WEIGHT_STEP );
    mWeight.setProgress( Math.max( 0, Math.min( MAX_WEIGHT_PROGRESS, progress ) ) );
    mUpdating = false;
    updateWeightLabel();
  }

  private float weightFromProgress()
  {
    return MIN_WEIGHT + mWeight.getProgress() * WEIGHT_STEP;
  }

  private void updateWeightLabel()
  {
    if ( mWeightValue == null ) return;
    String value = String.format( Locale.US, "%.2f", weightFromProgress() );
    mWeightValue.setText( mUseStandard
      ? getContext().getString( R.string.title_legend_standard_weight ) + "  " + value
      : getContext().getString( R.string.title_legend_custom_weight ) + "  " + value );
  }

  private static float parseOr( EditText field, float fallback )
  {
    try {
      float value = Float.parseFloat( field.getText().toString() );
      return Float.isFinite( value ) ? value : fallback;
    } catch ( NumberFormatException e ) {
      return fallback;
    }
  }

  private String previewTextValue()
  {
    String value = mValue == null ? "" : mValue.getText().toString();
    return value.length() == 0 ? null : value;
  }

  private EditText addField( LinearLayout root, int label, String value )
  {
    addLabel( root, label );
    EditText field = new EditText( getContext() );
    field.setText( value );
    field.setTextColor( Color.WHITE );
    field.setSingleLine( true );
    root.addView( field );
    return field;
  }

  private void addLabel( LinearLayout root, int label )
  {
    TextView view = new TextView( getContext() );
    view.setText( label );
    view.setTextColor( 0xffc8ccd4 );
    view.setPadding( 0, dp( 7 ), 0, 0 );
    root.addView( view );
  }

  private int dp( int value ) { return Math.round( value * getContext().getResources().getDisplayMetrics().density ); }
}
