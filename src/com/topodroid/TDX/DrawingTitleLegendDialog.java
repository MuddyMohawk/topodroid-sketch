/* @file DrawingTitleLegendDialog.java
 *
 * @brief Transactional editor for the Title Sheet and Legend composite point
 */
package com.topodroid.TDX;

import com.topodroid.ui.MyDialog;
import com.topodroid.ui.SegmentedToggleBar;
import com.topodroid.util.TDColor;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.json.JSONException;

final class DrawingTitleLegendDialog extends MyDialog implements View.OnClickListener
{
  private static final int LEGEND_SCALE_MIN_PERCENT = 50;
  private static final int LEGEND_SCALE_MAX_PERCENT = 200;

  private final DrawingWindow mParent;
  private final DrawingSemanticPointPath mPoint;
  private final boolean mInitialPlacement;

  private final ArrayList< TitleLegendPointState.Row > mRows = new ArrayList<>();
  private final HashSet< String > mDismissed = new HashSet<>();
  private final HashMap< String, TitleLegendPointState.Row > mRemovedSession = new HashMap<>();
  private PlotSymbolUsageSnapshot mUsage;
  private SketchTextStyle mTextStyle;
  private String mObjectId;
  private int mRequestedColumns;
  private int mRowsPerColumn;
  private float mLegendScale;
  private boolean mFinished;

  private SegmentedToggleBar mTabs;
  private View mTitlePanel;
  private View mLegendPanel;
  private LinearLayout mRowsContainer;
  private TextView mColumnsValue;
  private TextView mRowsValue;
  private SeekBar mLegendSize;
  private TextView mLegendSizeValue;
  private TextView mCapacityWarning;
  private String mLastCapacityAnnouncement;
  private boolean mCustomNoteShown;

  DrawingTitleLegendDialog( DrawingWindow parent, DrawingSemanticPointPath point,
                            boolean initial_placement )
  {
    super( parent, null, 0 );
    mParent = parent;
    mPoint = point;
    mInitialPlacement = initial_placement;
    TitleLegendPointState state = point.specialState() instanceof TitleLegendPointState
      ? (TitleLegendPointState)point.specialState() : TitleLegendPointState.empty();
    mObjectId = state.objectId;
    mRequestedColumns = state.requestedColumns;
    mRowsPerColumn = state.rowsPerColumn;
    mLegendScale = state.legendScale;
    mTextStyle = state.textStyle;
    mRows.addAll( state.rows );
    mDismissed.addAll( state.dismissed );
    mUsage = parent.computePlotSymbolUsageSnapshot();
  }

  @Override protected void onCreate( Bundle saved_instance_state )
  {
    super.onCreate( saved_instance_state );
    initLayout( R.layout.drawing_title_legend_dialog, R.string.title_legend_dialog_title );
    setCanceledOnTouchOutside( false );
    Window window = getWindow();
    if ( window != null ) window.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE );
    mTabs = (SegmentedToggleBar)findViewById( R.id.title_legend_tabs );
    mTitlePanel = findViewById( R.id.title_sheet_placeholder );
    mLegendPanel = findViewById( R.id.legend_editor_panel );
    mRowsContainer = (LinearLayout)findViewById( R.id.legend_rows );
    mColumnsValue = (TextView)findViewById( R.id.legend_columns_value );
    mRowsValue = (TextView)findViewById( R.id.legend_rows_value );
    mLegendSize = (SeekBar)findViewById( R.id.legend_size );
    mLegendSizeValue = (TextView)findViewById( R.id.legend_size_value );
    mCapacityWarning = (TextView)findViewById( R.id.legend_capacity_warning );
    tintReservedSwitch( (Switch)findViewById( R.id.title_sheet_enabled ) );
    tintReservedSwitch( (Switch)findViewById( R.id.legend_enabled ) );

    mTabs.setLabels( getContext().getString( R.string.title_legend_title_sheet ),
                     getContext().getString( R.string.title_legend_legend ) );
    mTabs.setOnSelectionChangedListener( new SegmentedToggleBar.OnSelectionChangedListener() {
      @Override public void onSelectionChanged( int index ) { showTab( index ); }
    } );
    mTabs.setSelectedIndex( 1 );
    showTab( 1 );
    configureLegendSize();
    styleDialogButtons();

    int[] buttons = {
      R.id.legend_add_custom, R.id.legend_add_symbol, R.id.legend_rescan,
      R.id.legend_columns_minus, R.id.legend_columns_plus,
      R.id.legend_rows_minus, R.id.legend_rows_plus,
      R.id.legend_label_format, R.id.legend_cancel, R.id.legend_save
    };
    for ( int id : buttons ) findViewById( id ).setOnClickListener( this );
    rebuildRows();
    updateCapacity();
  }

  @Override protected void onStart()
  {
    super.onStart();
    Window window = getWindow();
    if ( window != null ) {
      int height = (int)( getContext().getResources().getDisplayMetrics().heightPixels * 0.94f );
      window.setLayout( ViewGroup.LayoutParams.MATCH_PARENT, height );
    }
  }

  @Override public void onBackPressed() { finishCancel(); }

  @Override public void onClick( View view )
  {
    int id = view.getId();
    if ( id == R.id.legend_add_custom ) {
      if ( ensureRowCapacity() ) {
        TitleLegendPointState.Row custom = TitleLegendPointState.Row.custom();
        mRows.add( custom );
        rebuildRows();
        updateCapacity();
        focusLabel( custom.rowId );
        if ( ! mCustomNoteShown ) {
          mCustomNoteShown = true;
          Toast.makeText( getContext(), R.string.title_legend_custom_v1_note, Toast.LENGTH_LONG ).show();
        }
      }
    } else if ( id == R.id.legend_add_symbol ) {
      new LegendSymbolPickerDialog( getContext(), currentIdentities(),
        new LegendSymbolPickerDialog.Listener() {
          @Override public void onSymbolsSelected( List< PlotSymbolUsageSnapshot.Entry > selected ) {
            addSelectedSymbols( selected );
          }
        } ).show();
    } else if ( id == R.id.legend_rescan ) {
      rescan();
    } else if ( id == R.id.legend_columns_minus ) {
      mRequestedColumns = Math.max( TitleLegendPointState.MIN_SHAPE, mRequestedColumns - 1 ); updateCapacity();
    } else if ( id == R.id.legend_columns_plus ) {
      mRequestedColumns = Math.min( TitleLegendPointState.MAX_SHAPE, mRequestedColumns + 1 ); updateCapacity();
    } else if ( id == R.id.legend_rows_minus ) {
      mRowsPerColumn = Math.max( TitleLegendPointState.MIN_SHAPE, mRowsPerColumn - 1 ); updateCapacity();
    } else if ( id == R.id.legend_rows_plus ) {
      mRowsPerColumn = Math.min( TitleLegendPointState.MAX_SHAPE, mRowsPerColumn + 1 ); updateCapacity();
    } else if ( id == R.id.legend_label_format ) {
      new LegendLabelFormatDialog( getContext(), mTextStyle, new LegendLabelFormatDialog.Listener() {
        @Override public void onStyleChanged( SketchTextStyle style ) { mTextStyle = style; }
      } ).show();
    } else if ( id == R.id.legend_cancel ) {
      finishCancel();
    } else if ( id == R.id.legend_save ) {
      finishSave();
    }
  }

  private void showTab( int index )
  {
    boolean legend = index == 1;
    mTitlePanel.setVisibility( legend ? View.GONE : View.VISIBLE );
    mLegendPanel.setVisibility( legend ? View.VISIBLE : View.GONE );
  }

  private void rebuildRows()
  {
    mRowsContainer.removeAllViews();
    for ( int index = 0; index < mRows.size(); ++index ) addRowView( index, mRows.get( index ) );
  }

  private void addRowView( final int index, final TitleLegendPointState.Row row )
  {
    LinearLayout card = new LinearLayout( getContext() );
    card.setOrientation( LinearLayout.VERTICAL );
    card.setPadding( dp( 6 ), dp( 4 ), dp( 4 ), dp( 4 ) );
    GradientDrawable card_background = new GradientDrawable();
    card_background.setColor( 0xff24272f );
    card_background.setCornerRadius( dp( 9 ) );
    card_background.setStroke( dp( 1 ), 0xff3b404b );
    card.setBackground( card_background );
    LinearLayout.LayoutParams card_params = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT );
    card_params.bottomMargin = dp( 5 );

    LinearLayout main = new LinearLayout( getContext() );
    main.setOrientation( LinearLayout.HORIZONTAL );
    main.setGravity( Gravity.CENTER_VERTICAL );

    FrameLayout swatch_frame = new FrameLayout( getContext() );
    GradientDrawable swatch_background = new GradientDrawable();
    swatch_background.setColor( 0xff111318 );
    swatch_background.setStroke( dp( 1 ), 0xff8b919d );
    swatch_background.setCornerRadius( dp( 4 ) );
    swatch_frame.setBackground( swatch_background );
    LegendEditorSwatchView swatch = new LegendEditorSwatchView( getContext() );
    swatch.setRow( row );
    swatch_frame.addView( swatch, new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
    main.addView( swatch_frame, new LinearLayout.LayoutParams( dp( 68 ), dp( 46 ) ) );

    final String accessible_label = row.displayLabel( new TitleLegendLayout.InstalledSymbolResolver() );
    ImageButton edit = iconButton( R.drawable.legend_item_properties,
      R.string.title_legend_edit_row, 7 );
    LinearLayout.LayoutParams edit_params = new LinearLayout.LayoutParams( dp( 48 ), dp( 48 ) );
    edit_params.leftMargin = dp( 5 );
    main.addView( edit, edit_params );
    if ( row.kind == TitleLegendPointState.Kind.CUSTOM ) {
      setIconEnabled( edit, false );
      edit.setContentDescription( getContext().getString( R.string.title_legend_edit_custom_disabled ) );
    }
    edit.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) {
        TitleLegendPointState.Row current = currentRow( row.rowId );
        if ( current == null ) return;
        new LegendRowEditDialog( getContext(), current, new LegendRowEditDialog.Listener() {
          @Override public void onRowChanged( TitleLegendPointState.Row changed ) {
            replaceRow( row.rowId, changed ); rebuildRows();
          }
        } ).show();
      }
    } );

    EditText label = new EditText( getContext() );
    label.setSingleLine( false );
    label.setMaxLines( 3 );
    label.setFilters( new InputFilter[] {
      SketchTextInput.codePointLengthFilter( TitleLegendPointState.MAX_TEXT_CODEPOINTS ) } );
    label.setTextColor( Color.WHITE );
    label.setHintTextColor( 0xff8d929b );
    label.setText( row.userLabel == null
      ? row.displayLabel( new TitleLegendLayout.InstalledSymbolResolver() ) : row.userLabel );
    label.setSelectAllOnFocus( false );
    LinearLayout.LayoutParams label_params = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f );
    label_params.leftMargin = dp( 6 );
    main.addView( label, label_params );
    label.setTag( row.rowId );
    label.addTextChangedListener( new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence s, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence s, int start, int before, int count ) {
        String value = s == null ? "" : s.toString();
        if ( value.codePointCount( 0, value.length() ) <= TitleLegendPointState.MAX_TEXT_CODEPOINTS ) {
          replaceRow( row.rowId, row.withLabel( value ) );
        }
      }
      @Override public void afterTextChanged( Editable editable ) { }
    } );

    LinearLayout moves = new LinearLayout( getContext() );
    moves.setOrientation( LinearLayout.HORIZONTAL );
    moves.setGravity( Gravity.CENTER_VERTICAL );
    ImageButton up = iconButton( R.drawable.legend_move_up, R.string.title_legend_move_up, 8 );
    ImageButton down = iconButton( R.drawable.legend_move_down, R.string.title_legend_move_down, 8 );
    up.setContentDescription( getContext().getString( R.string.title_legend_move_up_named, accessible_label ) );
    down.setContentDescription( getContext().getString( R.string.title_legend_move_down_named, accessible_label ) );
    setIconEnabled( up, index > 0 );
    setIconEnabled( down, index + 1 < mRows.size() );
    moves.addView( up, new LinearLayout.LayoutParams( dp( 48 ), dp( 48 ) ) );
    moves.addView( down, new LinearLayout.LayoutParams( dp( 48 ), dp( 48 ) ) );
    main.addView( moves );
    up.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { moveRow( index, index - 1 ); }
    } );
    down.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { moveRow( index, index + 1 ); }
    } );

    ImageButton remove = iconButton( R.drawable.iz_delete_transp,
      R.string.title_legend_delete_row, 0 );
    remove.setContentDescription( getContext().getString( R.string.title_legend_delete_named, accessible_label ) );
    main.addView( remove, new LinearLayout.LayoutParams( dp( 46 ), dp( 48 ) ) );
    remove.setOnClickListener( new View.OnClickListener() {
      @Override public void onClick( View view ) { removeRow( row ); }
    } );
    card.addView( main );

    String status = rowStatus( row );
    if ( status != null ) {
      TextView badge = new TextView( getContext() );
      badge.setText( status );
      badge.setTextSize( 12.0f );
      badge.setTextColor( status.equals( getContext().getString( R.string.title_legend_unavailable ) )
        ? 0xffff8d8d : 0xffffc768 );
      badge.setPadding( dp( 78 ), dp( 2 ), 0, 0 );
      card.addView( badge );
    }
    mRowsContainer.addView( card, card_params );
  }

  private ImageButton iconButton( int drawable, int description, int padding )
  {
    ImageButton button = new ImageButton( getContext() );
    button.setImageResource( drawable );
    button.setScaleType( ImageView.ScaleType.CENTER_INSIDE );
    int inset = dp( padding );
    button.setPadding( inset, inset, inset, inset );
    button.setBackground( iconTouchBackground() );
    button.setContentDescription( getContext().getString( description ) );
    return button;
  }

  private void setIconEnabled( ImageButton button, boolean enabled )
  {
    button.setEnabled( enabled );
    button.setAlpha( enabled ? 1.0f : 0.28f );
  }

  private String rowStatus( TitleLegendPointState.Row row )
  {
    if ( row.kind == TitleLegendPointState.Kind.CUSTOM ) return null;
    TitleLegendLayout.InstalledSymbolResolver resolver = new TitleLegendLayout.InstalledSymbolResolver();
    if ( resolver.libraryIndex( row.kind, row.thName ) < 0 ) {
      return getContext().getString( R.string.title_legend_unavailable );
    }
    if ( ! mUsage.contains( TitleLegendPointState.toUsageKind( row.kind ), row.thName ) ) {
      return getContext().getString( R.string.title_legend_not_used );
    }
    return null;
  }

  private void replaceRow( String row_id, TitleLegendPointState.Row replacement )
  {
    for ( int i = 0; i < mRows.size(); ++i ) {
      if ( mRows.get( i ).rowId.equals( row_id ) ) { mRows.set( i, replacement ); return; }
    }
  }

  private void moveRow( int from, int to )
  {
    if ( from < 0 || from >= mRows.size() || to < 0 || to >= mRows.size() ) return;
    TitleLegendPointState.Row row = mRows.remove( from );
    mRows.add( to, row );
    rebuildRows();
    if ( mRowsContainer != null ) {
      mRowsContainer.announceForAccessibility( getContext().getString(
        R.string.title_legend_moved_announcement,
        row.displayLabel( new TitleLegendLayout.InstalledSymbolResolver() ), to + 1, mRows.size() ) );
    }
  }

  private void removeRow( TitleLegendPointState.Row row )
  {
    int index = findRow( row.rowId );
    if ( index < 0 ) return;
    TitleLegendPointState.Row current = mRows.remove( index );
    String identity = current.identity();
    if ( identity != null ) {
      mDismissed.add( identity );
      mRemovedSession.put( identity, current );
    }
    rebuildRows();
    updateCapacity();
  }

  private int findRow( String id )
  {
    for ( int i = 0; i < mRows.size(); ++i ) if ( mRows.get( i ).rowId.equals( id ) ) return i;
    return -1;
  }

  private TitleLegendPointState.Row currentRow( String id )
  {
    int index = findRow( id );
    return index < 0 ? null : mRows.get( index );
  }

  private HashSet< String > currentIdentities()
  {
    HashSet< String > identities = new HashSet<>();
    for ( TitleLegendPointState.Row row : mRows ) if ( row.identity() != null ) identities.add( row.identity() );
    return identities;
  }

  private void addSelectedSymbols( List< PlotSymbolUsageSnapshot.Entry > selected )
  {
    if ( selected == null || selected.isEmpty() ) return;
    HashSet< String > existing = currentIdentities();
    for ( PlotSymbolUsageSnapshot.Entry entry : selected ) {
      if ( mRows.size() >= TitleLegendPointState.MAX_ROWS ) break;
      TitleLegendPointState.Kind kind = entry.kind == PlotSymbolUsageSnapshot.Kind.LINE
        ? TitleLegendPointState.Kind.LINE : entry.kind == PlotSymbolUsageSnapshot.Kind.AREA
        ? TitleLegendPointState.Kind.AREA : TitleLegendPointState.Kind.POINT;
      String identity = kind.name() + ":" + entry.thName;
      if ( existing.contains( identity ) ) continue;
      TitleLegendPointState.Row restored = mRemovedSession.remove( identity );
      mRows.add( restored == null ? TitleLegendPointState.Row.fromEntry( entry ) : restored );
      mDismissed.remove( identity );
      existing.add( identity );
    }
    rebuildRows();
    updateCapacity();
  }

  private void rescan()
  {
    mUsage = mParent.computePlotSymbolUsageSnapshot();
    int before = mRows.size();
    TitleLegendPointState state = buildState().rescan( mUsage );
    mRows.clear();
    mRows.addAll( state.rows );
    int added = mRows.size() - before;
    rebuildRows();
    updateCapacity();
    Toast.makeText( getContext(), added == 0 ? getContext().getString( R.string.title_legend_no_new_symbols )
      : getContext().getString( R.string.title_legend_symbols_added, added ), Toast.LENGTH_SHORT ).show();
  }

  private boolean ensureRowCapacity()
  {
    if ( mRows.size() < TitleLegendPointState.MAX_ROWS ) return true;
    Toast.makeText( getContext(), R.string.title_legend_row_limit, Toast.LENGTH_LONG ).show();
    return false;
  }

  private void updateCapacity()
  {
    if ( mColumnsValue == null ) return;
    mColumnsValue.setText( Integer.toString( mRequestedColumns ) );
    mRowsValue.setText( Integer.toString( mRowsPerColumn ) );
    TitleLegendPointState.Capacity capacity =
      new TitleLegendPointState.Capacity( mRows.size(), mRequestedColumns, mRowsPerColumn );
    if ( capacity.expanded ) {
      int needed_rows = ( mRows.size() + mRequestedColumns - 1 ) / mRequestedColumns;
      mCapacityWarning.setText( getContext().getString( R.string.title_legend_capacity_warning,
        capacity.renderedColumns, mRows.size(), mRequestedColumns, mRowsPerColumn,
        capacity.shortfall, needed_rows ) );
      mCapacityWarning.setVisibility( View.VISIBLE );
      String announcement = mCapacityWarning.getText().toString();
      if ( ! announcement.equals( mLastCapacityAnnouncement ) ) {
        mLastCapacityAnnouncement = announcement;
        mCapacityWarning.announceForAccessibility( announcement );
      }
    } else {
      mCapacityWarning.setVisibility( View.GONE );
      mLastCapacityAnnouncement = null;
    }
  }

  private void focusLabel( final String row_id )
  {
    if ( mRowsContainer == null ) return;
    final View candidate = mRowsContainer.findViewWithTag( row_id );
    if ( ! ( candidate instanceof EditText ) ) return;
    candidate.post( new Runnable() {
      @Override public void run() {
        candidate.requestFocus();
        InputMethodManager keyboard = (InputMethodManager)getContext().getSystemService( Context.INPUT_METHOD_SERVICE );
        if ( keyboard != null ) keyboard.showSoftInput( candidate, InputMethodManager.SHOW_IMPLICIT );
      }
    } );
  }

  private void tintReservedSwitch( Switch control )
  {
    if ( control == null ) return;
    int[][] states = { { android.R.attr.state_checked }, { -android.R.attr.state_checked } };
    control.setThumbTintList( new ColorStateList( states,
      new int[] { TDColor.SKETCH_TOGGLE_ON_TEXT, TDColor.SKETCH_TOGGLE_OFF_TEXT } ) );
    control.setTrackTintList( new ColorStateList( states,
      new int[] { TDColor.SKETCH_TOGGLE_ON, TDColor.SKETCH_TOGGLE_OFF } ) );
  }

  private void styleDialogButtons()
  {
    int[] neutral = {
      R.id.legend_add_custom, R.id.legend_add_symbol, R.id.legend_rescan,
      R.id.legend_columns_minus, R.id.legend_columns_plus,
      R.id.legend_rows_minus, R.id.legend_rows_plus,
      R.id.legend_label_format, R.id.legend_cancel
    };
    for ( int id : neutral ) styleFlatButton( (Button)findViewById( id ), false );
    styleFlatButton( (Button)findViewById( R.id.legend_save ), true );
    ( (Button)findViewById( R.id.legend_columns_minus ) ).setTextSize( 20.0f );
    ( (Button)findViewById( R.id.legend_columns_plus ) ).setTextSize( 20.0f );
    ( (Button)findViewById( R.id.legend_rows_minus ) ).setTextSize( 20.0f );
    ( (Button)findViewById( R.id.legend_rows_plus ) ).setTextSize( 20.0f );
  }

  private void styleFlatButton( Button button, boolean primary )
  {
    if ( button == null ) return;
    button.setAllCaps( false );
    button.setTextSize( 14.0f );
    button.setTextColor( new ColorStateList(
      new int[][] { { -android.R.attr.state_enabled }, { } },
      new int[] { 0xff777b84, primary ? TDColor.SKETCH_TOGGLE_ON_TEXT : 0xffe1e3e8 } ) );
    button.setMinHeight( dp( 48 ) );
    button.setPadding( dp( 8 ), 0, dp( 8 ), 0 );
    button.setElevation( 0.0f );
    button.setStateListAnimator( null );
    button.setBackground( flatButtonBackground( primary ) );
  }

  private StateListDrawable flatButtonBackground( boolean primary )
  {
    StateListDrawable states = new StateListDrawable();
    int border = primary ? 0xff4b8cca : 0xff565b66;
    states.addState( new int[] { -android.R.attr.state_enabled },
      roundedBackground( primary ? 0xff294d70 : 0xff25272c, 0xff343840, 5 ) );
    states.addState( new int[] { android.R.attr.state_pressed },
      roundedBackground( primary ? 0xff4a8dcc : 0xff41454d, border, 5 ) );
    states.addState( new int[] { android.R.attr.state_focused },
      roundedBackground( primary ? 0xff4789c9 : 0xff3b3f47, border, 5 ) );
    states.addState( new int[] { }, roundedBackground(
      primary ? TDColor.SKETCH_TOGGLE_ON : TDColor.SKETCH_TOGGLE_OFF, border, 5 ) );
    return states;
  }

  private StateListDrawable iconTouchBackground()
  {
    StateListDrawable states = new StateListDrawable();
    states.addState( new int[] { android.R.attr.state_pressed },
      roundedBackground( 0x333d7fc0, Color.TRANSPARENT, 24 ) );
    states.addState( new int[] { android.R.attr.state_focused },
      roundedBackground( 0x293d7fc0, Color.TRANSPARENT, 24 ) );
    states.addState( new int[] { },
      roundedBackground( Color.TRANSPARENT, Color.TRANSPARENT, 24 ) );
    return states;
  }

  private GradientDrawable roundedBackground( int fill, int stroke, int radius )
  {
    GradientDrawable background = new GradientDrawable();
    background.setColor( fill );
    background.setCornerRadius( dp( radius ) );
    if ( stroke != Color.TRANSPARENT ) background.setStroke( dp( 1 ), stroke );
    return background;
  }

  private void configureLegendSize()
  {
    int percent = Math.max( LEGEND_SCALE_MIN_PERCENT,
      Math.min( LEGEND_SCALE_MAX_PERCENT, Math.round( mLegendScale * 100.0f ) ) );
    mLegendScale = percent / 100.0f;
    mLegendSize.setMax( LEGEND_SCALE_MAX_PERCENT - LEGEND_SCALE_MIN_PERCENT );
    mLegendSize.setProgress( percent - LEGEND_SCALE_MIN_PERCENT );
    updateLegendSizeValue( percent );
    mLegendSize.setOnSeekBarChangeListener( new SeekBar.OnSeekBarChangeListener() {
      @Override public void onProgressChanged( SeekBar seek_bar, int progress, boolean from_user )
      {
        int value = LEGEND_SCALE_MIN_PERCENT + progress;
        mLegendScale = value / 100.0f;
        updateLegendSizeValue( value );
      }

      @Override public void onStartTrackingTouch( SeekBar seek_bar ) { }
      @Override public void onStopTrackingTouch( SeekBar seek_bar ) { }
    } );
  }

  private void updateLegendSizeValue( int percent )
  {
    mLegendSizeValue.setText( getContext().getString( R.string.title_legend_size_value, percent ) );
    mLegendSize.setContentDescription( getContext().getString(
      R.string.title_legend_size_accessibility, percent ) );
  }

  private TitleLegendPointState buildState()
  {
    return new TitleLegendPointState( mObjectId, false, true, mRequestedColumns,
      mRowsPerColumn, mLegendScale, mTextStyle, mRows, mDismissed );
  }

  private void finishSave()
  {
    if ( mFinished ) return;
    try {
      TitleLegendPointState candidate = buildState();
      String options = mPoint.encodeSpecialOptions( candidate );
      if ( ! SketchTextInput.fitsModifiedUtf( options ) ) {
        Toast.makeText( getContext(), R.string.title_legend_state_too_large, Toast.LENGTH_LONG ).show();
        return;
      }
      Object prepared = mPoint.prepareSpecialState( candidate );
      if ( ! ( prepared instanceof TitleLegendLayout ) ) throw new IllegalStateException( "Legend layout failed" );
      mPoint.commitPreparedSpecialState( candidate, options, prepared );
      mParent.updatePointObject( mPoint );
      mFinished = true;
      dismiss();
    } catch ( JSONException | IllegalArgumentException | IllegalStateException e ) {
      Toast.makeText( getContext(), e.getMessage(), Toast.LENGTH_LONG ).show();
    }
  }

  private void finishCancel()
  {
    if ( mFinished ) return;
    mFinished = true;
    if ( mInitialPlacement ) mParent.cancelUnconfiguredTitleLegendPoint( mPoint );
    dismiss();
  }

  private int dp( int value )
  {
    return Math.round( value * getContext().getResources().getDisplayMetrics().density );
  }
}
