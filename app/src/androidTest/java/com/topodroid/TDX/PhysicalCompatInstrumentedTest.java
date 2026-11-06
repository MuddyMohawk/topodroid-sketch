package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
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

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.topodroid.util.MyFileProvider;
import com.topodroid.util.TDStatus;
import com.topodroid.types.LegType;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

@RunWith( com.topodroid.TDX.QaJUnit4.class )
@LargeTest
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.COMPAT)
@com.topodroid.TDX.QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.VANILLA})
public class PhysicalCompatInstrumentedTest
{
  @org.junit.Rule public final com.topodroid.TDX.QaFixtureRule qaFixture = new com.topodroid.TDX.QaFixtureRule();
  private static final String VANILLA_PACKAGE_DEFAULT = "com.topodroid.TDX";
  private static final String SKETCH_PACKAGE_DEFAULT = "com.topodroid.TDX.sketch";
  private static final String MAIN_WINDOW_CLASS = "com.topodroid.TDX.MainWindow";
  private static final String IMPORT_ACTION = "TopoDroid.intent.action.Import";
  private static final String PLOT_NAME = "1";
  private static final long UI_TIMEOUT_MS = 5000L;
  private static final long IMPORT_TIMEOUT_MS = 45000L;
  private static final long EXPORT_TIMEOUT_MS = 45000L;

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
    assertTrue("Compatibility survey must use a unique, path-safe QA name",mSurveyName.matches("compat_[A-Za-z0-9_]+"));
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
    progress( "draw styled Sketch compatibility probe" );
    drawCanonicalSketch();
    progress( "export Sketch ZIP" );
    File sketchZip = exportSketchZip( mSurveyName );
    mSupport.assertZipContainsSurveyCore( sketchZip );

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
    assertCanonicalLegTypes( mSurveyName );
    mSupport.openExistingPlanPlot( PLOT_NAME );
    mSupport.waitForDrawingWindow();
    assertEquals("Vanilla round trip must preserve all three committed drawing strokes",3,mSupport.countSketchLinePaths());
    mSupport.assertLatestLineBrushWeight(SketchBrushStyle.DEFAULT_WEIGHT_THICK);
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
    mSupport.tapViewByDevice( R.id.button_ok );
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

    // Vanilla can use a different working directory. Locate only this run's
    // uniquely named export; never assume or clean a user's field-data path.
    long deadline=SystemClock.uptimeMillis()+EXPORT_TIMEOUT_MS;
    File documents=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
    while(SystemClock.uptimeMillis()<deadline) {
      java.util.ArrayList<File> matches=new java.util.ArrayList<>();
      findGeneratedVanillaZip(documents,documents.getCanonicalPath(),surveyName+".zip",0,matches);
      assertTrue("Ambiguous Vanilla QA export paths: "+matches,matches.size()<=1);
      if(matches.size()==1)return mSupport.waitForFile(matches.get(0),Math.max(1,deadline-SystemClock.uptimeMillis()));
      SystemClock.sleep(200);
    }
    fail("Vanilla did not create its uniquely named ZIP in Documents within 45 seconds: "+surveyName);
    return null;
  }

  private void findGeneratedVanillaZip(File directory,String documents,String name,int depth,java.util.List<File> matches) throws Exception
  {
    if(depth>8 || QaFixtureRule.owns(directory))return;
    String canonical=directory.getCanonicalPath();
    if(!canonical.equals(documents) && !canonical.startsWith(documents+File.separator))return;
    File candidate=new File(directory,name);
    if(directory.getName().equals("zip") && candidate.isFile())matches.add(candidate);
    File[] children=directory.listFiles();
    if(children!=null)for(File child:children)if(child.isDirectory())findGeneratedVanillaZip(child,documents,name,depth+1,matches);
  }

  private void createCanonicalSurveyAndOpenSketch( String surveyName )
  {
    mSupport.createSurveyAndOpenShots( surveyName, "Physical Compat Team", "1", "physical vanilla compatibility" );
    mSupport.addManualShot( "1", "2", "10.0", "90.0", "0.0", false );
    mSupport.addManualShot( "2", "3", "6.0", "0.0", "0.0", false );
    mSupport.addManualShot( "2", "4", "5.0", "180.0", "0.0", false );
    mSupport.addManualShot( "2", "1", "10.1", "270.0", "0.0", false );
    mSupport.addManualShot( "2", "", "2.0", "45.0", "10.0", false );
    mSupport.addManualShot( "3", "", "3.0", "315.0", "-10.0", true );

    setPersistedLegType( surveyName, "2", "1", LegType.BACK );
    setPersistedLegType( surveyName, "3", "", LegType.XSPLAY );
    assertCanonicalLegTypes( surveyName );

    mSupport.pressBackToMainWindow();
    mSupport.openSurveyFromMainList( surveyName );
    assertCanonicalLegTypes( surveyName );
    mSupport.openNewPlotFromShotWindow( PLOT_NAME, "1" );
    mSupport.enterDrawMode();
  }

  private void setPersistedLegType( String surveyName, String from, String to, int legType )
  {
    long sid = requireSurveyId( surveyName );
    DBlock block = findShot( sid, from, to );
    TopoDroidApp.mData.updateShotLeg( block.mId, sid, legType );
  }

  private void assertCanonicalLegTypes( String surveyName )
  {
    long sid = requireSurveyId( surveyName );
    assertEquals( "plain-splay persisted type", LegType.NORMAL,
      findShot( sid, "2", "" ).getLegType() );
    assertEquals( "cross-splay persisted type", LegType.XSPLAY,
      findShot( sid, "3", "" ).getLegType() );
    assertEquals( "backsight persisted type", LegType.BACK,
      findShot( sid, "2", "1" ).getLegType() );
  }

  private long requireSurveyId( String surveyName )
  {
    assertNotNull( "Sketch data helper is not available", TopoDroidApp.mData );
    long sid = TopoDroidApp.mData.getSurveyId( surveyName );
    assertTrue( "Survey is missing from the Sketch database: " + surveyName, sid > 0L );
    return sid;
  }

  private DBlock findShot( long sid, String from, String to )
  {
    List< DBlock > shots = TopoDroidApp.mData.selectAllShots( sid, TDStatus.NORMAL );
    for ( DBlock block : shots ) {
      if ( from.equals( block.mFrom ) && to.equals( block.mTo ) ) return block;
    }
    fail( "Shot is missing from the Sketch database: " + from + " -> " + to );
    return null;
  }

  private void drawCanonicalSketch()
  {
    mSupport.assertStyleBarVisible( "Thin", "Standard", "Thick" );
    drawStyledUserLineCurve( 1, 1, 0.08, 0.18, 0.48, 0.18,  0.04 );
    drawStyledUserLineCurve( 2, 2, 0.08, 0.34, 0.48, 0.34, -0.05 );
    drawStyledUserLineCurve( 3, 1, 0.08, 0.50, 0.48, 0.50,  0.06 );
    mSupport.assertLatestLineBrushWeight( 5.0f );

    mSupport.tapStyleButton( 3 );
    mSupport.addOrdinaryPointWithActiveStyle( 80.0f, 120.0f );
    mSupport.assertLatestPointBrushWeight( 5.0f );
    mSupport.setCanonicalToolbarState();
  }

  private void drawStyledUserLineCurve( int style, int preset,
    double startX, double startY, double endX, double endY, double curveOffset )
  {
    mSupport.tapStyleButton( style );
    mSupport.tapPresetButton( preset );
    mSupport.clickRecentLineByThName( SymbolLibrary.USER );
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

  private UiObject2 waitForSurveyOnMainList( String packageName, String surveyName, long timeoutMs )
  {
    long deadline = SystemClock.uptimeMillis() + timeoutMs;
    waitForPackage(packageName,Math.min(10000,timeoutMs));
    while ( SystemClock.uptimeMillis() < deadline ) {
      dismissKnownPrompts(packageName,1);
      UiObject2 row = findSurveyRow( packageName, surveyName, 500 );
      if ( row != null ) return row;
      UiObject2 list=mDevice.findObject(By.pkg(packageName).scrollable(true));
      if(list!=null)list.scroll(androidx.test.uiautomator.Direction.DOWN,0.7f);
      SystemClock.sleep(100);
    }
    // Capture the failure before Sketch's activity cleanup changes focus. An
    // importer error, delayed list refresh, and a lost app are different causes.
    try {
      mDevice.dumpWindowHierarchy(new File(mSupport.getCaseArtifactsDirectory(),"import-wait-failure.xml"));
      mSupport.captureScreen("import-wait-failure.png");
      writeTextArtifact("import-wait-activity.txt",mDevice.executeShellCommand("dumpsys activity activities"));
      int uid=mInstrumentation.getTargetContext().getPackageManager().getApplicationInfo(packageName,0).uid;
      writeTextArtifact("import-wait-logcat.txt",mDevice.executeShellCommand("logcat -d --uid="+uid+" -t 200"));
    } catch(Exception diagnostic) {progress("Import failure diagnostics: "+diagnostic);}
    fail( "Survey did not become visible within " + timeoutMs + "ms in " + packageName + ": " + surveyName );
    return null;
  }

  private void openSurveyWindowFromMainListLongPress( String packageName, String surveyName )
  {
    UiObject2 row=waitForSurveyOnMainList(packageName,surveyName,10000);
    row.longClick();
    assertNotNull("Survey window did not open within 10 seconds",waitForOptional(By.res(packageName,"survey_team"),10000));
  }

  private void openMenuAndClickText( String packageName, String text )
  {
    clickRequired( By.res( packageName, "handle" ), "menu handle" );
    UiObject2 item=waitForOptional(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text),java.util.regex.Pattern.CASE_INSENSITIVE)),UI_TIMEOUT_MS);
    assertNotNull( "Timed out waiting for " + text, item );
    item.click();
    waitForIdle();
  }

  private void ensureZipExportDialog( String packageName )
  {
    UiObject2 zipOptions = mDevice.findObject(By.res(packageName,"layout_zip"));
    if ( zipOptions != null ) return;
    zipOptions=mDevice.findObject(By.res(packageName,"zip_overwrite"));
    if ( zipOptions != null ) return;

    VisualTestSupport.chooseSpinnerValue(mDevice,packageName,"spin","ZIP");
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
    mInstrumentation.runOnMainSync(() -> {});
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
