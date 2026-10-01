package com.topodroid.TDX;

import com.topodroid.prefs.TDPrefCat;
import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** General hardware-button bindings, saved as soon as an action is chosen. */
public class ButtonShortcutsActivity extends Activity
{
  private final FrameLayout[][] mCells = new FrameLayout[4][3];
  private final ButtonShortcutBinding[][] mBindings = new ButtonShortcutBinding[4][3];
  private LinearLayout mRoot;
  private ScrollView mScroll;
  private LinearLayout mNotes;
  private Dialog mPicker;
  private ButtonShortcutBinding mEditing;
  private boolean mCompact;

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    getWindow().getDecorView().setSystemUiVisibility( TDSetting.mUiVisibility );
    mCompact = getResources().getConfiguration().screenWidthDp < 480;
    for ( int row = 0; row < 4; ++row ) for ( int gesture = 0; gesture < 3; ++gesture ) {
      mBindings[row][gesture] = new ButtonShortcutBinding( row, gesture );
    }
    mRoot = vertical(); mRoot.setBackgroundColor( color( R.color.toolbar_dock ) );
    mRoot.addView( buildAppBar(), match( dp( 56 ) ) );
    setContentView( mRoot );
    buildBody();
    // Use the measured window width, including split-screen windows, rather than display width.
    mRoot.addOnLayoutChangeListener( ( view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ) -> {
      boolean compact = right - left < dp( 480 );
      if ( compact != mCompact ) {
        mCompact = compact;
        if ( mPicker != null ) mPicker.dismiss();
        buildBody();
      }
    } );
  }

  @Override protected void onResume()
  {
    super.onResume();
    if ( mNotes != null ) refreshBindings();
  }

  @Override protected void onDestroy()
  {
    if ( mPicker != null ) mPicker.dismiss();
    super.onDestroy();
  }

  private View buildAppBar()
  {
    LinearLayout bar = horizontal(); bar.setGravity( Gravity.CENTER_VERTICAL );
    bar.setPadding( inset(), dp( 5 ), inset(), dp( 5 ) );
    bar.setBackgroundColor( color( R.color.toolbar_surface ) );
    TextView back = action( "‹", R.color.toolbar_dim ); back.setTextSize( 26 );
    back.setContentDescription( getString( R.string.button_shortcuts_back ) );
    back.setOnClickListener( view -> finish() );
    bar.addView( back, new LinearLayout.LayoutParams( dp( 34 ), ViewGroup.LayoutParams.MATCH_PARENT ) );
    TextView title = label( R.string.button_shortcuts_title, 17, R.color.toolbar_ink );
    title.setId( R.id.title_text ); title.setTypeface( Typeface.DEFAULT_BOLD );
    bar.addView( title, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1 ) );
    TextView done = ToolbarVisualStyle.action( this, getString( R.string.button_shortcuts_done ), Color.rgb( 8, 34, 42 ) );
    done.setTypeface( Typeface.DEFAULT_BOLD );
    done.setBackground( ToolbarVisualStyle.rounded( this, color( R.color.toolbar_accent ), color( R.color.toolbar_accent ), 0, 1 ) );
    done.setOnClickListener( view -> finish() );
    bar.addView( done, new LinearLayout.LayoutParams( dp( 66 ), dp( 40 ) ) );
    return bar;
  }

  private void buildBody()
  {
    int scrollY = mScroll == null ? 0 : mScroll.getScrollY();
    if ( mScroll != null ) mRoot.removeView( mScroll );
    mScroll = new ScrollView( this ); mScroll.setFillViewport( true );
    LinearLayout body = vertical();
    body.setPadding( 0, 0, 0, inset() );
    ToolsetFlowLayout chips = new ToolsetFlowLayout( this, gap() );
    chips.setPadding( inset(), inset(), inset(), 0 );
    int[] scopes = { R.string.button_shortcuts_scope_general, R.string.button_shortcuts_scope_data, R.string.button_shortcuts_scope_sketch };
    for ( int i = 0; i < scopes.length; ++i ) {
      TextView chip = ToolbarVisualStyle.chip( this, getString( scopes[i] ) );
      chip.setPadding( dp( 14 ), dp( 6 ), dp( 14 ), dp( 6 ) );
      boolean selected = i == 0;
      chip.setBackground( ToolbarVisualStyle.rounded( this,
        color( selected ? R.color.toolbar_accent : R.color.toolbar_surface ),
        color( selected ? R.color.toolbar_accent : R.color.toolbar_grid_line ), 1, 1 ) );
      if ( selected ) {
        chip.setTextColor( color( R.color.toolbar_active_ink ) ); chip.setTypeface( Typeface.DEFAULT_BOLD );
        chip.setSelected( true );
      } else {
        chip.setAlpha( .4f ); chip.setEnabled( false );
        chip.setContentDescription( getString( i == 1 ? R.string.button_shortcuts_scope_data_later : R.string.button_shortcuts_scope_sketch_later ) );
      }
      chip.setClickable( false ); chip.setFocusable( false ); chips.addView( chip );
    }
    body.addView( chips );
    TextView caption = label( R.string.button_shortcuts_scope_caption, 12, R.color.toolbar_dim );
    caption.setPadding( inset(), dp( 6 ), inset(), dp( 4 ) ); body.addView( caption );
    addHeading( body, R.string.button_shortcuts_zone_buttons, false );
    body.addView( buildButtonGrid() );
    LinearLayout legend = horizontal(); legend.setGravity( Gravity.CENTER_VERTICAL );
    legend.setPadding( inset(), dp( 6 ), inset(), 0 );
    TextView mark = label( R.string.button_shortcuts_sketch_mark, 10, R.color.toolbar_dim );
    mark.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); legend.addView( mark );
    TextView explanation = label( R.string.button_shortcuts_legend, 12, R.color.toolbar_dim );
    LinearLayout.LayoutParams explainParams = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1 );
    explainParams.setMarginStart( dp( 6 ) ); legend.addView( explanation, explainParams ); body.addView( legend );
    mNotes = vertical(); mNotes.setPadding( inset(), 0, inset(), 0 ); body.addView( mNotes );
    addHeading( body, R.string.button_shortcuts_zone_touch, true );
    body.addView( buildTouchGrid() );
    addHeading( body, R.string.button_shortcuts_zone_timing, true );
    body.addView( buildTiming() );
    mScroll.addView( body );
    mRoot.addView( mScroll, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1 ) );
    refreshBindings();
    mScroll.post( () -> mScroll.scrollTo( 0, scrollY ) );
  }

  private void addHeading( LinearLayout body, int text, boolean later )
  {
    LinearLayout heading = ToolbarVisualStyle.zoneHeading( this, getString( text ), color( later ? R.color.toolbar_dim : R.color.toolbar_ink ) );
    if ( later ) {
      TextView title = (TextView)heading.getChildAt( 0 );
      title.setTypeface( Typeface.MONOSPACE ); title.setText( headingText( getString( text ), getString( text ).indexOf( '·' ), false ) );
      heading.setAlpha( .6f );
    }
    LinearLayout.LayoutParams params = match( dp( 28 ) ); params.topMargin = dp( 10 ); body.addView( heading, params );
  }

  private LinearLayout grid()
  {
    LinearLayout grid = vertical(); grid.setPadding( inset(), dp( 4 ), inset(), 0 ); return grid;
  }

  private View buildButtonGrid()
  {
    LinearLayout grid = grid(); grid.addView( headerRow( ButtonShortcutBinding.GESTURE_NAMES ) );
    for ( int row = 0; row < 4; ++row ) {
      LinearLayout line = horizontal();
      line.addView( rowLabel( ButtonShortcutBinding.BUTTON_NAMES[row], ButtonShortcutIcon.BUTTON_PEN + row ), labelParams() );
      for ( int gesture = 0; gesture < 3; ++gesture ) {
        ButtonShortcutBinding binding = mBindings[row][gesture];
        FrameLayout cell = new FrameLayout( this ); cell.setTag( binding.key );
        cell.setClickable( true ); cell.setFocusable( true );
        cell.setOnClickListener( view -> showPicker( binding ) ); mCells[row][gesture] = cell;
        line.addView( cell, columnParams( dp( 74 ) ) );
      }
      LinearLayout.LayoutParams params = match( ViewGroup.LayoutParams.WRAP_CONTENT ); params.topMargin = gap(); grid.addView( line, params );
    }
    return grid;
  }

  private View headerRow( int[] names )
  {
    LinearLayout row = horizontal(); row.addView( new View( this ), labelParams() );
    for ( int i = 0; i < names.length; ++i ) {
      LinearLayout header = mCompact ? vertical() : new LinearLayout( this ) {
        @Override protected void onMeasure( int widthSpec, int heightSpec ) {
          TextView title = (TextView)getChildAt( 1 );
          LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)title.getLayoutParams();
          // Keep the normal spacing where it fits, and give the label room at the tablet breakpoint.
          int remaining = MeasureSpec.getSize( widthSpec ) - dp( 20 ) - (int)Math.ceil( title.getPaint().measureText( title.getText().toString() ) );
          params.setMarginStart( Math.max( 0, Math.min( dp( 7 ), remaining ) ) );
          params.resolveLayoutDirection( getLayoutDirection() );
          super.onMeasure( widthSpec, heightSpec );
        }
      };
      header.setGravity( Gravity.CENTER );
      ButtonShortcutIcon glyph = new ButtonShortcutIcon( this, ButtonShortcutIcon.GESTURE_PRESS + i, color( R.color.toolbar_accent ) );
      header.addView( glyph, new LinearLayout.LayoutParams( dp( 20 ), dp( 9 ) ) );
      TextView text = label( names[i], mCompact ? 9.5f : 11, R.color.toolbar_dim );
      text.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); text.setGravity( Gravity.CENTER );
      LinearLayout.LayoutParams params = mCompact
        ? new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT )
        : new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT );
      if ( mCompact ) params.topMargin = dp( 4 ); else params.setMarginStart( dp( 7 ) );
      header.addView( text, params ); row.addView( header, columnParams( dp( 30 ) ) );
    }
    return row;
  }

  private View rowLabel( int name, int icon )
  {
    LinearLayout label = mCompact ? vertical() : horizontal();
    label.setGravity( mCompact ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER_VERTICAL );
    label.setPadding( dp( 2 ), 0, dp( 6 ), 0 );
    label.addView( new ButtonShortcutIcon( this, icon, color( R.color.toolbar_accent ) ),
      new LinearLayout.LayoutParams( dp( mCompact ? 30 : 40 ), dp( mCompact ? 20 : 27 ) ) );
    TextView text = label( name, mCompact ? 12 : 14, R.color.toolbar_ink );
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( mCompact ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
      ViewGroup.LayoutParams.WRAP_CONTENT, mCompact ? 0 : 1 );
    if ( mCompact ) params.topMargin = dp( 3 ); else params.setMarginStart( dp( 10 ) );
    label.addView( text, params ); return label;
  }

  private void refreshBindings()
  {
    for ( int row = 0; row < 4; ++row ) for ( int gesture = 0; gesture < 3; ++gesture ) refreshCell( mBindings[row][gesture] );
    mNotes.removeAllViews();
    if ( rowFilled( 0 ) ) addNote( TDSetting.isHeldNavigationAction( mBindings[0][0].value() )
      || TDSetting.isHeldNavigationAction( mBindings[0][2].value() ) ? R.string.button_shortcuts_note_pen_navigation : R.string.button_shortcuts_note_pen );
    if ( rowFilled( 2 ) || rowFilled( 3 ) ) addNote( R.string.button_shortcuts_note_volume );
    if ( rowFilled( 1 ) ) addNote( R.string.button_shortcuts_note_active );
    for ( int row = 0; row < 4; ++row ) {
      if ( TDSetting.isHeldNavigationAction( mBindings[row][0].value() ) ) {
        addNote( getString( R.string.button_shortcuts_note_press_navigation, getString( ButtonShortcutBinding.BUTTON_NAMES[row] ) ) );
      }
    }
    if ( TDSetting.isHeldNavigationAction( mBindings[3][0].value() ) || TDSetting.isHeldNavigationAction( mBindings[3][2].value() ) ) {
      addNote( R.string.button_shortcuts_note_navigation_recovery );
    }
  }

  private boolean rowFilled( int row )
  {
    for ( ButtonShortcutBinding binding : mBindings[row] ) if ( binding.value() != TDSetting.SPEN_ACTION_NONE ) return true;
    return false;
  }

  private void refreshCell( ButtonShortcutBinding binding )
  {
    FrameLayout cell = mCells[binding.row][binding.gesture]; cell.removeAllViews();
    ButtonShortcutBinding.Action action = ButtonShortcutBinding.action( binding.value() );
    boolean empty = action.value == TDSetting.SPEN_ACTION_NONE;
    cell.setBackground( binding == mEditing ? ToolbarVisualStyle.symbolCell( this, true )
      : empty ? ToolbarVisualStyle.dashedCell( this, R.color.toolbar_dock ) : ToolbarVisualStyle.cell( this, R.color.toolbar_cell ) );
    if ( ! empty ) {
      LinearLayout contents = actionContents( action.value, action.shortName, 26, mCompact ? 12 : 14, R.color.toolbar_ink );
      cell.addView( contents, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
      if ( action.sketchOnly() ) {
        TextView mark = label( R.string.button_shortcuts_sketch_mark, mCompact ? 9 : 10, R.color.toolbar_dim );
        mark.setTypeface( Typeface.MONOSPACE, Typeface.BOLD );
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START );
        params.topMargin = dp( 3 ); params.setMarginStart( dp( 5 ) ); cell.addView( mark, params );
      }
    }
    cell.setContentDescription( getString( R.string.button_shortcuts_cell_description,
      getString( ButtonShortcutBinding.BUTTON_NAMES[binding.row] ),
      getString( ButtonShortcutBinding.GESTURE_NAMES[binding.gesture] ).toLowerCase( Locale.getDefault() ),
      getString( empty ? R.string.button_shortcuts_not_set : action.name ) ) );
    cell.setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_YES );
    for ( int i = 0; i < cell.getChildCount(); ++i ) cell.getChildAt( i ).setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS );
  }

  private LinearLayout actionContents( int icon, int name, int iconDp, int sp, int ink )
  {
    LinearLayout contents = vertical(); contents.setGravity( Gravity.CENTER ); contents.setPadding( dp( 4 ), 0, dp( 4 ), 0 );
    contents.addView( new ButtonShortcutIcon( this, icon, color( ink ) ), new LinearLayout.LayoutParams( dp( iconDp ), dp( iconDp ) ) );
    TextView text = label( name, sp, ink ); text.setGravity( Gravity.CENTER );
    LinearLayout.LayoutParams params = match( ViewGroup.LayoutParams.WRAP_CONTENT ); params.topMargin = dp( 5 ); contents.addView( text, params );
    return contents;
  }

  private void addNote( int text )
  {
    addNote( getString( text ) );
  }

  private void addNote( String text )
  {
    LinearLayout note = horizontal(); note.setGravity( Gravity.TOP ); note.setPadding( 0, dp( 8 ), 0, 0 );
    TextView info = ToolbarVisualStyle.label( this, "i", 10, color( R.color.toolbar_dim ) );
    info.setTypeface( Typeface.SERIF, Typeface.ITALIC ); info.setGravity( Gravity.CENTER );
    info.setBackground( ToolbarVisualStyle.rounded( this, Color.TRANSPARENT, color( R.color.toolbar_dim ), 1, 8 ) );
    LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams( dp( 16 ), dp( 16 ) ); infoParams.topMargin = dp( 1 ); note.addView( info, infoParams );
    TextView message = ToolbarVisualStyle.label( this, text, 13, color( R.color.toolbar_dim ) );
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1 ); params.setMarginStart( dp( 8 ) ); note.addView( message, params );
    mNotes.addView( note );
  }

  private View buildTouchGrid()
  {
    LinearLayout grid = grid(); grid.setAlpha( .4f );
    grid.addView( headerRow( new int[] { R.string.button_shortcuts_col_tap, R.string.button_shortcuts_col_dtap,
      R.string.button_shortcuts_col_hold, R.string.button_shortcuts_col_drag } ) );
    int[] names = { R.string.button_shortcuts_row_t2, R.string.button_shortcuts_row_t3, R.string.button_shortcuts_row_t4 };
    for ( int row = 0; row < 3; ++row ) {
      LinearLayout line = horizontal(); line.addView( rowLabel( names[row], ButtonShortcutIcon.FINGERS_TWO + row ), labelParams() );
      for ( int gesture = 0; gesture < 4; ++gesture ) {
        View cell;
        if ( row == 0 && gesture == 3 ) {
          cell = actionContents( ButtonShortcutIcon.PAN, R.string.button_shortcuts_pan_zoom, 22, 12, R.color.toolbar_dim );
          cell.setBackground( ToolbarVisualStyle.outlinedCell( this, R.color.toolbar_dock, R.color.toolbar_grid_line, R.dimen.toolbar_assignment_stroke ) );
        } else {
          cell = new View( this ); cell.setBackground( ToolbarVisualStyle.dashedCell( this, R.color.toolbar_dock ) );
        }
        line.addView( cell, columnParams( dp( 74 ) ) );
      }
      LinearLayout.LayoutParams params = match( dp( 74 ) ); params.topMargin = gap(); grid.addView( line, params );
    }
    describePlaceholder( grid, R.string.button_shortcuts_touch_later ); return grid;
  }

  private View buildTiming()
  {
    LinearLayout timing = vertical(); timing.setPadding( inset(), 0, inset(), 0 ); timing.setAlpha( .4f );
    int[] names = { R.string.button_shortcuts_timing_hold, R.string.button_shortcuts_timing_double };
    for ( int name : names ) {
      LinearLayout row = horizontal(); row.setGravity( Gravity.CENTER_VERTICAL );
      row.addView( label( name, 14, R.color.toolbar_ink ), new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1 ) );
      TextView value = label( R.string.button_shortcuts_timing_default, 12, R.color.toolbar_dim ); value.setTypeface( Typeface.MONOSPACE ); row.addView( value );
      timing.addView( row, match( dp( 45 ) ) );
      View divider = new View( this ); divider.setBackgroundColor( color( R.color.toolbar_grid_line ) ); timing.addView( divider, match( gap() ) );
    }
    describePlaceholder( timing, R.string.button_shortcuts_timing_later ); return timing;
  }

  private void describePlaceholder( ViewGroup section, int description )
  {
    section.setContentDescription( getString( description ) ); section.setFocusable( false ); section.setClickable( false );
    section.setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_YES );
    for ( int i = 0; i < section.getChildCount(); ++i ) section.getChildAt( i ).setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS );
  }

  private void showPicker( ButtonShortcutBinding binding )
  {
    if ( mPicker != null ) return;
    mEditing = binding; refreshCell( binding );
    Dialog picker = new Dialog( this ); picker.requestWindowFeature( Window.FEATURE_NO_TITLE );
    LinearLayout panel = new LinearLayout( this ) {
      @Override protected void onMeasure( int widthSpec, int heightSpec ) {
        int limit = Math.max( dp( 120 ), mRoot.getHeight() - dp( 24 ) );
        super.onMeasure( widthSpec, MeasureSpec.makeMeasureSpec( Math.min( limit, MeasureSpec.getSize( heightSpec ) ), MeasureSpec.AT_MOST ) );
      }
    };
    panel.setOrientation( LinearLayout.VERTICAL ); panel.setBackgroundColor( color( R.color.toolbar_surface ) );
    View border = new View( this ); border.setBackgroundColor( color( R.color.toolbar_grid_line ) ); panel.addView( border, match( gap() ) );
    String button = getString( ButtonShortcutBinding.BUTTON_NAMES[binding.row] ).toUpperCase( Locale.getDefault() );
    String heading = button + " · " + getString( ButtonShortcutBinding.GESTURE_NAMES[binding.gesture] );
    LinearLayout header = ToolbarVisualStyle.zoneHeading( this, headingText( heading, button.length(), true ), color( R.color.toolbar_ink ) );
    TextView headerText = (TextView)header.getChildAt( 0 ); headerText.setTypeface( Typeface.MONOSPACE );
    header.setPadding( inset(), dp( 12 ), inset(), dp( 8 ) ); panel.addView( header );
    ScrollView scroll = new ScrollView( this ); LinearLayout options = vertical(); options.setPadding( inset(), 0, inset(), 0 );
    for ( ButtonShortcutBinding.Action action : ButtonShortcutBinding.ACTIONS ) {
      if ( action.availableFor( binding.gesture ) ) options.addView( pickerOption( binding, action, picker ) );
    }
    scroll.addView( options ); panel.addView( scroll, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1 ) );
    LinearLayout footer = horizontal(); footer.setGravity( Gravity.END ); footer.setPadding( inset(), dp( 6 ), inset(), inset() );
    TextView cancel = action( getString( R.string.button_shortcuts_cancel ), R.color.toolbar_dim );
    cancel.setPadding( dp( 18 ), 0, dp( 18 ), 0 ); cancel.setBackground( ToolbarVisualStyle.rounded( this, color( R.color.toolbar_surface ), color( R.color.toolbar_grid_line ), 1, 1 ) );
    cancel.setOnClickListener( view -> picker.dismiss() ); footer.addView( cancel, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, dp( 40 ) ) ); panel.addView( footer );
    picker.setContentView( panel ); picker.setCanceledOnTouchOutside( true );
    picker.setOnDismissListener( dialog -> { mPicker = null; mEditing = null; refreshBindings(); } );
    Window window = picker.getWindow();
    window.setBackgroundDrawable( new ColorDrawable( Color.TRANSPARENT ) );
    window.addFlags( WindowManager.LayoutParams.FLAG_DIM_BEHIND ); window.setDimAmount( .5f ); window.setGravity( Gravity.BOTTOM );
    window.getDecorView().setSystemUiVisibility( TDSetting.mUiVisibility );
    mPicker = StylusOnlyInputController.guardDialog( picker ); picker.show();
    window.setLayout( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT );
  }

  private View pickerOption( ButtonShortcutBinding binding, ButtonShortcutBinding.Action action, Dialog picker )
  {
    boolean selected = action.value == binding.value();
    LinearLayout row = horizontal(); row.setGravity( Gravity.CENTER_VERTICAL ); row.setMinimumHeight( dp( 64 ) );
    row.setPadding( dp( 8 ), dp( 8 ), dp( 12 ), dp( 8 ) );
    row.setBackground( selected ? ToolbarVisualStyle.outlinedCell( this, R.color.toolbar_selected_tint, R.color.toolbar_amber, R.dimen.toolbar_assignment_stroke )
      : ToolbarVisualStyle.cell( this, R.color.toolbar_dock ) );
    FrameLayout tile = new FrameLayout( this ); tile.setBackground( ToolbarVisualStyle.cell( this, R.color.toolbar_cell ) );
    tile.addView( new ButtonShortcutIcon( this, action.value, color( R.color.toolbar_ink ) ), new FrameLayout.LayoutParams( dp( 24 ), dp( 24 ), Gravity.CENTER ) );
    row.addView( tile, new LinearLayout.LayoutParams( dp( 44 ), dp( 44 ) ) );
    LinearLayout words = vertical();
    TextView name = label( action.name, 15, R.color.toolbar_ink ); name.setTypeface( Typeface.create( "sans-serif-medium", Typeface.NORMAL ) );
    if ( selected ) {
      SpannableString checked = new SpannableString( getString( action.name ) + " ✓" );
      checked.setSpan( new ForegroundColorSpan( color( R.color.toolbar_amber ) ), checked.length() - 1, checked.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE ); name.setText( checked );
    }
    words.addView( name );
    String description = getString( action.value == TDSetting.SPEN_ACTION_NONE
      ? binding.row >= 2 ? R.string.button_shortcuts_none_volume : R.string.button_shortcuts_none_other : action.description );
    TextView detail = ToolbarVisualStyle.label( this, description, 13, color( R.color.toolbar_dim ) );
    LinearLayout.LayoutParams detailParams = match( ViewGroup.LayoutParams.WRAP_CONTENT ); detailParams.topMargin = dp( 2 ); words.addView( detail, detailParams );
    LinearLayout.LayoutParams wordParams = new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1 ); wordParams.setMarginStart( dp( 12 ) ); row.addView( words, wordParams );
    if ( action.sketchOnly() && ! mCompact ) {
      TextView tag = label( R.string.button_shortcuts_tag_sketch_only, 10, R.color.toolbar_dim ); tag.setTypeface( Typeface.MONOSPACE, Typeface.BOLD );
      tag.setPadding( dp( 6 ), dp( 2 ), dp( 6 ), dp( 2 ) );
      tag.setBackground( ToolbarVisualStyle.rounded( this, Color.TRANSPARENT, color( R.color.toolbar_grid_line ), 1, 1 ) );
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT ); params.setMarginStart( dp( 8 ) ); row.addView( tag, params );
    }
    row.setTag( "action:" + action.value ); row.setSelected( selected ); row.setClickable( true ); row.setFocusable( true );
    row.setContentDescription( getString( R.string.button_shortcuts_option_description, getString( action.name ), description,
      getString( selected ? R.string.button_shortcuts_selected : R.string.button_shortcuts_not_selected ) ) );
    for ( int i = 0; i < row.getChildCount(); ++i ) row.getChildAt( i ).setImportantForAccessibility( View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS );
    row.setOnClickListener( view -> {
      if ( binding.value() != action.value ) TDSetting.updatePreference( new TDPrefHelper( this ), TDPrefCat.PREF_CATEGORY_SPEN, binding.key, Integer.toString( action.value ) );
      picker.dismiss();
    } );
    LinearLayout.LayoutParams params = match( ViewGroup.LayoutParams.WRAP_CONTENT ); params.bottomMargin = gap(); row.setLayoutParams( params );
    return row;
  }

  private SpannableString headingText( String text, int boldEnd, boolean dimSuffix )
  {
    SpannableString styled = new SpannableString( text );
    styled.setSpan( new StyleSpan( Typeface.BOLD ), 0, Math.max( 0, boldEnd ), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
    if ( dimSuffix ) styled.setSpan( new ForegroundColorSpan( color( R.color.toolbar_dim ) ), boldEnd, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
    return styled;
  }

  private LinearLayout.LayoutParams labelParams() { return new LinearLayout.LayoutParams( dp( mCompact ? 96 : 148 ), ViewGroup.LayoutParams.MATCH_PARENT ); }
  private LinearLayout.LayoutParams columnParams( int height ) {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 0, height, 1 ); params.setMarginStart( gap() ); return params;
  }
  private LinearLayout.LayoutParams match( int height ) { return new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, height ); }
  private LinearLayout horizontal() { LinearLayout view = new LinearLayout( this ); view.setOrientation( LinearLayout.HORIZONTAL ); return view; }
  private LinearLayout vertical() { LinearLayout view = new LinearLayout( this ); view.setOrientation( LinearLayout.VERTICAL ); return view; }
  private TextView label( int resource, float sp, int ink ) { return ToolbarVisualStyle.label( this, getString( resource ), sp, color( ink ) ); }
  private TextView action( String text, int ink ) { return ToolbarVisualStyle.action( this, text, color( ink ) ); }
  private int color( int resource ) { return ToolbarVisualStyle.color( this, resource ); }
  private int dp( int value ) { return ToolbarVisualStyle.dp( this, value ); }
  private int inset() { return ToolbarVisualStyle.inset( this ); }
  private int gap() { return ToolbarVisualStyle.gap( this ); }
}
