package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;
import com.topodroid.geo.BeddingAttitude;
import com.topodroid.geo.BeddingMeasurementModel;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@RunWith( AndroidJUnit4.class )
public class SpecialPointInstrumentedTest
{
  private static final int TDR_VERSION = 602055;
  private Context mPreviousContext;
  private boolean mPreviousCount;
  private boolean mPreviousOrthogonal;
  private float mPreviousVertical;
  private float mPreviousHorizontal;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( context );
    TDPath.clearSymbols();
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    mPreviousCount = TDSetting.mLRUDcount;
    mPreviousOrthogonal = TDSetting.mOrthogonalLRUD;
    mPreviousVertical = TDSetting.mLRUDvertical;
    mPreviousHorizontal = TDSetting.mLRUDhorizontal;
    TDSetting.mLRUDcount = false;
    TDSetting.mOrthogonalLRUD = false;
    TDSetting.mLRUDvertical = 45.0f;
    TDSetting.mLRUDhorizontal = 45.0f;
  }

  @After public void tearDown()
  {
    TDSetting.mLRUDcount = mPreviousCount;
    TDSetting.mOrthogonalLRUD = mPreviousOrthogonal;
    TDSetting.mLRUDvertical = mPreviousVertical;
    TDSetting.mLRUDhorizontal = mPreviousHorizontal;
    TDInstance.context = mPreviousContext;
  }

  @Test public void registryFactory_routesOnlyRegisteredTherionNames()
  {
    int ceiling_type = BrushManager.getPointIndexByThName( CeilingHeightPointBehavior.THERION_NAME );
    int pit_type = BrushManager.getPointIndexByThName( PitDepthPointBehavior.THERION_NAME );
    int bedding_type = BrushManager.getPointIndexByThName( BeddingAttitudePointBehavior.THERION_NAME );
    int sand_type = BrushManager.getPointIndexByThName( "sand" );
    assertTrue( ceiling_type >= 0 );
    assertTrue( pit_type >= 0 );
    assertTrue( bedding_type >= 0 );
    assertTrue( sand_type >= 0 );

    DrawingPointPath ceiling = DrawingPointFactory.createPlacement(
      ceiling_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath sand = DrawingPointFactory.createPlacement(
      sand_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath pit = DrawingPointFactory.createPlacement(
      pit_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath bedding = DrawingPointFactory.createPlacement(
      bedding_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath preview = DrawingPointFactory.createPreview(
      ceiling_type, 0.0f, 0.0f, PointScale.SCALE_M, 0 );

    assertTrue( ceiling instanceof DrawingSemanticPointPath );
    assertTrue( pit instanceof DrawingSemanticPointPath );
    assertTrue( bedding instanceof DrawingSemanticPointPath );
    assertFalse( sand instanceof DrawingSemanticPointPath );
    assertEquals( CeilingHeightPointBehavior.BEHAVIOR_ID,
      ((DrawingSemanticPointPath)ceiling).specialBehavior().behaviorId() );
    assertEquals( "10", preview.getPointText() );
    assertEquals( 175,
      ((CeilingHeightPointState)((DrawingSemanticPointPath)ceiling).specialState()).textScalePercent() );
    assertEquals( SketchPointScale.legacyScaleValue( PointScale.SCALE_S ),
      CeilingHeightPointBehavior.BASE_FOOTPRINT_SCALE, 0.001f );
    assertEquals( PitDepthPointBehavior.BEHAVIOR_ID,
      ((DrawingSemanticPointPath)pit).specialBehavior().behaviorId() );
    assertEquals( PitDepthPointBehavior.FULL_THERION_NAME, pit.getFullThName() );
    assertEquals( BeddingAttitudePointBehavior.FULL_THERION_NAME, bedding.getFullThName() );
  }

  @Test public void beddingState_roundTripsProvenanceProjectionAndTypography() throws Exception
  {
    int type = BrushManager.getPointIndexByThName( BeddingAttitudePointBehavior.THERION_NAME );
    DrawingSemanticPointPath original = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 100.0f, 100.0f, PointScale.SCALE_M, 1 );
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 90.0, 30.0 );
    BeddingAttitudePointState state = new BeddingAttitudePointState(
      true, BeddingAttitudePointState.Mode.FIT,
      attitude.unitNormal.east, attitude.unitNormal.north, attitude.unitNormal.up,
      "A1", new long[] { 11, 12, 13, 19 },
      new double[] { 1, 2, 3, 4 }, new double[] { 10, 20, 30, 40 },
      new double[] { -5, 0, 5, 10 }, "SURVEY_MAGNETIC", 0.0,
      BeddingMeasurementModel.DISTOX_CONSERVATIVE_V1, 0.002, 0.5, 0.5, 0.015, 0.25,
      "CAUTION", new String[] { "NO_REDUNDANCY", "VERTICAL_AZIMUTH_WEAK" },
      27.5, 32.5, 80.0, 100.0, false, "BOUNDED",
      25.0, 35.0, 75.0, 105.0, false, "BOUNDED",
      BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, true, 22.25, 18.5,
      Double.NaN, Double.NaN, false,
      SketchFontRegistry.FONT_SERIF, true, true, false, 135 );
    original.setPointText( "30" );
    original.setSpecialState( state, true );

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    original.toDataStream( output, 1 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded_path = DrawingPointPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertTrue( loaded_path instanceof DrawingSemanticPointPath );
    BeddingAttitudePointState loaded = (BeddingAttitudePointState)
      ((DrawingSemanticPointPath)loaded_path).specialState();
    assertTrue( loaded.configured );
    assertEquals( BeddingAttitudePointState.Mode.FIT, loaded.mode );
    assertEquals( "A1", loaded.stationName );
    assertEquals( 4, loaded.sourceShotIds.length );
    assertEquals( 19, loaded.sourceShotIds[3] );
    assertEquals( 4.0, loaded.sourceLengthsMeters[3], 0.0 );
    assertEquals( BeddingMeasurementModel.DISTOX_CONSERVATIVE_V1, loaded.measurementModelId );
    assertEquals( 0.015, loaded.surfaceScatterMeters, 0.0 );
    assertEquals( BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, loaded.viewKind );
    assertEquals( 22.25, loaded.canvasTraceAngleDegrees, 1.0e-8 );
    assertEquals( 30.0, loaded.attitude().dipDegrees, 1.0e-8 );
    assertEquals( SketchFontRegistry.FONT_SERIF, loaded.fontId() );
    assertEquals( 135, loaded.textScalePercent() );
    double east = loaded.normalEast;
    assertFalse( loaded_path.rotateBy( 45.0f ) );
    assertEquals( east, ((BeddingAttitudePointState)
      ((DrawingSemanticPointPath)loaded_path).specialState()).normalEast, 0.0 );

    Bitmap bitmap = Bitmap.createBitmap( 220, 220, Bitmap.Config.ARGB_8888 );
    loaded_path.draw( new Canvas( bitmap ), new Matrix(), 1.0f, new RectF( 0, 0, 220, 220 ) );
    assertTrue( countOpaque( bitmap ) > 30 );
    bitmap.recycle();
  }

  @Test public void lrudCalculator_handlesDirectAndReverseSplaysWithPresence()
  {
    DBlock leg = block( "A", "B", 5.0f, 0.0f, 0.0f );
    ArrayList< DBlock > splays = new ArrayList<>();
    splays.add( block( "A", "", 2.0f, 0.0f, 90.0f ) );
    splays.add( block( "", "A", 1.5f, 0.0f, 90.0f ) );
    StationLrudResult lrud = StationLrudCalculator.computeAtStation( leg, splays, "A" );

    assertTrue( lrud.hasUp );
    assertTrue( lrud.hasDown );
    assertEquals( 2.0f, lrud.up, 0.001f );
    assertEquals( 1.5f, lrud.down, 0.001f );

    splays.remove( 1 );
    StationLrudResult incomplete = StationLrudCalculator.computeAtStation( leg, splays, "A" );
    assertTrue( incomplete.hasUp );
    assertFalse( incomplete.hasDown );
  }

  @Test public void ceilingState_roundTripsAndStaysPrivate() throws Exception
  {
    int type = BrushManager.getPointIndexByThName( CeilingHeightPointBehavior.THERION_NAME );
    DrawingSemanticPointPath original = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 12.0f, 24.0f, PointScale.SCALE_L, 3 );
    original.setPointText( "7-ish" );
    original.setSpecialState( new CeilingHeightPointState(
      true, "wa ter 深", SketchFontRegistry.FONT_SERIF, true, true, true, 145 ), true );

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    original.toDataStream( output, 3 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded_path = DrawingPointPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertTrue( loaded_path instanceof DrawingSemanticPointPath );
    CeilingHeightPointState loaded = (CeilingHeightPointState)
      ((DrawingSemanticPointPath)loaded_path).specialState();
    assertNotNull( loaded );
    assertTrue( loaded.waterEnabled );
    assertEquals( "wa ter 深", loaded.waterDepth );
    assertEquals( SketchFontRegistry.FONT_SERIF, loaded.fontId() );
    assertEquals( 145, loaded.textScalePercent() );
    assertEquals( "7-ish", loaded_path.getPointText() );
    assertTrue( loaded_path.toTherion().contains( "-value 7-ish" ) );
    assertFalse( loaded_path.toTherion().contains( "-tdx-special" ) );
  }

  @Test public void unknownEnvelope_fallsBackWithoutDiscardingPayload() throws Exception
  {
    JSONObject json = new JSONObject();
    json.put( "envelope", 99 );
    json.put( "behavior", CeilingHeightPointBehavior.BEHAVIOR_ID );
    json.put( "stateVersion", 77 );
    json.put( "data", new JSONObject().put( "future", "kept" ) );
    String encoded = Base64.encodeToString( json.toString().getBytes( StandardCharsets.UTF_8 ),
      Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING );
    String options = SketchPrivateOptions.storeOption( "-id future", SketchPrivateOptions.OPTION_SPECIAL, encoded );
    int type = BrushManager.getPointIndexByThName( CeilingHeightPointBehavior.THERION_NAME );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createLoaded(
      type, CeilingHeightPointBehavior.THERION_NAME, 0.0f, 0.0f,
      PointScale.SCALE_M, "9", options, 0 );
    assertFalse( point.hasUsableSpecialState() );
    assertEquals( encoded, SketchPrivateOptions.getOptionValue( point.mOptions, SketchPrivateOptions.OPTION_SPECIAL ) );
  }

  @Test public void pitDepth_usesDownAndRoundTripsFreeformBoxedValue() throws Exception
  {
    StationLrudResult lrud = new StationLrudResult();
    lrud.down = 3.0f;
    assertEquals( "", PitDepthPointBehavior.initialValue( lrud, 1.0f ) );
    lrud.hasDown = true;
    assertEquals( "3", PitDepthPointBehavior.initialValue( lrud, 1.0f ) );
    assertEquals( "10", PitDepthPointBehavior.initialValue( lrud, 3.28084f ) );

    int type = BrushManager.getPointIndexByThName( PitDepthPointBehavior.THERION_NAME );
    DrawingSemanticPointPath original = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 40.0f, 50.0f, PointScale.SCALE_M, 2 );
    original.setPointText( "10C" );
    original.setSpecialState( new PitDepthPointState(
      SketchFontRegistry.FONT_SERIF, true, true, false, 140 ), true );

    RectF boxed = original.specialBehavior().renderer().sceneBounds( original );
    assertTrue( boxed.width() > boxed.height() );

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    original.toDataStream( output, 2 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded_path = DrawingPointPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertTrue( loaded_path instanceof DrawingSemanticPointPath );
    PitDepthPointState loaded = (PitDepthPointState)
      ((DrawingSemanticPointPath)loaded_path).specialState();
    assertEquals( "10C", loaded_path.getPointText() );
    assertEquals( SketchFontRegistry.FONT_SERIF, loaded.fontId() );
    assertTrue( loaded.bold() );
    assertTrue( loaded.italic() );
    assertEquals( 140, loaded.textScalePercent() );
    assertTrue( loaded_path.toTherion().contains( "u:pit-depth" ) );
    assertTrue( loaded_path.toTherion().contains( "-value 10C" ) );
    assertFalse( loaded_path.toTherion().contains( "-tdx-special" ) );

    loaded_path.setPointText( "7" );
    RectF square = ((DrawingSemanticPointPath)loaded_path).specialBehavior().renderer()
      .sceneBounds( (DrawingSemanticPointPath)loaded_path );
    assertEquals( square.width(), square.height(), 0.001f );
  }

  @Test public void framedRenderer_growsForWaterAndLongFreeformText()
  {
    int type = BrushManager.getPointIndexByThName( CeilingHeightPointBehavior.THERION_NAME );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 100.0f, 100.0f, PointScale.SCALE_M, 0 );
    point.setSketchBrushStyle( SketchBrushStyle.of( 2.0f, 1.0f, 1.0f, 0xff33ccff ) );
    point.setPointText( "10" );
    point.refreshSpecialBounds();
    RectF dry_bounds = point.specialBehavior().renderer().sceneBounds( point );
    assertEquals( dry_bounds.width(), dry_bounds.height(), 0.001f );
    float base_width = point.width();
    float base_height = point.height();

    point.setSpecialState( new CeilingHeightPointState(
      true, "5", SketchFontRegistry.FONT_DEFAULT, false, false, false, 175 ), false );
    point.setPointText( "7" );
    RectF water_bounds = point.specialBehavior().renderer().sceneBounds( point );
    assertTrue( water_bounds.height() > water_bounds.width() );

    point.setSpecialState( new CeilingHeightPointState(
      true, "water-depth?", SketchFontRegistry.FONT_DEFAULT, false, false, false, 100 ), false );
    point.setPointText( "very long ceiling" );
    assertTrue( point.width() > base_width );
    assertTrue( point.height() >= base_height );

    Bitmap bitmap = Bitmap.createBitmap( 240, 240, Bitmap.Config.ARGB_8888 );
    point.draw( new Canvas( bitmap ), new Matrix(), 1.0f, new RectF( 0, 0, 240, 240 ) );
    assertTrue( countOpaque( bitmap ) > 100 );
    bitmap.recycle();
  }

  @Test public void halfUnitFormatting_isLocaleNeutral()
  {
    assertEquals( "7", CeilingHeightPointBehavior.formatInitialHeight( 7.1f ) );
    assertEquals( "7.5", CeilingHeightPointBehavior.formatInitialHeight( 7.26f ) );
    assertEquals( "8", CeilingHeightPointBehavior.formatInitialHeight( 7.76f ) );
  }

  private static DBlock block( String from, String to, float length, float bearing, float clino )
  {
    DBlock block = new DBlock();
    block.mFrom = from;
    block.mTo = to;
    block.mLength = length;
    block.mBearing = bearing;
    block.mClino = clino;
    return block;
  }

  private static int countOpaque( Bitmap bitmap )
  {
    int[] pixels = new int[ bitmap.getWidth() * bitmap.getHeight() ];
    bitmap.getPixels( pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight() );
    int count = 0;
    for ( int pixel : pixels ) if ( ( pixel >>> 24 ) != 0 ) ++count;
    return count;
  }
}
