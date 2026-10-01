package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Edits a draft; closing the window never changes the underlying section. */
class DrawingSectionShotsDialog extends MyDialog
{
  interface Saver { boolean save( SectionReferenceSelection selection ); }
  private final Saver saver;
  private final SectionReferenceSelection draft;
  private final Map<String,Boolean> availability;
  private final List<String> ordered;
  private final List<String> filtered = new ArrayList<>();
  private final StationAdapter adapter = new StationAdapter();

  DrawingSectionShotsDialog( Context context, SectionReferenceSelection selection,
                              Map<String,Boolean> availability, Saver saver )
  {
    super( context, null, 0 );
    this.saver = saver;
    draft = new SectionReferenceSelection( selection );
    this.availability = availability;
    ordered = new ArrayList<>( availability.keySet() );
    Collections.sort( ordered, ( a, b ) -> {
      if ( draft.includes( a ) != draft.includes( b ) ) return draft.includes( a ) ? -1 : 1;
      return StationNameOrder.compare( a, b );
    } );
    filtered.addAll( ordered );
  }

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    initLayout( R.layout.drawing_section_shots_dialog, R.string.section_shots_title );
    // Bound the scrollable list while keeping search and actions on screen.
    getWindow().setLayout( ViewGroup.LayoutParams.MATCH_PARENT,
      (int)( mContext.getResources().getDisplayMetrics().heightPixels * 0.85f ) );
    getWindow().setSoftInputMode( android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE );
    ListView list = (ListView)findViewById( R.id.section_station_list );
    list.setAdapter( adapter );
    list.setEmptyView( findViewById( R.id.section_station_empty ) );
    CheckBox outOfPlane = (CheckBox)findViewById( R.id.section_out_of_plane );
    outOfPlane.setChecked( draft.showOutOfPlane );
    outOfPlane.setOnCheckedChangeListener( ( button, checked ) -> draft.showOutOfPlane = checked );
    ((EditText)findViewById( R.id.section_station_search )).addTextChangedListener( new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence s, int start, int before, int count )
      {
        String search = s.toString().toLowerCase( Locale.ROOT );
        filtered.clear();
        for ( String station : ordered ) if ( station.toLowerCase( Locale.ROOT ).contains( search ) ) filtered.add( station );
        adapter.notifyDataSetChanged();
      }
      @Override public void afterTextChanged( Editable s ) { }
    } );
    findViewById( R.id.button_cancel ).setOnClickListener( v -> dismiss() );
    findViewById( R.id.button_save ).setOnClickListener( v -> {
      if ( saver.save( new SectionReferenceSelection( draft ) ) ) dismiss();
      else TDToast.makeBad( R.string.section_selection_save_failed );
    } );
  }

  private final class StationAdapter extends BaseAdapter
  {
    @Override public int getCount() { return filtered.size(); }
    @Override public String getItem( int position ) { return filtered.get( position ); }
    @Override public long getItemId( int position ) { return position; }
    @Override public View getView( int position, View row, ViewGroup parent )
    {
      if ( row == null ) row = LayoutInflater.from( mContext ).inflate( R.layout.section_station_row, parent, false );
      String station = getItem( position );
      boolean available = Boolean.TRUE.equals( availability.get( station ) );
      CheckBox include = (CheckBox)row.findViewById( R.id.section_station_checked );
      CheckBox splays = (CheckBox)row.findViewById( R.id.section_station_splays );
      include.setOnCheckedChangeListener( null ); splays.setOnCheckedChangeListener( null );
      include.setText( station );
      include.setContentDescription( mContext.getString( R.string.section_station_accessibility, station ) );
      splays.setContentDescription( mContext.getString( R.string.section_splays_accessibility, station ) );
      include.setChecked( draft.includes( station ) );
      include.setEnabled( available );
      splays.setChecked( ! draft.hiddenSplays.contains( station ) );
      splays.setEnabled( available && draft.includes( station ) );
      row.findViewById( R.id.section_station_unavailable ).setVisibility( available ? View.GONE : View.VISIBLE );
      include.setOnCheckedChangeListener( ( button, checked ) -> {
        draft.setStation( station, checked );
        splays.setEnabled( available && checked );
      } );
      splays.setOnCheckedChangeListener( ( button, checked ) -> draft.setSplays( station, checked ) );
      return row;
    }
  }
}
