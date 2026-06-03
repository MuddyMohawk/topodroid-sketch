package com.topodroid.TDX;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.topodroid.util.MyFileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class PhysicalCompatInstrumentedTest
{
  private static final String VANILLA_PACKAGE_DEFAULT = "com.topodroid.TDX";
  private static final String SKETCH_PACKAGE_DEFAULT = "com.topodroid.TDX.sketch";
  private static final String MAIN_WINDOW_CLASS = "com.topodroid.TDX.MainWindow";
  private static final String IMPORT_ACTION = "TopoDroid.intent.action.Import";
  private static final String PLOT_NAME = "1";
  private static final long UI_TIMEOUT_MS = 30000L;
  private static final long IMPORT_TIMEOUT_MS = 90000L;
  private static final long EXPORT_TIMEOUT_MS = 90000L;

  private Instrumentation mInstrumentation;
  private UiDevice mDevice;
  private VisualTestSupport mSupport;
  private String mSurveyName;
  private String mVanillaPackage;
  private String mSketchPackage;

  @Before
  public void setUp() throws Exception
  {
    mInstrumentation = InstrumentationRegistry.getInstrumentation();
    mDevice = UiDevice.getInstance( mInstrumentation );
    Bundle args = InstrumentationRegistry.getArguments();
    mSurveyName = arg( args, "physical_compat_survey", defaultSurveyName() );
    mVanillaPackage = arg( args, "physical_compat_vanilla_package", VANILLA_PACKAGE_DEFAULT );
    mSketchPackage = arg( args, "physical_compat_sketch_package", SKETCH_PACKAGE_DEFAULT );
    mSupport = new VisualTestSupport( "physical_compat_" + mSurveyName );
  }

  @After
  public void tearDown()
  {
    if ( mSupport != null ) mSupport.finish();
  }

  @Test
  public void sketchVanillaZipRoundTrip_onPhysicalTablet() throws Exception
  {
    progress( "prepare Sketch physical compatibility data" );
    mSupport.prepareForPhysicalCompatCase();
    mSupport.launchMainWindowOnAnyDevice();

    progress( "create canonical Sketch survey" );
    createCanonicalSurveyAndOpenSketch( mSurveyName );
    progress( "draw canonical Sketch lines" );
    drawCanonicalSketch();
    progress( "export Sketch ZIP with symbols" );
    File sketchZip = exportSketchZip( mSurveyName );
    mSupport.assertZipContainsSketchLineSymbols( sketchZip );

    mSupport.copyFileToDownloads( sketchZip );
    progress( "import Sketch ZIP into vanilla" );
    importZipWithIntent( mVanillaPackage, sketchZip );
    waitForSurveyOnMainList( mVanillaPackage, mSurveyName, IMPORT_TIMEOUT_MS );

    progress( "export vanilla ZIP" );
    File vanillaZip = exportSurveyZipFromVanilla( mSurveyName );
    assertTrue( "Vanilla export ZIP does not exist: " + vanillaZip.getAbsolutePath(), vanillaZip.exists() );

    // The round-trip import uses the same survey name. Remove only the survey
    // created by this test run so the import is not blocked by a duplicate row.
    progress( "remove generated Sketch survey before round-trip import" );
    mSupport.deleteGeneratedSurveyAndArtifacts( mSurveyName );
    progress( "import vanilla ZIP back into Sketch" );
    importZipWithIntent( mSketchPackage, vanillaZip );

    progress( "open round-tripped Sketch survey" );
    mSupport.waitForSurveyOnMainList( mSurveyName );
    mSupport.openSurveyFromMainList( mSurveyName );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.waitForDrawingWindow();
    mSupport.captureScreen( "roundtrip-opened-in-sketch.png" );
    progress( "round-trip opened in Sketch" );
  }

  private File exportSketchZip( String surveyName ) throws Exception
  {
    mSupport.pressBackToShotWindow();
    mSupport.pressBackToMainWindow();
    mSupport.openSurveyWindowFromMainListLongPress( surveyName );
    mSupport.openCurrentMenuAndClickText( mSupport.string( R.string.menu_export ) );
    mSupport.chooseSpinnerValue( R.id.spin, "ZIP" );
    mSupport.setCheckboxChecked( R.id.export_share, false );
    mSupport.setCheckboxChecked( R.id.zip_symbols, true );
    mSupport.setZipSymbolsExportEnabled( true );
    mSupport.tapView( R.id.button_ok );
    return mSupport.waitForFile( mSupport.getZipFile( surveyName ), EXPORT_TIMEOUT_MS );
  }

  private File exportSurveyZipFromVanilla( String surveyName ) throws Exception
  {
    openSurveyWindowFromMainListLongPress( mVanillaPackage, surveyName );
    openMenuAndClickText( mVanillaPackage, "Export" );
    ensureZipExportDialog( mVanillaPackage );
    setCheckboxChecked( mVanillaPackage, "export_share", false );
    setCheckboxChecked( mVanillaPackage, "zip_overwrite", true );
    clickRequired( By.res( mVanillaPackage, "button_ok" ), "vanilla export OK" );

    File zipFile = new File(
      new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS ), "TDX/TopoDroid/zip" ),
      surveyName + ".zip"
    );
    return waitForFile( zipFile, EXPORT_TIMEOUT_MS );
  }

  private void createCanonicalSurveyAndOpenSketch( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Physical Compat Team", "1", "physical vanilla compatibility" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", false );
    mSupport.addManualShot( "2", "4", "5.0", "180.0", "0.0", true );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private void drawCanonicalSketch()
  {
    drawUserLineCurve( 1, SketchLineSymbolManager.LEGACY_TH_NAME_FINE,
      0.08, 0.14, 0.48, 0.14,  0.04 );
    drawUserLineCurve( 2, SketchLineSymbolManager.LEGACY_TH_NAME_FINE,
      0.08, 0.26, 0.48, 0.26, -0.04 );
    drawUserLineCurve( 1, SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD,
      0.08, 0.38, 0.48, 0.38,  0.05 );
    drawUserLineCurve( 2, SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD,
      0.08, 0.50, 0.48, 0.50, -0.05 );
    drawUserLineCurve( 1, SketchLineSymbolManager.LEGACY_TH_NAME_THICK,
      0.08, 0.62, 0.48, 0.62,  0.06 );
    drawUserLineCurve( 2, SketchLineSymbolManager.LEGACY_TH_NAME_THICK,
      0.08, 0.74, 0.48, 0.74, -0.06 );
    mSupport.setCanonicalToolbarState();
  }

  private void drawUserLineCurve( int preset, String lineThName,
    double startX, double startY, double endX, double endY, double curveOffset )
  {
    mSupport.tapPresetButton( preset );
    mSupport.clickRecentLineByThName( lineThName );
    mSupport.drawCurveStrokeNormalized( startX, startY, endX, endY, curveOffset, 30, 6 );
  }

  private void importZipWithIntent( String packageName, File zipFile ) throws Exception
  {
    assertTrue( "Import ZIP missing: " + zipFile.getAbsolutePath(), zipFile.exists() );
    Context context = mInstrumentation.getTargetContext();
    Uri uri = MyFileProvider.fileToUri( context, zipFile );
    Intent intent = new Intent( IMPORT_ACTION );
    intent.addCategory( Intent.CATEGORY_DEFAULT );
    intent.setClassName( packageName, MAIN_WINDOW_CLASS );
    intent.setDataAndType( uri, "application/zip" );
    intent.putExtra( "REQUEST", "unarchive" );
    intent.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
    context.grantUriPermission( packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION );

    writeTextArtifact( "import-start-" + artifactName( packageName ) + ".txt",
      "package=" + packageName + "\n"
      + "zip=" + zipFile.getAbsolutePath() + "\n"
      + "uri=" + uri.toString() + "\n"
      + "intent=" + intent.toString() + "\n" );
    context.startActivity( intent );
    System.out.println( "PhysicalCompat import start " + packageName + ": " + intent );
    progress( "import intent sent to " + packageName );
  }

  private void progress( String message )
  {
    String line = "PhysicalCompat " + mSurveyName + ": " + message;
    Bundle status = new Bundle();
    status.putString( Instrumentation.REPORT_KEY_STREAMRESULT, line + "\n" );
    mInstrumentation.sendStatus( 0, status );
    System.out.println( line );
    try {
      appendTextArtifact( "progress.txt", line + "\n" );
    } catch ( Exception e ) {
      System.out.println( "PhysicalCompat progress artifact failed: " + e.getMessage() );
    }
  }

  private void appendTextArtifact( String name, String text ) throws Exception
  {
    File file = new File( mSupport.getCaseArtifactsDirectory(), name );
    File parent = file.getParentFile();
    if ( parent != null ) parent.mkdirs();
    FileOutputStream output = new FileOutputStream( file, true );
    try {
      output.write( text.getBytes( "UTF-8" ) );
    } finally {
      output.close();
    }
  }

  private void writeTextArtifact( String name, String text ) throws Exception
  {
    File file = new File( mSupport.getCaseArtifactsDirectory(), name );
    File parent = file.getParentFile();
    if ( parent != null ) parent.mkdirs();
    FileOutputStream output = new FileOutputStream( file );
    try {
      output.write( text.getBytes( "UTF-8" ) );
    } finally {
      output.close();
    }
  }

  private String artifactName( String value )
  {
    return value.replaceAll( "[^A-Za-z0-9_.-]", "_" );
  }

  private boolean containsShellStartError( String output )
  {
    if ( output == null ) return false;
    String lower = output.toLowerCase( Locale.US );
    return lower.contains( "error:" )
      || lower.contains( "exception" )
      || lower.contains( "permission denial" )
      || lower.contains( "not found" )
      || lower.contains( "unable to resolve intent" );
  }

  private Uri externalStorageDocumentUri( File file )
  {
    String root = Environment.getExternalStorageDirectory().getAbsolutePath();
    String absolute = file.getAbsolutePath();
    assertTrue( "File is not under external storage: " + absolute, absolute.startsWith( root + File.separator ) );
    String relative = absolute.substring( root.length() + 1 ).replace( File.separatorChar, '/' );
    return Uri.parse( "content://com.android.externalstorage.documents/document/" + Uri.encode( "primary:" + relative ) );
  }

  private UiObject2 waitForSurveyOnMainList( String packageName, String surveyName, long timeoutMs )
  {
    waitForPackage( packageName, timeoutMs );
    dismissKnownPrompts( packageName, 6 );
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      UiObject2 row = findSurveyRow( packageName, surveyName, 500 );
      if ( row != null ) return row;
      scrollCurrentListToText( surveyName );
      SystemClock.sleep( 500 );
    }
    fail( "Survey not visible in " + packageName + ": " + surveyName );
    return null;
  }

  private void openSurveyWindowFromMainListLongPress( String packageName, String surveyName )
  {
    for ( int attempt = 0; attempt < 3; ++attempt ) {
      UiObject2 row = waitForSurveyOnMainList( packageName, surveyName, UI_TIMEOUT_MS );
      row.longClick();
      waitForIdle();
      if ( waitForOptional( By.res( packageName, "survey_team" ), 5000 ) != null ) return;
      if ( waitForOptional( By.res( packageName, "list" ), 1000 ) != null ) {
        mDevice.pressBack();
        waitForIdle();
      }
    }
    waitForRequired( By.res( packageName, "survey_team" ), "survey window" );
  }

  private void openMenuAndClickText( String packageName, String text )
  {
    clickRequired( By.res( packageName, "handle" ), "menu handle" );
    UiObject2 item = waitForOptional( By.text( text ), 1000 );
    if ( item == null ) item = waitForOptional( By.text( text.toUpperCase( Locale.US ) ), UI_TIMEOUT_MS );
    if ( item == null ) item = waitForOptional( By.text( text.toLowerCase( Locale.US ) ), 1000 );
    assertNotNull( "Timed out waiting for " + text, item );
    item.click();
    waitForIdle();
  }

  private void ensureZipExportDialog( String packageName )
  {
    UiObject2 zipOptions = waitForOptional( By.res( packageName, "layout_zip" ), 2000 );
    if ( zipOptions != null ) return;
    zipOptions = waitForOptional( By.res( packageName, "zip_overwrite" ), 1000 );
    if ( zipOptions != null ) return;

    clickRequired( By.res( packageName, "spin" ), "export spinner" );
    clickRequired( By.text( "ZIP" ), "ZIP export type" );
    assertNotNull( "ZIP export options did not become visible",
      waitForOptional( By.res( packageName, "zip_overwrite" ), UI_TIMEOUT_MS ) );
  }

  private void setCheckboxChecked( String packageName, String resourceName, boolean checked )
  {
    UiObject2 checkbox = waitForRequired( By.res( packageName, resourceName ), "checkbox " + resourceName );
    if ( checkbox.isChecked() != checked ) {
      checkbox.click();
      waitForIdle();
    }
    assertTrue( "Unexpected checkbox state for " + resourceName, checkbox.isChecked() == checked );
  }

  private File waitForFile( File file, long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( file.exists() ) return file;
      SystemClock.sleep( 500 );
    }
    fail( "Timed out waiting for file " + file.getAbsolutePath() );
    return file;
  }

  private UiObject2 findSurveyRow( String packageName, String surveyName, long waitMs )
  {
    long deadline = SystemClock.uptimeMillis() + waitMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      UiObject2 object = mDevice.findObject( By.pkg( packageName ).text( surveyName ) );
      if ( object != null ) return object;
      object = mDevice.findObject( By.pkg( packageName ).textContains( surveyName ) );
      if ( object != null ) return object;
      SystemClock.sleep( 150 );
    }
    return null;
  }

  private void scrollCurrentListToText( String text )
  {
    try {
      UiScrollable scrollable = new UiScrollable( new UiSelector().scrollable( true ).instance( 0 ) );
      scrollable.setAsVerticalList();
      scrollable.setMaxSearchSwipes( 10 );
      scrollable.scrollIntoView( new UiSelector().textContains( text ) );
    } catch ( UiObjectNotFoundException e ) {
      // The next polling pass will fail clearly if the row is still absent.
    }
  }

  private void dismissKnownPrompts( String packageName, int maxClicks )
  {
    for ( int click = 0; click < maxClicks; ++click ) {
      UiObject2 button = firstPresent(
        By.res( "com.android.permissioncontroller", "permission_allow_button" ),
        By.res( "com.android.permissioncontroller", "permission_allow_foreground_only_button" ),
        By.res( "android", "button1" ),
        By.res( packageName, "btn_ok" ),
        By.res( packageName, "button_ok" ),
        By.res( packageName, "btn_skip" ),
        By.res( packageName, "btn_next" ),
        By.text( "While using the app" ),
        By.text( "Only this time" ),
        By.text( "Allow" ),
        By.text( "OK" ),
        By.text( "Continue" ),
        By.text( "Got it" ),
        By.text( "Skip" )
      );
      if ( button == null ) return;
      button.click();
      waitForIdle();
      SystemClock.sleep( 500 );
    }
  }

  private UiObject2 firstPresent( BySelector... selectors )
  {
    for ( BySelector selector : selectors ) {
      UiObject2 object = mDevice.findObject( selector );
      if ( object != null ) return object;
    }
    return null;
  }

  private void clickRequired( BySelector selector, String label )
  {
    UiObject2 object = waitForRequired( selector, label );
    object.click();
    waitForIdle();
  }

  private UiObject2 waitForRequired( BySelector selector, String label )
  {
    UiObject2 object = waitForOptional( selector, UI_TIMEOUT_MS );
    assertNotNull( "Timed out waiting for " + label, object );
    return object;
  }

  private UiObject2 waitForOptional( BySelector selector, long timeoutMs )
  {
    return mDevice.wait( Until.findObject( selector ), timeoutMs );
  }

  private void waitForPackage( String packageName, long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    while ( SystemClock.uptimeMillis() < deadline ) {
      if ( packageName.equals( mDevice.getCurrentPackageName() ) ) return;
      if ( mDevice.hasObject( By.pkg( packageName ) ) ) return;
      SystemClock.sleep( 250 );
    }
    fail( "Timed out waiting for current package " + packageName
      + "; current=" + mDevice.getCurrentPackageName() );
  }

  private void waitForIdle()
  {
    mInstrumentation.waitForIdleSync();
    mDevice.waitForIdle();
  }

  private static String arg( Bundle args, String key, String defaultValue )
  {
    String value = args == null ? null : args.getString( key );
    return value == null || value.length() == 0 ? defaultValue : value;
  }

  private static String defaultSurveyName()
  {
    return "compat_" + Long.toString( System.currentTimeMillis() );
  }
}
