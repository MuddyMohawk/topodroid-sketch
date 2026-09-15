/* @file ToolbarVisualStyle.java
 *
 * Shared minimal-grid appearance for the drawing toolbar and its editor.
 */
package com.topodroid.TDX;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

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
