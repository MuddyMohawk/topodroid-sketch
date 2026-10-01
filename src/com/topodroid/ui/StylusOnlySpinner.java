package com.topodroid.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import com.topodroid.TDX.StylusOnlyInputController;

/** A spinner chooser whose window participates in app-wide input protection. */
public class StylusOnlySpinner extends Spinner
{
  private AlertDialog chooser;

  public StylusOnlySpinner( Context context ) { super( context ); }
  public StylusOnlySpinner( Context context, AttributeSet attrs ) { super( context, attrs ); }
  public StylusOnlySpinner( Context context, AttributeSet attrs, int style ) { super( context, attrs, style ); }

  @Override public boolean performClick()
  {
    if ( chooser != null && chooser.isShowing() ) return true;
    final SpinnerAdapter adapter = getAdapter();
    if ( adapter == null ) return true;
    BaseAdapter choices = new BaseAdapter() {
      @Override public int getCount() { return adapter.getCount(); }
      @Override public Object getItem( int position ) { return adapter.getItem( position ); }
      @Override public long getItemId( int position ) { return adapter.getItemId( position ); }
      @Override public View getView( int position, View convertView, ViewGroup parent ) {
        return adapter.getDropDownView( position, convertView, parent );
      }
    };
    chooser = new AlertDialog.Builder( getContext() )
        .setTitle( getPrompt() )
        .setSingleChoiceItems( choices, getSelectedItemPosition(), ( dialog, position ) -> {
          setSelection( position );
          dialog.dismiss();
        } ).create();
    StylusOnlyInputController.guardDialog( chooser ).show();
    return true;
  }

  @Override protected void onDetachedFromWindow()
  {
    if ( chooser != null ) { chooser.dismiss(); chooser = null; }
    super.onDetachedFromWindow();
  }
}
