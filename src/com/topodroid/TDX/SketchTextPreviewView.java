/* @file SketchTextPreviewView.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Preview surface backed by the production Sketch text renderer
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;

public class SketchTextPreviewView extends View
{
  private String mText = "";
  private SketchTextStyle mStyle = SketchTextStyle.defaultStyle();

  public SketchTextPreviewView( Context context )
  {
    super( context );
    initialize();
  }

  public SketchTextPreviewView( Context context, AttributeSet attrs )
  {
    super( context, attrs );
    initialize();
  }

  public SketchTextPreviewView( Context context, AttributeSet attrs, int def_style )
  {
    super( context, attrs, def_style );
    initialize();
  }

  private void initialize()
  {
    setBackgroundColor( Color.rgb( 32, 32, 32 ) );
  }

  void setPreview( String text, SketchTextStyle style )
  {
    mText = ( text == null ) ? "" : SketchTextInput.normalizeLineEndings( text );
    mStyle = ( style == null ) ? SketchTextStyle.defaultStyle() : style;
    invalidate();
  }

  @Override
  protected void onDraw( Canvas canvas )
  {
    super.onDraw( canvas );
    String preview_text = SketchTextInput.hasVisibleText( mText ) ? mText : getContext().getString( R.string.text_preview_sample );
    SketchTextLayoutSnapshot layout = SketchTextLayoutSnapshot.create( preview_text, mStyle );
    float available_width = Math.max( 1.0f, getWidth() - 24.0f );
    float available_height = Math.max( 1.0f, getHeight() - 24.0f );
    float width_height = ( layout.maximumWidth > 0.0f )
      ? available_width / layout.maximumWidth
      : available_height;
    float block_height = Math.max( 1.0f, layout.bottom() - layout.top() );
    float base_line_height = Math.min( available_height / block_height, width_height );
    base_line_height = Math.max( 8.0f,
        Math.min( 48.0f * TopoDroidApp.getDisplayDensity(), base_line_height ) );
    float line_height = base_line_height * SketchTextRenderer.footprintScale( mStyle );

    float anchor_x;
    if ( mStyle.alignment() == SketchTextStyle.Alignment.CENTER ) {
      anchor_x = getWidth() * 0.5f;
    } else if ( mStyle.alignment() == SketchTextStyle.Alignment.RIGHT ) {
      anchor_x = getWidth() - 12.0f;
    } else {
      anchor_x = 12.0f;
    }
    float anchor_y = 12.0f - layout.top() * line_height;
    SketchTextRenderer.drawAt( canvas, anchor_x, anchor_y, 0.0f,
                               mStyle, layout, line_height, 0 );
  }
}
