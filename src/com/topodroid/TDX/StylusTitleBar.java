package com.topodroid.TDX;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Add session controls inside the existing title row, preserving its height and title view. */
final class StylusTitleBar
{
  private final TextView indicator;
  private final ImageButton zoomIn;
  private final ImageButton zoomOut;
  private Runnable zoomInAction;
  private Runnable zoomOutAction;
  private boolean zoomEnabled;

  private StylusTitleBar( TextView title, boolean nativeTitle )
  {
    title.setSingleLine( true );
    title.setEllipsize( TextUtils.TruncateAt.END );
    ViewGroup parent = (ViewGroup)title.getParent();
    LinearLayout row;
    if ( ! nativeTitle && parent instanceof LinearLayout ) {
      row = (LinearLayout)parent;
    } else {
      int index = parent.indexOfChild( title );
      ViewGroup.LayoutParams params = title.getLayoutParams();
      parent.removeView( title );
      row = new LinearLayout( title.getContext() );
      row.setOrientation( LinearLayout.HORIZONTAL );
      row.setGravity( Gravity.CENTER_VERTICAL );
      parent.addView( row, index, params );
      title.setGravity( Gravity.CENTER_VERTICAL );
      row.addView( title, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.MATCH_PARENT, 1 ) );
    }
    indicator = new TextView( title.getContext() );
    indicator.setId( R.id.stylus_input_indicator );
    indicator.setText( R.string.stylus_only_badge );
    indicator.setTextSize( 12 );
    indicator.setTextColor( 0xffffdd66 );
    indicator.setSingleLine( true );
    indicator.setGravity( Gravity.CENTER_VERTICAL );
    int padding = (int)( 6 * title.getResources().getDisplayMetrics().density );
    indicator.setPadding( padding, 0, padding, 0 );
    indicator.setClickable( false );
    indicator.setFocusable( false );
    row.addView( indicator, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT ) );
    zoomOut = button( row, R.id.stylus_zoom_out, R.drawable.iz_zoom_out, R.string.stylus_zoom_out );
    zoomIn = button( row, R.id.stylus_zoom_in, R.drawable.iz_zoom_in, R.string.stylus_zoom_in );
    zoomOut.setOnClickListener( v -> { if ( zoomOutAction != null ) zoomOutAction.run(); } );
    zoomIn.setOnClickListener( v -> { if ( zoomInAction != null ) zoomInAction.run(); } );
  }

  private static ImageButton button( LinearLayout row, int id, int image, int description )
  {
    ImageButton button = new ImageButton( row.getContext() );
    button.setId( id );
    button.setImageResource( image );
    button.setContentDescription( row.getResources().getString( description ) );
    button.setBackgroundColor( android.graphics.Color.TRANSPARENT );
    button.setPadding( 2, 2, 2, 2 );
    button.setScaleType( android.widget.ImageView.ScaleType.FIT_CENTER );
    // The title's existing height determines the icon size; controls cannot enlarge it.
    int width = (int)( 40 * row.getResources().getDisplayMetrics().density );
    row.addView( button, new LinearLayout.LayoutParams( width, ViewGroup.LayoutParams.MATCH_PARENT ) );
    return button;
  }

  static StylusTitleBar install( Window window )
  {
    TextView title = window.findViewById( android.R.id.title );
    boolean nativeTitle = title != null;
    if ( title == null ) title = window.findViewById( R.id.title_text );
    if ( title == null || ! ( title.getParent() instanceof ViewGroup ) ) return null;
    return new StylusTitleBar( title, nativeTitle );
  }

  boolean isAttached( Window window ) { return window.findViewById( R.id.stylus_input_indicator ) == indicator; }

  void setZoomActions( Runnable in, Runnable out, boolean available )
  {
    zoomInAction = in;
    zoomOutAction = out;
    zoomEnabled = available;
    refresh();
  }

  void refresh()
  {
    boolean protectedInput = StylusOnlyInputController.isEnabled();
    indicator.setVisibility( protectedInput ? View.VISIBLE : View.GONE );
    boolean showZoom = zoomInAction != null && ( protectedInput || com.topodroid.prefs.TDSetting.mZoomCtrl > 0 );
    zoomIn.setVisibility( showZoom ? View.VISIBLE : View.GONE );
    zoomOut.setVisibility( showZoom ? View.VISIBLE : View.GONE );
    zoomIn.setEnabled( zoomEnabled );
    zoomOut.setEnabled( zoomEnabled );
    zoomIn.setAlpha( zoomEnabled ? 1 : 0.4f );
    zoomOut.setAlpha( zoomEnabled ? 1 : 0.4f );
  }
}
