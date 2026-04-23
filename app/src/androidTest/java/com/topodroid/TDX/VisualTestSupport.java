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
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoActivityResumedException;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.PerformException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import junit.framework.AssertionFailedError;

final class VisualTestSupport
{
  private static final double SCREEN_MAX_DIFF_RATIO = 0.05;
  private static final int SCREEN_MAX_CHANNEL_DELTA = 8;

  interface TextNormalizer
  {
    String normalize( String text );
  }

  static final String PACKAGE_NAME = "com.topodroid.TDX";
  static final String TEST_PACKAGE = "com.topodroid.TDX.test";
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
    cleanupNamedSurveysInDatabase( surveyNames );
    cleanupNamedSurveyArtifacts( surveyNames );
    resetSelectedSurveyState();
    configureStablePreferences();
    configureStableRuntimeState();
    disableDialogRExit();
    waitForIdle();
  }

  void launchMainWindow() throws Exception
  {
    Intent intent = new Intent( mTargetContext, MainWindow.class );
    intent.setAction( Intent.ACTION_VIEW );
    intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
    mScenario = ActivityScenario.launch( intent );
    configureStableRuntimeState();
    disableDialogRExit();
    waitForMainWindow();
    ensureSketchLineSymbolsReady();
    verifyEmulatorProfile();
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
    waitForDisplayedView( R.id.layout_tool_l );
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
    tapToolbarChild( "listview", 0 );
    waitForIdle();
  }

  void tapToolbarChild( String containerResName, int childIndex )
  {
    int containerId = mTargetContext.getResources().getIdentifier( containerResName, "id", PACKAGE_NAME );
    assertTrue( "Unknown toolbar container " + containerResName, containerId != 0 );
    tapChildInContainer( containerId, childIndex, containerResName );
  }

  void clickRecentLineButton( int childIndex )
  {
    tapChildInContainer( R.id.layout_tool_l, childIndex, "layout_tool_l" );
  }

  void tapProfileButton( int viewId )
  {
    tapViewCenter( viewId, "profile button" );
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
      By.res( "android", "button2" ),
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

  void drawStrokeNormalized( double startX, double startY, double endX, double endY, int steps )
  {
    UiObject2 surface = waitForObject( By.res( PACKAGE_NAME, "drawingSurface" ) );
    assertNotNull( "Missing drawing surface", surface );
    int left   = surface.getVisibleBounds().left;
    int top    = surface.getVisibleBounds().top;
    int width  = surface.getVisibleBounds().width();
    int height = surface.getVisibleBounds().height();
    int x0 = left + (int)Math.round( width  * startX );
    int y0 = top  + (int)Math.round( height * startY );
    int x1 = left + (int)Math.round( width  * endX );
    int y1 = top  + (int)Math.round( height * endY );
    mDevice.swipe( x0, y0, x1, y1, steps );
    SystemClock.sleep( 500 );
    waitForIdle();
  }

  void setCanonicalToolbarState()
  {
    tapProfileButton( R.id.button_profile_1 );
    clickRecentLineButton( 2 );
  }

  File getPublicRoot()
  {
    return new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS ), "TDX/TopoDroid" );
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

  File getDownloadFile( String filename )
  {
    return new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOWNLOADS ), filename );
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
    return targetFile;
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

  void assertPngFileMatchesGolden( File actualFile, String assetName ) throws Exception
  {
    File artifactCopy = new File( getCaseArtifactsDir(), assetName );
    copyFile( actualFile, artifactCopy );
    if ( mRecordMode ) {
      copyFile( actualFile, getRecordedGoldenFile( assetName ) );
      return;
    }
    compareBitmapFileToGolden( actualFile, assetName, 0.0, 0 );
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

    if ( tryPickVisibleDocument( fileName, documentsUiPackage, 3000 ) ) return;

    UiObject2 showRootsButton = findAnyObject( 2000,
      By.descContains( "Show roots" ),
      By.descContains( "Open roots" )
    );
    if ( showRootsButton != null ) {
      clickObject( showRootsButton );
      if ( tryTapDocumentText( "Downloads", 3000 ) ) {
        if ( tryPickVisibleDocument( fileName, documentsUiPackage, 3000 ) ) return;
      } else if ( tryTapDocumentText( "sdk_gphone64_x86_64", 1500 )
        || tryTapDocumentText( "SDCARD", 1500 ) ) {
        openDocumentsUiPath( "Download" );
        if ( tryPickVisibleDocument( fileName, documentsUiPackage, 3000 ) ) return;
      }
    }

    fail( "Requested document not visible in picker: " + fileName );
  }

  private boolean tryPickVisibleDocument( String fileName, String documentsUiPackage, long waitMs )
  {
    UiObject2 row = mDevice.wait( Until.findObject( By.text( fileName ) ), waitMs );
    if ( row == null ) return false;

    clickObject( row );

    UiObject2 openButton = findAnyObject( 1500,
      By.res( documentsUiPackage, "action_menu_select" ),
      By.text( "Open" ),
      By.textContains( "Open" ),
      By.text( "Select" ),
      By.descContains( "Open" ),
      By.descContains( "Select" )
    );
    if ( openButton != null ) {
      clickObject( openButton );
    }
    return waitForDocumentsUiToClose( 5000 );
  }

  private boolean tryTapDocumentText( String text, long timeoutMs )
  {
    UiObject2 object = mDevice.wait( Until.findObject( By.textContains( text ) ), timeoutMs );
    if ( object == null ) return false;
    clickObject( object );
    return true;
  }

  private void openDocumentsUiPath( String... pathSegments )
  {
    if ( pathSegments == null ) return;
    for ( String segment : pathSegments ) {
      if ( segment == null || segment.length() == 0 ) continue;
      UiObject2 row = waitForObject( By.text( segment ) );
      clickObject( row );
      SystemClock.sleep( 400 );
    }
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
    editor.putString( "DISTOX_TOOLBAR_UPDATE", "0" );
    editor.putString( "DISTOX_PROFILE_1_LINE_STYLE", "1" );
    editor.putString( "DISTOX_PROFILE_1_LINE_SEGMENT", "1" );
    editor.putString( "DISTOX_PROFILE_2_LINE_STYLE", "0" );
    editor.putString( "DISTOX_PROFILE_2_LINE_SEGMENT", "10" );
    editor.putString( "DISTOX_ACTIVE_SKETCH_PROFILE", "1" );
    editor.apply();
  }

  private void configureStableRuntimeState()
  {
    TDSetting.mSingleBack = true;
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
    UiObject2 list = waitForObject( By.res( PACKAGE_NAME, "td_list" ) );

    UiObject2 row = mDevice.findObject( By.text( surveyName ) );
    if ( row != null ) return row;

    scrollListToBoundary( list, false );
    row = mDevice.findObject( By.text( surveyName ) );
    if ( row != null ) return row;

    String previousSignature = getVisibleListSignature( list );
    for ( int i = 0; i < 128; ++i ) {
      swipeObjectVertically( list, true );
      row = mDevice.findObject( By.text( surveyName ) );
      if ( row != null ) return row;

      String currentSignature = getVisibleListSignature( list );
      if ( currentSignature.equals( previousSignature ) ) break;
      previousSignature = currentSignature;
    }
    return null;
  }

  private void scrollListToBoundary( UiObject2 list, boolean towardEnd )
  {
    String previousSignature = getVisibleListSignature( list );
    for ( int i = 0; i < 64; ++i ) {
      swipeObjectVertically( list, towardEnd );
      String currentSignature = getVisibleListSignature( list );
      if ( currentSignature.equals( previousSignature ) ) return;
      previousSignature = currentSignature;
    }
  }

  private String getVisibleListSignature( UiObject2 list )
  {
    try {
      return buildVisibleListSignature( list );
    } catch ( StaleObjectException e ) {
      UiObject2 freshList = mDevice.findObject( By.res( PACKAGE_NAME, "td_list" ) );
      if ( freshList == null ) return "<stale>";
      return buildVisibleListSignature( freshList );
    }
  }

  private String buildVisibleListSignature( UiObject2 list )
  {
    List< UiObject2 > children = list.getChildren();
    if ( children == null || children.isEmpty() ) return "<empty>";

    StringBuilder builder = new StringBuilder();
    builder.append( children.size() );
    for ( UiObject2 child : children ) {
      if ( child == null ) continue;
      builder.append( '|' );
      builder.append( child.getText() );
      builder.append( '@' );
      builder.append( child.getVisibleBounds().top );
    }
    return builder.toString();
  }

  private UiObject2 requireSurveyOnMainList( String surveyName )
  {
    waitForSurveyOnMainList( surveyName );
    UiObject2 row = findSurveyOnMainList( surveyName );
    assertNotNull( "Survey not visible on the main list: " + surveyName, row );
    return row;
  }

  private void swipeObjectVertically( UiObject2 object, boolean towardEnd )
  {
    assertNotNull( "Cannot swipe a null object", object );
    int left = object.getVisibleBounds().left;
    int top = object.getVisibleBounds().top;
    int width = object.getVisibleBounds().width();
    int height = object.getVisibleBounds().height();
    int centerX = left + width / 2;
    int startY = top + ( towardEnd ? (int)( height * 0.82f ) : (int)( height * 0.18f ) );
    int endY = top + ( towardEnd ? (int)( height * 0.18f ) : (int)( height * 0.82f ) );
    mDevice.swipe( centerX, startY, centerX, endY, 24 );
    waitForIdle();
    SystemClock.sleep( 250 );
  }

  private void clickObject( UiObject2 object )
  {
    assertNotNull( "Cannot click a null object", object );
    UiObject2 target = getInteractiveObject( object );
    target.click();
    waitForIdle();
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
    assertNotNull( "Cannot long-click a null object", object );
    UiObject2 target = getInteractiveObject( object );
    int centerX = target.getVisibleBounds().centerX();
    int centerY = target.getVisibleBounds().centerY();
    mDevice.swipe( centerX, centerY, centerX, centerY, 120 );
    waitForIdle();
  }

  private UiObject2 getInteractiveObject( UiObject2 object )
  {
    UiObject2 current = object;
    while ( current != null ) {
      if ( current.isClickable() || current.isLongClickable() ) return current;
      current = current.getParent();
    }
    return object;
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
