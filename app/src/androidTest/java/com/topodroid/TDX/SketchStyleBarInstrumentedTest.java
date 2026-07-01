package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDPrefCat;
import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class SketchStyleBarInstrumentedTest
{
  private static final String STYLE_SLOTS_KEY = "DISTOX_STYLE_SLOTS";
  private static final String STYLE_DEFAULTS_VERSION_KEY = "DISTOX_STYLE_DEFAULTS_VERSION";
  private static final String ACTIVE_STYLE_KEY = "DISTOX_ACTIVE_SKETCH_STYLE";
  private static final String SURVEY_STYLE_BAR = "style_bar_phase5_case";
  private static final String PLOT_NAME = "1";

  private Context mPreviousContext;
  private Context mContext;
  private SharedPreferences mPrefs;
  private Map<String, Object> mSavedPrefs;

  @Before
  public void setUp()
  {
    mPreviousContext = TDInstance.context;
    mContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( mContext );
    mPrefs = PreferenceManager.getDefaultSharedPreferences( mContext );
    mSavedPrefs = new HashMap<>();
    rememberStylePrefs();
    resetStylePrefs();
  }

  @After
  public void tearDown()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    removeStylePrefs( editor );
    for ( Map.Entry<String, Object> entry : mSavedPrefs.entrySet() ) {
      Object value = entry.getValue();
      if ( value instanceof String ) editor.putString( entry.getKey(), (String)value );
      else if ( value instanceof Boolean ) editor.putBoolean( entry.getKey(), (Boolean)value );
      else if ( value instanceof Integer ) editor.putInt( entry.getKey(), (Integer)value );
      else if ( value instanceof Long ) editor.putLong( entry.getKey(), (Long)value );
      else if ( value instanceof Float ) editor.putFloat( entry.getKey(), (Float)value );
    }
    editor.apply();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );
    TDInstance.context = mPreviousContext;
  }

  @Test
  public void freshStylePrefs_defaultToThinStandardThickWithStandardActive()
  {
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 3, TDSetting.getSketchStyleSlotCount() );
    assertEquals( 2, TDSetting.getActiveSketchStyle() );
    assertEquals( "Thin", TDSetting.getSketchStyleName( 1 ) );
    assertEquals( "Standard", TDSetting.getSketchStyleName( 2 ) );
    assertEquals( "Thick", TDSetting.getSketchStyleName( 3 ) );
    assertEquals( "STYLE 2 - Standard", TDSetting.getSketchStyleSettingsTitle( 2 ) );

    assertStyle( 1, 1.0f, 1.0f, 1.0f, false, 0xffffff );
    assertStyle( 2, 2.0f, 1.0f, 1.0f, false, 0xffffff );
    assertStyle( 3, 5.0f, 1.0f, 1.0f, false, 0xffffff );

    assertTrue( TDSetting.selectSketchStyle( mPrefs, 3 ) );
    assertEquals( 3, TDSetting.getActiveSketchStyle() );
    assertEquals( 5.0f, TDSetting.getSketchStyle( 3 ).weightOr( 0.0f ), 0.0001f );
  }

  @Test
  public void styleCanStoreFullFieldsAndSurviveReload()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( STYLE_DEFAULTS_VERSION_KEY, "1" );
    editor.putString( STYLE_SLOTS_KEY, "4" );
    editor.putString( ACTIVE_STYLE_KEY, "4" );
    editor.putString( TDSetting.sketchStyleNameKey( 4 ), "Red detail" );
    editor.putString( TDSetting.sketchStyleWeightKey( 4 ), "3.5" );
    editor.putString( TDSetting.sketchStylePointScaleKey( 4 ), "1.25" );
    editor.putString( TDSetting.sketchStyleOpacityKey( 4 ), "0.65" );
    editor.putBoolean( TDSetting.sketchStyleColorEnabledKey( 4 ), true );
    editor.putString( TDSetting.sketchStyleColorKey( 4 ), Integer.toString( 0x123456 ) );
    editor.apply();

    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 4, TDSetting.getSketchStyleSlotCount() );
    assertEquals( 4, TDSetting.getActiveSketchStyle() );
    assertEquals( "Red detail", TDSetting.getSketchStyleName( 4 ) );
    assertStyle( 4, 3.5f, 1.25f, 0.65f, true, 0x123456 );

    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 4, TDSetting.getActiveSketchStyle() );
    assertEquals( "Red detail", TDSetting.getSketchStyleName( 4 ) );
    assertStyle( 4, 3.5f, 1.25f, 0.65f, true, 0x123456 );
  }

  @Test
  public void loweringSlotCount_hidesButPreservesStyleDefinitions()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( STYLE_DEFAULTS_VERSION_KEY, "1" );
    editor.putString( STYLE_SLOTS_KEY, "5" );
    editor.putString( ACTIVE_STYLE_KEY, "5" );
    editor.putString( TDSetting.sketchStyleNameKey( 5 ), "Hidden" );
    editor.putString( TDSetting.sketchStyleWeightKey( 5 ), "7.5" );
    editor.putString( TDSetting.sketchStylePointScaleKey( 5 ), "1.4" );
    editor.putString( TDSetting.sketchStyleOpacityKey( 5 ), "0.4" );
    editor.apply();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 5, TDSetting.getActiveSketchStyle() );

    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_STYLE, STYLE_SLOTS_KEY, "3" );

    assertEquals( 3, TDSetting.getSketchStyleSlotCount() );
    assertEquals( 3, TDSetting.getActiveSketchStyle() );

    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_STYLE, STYLE_SLOTS_KEY, "5" );

    assertEquals( "Hidden", TDSetting.getSketchStyleName( 5 ) );
    assertStyle( 5, 7.5f, 1.4f, 0.4f, false, 0xffffff );
  }

  @Test
  public void editingStyle_normalizesInvalidValuesConservatively()
  {
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStyleNameKey( 2 ), "   " );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStyleWeightKey( 2 ), "-3" );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStylePointScaleKey( 2 ), "0" );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStyleOpacityKey( 2 ), "2" );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStyleColorEnabledKey( 2 ), "true" );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_STYLE_2, TDSetting.sketchStyleColorKey( 2 ), Integer.toString( 0xff00ff ) );

    assertEquals( "Standard", TDSetting.getSketchStyleName( 2 ) );
    assertEquals( "STYLE 2 - Standard", TDSetting.getSketchStyleSettingsTitle( 2 ) );
    assertStyle( 2, 2.0f, 1.0f, 1.0f, true, 0xff00ff );
  }

  @Test
  public void drawingWindow_styleRowAppliesThickToNewLineAndPoint() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "style_bar_phase5" );
    try {
      support.prepareForPhysicalCompatCase();
      support.launchMainWindowOnAnyDevice();
      support.deleteGeneratedSurveyAndArtifacts( SURVEY_STYLE_BAR );
      support.createSurveyAndOpenShots( SURVEY_STYLE_BAR, "Style Test Team", "1", "style bar regression" );
      support.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
      support.openNewPlotFromShotWindow( PLOT_NAME, "1" );
      support.enterDrawMode();

      support.assertStyleBarVisible( "Thin", "Standard", "Thick" );
      support.tapStyleButton( 3 );

      support.clickRecentLineByThName( SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD );
      support.drawCurveStrokeNormalized( 0.25, 0.45, 0.75, 0.48, 0.08, 8, 20 );
      support.assertLatestLineBrushWeight( 5.0f );

      support.addOrdinaryPointWithActiveStyle( 80.0f, 120.0f );
      support.assertLatestPointBrushWeight( 5.0f );
    } finally {
      support.finish();
    }
  }

  @Test
  public void drawingWindow_shortLineTapDoesNotDelayNextStyleSelection() throws Exception
  {
    VisualTestSupport support = new VisualTestSupport( "style_bar_short_line_tap" );
    try {
      support.prepareForPhysicalCompatCase();
      support.launchMainWindowOnAnyDevice();
      support.deleteGeneratedSurveyAndArtifacts( SURVEY_STYLE_BAR );
      support.createSurveyAndOpenShots( SURVEY_STYLE_BAR, "Style Test Team", "1", "style bar regression" );
      support.addManualShot( "1", "2", "10.0", "90.0", "0.0", true );
      support.openNewPlotFromShotWindow( PLOT_NAME, "1" );
      support.enterDrawMode();

      support.assertStyleBarVisible( "Thin", "Standard", "Thick" );
      support.clickRecentLineByThName( SketchLineSymbolManager.LEGACY_TH_NAME_STANDARD );
      support.tapDrawingSurfaceNormalized( 0.50, 0.50 );
      support.tapStyleButton( 3 );

      support.drawCurveStrokeNormalized( 0.25, 0.45, 0.75, 0.48, 0.08, 8, 20 );
      support.assertLatestLineBrushWeight( 5.0f );
    } finally {
      support.finish();
    }
  }

  private void assertStyle( int style, float weight, float pointScale, float opacity, boolean hasColor, int color )
  {
    SketchBrushStyle brushStyle = TDSetting.getSketchStyle( style );
    assertTrue( brushStyle.hasWeight() );
    assertTrue( brushStyle.hasPointScale() );
    assertTrue( brushStyle.hasOpacity() );
    if ( hasColor ) {
      assertTrue( brushStyle.hasColor() );
    } else {
      assertFalse( brushStyle.hasColor() );
    }
    assertEquals( weight, brushStyle.weightOr( 0.0f ), 0.0001f );
    assertEquals( pointScale, brushStyle.pointScaleOr( 0.0f ), 0.0001f );
    assertEquals( opacity, brushStyle.opacityOr( 0.0f ), 0.0001f );
    assertEquals( color & 0x00ffffff, brushStyle.colorOr( 0xffffff ) );
  }

  private void rememberStylePrefs()
  {
    remember( STYLE_SLOTS_KEY );
    remember( STYLE_DEFAULTS_VERSION_KEY );
    remember( ACTIVE_STYLE_KEY );
    for ( int style = 1; style <= TDSetting.SKETCH_STYLE_MAX; ++ style ) {
      remember( TDSetting.sketchStyleNameKey( style ) );
      remember( TDSetting.sketchStyleWeightKey( style ) );
      remember( TDSetting.sketchStylePointScaleKey( style ) );
      remember( TDSetting.sketchStyleOpacityKey( style ) );
      remember( TDSetting.sketchStyleColorEnabledKey( style ) );
      remember( TDSetting.sketchStyleColorKey( style ) );
    }
  }

  private void remember( String key )
  {
    if ( mPrefs.contains( key ) ) mSavedPrefs.put( key, mPrefs.getAll().get( key ) );
  }

  private void resetStylePrefs()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    removeStylePrefs( editor );
    editor.apply();
  }

  private void removeStylePrefs( SharedPreferences.Editor editor )
  {
    editor.remove( STYLE_SLOTS_KEY );
    editor.remove( STYLE_DEFAULTS_VERSION_KEY );
    editor.remove( ACTIVE_STYLE_KEY );
    for ( int style = 1; style <= TDSetting.SKETCH_STYLE_MAX; ++ style ) {
      editor.remove( TDSetting.sketchStyleNameKey( style ) );
      editor.remove( TDSetting.sketchStyleWeightKey( style ) );
      editor.remove( TDSetting.sketchStylePointScaleKey( style ) );
      editor.remove( TDSetting.sketchStyleOpacityKey( style ) );
      editor.remove( TDSetting.sketchStyleColorEnabledKey( style ) );
      editor.remove( TDSetting.sketchStyleColorKey( style ) );
    }
  }
}
