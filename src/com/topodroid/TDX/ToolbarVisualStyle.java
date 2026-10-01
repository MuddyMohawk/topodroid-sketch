/* @file ToolbarVisualStyle.java
 *
 * Shared minimal-grid appearance for the drawing toolbar and its editor.
 */
package com.topodroid.TDX;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Package-local toolbar palette and drawable factory. */
final class ToolbarVisualStyle
{
  private ToolbarVisualStyle() { }

  static int color( Context context, int resource )
  {
    return context.getResources().getColor( resource );
  }

  static int dp( Context context, int value )
  {
    return Math.max( value == 0 ? 0 : 1,
      Math.round( value * context.getResources().getDisplayMetrics().density ) );
  }

  static int gap( Context context )
  {
    return context.getResources().getDimensionPixelSize( R.dimen.toolbar_grid_gap );
  }

  static int inset( Context context )
  {
    return context.getResources().getDimensionPixelSize( R.dimen.toolbar_editor_inset );
  }

  static TextView label( Context context, CharSequence text, float sp, int color )
  {
    TextView view = new TextView( context );
    view.setText( text ); view.setTextSize( sp ); view.setTextColor( color );
    view.setGravity( Gravity.CENTER_VERTICAL );
    return view;
  }

  static TextView action( Context context, String text, int color )
  {
    TextView view = label( context, text, 13, color );
    view.setGravity( Gravity.CENTER ); view.setPadding( dp( context, 7 ), 0, dp( context, 7 ), 0 );
    view.setClickable( true ); view.setFocusable( true );
    return view;
  }

  static TextView chip( Context context, String text )
  {
    TextView view = label( context, text, 14, color( context, R.color.toolbar_dim ) );
    view.setGravity( Gravity.CENTER ); view.setMinHeight( dp( context, 38 ) );
    view.setPadding( dp( context, 10 ), dp( context, 6 ), dp( context, 10 ), dp( context, 6 ) );
    return view;
  }

  static LinearLayout zoneHeading( Context context, CharSequence text, int color )
  {
    LinearLayout row = new LinearLayout( context ); row.setOrientation( LinearLayout.HORIZONTAL );
    row.setGravity( Gravity.CENTER_VERTICAL ); row.setPadding( inset( context ), 0, inset( context ), 0 );
    TextView label = label( context, text, 12, color );
    label.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); row.addView( label );
    View line = new View( context ); line.setBackgroundColor( color( context, R.color.toolbar_grid_line ) );
    // Preserve the editor's existing one-pixel heading hairline.
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 0, 1, 1.0f );
    params.setMarginStart( dp( context, 8 ) ); row.addView( line, params );
    return row;
  }

  static GradientDrawable rounded( Context context, int fill, int stroke, int strokeWidthDp, int radiusDp )
  {
    GradientDrawable drawable = new GradientDrawable(); drawable.setColor( fill );
    drawable.setCornerRadius( dp( context, radiusDp ) );
    if ( strokeWidthDp > 0 ) drawable.setStroke( dp( context, strokeWidthDp ), stroke );
    return drawable;
  }

  static GradientDrawable cell( Context context, int fillResource )
  {
    return cell( context, color( context, fillResource ), Color.TRANSPARENT, 0, false );
  }

  static GradientDrawable outlinedCell( Context context, int fillResource, int strokeResource, int strokeWidthResource )
  {
    return cell( context, color( context, fillResource ), color( context, strokeResource ),
      context.getResources().getDimensionPixelSize( strokeWidthResource ), false );
  }

  static GradientDrawable dashedCell( Context context, int fillResource )
  {
    return cell( context, color( context, fillResource ), color( context, R.color.toolbar_grid_line ),
      gap( context ), true );
  }

  static GradientDrawable symbolCell( Context context, boolean selected )
  {
    return selected
      ? outlinedCell( context, R.color.toolbar_selected_tint, R.color.toolbar_amber, R.dimen.toolbar_selection_stroke )
      : cell( context, R.color.toolbar_cell );
  }

  static GradientDrawable dropCell( Context context )
  {
    return outlinedCell( context, R.color.toolbar_cell, R.color.toolbar_accent, R.dimen.toolbar_drop_stroke );
  }

  private static GradientDrawable cell( Context context, int fill, int stroke, int strokeWidth, boolean dashed )
  {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor( fill );
    drawable.setCornerRadius( context.getResources().getDimension( R.dimen.toolbar_grid_radius ) );
    if ( strokeWidth > 0 ) {
      if ( dashed ) {
        int dash = dp( context, 3 );
        drawable.setStroke( strokeWidth, stroke, dash, dash );
      } else {
        drawable.setStroke( strokeWidth, stroke );
      }
    }
    return drawable;
  }
}
