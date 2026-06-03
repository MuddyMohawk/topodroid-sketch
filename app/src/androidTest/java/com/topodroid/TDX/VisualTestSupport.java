package com.topodroid.TDX;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.PointF;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoActivityResumedException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.PerformException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.Until;

import com.topodroid.prefs.TDSetting;
import com.topodroid.prefs.TDPrefHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import junit.framework.AssertionFailedError;

final class VisualTestSupport
{
  private static final double SCREEN_MAX_DIFF_RATIO = 0.05;
  private static final int SCREEN_MAX_CHANNEL_DELTA = 8;
  private static final double PNG_MAX_DIFF_RATIO = 0.005;
  private static final int PNG_MAX_CHANNEL_DELTA = 8;

  interface TextNormalizer
  {
    String normalize( String text );
  }

  static final class ReferenceSnapshot
  {
    final String sourceName;
    final float sceneWidth;
    final float sceneHeight;
    final double orientation;
    final int alphaPercent;
    final boolean visible;

    ReferenceSnapshot( String source_name, float scene_width, float scene_height,
                       double orientation_degrees, int alpha_percent, boolean is_visible )
    {
      sourceName = source_name;
      sceneWidth = scene_width;
      sceneHeight = scene_height;
      orientation = orientation_degrees;
      alphaPercent = alpha_percent;
      visible = is_visible;
    }
  }

  static final String PACKAGE_NAME = "com.topodroid.TDX.sketch";
  static final String TEST_PACKAGE = "com.topodroid.TDX.sketch.test";
  static final String DOCUMENTS_UI_PACKAGE = "com.android.documentsui";
  static final String GOOGLE_DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui";
  static final String GOLDEN_PROFILE_DIR = "goldens/emulator_2560x1600_320dpi_font1.0";
  static final String RECORDED_GOLDENS_DIR = "recorded-goldens/emulator_2560x1600_320dpi_font1.0";
  static final String TEST_ARTIFACTS_DIR = "test-artifacts";
  static final long UI_TIMEOUT_MS = 20000L;
  static final long FILE_TIMEOUT_MS = 45000L;

  static final TextNormalizer COMPASS_NORMALIZER = new TextNormalizer() {
    @Override
    public String normalize( String text )
    {
      String normalized = text.replace( "\r\n", "\n" ).replace( '\r', '\n' );
      normalized = normalized.replaceAll( "(?m)^SURVEY DATE: .* COMMENT:(.*)$", "SURVEY DATE: <DATE> COMMENT:$1" );
      normalized = normalized.replaceAll( "[ \t]+\n", "\n" );
      return normalized.trim() + "\n";
    }
  };

  private final Instrumentation mInstrumentation;
  private final Context mTargetContext;
  private final Context mTestContext;
  private final UiDevice mDevice;
  private final boolean mRecordMode;
  private final String mCaseName;
  private static boolean sRecordedGoldensCleared;

  private ActivityScenario< MainWindow > mScenario;
  private boolean mShellIdentityAdopted;
  private boolean mDemoModeEnabled;

  VisualTestSupport( String caseName )
  {
    mInstrumentation = InstrumentationRegistry.getInstrumentation();
    mTargetContext   = mInstrumentation.getTargetContext();
    mTestContext     = mInstrumentation.getContext();
    mDevice          = UiDevice.getInstance( mInstrumentation );
    mCaseName        = sanitizeName( caseName );
    String baselineMode = InstrumentationRegistry.getArguments().getString( "visual_baseline_mode", "" );
    mRecordMode = "record".equalsIgnoreCase( baselineMode );
  }

  String string( int resId )
  {
    return mTargetContext.getString( resId );
  }

  void prepareForCase( List< String > surveyNames ) throws Exception
  {
    adoptShellPermissions();
    closeScenario();
    clearCaseArtifacts();
    configureStablePreferences();
    configureStableRuntimeState();
    disableDialogRExit();
    ensureDataHelperReady();
    // Wipe every known survey (from this run, prior runs, or dev work) so the
    // main list is empty when the test starts. Without this, the list grows
    // across runs and UiScrollable has to fling through dozens of rows to find
    // our target survey. Named-survey cleanup is kept as a belt-and-suspenders
    // pass in case the DB handle wasn't ready when wipeAllSurveys ran.
    wipeAllSurveys();
    cleanupNamedSurveysInDatabase( surveyNames );
    cleanupNamedSurveyArtifacts( surveyNames );
    resetSelectedSurveyState();
    configureStablePreferences();
    configureStableRuntimeState();
    disableDialogRExit();
    waitForIdle();
  }

  void prepareForPhysicalCompatCase() throws Exception
  {
    adoptShellPermissions();
    closeScenario();
    clearCaseArtifacts();
    configureStablePreferences();
    configureStableRuntimeState();
    disableDialogRExit();
    resetSelectedSurveyState();
    waitForIdle();
  }

  private void ensureDataHelperReady() throws Exception
  {
    if ( TopoDroidApp.mData != null ) return;
    launchMainWindow();
    closeScenario();
  }

  private void wipeAllSurveys()
  {
    // Database: delete every survey row regardless of name. Snapshot the list
    // first — doDeleteSurvey mutates the table we just iterated on some
    // branches, and we'd rather not trust the implementation here.
    List< String > allNames = null;
    if ( TopoDroidApp.mData != null ) {
      allNames = TopoDroidApp.mData.selectAllSurveys();
      if ( allNames != null ) {
        for ( String name : allNames ) {
          long sid = TopoDroidApp.mData.getSurveyId( name );
          if ( sid > 0L ) TopoDroidApp.mData.doDeleteSurvey( sid );
        }
      }
    }

    // Filesystem: the public root (/Documents/TopoDroid Sketch) also hosts
    // distox14.sqlite — TopoDroid's main DB. Deleting the whole tree yanks the
    // DB file out from under the live SQLite handle and every subsequent query
    // fails with SQLITE_IOERR_FSTAT. Instead, delete per-survey subdirectories
    // we know about (from both the DB and whatever's on disk) plus the export
    // folders, and leave distox14.sqlite* alone.
    File root = getPublicRoot();
    if ( root.isDirectory() ) {
      if ( allNames != null ) {
        for ( String name : allNames ) deleteRecursively( new File( root, name ) );
      }
      // Catch any survey dirs left over from prior runs whose DB rows were
      // already gone (e.g. pm-clear wiped the DB but the public dir persisted).
      File[] children = root.listFiles();
      if ( children != null ) {
        for ( File child : children ) {
          String name = child.getName();
          if ( ! child.isDirectory() ) continue;
          if ( name.equals( "zip" ) || name.equals( "tmp" )
            || name.equals( "thconfig" ) || name.equals( "c3export" ) ) continue;
          deleteRecursively( child );
        }
      }
      // Wipe the zip/ folder contents so stale exports don't survive, but keep
      // the folder itself — TopoDroid expects it to exist.
      File zipDir = new File( root, "zip" );
      if ( zipDir.isDirectory() ) {
        File[] zipKids = zipDir.listFiles();
        if ( zipKids != null ) {
          for ( File z : zipKids ) deleteRecursively( z );
        }
      }
    }

    // /Downloads copies made by the ZIP-import round-trip test.
    File downloads = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOWNLOADS );
    if ( downloads != null && downloads.isDirectory() ) {
      File[] children = downloads.listFiles();
      if ( children != null ) {
        for ( File child : children ) {
          String n = child.getName();
          if ( n.startsWith( "visual_" ) && n.endsWith( ".zip" ) ) {
            //noinspection ResultOfMethodCallIgnored
            child.delete();
          }
        }
      }
    }
  }

  void launchMainWindow() throws Exception
  {
    launchMainWindow( true );
  }

  void launchMainWindowOnAnyDevice() throws Exception
  {
    launchMainWindow( false );
  }

  private void launchMainWindow( boolean verifyGoldenEmulatorProfile ) throws Exception
  {
    Intent intent = new Intent( mTargetContext, MainWindow.class );
    intent.setAction( Intent.ACTION_VIEW );
    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
    mScenario = ActivityScenario.launch( intent );
    configureStableRuntimeState();
    disableDialogRExit();
    waitForMainWindow();
    ensureSketchLineSymbolsReady();
    if ( verifyGoldenEmulatorProfile ) verifyEmulatorProfile();
  }

  void relaunchMainWindow() throws Exception
  {
    closeScenario();
    launchMainWindow();
  }

  void finish()
  {
    try {
      disableDemoMode();
    } catch ( Exception e ) {
      // ignore cleanup failures in tear-down
    }
    try {
      returnToMainWindowForCleanup();
    } catch ( Throwable t ) {
      Log.w( "VisualTestSupport", "Unable to return to main window before cleanup", t );
    }
    closeScenario();
    if ( mShellIdentityAdopted ) {
      mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
      mShellIdentityAdopted = false;
    }
  }

  void waitForMainWindow()
  {
    waitForMainWindow( UI_TIMEOUT_MS );
  }

  void waitForMainWindow( long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    boolean relaunched = false;
    while ( SystemClock.uptimeMillis() < deadline ) {
      String currentPackage = mDevice.getCurrentPackageName();
      if ( PACKAGE_NAME.equals( currentPackage ) ) {
        if ( dismissStartupDialogsIfPresent() ) continue;
        if ( isObjectPresent( By.res( PACKAGE_NAME, "td_list" ), 250 ) ) return;
      } else if ( isDocumentsUiPackage( currentPackage ) ) {
        // wait for the document picker to return control to the app
      } else if ( ! relaunched ) {
        bringMainWindowToForeground();
        relaunched = true;
        continue;
      }
      SystemClock.sleep( 200 );
    }
    fail( "Timed out waiting for the main survey list after dismissing startup dialogs. currentPackage="
      + mDevice.getCurrentPackageName() );
  }

  void waitForSurveyWindow()
  {
    waitForDisplayedView( R.id.survey_team );
  }

  void waitForShotWindow()
  {
    waitForDisplayedView( R.id.list );
  }

  void waitForDrawingWindow()
  {
    waitForDisplayedView( R.id.drawingSurface );
  }

  void waitForSurveyOnMainList( String surveyName )
  {
    waitForMainWindow( FILE_TIMEOUT_MS );
    long deadline = SystemClock.uptimeMillis() + FILE_TIMEOUT_MS;
    while ( SystemClock.uptimeMillis() < deadline ) {
      UiObject2 row = findSurveyOnMainList( surveyName );
      if ( row != null ) return;
      SystemClock.sleep( 500 );
    }
    fail( "Survey not visible on the main list: " + surveyName );
  }

  void openSurveyWindowFromMainListLongPress( String surveyName )
  {
    for ( int attempt = 0; attempt < 2; ++attempt ) {
      UiObject2 row = requireSurveyOnMainList( surveyName );
      longClickObject( row );
      waitForIdle();
      if ( isViewDisplayed( R.id.survey_team ) ) return;
      if ( isViewDisplayed( R.id.list ) ) {
        pressBackToMainWindow();
        continue;
      }
    }
    waitForSurveyWindow();
  }

  void openSurveyFromMainList( String surveyName )
  {
    UiObject2 row = requireSurveyOnMainList( surveyName );
    clickObject( row );
    waitForShotWindow();
  }

  void openMainImportDialogFromToolbar()
  {
    tapToolbarChild( "listview", 2 );
    waitForDisplayedView( R.id.spin );
  }

  void openNewSurveyDialogFromToolbar()
  {
    tapToolbarChild( "listview", 1 );
    waitForDisplayedView( R.id.survey_name );
  }

  void openPlotListFromShotToolbar()
  {
    tapToolbarChild( "listview", 3 );
    waitForDisplayedView( R.id.plot_new );
  }

  void openManualShotDialogFromShotToolbar()
  {
    if ( isObjectPresent( By.res( PACKAGE_NAME, "shot_from" ), 500 ) ) return;
    tapToolbarChild( "listview", 5 );
    waitForDisplayedView( R.id.shot_from );
  }

  void enterDrawMode()
  {
    // The DrawingWindow toolbar's index-0 button is a mode toggle: in MOVE it
    // enters DRAW, in DRAW it returns to MOVE. If two callers both "defensively"
    // invoke this (e.g. open-sketch setup and the draw helper), the second call
    // flips the mode back to MOVE and every subsequent swipe pans the canvas
    // instead of drawing. Make this idempotent by checking the action-bar title.
    if ( isInDrawMode() ) return;
    tapToolbarChild( "listview", 0 );
    waitForIdle();
    long deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( isInDrawMode() ) return;
      SystemClock.sleep( 100 );
    }
    fail( "Failed to enter DRAW mode; last title=" + getDrawingWindowTitleText() );
  }

  private boolean isInDrawMode()
  {
    String title = getDrawingWindowTitleText();
    if ( title == null ) return false;
    // See DrawingWindow#setTheTitle: draw-mode titles are "<plot>: LINE <name>",
    // "<plot>: POINT <name>", "<plot>: AREA <name>". Move-mode is "<plot>: Moving".
    return title.contains( ": LINE " )
        || title.contains( ": POINT " )
        || title.contains( ": AREA " );
  }

  private String getDrawingWindowTitleText()
  {
    // Pull the title straight off the currently-resumed DrawingWindow. This is
    // more reliable than UI scraping since the ActionBar TextView has no stable
    // resource id on AppCompat themes and the plot-name prefix varies.
    final String[] out = new String[1];
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = findCurrentDrawingWindow();
      if ( window == null ) return;
      CharSequence title = window.getTitle();
      out[0] = ( title != null ) ? title.toString() : null;
    } );
    return out[0];
  }

  private DrawingWindow requireCurrentDrawingWindow()
  {
    DrawingWindow window = findCurrentDrawingWindow();
    if ( window != null ) return window;
    fail( "No active DrawingWindow found; currentPackage=" + mDevice.getCurrentPackageName() );
    return null;
  }

  private DrawingWindow findCurrentDrawingWindow()
  {
    DrawingWindow window = findDrawingWindowInStage( androidx.test.runner.lifecycle.Stage.RESUMED );
    if ( window != null ) return window;
    window = findDrawingWindowInStage( androidx.test.runner.lifecycle.Stage.STARTED );
    if ( window != null ) return window;
    window = findDrawingWindowInStage( androidx.test.runner.lifecycle.Stage.PAUSED );
    if ( window != null ) return window;
    return TopoDroidApp.mDrawingWindow;
  }

  private DrawingWindow findDrawingWindowInStage( androidx.test.runner.lifecycle.Stage stage )
  {
    java.util.Collection< android.app.Activity > activities =
      androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage( stage );
    for ( android.app.Activity activity : activities ) {
      if ( activity instanceof DrawingWindow ) return (DrawingWindow)activity;
    }
    return null;
  }

  private Object getPrivateField( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.get( target );
  }

  private float getPrivateFloat( Object target, String name ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    return field.getFloat( target );
  }

  private void setPrivateField( Object target, String name, Object value ) throws Exception
  {
    Field field = target.getClass().getDeclaredField( name );
    field.setAccessible( true );
    field.set( target, value );
  }

  private DrawingSurface requireCurrentDrawingSurface( DrawingWindow window )
  {
    try {
      DrawingSurface surface = (DrawingSurface)getPrivateField( window, "mDrawingSurface" );
      assertNotNull( "DrawingWindow surface is null", surface );
      return surface;
    } catch ( Exception e ) {
      throw new AssertionFailedError( "Unable to access DrawingWindow surface: " + e.getMessage() );
    }
  }

  private DrawingCommandManager requireCurrentDrawingManager( DrawingWindow window )
  {
    DrawingSurface surface = requireCurrentDrawingSurface( window );
    DrawingCommandManager manager = surface.getManager( window.getPlotType() );
    assertNotNull( "Drawing manager is null for plot type " + window.getPlotType(), manager );
    return manager;
  }

  private DrawingReferencePath requireFirstReferencePoint( DrawingWindow window )
  {
    DrawingCommandManager manager = requireCurrentDrawingManager( window );
    for ( Scrap scrap : manager.getScraps() ) {
      synchronized ( TDPath.mCommandsLock ) {
        for ( ICanvasCommand command : scrap.mCurrentStack ) {
          if ( command instanceof DrawingReferencePath ) return (DrawingReferencePath)command;
        }
      }
    }
    fail( "No reference point found in current drawing" );
    return null;
  }

  private PointF getReferenceHandlePoint( DrawingReferencePath reference, int handleRole )
  {
    switch ( handleRole ) {
      case ReferencePointHelper.HANDLE_SCALE_NW:
        return ReferencePointHelper.getSelectionPoint( reference, handleRole, -0.5f, -0.5f );
      case ReferencePointHelper.HANDLE_SCALE_NE:
        return ReferencePointHelper.getSelectionPoint( reference, handleRole, 0.5f, -0.5f );
      case ReferencePointHelper.HANDLE_SCALE_SE:
        return ReferencePointHelper.getSelectionPoint( reference, handleRole, 0.5f, 0.5f );
      case ReferencePointHelper.HANDLE_SCALE_SW:
        return ReferencePointHelper.getSelectionPoint( reference, handleRole, -0.5f, 0.5f );
      case ReferencePointHelper.HANDLE_ROTATE:
        return ReferencePointHelper.getSelectionPoint( reference, handleRole, 0.0f, 0.0f );
      default:
        fail( "Unsupported reference handle role " + handleRole );
        return new PointF( reference.cx, reference.cy );
    }
  }

  void tapToolbarChild( String containerResName, int childIndex )
  {
    int containerId = mTargetContext.getResources().getIdentifier( containerResName, "id", PACKAGE_NAME );
    assertTrue( "Unknown toolbar container " + containerResName, containerId != 0 );
    tapChildInContainer( containerId, childIndex, containerResName );
  }

  void clickRecentLineButton( int childIndex )
  {
    if ( ItemDrawer.isManualToolbar() ) {
      tapManualToolbarChild( 0, childIndex );
    } else {
      tapChildInContainer( R.id.layout_tool_l, childIndex, "layout_tool_l" );
    }
  }

  /** Tap the recent-line toolbar button for a specific symbol, identified by
   * its therion name (e.g. "u:user-fine"). The position of each symbol in the
   * recent-line palette is not stable across installs — out of the box,
   * TopoDroid sits walls at index 0 and section at index 1, pushing the user
   * sketch lines further right. Hard-coded indices in tests were hitting walls
   * / section by mistake, which in turn opened the cross-section dialog. Look
   * the symbol up at runtime instead so we always click the right button.
   */
  void clickRecentLineByThName( String thName )
  {
    int index = resolveRecentLineIndex( thName );
    assertTrue( "User sketch line not present in recent-line palette: " + thName + "; "
      + "current=" + describeRecentLinePalette(), index >= 0 );
    clickRecentLineButton( index );
  }

  private int resolveRecentLineIndex( String thName )
  {
    String target = Symbol.deprefix_u( thName );
    for ( int k = 0; k < ItemDrawer.getToolbarSlotCount(); ++k ) {
      Symbol symbol = ItemDrawer.mRecentLine[k];
      if ( symbol == null ) continue;
      String full = symbol.getFullThName();
      if ( full == null ) continue;
      if ( full.equals( thName ) ) return k;
      if ( target != null && target.equals( Symbol.deprefix_u( full ) ) ) return k;
    }
    return -1;
  }

  private String describeRecentLinePalette()
  {
    StringBuilder sb = new StringBuilder( "[" );
    for ( int k = 0; k < ItemDrawer.getToolbarSlotCount(); ++k ) {
      if ( k > 0 ) sb.append( "," );
      Symbol symbol = ItemDrawer.mRecentLine[k];
      sb.append( symbol == null ? "null" : symbol.getFullThName() );
    }
    sb.append( "]" );
    return sb.toString();
  }

  void tapPresetButton( int preset )
  {
    tapChildInContainer( R.id.layout_tool_preset, preset - 1, "preset bar" );
  }

  void assertDefaultSketchToolbarVisible()
  {
    assertPresetBarVisible( "Fine", "Smooth", "Straight" );
    assertManualToolbarVisible( 8 );
  }

  private void assertPresetBarVisible( String... expectedLabels )
  {
    onView( withId( R.id.layout_tool_preset ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Preset bar is not a ViewGroup", view instanceof ViewGroup );
      assertTrue( "Preset bar is not visible",
        view.getVisibility() == View.VISIBLE && view.getWidth() > 0 && view.getHeight() > 0 );
      ViewGroup group = (ViewGroup)view;
      assertEquals( "Unexpected preset button count", expectedLabels.length, group.getChildCount() );
      for ( int index = 0; index < expectedLabels.length; ++index ) {
        View child = group.getChildAt( index );
        assertTrue( "Preset button " + index + " is not visible",
          child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0 );
        assertTrue( "Preset button " + index + " has no text", child instanceof TextView );
        assertEquals( "Preset button " + index + " label",
          expectedLabels[index], ((TextView)child).getText().toString() );
      }
    } );
  }

  private void assertManualToolbarVisible( int expectedSlots )
  {
    onView( withId( R.id.layout_tools ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Tools container is not a ViewGroup", view instanceof ViewGroup );
      ViewGroup row = findManualToolbarRow( (ViewGroup)view, 0 );
      assertNotNull( "Manual toolbar row is not visible", row );
      assertTrue( "Manual toolbar row has too few children",
        row.getChildCount() >= expectedSlots + 1 );
      for ( int slot = 0; slot < expectedSlots; ++slot ) {
        View child = row.getChildAt( slot );
        assertTrue( "Manual toolbar slot " + slot + " is not visible",
          child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0 );
      }
    } );
  }

  void selectReferencePointTool()
  {
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = requireCurrentDrawingWindow();
      window.pointSelected( BrushManager.getPointReferenceIndex(), true );
    } );
    waitForIdle();
  }

  void tapDrawingSurfaceNormalized( double x, double y )
  {
    UiObject2 surface = waitForObject( By.res( PACKAGE_NAME, "drawingSurface" ) );
    assertNotNull( "Missing drawing surface", surface );
    int tapX = surface.getVisibleBounds().left + (int)Math.round( surface.getVisibleBounds().width() * x );
    int tapY = surface.getVisibleBounds().top + (int)Math.round( surface.getVisibleBounds().height() * y );
    mDevice.swipe( tapX, tapY, tapX, tapY, 120 );
    waitForIdle();
  }

  void insertReferenceFromFile( File sourceFile, double x, double y ) throws Exception
  {
    assertNotNull( "Reference source file is null", sourceFile );
    assertTrue( "Reference source file does not exist: " + sourceFile.getAbsolutePath(), sourceFile.exists() );
    int before = countReferencePoints();
    final Throwable[] error = new Throwable[1];
    final Uri sourceUri = Uri.fromFile( sourceFile );
    final float normalizedX = (float)x;
    final float normalizedY = (float)y;
    waitForDrawingWindow();
    mInstrumentation.runOnMainSync( () -> {
      try {
        DrawingWindow window = requireCurrentDrawingWindow();
        DrawingSurface surface = requireCurrentDrawingSurface( window );
        int scrap = surface.scrapIndex();
        assertTrue( "Invalid current scrap index", scrap >= 0 );

        float zoom = getPrivateFloat( window, "mZoom" );
        PointF offset = (PointF)getPrivateField( window, "mOffset" );
        assertNotNull( "DrawingWindow offset is null", offset );

        float xCanvas = surface.getWidth() * normalizedX;
        float yCanvas = surface.getHeight() * normalizedY;
        setPrivateField( window, "mPendingReferenceX", xCanvas / zoom - offset.x );
        setPrivateField( window, "mPendingReferenceY", yCanvas / zoom - offset.y );
        setPrivateField( window, "mPendingReferenceScrap", scrap );
        setPrivateField( window, "mPendingReferenceReplace", null );

        Method method = DrawingWindow.class.getDeclaredMethod( "handleReferenceImageResult", Uri.class );
        method.setAccessible( true );
        method.invoke( window, sourceUri );
      } catch ( Throwable t ) {
        error[0] = t;
      }
    } );
    if ( error[0] != null ) {
      Throwable cause = error[0].getCause();
      if ( cause != null ) error[0] = cause;
      throw new AssertionFailedError( "Unable to insert reference image: " + error[0].getMessage() );
    }
    waitForReferenceCount( before + 1 );
  }

  void transformFirstReference( float scaleFactor, double rotationDelta,
                                float alpha, boolean visible, float shiftX, float shiftY )
  {
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = requireCurrentDrawingWindow();
      DrawingReferencePath reference = requireFirstReferencePoint( window );
      if ( shiftX != 0.0f || shiftY != 0.0f ) {
        reference.shiftBy( shiftX, shiftY );
      }
      if ( scaleFactor != 1.0f ) {
        reference.setSceneSize( reference.getSceneWidth() * scaleFactor,
                                reference.getSceneHeight() * scaleFactor );
      }
      if ( rotationDelta != 0.0 ) {
        reference.setOrientation( reference.mOrientation + rotationDelta );
      }
      reference.setReferenceAlpha( alpha );
      reference.setReferenceVisible( visible );
      window.notifyReferencePointChanged( reference );
    } );
    waitForIdle();
  }

  void dragFirstReferenceHandle( int handleRole, float dx, float dy )
  {
    final Throwable[] error = new Throwable[1];
    mInstrumentation.runOnMainSync( () -> {
      try {
        DrawingWindow window = requireCurrentDrawingWindow();
        DrawingCommandManager manager = requireCurrentDrawingManager( window );
        DrawingReferencePath reference = requireFirstReferencePoint( window );
        PointF handlePoint = getReferenceHandlePoint( reference, handleRole );
        float zoom = getPrivateFloat( window, "mZoom" );

        SelectionSet selection = manager.getItemsAt( handlePoint.x, handlePoint.y, zoom, Drawing.FILTER_POINT, 40.0f, 
null );
        assertNotNull( "Selection set is null for reference handle drag", selection );
        assertNotNull( "Reference handle drag did not select a hot item", selection.mHotItem );
        assertEquals( "Unexpected handle role selected for reference drag", handleRole, 
selection.mHotItem.getHandleRole() );

        manager.shiftHotItem( dx, dy );
        window.notifyReferencePointChanged( reference );
      } catch ( Throwable t ) {
        error[0] = t;
      }
    } );
    if ( error[0] != null ) {
      throw new AssertionFailedError( "Unable to drag reference handle: " + error[0].getMessage() );
    }
    waitForIdle();
  }

  ReferenceSnapshot getFirstReferenceSnapshot()
  {
    final ReferenceSnapshot[] snapshot = new ReferenceSnapshot[1];
    mInstrumentation.runOnMainSync( () -> {
      DrawingReferencePath reference = requireFirstReferencePoint( requireCurrentDrawingWindow() );
      snapshot[0] = new ReferenceSnapshot(
        reference.getSourceName(),
        reference.getSceneWidth(),
        reference.getSceneHeight(),
        reference.mOrientation,
        reference.getAlphaPercent(),
        reference.isReferenceVisible()
      );
    } );
    return snapshot[0];
  }

  int countReferencePoints()
  {
    final int[] count = new int[1];
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = requireCurrentDrawingWindow();
      DrawingCommandManager manager = requireCurrentDrawingManager( window );
      int total = 0;
      if ( manager != null ) {
        for ( Scrap scrap : manager.getScraps() ) {
          synchronized ( TDPath.mCommandsLock ) {
            for ( ICanvasCommand command : scrap.mCurrentStack ) {
              if ( command instanceof DrawingReferencePath ) ++ total;
            }
          }
        }
      }
      count[0] = total;
    } );
    return count[0];
  }

  int countSketchLinePaths()
  {
    final int[] count = new int[1];
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = requireCurrentDrawingWindow();
      DrawingCommandManager manager = requireCurrentDrawingManager( window );
      int total = 0;
      if ( manager != null ) {
        for ( Scrap scrap : manager.getScraps() ) {
          synchronized ( TDPath.mCommandsLock ) {
            for ( ICanvasCommand command : scrap.mCurrentStack ) {
              if ( command instanceof DrawingLinePath && ! BrushManager.isLineSection( ((DrawingLinePath)command).mLineType ) ) {
                ++ total;
              }
            }
          }
        }
      }
      count[0] = total;
    } );
    return count[0];
  }

  void waitForReferenceCount( int expected )
  {
    long deadline = SystemClock.uptimeMillis() + FILE_TIMEOUT_MS;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( countReferencePoints() == expected ) return;
      SystemClock.sleep( 200 );
    }
    fail( "Timed out waiting for reference count " + expected + "; actual=" + countReferencePoints() );
  }

  void addStraightLineAcrossFirstReference()
  {
    mInstrumentation.runOnMainSync( () -> {
      DrawingWindow window = requireCurrentDrawingWindow();
      DrawingSurface surface = requireCurrentDrawingSurface( window );
      DrawingReferencePath reference = requireFirstReferencePoint( window );
      float halfWidth = reference.getSceneWidth() * 0.35f;

      DrawingLinePath line = new DrawingLinePath( BrushManager.getLineWallIndex(), surface.scrapIndex() );
      line.addStartPoint( reference.cx - halfWidth, reference.cy );
      line.addPoint( reference.cx, reference.cy );
      line.addPoint( reference.cx + halfWidth, reference.cy );
      line.computeUnitNormal();
      surface.addDrawingPath( line );
    } );
    waitForIdle();
  }

  void eraseAtFirstReferenceCenter( int eraseMode, float eraseSize )
  {
    final Throwable[] error = new Throwable[1];
    mInstrumentation.runOnMainSync( () -> {
      try {
        DrawingWindow window = requireCurrentDrawingWindow();
        DrawingSurface surface = requireCurrentDrawingSurface( window );
        DrawingReferencePath reference = requireFirstReferencePoint( window );
        float zoom = getPrivateFloat( window, "mZoom" );

        EraseCommand command = new EraseCommand();
        surface.eraseAt( reference.cx, reference.cy, zoom, command, eraseMode, eraseSize );
        if ( command.size() > 0 ) {
          command.completeCommand();
          surface.addEraseCommand( command );
        }
      } catch ( Throwable t ) {
        error[0] = t;
      }
    } );
    if ( error[0] != null ) {
      throw new AssertionFailedError( "Unable to erase at reference center: " + error[0].getMessage() );
    }
    waitForIdle();
  }

  void replaceTextInField( int viewId, String value )
  {
    onView( withId( viewId ) ).perform( replaceText( value ) );
    closeSoftKeyboard();
    waitForIdle();
  }

  void tapView( int viewId )
  {
    onView( withId( viewId ) ).perform( click() );
    waitForIdle();
  }

  void setCheckboxChecked( int viewId, boolean checked )
  {
    final boolean[] current = new boolean[1];
    onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Expected a checkable widget for " + viewId, view instanceof CompoundButton );
      current[0] = ((CompoundButton)view).isChecked();
    } );
    if ( current[0] != checked ) {
      tapViewCenter( viewId, "checkbox " + viewId );
    }
    onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Expected a checkable widget for " + viewId, view instanceof CompoundButton );
      assertEquals( "Unexpected checkbox state for " + viewId, checked, ((CompoundButton)view).isChecked() );
    } );
  }

  void setZipSymbolsExportEnabled( boolean enabled )
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( mTargetContext );
    SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean( "DISTOX_ZIP_WITH_SYMBOLS", enabled );
    editor.apply();
    TDPrefHelper.update( "DISTOX_ZIP_WITH_SYMBOLS", enabled );
    TDSetting.mZipWithSymbols = enabled;
  }

  void setReferenceEraseEnabled( boolean enabled )
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( mTargetContext );
    SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean( "DISTOX_ERASE_REFERENCE", enabled );
    editor.apply();
    TDPrefHelper.update( "DISTOX_ERASE_REFERENCE", enabled );
    TDSetting.mEraseReferenceImages = enabled;
  }

  void tapText( String text )
  {
    onView( withText( text ) ).perform( click() );
    waitForIdle();
  }

  void chooseSpinnerValue( int spinnerId, String value )
  {
    onView( withId( spinnerId ) ).perform( click() );
    onData( allOf( is( instanceOf( String.class ) ), is( value ) ) )
      .inRoot( isPlatformPopup() )
      .perform( click() );
    waitForIdle();
  }

  void confirmAlertOk()
  {
    UiObject2 okButton = waitForAnyObject(
      By.res( "android", "button1" ),
      By.text( string( R.string.button_ok ) )
    );
    assertNotNull( "OK button not visible in alert dialog", okButton );
    clickObject( okButton );
  }

  void openCurrentMenuAndClickText( String menuText )
  {
    tapView( R.id.handle );
    waitForObject( By.text( menuText ) );
    tapText( menuText );
  }

  void createSurveyAndOpenShots( String surveyName, String team, String startStation, String comment )
  {
    openNewSurveyDialogFromToolbar();
    replaceTextInField( R.id.survey_name, surveyName );
    replaceTextInField( R.id.survey_team, team );
    replaceTextInField( R.id.survey_station, startStation );
    replaceTextInField( R.id.survey_decl, "0" );
    replaceTextInField( R.id.survey_comment, comment );
    tapView( R.id.surveyOpen );
    waitForShotWindow();
  }

  void addManualShot( String from, String to, String distance, String bearing, String clino, boolean closeAfterSave )
  {
    openManualShotDialogFromShotToolbar();
    replaceTextInField( R.id.shot_from, from );
    replaceTextInField( R.id.shot_to, to );
    replaceTextInField( R.id.shot_distance, distance );
    replaceTextInField( R.id.shot_bearing, bearing );
    replaceTextInField( R.id.shot_clino, clino );
    tapView( closeAfterSave ? R.id.button_ok_shot_name : R.id.button_save_shot_name );
    if ( closeAfterSave ) {
      waitForShotWindow();
    } else {
      waitForObject( By.res( PACKAGE_NAME, "shot_from" ) );
    }
  }

  void openNewPlotFromShotWindow( String plotName, String startStation )
  {
    openPlotListFromShotToolbar();
    tapView( R.id.plot_new );
    replaceTextInField( R.id.edit_plot_name, plotName );
    replaceTextInField( R.id.edit_plot_start, startStation );
    tapView( R.id.btn_ok );
    waitForDrawingWindow();
  }

  void openExistingPlanPlot( String plotName )
  {
    openPlotListFromShotToolbar();
    tapText( plotName + ": PLAN" );
    waitForDrawingWindow();
  }

  void pressBackToShotWindow()
  {
    int attempts = 0;
    while ( attempts < 4 ) {
      if ( isViewDisplayed( R.id.list ) ) return;
      if ( dismissStartupDialogsIfPresent() ) {
        ++ attempts;
        continue;
      }
      mDevice.pressBack();
      waitForIdle();
      SystemClock.sleep( 300 );
      ++ attempts;
    }
    waitForShotWindow();
  }

  void pressBackToMainWindow()
  {
    int attempts = 0;
    while ( attempts < 4 ) {
      if ( dismissStartupDialogsIfPresent() ) continue;
      if ( isViewDisplayed( R.id.td_list ) ) return;
      mDevice.pressBack();
      waitForIdle();
      SystemClock.sleep( 300 );
      ++ attempts;
    }
    waitForMainWindow();
  }

  private void returnToMainWindowForCleanup()
  {
    if ( ! PACKAGE_NAME.equals( mDevice.getCurrentPackageName() ) ) return;
    if ( isViewDisplayed( R.id.td_list ) ) return;
    pressBackToMainWindow();
    waitForIdle();
    SystemClock.sleep( 500 );
  }

  /** Draw a curved stroke on the sketch surface. The path is a quadratic
   * bezier sampled at `samples` points; the control point sits `curveOffset`
   * (in normalized canvas units) perpendicular to the start->end chord. A
   * curved input is required to visually distinguish preset 1 (tight,
   * segment-1 rendering) from preset 2 (smoothed, segment-10 rendering); a
   * straight swipe renders identically under both presets.
   */
  void drawCurveStrokeNormalized( double startX, double startY, double endX, double endY,
                                  double curveOffset, int samples, int segmentSteps )
  {
    UiObject2 surface = waitForObject( By.res( PACKAGE_NAME, "drawingSurface" ) );
    assertNotNull( "Missing drawing surface", surface );
    int left   = surface.getVisibleBounds().left;
    int top    = surface.getVisibleBounds().top;
    int width  = surface.getVisibleBounds().width();
    int height = surface.getVisibleBounds().height();

    double midX  = 0.5 * ( startX + endX );
    double midY  = 0.5 * ( startY + endY );
    double dx    = endX - startX;
    double dy    = endY - startY;
    double len   = Math.sqrt( dx * dx + dy * dy );
    double perpX = ( len == 0 ) ? 0.0 : ( -dy / len );
    double perpY = ( len == 0 ) ? 0.0 : (  dx / len );
    double ctrlX = midX + perpX * curveOffset;
    double ctrlY = midY + perpY * curveOffset;

    int n = Math.max( 3, samples );
    Point[] path = new Point[ n ];
    for ( int i = 0; i < n; ++i ) {
      double t = (double)i / (double)( n - 1 );
      double u = 1.0 - t;
      double x = u * u * startX + 2.0 * u * t * ctrlX + t * t * endX;
      double y = u * u * startY + 2.0 * u * t * ctrlY + t * t * endY;
      path[i] = new Point(
        left + (int)Math.round( width  * x ),
        top  + (int)Math.round( height * y )
      );
    }
    mDevice.swipe( path, Math.max( 1, segmentSteps ) );
    SystemClock.sleep( 500 );
    waitForIdle();
  }

  void setCanonicalToolbarState()
  {
    tapPresetButton( 1 );
    // Resolve by th_name so the "active line" highlight sits on user-fine
    // regardless of where it lives in the recent-line palette on this install.
    clickRecentLineByThName( SketchLineSymbolManager.LEGACY_TH_NAME_FINE );
  }

  File getPublicRoot()
  {
    return new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS ), "TopoDroid Sketch" );
  }

  File getSurveyDir( String surveyName )
  {
    return new File( getPublicRoot(), surveyName );
  }

  File getSurveyOutDir( String surveyName )
  {
    return new File( getSurveyDir( surveyName ), "out" );
  }

  File getZipFile( String surveyName )
  {
    return new File( new File( getPublicRoot(), "zip" ), surveyName + ".zip" );
  }

  File getCaseArtifactsDirectory()
  {
    return getCaseArtifactsDir();
  }

  void deleteGeneratedSurveyAndArtifacts( String surveyName )
  {
    List< String > surveyNames = allSurveyNames( surveyName );
    cleanupNamedSurveysInDatabase( surveyNames );
    cleanupNamedSurveyArtifacts( surveyNames );
    resetSelectedSurveyState();
  }

  File getDownloadFile( String filename )
  {
    return new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOWNLOADS ), filename );
  }

  File getSurveyPhotoFile( String surveyName, String sourceName )
  {
    return new File( new File( getSurveyDir( surveyName ), "photo" ), sourceName );
  }

  File createReferenceFixtureInDownloads( String filename, boolean jpeg ) throws Exception
  {
    File target = getDownloadFile( filename );
    ensureParentDir( target );
    Bitmap bitmap = Bitmap.createBitmap( 160, 100, Bitmap.Config.ARGB_8888 );
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        int color;
        if ( y < 50 ) {
          color = ( x < 80 ) ? 0xffff00ff : 0xff00ffff;
        } else {
          color = ( x < 80 ) ? 0xffffff00 : 0xffff8800;
        }
        bitmap.setPixel( x, y, color );
      }
    }
    FileOutputStream output = new FileOutputStream( target );
    try {
      Bitmap.CompressFormat format = jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
      int quality = jpeg ? 95 : 100;
      assertTrue( "Failed to write reference fixture " + target.getAbsolutePath(),
        bitmap.compress( format, quality, output ) );
    } finally {
      output.close();
      bitmap.recycle();
    }
    return target;
  }

  File getCompassExportFile( String surveyName )
  {
    return new File( getSurveyOutDir( surveyName ), surveyName + ".dat" );
  }

  File getPngExportFile( String surveyName, String filename )
  {
    return new File( getSurveyOutDir( surveyName ), filename );
  }

  File waitForFile( File file, long timeoutMs )
  {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while ( System.currentTimeMillis() < deadline ) {
      if ( file.exists() ) return file;
      SystemClock.sleep( 250 );
    }
    fail( "Timed out waiting for file " + file.getAbsolutePath() );
    return file;
  }

  void assertZipContainsSketchLineSymbols( File zipFile ) throws Exception
  {
    assertTrue( "ZIP export does not exist: " + zipFile.getAbsolutePath(), zipFile.exists() );
    File artifactCopy = new File( getCaseArtifactsDir(), zipFile.getName() );
    copyFile( zipFile, artifactCopy );

    ByteArrayOutputStream nestedZipBytes = new ByteArrayOutputStream();
    ZipFile outerZip = new ZipFile( zipFile );
    try {
      ZipEntry linesZipEntry = outerZip.getEntry( "lines.zip" );
      assertNotNull( "ZIP export is missing lines.zip", linesZipEntry );
      InputStream input = outerZip.getInputStream( linesZipEntry );
      try {
        copyStream( input, nestedZipBytes );
      } finally {
        input.close();
      }
    } finally {
      outerZip.close();
    }

    boolean foundFine = false;
    boolean foundStandard = false;
    boolean foundThick = false;
    ZipInputStream nestedZip = new ZipInputStream( new ByteArrayInputStream( nestedZipBytes.toByteArray() ) );
    try {
      ZipEntry entry;
      while ( (entry = nestedZip.getNextEntry()) != null ) {
        String name = entry.getName();
        if ( name.endsWith( "user-fine" ) ) {
          foundFine = true;
        } else if ( name.endsWith( "user-standard" ) ) {
          foundStandard = true;
        } else if ( name.endsWith( "user-thick" ) ) {
          foundThick = true;
        }
      }
    } finally {
      nestedZip.close();
    }

    assertTrue( "ZIP export is missing user-fine", foundFine );
    assertTrue( "ZIP export is missing user-standard", foundStandard );
    assertTrue( "ZIP export is missing user-thick", foundThick );
  }

  File copyFileToDownloads( File sourceFile ) throws Exception
  {
    File targetFile = getDownloadFile( sourceFile.getName() );
    copyFile( sourceFile, targetFile );
    assertTrue( "Failed to copy import fixture to Downloads: " + targetFile.getAbsolutePath(), targetFile.exists() );
    // Make freshly copied imports show up in DocumentsUI immediately instead
    // of relying on the downloads provider to infer metadata lazily.
    //noinspection ResultOfMethodCallIgnored
    targetFile.setLastModified( System.currentTimeMillis() );
    scanFileForDocumentsUi( targetFile, getMimeTypeForPicker( targetFile ) );
    return targetFile;
  }

  private String getMimeTypeForPicker( File file )
  {
    String name = file.getName().toLowerCase( Locale.US );
    if ( name.endsWith( ".zip" ) ) return "application/zip";
    if ( name.endsWith( ".jpg" ) || name.endsWith( ".jpeg" ) ) return "image/jpeg";
    if ( name.endsWith( ".png" ) ) return "image/png";
    return "application/octet-stream";
  }

  private void scanFileForDocumentsUi( File file, String mimeType ) throws Exception
  {
    final CountDownLatch latch = new CountDownLatch( 1 );
    MediaScannerConnection.scanFile(
      mTargetContext,
      new String[] { file.getAbsolutePath() },
      new String[] { mimeType },
      new MediaScannerConnection.OnScanCompletedListener() {
        @Override public void onScanCompleted( String path, Uri uri )
        {
          latch.countDown();
        }
      }
    );
    assertTrue( "Timed out scanning file for DocumentsUI: " + file.getAbsolutePath(),
      latch.await( 5000, TimeUnit.MILLISECONDS ) );
  }

  void captureAndAssertScreen( String assetName ) throws Exception
  {
    enableDemoMode();
    File actualFile = new File( getCaseArtifactsDir(), assetName );
    assertTrue( "Failed to capture screenshot", takeScreenshotWithRetry( actualFile ) );
    if ( mRecordMode ) {
      copyFile( actualFile, getRecordedGoldenFile( assetName ) );
      return;
    }
    compareBitmapFileToGolden( actualFile, assetName, SCREEN_MAX_DIFF_RATIO, SCREEN_MAX_CHANNEL_DELTA );
  }

  File captureScreen( String assetName ) throws Exception
  {
    enableDemoMode();
    File actualFile = new File( getCaseArtifactsDir(), assetName );
    assertTrue( "Failed to capture screenshot", takeScreenshotWithRetry( actualFile ) );
    return actualFile;
  }

  void assertPngFileMatchesGolden( File actualFile, String assetName ) throws Exception
  {
    File artifactCopy = new File( getCaseArtifactsDir(), assetName );
    copyFile( actualFile, artifactCopy );
    if ( mRecordMode ) {
      copyFile( actualFile, getRecordedGoldenFile( assetName ) );
      return;
    }
    compareBitmapFileToGolden( actualFile, assetName, PNG_MAX_DIFF_RATIO, PNG_MAX_CHANNEL_DELTA );
  }

  void assertBitmapContainsColor( File actualFile, int expectedColor, int tolerance, int minCount )
  {
    Bitmap bitmap = BitmapFactory.decodeFile( actualFile.getAbsolutePath() );
    assertNotNull( "Unable to decode bitmap " + actualFile.getAbsolutePath(), bitmap );
    int matches = 0;
    int er = ( expectedColor >> 16 ) & 0xff;
    int eg = ( expectedColor >> 8 ) & 0xff;
    int eb = expectedColor & 0xff;
    try {
      for ( int y = 0; y < bitmap.getHeight(); ++y ) {
        for ( int x = 0; x < bitmap.getWidth(); ++x ) {
          int pixel = bitmap.getPixel( x, y );
          int pr = ( pixel >> 16 ) & 0xff;
          int pg = ( pixel >> 8 ) & 0xff;
          int pb = pixel & 0xff;
          if ( Math.abs( pr - er ) <= tolerance
            && Math.abs( pg - eg ) <= tolerance
            && Math.abs( pb - eb ) <= tolerance ) {
            ++ matches;
            if ( matches >= minCount ) return;
          }
        }
      }
    } finally {
      bitmap.recycle();
    }
    fail( "Bitmap " + actualFile.getAbsolutePath() + " does not contain color 0x"
      + Integer.toHexString( expectedColor ) + " at least " + minCount + " times; found " + matches );
  }

  void assertBitmapDoesNotContainColor( File actualFile, int expectedColor, int tolerance, int maxCount )
  {
    Bitmap bitmap = BitmapFactory.decodeFile( actualFile.getAbsolutePath() );
    assertNotNull( "Unable to decode bitmap " + actualFile.getAbsolutePath(), bitmap );
    int matches = 0;
    int er = ( expectedColor >> 16 ) & 0xff;
    int eg = ( expectedColor >> 8 ) & 0xff;
    int eb = expectedColor & 0xff;
    try {
      for ( int y = 0; y < bitmap.getHeight(); ++y ) {
        for ( int x = 0; x < bitmap.getWidth(); ++x ) {
          int pixel = bitmap.getPixel( x, y );
          int pr = ( pixel >> 16 ) & 0xff;
          int pg = ( pixel >> 8 ) & 0xff;
          int pb = pixel & 0xff;
          if ( Math.abs( pr - er ) <= tolerance
            && Math.abs( pg - eg ) <= tolerance
            && Math.abs( pb - eb ) <= tolerance ) {
            ++ matches;
            if ( matches > maxCount ) {
              fail( "Bitmap " + actualFile.getAbsolutePath() + " unexpectedly contains color 0x"
                + Integer.toHexString( expectedColor ) + " more than " + maxCount + " times" );
            }
          }
        }
      }
    } finally {
      bitmap.recycle();
    }
  }

  void assertTextFileMatchesGolden( File actualFile, String assetName, TextNormalizer normalizer ) throws Exception
  {
    String actualText = readFileText( actualFile );
    String normalizedActual = ( normalizer == null ) ? actualText : normalizer.normalize( actualText );
    writeTextFile( new File( getCaseArtifactsDir(), assetName ), normalizedActual );

    if ( mRecordMode ) {
      writeTextFile( getRecordedGoldenFile( assetName ), normalizedActual );
      return;
    }

    String expectedText = readAssetText( assetName );
    assertEquals( "Text export differs from golden fixture " + assetName, expectedText, normalizedActual );
  }

  void pickDocumentByFileName( String fileName ) throws Exception
  {
    resolveDocumentPickerChooserIfPresent();
    String documentsUiPackage = waitForDocumentsUi();

    if ( tryPickVisibleDocument( fileName, documentsUiPackage, 5000 ) ) return;

    UiObject2 showRootsButton = findAnyObject( 3000,
      By.descContains( "Show roots" ),
      By.descContains( "Open roots" )
    );
    if ( showRootsButton != null && tryClickObject( showRootsButton ) ) {
      if ( tryTapDocumentText( "Downloads", 5000 ) || tryTapDocumentText( "Download", 3000 ) ) {
        if ( tryPickVisibleDocument( fileName, documentsUiPackage, 5000 ) ) return;
        if ( trySearchDocument( fileName, documentsUiPackage ) ) return;
      } else if ( tryTapDocumentText( "sdk_gphone64_x86_64", 3000 )
        || tryTapDocumentText( "SDCARD", 3000 )
        || tryTapDocumentText( "Internal storage", 3000 ) ) {
        openDocumentsUiPath( "Download" );
        if ( tryPickVisibleDocument( fileName, documentsUiPackage, 5000 ) ) return;
        if ( trySearchDocument( fileName, documentsUiPackage ) ) return;
      }
    }

    if ( ! isDocumentsUiPackage( mDevice.getCurrentPackageName() ) ) return;

    captureDocumentPickerFailure( fileName );
    fail( "Requested document not visible in picker: " + fileName );
  }

  private boolean trySearchDocument( String fileName, String documentsUiPackage )
  {
    UiObject2 searchButton = findAnyObject( 3000,
      By.res( documentsUiPackage, "option_menu_search" ),
      By.res( documentsUiPackage, "menu_search" ),
      By.res( documentsUiPackage, "action_menu_search" ),
      By.descContains( "Search" ),
      By.text( "Search" )
    );
    if ( searchButton == null || ! tryClickObject( searchButton ) ) return false;

    UiObject2 searchBox = findAnyObject( 5000,
      By.res( "android", "search_src_text" ),
      By.res( documentsUiPackage, "search_src_text" ),
      By.clazz( "android.widget.EditText" )
    );
    if ( searchBox == null ) {
      return false;
    }

    try {
      searchBox.setText( fileName );
    } catch ( StaleObjectException e ) {
      return false;
    }
    waitForIdle();
    SystemClock.sleep( 1000 );
    if ( tryPickVisibleDocument( fileName, documentsUiPackage, 8000 ) ) return true;
    return false;
  }

  private void captureDocumentPickerFailure( String fileName ) throws Exception
  {
    File artifactDir = getCaseArtifactsDir();
    takeScreenshotWithRetry( new File( artifactDir, "document-picker-failure.png" ) );
    File hierarchy = new File( artifactDir, "document-picker-failure.xml" );
    ensureParentDir( hierarchy );
    mDevice.dumpWindowHierarchy( hierarchy );
    writeTextFile( new File( artifactDir, "document-picker-failure.txt" ),
      "file=" + fileName + "\n"
      + "package=" + mDevice.getCurrentPackageName() + "\n" );
  }

  private boolean tryPickVisibleDocument( String fileName, String documentsUiPackage, long waitMs )
  {
    long deadline = SystemClock.uptimeMillis() + waitMs;
    boolean scrolled = false;
    while ( SystemClock.uptimeMillis() < deadline ) {
      // Some DocumentsUI providers close immediately after a matching search
      // result is accepted. Let the caller assert the import postcondition.
      if ( ! isDocumentsUiPackage( mDevice.getCurrentPackageName() ) ) return true;

      UiObject2 row = findDocumentObject( fileName, 500 );
      if ( row != null && tryClickObject( row ) ) {
        if ( waitForDocumentsUiToClose( 1500 ) ) return true;

        UiObject2 openButton = findAnyObject( 1500,
          By.res( documentsUiPackage, "action_menu_select" ),
          By.text( "Open" ),
          By.textContains( "Open" ),
          By.text( "Select" ),
          By.descContains( "Open" ),
          By.descContains( "Select" )
        );
        if ( openButton != null && tryClickObject( openButton ) ) {
          if ( waitForDocumentsUiToClose( 5000 ) ) return true;
        }
      }

      if ( ! scrolled ) {
        scrolled = scrollDocumentsListToText( fileName );
        continue;
      }

      SystemClock.sleep( 200 );
    }
    return false;
  }

  private boolean tryTapDocumentText( String text, long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    boolean scrolled = false;
    while ( SystemClock.uptimeMillis() < deadline ) {
      UiObject2 object = findDocumentObject( text, 500 );
      if ( object != null && tryClickObject( object ) ) return true;

      if ( ! scrolled ) {
        scrolled = scrollDocumentsListToText( text );
        continue;
      }

      SystemClock.sleep( 200 );
    }
    return false;
  }

  private void openDocumentsUiPath( String... pathSegments )
  {
    if ( pathSegments == null ) return;
    for ( String segment : pathSegments ) {
      if ( segment == null || segment.length() == 0 ) continue;
      assertTrue( "DocumentsUI path segment not found: " + segment,
        tryTapDocumentText( segment, UI_TIMEOUT_MS ) );
      SystemClock.sleep( 400 );
    }
  }

  private UiObject2 findDocumentObject( String text, long timeoutMs )
  {
    if ( text == null || text.length() == 0 ) return null;
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      UiObject2 object = mDevice.findObject( By.text( text ) );
      if ( object != null ) return object;
      object = mDevice.findObject( By.textContains( text ) );
      if ( object != null ) return object;
      object = mDevice.findObject( By.desc( text ) );
      if ( object != null ) return object;
      object = mDevice.findObject( By.descContains( text ) );
      if ( object != null ) return object;
      SystemClock.sleep( 150 );
    }
    return null;
  }

  private boolean scrollDocumentsListToText( String text )
  {
    try {
      UiScrollable scrollable = new UiScrollable( new UiSelector().scrollable( true ).instance( 0 ) );
      scrollable.setAsVerticalList();
      scrollable.setMaxSearchSwipes( 20 );
      if ( scrollable.scrollIntoView( new UiSelector().text( text ) ) ) return true;
    } catch ( UiObjectNotFoundException e ) {
      // fall through and try a contains-based scroll below
    }

    try {
      UiScrollable scrollable = new UiScrollable( new UiSelector().scrollable( true ).instance( 0 ) );
      scrollable.setAsVerticalList();
      scrollable.setMaxSearchSwipes( 20 );
      if ( scrollable.scrollIntoView( new UiSelector().textContains( text ) ) ) return true;
    } catch ( UiObjectNotFoundException e ) {
      // fall through to the direct lookup below
    }

    return mDevice.findObject( By.text( text ) ) != null || mDevice.findObject( By.textContains( text ) ) != null;
  }

  private boolean waitForDocumentsUiToClose( long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( ! isDocumentsUiPackage( mDevice.getCurrentPackageName() ) ) return true;
      SystemClock.sleep( 250 );
    }
    return false;
  }

  void waitForIdle()
  {
    mInstrumentation.waitForIdleSync();
    mDevice.waitForIdle();
  }

  private void waitForDisplayedView( int viewId )
  {
    long deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS;
    Throwable lastError = null;
    while ( SystemClock.uptimeMillis() < deadline ) {
      try {
        onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
          if ( error != null ) throw error;
          assertNotNull( "View lookup returned null for " + viewId, view );
          assertEquals( "Expected visible view for " + viewId, View.VISIBLE, view.getVisibility() );
          assertTrue( "Expected non-zero size for " + viewId, view.getWidth() > 0 && view.getHeight() > 0 );
        } );
        waitForIdle();
        return;
      } catch ( AssertionFailedError | RuntimeException e ) {
        lastError = e;
        SystemClock.sleep( 200 );
      }
    }
    AssertionError error = new AssertionError( "Timed out waiting for view id " + viewId + " to be displayed" );
    if ( lastError != null ) error.initCause( lastError );
    throw error;
  }

  private boolean isViewDisplayed( int viewId )
  {
    try {
      onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
        if ( error != null ) throw error;
        assertNotNull( "View lookup returned null for " + viewId, view );
        assertEquals( "Expected visible view for " + viewId, View.VISIBLE, view.getVisibility() );
        assertTrue( "Expected non-zero size for " + viewId, view.getWidth() > 0 && view.getHeight() > 0 );
      } );
      return true;
    } catch ( AssertionFailedError | RuntimeException e ) {
      return false;
    }
  }

  private boolean isViewPresent( int viewId )
  {
    try {
      onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
        if ( error != null ) throw error;
        assertNotNull( view );
      } );
      return true;
    } catch ( NoMatchingViewException | AssertionFailedError e ) {
      return false;
    }
  }

  private boolean isObjectPresent( BySelector selector, long timeoutMs )
  {
    return mDevice.wait( Until.findObject( selector ), timeoutMs ) != null;
  }

  private boolean tapViewIfPresent( int viewId )
  {
    try {
      tapViewCenter( viewId, "optional view " + viewId );
      return true;
    } catch ( NoMatchingViewException | NoActivityResumedException | AssertionFailedError | PerformException e ) {
      return false;
    }
  }

  private boolean dismissStartupDialogsIfPresent()
  {
    UiObject2 dialogButton = mDevice.findObject( By.res( PACKAGE_NAME, "btn_skip" ) );
    if ( dialogButton == null ) dialogButton = mDevice.findObject( By.res( PACKAGE_NAME, "btn_ok" ) );
    if ( dialogButton == null ) dialogButton = mDevice.findObject( By.res( PACKAGE_NAME, "btn_next" ) );
    if ( dialogButton != null ) {
      clickObject( dialogButton );
      return true;
    }
    return false;
  }

  private void tapViewCenter( int viewId, String label )
  {
    final int[] center = new int[2];
    onView( withId( viewId ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertNotNull( "Missing " + label, view );
      assertTrue( label + " is not visible",
        view.getVisibility() == View.VISIBLE && view.getWidth() > 0 && view.getHeight() > 0 );
      int[] location = new int[2];
      view.getLocationOnScreen( location );
      center[0] = location[0] + view.getWidth() / 2;
      center[1] = location[1] + view.getHeight() / 2;
    } );
    assertTrue( "Failed to tap " + label, mDevice.click( center[0], center[1] ) );
    waitForIdle();
  }

  private void tapChildInContainer( int containerId, int childIndex, String containerName )
  {
    final int[] center = new int[2];
    onView( withId( containerId ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Container " + containerName + " is not a ViewGroup", view instanceof ViewGroup );
      ViewGroup group = (ViewGroup)view;
      assertTrue( "Container " + containerName + " does not have child index " + childIndex,
        childIndex >= 0 && childIndex < group.getChildCount() );
      View child = group.getChildAt( childIndex );
      assertNotNull( "Missing child " + childIndex + " in " + containerName, child );
      assertTrue( "Child " + childIndex + " in " + containerName + " is not visible",
        child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0 );
      int[] location = new int[2];
      child.getLocationOnScreen( location );
      center[0] = location[0] + child.getWidth() / 2;
      center[1] = location[1] + child.getHeight() / 2;
    } );
    assertTrue( "Failed to inject tap into " + containerName + " child " + childIndex,
      mDevice.click( center[0], center[1] ) );
    waitForIdle();
  }

  private void tapManualToolbarChild( int rowIndex, int childIndex )
  {
    final int[] center = new int[2];
    onView( withId( R.id.layout_tools ) ).check( ( View view, NoMatchingViewException error ) -> {
      if ( error != null ) throw error;
      assertTrue( "Tools container is not a ViewGroup", view instanceof ViewGroup );
      ViewGroup row = findManualToolbarRow( (ViewGroup)view, rowIndex );
      assertNotNull( "Manual toolbar row " + rowIndex + " is not visible", row );
      assertTrue( "Manual toolbar row " + rowIndex + " does not have child index " + childIndex,
        childIndex >= 0 && childIndex < row.getChildCount() );
      View child = row.getChildAt( childIndex );
      assertTrue( "Manual toolbar child " + childIndex + " is not visible",
        child.getVisibility() == View.VISIBLE && child.getWidth() > 0 && child.getHeight() > 0 );
      int[] location = new int[2];
      child.getLocationOnScreen( location );
      center[0] = location[0] + child.getWidth() / 2;
      center[1] = location[1] + child.getHeight() / 2;
    } );
    assertTrue( "Failed to inject tap into manual toolbar row " + rowIndex + " child " + childIndex,
      mDevice.click( center[0], center[1] ) );
    waitForIdle();
  }

  private ViewGroup findManualToolbarRow( ViewGroup tools, int rowIndex )
  {
    int found = 0;
    for ( int index = 0; index < tools.getChildCount(); ++index ) {
      View child = tools.getChildAt( index );
      if ( ! ( child instanceof ViewGroup ) ) continue;
      if ( child.getId() != View.NO_ID ) continue;
      if ( child.getVisibility() != View.VISIBLE || child.getWidth() <= 0 || child.getHeight() <= 0 ) continue;
      ViewGroup row = (ViewGroup)child;
      if ( row.getChildCount() < ItemDrawer.getToolbarSlotCount() + 1 ) continue;
      if ( found == rowIndex ) return row;
      ++found;
    }
    return null;
  }

  private void compareBitmapFileToGolden( File actualFile, String assetName, double maxDiffRatio, int maxChannelDelta ) throws Exception
  {
    Bitmap actual = BitmapFactory.decodeFile( actualFile.getAbsolutePath() );
    assertNotNull( "Unable to decode bitmap " + actualFile.getAbsolutePath(), actual );
    Bitmap expected = BitmapFactory.decodeStream( mTestContext.getAssets().open( getGoldenAssetPath( assetName ) ) );
    assertNotNull( "Unable to decode golden bitmap " + assetName, expected );

    boolean sameSize = expected.getWidth() == actual.getWidth() && expected.getHeight() == actual.getHeight();
    double diffRatio = sameSize ? bitmapDifferenceRatio( expected, actual, maxChannelDelta ) : 1.0;
    if ( sameSize && diffRatio <= maxDiffRatio ) {
      return;
    }

    File expectedCopy = new File( getCaseArtifactsDir(), "expected-" + assetName );
    File diffCopy = new File( getCaseArtifactsDir(), "diff-" + assetName );
    saveBitmap( expected, expectedCopy );
    if ( sameSize ) {
      saveBitmap( createDiffBitmap( expected, actual ), diffCopy );
    }
    fail(
      "Bitmap golden mismatch for " + assetName
        + " expected=" + expectedCopy.getAbsolutePath()
        + " actual=" + actualFile.getAbsolutePath()
        + (sameSize ? " diff=" + diffCopy.getAbsolutePath() + " diffRatio=" + diffRatio : " (size mismatch)")
    );
  }

  private double bitmapDifferenceRatio( Bitmap expected, Bitmap actual, int maxChannelDelta )
  {
    if ( expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight() ) return 1.0;
    int width = expected.getWidth();
    int height = expected.getHeight();
    int[] expectedPixels = new int[ width ];
    int[] actualPixels   = new int[ width ];
    long diffCount = 0L;
    for ( int y = 0; y < height; ++y ) {
      expected.getPixels( expectedPixels, 0, width, 0, y, width, 1 );
      actual.getPixels( actualPixels, 0, width, 0, y, width, 1 );
      for ( int x = 0; x < width; ++x ) {
        if ( ! pixelsWithinTolerance( expectedPixels[x], actualPixels[x], maxChannelDelta ) ) {
          ++ diffCount;
        }
      }
    }
    return diffCount / (double)( width * height );
  }

  private boolean pixelsWithinTolerance( int expectedPixel, int actualPixel, int maxChannelDelta )
  {
    if ( expectedPixel == actualPixel ) return true;
    if ( maxChannelDelta <= 0 ) return false;

    int expectedA = ( expectedPixel >>> 24 ) & 0xff;
    int expectedR = ( expectedPixel >>> 16 ) & 0xff;
    int expectedG = ( expectedPixel >>> 8 ) & 0xff;
    int expectedB = expectedPixel & 0xff;

    int actualA = ( actualPixel >>> 24 ) & 0xff;
    int actualR = ( actualPixel >>> 16 ) & 0xff;
    int actualG = ( actualPixel >>> 8 ) & 0xff;
    int actualB = actualPixel & 0xff;

    return Math.abs( expectedA - actualA ) <= maxChannelDelta
      && Math.abs( expectedR - actualR ) <= maxChannelDelta
      && Math.abs( expectedG - actualG ) <= maxChannelDelta
      && Math.abs( expectedB - actualB ) <= maxChannelDelta;
  }

  private Bitmap createDiffBitmap( Bitmap expected, Bitmap actual )
  {
    int width = expected.getWidth();
    int height = expected.getHeight();
    Bitmap diff = Bitmap.createBitmap( width, height, Bitmap.Config.ARGB_8888 );
    int[] expectedPixels = new int[ width ];
    int[] actualPixels   = new int[ width ];
    int[] diffPixels     = new int[ width ];
    for ( int y = 0; y < height; ++y ) {
      expected.getPixels( expectedPixels, 0, width, 0, y, width, 1 );
      actual.getPixels( actualPixels, 0, width, 0, y, width, 1 );
      for ( int x = 0; x < width; ++x ) {
        diffPixels[x] = ( expectedPixels[x] == actualPixels[x] ) ? actualPixels[x] : 0xffff00ff;
      }
      diff.setPixels( diffPixels, 0, width, 0, y, width, 1 );
    }
    return diff;
  }

  private void saveBitmap( Bitmap bitmap, File file ) throws Exception
  {
    ensureParentDir( file );
    OutputStream output = new FileOutputStream( file );
    try {
      assertTrue( "Failed to write bitmap " + file.getAbsolutePath(), bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    } finally {
      output.close();
    }
  }

  private void resolveDocumentPickerChooserIfPresent() throws Exception
  {
    UiObject2 resolverList = mDevice.wait( Until.findObject( By.res( "android", "resolver_list" ) ), 2000 );
    if ( resolverList == null ) return;

    UiObject2 filesOption = waitForAnyObject(
      By.textContains( "Files" ),
      By.textContains( "Documents" ),
      By.textContains( "Files by Google" )
    );
    if ( filesOption != null ) {
      clickObject( filesOption );
      return;
    }

    List< UiObject2 > children = resolverList.getChildren();
    assertFalse( "Resolver chooser is visible but has no options", children == null || children.isEmpty() );
    clickObject( children.get( 0 ) );
  }

  private String waitForDocumentsUi()
  {
    UiObject2 documentRoot = waitForAnyObject(
      By.pkg( DOCUMENTS_UI_PACKAGE ),
      By.pkg( GOOGLE_DOCUMENTS_UI_PACKAGE ),
      By.res( DOCUMENTS_UI_PACKAGE, "container_directory" ),
      By.res( DOCUMENTS_UI_PACKAGE, "roots_list" ),
      By.res( GOOGLE_DOCUMENTS_UI_PACKAGE, "container_directory" ),
      By.res( GOOGLE_DOCUMENTS_UI_PACKAGE, "roots_list" )
    );
    assertNotNull( "DocumentsUI did not open", documentRoot );
    String packageName = documentRoot.getApplicationPackage();
    if ( GOOGLE_DOCUMENTS_UI_PACKAGE.equals( packageName ) ) return GOOGLE_DOCUMENTS_UI_PACKAGE;
    return DOCUMENTS_UI_PACKAGE;
  }

  private void waitForDocumentsUiToClose()
  {
    if ( waitForDocumentsUiToClose( UI_TIMEOUT_MS ) ) return;
    fail( "DocumentsUI did not close after selecting the import file" );
  }

  private boolean isDocumentsUiPackage( String packageName )
  {
    return DOCUMENTS_UI_PACKAGE.equals( packageName ) || GOOGLE_DOCUMENTS_UI_PACKAGE.equals( packageName );
  }

  private void verifyEmulatorProfile() throws Exception
  {
    String wmSize = runShellCommand( "wm size" );
    String wmDensity = runShellCommand( "wm density" );
    String fontScale = runShellCommand( "settings get system font_scale" );
    String language = Locale.getDefault().getLanguage();

    assertTrue( "Unexpected emulator size: " + wmSize, wmSize.contains( "2560x1600" ) );
    assertTrue( "Unexpected emulator density: " + wmDensity, wmDensity.contains( "320" ) );
    assertTrue( "Unexpected font scale: " + fontScale, fontScale.startsWith( "1.0" ) );
    assertEquals( "Tests expect an English emulator locale", "en", language );
  }

  private void enableDemoMode() throws Exception
  {
    if ( mDemoModeEnabled ) return;
    runShellCommand( "settings put global sysui_demo_allowed 1" );
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command enter" );
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0900" );
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false" );
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e nosim true" );
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command notifications -e visible false" );
    mDemoModeEnabled = true;
  }

  private void disableDemoMode() throws Exception
  {
    if ( ! mDemoModeEnabled ) return;
    runShellCommand( "am broadcast -a com.android.systemui.demo -e command exit" );
    mDemoModeEnabled = false;
  }

  private void adoptShellPermissions()
  {
    if ( mShellIdentityAdopted ) return;
    UiAutomation automation = mInstrumentation.getUiAutomation();
    automation.adoptShellPermissionIdentity();
    mShellIdentityAdopted = true;
  }

  private void configureStablePreferences()
  {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( mTargetContext );
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString( "DISTOX_EXTRA_BUTTONS", "3" );
    editor.putBoolean( "DISTOX_MKEYBOARD", false );
    editor.putBoolean( "DISTOX_SETUP_SCREEN", false );
    editor.putBoolean( "DISTOX_WELCOME_SCREEN", false );
    editor.putBoolean( "DISTOX_SINGLE_BACK", true );
    editor.putString( "DISTOX_WITH_LEVELS", "0" );
    editor.putString( "DISTOX_TOOLBAR_UPDATE", Integer.toString( TDSetting.TOOLBAR_UPDATE_MANUAL ) );
    editor.putString( "DISTOX_TOOLBAR_SLOTS", "8" );
    editor.putString( "DISTOX_TOOLBAR_ROWS", "1" );
    editor.putString( "DISTOX_PRESET_SLOTS", "3" );
    editor.putString( "DISTOX_PRESET_1_NAME", "Fine" );
    editor.putString( "DISTOX_PRESET_1_LINE_STYLE", "1" );
    editor.putString( "DISTOX_PRESET_1_LINE_SEGMENT", "1" );
    editor.putString( "DISTOX_PRESET_2_NAME", "Smooth" );
    editor.putString( "DISTOX_PRESET_2_LINE_STYLE", "0" );
    editor.putString( "DISTOX_PRESET_2_LINE_SEGMENT", "10" );
    editor.putString( "DISTOX_PRESET_3_NAME", "Straight" );
    editor.putString( "DISTOX_PRESET_3_LINE_STYLE", "5" );
    editor.putString( "DISTOX_PRESET_3_LINE_SEGMENT", "5" );
    editor.putString( "DISTOX_ACTIVE_SKETCH_PRESET", "1" );
    editor.putBoolean( "DISTOX_ERASE_REFERENCE", false );
    editor.apply();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mTargetContext ) );
  }

  private void configureStableRuntimeState()
  {
    TDSetting.mSingleBack = true;
    TDSetting.mEraseReferenceImages = false;
    TDSetting.mExportDataShare = false;
    Context appContext = mTargetContext.getApplicationContext();
    if ( appContext instanceof TopoDroidApp ) {
      TopoDroidApp app = (TopoDroidApp)appContext;
      app.mWelcomeScreen = false;
      app.mSetupScreen = false;
    }
  }

  private void ensureSketchLineSymbolsReady()
  {
    SketchLineSymbolManager.ensureLineSymbols();
    SketchLineSymbolManager.syncPrefsFromSymbolFiles();
  }

  private void bringMainWindowToForeground()
  {
    Intent intent = new Intent( mTargetContext, MainWindow.class );
    intent.setAction( Intent.ACTION_VIEW );
    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP );
    mTargetContext.startActivity( intent );
    waitForIdle();
  }

  void waitForSurveyAbsentInDatabase( String surveyName )
  {
    long deadline = SystemClock.uptimeMillis() + FILE_TIMEOUT_MS;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( surveyName == null || TopoDroidApp.mData == null || TopoDroidApp.mData.getSurveyId( surveyName ) <= 0L ) {
        return;
      }
      SystemClock.sleep( 250 );
    }
    fail( "Survey still exists in database after delete: " + surveyName );
  }

  private void cleanupNamedSurveyArtifacts( List< String > surveyNames )
  {
    if ( surveyNames == null ) return;
    for ( String surveyName : surveyNames ) {
      deleteRecursively( getSurveyDir( surveyName ) );
      File zipFile = getZipFile( surveyName );
      if ( zipFile.exists() ) {
        //noinspection ResultOfMethodCallIgnored
        zipFile.delete();
      }
      File downloadCopy = getDownloadFile( surveyName + ".zip" );
      if ( downloadCopy.exists() ) {
        //noinspection ResultOfMethodCallIgnored
        downloadCopy.delete();
      }
    }
  }

  private void cleanupNamedSurveysInDatabase( List< String > surveyNames )
  {
    if ( surveyNames == null || TopoDroidApp.mData == null ) return;
    for ( String surveyName : surveyNames ) {
      long sid = TopoDroidApp.mData.getSurveyId( surveyName );
      if ( sid > 0L ) {
        TopoDroidApp.mData.doDeleteSurvey( sid );
      }
    }
  }

  private void resetSelectedSurveyState()
  {
    TDInstance.sid = -1L;
    TDInstance.survey = null;
    TDInstance.datamode = 0;
  }

  private void disableDialogRExit()
  {
    if ( TopoDroidApp.mDData == null ) {
      TopoDroidApp.mDData = new DeviceHelper( mTargetContext );
    }
    TopoDroidApp.mDData.setValue( "say_dialogR", "NO" );
    String rawValue = TopoDroidApp.mDData.getValue( "say_dialogR" );
    boolean enabled = TopoDroidApp.sayDialogR();
    Log.i( "VisualTestSupport", "sayDialogR raw value = " + rawValue + " enabled = " + enabled );
    assertEquals( "NO", rawValue );
    assertFalse( "MainWindow would still kill the process on destroy", enabled );
  }

  private void clearCaseArtifacts()
  {
    deleteRecursively( getCaseArtifactsDir() );
    if ( ! mRecordMode ) {
      deleteRecursively( new File( getArtifactsRootDir(), RECORDED_GOLDENS_DIR ) );
    } else if ( ! sRecordedGoldensCleared ) {
      deleteRecursively( new File( getArtifactsRootDir(), RECORDED_GOLDENS_DIR ) );
      sRecordedGoldensCleared = true;
    }
  }

  private File getExternalFilesDir()
  {
    File dir = mTargetContext.getExternalFilesDir( null );
    assertNotNull( "Target external files directory is null", dir );
    return dir;
  }

  private File getCaseArtifactsDir()
  {
    File dir = new File( getArtifactsRootDir(), mCaseName );
    ensureDir( dir );
    return dir;
  }

  private File getRecordedGoldenFile( String assetName )
  {
    File file = new File( getArtifactsRootDir(), RECORDED_GOLDENS_DIR + File.separator + assetName );
    ensureParentDir( file );
    return file;
  }

  private File getArtifactsRootDir()
  {
    File dir = new File( getExternalFilesDir(), TEST_ARTIFACTS_DIR );
    ensureDir( dir );
    return dir;
  }

  private String getGoldenAssetPath( String assetName )
  {
    return GOLDEN_PROFILE_DIR + "/" + assetName;
  }

  private UiObject2 waitForObject( BySelector selector )
  {
    UiObject2 object = mDevice.wait( Until.findObject( selector ), UI_TIMEOUT_MS );
    assertNotNull( "Timed out waiting for " + selector, object );
    return object;
  }

  private UiObject2 waitForAnyObject( BySelector... selectors )
  {
    return findAnyObject( UI_TIMEOUT_MS, selectors );
  }

  private UiObject2 findAnyObject( long timeoutMs, BySelector... selectors )
  {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while ( System.currentTimeMillis() < deadline ) {
      for ( BySelector selector : selectors ) {
        UiObject2 object = mDevice.findObject( selector );
        if ( object != null ) return object;
      }
      SystemClock.sleep( 250 );
    }
    return null;
  }

  private void waitUntilGone( BySelector selector )
  {
    mDevice.wait( Until.gone( selector ), UI_TIMEOUT_MS );
  }

  private UiObject2 findSurveyOnMainList( String surveyName )
  {
    waitForMainWindow();
    // Most callers just created the survey — check the current viewport first.
    UiObject2 row = mDevice.findObject( By.text( surveyName ) );
    if ( row != null ) return row;

    // Let UiScrollable fling through the list in both directions (fast; uses
    // fling momentum instead of the drag-sized swipes this used to do).
    try {
      UiScrollable scrollable = new UiScrollable(
        new UiSelector().resourceId( PACKAGE_NAME + ":id/td_list" ) );
      scrollable.setAsVerticalList();
      scrollable.setMaxSearchSwipes( 60 );
      if ( scrollable.scrollIntoView( new UiSelector().text( surveyName ) ) ) {
        return mDevice.findObject( By.text( surveyName ) );
      }
    } catch ( UiObjectNotFoundException e ) {
      // fall through — let the caller treat "not found" as a miss
    }
    return null;
  }

  private UiObject2 requireSurveyOnMainList( String surveyName )
  {
    waitForSurveyOnMainList( surveyName );
    UiObject2 row = findSurveyOnMainList( surveyName );
    assertNotNull( "Survey not visible on the main list: " + surveyName, row );
    return row;
  }

  private void clickObject( UiObject2 object )
  {
    assertTrue( "Failed to click object", tryClickObject( object ) );
  }

  private boolean takeScreenshotWithRetry( File file ) throws Exception
  {
    for ( int attempt = 0; attempt < 5; ++attempt ) {
      waitForIdle();
      if ( mDevice.takeScreenshot( file ) ) return true;
      SystemClock.sleep( 400 );
    }
    return false;
  }

  private void longClickObject( UiObject2 object )
  {
    assertTrue( "Failed to long-click object", tryLongClickObject( object ) );
  }

  private UiObject2 getInteractiveObject( UiObject2 object )
  {
    UiObject2 current = object;
    while ( current != null ) {
      try {
        if ( current.isClickable() || current.isLongClickable() ) return current;
        current = current.getParent();
      } catch ( StaleObjectException e ) {
        return null;
      }
    }
    return object;
  }

  private boolean tryClickObject( UiObject2 object )
  {
    assertNotNull( "Cannot click a null object", object );
    for ( int attempt = 0; attempt < 3; ++attempt ) {
      try {
        UiObject2 target = getInteractiveObject( object );
        if ( target == null ) {
          SystemClock.sleep( 150 );
          continue;
        }
        target.click();
        waitForIdle();
        return true;
      } catch ( StaleObjectException e ) {
        SystemClock.sleep( 150 );
      }
    }
    return false;
  }

  private boolean tryLongClickObject( UiObject2 object )
  {
    assertNotNull( "Cannot long-click a null object", object );
    for ( int attempt = 0; attempt < 3; ++attempt ) {
      try {
        UiObject2 target = getInteractiveObject( object );
        if ( target == null ) {
          SystemClock.sleep( 150 );
          continue;
        }
        int centerX = target.getVisibleBounds().centerX();
        int centerY = target.getVisibleBounds().centerY();
        mDevice.swipe( centerX, centerY, centerX, centerY, 120 );
        waitForIdle();
        return true;
      } catch ( StaleObjectException e ) {
        SystemClock.sleep( 150 );
      }
    }
    return false;
  }

  private String runShellCommand( String command ) throws Exception
  {
    ParcelFileDescriptor descriptor = mInstrumentation.getUiAutomation().executeShellCommand( command );
    try {
      return readAllText( descriptor );
    } finally {
      descriptor.close();
    }
  }

  private String readAllText( ParcelFileDescriptor descriptor ) throws Exception
  {
    InputStream input = new FileInputStream( descriptor.getFileDescriptor() );
    try {
      Reader reader = new InputStreamReader( input, StandardCharsets.UTF_8 );
      char[] buffer = new char[ 4096 ];
      StringBuilder builder = new StringBuilder();
      int read;
      while ( (read = reader.read( buffer )) >= 0 ) {
        builder.append( buffer, 0, read );
      }
      return builder.toString().trim();
    } finally {
      input.close();
    }
  }

  private String readAssetText( String assetName ) throws Exception
  {
    InputStream input = mTestContext.getAssets().open( getGoldenAssetPath( assetName ) );
    try {
      return readStreamText( input );
    } finally {
      input.close();
    }
  }

  private String readFileText( File file ) throws Exception
  {
    InputStream input = new FileInputStream( file );
    try {
      return readStreamText( input );
    } finally {
      input.close();
    }
  }

  private String readStreamText( InputStream input ) throws Exception
  {
    Reader reader = new InputStreamReader( input, StandardCharsets.UTF_8 );
    char[] buffer = new char[ 4096 ];
    StringBuilder builder = new StringBuilder();
    int read;
    while ( (read = reader.read( buffer )) >= 0 ) {
      builder.append( buffer, 0, read );
    }
    return builder.toString();
  }

  private void writeTextFile( File file, String content ) throws Exception
  {
    ensureParentDir( file );
    OutputStream output = new FileOutputStream( file );
    try {
      output.write( content.getBytes( StandardCharsets.UTF_8 ) );
    } finally {
      output.close();
    }
  }

  private void copyFile( File source, File target ) throws Exception
  {
    ensureParentDir( target );
    InputStream input = new FileInputStream( source );
    try {
      OutputStream output = new FileOutputStream( target );
      try {
        copyStream( input, output );
      } finally {
        output.close();
      }
    } finally {
      input.close();
    }
  }

  private void copyStream( InputStream input, OutputStream output ) throws IOException
  {
    byte[] buffer = new byte[ 8192 ];
    int read;
    while ( (read = input.read( buffer )) >= 0 ) {
      output.write( buffer, 0, read );
    }
  }

  private void ensureDir( File dir )
  {
    if ( dir.exists() ) return;
    assertTrue( "Failed to create directory " + dir.getAbsolutePath(), dir.mkdirs() );
  }

  private void ensureParentDir( File file )
  {
    File parent = file.getParentFile();
    if ( parent != null ) ensureDir( parent );
  }

  private void deleteRecursively( File file )
  {
    if ( file == null || ! file.exists() ) return;
    if ( file.isDirectory() ) {
      File[] children = file.listFiles();
      if ( children != null ) {
        for ( File child : children ) deleteRecursively( child );
      }
    }
    //noinspection ResultOfMethodCallIgnored
    file.delete();
  }

  private void closeScenario()
  {
    if ( mScenario != null ) {
      mScenario.close();
      mScenario = null;
      waitForIdle();
      waitForPackageToLeaveForeground();
    }
  }

  private void waitForPackageToLeaveForeground()
  {
    long deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MS;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( ! PACKAGE_NAME.equals( mDevice.getCurrentPackageName() ) ) {
        SystemClock.sleep( 500 );
        return;
      }
      SystemClock.sleep( 200 );
    }
  }

  private String sanitizeName( String raw )
  {
    if ( raw == null ) return "visual-test";
    return raw.replaceAll( "[^A-Za-z0-9._-]", "_" );
  }

  static List< String > allSurveyNames( String... surveyNames )
  {
    ArrayList< String > surveys = new ArrayList<>();
    if ( surveyNames != null ) {
      for ( String surveyName : surveyNames ) {
        if ( surveyName != null && surveyName.length() > 0 ) surveys.add( surveyName );
      }
    }
    return surveys;
  }
}
