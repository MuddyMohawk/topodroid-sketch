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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

final class CeilingHeightPointEditorController implements SpecialPointEditorController
{
  private final DrawingWindow mParent;
  private final DrawingSemanticPointPath mPoint;
  private final FramedTextTypographyEditor mTypography = new FramedTextTypographyEditor();
  private CheckBox mWaterEnabled;
  private EditText mWaterDepth;

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
    mTypography.bind( mParent, root, state,
      CeilingHeightPointState.MIN_TEXT_SCALE, CeilingHeightPointState.MAX_TEXT_SCALE );
    mWaterEnabled.setChecked( state.waterEnabled );
    mWaterDepth.setText( state.waterDepth );
    showWaterField( state.waterEnabled, false );
    mWaterEnabled.setOnClickListener( view -> showWaterField( mWaterEnabled.isChecked(), true ) );
  }

  @Override public void apply()
  {
    if ( mWaterEnabled == null || ! mTypography.isBound() ) return;
    CeilingHeightPointState state = new CeilingHeightPointState(
      mWaterEnabled.isChecked(), mWaterDepth.getText().toString(), mTypography.fontId(),
      mTypography.bold(), mTypography.italic(), mTypography.underline(),
      mTypography.textScalePercent() );
    mPoint.setSpecialState( state, true );
    mTypography.rememberAsTextDefault( mParent );
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
}
