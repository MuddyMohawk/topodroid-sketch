/* @file PitDepthPointEditorController.java
 *
 * @brief Point-dialog controls for the pit-depth annotation
 */
package com.topodroid.TDX;

import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

final class PitDepthPointEditorController implements SpecialPointEditorController
{
  private final DrawingWindow mParent;
  private final DrawingSemanticPointPath mPoint;
  private final FramedTextTypographyEditor mTypography = new FramedTextTypographyEditor();

  PitDepthPointEditorController( DrawingWindow parent, DrawingSemanticPointPath point )
  {
    mParent = parent;
    mPoint = point;
  }

  @Override public void bind( LinearLayout container, EditText primary_text )
  {
    if ( container == null || primary_text == null
        || ! ( mPoint.specialState() instanceof PitDepthPointState ) ) return;
    PitDepthPointState state = (PitDepthPointState)mPoint.specialState();
    primary_text.setHint( R.string.pit_depth );
    primary_text.setSingleLine( true );
    primary_text.setInputType( InputType.TYPE_CLASS_TEXT );

    View root = LayoutInflater.from( mParent ).inflate( R.layout.drawing_pit_depth_editor, container, false );
    container.addView( root );
    mTypography.bind( mParent, root, state,
      PitDepthPointState.MIN_TEXT_SCALE, PitDepthPointState.MAX_TEXT_SCALE );
  }

  @Override public void apply()
  {
    if ( ! mTypography.isBound() ) return;
    PitDepthPointState state = new PitDepthPointState(
      mTypography.fontId(), mTypography.bold(), mTypography.italic(),
      mTypography.underline(), mTypography.textScalePercent() );
    mPoint.setSpecialState( state, true );
    mTypography.rememberAsTextDefault( mParent );
  }
}
