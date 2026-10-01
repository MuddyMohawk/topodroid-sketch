package com.topodroid.TDX;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.net.Uri;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.topodroid.util.TDFile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDPrefCat;
import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.SymbolType;
import com.topodroid.ui.MotionEventWrap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;

@RunWith( AndroidJUnit4.class )
public class SymbolSketchPreferencesInstrumentedTest
{
  private static final String UI_SURVEY = "symbol_memory_ui_case";
  private Context mContext;
  private Context mPreviousContext;
  private SharedPreferences mPrefs;
  private Map<String, ?> mSavedPrefs;
  private DataHelper mData;
  private long mSurvey;
  private long mOtherSurvey;

  @Before public void setUp()
  {
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    mPreviousContext = TDInstance.context;
    TDInstance.setContext( mContext );
    mPrefs = PreferenceManager.getDefaultSharedPreferences( mContext );
    mSavedPrefs = new HashMap<>( mPrefs.getAll() );
    SharedPreferences.Editor editor = mPrefs.edit();
    for ( String key : mSavedPrefs.keySet() ) {
      if ( key.startsWith( "DISTOX_STYLE_" ) || key.startsWith( "DISTOX_PRESET_" )
        || key.startsWith( "DISTOX_ACTIVE_SKETCH_" ) ) editor.remove( key );
    }
    editor.remove( TDSetting.REMEMBER_SYMBOL_STYLE_KEY );
    editor.remove( TDSetting.REMEMBER_SYMBOL_PRESET_KEY );
    editor.commit();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );
    if ( TopoDroidApp.mData == null ) TopoDroidApp.mData = new DataHelper( mContext );
    mData = TopoDroidApp.mData;
    String name = "symbol_memory_repo_" + System.nanoTime();
    mSurvey = mData.setSurvey( name, 0 );
    mOtherSurvey = mData.setSurvey( name + "_other", 0 );
    assertTrue( mSurvey >= 0 );
    assertTrue( mOtherSurvey >= 0 );
  }

  @After public void tearDown()
  {
    mData.doDeleteSurvey( mSurvey );
    mData.doDeleteSurvey( mOtherSurvey );
    SharedPreferences.Editor editor = mPrefs.edit().clear();
    for ( Map.Entry<String, ?> entry : mSavedPrefs.entrySet() ) {
      Object value = entry.getValue();
      String key = entry.getKey();
      if ( value instanceof String ) editor.putString( key, (String)value );
      else if ( value instanceof Boolean ) editor.putBoolean( key, (Boolean)value );
      else if ( value instanceof Integer ) editor.putInt( key, (Integer)value );
      else if ( value instanceof Long ) editor.putLong( key, (Long)value );
      else if ( value instanceof Float ) editor.putFloat( key, (Float)value );
      else if ( value instanceof Set ) editor.putStringSet( key, (Set<String>)value );
    }
    editor.commit();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );
    TDInstance.context = mPreviousContext;
  }

  private SymbolSketchPreferences.Use use( long survey, int type, String name, int style, int preset )
  {
    return new SymbolSketchPreferences.Use( survey, type, name, style, preset, true, true );
  }

  private SymbolSketchPreferences.Choice choice( long survey, int type, String name )
  {
    return SymbolSketchPreferences.load( mData, survey, type, name );
  }

  @Test public void choicesPersistIndependentlyAndDoNotLeakAcrossSurveysOrTypes()
  {
    assertTrue( TDSetting.mRememberSymbolStyle );
    assertTrue( TDSetting.mRememberSymbolPreset );
    SymbolSketchPreferences.Use wall = use( mSurvey, SymbolType.LINE, "wall", 2, 1 );
    SymbolSketchPreferences.rememberStyle( mData, wall, 3 );
    assertEquals( 3, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 0, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
    SymbolSketchPreferences.rememberPreset( mData, wall, 2 );
    assertEquals( 2, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
    assertEquals( 0, choice( mOtherSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 0, choice( mSurvey, SymbolType.POINT, "wall" ).style );
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );
    assertEquals( 3, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 2, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
  }

  @Test public void firstUseFillsMissingFieldsWithoutOverwritingNewerManualChoices()
  {
    SymbolSketchPreferences.Use wall = use( mSurvey, SymbolType.LINE, "wall", 2, 1 );
    SymbolSketchPreferences.rememberStyle( mData, wall, 3 );
    SymbolSketchPreferences.rememberMissing( mData, wall );
    assertEquals( 3, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 1, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
    SymbolSketchPreferences.rememberMissing( mData, use( mSurvey, SymbolType.LINE, "wall", 1, 4 ) );
    assertEquals( 3, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 1, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
  }

  @Test public void disabledSettingsFreezeEachKindOfMemoryIndependently()
  {
    SymbolSketchPreferences.Use point = use( mSurvey, SymbolType.POINT, "stalactite", 2, 1 );
    SymbolSketchPreferences.rememberMissing( mData, point );
    TDPrefHelper helper = new TDPrefHelper( mContext );
    TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_STYLE, TDSetting.REMEMBER_SYMBOL_STYLE_KEY, "false" );
    SymbolSketchPreferences.rememberStyle( mData, point, 3 );
    SymbolSketchPreferences.rememberPreset( mData, point, 4 );
    assertEquals( 2, choice( mSurvey, SymbolType.POINT, "stalactite" ).style );
    assertEquals( 4, choice( mSurvey, SymbolType.POINT, "stalactite" ).preset );
    TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_PRESET, TDSetting.REMEMBER_SYMBOL_PRESET_KEY, "false" );
    TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_STYLE, TDSetting.REMEMBER_SYMBOL_STYLE_KEY, "true" );
    SymbolSketchPreferences.rememberStyle( mData, point, 3 );
    SymbolSketchPreferences.rememberPreset( mData, point, 2 );
    assertEquals( 3, choice( mSurvey, SymbolType.POINT, "stalactite" ).style );
    assertEquals( 4, choice( mSurvey, SymbolType.POINT, "stalactite" ).preset );
    TDSetting.loadSecondaryPreferences( helper );
    assertTrue( TDSetting.mRememberSymbolStyle );
    assertFalse( TDSetting.mRememberSymbolPreset );
  }

  @Test public void malformedEntriesAreIgnoredAndHiddenSlotReferencesSurvive()
  {
    String stored = "{\"2\":{\"wall\":{\"style\":5,\"preset\":6},\"bad\":{\"style\":2.5,\"preset\":99}}}";
    mData.setValue( SymbolSketchPreferences.key( mSurvey ), stored );
    SymbolSketchPreferences.Choice wall = choice( mSurvey, SymbolType.LINE, "wall" );
    assertEquals( 2, wall.resolveStyle( 3, 3 ) );
    assertEquals( 1, wall.resolvePreset( 4, 1 ) );
    SymbolSketchPreferences.rememberMissing( mData, use( mSurvey, SymbolType.LINE, "wall", 2, 1 ) );
    assertEquals( 5, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    assertEquals( 6, choice( mSurvey, SymbolType.LINE, "wall" ).preset );
    assertEquals( 0, choice( mSurvey, SymbolType.LINE, "bad" ).style );
    assertEquals( 0, choice( mSurvey, SymbolType.LINE, "bad" ).preset );
    mData.setValue( SymbolSketchPreferences.key( mSurvey ), "broken" );
    assertEquals( 0, choice( mSurvey, SymbolType.LINE, "wall" ).style );
    SymbolSketchPreferences.rememberMissing( mData, use( mSurvey, SymbolType.LINE, "wall", 2, 1 ) );
    assertEquals( 2, choice( mSurvey, SymbolType.LINE, "wall" ).style );
  }

  @Test public void renameRetainsMemoryAndDeletionRemovesIt()
  {
    SymbolSketchPreferences.rememberMissing( mData, use( mSurvey, SymbolType.AREA, "water", 3, 2 ) );
    assertTrue( mData.renameSurvey( mSurvey, "symbol_memory_renamed_" + System.nanoTime() ) );
    assertEquals( 3, choice( mSurvey, SymbolType.AREA, "water" ).style );
    mData.doDeleteSurvey( mSurvey );
    assertNull( mData.getValue( SymbolSketchPreferences.key( mSurvey ) ) );
  }

  @Test public void recallSwitchesRoundTripThroughSettingsExportAndImport() throws Exception
  {
    File file = TDFile.getSettingsFile();
    byte[] previous = file.exists() ? Files.readAllBytes( file.toPath() ) : null;
    try {
      TDPrefHelper helper = new TDPrefHelper( mContext );
      TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_STYLE, TDSetting.REMEMBER_SYMBOL_STYLE_KEY, "false" );
      assertTrue( TDSetting.exportSettings( mContext, Uri.fromFile( file ), mPrefs, -1 ) );
      String exported = new String( Files.readAllBytes( file.toPath() ), StandardCharsets.UTF_8 );
      assertTrue( exported.contains( "B " + TDSetting.REMEMBER_SYMBOL_STYLE_KEY + " false" ) );
      assertTrue( exported.contains( "B " + TDSetting.REMEMBER_SYMBOL_PRESET_KEY + " true" ) );
      TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_STYLE, TDSetting.REMEMBER_SYMBOL_STYLE_KEY, "true" );
      TDSetting.updatePreference( helper, TDPrefCat.PREF_TOOL_PRESET, TDSetting.REMEMBER_SYMBOL_PRESET_KEY, "false" );
      // Import the drawing group (TDPrefKey.DR) from the ordinary full settings export.
      assertTrue( TDSetting.importSettings( mContext, Uri.fromFile( file ), mPrefs, 1 << 4 ) );
      assertFalse( TDSetting.mRememberSymbolStyle );
      assertTrue( TDSetting.mRememberSymbolPreset );
      assertFalse( mPrefs.getBoolean( TDSetting.REMEMBER_SYMBOL_STYLE_KEY, true ) );
      assertTrue( mPrefs.getBoolean( TDSetting.REMEMBER_SYMBOL_PRESET_KEY, false ) );
    } finally {
      if ( previous != null ) Files.write( file.toPath(), previous );
      else Files.deleteIfExists( file.toPath() );
    }
  }

  @Test public void drawingWindowRecallsWallPointAndAreaAndLearnsSuccessfulPlacement() throws Exception
  {
    VisualTestSupport support = openSketch();
    try {
      support.clickRecentLineByThName( SymbolLibrary.WALL );
      support.tapStyleButton( 3 );
      support.tapPresetButton( 2 );
      main( () -> assertTrue( window().handleActionBindingAction( TDSetting.SPEN_ACTION_TOGGLE_PRESET ) ) );
      assertEquals( 3, TDSetting.getActiveSketchPreset() );
      int point = BrushManager.getPointIndexByThName( SymbolLibrary.STALACTITE );
      assertTrue( point >= 0 );
      main( () -> window().pointSelected( point, false ) );
      assertEquals( 2, TDSetting.getActiveSketchStyle() );
      assertEquals( 3, TDSetting.getActiveSketchPreset() );
      long survey = surveyId();
      String pointName = BrushManager.getPointByIndex( point ).getFullThName();
      assertEquals( 0, choice( survey, SymbolType.POINT, pointName ).style );
      support.tapDrawingSurfaceNormalized( 0.45, 0.50 );
      assertEquals( 2, choice( survey, SymbolType.POINT, pointName ).style );
      support.assertLatestPointBrushWeight( 2.0f );
      support.tapPresetButton( 1 );
      support.clickRecentLineByThName( SymbolLibrary.WALL );
      assertEquals( 3, TDSetting.getActiveSketchStyle() );
      assertEquals( 3, TDSetting.getActiveSketchPreset() );
      main( () -> window().pointSelected( point, false ) );
      assertEquals( 2, TDSetting.getActiveSketchStyle() );
      assertEquals( 1, TDSetting.getActiveSketchPreset() );
      int area = BrushManager.getAreaIndexByThName( SymbolLibrary.WATER );
      assertTrue( area >= 0 );
      main( () -> window().areaSelected( area, false ) );
      support.tapStyleButton( 1 );
      support.tapPresetButton( 4 );
      support.clickRecentLineByThName( SymbolLibrary.WALL );
      main( () -> window().areaSelected( area, false ) );
      assertEquals( 1, TDSetting.getActiveSketchStyle() );
      assertEquals( 4, TDSetting.getActiveSketchPreset() );
      String saved = mData.getValue( SymbolSketchPreferences.key( survey ) );
      main( () -> invoke( "setToolsToolbars", new Class<?>[0] ) );
      assertEquals( saved, mData.getValue( SymbolSketchPreferences.key( survey ) ) );
      // Simulate restoring the active tool after another window changed global selections.
      main( () -> {
        TDSetting.selectSketchStyle( mPrefs, 3 );
        Field recalled = DrawingWindow.class.getDeclaredField( "mRecalledSymbol" );
        recalled.setAccessible( true );
        recalled.set( window(), null );
        invoke( "restoreSymbolPreferences", new Class<?>[0] );
      } );
      assertEquals( 1, TDSetting.getActiveSketchStyle() );
      TDPrefHelper helper = new TDPrefHelper( mContext );
      main( () -> {
        TDSetting.updatePreference( helper, TDPrefCat.PREF_STYLE_1, TDSetting.sketchStyleNameKey( 1 ), "Detail" );
        TDSetting.updatePreference( helper, TDPrefCat.PREF_STYLE_1, TDSetting.sketchStyleWeightKey( 1 ), "1.5" );
      } );
      support.clickRecentLineByThName( SymbolLibrary.WALL );
      main( () -> window().areaSelected( area, false ) );
      assertEquals( "Detail", TDSetting.getSketchStyleName( TDSetting.getActiveSketchStyle() ) );
      assertEquals( 1.5f, TDSetting.getSketchStyle( 1 ).weightOr( 0 ), 0.001f );
      int label = BrushManager.getPointIndexByThName( SymbolLibrary.LABEL );
      String labelName = BrushManager.getPointByIndex( label ).getFullThName();
      main( () -> window().pointSelected( label, false ) );
      support.tapDrawingSurfaceNormalized( 0.55, 0.55 );
      onView( withId( R.id.label_cancel ) ).perform( click() );
      assertEquals( 0, choice( survey, SymbolType.POINT, labelName ).style );
      support.tapDrawingSurfaceNormalized( 0.55, 0.55 );
      onView( withId( R.id.label_text ) ).perform( replaceText( "Memory label" ), closeSoftKeyboard() );
      onView( withId( R.id.label_ok ) ).perform( click() );
      assertEquals( 2, choice( survey, SymbolType.POINT, labelName ).style );
      support.tapStyleSettingsButton();
      onView( withText( R.string.pref_remember_symbol_style_title ) ).check( matches( isDisplayed() ) );
      support.pressBackToDrawingWindow();
      support.tapPresetSettingsButton();
      onView( withText( R.string.pref_remember_symbol_preset_title ) ).check( matches( isDisplayed() ) );
      support.pressBackToDrawingWindow();
    } finally { support.finish(); cleanupUiSurvey(); }
  }

  @Test public void drawingWindowDefersManualChoicesWithoutChangingCompletedStrokeAndCancelsCleanly() throws Exception
  {
    VisualTestSupport support = openSketch();
    try {
      support.clickRecentLineByThName( SymbolLibrary.WALL );
      long survey = surveyId();
      String name = BrushManager.getLineByThName( SymbolLibrary.WALL ).getFullThName();
      support.tapDrawingSurfaceNormalized( 0.50, 0.50 );
      assertEquals( 0, choice( survey, SymbolType.LINE, name ).style );
      main( () -> {
        invoke( "onTouchDown", fourFloats(), 100f, 200f, 0f, 0f );
        invoke( "requestSketchStyleSelection", new Class<?>[] { int.class }, 3 );
        invoke( "requestSketchPresetSelection", new Class<?>[] { int.class }, 2 );
        assertEquals( 2, TDSetting.getActiveSketchStyle() );
        assertEquals( 1, TDSetting.getActiveSketchPreset() );
        assertEquals( 3, choice( survey, SymbolType.LINE, name ).style );
        for ( int i = 1; i <= 12; ++i ) {
          invoke( "onTouchMove", new Class<?>[] { float.class, float.class, float.class, float.class, MotionEventWrap.class },
            100f + i * 10, 200f, (float)i, 0f, null );
        }
        invoke( "onTouchUp", fourFloats(), 230f, 200f, 13f, 0f );
      } );
      support.assertLatestLineBrushWeight( 2.0f );
      assertEquals( 3, TDSetting.getActiveSketchStyle() );
      assertEquals( 2, TDSetting.getActiveSketchPreset() );
      assertEquals( 3, choice( survey, SymbolType.LINE, name ).style );
      assertEquals( 2, choice( survey, SymbolType.LINE, name ).preset );
      main( () -> {
        invoke( "onTouchDown", fourFloats(), 100f, 200f, 0f, 0f );
        invoke( "requestSketchStyleSelection", new Class<?>[] { int.class }, 1 );
        MotionEvent cancel = MotionEvent.obtain( 0, 0, MotionEvent.ACTION_CANCEL, 100, 200, 0 );
        try { window().onTouch( window().findViewById( R.id.drawingSurface ), cancel ); }
        finally { cancel.recycle(); }
      } );
      assertEquals( 1, TDSetting.getActiveSketchStyle() );
      assertEquals( 1, choice( survey, SymbolType.LINE, name ).style );
      // An explicit tap on the active preset still teaches an unfamiliar symbol.
      int line = BrushManager.getLineIndexByThName( SymbolLibrary.USER );
      main( () -> window().lineSelected( line, false ) );
      support.tapStyleButton( 2 );
      assertEquals( 2, choice( survey, SymbolType.LINE, BrushManager.getLineByIndex( line ).getFullThName() ).style );
      support.tapPresetButton( 2 );
      int point = BrushManager.getPointIndexByThName( SymbolLibrary.STALACTITE );
      main( () -> window().pointSelected( point, false ) );
      support.tapStyleButton( 3 );
      support.tapPresetButton( 4 );
      main( () -> window().lineSelected( line, false ) );
      main( () -> {
        invoke( "onTouchDown", fourFloats(), 100f, 200f, 0f, 0f );
        window().pointSelected( point, false );
        assertEquals( SymbolType.LINE, window().mSymbol );
        assertEquals( 2, TDSetting.getActiveSketchStyle() );
        assertEquals( 2, TDSetting.getActiveSketchPreset() );
        invoke( "requestSketchStyleSelection", new Class<?>[] { int.class }, 1 );
        for ( int i = 1; i <= 24; ++i ) {
          invoke( "onTouchMove", new Class<?>[] { float.class, float.class, float.class, float.class, MotionEventWrap.class },
            100f + i * 10, 200f, (float)i, 0f, null );
        }
        invoke( "onTouchUp", fourFloats(), 350f, 200f, 25f, 0f );
        assertEquals( SymbolType.POINT, window().mSymbol );
        assertEquals( 1, TDSetting.getActiveSketchStyle() );
        assertEquals( 4, TDSetting.getActiveSketchPreset() );
      } );
      support.assertLatestLineBrushWeight( 2.0f );
      assertEquals( 2, choice( survey, SymbolType.LINE, BrushManager.getLineByIndex( line ).getFullThName() ).style );
      assertEquals( 1, choice( survey, SymbolType.POINT, BrushManager.getPointByIndex( point ).getFullThName() ).style );
    } finally { support.finish(); cleanupUiSurvey(); }
  }

  @Test public void initialSpecialPointDialogsLearnOnSaveButNotCancellationOrPropertyEdits() throws Exception
  {
    VisualTestSupport support = openSketch();
    try {
      long survey = surveyId();
      int bedding = BrushManager.getPointIndexByThName( BeddingAttitudePointBehavior.FULL_THERION_NAME );
      int legend = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.FULL_THERION_NAME );
      assertTrue( bedding >= 0 );
      assertTrue( legend >= 0 );
      main( () -> window().pointSelected( bedding, false ) );
      support.tapDrawingSurfaceNormalized( 0.45, 0.50 );
      onView( withId( R.id.button_cancel ) ).perform( click() );
      assertEquals( 0, choice( survey, SymbolType.POINT, BeddingAttitudePointBehavior.FULL_THERION_NAME ).style );
      main( () -> window().pointSelected( legend, false ) );
      support.tapDrawingSurfaceNormalized( 0.55, 0.55 );
      onView( withId( R.id.legend_cancel ) ).perform( click() );
      assertEquals( 0, choice( survey, SymbolType.POINT, TitleLegendPointBehavior.FULL_THERION_NAME ).style );
      support.tapDrawingSurfaceNormalized( 0.55, 0.55 );
      onView( withId( R.id.legend_save ) ).perform( click() );
      assertEquals( 2, choice( survey, SymbolType.POINT, TitleLegendPointBehavior.FULL_THERION_NAME ).style );
      assertEquals( TDSetting.getActiveSketchPreset(),
        choice( survey, SymbolType.POINT, TitleLegendPointBehavior.FULL_THERION_NAME ).preset );

      // Editing an existing object cannot fill a tool's missing preferences.
      mData.setValue( SymbolSketchPreferences.key( survey ), "{}" );
      main( () -> {
        Field surfaceField = DrawingWindow.class.getDeclaredField( "mDrawingSurface" );
        surfaceField.setAccessible( true );
        DrawingSurface surface = (DrawingSurface)surfaceField.get( window() );
        DrawingSemanticPointPath point = null;
        for ( DrawingPath path : surface.getManager( window().getPlotType() ).getCommands() ) {
          if ( path instanceof DrawingSemanticPointPath
            && TitleLegendPointBehavior.isTitleLegend( (DrawingSemanticPointPath)path ) ) {
            point = (DrawingSemanticPointPath)path;
          }
        }
        assertNotNull( point );
        DrawingTitleLegendDialog editor = new DrawingTitleLegendDialog( window(), point, false );
        editor.show();
        editor.findViewById( R.id.legend_save ).performClick();
      } );
      assertEquals( 0, choice( survey, SymbolType.POINT, TitleLegendPointBehavior.FULL_THERION_NAME ).style );
      assertEquals( 0, choice( survey, SymbolType.POINT, TitleLegendPointBehavior.FULL_THERION_NAME ).preset );
    } finally { support.finish(); cleanupUiSurvey(); }
  }

  private VisualTestSupport openSketch() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "symbol_memory" );
    support.prepareForPhysicalCompatCase();
    support.launchMainWindowOnAnyDevice();
    support.deleteGeneratedSurveyAndArtifacts( UI_SURVEY );
    support.createSurveyAndOpenShots( UI_SURVEY, "Style Test Team", "1", "symbol memory regression" );
    support.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
    support.openNewPlotFromShotWindow( "1", "1" );
    support.enterDrawMode();
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_STYLE,
      TDSetting.REMEMBER_SYMBOL_STYLE_KEY, "true" );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_PRESET,
      TDSetting.REMEMBER_SYMBOL_PRESET_KEY, "true" );
    return support;
  }

  private void cleanupUiSurvey()
  {
    long survey = mData.getSurveyId( UI_SURVEY );
    if ( survey >= 0 ) mData.doDeleteSurvey( survey );
  }

  private static DrawingWindow window() { return TopoDroidApp.mDrawingWindow; }

  private static long surveyId() throws Exception
  {
    Field field = DrawingWindow.class.getDeclaredField( "mSid" );
    field.setAccessible( true );
    return field.getLong( window() );
  }

  private static Class<?>[] fourFloats()
  {
    return new Class<?>[] { float.class, float.class, float.class, float.class };
  }

  private static Object invoke( String name, Class<?>[] types, Object... args ) throws Exception
  {
    Method method = DrawingWindow.class.getDeclaredMethod( name, types );
    method.setAccessible( true );
    return method.invoke( window(), args );
  }

  private interface MainAction { void run() throws Exception; }

  private static void main( MainAction action )
  {
    Throwable[] error = new Throwable[1];
    InstrumentationRegistry.getInstrumentation().runOnMainSync( () -> {
      try { action.run(); } catch ( Throwable t ) { error[0] = t; }
    } );
    if ( error[0] != null ) throw new AssertionError( error[0] );
  }
}
