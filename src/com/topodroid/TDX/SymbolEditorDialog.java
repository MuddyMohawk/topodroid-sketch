/* @file SymbolEditorDialog.java
 *
 * @author MuddyMohawk
 * @date jun 2026
 *
 * @brief TopoDroid symbol file editor dialog
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyColorPicker;
import com.topodroid.ui.MyDialog;

import android.os.Bundle;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

class SymbolEditorDialog extends MyDialog
                         implements View.OnClickListener
{
  interface OnSymbolSaved
  {
    void onSymbolSaved( int type );
  }

  private final int mType;
  private final SymbolEditorDocument mDocument;
  private final OnSymbolSaved mListener;

  private TextView mFileView;
  private LinearLayout mFieldsView;
  private EditText mText;
  private Button mBtnReset;
  private Button mBtnCancel;
  private Button mBtnSave;

  private String mOriginalText = "";
  private boolean mUpdatingText = false;

  SymbolEditorDialog( Context context, EnableSymbol symbol, OnSymbolSaved listener )
  {
    super( context, null, 0 );
    mType = symbol.getType();
    mDocument = SymbolEditorDocument.create( symbol );
    mListener = listener;
  }

  @Override
  public void onCreate( Bundle savedInstanceState )
  {
    super.onCreate( savedInstanceState );
    initLayout( R.layout.symbol_editor_dialog, R.string.title_symbol_editor );

    mFileView = (TextView) findViewById( R.id.symbol_editor_file );
    mFieldsView = (LinearLayout) findViewById( R.id.symbol_editor_fields );
    mText = (EditText) findViewById( R.id.symbol_editor_text );
    mBtnReset = (Button) findViewById( R.id.symbol_editor_reset );
    mBtnCancel = (Button) findViewById( R.id.symbol_editor_cancel );
    mBtnSave = (Button) findViewById( R.id.symbol_editor_save );

    mBtnReset.setOnClickListener( this );
    mBtnCancel.setOnClickListener( this );
    mBtnSave.setOnClickListener( this );

    if ( mDocument == null ) {
      TDToast.makeWarn( "This symbol is built in and cannot be edited here" );
      dismiss();
      return;
    }

    try {
      mOriginalText = mDocument.readText();
    } catch ( IOException e ) {
      TDToast.makeBad( "Cannot read symbol file" );
      dismiss();
      return;
    }

    mFileView.setText( resString( R.string.symbol_editor_file ) + ": " + mDocument.file().getName() );
    setEditorText( mOriginalText );
    buildFieldControls();
  }

  @Override
  public void onClick( View view )
  {
    int id = view.getId();
    if ( id == R.id.symbol_editor_reset ) {
      setEditorText( mOriginalText );
      buildFieldControls();
    } else if ( id == R.id.symbol_editor_cancel ) {
      dismiss();
    } else if ( id == R.id.symbol_editor_save ) {
      save();
    }
  }

  private void save()
  {
    String error = mDocument.saveText( mText.getText().toString() );
    if ( error != null ) {
      TDToast.makeBad( error );
      return;
    }
    TDToast.make( "Symbol saved" );
    if ( mListener != null ) mListener.onSymbolSaved( mType );
    dismiss();
  }

  private void setEditorText( String text )
  {
    mUpdatingText = true;
    mText.setText( text );
    mText.setSelection( 0 );
    mUpdatingText = false;
  }

  private void buildFieldControls()
  {
    mFieldsView.removeAllViews();
    ArrayList< SymbolEditorDocument.Field > fields = mDocument.fields( mText.getText().toString() );
    for ( SymbolEditorDocument.Field field : fields ) addFieldRow( field );
  }

  private void addFieldRow( final SymbolEditorDocument.Field field )
  {
    LinearLayout row = new LinearLayout( mContext );
    row.setOrientation( LinearLayout.HORIZONTAL );
    row.setPadding( 0, dp( 3 ), 0, dp( 3 ) );

    TextView label = new TextView( mContext );
    label.setText( field.mKey );
    LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT );
    labelLp.weight = 35;
    row.addView( label, labelLp );

    if ( field.isColor() ) {
      final Button button = new Button( mContext );
      updateColorButton( button, field.colorValue() );
      button.setOnClickListener( new View.OnClickListener() {
        @Override
        public void onClick( View v )
        {
          MyColorPicker picker = new MyColorPicker( mContext, new MyColorPicker.IColorChanged() {
            @Override
            public void colorChanged( int color )
            {
              String value = field.colorText( color );
              replaceFieldValue( field, value );
              updateColorButton( button, color );
            }
          }, field.colorValue() );
          picker.show();
        }
      } );
      LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT );
      valueLp.weight = 65;
      row.addView( button, valueLp );
    } else {
      final EditText edit = new EditText( mContext );
      edit.setSingleLine( true );
      edit.setText( field.mValue );
      edit.addTextChangedListener( new TextWatcher() {
        @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
        @Override public void onTextChanged( CharSequence s, int start, int before, int count ) { }
        @Override public void afterTextChanged( Editable s )
        {
          if ( ! mUpdatingText ) replaceFieldValue( field, s.toString() );
        }
      } );
      LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT );
      valueLp.weight = 65;
      row.addView( edit, valueLp );
    }
    mFieldsView.addView( row );
  }

  private void replaceFieldValue( SymbolEditorDocument.Field field, String value )
  {
    String oldText = mText.getText().toString();
    String newText = SymbolEditorDocument.updateFieldValue( oldText, field, value );
    if ( newText.equals( oldText ) ) return;

    int selection = mText.getSelectionStart();
    mUpdatingText = true;
    mText.setText( newText );
    mText.setSelection( Math.max( 0, Math.min( selection, newText.length() ) ) );
    mUpdatingText = false;
  }

  private void updateColorButton( Button button, int color )
  {
    int rgb = 0xff000000 | ( color & 0x00ffffff );
    button.setText( String.format( Locale.US, "0x%06x", color & 0x00ffffff ) );
    button.setBackgroundColor( rgb );
    button.setTextColor( isLightColor( rgb ) ? 0xff222222 : 0xffffffff );
  }

  private boolean isLightColor( int color )
  {
    int luma = (299 * Color.red( color ) + 587 * Color.green( color ) + 114 * Color.blue( color )) / 1000;
    return luma >= 186;
  }

  private int dp( int value )
  {
    float density = mContext.getResources().getDisplayMetrics().density;
    return Math.max( 1, Math.round( value * density ) );
  }
}
