/* @file ToolbarEditorActivity.java
 *
 * Full-screen, category-first editor for mixed drawing toolbars.
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ToolbarEditorActivity extends Activity
{
  static final String EXTRA_FOCUS_QUICK = "focus_quick";

  private static final int CHROME = Color.rgb( 32, 38, 43 );
  private static final int CHROME_2 = Color.rgb( 42, 50, 56 );
  private static final int CHROME_3 = Color.rgb( 52, 61, 68 );
  private static final int LINE = Color.rgb( 61, 71, 78 );
  private static final int INK = Color.WHITE;
  private static final int DIM = INK;
  private static final int ACCENT = Color.rgb( 82, 192, 212 );
  private static final int AMBER = Color.rgb( 229, 160, 87 );
  private static final int QUICK = Color.rgb( 201, 138, 224 );
  private static final int DANGER = Color.rgb( 226, 120, 110 );

  private static final int DRAG_SYMBOL = 1;
  private static final int DRAG_SLOT = 2;
  private static final int DRAG_ROW = 3;

  private DataHelper mData;
  private long mSurveyId;
  private ToolsetProfile mProfile;
  private ArrayList< ToolsetCatalog.Entry > mCatalog;
  private final HashMap< String, ArrayDeque< ToolsetProfile > > mUndo = new HashMap<>();
  private final HashMap< String, Integer > mBadgeCycle = new HashMap<>();

  private TextView mSaved;
  private TextView mProfileButton;
  private TextView mUndoButton;
  private TextView mDeleteButton;
  private EditText mSearch;
  private ToolsetFlowLayout mCategoryBar;
  private final HashMap< String, TextView > mCategoryButtons = new HashMap<>();
  private final HashMap< Integer, TextView > mTypeButtons = new HashMap<>();
  private ListView mBrowser;
  private ListView mOnCanvas;
  private ListView mConfigured;
  private LinearLayout mQuickRow;
  private TextView mSlotCount;
  private TextView mClearSlot;
  private BrowserAdapter mBrowserAdapter;
  private RowAdapter mOnCanvasAdapter;
  private RowAdapter mConfiguredAdapter;

  private String mCategory = ToolsetCategory.PASSAGES;
  private String mCategoryBeforeSearch = ToolsetCategory.PASSAGES;
  private boolean mSearchActive;
  private int mTypeFilter = SymbolType.UNDEF;
  private int mArmedRow = -1;
  private int mArmedSlot = -1;
  private boolean mArmedQuick;
  private boolean mLastSaveSucceeded = true;
  private Handler mSaveHandler;
  private boolean mSavePending;
  private final Runnable mSaveRunnable = new Runnable() { @Override public void run() { save(); } };

  @Override protected void onCreate( Bundle state )
  {
    super.onCreate( state );
    mData = TopoDroidApp.mData;
    mSaveHandler = new Handler( Looper.getMainLooper() );
    mSurveyId = TDInstance.sid;
    mProfile = ToolsetRepository.activeProfile( mData, mSurveyId );
    mCatalog = ToolsetCatalog.all();
    setContentView( buildScreen() );
    refreshAll();
    if ( getIntent() != null && getIntent().getBooleanExtra( EXTRA_FOCUS_QUICK, false ) ) {
      mArmedQuick = true;
      mArmedSlot = firstEmpty( mProfile.mQuick, ToolsetProfile.QUICK_CAPACITY );
      if ( mArmedSlot < 0 ) mArmedSlot = 0;
      mQuickRow.post( new Runnable() { @Override public void run() { refreshRows(); } } );
    }
  }

  @Override public void onBackPressed() { requestExit(); }

  @Override protected void onPause()
  {
    if ( mSavePending ) save();
    super.onPause();
  }

  @Override protected void onDestroy()
  {
    if ( mSaveHandler != null ) mSaveHandler.removeCallbacks( mSaveRunnable );
    super.onDestroy();
  }

  private View buildScreen()
  {
    LinearLayout root = vertical();
    root.setBackgroundColor( CHROME );
    root.addView( buildAppBar(), lpMatch( dp( 56 ) ) );
    root.addView( buildSearchBar(), lpMatch( dp( 56 ) ) );
    mCategoryBar = new ToolsetFlowLayout( this, dp( 5 ) );
    mCategoryBar.setPadding( dp( 10 ), dp( 6 ), dp( 10 ), dp( 6 ) );
    for ( String category : ToolsetCategory.orderedIds() ) {
      final String id = category;
      TextView chip = chip( categoryIcon( id ) + "  " + ToolsetCategory.label( id ) );
      chip.setOnClickListener( new View.OnClickListener() {
        @Override public void onClick( View view ) { mCategory = id; refreshBrowser(); }
      } );
      mCategoryButtons.put( id, chip );
      mCategoryBar.addView( chip );
    }
    root.addView( mCategoryBar, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT ) );

    LinearLayout body = vertical();
    mBrowser = persistentList();
    mBrowserAdapter = new BrowserAdapter();
    mBrowser.setAdapter( mBrowserAdapter );
    mBrowser.setOnDragListener( new View.OnDragListener() {
      @Override public boolean onDrag( View view, DragEvent event ) {
        if ( event.getAction() == DragEvent.ACTION_DROP && event.getLocalState() instanceof DragPayload ) {
          DragPayload payload = (DragPayload)event.getLocalState();
          if ( payload.mKind == DRAG_SLOT ) clearSourceSlot( payload );
        }
        return true;
      }
    } );
    body.addView( mBrowser, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );
    body.addView( buildRowsPanel(), new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.45f ) );
    root.addView( body, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );
    return root;
  }

  private View buildAppBar()
  {
    LinearLayout bar = horizontal();
    bar.setGravity( Gravity.CENTER_VERTICAL );
    bar.setPadding( dp( 7 ), dp( 5 ), dp( 7 ), dp( 5 ) );
    bar.setBackgroundColor( CHROME_2 );
    TextView back = action( "‹", DIM );
    back.setTextSize( 26 );
    back.setContentDescription( "Back" );
    back.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { requestExit(); } } );
    bar.addView( back, new LinearLayout.LayoutParams( dp( 34 ), ViewGroup.LayoutParams.MATCH_PARENT ) );
    TextView title = label( "Toolbars", 17, INK );
    title.setTypeface( Typeface.DEFAULT_BOLD );
    bar.addView( title, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    mSaved = label( "Saved", 12, DIM );
    mSaved.setPadding( dp( 4 ), 0, dp( 4 ), 0 );
    bar.addView( mSaved );
    mProfileButton = action( "Default ▾", ACCENT );
    mProfileButton.setMaxWidth( dp( 120 ) );
    mProfileButton.setEllipsize( TextUtils.TruncateAt.END );
    mProfileButton.setBackground( rounded( Color.argb( 36, 82, 192, 212 ), ACCENT, 1, 4 ) );
    mProfileButton.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { showProfileMenu(); } } );
    bar.addView( mProfileButton, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, dp( 40 ) ) );
    mUndoButton = action( "↶", INK );
    mUndoButton.setTextSize( 20 );
    mUndoButton.setContentDescription( "Undo toolbar change" );
    mUndoButton.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { undo(); } } );
    bar.addView( mUndoButton, new LinearLayout.LayoutParams( dp( 42 ), dp( 40 ) ) );
    mDeleteButton = action( "✕", DANGER );
    mDeleteButton.setTextSize( 28 );
    mDeleteButton.setTypeface( Typeface.DEFAULT_BOLD );
    mDeleteButton.setContentDescription( "Delete profile" );
    mDeleteButton.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { confirmDelete(); } } );
    bar.addView( mDeleteButton, new LinearLayout.LayoutParams( dp( 42 ), dp( 40 ) ) );
    TextView done = action( "Done", Color.rgb( 8, 34, 42 ) );
    done.setTypeface( Typeface.DEFAULT_BOLD );
    done.setGravity( Gravity.CENTER );
    done.setBackground( rounded( ACCENT, ACCENT, 0, 4 ) );
    done.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { requestExit(); } } );
    bar.addView( done, new LinearLayout.LayoutParams( dp( 66 ), dp( 40 ) ) );
    return bar;
  }

  private View buildSearchBar()
  {
    LinearLayout bar = horizontal();
    bar.setGravity( Gravity.CENTER_VERTICAL );
    bar.setPadding( dp( 10 ), dp( 6 ), dp( 10 ), dp( 6 ) );
    mSearch = new EditText( this );
    mSearch.setSingleLine( true );
    mSearch.setTextColor( INK );
    mSearch.setHintTextColor( DIM );
    mSearch.setTextSize( 16 );
    mSearch.setPadding( dp( 10 ), 0, dp( 10 ), 0 );
    mSearch.setBackground( rounded( Color.argb( 72, 0, 0, 0 ), Color.TRANSPARENT, 0, 5 ) );
    mSearch.setHint( "Search " + mCatalog.size() + " symbols" );
    mSearch.setOnTouchListener( new View.OnTouchListener() {
      @Override public boolean onTouch( View view, MotionEvent event ) {
        if ( event.getActionMasked() == MotionEvent.ACTION_DOWN && ! mSearch.hasFocus() ) {
          mSearch.requestFocus();
          InputMethodManager keyboard = (InputMethodManager)getSystemService( INPUT_METHOD_SERVICE );
          if ( keyboard != null ) keyboard.showSoftInput( mSearch, InputMethodManager.SHOW_IMPLICIT );
        }
        return false;
      }
    } );
    mSearch.addTextChangedListener( new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence text, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence text, int start, int before, int count ) {
        boolean searching = text.length() > 0;
        if ( searching && ! mSearchActive ) mCategoryBeforeSearch = mCategory;
        if ( ! searching && mSearchActive ) mCategory = mCategoryBeforeSearch;
        mSearchActive = searching;
        refreshBrowser();
      }
      @Override public void afterTextChanged( Editable editable ) { }
    } );
    bar.addView( mSearch, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f ) );
    int[] types = { SymbolType.UNDEF, SymbolType.POINT, SymbolType.LINE, SymbolType.AREA };
    String[] names = { "ALL", "PT", "LN", "AR" };
    for ( int k = 0; k < types.length; ++k ) {
      final int type = types[k];
      TextView filter = action( names[k], DIM );
      filter.setTextSize( 12 );
      filter.setGravity( Gravity.CENTER );
      filter.setTypeface( Typeface.MONOSPACE );
      filter.setOnClickListener( new View.OnClickListener() {
        @Override public void onClick( View view ) { mTypeFilter = type; refreshBrowser(); }
      } );
      mTypeButtons.put( type, filter );
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( dp( 42 ), dp( 36 ) );
      params.setMarginStart( dp( 3 ) );
      bar.addView( filter, params );
    }
    return bar;
  }

  private View buildRowsPanel()
  {
    LinearLayout panel = vertical();
    panel.setBackgroundColor( Color.argb( 44, 0, 0, 0 ) );
    LinearLayout head = horizontal();
    head.setGravity( Gravity.CENTER_VERTICAL );
    head.setPadding( dp( 10 ), dp( 4 ), dp( 10 ), dp( 4 ) );
    TextView heading = label( "Toolbar rows", 16, INK );
    heading.setTypeface( Typeface.DEFAULT_BOLD );
    head.addView( heading, new LinearLayout.LayoutParams( 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f ) );
    LinearLayout stepper = horizontal();
    stepper.setBackground( rounded( Color.TRANSPARENT, LINE, 1, 4 ) );
    TextView minus = action( "−", INK );
    minus.setGravity( Gravity.CENTER );
    minus.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { changeSlotCount( -1 ); } } );
    mSlotCount = label( "8 slots", 13, INK );
    mSlotCount.setTypeface( Typeface.MONOSPACE );
    mSlotCount.setGravity( Gravity.CENTER );
    TextView plus = action( "+", INK );
    plus.setGravity( Gravity.CENTER );
    plus.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { changeSlotCount( 1 ); } } );
    stepper.addView( minus, new LinearLayout.LayoutParams( dp( 34 ), dp( 40 ) ) );
    stepper.addView( mSlotCount, new LinearLayout.LayoutParams( dp( 82 ), dp( 40 ) ) );
    stepper.addView( plus, new LinearLayout.LayoutParams( dp( 34 ), dp( 40 ) ) );
    head.addView( stepper );
    mClearSlot = action( "Clear slot", DIM );
    mClearSlot.setGravity( Gravity.CENTER );
    mClearSlot.setBackground( rounded( Color.TRANSPARENT, LINE, 1, 4 ) );
    mClearSlot.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { clearArmedSlot(); } } );
    LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams( dp( 104 ), dp( 40 ) );
    clearParams.setMarginStart( dp( 7 ) );
    head.addView( mClearSlot, clearParams );
    panel.addView( head, lpMatch( dp( 52 ) ) );
    panel.addView( zoneHeading( "ENABLED · DRAG ≡ TO REORDER · TAP SLOT TO SELECT · TAP OR DRAG SYMBOLS", INK ), lpMatch( dp( 28 ) ) );
    mOnCanvas = persistentList();
    mOnCanvasAdapter = new RowAdapter( true );
    mOnCanvas.setAdapter( mOnCanvasAdapter );
    mOnCanvas.setOnDragListener( rowZoneDropListener( true ) );
    panel.addView( mOnCanvas, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f ) );
    panel.addView( zoneHeading( "DISABLED · TAP LETTER TO ENABLE/DISABLE ROW", INK ), lpMatch( dp( 28 ) ) );
    mConfigured = persistentList();
    mConfiguredAdapter = new RowAdapter( false );
    mConfigured.setAdapter( mConfiguredAdapter );
    mConfigured.setOnDragListener( rowZoneDropListener( false ) );
    FrameLayout configuredFrame = new FrameLayout( this );
    configuredFrame.addView( mConfigured, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
    TextView configuredEmpty = label( "All rows are on canvas", 14, DIM );
    configuredEmpty.setGravity( Gravity.CENTER );
    configuredEmpty.setPadding( dp( 12 ), dp( 8 ), dp( 12 ), dp( 8 ) );
    configuredFrame.addView( configuredEmpty, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
    mConfigured.setEmptyView( configuredEmpty );
    panel.addView( configuredFrame, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );
    panel.addView( zoneHeading( "QUICK SWITCHER", QUICK ), lpMatch( dp( 28 ) ) );
    mQuickRow = horizontal();
    mQuickRow.setGravity( Gravity.CENTER_VERTICAL );
    mQuickRow.setPadding( dp( 8 ), dp( 4 ), dp( 8 ), dp( 4 ) );
    mQuickRow.setBackgroundColor( Color.argb( 28, 201, 138, 224 ) );
    panel.addView( mQuickRow, lpMatch( dp( 64 ) ) );
    return panel;
  }

  private void refreshAll()
  {
    mProfileButton.setText( mProfile.mName + " ▾" );
    mSlotCount.setText( mProfile.mVisibleSlots + " slots" );
    mDeleteButton.setEnabled( ! mProfile.isDefault() );
    mDeleteButton.setAlpha( mProfile.isDefault() ? 0.35f : 1.0f );
    refreshBrowser();
    refreshRows();
    updateUndoState();
  }

  private void refreshBrowser()
  {
    String query = mSearch == null ? "" : mSearch.getText().toString();
    if ( mBrowserAdapter != null ) {
      mBrowserAdapter.rebuild( ToolsetCatalog.filter( mCatalog, mCategory, mTypeFilter, query ), query.length() > 0 );
      mBrowserAdapter.notifyDataSetChanged();
    }
    for ( String id : ToolsetCategory.orderedIds() ) {
      TextView chip = mCategoryButtons.get( id );
      boolean selected = id.equals( mCategory ) && query.length() == 0;
      chip.setTextColor( selected ? Color.rgb( 8, 34, 42 ) : DIM );
      chip.setTypeface( selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT );
      chip.setBackground( rounded( selected ? ACCENT : Color.TRANSPARENT, selected ? ACCENT : LINE, 1, 15 ) );
    }
    for ( Integer type : mTypeButtons.keySet() ) {
      TextView button = mTypeButtons.get( type );
      boolean selected = type == mTypeFilter;
      button.setTextColor( selected ? INK : DIM );
      button.setBackground( rounded( selected ? CHROME_3 : Color.TRANSPARENT, LINE, 1, 3 ) );
    }
  }

  private void refreshRows()
  {
    if ( mOnCanvasAdapter != null ) { mOnCanvasAdapter.rebuild(); mOnCanvasAdapter.notifyDataSetChanged(); }
    if ( mConfiguredAdapter != null ) { mConfiguredAdapter.rebuild(); mConfiguredAdapter.notifyDataSetChanged(); }
    if ( mQuickRow != null ) buildQuickRow();
    updateClearSlot();
  }

  private void updateClearSlot()
  {
    if ( mClearSlot != null ) {
      boolean armed = mArmedSlot >= 0;
      mClearSlot.setEnabled( armed );
      mClearSlot.setTextColor( armed ? INK : DIM );
      mClearSlot.setAlpha( armed ? 1.0f : 0.5f );
    }
  }

  private void selectSlot( int row, int slot, boolean quick )
  {
    mArmedQuick = quick;
    mArmedRow = quick ? -1 : row;
    mArmedSlot = slot;
    refreshArmedState( mOnCanvas );
    refreshArmedState( mConfigured );
    refreshArmedState( mQuickRow );
    updateClearSlot();
  }

  private void refreshArmedState( View view )
  {
    if ( view == null ) return;
    if ( view instanceof ToolsetSlotView ) {
      ToolsetSlotView slot = (ToolsetSlotView)view;
      slot.setArmed( mArmedSlot == slot.mSlot && mArmedQuick == slot.mQuick
        && ( slot.mQuick || mArmedRow == slot.mRow ) );
      return;
    }
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) refreshArmedState( group.getChildAt( index ) );
    }
  }

  private void refreshVisibleSlot( int row, int slot, boolean quick )
  {
    View root = quick ? mQuickRow : ( mProfile.isOnCanvas( row ) ? mOnCanvas : mConfigured );
    refreshVisibleSlot( root, row, slot, quick );
  }

  private void refreshVisibleSlot( View view, int row, int slot, boolean quick )
  {
    if ( view == null ) return;
    if ( view instanceof ToolsetSlotView ) {
      ToolsetSlotView target = (ToolsetSlotView)view;
      if ( target.mRow == row && target.mSlot == slot && target.mQuick == quick ) target.bindCurrentValue();
      return;
    }
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) refreshVisibleSlot( group.getChildAt( index ), row, slot, quick );
    }
  }

  private void refreshSlotMutationUi( int row, int slot, boolean quick,
                                      ToolsetProfile.Slot first, ToolsetProfile.Slot second )
  {
    refreshVisibleSlot( row, slot, quick );
    refreshArmedState( mOnCanvas );
    refreshArmedState( mConfigured );
    refreshArmedState( mQuickRow );
    updateClearSlot();
    refreshVisibleBrowserAssignments( mBrowser, first, second );
  }

  private void buildQuickRow()
  {
    mQuickRow.removeAllViews();
    TextView tag = rowTag( "Q", QUICK, false );
    mQuickRow.addView( tag, new LinearLayout.LayoutParams( dp( 42 ), dp( 42 ) ) );
    HorizontalScrollView scroll = horizontalScroller();
    LinearLayout slots = horizontal();
    for ( int slot = 0; slot < ToolsetProfile.QUICK_CAPACITY; ++slot ) slots.addView( slotView( -1, slot, true ), slotParams( true ) );
    scroll.addView( slots );
    mQuickRow.addView( scroll, new LinearLayout.LayoutParams( 0, dp( 58 ), 1.0f ) );
  }

  private void mutate( Mutation mutation )
  {
    mutate( mutation, null );
  }

  private void mutate( Mutation mutation, Runnable targetedRefresh )
  {
    undoStack().push( mProfile.copy() );
    if ( undoStack().size() > 100 ) undoStack().removeLast();
    mutation.apply();
    if ( targetedRefresh == null ) refreshAll();
    else { targetedRefresh.run(); updateUndoState(); }
    scheduleSave();
  }

  private void scheduleSave()
  {
    mSavePending = true;
    mSaved.setText( "Saving…" );
    mSaved.setTextColor( DIM );
    mSaveHandler.removeCallbacks( mSaveRunnable );
    mSaveHandler.postDelayed( mSaveRunnable, 150 );
  }

  private void save()
  {
    if ( mSaveHandler != null ) mSaveHandler.removeCallbacks( mSaveRunnable );
    mSavePending = false;
    mSaved.setText( "Saving…" );
    mLastSaveSucceeded = ToolsetRepository.saveProfile( mData, mProfile );
    mSaved.setText( mLastSaveSucceeded ? "Saved" : "Save failed" );
    mSaved.setTextColor( mLastSaveSucceeded ? DIM : DANGER );
  }

  private void requestExit()
  {
    if ( mSavePending ) save();
    if ( mLastSaveSucceeded ) { finish(); return; }
    new AlertDialog.Builder( this )
      .setTitle( "Toolbar changes are not saved" )
      .setMessage( "Retry saving before leaving the editor." )
      .setNegativeButton( "Stay", null )
      .setPositiveButton( "Retry", new DialogInterface.OnClickListener() {
        @Override public void onClick( DialogInterface dialog, int which ) {
          save();
          if ( mLastSaveSucceeded ) finish();
        }
      } ).show();
  }

  private void undo()
  {
    ArrayDeque< ToolsetProfile > stack = undoStack();
    if ( stack.isEmpty() ) return;
    mProfile = stack.pop();
    refreshAll();
    scheduleSave();
  }

  private ArrayDeque< ToolsetProfile > undoStack()
  {
    ArrayDeque< ToolsetProfile > stack = mUndo.get( mProfile.mId );
    if ( stack == null ) { stack = new ArrayDeque<>(); mUndo.put( mProfile.mId, stack ); }
    return stack;
  }

  private void updateUndoState()
  {
    boolean enabled = ! undoStack().isEmpty();
    mUndoButton.setEnabled( enabled );
    mUndoButton.setAlpha( enabled ? 1.0f : 0.35f );
  }

  private void placeEntry( final ToolsetCatalog.Entry entry )
  {
    if ( entry == null ) return;
    if ( mArmedQuick && entry.isQuickSwitcher() ) {
      TDToast.makeWarn( "Place Quick Switcher on a toolbar row" );
      return;
    }
    int row = mArmedQuick ? -1 : mArmedRow;
    int slot = mArmedSlot;
    boolean quick = mArmedQuick;
    if ( slot < 0 ) {
      for ( Integer canvasRow : mProfile.mOnCanvas ) {
        int empty = firstEmpty( mProfile.mRows[canvasRow], mProfile.mVisibleSlots );
        if ( empty >= 0 ) { row = canvasRow; slot = empty; break; }
      }
    }
    if ( slot < 0 ) { TDToast.makeWarn( "No empty visible toolbar slot" ); return; }
    final int targetRow = row;
    final int targetSlot = slot;
    final boolean targetQuick = quick;
    final ToolsetProfile.Slot previous = targetQuick ? mProfile.mQuick[targetSlot] : mProfile.mRows[targetRow][targetSlot];
    final ToolsetProfile.Slot placed = entry.ref();
    mutate( new Mutation() { @Override public void apply() {
      if ( targetQuick ) mProfile.mQuick[targetSlot] = placed;
      else mProfile.mRows[targetRow][targetSlot] = placed;
      armNext( targetRow, targetSlot, targetQuick );
    } }, new Runnable() { @Override public void run() {
      refreshSlotMutationUi( targetRow, targetSlot, targetQuick, previous, placed );
    } } );
  }

  private void clearArmedSlot()
  {
    if ( mArmedSlot < 0 ) return;
    final int row = mArmedRow;
    final int slot = mArmedSlot;
    final boolean quick = mArmedQuick;
    final ToolsetProfile.Slot removed = quick ? mProfile.mQuick[slot] : ( row >= 0 ? mProfile.mRows[row][slot] : null );
    mutate( new Mutation() { @Override public void apply() {
      if ( quick ) mProfile.mQuick[slot] = null; else if ( row >= 0 ) mProfile.mRows[row][slot] = null;
    } }, new Runnable() { @Override public void run() {
      refreshSlotMutationUi( row, slot, quick, removed, null );
    } } );
  }

  private void armNext( int row, int slot, boolean quick )
  {
    int limit = quick ? ToolsetProfile.QUICK_CAPACITY : mProfile.mVisibleSlots;
    mArmedQuick = quick;
    mArmedRow = quick ? -1 : row;
    mArmedSlot = Math.min( limit - 1, slot + 1 );
  }

  private void changeSlotCount( final int delta )
  {
    int next = Math.max( ToolsetProfile.MIN_VISIBLE_SLOTS, Math.min( ToolsetProfile.MAX_VISIBLE_SLOTS, mProfile.mVisibleSlots + delta ) );
    if ( next == mProfile.mVisibleSlots ) return;
    final int value = next;
    mutate( new Mutation() { @Override public void apply() { mProfile.setVisibleSlots( value ); } } );
  }

  private void toggleRow( final int row )
  {
    if ( mProfile.isOnCanvas( row ) && mProfile.mOnCanvas.size() == 1 ) {
      TDToast.makeWarn( "At least one row must remain on canvas" );
      return;
    }
    mutate( new Mutation() { @Override public void apply() {
      if ( mProfile.isOnCanvas( row ) ) mProfile.moveRowOffCanvas( row ); else mProfile.moveRowToCanvasEnd( row );
    } } );
  }

  private View.OnDragListener rowZoneDropListener( final boolean onCanvas )
  {
    return new View.OnDragListener() {
      @Override public boolean onDrag( View view, DragEvent event ) {
        if ( ! ( event.getLocalState() instanceof DragPayload ) ) return true;
        final DragPayload payload = (DragPayload)event.getLocalState();
        if ( payload.mKind != DRAG_ROW ) return true;
        if ( event.getAction() == DragEvent.ACTION_DRAG_ENTERED ) {
          view.setBackgroundColor( Color.argb( 38, 82, 192, 212 ) );
          return true;
        }
        if ( event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DRAG_ENDED ) {
          view.setBackgroundColor( CHROME );
          return true;
        }
        if ( event.getAction() != DragEvent.ACTION_DROP ) return true;
        view.setBackgroundColor( CHROME );
        if ( onCanvas && ! mProfile.isOnCanvas( payload.mRow ) ) {
          mutate( new Mutation() { @Override public void apply() { mProfile.moveRowToCanvasEnd( payload.mRow ); } } );
        } else if ( ! onCanvas && mProfile.isOnCanvas( payload.mRow ) ) {
          if ( mProfile.mOnCanvas.size() == 1 ) TDToast.makeWarn( "At least one row must remain on canvas" );
          else mutate( new Mutation() { @Override public void apply() { mProfile.moveRowOffCanvas( payload.mRow ); } } );
        }
        return true;
      }
    };
  }

  private void dropOnRow( final DragPayload payload, final int targetRow, final int targetPosition, final boolean targetOnCanvas )
  {
    if ( payload == null || payload.mKind != DRAG_ROW ) return;
    if ( ! targetOnCanvas ) {
      if ( mProfile.isOnCanvas( payload.mRow ) && mProfile.mOnCanvas.size() <= 1 ) { TDToast.makeWarn( "At least one row must remain on canvas" ); return; }
      mutate( new Mutation() { @Override public void apply() { mProfile.moveRowOffCanvas( payload.mRow ); } } );
      return;
    }
    mutate( new Mutation() { @Override public void apply() {
      int old = mProfile.mOnCanvas.indexOf( payload.mRow );
      if ( old >= 0 ) mProfile.mOnCanvas.remove( old );
      int insertion = Math.max( 0, Math.min( mProfile.mOnCanvas.size(), targetPosition ) );
      mProfile.mOnCanvas.add( insertion, payload.mRow );
    } } );
  }

  private void dropOnSlot( final DragPayload payload, final int row, final int slot, final boolean quick )
  {
    if ( payload == null || ( payload.mKind != DRAG_SYMBOL && payload.mKind != DRAG_SLOT ) ) return;
    ToolsetProfile.Slot incoming = payload.mKind == DRAG_SYMBOL ? payload.mEntry.ref() : sourceValue( payload );
    if ( quick && ToolsetProfile.isQuickSwitcher( incoming ) ) {
      TDToast.makeWarn( "Place Quick Switcher on a toolbar row" );
      return;
    }
    mutate( new Mutation() { @Override public void apply() {
      ToolsetProfile.Slot value = payload.mKind == DRAG_SYMBOL ? payload.mEntry.ref() : sourceValue( payload );
      if ( value == null ) return;
      if ( payload.mKind == DRAG_SLOT ) clearSourceSlotValue( payload );
      if ( quick ) mProfile.mQuick[slot] = value; else mProfile.mRows[row][slot] = value;
      mArmedQuick = quick; mArmedRow = quick ? -1 : row; mArmedSlot = slot;
    } } );
  }

  private ToolsetProfile.Slot sourceValue( DragPayload payload )
  {
    return payload.mQuick ? mProfile.mQuick[payload.mSlot] : mProfile.mRows[payload.mRow][payload.mSlot];
  }

  private void clearSourceSlot( final DragPayload payload )
  {
    if ( sourceValue( payload ) == null ) return;
    mutate( new Mutation() { @Override public void apply() { clearSourceSlotValue( payload ); } } );
  }

  private void clearSourceSlotValue( DragPayload payload )
  {
    if ( payload.mQuick ) mProfile.mQuick[payload.mSlot] = null;
    else mProfile.mRows[payload.mRow][payload.mSlot] = null;
  }

  private void showProfileMenu()
  {
    final ArrayList< ToolsetProfile > profiles = ToolsetRepository.profiles( mData );
    PopupMenu popup = new PopupMenu( this, mProfileButton );
    for ( int k = 0; k < profiles.size(); ++k ) popup.getMenu().add( 0, 1000 + k, k, profiles.get( k ).mName );
    popup.getMenu().add( 0, 2000, profiles.size() + 1, "Duplicate…" );
    popup.setOnMenuItemClickListener( item -> {
      if ( item.getItemId() == 2000 ) { promptDuplicate(); return true; }
      int index = item.getItemId() - 1000;
      if ( index >= 0 && index < profiles.size() ) switchProfile( profiles.get( index ).mId );
      return true;
    } );
    popup.show();
  }

  private void switchProfile( String id )
  {
    if ( id == null || id.equals( mProfile.mId ) ) return;
    if ( mSavePending ) save();
    if ( ! mLastSaveSucceeded ) {
      save();
      if ( ! mLastSaveSucceeded ) { TDToast.makeBad( "Save the current profile before switching" ); return; }
    }
    if ( ! ToolsetRepository.selectProfile( mData, mSurveyId, id ) ) { TDToast.makeBad( "Could not select profile" ); return; }
    mProfile = ToolsetRepository.profile( mData, id );
    mArmedRow = -1; mArmedSlot = -1; mArmedQuick = false;
    refreshAll();
  }

  private void promptDuplicate()
  {
    final EditText input = new EditText( this );
    input.setSingleLine( true );
    input.setText( mProfile.mName + " copy" );
    input.selectAll();
    new AlertDialog.Builder( this )
      .setTitle( "Duplicate profile" )
      .setView( input )
      .setNegativeButton( "Cancel", null )
      .setPositiveButton( "Duplicate", new DialogInterface.OnClickListener() {
        @Override public void onClick( DialogInterface dialog, int which ) {
          if ( mSavePending ) save();
          if ( ! mLastSaveSucceeded ) { TDToast.makeBad( "Save the current profile before duplicating" ); return; }
          String name = input.getText().toString();
          if ( ! ToolsetRepository.validNewName( mData, name ) ) { TDToast.makeBad( "Use a unique profile name (1–40 characters)" ); return; }
          ToolsetProfile duplicate = ToolsetRepository.duplicate( mData, mProfile, name );
          if ( duplicate == null || ! ToolsetRepository.selectProfile( mData, mSurveyId, duplicate.mId ) ) { TDToast.makeBad( "Could not duplicate profile" ); return; }
          mProfile = duplicate;
          refreshAll();
        }
      } ).show();
  }

  private void confirmDelete()
  {
    if ( mProfile.isDefault() ) return;
    final String id = mProfile.mId;
    final String name = mProfile.mName;
    new AlertDialog.Builder( this )
      .setTitle( "Delete “" + name + "”?" )
      .setMessage( "Surveys using this profile will fall back to Default." )
      .setNegativeButton( "Cancel", null )
      .setPositiveButton( "Delete", new DialogInterface.OnClickListener() {
        @Override public void onClick( DialogInterface dialog, int which ) {
          if ( mSavePending ) save();
          if ( ! mLastSaveSucceeded ) { TDToast.makeBad( "Save the current profile before deleting" ); return; }
          if ( ! ToolsetRepository.deleteProfile( mData, id ) ) { TDToast.makeBad( "Could not delete profile" ); return; }
          ToolsetRepository.selectProfile( mData, mSurveyId, ToolsetProfile.DEFAULT_ID );
          mProfile = ToolsetRepository.profile( mData, ToolsetProfile.DEFAULT_ID );
          refreshAll();
        }
      } ).show();
  }

  private ToolsetSlotView slotView( final int row, final int slot, final boolean quick )
  {
    ToolsetProfile.Slot value = quick ? mProfile.mQuick[slot] : mProfile.mRows[row][slot];
    final ToolsetSlotView view = new ToolsetSlotView( value, row, slot, quick );
    view.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View target ) {
      selectSlot( row, slot, quick );
    } } );
    view.setOnDragListener( new View.OnDragListener() {
      @Override public boolean onDrag( View target, DragEvent event ) {
        if ( event.getLocalState() instanceof DragPayload
          && ((DragPayload)event.getLocalState()).mKind == DRAG_ROW ) return false;
        ToolsetSlotView slotView = (ToolsetSlotView)target;
        if ( event.getAction() == DragEvent.ACTION_DRAG_ENTERED ) slotView.setDropping( true );
        else if ( event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DRAG_ENDED ) slotView.setDropping( false );
        else if ( event.getAction() == DragEvent.ACTION_DROP && event.getLocalState() instanceof DragPayload ) {
          slotView.setDropping( false ); dropOnSlot( (DragPayload)event.getLocalState(), row, slot, quick );
        }
        return true;
      }
    } );
    return view;
  }

  private View.OnTouchListener dragTouch( final DragPayload payload )
  {
    final int slop = ViewConfiguration.get( this ).getScaledTouchSlop();
    final boolean ownsGesture = payload.mKind == DRAG_ROW;
    return new View.OnTouchListener() {
      float downX, downY;
      boolean dragging;
      @Override public boolean onTouch( View view, MotionEvent event ) {
        int action = event.getActionMasked();
        if ( action == MotionEvent.ACTION_DOWN ) {
          downX = event.getX(); downY = event.getY(); dragging = false;
          if ( ownsGesture && view.getParent() != null ) view.getParent().requestDisallowInterceptTouchEvent( true );
          return ownsGesture;
        }
        if ( action == MotionEvent.ACTION_MOVE && ! dragging ) {
          float dx = event.getX() - downX, dy = event.getY() - downY;
          if ( dx * dx + dy * dy > slop * slop ) {
            ClipData data = ClipData.newPlainText( "toolset", "toolset" );
            if ( Build.VERSION.SDK_INT >= 24 ) dragging = view.startDragAndDrop( data, new View.DragShadowBuilder( view ), payload, 0 );
            else dragging = view.startDrag( data, new View.DragShadowBuilder( view ), payload, 0 );
            if ( dragging ) view.performHapticFeedback( HapticFeedbackConstants.LONG_PRESS );
            return dragging;
          }
        }
        if ( action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ) {
          boolean handled = dragging || ownsGesture;
          if ( ownsGesture && view.getParent() != null ) view.getParent().requestDisallowInterceptTouchEvent( false );
          dragging = false;
          return handled;
        }
        return dragging || ownsGesture;
      }
    };
  }

  private void jumpToAssignments( ToolsetCatalog.Entry entry, boolean quickOnly )
  {
    ArrayList< Assignment > matches = assignments( entry );
    if ( matches.isEmpty() ) return;
    ArrayList< Assignment > wanted = new ArrayList<>();
    for ( Assignment assignment : matches ) if ( assignment.mQuick == quickOnly ) wanted.add( assignment );
    if ( wanted.isEmpty() ) return;
    String key = entry.mType + ":" + entry.mSymbol.getFullThName() + ":" + quickOnly;
    int cycle = mBadgeCycle.containsKey( key ) ? mBadgeCycle.get( key ) : 0;
    Assignment assignment = wanted.get( cycle % wanted.size() );
    mBadgeCycle.put( key, cycle + 1 );
    selectSlot( assignment.mRow, assignment.mSlot, assignment.mQuick );
    if ( ! assignment.mQuick ) {
      if ( mProfile.isOnCanvas( assignment.mRow ) ) mOnCanvas.smoothScrollToPosition( mProfile.mOnCanvas.indexOf( assignment.mRow ) );
      else mConfigured.smoothScrollToPosition( configuredRows().indexOf( assignment.mRow ) );
    }
  }

  private ArrayList< Assignment > assignments( ToolsetCatalog.Entry entry )
  {
    ArrayList< Assignment > found = new ArrayList<>();
    if ( entry == null ) return found;
    for ( Integer row : mProfile.mOnCanvas ) addAssignments( found, entry, row );
    for ( Integer row : configuredRows() ) addAssignments( found, entry, row );
    for ( int slot = 0; slot < ToolsetProfile.QUICK_CAPACITY; ++slot ) if ( entry.ref().equals( mProfile.mQuick[slot] ) ) found.add( new Assignment( -1, slot, true ) );
    return found;
  }

  private void addAssignments( ArrayList< Assignment > found, ToolsetCatalog.Entry entry, int row )
  {
    for ( int slot = 0; slot < ToolsetProfile.ROW_CAPACITY; ++slot ) if ( entry.ref().equals( mProfile.mRows[row][slot] ) ) found.add( new Assignment( row, slot, false ) );
  }

  private ArrayList< Integer > configuredRows()
  {
    ArrayList< Integer > rows = new ArrayList<>();
    for ( int row = 0; row < ToolsetProfile.ROW_COUNT; ++row ) if ( ! mProfile.isOnCanvas( row ) ) rows.add( row );
    return rows;
  }

  private static int firstEmpty( ToolsetProfile.Slot[] slots, int limit )
  {
    for ( int k = 0; k < Math.min( slots.length, limit ); ++k ) if ( slots[k] == null ) return k;
    return -1;
  }

  private LinearLayout zoneHeading( String text, int color )
  {
    LinearLayout row = horizontal(); row.setGravity( Gravity.CENTER_VERTICAL ); row.setPadding( dp( 10 ), 0, dp( 10 ), 0 );
    TextView label = label( text, 12, color ); label.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); row.addView( label );
    View line = new View( this ); line.setBackgroundColor( LINE ); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 0, 1, 1.0f ); params.setMarginStart( dp( 8 ) ); row.addView( line, params );
    return row;
  }

  private TextView rowTag( String text, int color, boolean spare )
  {
    TextView tag = label( text, 15, spare ? DIM : Color.rgb( 13, 21, 23 ) );
    tag.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); tag.setGravity( Gravity.CENTER );
    tag.setBackground( rounded( spare ? Color.TRANSPARENT : color, spare ? LINE : color, 1, 4 ) );
    return tag;
  }

  private TextView chip( String text )
  {
    TextView chip = label( text, 14, DIM ); chip.setGravity( Gravity.CENTER ); chip.setPadding( dp( 10 ), dp( 6 ), dp( 10 ), dp( 6 ) ); return chip;
  }

  private TextView action( String text, int color )
  {
    TextView view = label( text, 13, color ); view.setGravity( Gravity.CENTER ); view.setPadding( dp( 7 ), 0, dp( 7 ), 0 ); view.setClickable( true ); view.setFocusable( true ); return view;
  }

  private TextView label( String text, int sp, int color )
  {
    TextView view = new TextView( this ); view.setText( text ); view.setTextSize( sp ); view.setTextColor( color ); view.setGravity( Gravity.CENTER_VERTICAL ); return view;
  }

  private LinearLayout horizontal() { LinearLayout layout = new LinearLayout( this ); layout.setOrientation( LinearLayout.HORIZONTAL ); return layout; }
  private LinearLayout vertical() { LinearLayout layout = new LinearLayout( this ); layout.setOrientation( LinearLayout.VERTICAL ); return layout; }
  private LinearLayout.LayoutParams lpMatch( int height ) { return new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, height ); }
  private LinearLayout.LayoutParams slotParams( boolean quick )
  {
    int reserved = dp( quick ? 58 : 98 );
    int gap = dp( 5 );
    int cells = quick ? ToolsetProfile.QUICK_CAPACITY : ToolsetProfile.DEFAULT_VISIBLE_SLOTS;
    int width = Math.max( dp( 54 ), ( getResources().getDisplayMetrics().widthPixels - reserved ) / cells - gap );
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( width, dp( 54 ) );
    params.setMarginEnd( gap );
    return params;
  }
  private int dp( int value ) { return Math.round( value * getResources().getDisplayMetrics().density ); }

  private HorizontalScrollView horizontalScroller()
  {
    HorizontalScrollView scroll = new HorizontalScrollView( this ); scroll.setHorizontalScrollBarEnabled( true ); scroll.setScrollbarFadingEnabled( false ); scroll.setFillViewport( false ); return scroll;
  }

  private ListView persistentList()
  {
    ListView list = new ListView( this ); list.setDividerHeight( 0 ); list.setBackgroundColor( CHROME ); list.setVerticalScrollBarEnabled( true ); list.setScrollbarFadingEnabled( false ); list.setScrollBarStyle( View.SCROLLBARS_INSIDE_INSET ); return list;
  }

  private GradientDrawable rounded( int fill, int stroke, int strokeWidthDp, int radiusDp )
  {
    GradientDrawable drawable = new GradientDrawable(); drawable.setColor( fill ); drawable.setCornerRadius( dp( radiusDp ) ); if ( strokeWidthDp > 0 ) drawable.setStroke( dp( strokeWidthDp ), stroke ); return drawable;
  }

  private String categoryIcon( String category )
  {
    if ( ToolsetCategory.PASSAGES.equals( category ) ) return "⌒";
    if ( ToolsetCategory.SPELEOTHEMS.equals( category ) ) return "⌄";
    if ( ToolsetCategory.SPELEOCLASTS.equals( category ) ) return "◇";
    if ( ToolsetCategory.HYDROLOGY.equals( category ) ) return "≈";
    if ( ToolsetCategory.GEOLOGY.equals( category ) ) return "∠";
    if ( ToolsetCategory.BIOLOGY.equals( category ) ) return "⌁";
    if ( ToolsetCategory.TEXT_MARKS.equals( category ) ) return "A";
    return "✣";
  }

  private interface Mutation { void apply(); }

  private static final class Assignment
  {
    final int mRow, mSlot; final boolean mQuick;
    Assignment( int row, int slot, boolean quick ) { mRow = row; mSlot = slot; mQuick = quick; }
  }

  private static final class DragPayload
  {
    final int mKind; final ToolsetCatalog.Entry mEntry; final int mRow, mSlot; final boolean mQuick;
    DragPayload( ToolsetCatalog.Entry entry ) { mKind = DRAG_SYMBOL; mEntry = entry; mRow = -1; mSlot = -1; mQuick = false; }
    DragPayload( int row, int slot, boolean quick ) { mKind = DRAG_SLOT; mEntry = null; mRow = row; mSlot = slot; mQuick = quick; }
    DragPayload( int row ) { mKind = DRAG_ROW; mEntry = null; mRow = row; mSlot = -1; mQuick = false; }
  }

  private final class BrowserAdapter extends BaseAdapter
  {
    private final ArrayList< BrowserItem > mItems = new ArrayList<>();

    void rebuild( ArrayList< ToolsetCatalog.Entry > entries, boolean searching )
    {
      mItems.clear();
      if ( entries.isEmpty() ) {
        String query = mSearch == null ? "" : mSearch.getText().toString().trim();
        String message = query.length() == 0 ? "No symbols in this category"
          : "No symbols match “" + query + "” — tap to clear";
        mItems.add( new BrowserItem( message, null ) );
        return;
      }
      String previousGroup = null;
      ArrayList< ToolsetCatalog.Entry > row = null;
      int columns = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 6 : ( getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 390 ? 4 : 3 );
      for ( ToolsetCatalog.Entry entry : entries ) {
        String group = searching ? ToolsetCategory.label( entry.mCategory ) : ToolsetCategory.sectionLabel( entry.mSection );
        if ( group == null && previousGroup == null && mItems.isEmpty() ) group = ToolsetCategory.label( entry.mCategory );
        if ( group != null && ! group.equals( previousGroup ) ) {
          row = null; mItems.add( new BrowserItem( group, null ) ); previousGroup = group;
        }
        if ( row == null || row.size() >= columns ) { row = new ArrayList<>(); mItems.add( new BrowserItem( null, row ) ); }
        row.add( entry );
      }
    }

    @Override public int getCount() { return mItems.size(); }
    @Override public Object getItem( int position ) { return mItems.get( position ); }
    @Override public long getItemId( int position ) { return position; }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType( int position ) { return mItems.get( position ).mHeader == null ? 1 : 0; }

    @Override public View getView( int position, View recycled, ViewGroup parent )
    {
      BrowserItem item = mItems.get( position );
      if ( item.mHeader != null ) {
        TextView header = label( item.mHeader, 15, INK ); header.setTypeface( Typeface.DEFAULT_BOLD ); header.setPadding( dp( 10 ), dp( 7 ), dp( 10 ), dp( 6 ) );
        if ( item.mEntries == null && item.mHeader.contains( "tap to clear" ) ) header.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { mSearch.setText( "" ); } } );
        return header;
      }
      LinearLayout row = horizontal(); row.setPadding( dp( 9 ), dp( 3 ), dp( 9 ), dp( 3 ) );
      int columns = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 6 : ( getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density >= 390 ? 4 : 3 );
      for ( int k = 0; k < columns; ++k ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams( 0, dp( 78 ), 1.0f ); if ( k > 0 ) params.setMarginStart( dp( 5 ) );
        if ( k < item.mEntries.size() ) row.addView( symbolTile( item.mEntries.get( k ) ), params ); else row.addView( new View( ToolbarEditorActivity.this ), params );
      }
      return row;
    }
  }

  private final class RowAdapter extends BaseAdapter
  {
    final boolean mCanvas; final ArrayList< Integer > mRows = new ArrayList<>();
    RowAdapter( boolean canvas ) { mCanvas = canvas; rebuild(); }
    void rebuild() { mRows.clear(); if ( mCanvas ) mRows.addAll( mProfile.mOnCanvas ); else mRows.addAll( configuredRows() ); }
    @Override public int getCount() { return mRows.size(); }
    @Override public Object getItem( int position ) { return mRows.get( position ); }
    @Override public long getItemId( int position ) { return mRows.get( position ); }
    @Override public View getView( final int position, View recycled, ViewGroup parent )
    {
      final int rowIndex = mRows.get( position );
      LinearLayout row = horizontal(); row.setGravity( Gravity.CENTER_VERTICAL ); row.setPadding( dp( 7 ), dp( 3 ), dp( 7 ), dp( 3 ) );
      row.setAlpha( 1.0f ); if ( mCanvas ) row.setBackgroundColor( Color.argb( 14, 82, 192, 212 ) );
      TextView handle = label( "≡", 22, mCanvas ? ACCENT : DIM ); handle.setGravity( Gravity.CENTER ); handle.setContentDescription( "Drag row " + ToolsetProfile.rowName( rowIndex ) ); handle.setOnTouchListener( dragTouch( new DragPayload( rowIndex ) ) );
      row.addView( handle, new LinearLayout.LayoutParams( dp( 30 ), dp( 56 ) ) );
      TextView tag = rowTag( ToolsetProfile.rowName( rowIndex ), ACCENT, ! mCanvas ); tag.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { toggleRow( rowIndex ); } } );
      row.addView( tag, new LinearLayout.LayoutParams( dp( 42 ), dp( 42 ) ) );
      HorizontalScrollView scroll = horizontalScroller(); LinearLayout slots = horizontal(); slots.setPadding( dp( 8 ), 0, 0, 0 );
      for ( int slot = 0; slot < mProfile.mVisibleSlots; ++slot ) slots.addView( slotView( rowIndex, slot, false ), slotParams( false ) );
      scroll.addView( slots ); row.addView( scroll, new LinearLayout.LayoutParams( 0, dp( 58 ), 1.0f ) );
      row.setOnDragListener( new View.OnDragListener() {
        @Override public boolean onDrag( View view, DragEvent event ) {
          if ( event.getAction() == DragEvent.ACTION_DRAG_ENTERED ) {
            view.setBackgroundColor( Color.argb( 45, 82, 192, 212 ) );
          } else if ( event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DRAG_ENDED ) {
            view.setBackgroundColor( mCanvas ? Color.argb( 14, 82, 192, 212 ) : Color.TRANSPARENT );
          } else if ( event.getAction() == DragEvent.ACTION_DROP && event.getLocalState() instanceof DragPayload ) {
            view.setBackgroundColor( mCanvas ? Color.argb( 14, 82, 192, 212 ) : Color.TRANSPARENT );
            dropOnRow( (DragPayload)event.getLocalState(), rowIndex, position, mCanvas );
          }
          return true;
        }
      } );
      return row;
    }
  }

  private View symbolTile( final ToolsetCatalog.Entry entry )
  {
    FrameLayout tile = new FrameLayout( this );
    bindSymbolTile( tile, entry );
    return tile;
  }

  private void bindSymbolTile( final FrameLayout tile, final ToolsetCatalog.Entry entry )
  {
    tile.removeAllViews(); tile.setTag( entry ); tile.setBackground( rounded( CHROME_3, Color.TRANSPARENT, 0, 5 ) ); tile.setContentDescription( entry.mSymbol.getName() + ", " + entry.typeMark() );
    LinearLayout content = vertical(); content.setGravity( Gravity.CENTER );
    if ( entry.isQuickSwitcher() ) {
      TextView preview = quickSwitcherPreview( 24 );
      content.addView( preview, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );
    } else {
      SymbolPreviewButton preview = new SymbolPreviewButton( this ); preview.setBackgroundColor( Color.TRANSPARENT ); preview.bind( entry.mType, entry.mIndex, entry.mSymbol ); preview.setClickable( false );
      content.addView( preview, new LinearLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f ) );
    }
    TextView name = label( entry.mSymbol.getName(), 12, DIM ); name.setGravity( Gravity.CENTER ); name.setSingleLine( true ); name.setEllipsize( TextUtils.TruncateAt.END );
    content.addView( name, lpMatch( dp( 24 ) ) ); tile.addView( content, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) );
    TextView kind = label( entry.typeMark(), 10, DIM ); kind.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); FrameLayout.LayoutParams kindParams = new FrameLayout.LayoutParams( dp( 28 ), dp( 20 ), Gravity.TOP | Gravity.START ); kindParams.setMargins( dp( 5 ), dp( 2 ), 0, 0 ); tile.addView( kind, kindParams );
    ArrayList< Assignment > assignments = assignments( entry ); int rows = 0; Assignment single = null; boolean quick = false;
    boolean[] rowSeen = new boolean[ ToolsetProfile.ROW_COUNT ];
    for ( Assignment assignment : assignments ) {
      if ( assignment.mQuick ) {
        quick = true;
      } else if ( ! rowSeen[assignment.mRow] ) {
        rowSeen[assignment.mRow] = true;
        ++rows;
        if ( single == null ) single = assignment;
      }
    }
    if ( rows > 0 ) {
      String badgeText = rows == 1 ? ToolsetProfile.rowName( single.mRow ) + ( single.mSlot + 1 ) : "×" + rows;
      TextView badge = badge( badgeText, rows == 1 ? AMBER : ACCENT ); badge.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { jumpToAssignments( entry, false ); } } );
      tile.addView( badge, badgeParams( 0 ) );
      if ( rows == 1 ) tile.setBackground( rounded( CHROME_3, Color.argb( 150, 229, 160, 87 ), 1, 5 ) );
    }
    if ( quick ) {
      TextView q = badge( "Q", QUICK ); q.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { jumpToAssignments( entry, true ); } } );
      tile.addView( q, badgeParams( rows > 0 ? dp( 40 ) : 0 ) );
    }
    tile.setOnClickListener( new View.OnClickListener() { @Override public void onClick( View view ) { placeEntry( entry ); } } );
    tile.setOnTouchListener( dragTouch( new DragPayload( entry ) ) );
  }

  private void refreshVisibleBrowserAssignments( View view, ToolsetProfile.Slot first, ToolsetProfile.Slot second )
  {
    if ( view == null ) return;
    Object tag = view.getTag();
    if ( view instanceof FrameLayout && tag instanceof ToolsetCatalog.Entry ) {
      ToolsetCatalog.Entry entry = (ToolsetCatalog.Entry)tag;
      ToolsetProfile.Slot reference = entry.ref();
      if ( reference.equals( first ) || reference.equals( second ) ) bindSymbolTile( (FrameLayout)view, entry );
      return;
    }
    if ( view instanceof ViewGroup ) {
      ViewGroup group = (ViewGroup)view;
      for ( int index = 0; index < group.getChildCount(); ++index ) refreshVisibleBrowserAssignments( group.getChildAt( index ), first, second );
    }
  }

  private TextView badge( String text, int color )
  {
    TextView badge = label( text, 12, Color.rgb( 28, 20, 10 ) ); badge.setTypeface( Typeface.MONOSPACE, Typeface.BOLD ); badge.setGravity( Gravity.CENTER ); badge.setMinWidth( dp( 34 ) ); badge.setPadding( dp( 6 ), 0, dp( 6 ), 0 ); badge.setBackground( rounded( color, CHROME, 1, 4 ) ); badge.setClickable( true ); return badge;
  }

  private TextView quickSwitcherPreview( int textSize )
  {
    TextView preview = label( "Q", textSize, Color.rgb( 36, 16, 44 ) );
    preview.setTypeface( Typeface.DEFAULT_BOLD );
    preview.setGravity( Gravity.CENTER );
    preview.setBackground( rounded( QUICK, QUICK, 0, 5 ) );
    preview.setClickable( false );
    return preview;
  }

  private FrameLayout.LayoutParams badgeParams( int rightMargin )
  {
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams( ViewGroup.LayoutParams.WRAP_CONTENT, dp( 27 ), Gravity.TOP | Gravity.END ); params.setMargins( 0, dp( 2 ), dp( 2 ) + rightMargin, 0 ); return params;
  }

  private final class ToolsetSlotView extends FrameLayout
  {
    final int mRow, mSlot; final boolean mQuick; boolean mFilled, mArmed, mDropping;
    ToolsetSlotView( ToolsetProfile.Slot value, int row, int slot, boolean quick )
    {
      super( ToolbarEditorActivity.this ); mRow = row; mSlot = slot; mQuick = quick; setPadding( dp( 2 ), dp( 2 ), dp( 2 ), dp( 2 ) );
      mArmed = mArmedSlot == slot && mArmedQuick == quick && ( quick || mArmedRow == row );
      bindValue( value );
    }
    void bindCurrentValue()
    {
      bindValue( mQuick ? mProfile.mQuick[mSlot] : mProfile.mRows[mRow][mSlot] );
    }
    void bindValue( ToolsetProfile.Slot value )
    {
      removeAllViews(); mFilled = value != null;
      if ( value != null ) {
        Symbol symbol = ToolsetCatalog.resolve( value ); int index = ToolsetCatalog.resolveIndex( value );
        if ( ToolsetProfile.isQuickSwitcher( value ) ) { addView( quickSwitcherPreview( 24 ), new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) ); setContentDescription( "Quick Switcher, slot " + ( mSlot + 1 ) ); }
        else if ( symbol != null ) { SymbolPreviewButton preview = new SymbolPreviewButton( ToolbarEditorActivity.this ); preview.setBackgroundColor( Color.TRANSPARENT ); preview.bind( value.mType, index, symbol ); preview.setClickable( false ); addView( preview, new FrameLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT ) ); setContentDescription( symbol.getName() + ", slot " + ( mSlot + 1 ) ); }
        else { TextView missing = label( "?", 18, DANGER ); missing.setGravity( Gravity.CENTER ); addView( missing ); setContentDescription( "Missing symbol " + value.mFullThName ); }
      } else setContentDescription( "Empty slot " + ( mSlot + 1 ) );
      setOnTouchListener( value == null ? null : dragTouch( new DragPayload( mRow, mSlot, mQuick ) ) );
      restoreBackground();
    }
    void setArmed( boolean armed ) { if ( mArmed != armed ) { mArmed = armed; restoreBackground(); } }
    void setDropping( boolean dropping )
    {
      mDropping = dropping;
      if ( dropping ) setBackground( rounded( Color.argb( 42, 82, 192, 212 ), ACCENT, 2, 5 ) ); else restoreBackground();
    }
    private void restoreBackground()
    {
      GradientDrawable background = rounded( mFilled ? CHROME_3 : Color.TRANSPARENT, mArmed ? AMBER : LINE, mArmed ? 2 : 1, 5 );
      if ( ! mFilled && ! mArmed ) background.setStroke( dp( 1 ), LINE, dp( 3 ), dp( 3 ) );
      setBackground( background );
    }
  }

  private static final class BrowserItem
  {
    final String mHeader; final ArrayList< ToolsetCatalog.Entry > mEntries;
    BrowserItem( String header, ArrayList< ToolsetCatalog.Entry > entries ) { mHeader = header; mEntries = entries; }
  }
}

/** Small wrapping container used by the always-visible category chips. */
final class ToolsetFlowLayout extends ViewGroup
{
  private final int mGap;
  ToolsetFlowLayout( android.content.Context context, int gap ) { super( context ); mGap = gap; }
  @Override protected void onMeasure( int widthSpec, int heightSpec )
  {
    int width = MeasureSpec.getSize( widthSpec ) - getPaddingLeft() - getPaddingRight(); int x = 0, y = getPaddingTop(), lineHeight = 0;
    for ( int k = 0; k < getChildCount(); ++k ) { View child = getChildAt( k ); measureChild( child, widthSpec, heightSpec ); if ( x > 0 && x + child.getMeasuredWidth() > width ) { x = 0; y += lineHeight + mGap; lineHeight = 0; } x += child.getMeasuredWidth() + mGap; lineHeight = Math.max( lineHeight, child.getMeasuredHeight() ); }
    setMeasuredDimension( MeasureSpec.getSize( widthSpec ), resolveSize( y + lineHeight + getPaddingBottom(), heightSpec ) );
  }
  @Override protected void onLayout( boolean changed, int l, int t, int r, int b )
  {
    int width = r - l - getPaddingLeft() - getPaddingRight(); int x = 0, y = getPaddingTop(), lineHeight = 0;
    for ( int k = 0; k < getChildCount(); ++k ) { View child = getChildAt( k ); if ( x > 0 && x + child.getMeasuredWidth() > width ) { x = 0; y += lineHeight + mGap; lineHeight = 0; } int left = getPaddingLeft() + x; child.layout( left, y, left + child.getMeasuredWidth(), y + child.getMeasuredHeight() ); x += child.getMeasuredWidth() + mGap; lineHeight = Math.max( lineHeight, child.getMeasuredHeight() ); }
  }
}
