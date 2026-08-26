package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.topodroid.prefs.TDPrefHelper;
import com.topodroid.prefs.TDSetting;
import com.topodroid.ui.SegmentedToggleBar;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
  private String mPreviousLengthUnit;

  @Before public void setUp()
  {
    mPreviousContext = TDInstance.context;
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    TDInstance.setContext( context );
    TDSetting.loadSecondaryPreferences( new TDPrefHelper( context ) );
    TDPath.clearSymbols();
    TopoDroidApp.installSymbols( true );
    BrushManager.reloadPointLibrary( context, context.getResources() );
    BrushManager.reloadLineLibrary( context.getResources() );
    mPreviousCount = TDSetting.mLRUDcount;
    mPreviousOrthogonal = TDSetting.mOrthogonalLRUD;
    mPreviousVertical = TDSetting.mLRUDvertical;
    mPreviousHorizontal = TDSetting.mLRUDhorizontal;
    mPreviousLengthUnit = TDSetting.mUnitLengthStr;
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
    TDSetting.mUnitLengthStr = mPreviousLengthUnit;
    TDInstance.context = mPreviousContext;
  }

  @Test public void registryFactory_routesOnlyRegisteredTherionNames()
  {
    int ceiling_type = BrushManager.getPointIndexByThName( CeilingHeightPointBehavior.THERION_NAME );
    int pit_type = BrushManager.getPointIndexByThName( PitDepthPointBehavior.THERION_NAME );
    int bedding_type = BrushManager.getPointIndexByThName( BeddingAttitudePointBehavior.THERION_NAME );
    int title_legend_type = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.THERION_NAME );
    int caver_type = BrushManager.getPointIndexByThName( CaverPointBehavior.THERION_NAME );
    int sand_type = BrushManager.getPointIndexByThName( "sand" );
    assertTrue( ceiling_type >= 0 );
    assertTrue( pit_type >= 0 );
    assertTrue( bedding_type >= 0 );
    assertTrue( title_legend_type >= 0 );
    assertTrue( caver_type >= 0 );
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
    DrawingPointPath title_legend = DrawingPointFactory.createPlacement(
      title_legend_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );
    DrawingPointPath caver = DrawingPointFactory.createPlacement(
      caver_type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );

    assertTrue( ceiling instanceof DrawingSemanticPointPath );
    assertTrue( pit instanceof DrawingSemanticPointPath );
    assertTrue( bedding instanceof DrawingSemanticPointPath );
    assertTrue( title_legend instanceof DrawingSemanticPointPath );
    assertTrue( caver instanceof DrawingSemanticPointPath );
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
    assertTrue( ((DrawingSemanticPointPath)title_legend).previewUsesAuthoredGlyph() );
    assertEquals( CaverPointBehavior.BEHAVIOR_ID,
      ((DrawingSemanticPointPath)caver).specialBehavior().behaviorId() );
    assertFalse( BrushManager.isPointOrientable( caver_type ) );
    assertFalse( BrushManager.isPointScalable( caver_type ) );
  }

  @Test public void caverState_roundTripsAndStaysTrueScaleAtFeet() throws Exception
  {
    int type = BrushManager.getPointIndexByThName( CaverPointBehavior.THERION_NAME );
    assertTrue( type >= 0 );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 100.0f, 150.0f, PointScale.SCALE_XS, 2 );
    CaverPointState defaults = (CaverPointState)point.specialState();
    assertEquals( CaverPointState.Variant.MAN, defaults.variant );
    assertEquals( 1.778, defaults.heightMeters, 0.0 );
    assertEquals( SpecialPointPlacementAction.NONE,
      point.initializePlacement( new SpecialPointPlacementContext( null ) ) );
    assertNotNull( SketchPrivateOptions.getOptionValue(
      point.mOptions, SketchPrivateOptions.OPTION_SPECIAL ) );

    RectF man = point.exactSpecialBounds( false );
    float expected_height = (float)( CaverPointState.DEFAULT_HEIGHT_METERS * DrawingUtil.SCALE_FIX );
    assertEquals( expected_height, man.height(), 0.001f );
    assertEquals( 150.0f, man.bottom, 0.001f );
    assertEquals( 100.0f, man.centerX(), 0.001f );
    assertEquals( expected_height * CaverPointRenderer.MAN_ASPECT, man.width(), 0.001f );

    Bitmap rendered_man = Bitmap.createBitmap( 220, 180, Bitmap.Config.ARGB_8888 );
    point.draw( new Canvas( rendered_man ), new Matrix(), 1.0f, new RectF( 0, 0, 220, 180 ) );
    assertTrue( countOpaque( rendered_man ) > 100 );
    assertOpaqueInside( rendered_man, man );
    rendered_man.recycle();

    point.setScale( PointScale.SCALE_XL );
    assertFalse( point.setExactPointScale( 3.0f ) );
    point.setSketchBrushStyle( SketchBrushStyle.of( 3.0f, 2.5f, 0.7f, 0xff00ff00 ) );
    RectF styled = point.exactSpecialBounds( false );
    assertEquals( man, styled );

    DrawingSemanticPointPath transformed = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 100.0f, 150.0f, PointScale.SCALE_M, 0 );
    Matrix doubled = new Matrix();
    doubled.setScale( 2.0f, 2.0f );
    transformed.scaleBy( 2.0f, doubled );
    RectF scaled = transformed.exactSpecialBounds( false );
    assertEquals( expected_height, scaled.height(), 0.001f );
    assertEquals( 200.0f, scaled.centerX(), 0.001f );
    assertEquals( 300.0f, scaled.bottom, 0.001f );
    float[] affine_values = { 2.0f, 0.0f, 5.0f, 0.0f, 0.5f, 7.0f };
    Matrix affine = new Matrix();
    affine.setValues( new float[] { 2.0f, 0.0f, 5.0f, 0.0f, 0.5f, 7.0f, 0.0f, 0.0f, 1.0f } );
    transformed.affineTransformBy( affine_values, affine );
    RectF affine_bounds = transformed.exactSpecialBounds( false );
    assertEquals( expected_height, affine_bounds.height(), 0.001f );
    assertEquals( 405.0f, affine_bounds.centerX(), 0.001f );
    assertEquals( 157.0f, affine_bounds.bottom, 0.001f );

    point.setSpecialState( new CaverPointState( CaverPointState.Variant.WOMAN, 1.65 ), true );
    RectF woman = point.exactSpecialBounds( false );
    assertEquals( 1.65f * DrawingUtil.SCALE_FIX, woman.height(), 0.001f );
    assertEquals( 150.0f, woman.bottom, 0.001f );
    assertEquals( 100.0f, woman.centerX(), 0.001f );
    assertEquals( woman.height() * CaverPointRenderer.WOMAN_ASPECT, woman.width(), 0.001f );
    assertTrue( woman.width() < man.width() );

    Bitmap rendered = Bitmap.createBitmap( 220, 180, Bitmap.Config.ARGB_8888 );
    point.draw( new Canvas( rendered ), new Matrix(), 1.0f, new RectF( 0, 0, 220, 180 ) );
    assertTrue( countOpaque( rendered ) > 100 );
    assertOpaqueInside( rendered, woman );
    rendered.recycle();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    point.toDataStream( output, 2 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded_path = DrawingPointPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertTrue( loaded_path instanceof DrawingSemanticPointPath );
    CaverPointState loaded = (CaverPointState)((DrawingSemanticPointPath)loaded_path).specialState();
    assertEquals( CaverPointState.Variant.WOMAN, loaded.variant );
    assertEquals( 1.65, loaded.heightMeters, 0.0 );
    assertTrue( loaded_path.toTherion().contains( CaverPointBehavior.FULL_THERION_NAME ) );
    assertFalse( loaded_path.toTherion().contains( "-tdx-special" ) );
  }

  @Test public void caverHeightUnits_andCenteredPreviewsAreStable()
  {
    CaverHeightUnits.FeetInches default_height =
      CaverHeightUnits.fromMeters( CaverPointState.DEFAULT_HEIGHT_METERS );
    assertEquals( 5, default_height.feet );
    assertEquals( 10.0, default_height.inches, 1.0e-8 );
    assertEquals( CaverPointState.DEFAULT_HEIGHT_METERS,
      CaverHeightUnits.toMeters( 5, 10.0 ), 1.0e-12 );
    assertEquals( 1.7907, CaverHeightUnits.toMeters( 5, 10.5 ), 1.0e-12 );
    String formatted = CaverPointEditorController.formatDecimal( 1.778 );
    assertEquals( 1.778, CaverPointEditorController.parseDecimal( formatted ), 1.0e-12 );

    String previous_unit = TDSetting.mUnitLengthStr;
    try {
      TDSetting.mUnitLengthStr = "ft";
      assertTrue( CaverPointEditorController.usesFeet() );
      TDSetting.mUnitLengthStr = "m";
      assertFalse( CaverPointEditorController.usesFeet() );
    } finally {
      TDSetting.mUnitLengthStr = previous_unit;
    }

    int type = BrushManager.getPointIndexByThName( CaverPointBehavior.THERION_NAME );
    SymbolInterface symbol = BrushManager.getPointByIndex( type );
    SymbolPreviewRenderer preview = SymbolPreviewRenderer.create(
      com.topodroid.types.SymbolType.POINT, type, symbol, 1.0f );
    assertNotNull( preview );
    Bitmap palette = Bitmap.createBitmap( 160, 100, Bitmap.Config.ARGB_8888 );
    preview.draw( new Canvas( palette ), new RectF( 0, 0, 160, 100 ) );
    assertCenteredInk( palette, 2.0f );
    palette.recycle();

    TitleLegendPointState.Row row = new TitleLegendPointState.Row(
      "caver-preview", TitleLegendPointState.Kind.POINT, CaverPointBehavior.THERION_NAME,
      "Caver", null, TitleLegendPointState.Preview.standard() );
    SymbolSwatchSnapshot swatch = SymbolSwatchSnapshot.create(
      row, new TitleLegendLayout.InstalledSymbolResolver() );
    assertNotNull( swatch );
    Bitmap legend = Bitmap.createBitmap( 160, 100, Bitmap.Config.ARGB_8888 );
    swatch.draw( new Canvas( legend ), new RectF( 0, 0, 160, 100 ), 0 );
    assertCenteredInk( legend, 2.0f );
    legend.recycle();
  }

  @Test public void caverEditor_validatesSaveCancelAndUnitChanges()
  {
    final Context context = InstrumentationRegistry.getInstrumentation()
      .getTargetContext().getApplicationContext();
    final String previous_unit = TDSetting.mUnitLengthStr;
    try {
      InstrumentationRegistry.getInstrumentation().runOnMainSync( new Runnable() {
        @Override public void run()
        {
          int type = BrushManager.getPointIndexByThName( CaverPointBehavior.THERION_NAME );
          DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
            type, 10.0f, 20.0f, PointScale.SCALE_M, 0 );

          TDSetting.mUnitLengthStr = "m";
          LinearLayout metric_container = new LinearLayout( context );
          EditText primary_text = new EditText( context );
          CaverPointEditorController metric = new CaverPointEditorController( context, point );
          metric.bind( metric_container, primary_text );
          EditText meters = (EditText)metric_container.findViewById( R.id.caver_height_meters );
          SegmentedToggleBar variant =
            (SegmentedToggleBar)metric_container.findViewById( R.id.caver_variant );
          assertEquals( View.GONE, primary_text.getVisibility() );
          assertEquals( View.VISIBLE,
            metric_container.findViewById( R.id.caver_metric_fields ).getVisibility() );
          assertEquals( View.GONE,
            metric_container.findViewById( R.id.caver_imperial_fields ).getVisibility() );

          meters.setText( "0" );
          assertFalse( metric.canApply() );
          assertNotNull( meters.getError() );
          assertEquals( CaverPointState.DEFAULT_HEIGHT_METERS,
            ((CaverPointState)point.specialState()).heightMeters, 0.0 );

          meters.setText( "1.65" );
          variant.setSelectedIndex( 1 );
          assertTrue( metric.canApply() );
          metric.cancel();
          CaverPointState cancelled = (CaverPointState)point.specialState();
          assertEquals( CaverPointState.Variant.MAN, cancelled.variant );
          assertEquals( CaverPointState.DEFAULT_HEIGHT_METERS, cancelled.heightMeters, 0.0 );
          assertTrue( metric.canApply() );
          metric.apply();
          CaverPointState saved_metric = (CaverPointState)point.specialState();
          assertEquals( CaverPointState.Variant.WOMAN, saved_metric.variant );
          assertEquals( 1.65, saved_metric.heightMeters, 0.0 );

          TDSetting.mUnitLengthStr = "ft";
          LinearLayout imperial_container = new LinearLayout( context );
          CaverPointEditorController imperial = new CaverPointEditorController( context, point );
          imperial.bind( imperial_container, new EditText( context ) );
          EditText feet = (EditText)imperial_container.findViewById( R.id.caver_height_feet );
          EditText inches = (EditText)imperial_container.findViewById( R.id.caver_height_inches );
          assertEquals( View.VISIBLE,
            imperial_container.findViewById( R.id.caver_imperial_fields ).getVisibility() );
          assertTrue( imperial.canApply() );
          imperial.apply();
          assertEquals( 1.65, ((CaverPointState)point.specialState()).heightMeters, 0.0 );

          inches.setText( "12" );
          assertFalse( imperial.canApply() );
          assertNotNull( inches.getError() );
          assertEquals( 1.65, ((CaverPointState)point.specialState()).heightMeters, 0.0 );
          feet.setText( "5" );
          inches.setText( "10.5" );
          assertTrue( imperial.canApply() );
          imperial.apply();
          assertEquals( 1.7907, ((CaverPointState)point.specialState()).heightMeters, 1.0e-12 );

          TDSetting.mUnitLengthStr = "m";
          LinearLayout reopened_container = new LinearLayout( context );
          CaverPointEditorController reopened = new CaverPointEditorController( context, point );
          reopened.bind( reopened_container, new EditText( context ) );
          assertTrue( reopened.canApply() );
          reopened.apply();
          assertEquals( 1.7907, ((CaverPointState)point.specialState()).heightMeters, 1.0e-12 );
        }
      } );
    } finally {
      TDSetting.mUnitLengthStr = previous_unit;
    }
  }

  @Test public void titleLegend_roundTripsRequestedShapeAndPreparedExpansion() throws Exception
  {
    int type = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.THERION_NAME );
    assertTrue( type >= 0 );
    DrawingSemanticPointPath original = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 20.0f, 20.0f, PointScale.SCALE_M, 0 );
    ArrayList< TitleLegendPointState.Row > rows = new ArrayList<>();
    for ( int i = 0; i < 7; ++i ) {
      rows.add( TitleLegendPointState.Row.custom().withLabel( "Custom " + ( i + 1 ) ) );
    }
    TitleLegendPointState state = new TitleLegendPointState( "legend-test", false, true, 2, 3,
      0.72f, SketchTextStyle.defaultStyle(), rows, java.util.Collections.< String >emptySet() );
    original.setSpecialState( state, true );
    TitleLegendLayout layout = (TitleLegendLayout)original.preparedSpecialState();
    TitleLegendLayout full_size_layout = TitleLegendLayout.prepare( original, state.withLegendScale( 1.0f ) );
    assertNotNull( layout );
    assertEquals( 2, state.requestedColumns );
    assertEquals( 3, layout.capacity.renderedColumns );
    assertTrue( layout.capacity.expanded );
    assertEquals( full_size_layout.localBounds.width() * 0.72f,
      layout.localBounds.width(), 0.1f );
    assertEquals( full_size_layout.localBounds.height() * 0.72f,
      layout.localBounds.height(), 0.1f );
    assertTrue( layout.localBounds.width() > layout.localBounds.height() );

    Bitmap bitmap = Bitmap.createBitmap( 900, 500, Bitmap.Config.ARGB_8888 );
    Matrix matrix = new Matrix();
    matrix.setScale( 0.45f, 0.45f );
    original.draw( new Canvas( bitmap ), matrix, 1.0f, new RectF( 0, 0, 2000, 1200 ) );
    assertTrue( countOpaque( bitmap ) > 100 );
    bitmap.recycle();

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    original.toDataStream( output, 1 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'P', input.read() );
    DrawingPointPath loaded_path = DrawingPointPath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertTrue( loaded_path instanceof DrawingSemanticPointPath );
    TitleLegendPointState loaded = (TitleLegendPointState)((DrawingSemanticPointPath)loaded_path).specialState();
    assertEquals( 2, loaded.requestedColumns );
    assertEquals( 3, loaded.rowsPerColumn );
    assertEquals( 0.72f, loaded.legendScale, 0.001f );
    assertEquals( 7, loaded.rows.size() );
    TitleLegendLayout loaded_layout = (TitleLegendLayout)
      ((DrawingSemanticPointPath)loaded_path).preparedSpecialState();
    assertEquals( 3, loaded_layout.capacity.renderedColumns );
    RectF portrait = ((DrawingSemanticPointPath)loaded_path).exactSpecialBounds( false );
    RectF landscape = ((DrawingSemanticPointPath)loaded_path).exactSpecialBounds( true );
    assertEquals( portrait.width(), landscape.height(), 0.01f );
    assertEquals( portrait.height(), landscape.width(), 0.01f );
    assertTrue( ((DrawingSemanticPointPath)loaded_path).hitSpecialBounds(
      portrait.centerX(), portrait.centerY(), 0.0f, false ) );
    assertFalse( ((DrawingSemanticPointPath)loaded_path).hitSpecialBounds(
      portrait.left - 2.0f, portrait.centerY(), 0.0f, false ) );
  }

  @Test public void titleLegend_newerStateRemainsVisibleAndBytePreserved()
  {
    JSONObject json = new JSONObject();
    try {
      json.put( "envelope", SpecialPointEnvelope.ENVELOPE_VERSION );
      json.put( "behavior", TitleLegendPointBehavior.BEHAVIOR_ID );
      json.put( "stateVersion", 99 );
      json.put( "data", new JSONObject().put( "futureLegendField", "preserved" ) );
    } catch ( Exception e ) {
      throw new AssertionError( e );
    }
    String encoded = Base64.encodeToString( json.toString().getBytes( StandardCharsets.UTF_8 ),
      Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING );
    String options = SketchPrivateOptions.storeOption( "-id newer-legend",
      SketchPrivateOptions.OPTION_SPECIAL, encoded );
    int type = BrushManager.getPointIndexByThName( TitleLegendPointBehavior.THERION_NAME );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createLoaded(
      type, TitleLegendPointBehavior.THERION_NAME, 20.0f, 20.0f,
      PointScale.SCALE_M, null, options, 0 );
    assertTrue( point.specialState() instanceof TitleLegendPointBehavior.NewerState );
    assertEquals( encoded,
      SketchPrivateOptions.getOptionValue( point.mOptions, SketchPrivateOptions.OPTION_SPECIAL ) );
    RectF bounds = point.exactSpecialBounds( false );
    assertTrue( bounds.width() > 20.0f && bounds.height() > 20.0f );
    Bitmap bitmap = Bitmap.createBitmap( 180, 100, Bitmap.Config.ARGB_8888 );
    point.draw( new Canvas( bitmap ), new Matrix(), 1.0f, new RectF( 0, 0, 180, 100 ) );
    assertTrue( countOpaque( bitmap ) > 40 );
    bitmap.recycle();
  }

  @Test public void titleLegend_targetedInstallIsMissingOnlyAndNonOverwriting() throws Exception
  {
    File target = com.topodroid.util.TDFile.getPrivateFile( "point", TitleLegendPointBehavior.THERION_NAME );
    assertTrue( target.exists() );
    assertTrue( target.delete() );
    assertTrue( TopoDroidApp.installSinglePackagedSymbol( R.raw.symbols_topodroid_sketch,
      "point", TitleLegendPointBehavior.THERION_NAME ) );
    assertTrue( target.exists() );
    String installed = readUtf8( target );
    assertTrue( installed.contains( "th_name u:title-legend" ) );

    FileOutputStream output = new FileOutputStream( target, false );
    output.write( "user-owned".getBytes( StandardCharsets.UTF_8 ) );
    output.close();
    assertTrue( TopoDroidApp.installSinglePackagedSymbol( R.raw.symbols_topodroid_sketch,
      "point", TitleLegendPointBehavior.THERION_NAME ) );
    assertEquals( "user-owned", readUtf8( target ) );
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
      new double[] { -5, 0, 5, 10 }, "SURVEY_MAGNETIC", -8.75,
      BeddingMeasurementModel.DISTOX_CONSERVATIVE_V1, 0.002, 0.5, 0.5, 0.015, 0.25,
      "CAUTION", new String[] { "NO_REDUNDANCY", "VERTICAL_AZIMUTH_WEAK" },
      27.5, 32.5, 80.0, 100.0, false, "BOUNDED",
      25.0, 35.0, 75.0, 105.0, false, "BOUNDED",
      BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, true, 22.25, 18.5,
      Double.NaN, Double.NaN, false,
      BeddingAttitudePointState.PlanGlyphOverride.HORIZONTAL,
      16.0, 21.0, "POSITIVE_X", 14.0, 24.0, "UNRESOLVED",
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
    assertEquals( -8.75, loaded.declinationDegrees, 0.0 );
    assertEquals( BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, loaded.viewKind );
    assertEquals( 22.25, loaded.canvasTraceAngleDegrees, 1.0e-8 );
    assertEquals( 30.0, loaded.attitude().dipDegrees, 1.0e-8 );
    assertEquals( BeddingAttitudePointState.PlanGlyphOverride.HORIZONTAL,
      loaded.planGlyphOverride );
    assertEquals( 16.0, loaded.region68ApparentDipMinimum, 0.0 );
    assertEquals( "POSITIVE_X", loaded.region68FallStatus );
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

    BeddingAttitudePointBehavior behavior = new BeddingAttitudePointBehavior();
    JSONObject unknown_declination = behavior.encodeState( BeddingAttitudePointState.defaultState() );
    assertFalse( unknown_declination.has( "declinationDegrees" ) );
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

  @Test public void stationSectionGuide_usesFacingLrudMarginsAndIndependentHalves()
  {
    ArrayList< DBlock > splays = new ArrayList<>();
    splays.add( block( "A", "", 4.0f, 270.0f, 0.0f ) );
    StationLrudResult lrud = StationLrudCalculator.computeAtStation( 0.0f, 0.0f, splays, "A" );
    assertTrue( lrud.hasLeft );
    assertFalse( lrud.hasRight );

    TDSetting.mUnitLengthStr = "m";
    StationSectionGuide.HalfLengths metric = StationSectionGuide.initialLengths( lrud, false );
    assertEquals( 5.0f, metric.firstMetres, 0.001f );
    assertEquals( 5.0f, metric.lastMetres, 0.001f );

    TDSetting.mUnitLengthStr = "ft";
    StationSectionGuide.HalfLengths imperial = StationSectionGuide.initialLengths( lrud, false );
    assertEquals( 4.9144f, imperial.firstMetres, 0.001f );
    assertEquals( 4.9144f, imperial.lastMetres, 0.001f );

    DrawingLinePath guide = StationSectionGuide.create( 0, "xs-A", 100.0f, 120.0f,
      0.0f, -1.0f, 2.0f, 5.0f );
    assertTrue( StationSectionGuide.isGuide( guide ) );
    assertEquals( "1", SketchPrivateOptions.getOptionValue( guide.mOptions,
      SketchPrivateOptions.OPTION_STATION_GUIDE ) );
    assertNull( SketchPrivateOptions.getOptionValue( SketchPrivateOptions.stripAll( guide.mOptions ),
      SketchPrivateOptions.OPTION_STATION_GUIDE ) );

    DrawingLinePath legacy = new DrawingLinePath( BrushManager.getLineSectionIndex(), 0 );
    legacy.addOption( "-id xs-legacy" );
    legacy.addStartPoint( 0.0f, 0.0f );
    legacy.addPoint( 1.0f, 0.0f );
    legacy.addPoint( 2.0f, 0.0f );
    assertFalse( StationSectionGuide.isGuide( legacy ) );

    DrawingPointPath sectionPoint = new DrawingPointPath( BrushManager.getPointSectionIndex(),
      20.0f, 20.0f, PointScale.SCALE_M, null, "-scrap test-xs-A", 0 );
    sectionPoint.setLink( guide );
    assertFalse( sectionPoint.shouldDrawLink() );
    sectionPoint.setLink( legacy );
    assertTrue( sectionPoint.shouldDrawLink() );

    assertEquals( 3, guide.size() );
    assertEquals( 100.0f, StationSectionGuide.anchor( guide ).x, 0.001f );
    assertEquals( 120.0f, StationSectionGuide.anchor( guide ).y, 0.001f );
    assertEquals( 40.0f, guide.mFirst.distance( StationSectionGuide.anchor( guide ) ), 0.001f );
    assertEquals( 100.0f, guide.mLast.distance( StationSectionGuide.anchor( guide ) ), 0.001f );

    StationSectionGizmo.Drag resize = StationSectionGizmo.beginDrag(
      guide, StationSectionGizmo.FIRST, guide.mFirst.x, guide.mFirst.y, 1.0f );
    assertNotNull( resize );
    resize.update( 20.0f, 120.0f, false, true );
    assertEquals( 80.0f, guide.mFirst.distance( StationSectionGuide.anchor( guide ) ), 0.001f );
    assertEquals( 100.0f, guide.mLast.distance( StationSectionGuide.anchor( guide ) ), 0.001f );

    StationSectionGizmo.Drag rotate = StationSectionGizmo.beginDrag(
      guide, StationSectionGizmo.ROTATE, 100.0f, 80.0f, 1.0f );
    assertNotNull( rotate );
    rotate.update( 140.0f, 120.0f, false, true );
    assertEquals( 100.0f, StationSectionGuide.anchor( guide ).x, 0.001f );
    assertEquals( 120.0f, StationSectionGuide.anchor( guide ).y, 0.001f );
    assertEquals( 80.0f, guide.mFirst.distance( StationSectionGuide.anchor( guide ) ), 0.001f );
    assertEquals( 100.0f, guide.mLast.distance( StationSectionGuide.anchor( guide ) ), 0.001f );
  }

  @Test public void stationSectionGuide_usesUpDownForVerticalProfilesAndMarginFallback()
  {
    StationLrudResult lrud = new StationLrudResult();
    lrud.up = 3.0f;
    lrud.hasUp = true;
    TDSetting.mUnitLengthStr = "m";
    StationSectionGuide.HalfLengths lengths = StationSectionGuide.initialLengths( lrud, true );
    assertEquals( 4.0f, lengths.firstMetres, 0.001f );
    assertEquals( 4.0f, lengths.lastMetres, 0.001f );

    StationSectionGuide.HalfLengths empty = StationSectionGuide.initialLengths( new StationLrudResult(), true );
    assertEquals( 1.0f, empty.firstMetres, 0.001f );
    assertEquals( 1.0f, empty.lastMetres, 0.001f );
  }

  @Test public void stationSectionGuide_preservesAsymmetryAndRoundTripsAsOrdinaryLine() throws Exception
  {
    ArrayList< DBlock > splays = new ArrayList<>();
    splays.add( block( "A", "", 4.0f, 270.0f, 0.0f ) );
    splays.add( block( "A", "", 1.0f, 90.0f, 0.0f ) );
    StationLrudResult lrud = StationLrudCalculator.computeAtStation( 0.0f, 0.0f, splays, "A" );
    TDSetting.mUnitLengthStr = "m";
    StationSectionGuide.HalfLengths lengths = StationSectionGuide.initialLengths( lrud, false );
    assertEquals( 5.0f, lengths.firstMetres, 0.001f );
    assertEquals( 2.0f, lengths.lastMetres, 0.001f );

    DrawingLinePath guide = StationSectionGuide.create( 2, "xs-A", 10.0f, 20.0f,
      0.0f, -1.0f, lengths.firstMetres, lengths.lastMetres );
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream output = new DataOutputStream( bytes );
    guide.toDataStream( output, 2 );
    output.flush();
    DataInputStream input = new DataInputStream( new ByteArrayInputStream( bytes.toByteArray() ) );
    assertEquals( 'L', input.read() );
    DrawingLinePath loaded = DrawingLinePath.loadDataStream( TDR_VERSION, input, 0.0f, 0.0f );
    assertNotNull( loaded );
    assertTrue( StationSectionGuide.isGuide( loaded ) );
    assertEquals( "xs-A", loaded.getOption( "-id" ) );
    assertEquals( 10.0f, StationSectionGuide.anchor( loaded ).x, 0.001f );
    assertEquals( 20.0f, StationSectionGuide.anchor( loaded ).y, 0.001f );
    assertEquals( 100.0f, loaded.mFirst.distance( StationSectionGuide.anchor( loaded ) ), 0.001f );
    assertEquals( 40.0f, loaded.mLast.distance( StationSectionGuide.anchor( loaded ) ), 0.001f );
  }

  @Test public void beddingRenderer_distinguishesConfirmedGlyphsAndProfileFallDirections()
  {
    int type = BrushManager.getPointIndexByThName( BeddingAttitudePointBehavior.THERION_NAME );
    DrawingSemanticPointPath point = (DrawingSemanticPointPath)DrawingPointFactory.createPlacement(
      type, 200.0f, 200.0f, PointScale.SCALE_M, 0 );
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 90.0, 32.0 );
    BeddingAttitudePointState plan = BeddingAttitudePointState.manual( true, attitude, "A1",
      BeddingAttitudePointState.ViewKind.PLAN, false, Double.NaN, Double.NaN,
      Double.NaN, Double.NaN, false, -8.75, SketchTextStyle.defaultStyle(),
      BeddingAttitudePointState.MAX_TEXT_SCALE );
    Bitmap inclined = renderBedding( point, plan );
    Bitmap horizontal = renderBedding( point, plan.withPlanGlyphOverride(
      BeddingAttitudePointState.PlanGlyphOverride.HORIZONTAL ) );
    Bitmap vertical = renderBedding( point, plan.withPlanGlyphOverride(
      BeddingAttitudePointState.PlanGlyphOverride.VERTICAL ) );
    assertTrue( bitmapDifference( inclined, horizontal ) > 30 );
    assertTrue( bitmapDifference( horizontal, vertical ) > 20 );
    assertEquals( 32.0, plan.withPlanGlyphOverride(
      BeddingAttitudePointState.PlanGlyphOverride.HORIZONTAL ).attitude().dipDegrees, 1.0e-8 );

    BeddingAttitudePointState falling_right = BeddingAttitudePointState.manual( true, attitude, "A1",
      BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, true, 28.0, 28.0,
      Double.NaN, Double.NaN, false, -8.75, SketchTextStyle.defaultStyle(), 125 );
    BeddingAttitudePointState falling_left = BeddingAttitudePointState.manual( true, attitude, "A1",
      BeddingAttitudePointState.ViewKind.PROJECTED_PROFILE, true, -28.0, 28.0,
      Double.NaN, Double.NaN, false, -8.75, SketchTextStyle.defaultStyle(), 125 );
    Bitmap right = renderBedding( point, falling_right );
    Bitmap left = renderBedding( point, falling_left );
    assertTrue( bitmapDifference( right, left ) > 20 );

    point.setSpecialState( plan, false );
    RectF bounds = point.specialBehavior().renderer().sceneBounds( point );
    assertOpaqueInside( inclined, bounds );
    inclined.recycle();
    horizontal.recycle();
    vertical.recycle();
    right.recycle();
    left.recycle();
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

  private static Rect opaqueBounds( Bitmap bitmap )
  {
    int left = bitmap.getWidth();
    int top = bitmap.getHeight();
    int right = -1;
    int bottom = -1;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      if ( ( bitmap.getPixel( x, y ) >>> 24 ) != 0 ) {
        left = Math.min( left, x );
        top = Math.min( top, y );
        right = Math.max( right, x );
        bottom = Math.max( bottom, y );
      }
    }
    return right < left ? new Rect() : new Rect( left, top, right + 1, bottom + 1 );
  }

  private static void assertCenteredInk( Bitmap bitmap, float tolerance )
  {
    Rect bounds = opaqueBounds( bitmap );
    assertFalse( bounds.isEmpty() );
    assertEquals( 0.5f * bitmap.getWidth(), bounds.exactCenterX(), tolerance );
    assertEquals( 0.5f * bitmap.getHeight(), bounds.exactCenterY(), tolerance );
  }

  private static Bitmap renderBedding( DrawingSemanticPointPath point,
                                       BeddingAttitudePointState state )
  {
    point.setSpecialState( state, false );
    Bitmap bitmap = Bitmap.createBitmap( 400, 400, Bitmap.Config.ARGB_8888 );
    point.draw( new Canvas( bitmap ), new Matrix(), 1.0f, new RectF( 0, 0, 400, 400 ) );
    assertTrue( countOpaque( bitmap ) > 20 );
    return bitmap;
  }

  private static int bitmapDifference( Bitmap first, Bitmap second )
  {
    int count = 0;
    for ( int y = 0; y < first.getHeight(); ++y ) for ( int x = 0; x < first.getWidth(); ++x ) {
      if ( first.getPixel( x, y ) != second.getPixel( x, y ) ) ++count;
    }
    return count;
  }

  private static String readUtf8( File file ) throws Exception
  {
    FileInputStream input = new FileInputStream( file );
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int count;
    while ( ( count = input.read( buffer ) ) != -1 ) output.write( buffer, 0, count );
    input.close();
    return new String( output.toByteArray(), StandardCharsets.UTF_8 );
  }

  private static void assertOpaqueInside( Bitmap bitmap, RectF bounds )
  {
    for ( int y = 0; y < bitmap.getHeight(); ++y ) for ( int x = 0; x < bitmap.getWidth(); ++x ) {
      if ( ( bitmap.getPixel( x, y ) >>> 24 ) != 0 ) {
        assertTrue( "ink x=" + x + " bounds=" + bounds, x >= Math.floor( bounds.left )
          && x <= Math.ceil( bounds.right ) );
        assertTrue( "ink y=" + y + " bounds=" + bounds, y >= Math.floor( bounds.top )
          && y <= Math.ceil( bounds.bottom ) );
      }
    }
  }
}
