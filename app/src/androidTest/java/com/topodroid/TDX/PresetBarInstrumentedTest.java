package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
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
public class PresetBarInstrumentedTest
{
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
    rememberPresetPrefs();
    resetPresetPrefs();
  }

  @After
  public void tearDown()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    removePresetPrefs( editor );
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
  public void freshPresetPrefs_defaultToFourSlotsWithSnapP4()
  {
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 4, TDSetting.getSketchPresetSlotCount() );
    assertEquals( "Fine", TDSetting.getSketchPresetName( 1 ) );
    assertEquals( "Smooth", TDSetting.getSketchPresetName( 2 ) );
    assertEquals( "Straight", TDSetting.getSketchPresetName( 3 ) );
    assertEquals( "Snap", TDSetting.getSketchPresetName( 4 ) );
    assertEquals( "PRESET 3 - Straight", TDSetting.getSketchPresetSettingsTitle( 3 ) );
    assertEquals( "PRESET 4 - Snap", TDSetting.getSketchPresetSettingsTitle( 4 ) );

    assertTrue( TDSetting.selectSketchPreset( mPrefs, 3 ) );
    assertTrue( TDSetting.isLineStyleStraight() );
    assertEquals( 5, TDSetting.mLineSegment );

    assertTrue( TDSetting.selectSketchPreset( mPrefs, 4 ) );
    assertTrue( TDSetting.isLineStyleSnapping() );
    assertEquals( 22.5f, TDSetting.getLineStyleSnapAngle(), 0.001f );
    assertEquals( 10, TDSetting.mLineSegment );
  }

  @Test
  public void presetCanStoreSnapLineStyle()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( "DISTOX_PRESET_DEFAULTS_VERSION", "2" );
    editor.putString( "DISTOX_PRESET_SLOTS", "5" );
    editor.putString( "DISTOX_PRESET_5_NAME", "Snap 90" );
    editor.putString( "DISTOX_PRESET_5_LINE_STYLE", "8" );
    editor.putString( "DISTOX_PRESET_5_LINE_SEGMENT", "5" );
    editor.apply();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 5, TDSetting.getSketchPresetSlotCount() );
    assertEquals( "Snap 90", TDSetting.getSketchPresetName( 5 ) );

    assertTrue( TDSetting.selectSketchPreset( mPrefs, 5 ) );
    assertTrue( TDSetting.isLineStyleSnapping() );
    assertEquals( 90.0f, TDSetting.getLineStyleSnapAngle(), 0.001f );
    assertEquals( 5, TDSetting.mLineSegment );
  }

  @Test
  public void legacyThreeSlotPrefs_resetToFourPresetDefaults()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( "DISTOX_PRESET_SLOTS", "3" );
    editor.putString( "DISTOX_PRESET_1_NAME", "Custom" );
    editor.putString( "DISTOX_PRESET_1_LINE_STYLE", "8" );
    editor.putString( "DISTOX_PRESET_1_LINE_SEGMENT", "12" );
    editor.putString( "DISTOX_PRESET_2_NAME", "Smooth" );
    editor.putString( "DISTOX_PRESET_2_LINE_STYLE", "0" );
    editor.putString( "DISTOX_PRESET_2_LINE_SEGMENT", "10" );
    editor.putString( "DISTOX_PRESET_3_NAME", "Straight" );
    editor.putString( "DISTOX_PRESET_3_LINE_STYLE", "5" );
    editor.putString( "DISTOX_PRESET_3_LINE_SEGMENT", "5" );
    editor.putString( "DISTOX_PRESET_4_NAME", "Snap 90" );
    editor.putString( "DISTOX_PRESET_4_LINE_STYLE", "8" );
    editor.putString( "DISTOX_PRESET_4_LINE_SEGMENT", "12" );
    editor.apply();

    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 4, TDSetting.getSketchPresetSlotCount() );
    assertEquals( "Fine", TDSetting.getSketchPresetName( 1 ) );
    assertEquals( "Snap", TDSetting.getSketchPresetName( 4 ) );
    assertEquals( "4", mPrefs.getString( "DISTOX_PRESET_SLOTS", null ) );
    assertEquals( "2", mPrefs.getString( "DISTOX_PRESET_DEFAULTS_VERSION", null ) );
    assertEquals( "Fine", mPrefs.getString( "DISTOX_PRESET_1_NAME", null ) );
    assertEquals( "1", mPrefs.getString( "DISTOX_PRESET_1_LINE_STYLE", null ) );
    assertEquals( "1", mPrefs.getString( "DISTOX_PRESET_1_LINE_SEGMENT", null ) );
    assertEquals( "Snap", mPrefs.getString( "DISTOX_PRESET_4_NAME", null ) );
    assertEquals( "6", mPrefs.getString( "DISTOX_PRESET_4_LINE_STYLE", null ) );
    assertEquals( "10", mPrefs.getString( "DISTOX_PRESET_4_LINE_SEGMENT", null ) );

    assertTrue( TDSetting.selectSketchPreset( mPrefs, 4 ) );
    assertTrue( TDSetting.isLineStyleSnapping() );
    assertEquals( 22.5f, TDSetting.getLineStyleSnapAngle(), 0.001f );
    assertEquals( 10, TDSetting.mLineSegment );
  }

  @Test
  public void currentThreeSlotPresetPrefs_preserveSlotCountAndHiddenPreset()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( "DISTOX_PRESET_DEFAULTS_VERSION", "2" );
    editor.putString( "DISTOX_PRESET_SLOTS", "3" );
    editor.putString( "DISTOX_PRESET_4_NAME", "Snap 90" );
    editor.putString( "DISTOX_PRESET_4_LINE_STYLE", "8" );
    editor.putString( "DISTOX_PRESET_4_LINE_SEGMENT", "12" );
    editor.apply();

    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 3, TDSetting.getSketchPresetSlotCount() );
    assertEquals( "Snap 90", TDSetting.getSketchPresetName( 4 ) );
    assertEquals( "3", mPrefs.getString( "DISTOX_PRESET_SLOTS", null ) );
    assertEquals( "Snap 90", mPrefs.getString( "DISTOX_PRESET_4_NAME", null ) );
    assertEquals( "8", mPrefs.getString( "DISTOX_PRESET_4_LINE_STYLE", null ) );
    assertEquals( "12", mPrefs.getString( "DISTOX_PRESET_4_LINE_SEGMENT", null ) );
  }

  @Test
  public void renamedPreset_updatesDisplayedNameAndSettingsTitle()
  {
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_PRESET_2, "DISTOX_PRESET_2_NAME", "Fast" );

    assertEquals( "Fast", TDSetting.getSketchPresetName( 2 ) );
    assertEquals( "PRESET 2 - Fast", TDSetting.getSketchPresetSettingsTitle( 2 ) );
  }

  @Test
  public void loweringSlotCount_hidesButPreservesPresetDefinitions()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    editor.putString( "DISTOX_PRESET_DEFAULTS_VERSION", "2" );
    editor.putString( "DISTOX_PRESET_SLOTS", "5" );
    editor.putString( "DISTOX_ACTIVE_SKETCH_PRESET", "5" );
    editor.putString( "DISTOX_PRESET_5_NAME", "Hidden" );
    editor.putString( "DISTOX_PRESET_5_LINE_STYLE", "5" );
    editor.putString( "DISTOX_PRESET_5_LINE_SEGMENT", "7" );
    editor.apply();
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( mContext ) );

    assertEquals( 5, TDSetting.getActiveSketchPreset() );
    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_PRESET, "DISTOX_PRESET_SLOTS", "3" );

    assertEquals( 3, TDSetting.getSketchPresetSlotCount() );
    assertEquals( 3, TDSetting.getActiveSketchPreset() );

    TDSetting.updatePreference( new TDPrefHelper( mContext ), TDPrefCat.PREF_TOOL_PRESET, "DISTOX_PRESET_SLOTS", "5" );
    assertEquals( "Hidden", TDSetting.getSketchPresetName( 5 ) );

    assertTrue( TDSetting.selectSketchPreset( mPrefs, 5 ) );
    assertTrue( TDSetting.isLineStyleStraight() );
    assertEquals( 7, TDSetting.mLineSegment );
  }

  private void rememberPresetPrefs()
  {
    remember( "DISTOX_LINE_STYLE" );
    remember( "DISTOX_LINE_SEGMENT" );
    remember( "DISTOX_PRESET_SLOTS" );
    remember( "DISTOX_PRESET_DEFAULTS_VERSION" );
    remember( "DISTOX_ACTIVE_SKETCH_PRESET" );
    for ( int preset = 1; preset <= TDSetting.SKETCH_PRESET_MAX; ++ preset ) {
      remember( TDSetting.sketchPresetNameKey( preset ) );
      remember( TDSetting.sketchPresetLineStyleKey( preset ) );
      remember( TDSetting.sketchPresetLineSegmentKey( preset ) );
    }
  }

  private void remember( String key )
  {
    if ( mPrefs.contains( key ) ) mSavedPrefs.put( key, mPrefs.getAll().get( key ) );
  }

  private void resetPresetPrefs()
  {
    SharedPreferences.Editor editor = mPrefs.edit();
    removePresetPrefs( editor );
    editor.putString( "DISTOX_LINE_STYLE", "1" );
    editor.putString( "DISTOX_LINE_SEGMENT", "1" );
    editor.apply();
  }

  private void removePresetPrefs( SharedPreferences.Editor editor )
  {
    editor.remove( "DISTOX_LINE_STYLE" );
    editor.remove( "DISTOX_LINE_SEGMENT" );
    editor.remove( "DISTOX_PRESET_SLOTS" );
    editor.remove( "DISTOX_PRESET_DEFAULTS_VERSION" );
    editor.remove( "DISTOX_ACTIVE_SKETCH_PRESET" );
    for ( int preset = 1; preset <= TDSetting.SKETCH_PRESET_MAX; ++ preset ) {
      editor.remove( TDSetting.sketchPresetNameKey( preset ) );
      editor.remove( TDSetting.sketchPresetLineStyleKey( preset ) );
      editor.remove( TDSetting.sketchPresetLineSegmentKey( preset ) );
    }
  }
}
