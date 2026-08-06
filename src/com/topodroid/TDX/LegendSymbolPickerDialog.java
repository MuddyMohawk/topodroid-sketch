/* @file LegendSymbolPickerDialog.java
 *
 * @brief Searchable, multi-select symbol picker for legend rows
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import com.topodroid.util.TDColor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LegendSymbolPickerDialog extends MyDialog
{
  interface Listener { void onSymbolsSelected( List< PlotSymbolUsageSnapshot.Entry > selected ); }

  private final Set< String > mAlreadyPresent;
  private final Listener mListener;
  private final ArrayList< PlotSymbolUsageSnapshot.Entry > mAll = new ArrayList<>();
  private final ArrayList< PlotSymbolUsageSnapshot.Entry > mVisible = new ArrayList<>();
  private final HashSet< String > mSelected = new HashSet<>();
  private PlotSymbolUsageSnapshot.Kind mKind = PlotSymbolUsageSnapshot.Kind.POINT;
  private String mQuery = "";
  private SymbolAdapter mAdapter;
  private final RadioButton[] mKindTabs = new RadioButton[3];
  private Button mAddButton;

  LegendSymbolPickerDialog( android.content.Context context, Set< String > already_present,
                            Listener listener )
  {
    super( context, null, 0 );
    mAlreadyPresent = already_present == null ? new HashSet< String >() : new HashSet<>( already_present );
    mListener = listener;
    loadSymbols();
  }

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    LinearLayout root = new LinearLayout( getContext() );
    root.setOrientation( LinearLayout.VERTICAL );
    root.setPadding( dp( 12 ), dp( 12 ), dp( 12 ), dp( 12 ) );
    root.setBackgroundColor( 0xff17191e );

    LinearLayout tabs = new LinearLayout( getContext() );
    tabs.setOrientation( LinearLayout.HORIZONTAL );
    int[] labels = { R.string.title_legend_points, R.string.title_legend_lines, R.string.title_legend_areas };
    for ( int i = 0; i < labels.length; ++i ) {
      final int index = i;
      RadioButton button = new RadioButton( getContext() );
      button.setButtonDrawable( null );
      button.setText( labels[i] );
      button.setGravity( Gravity.CENTER );
      button.setMinHeight( dp( 48 ) );
      mKindTabs[i] = button;
      button.setOnClickListener( new View.OnClickListener() {
        @Override public void onClick( View view ) {
          mKind = PlotSymbolUsageSnapshot.Kind.values()[ index ];
          updateKindTabs();
          refresh();
        }
      } );
      tabs.addView( button, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    }
    root.addView( tabs );

    EditText search = new EditText( getContext() );
    search.setHint( R.string.title_legend_search_symbols );
    search.setSingleLine( true );
    search.setTextColor( Color.WHITE );
    search.setHintTextColor( 0xff9499a3 );
    search.addTextChangedListener( new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence s, int start, int before, int count ) {
        mQuery = s == null ? "" : s.toString().trim().toLowerCase( Locale.ROOT );
        refresh();
      }
      @Override public void afterTextChanged( Editable editable ) { }
    } );
    root.addView( search, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT ) );

    ListView list = new ListView( getContext() );
    list.setDividerHeight( dp( 1 ) );
    mAdapter = new SymbolAdapter();
    list.setAdapter( mAdapter );
    root.addView( list, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );

    LinearLayout footer = new LinearLayout( getContext() );
    footer.setOrientation( LinearLayout.HORIZONTAL );
    Button cancel = new Button( getContext() );
    cancel.setText( R.string.button_cancel );
    mAddButton = new Button( getContext() );
    updateAddButton();
    cancel.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { dismiss(); }
    } );
    mAddButton.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { deliver(); }
    } );
    footer.addView( cancel, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    footer.addView( mAddButton, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    root.addView( footer );

    initLayout( root, R.string.title_legend_picker_title );
    setCanceledOnTouchOutside( false );
    updateKindTabs();
    refresh();
  }

  @Override protected void onStart()
  {
    super.onStart();
    Window window = getWindow();
    if ( window != null ) window.setLayout( ViewGroup.LayoutParams.MATCH_PARENT,
      (int)( getContext().getResources().getDisplayMetrics().heightPixels * 0.88f ) );
  }

  private void loadSymbols()
  {
    addLibrary( PlotSymbolUsageSnapshot.Kind.POINT, BrushManager.getPointLibSize() );
    addLibrary( PlotSymbolUsageSnapshot.Kind.LINE, BrushManager.getLineLibSize() );
    addLibrary( PlotSymbolUsageSnapshot.Kind.AREA, BrushManager.getAreaLibSize() );
  }

  private void addLibrary( PlotSymbolUsageSnapshot.Kind kind, int size )
  {
    for ( int index = 0; index < size; ++index ) {
      String th_name;
      String name;
      if ( kind == PlotSymbolUsageSnapshot.Kind.POINT ) {
        th_name = BrushManager.getPointFullThName( index );
        name = BrushManager.getPointName( index );
      } else if ( kind == PlotSymbolUsageSnapshot.Kind.LINE ) {
        th_name = BrushManager.getLineFullThName( index );
        name = BrushManager.getLineName( index );
      } else {
        th_name = BrushManager.getAreaFullThName( index );
        name = BrushManager.getAreaName( index );
      }
      if ( th_name == null || th_name.length() == 0 ) continue;
      if ( kind == PlotSymbolUsageSnapshot.Kind.POINT && TitleLegendPointBehavior.isTitleLegendName( th_name ) ) continue;
      String identity = PlotSymbolUsageSnapshot.identity( kind, th_name );
      if ( mAlreadyPresent.contains( identity ) ) continue;
      mAll.add( new PlotSymbolUsageSnapshot.Entry( kind, th_name, name, index ) );
    }
  }

  private void refresh()
  {
    mVisible.clear();
    for ( PlotSymbolUsageSnapshot.Entry entry : mAll ) {
      if ( entry.kind != mKind ) continue;
      String haystack = ( entry.displayName + " " + entry.thName ).toLowerCase( Locale.ROOT );
      if ( mQuery.length() == 0 || haystack.contains( mQuery ) ) mVisible.add( entry );
    }
    if ( mAdapter != null ) mAdapter.notifyDataSetChanged();
  }

  private void deliver()
  {
    ArrayList< PlotSymbolUsageSnapshot.Entry > selected = new ArrayList<>();
    for ( PlotSymbolUsageSnapshot.Entry entry : mAll ) if ( mSelected.contains( entry.identity() ) ) selected.add( entry );
    if ( mListener != null ) mListener.onSymbolsSelected( selected );
    dismiss();
  }

  private void updateKindTabs()
  {
    for ( int i = 0; i < mKindTabs.length; ++i ) {
      RadioButton tab = mKindTabs[i];
      if ( tab == null ) continue;
      boolean selected = i == mKind.ordinal();
      GradientDrawable background = new GradientDrawable();
      background.setColor( selected ? TDColor.SKETCH_TOGGLE_ON : TDColor.SKETCH_TOGGLE_OFF );
      background.setCornerRadius( dp( 8 ) );
      background.setStroke( dp( 1 ), 0xff4d5360 );
      tab.setBackground( background );
      tab.setTextColor( selected ? TDColor.SKETCH_TOGGLE_ON_TEXT : TDColor.SKETCH_TOGGLE_OFF_TEXT );
      tab.setChecked( selected );
    }
  }

  private void updateAddButton()
  {
    if ( mAddButton != null ) {
      mAddButton.setText( getContext().getString( R.string.title_legend_add_selected_count,
        mSelected.size() ) );
      mAddButton.setEnabled( ! mSelected.isEmpty() );
    }
  }

  private final class SymbolAdapter extends BaseAdapter
  {
    @Override public int getCount() { return mVisible.size(); }
    @Override public Object getItem( int position ) { return mVisible.get( position ); }
    @Override public long getItemId( int position ) { return position; }

    @Override public View getView( int position, View convert_view, ViewGroup parent )
    {
      final PlotSymbolUsageSnapshot.Entry entry = mVisible.get( position );
      LinearLayout row = new LinearLayout( getContext() );
      row.setOrientation( LinearLayout.HORIZONTAL );
      row.setGravity( Gravity.CENTER_VERTICAL );
      row.setPadding( dp( 6 ), dp( 4 ), dp( 6 ), dp( 4 ) );
      GradientDrawable background = new GradientDrawable();
      background.setColor( 0xff24272e );
      background.setCornerRadius( dp( 6 ) );
      row.setBackground( background );

      LegendEditorSwatchView swatch = new LegendEditorSwatchView( getContext() );
      swatch.setRow( TitleLegendPointState.Row.fromEntry( entry ) );
      row.addView( swatch, new LinearLayout.LayoutParams( dp( 82 ), dp( 52 ) ) );
      TextView label = new TextView( getContext() );
      label.setText( entry.displayName );
      label.setTextColor( Color.WHITE );
      label.setTextSize( 15.0f );
      label.setPadding( dp( 10 ), 0, dp( 6 ), 0 );
      row.addView( label, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
      final CheckBox check = new CheckBox( getContext() );
      check.setChecked( mSelected.contains( entry.identity() ) );
      row.addView( check );
      View.OnClickListener toggle = new View.OnClickListener() {
        @Override public void onClick( View view ) {
          boolean selected = ! mSelected.contains( entry.identity() );
          if ( selected ) mSelected.add( entry.identity() ); else mSelected.remove( entry.identity() );
          check.setChecked( selected );
          updateAddButton();
        }
      };
      row.setOnClickListener( toggle );
      check.setOnClickListener( new View.OnClickListener() {
        @Override public void onClick( View view ) {
          if ( check.isChecked() ) mSelected.add( entry.identity() ); else mSelected.remove( entry.identity() );
          updateAddButton();
        }
      } );
      return row;
    }
  }

  private int dp( int value )
  {
    return Math.round( value * getContext().getResources().getDisplayMetrics().density );
  }
}
