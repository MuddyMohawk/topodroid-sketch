/* @file DrawingReferenceDialog.java
 *
 * @author MuddyMohawk
 * @date apr 2026
 *
 * @brief TopoDroid sketch reference-image dialog
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;

import android.os.Bundle;
import android.content.Context;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

class DrawingReferenceDialog extends MyDialog
                             implements View.OnClickListener
{
  private final DrawingWindow mParent;
  private final DrawingReferencePath mPoint;

  private CheckBox mVisible;
  private SeekBar mOpacity;
  private TextView mOpacityValue;

  DrawingReferenceDialog( Context context, DrawingWindow parent, DrawingReferencePath point )
  {
    super( context, null, 0 );
    mParent = parent;
    mPoint = point;
  }

  @Override
  protected void onCreate( Bundle savedInstanceState )
  {
    super.onCreate( savedInstanceState );
    initLayout( R.layout.drawing_reference_dialog, R.string.title_reference_underlay );

    TextView source = (TextView) findViewById( R.id.reference_source );
    mVisible = (CheckBox) findViewById( R.id.reference_visible );
    mOpacity = (SeekBar) findViewById( R.id.reference_opacity );
    mOpacityValue = (TextView) findViewById( R.id.reference_opacity_value );

    String source_name = ( mPoint == null ) ? null : mPoint.getSourceName();
    source.setText( ( source_name == null ) ? "" : source_name );

    int alpha = ( mPoint == null ) ? Math.round( ReferencePointHelper.DEFAULT_ALPHA * 100.0f ) : mPoint.getAlphaPercent();
    mVisible.setChecked( mPoint != null && mPoint.isReferenceVisible() );
    mOpacity.setMax( 100 );
    mOpacity.setProgress( alpha );
    updateOpacityLabel( alpha );
    mOpacity.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser )
      {
        updateOpacityLabel( progress );
      }

      @Override
      public void onStartTrackingTouch( SeekBar seekBar ) { }

      @Override
      public void onStopTrackingTouch( SeekBar seekBar ) { }
    } );

    ( (Button) findViewById( R.id.reference_replace ) ).setOnClickListener( this );
    ( (Button) findViewById( R.id.button_cancel ) ).setOnClickListener( this );
    ( (Button) findViewById( R.id.button_ok ) ).setOnClickListener( this );
  }

  @Override
  public void onClick( View view )
  {
    if ( mPoint == null ) {
      dismiss();
      return;
    }

    int id = view.getId();
    if ( id == R.id.reference_replace ) {
      boolean changed = applySettings();
      if ( changed ) mParent.notifyReferencePointChanged( mPoint );
      mParent.replaceReferencePoint( mPoint );
    } else if ( id == R.id.button_ok ) {
      if ( applySettings() ) mParent.notifyReferencePointChanged( mPoint );
    }
    dismiss();
  }

  private boolean applySettings()
  {
    boolean changed = ( mPoint.isReferenceVisible() != mVisible.isChecked() )
                   || ( mPoint.getAlphaPercent() != mOpacity.getProgress() );
    mPoint.setReferenceVisible( mVisible.isChecked() );
    mPoint.setReferenceAlpha( mOpacity.getProgress() / 100.0f );
    return changed;
  }

  private void updateOpacityLabel( int value )
  {
    if ( mOpacityValue != null ) {
      mOpacityValue.setText( String.format( Locale.getDefault(), "%d%%", value ) );
    }
  }
}
