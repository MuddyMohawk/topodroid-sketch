/* @file BeddingAttitudePointEditorController.java
 *
 * @brief Manual and selected-splay editor for a bedding attitude point
 */
package com.topodroid.TDX;

import com.topodroid.geo.BeddingAttitude;
import com.topodroid.geo.BeddingFitResult;
import com.topodroid.geo.BeddingMeasurementModel;
import com.topodroid.geo.BeddingObservation;
import com.topodroid.geo.BeddingPlaneFitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Spinner;

final class BeddingAttitudePointEditorController implements SpecialPointEditorController
{
  private final DrawingWindow mParent;
  private final DrawingSemanticPointPath mPoint;
  private final BeddingAttitudePointState mOriginal;
  private final FramedTextTypographyEditor mTypography = new FramedTextTypographyEditor();
  private final ArrayList< CheckBox > mSplayChecks = new ArrayList<>();

  private BeddingSurveyContext mSurvey;
  private BeddingAttitudePointState mDraft;
  private View mRoot;
  private RadioButton mFitMode;
  private RadioButton mManualMode;
  private View mFitFields;
  private View mManualFields;
  private EditText mDipDirection;
  private EditText mDip;
  private TextView mDerivedStrike;
  private TextView mResult;
  private TextView mDiagnostics;
  private Button mCalculate;
  private Button mUseManual;
  private LinearLayout mSplayList;
  private Spinner mStation;
  private boolean mFitDirty;
  private boolean mCalculating;
  private int mCalculationGeneration;
  private long mTransientInfluentialId = -1L;
  private boolean mSourcesChanged;
  private boolean mProjectionChanged;

  BeddingAttitudePointEditorController( DrawingWindow parent, DrawingSemanticPointPath point )
  {
    mParent = parent;
    mPoint = point;
    mOriginal = point.specialState() instanceof BeddingAttitudePointState
      ? (BeddingAttitudePointState)point.specialState() : BeddingAttitudePointState.defaultState();
    mDraft = mOriginal;
  }

  @Override public void bind( LinearLayout container, EditText primary_text )
  {
    if ( container == null || primary_text == null ) return;
    primary_text.setVisibility( View.GONE );
    mRoot = LayoutInflater.from( mParent ).inflate(
      R.layout.drawing_bedding_attitude_editor, container, false );
    container.addView( mRoot );

    mFitMode = (RadioButton)mRoot.findViewById( R.id.bedding_mode_fit );
    mManualMode = (RadioButton)mRoot.findViewById( R.id.bedding_mode_manual );
    mFitFields = mRoot.findViewById( R.id.bedding_fit_fields );
    mManualFields = mRoot.findViewById( R.id.bedding_manual_fields );
    mDipDirection = (EditText)mRoot.findViewById( R.id.bedding_dip_direction );
    mDip = (EditText)mRoot.findViewById( R.id.bedding_dip );
    mDerivedStrike = (TextView)mRoot.findViewById( R.id.bedding_derived_strike );
    mResult = (TextView)mRoot.findViewById( R.id.bedding_result );
    mDiagnostics = (TextView)mRoot.findViewById( R.id.bedding_diagnostics );
    mCalculate = (Button)mRoot.findViewById( R.id.bedding_calculate );
    mUseManual = (Button)mRoot.findViewById( R.id.bedding_use_manual );
    mStation = (Spinner)mRoot.findViewById( R.id.bedding_station );
    mSplayList = (LinearLayout)mRoot.findViewById( R.id.bedding_splay_list );

    mTypography.bind( mParent, mRoot, mOriginal,
      BeddingAttitudePointState.MIN_TEXT_SCALE, BeddingAttitudePointState.MAX_TEXT_SCALE );
    BeddingAttitude attitude = mOriginal.attitude();
    if ( attitude != null ) {
      if ( attitude.kind != BeddingAttitude.Kind.HORIZONTAL ) {
        double direction = BeddingAttitude.wrap360( Math.toDegrees(
          Math.atan2( attitude.unitNormal.east, attitude.unitNormal.north ) ) );
        mDipDirection.setText( formatInput( direction ) );
      }
      mDip.setText( formatInput( attitude.dipDegrees ) );
    }

    mSurvey = mOriginal.stationName.length() > 0
      ? mParent.computeBeddingSurveyAtStation( mOriginal.stationName )
      : mParent.computeNearestBeddingSurvey( mPoint.cx, mPoint.cy );
    mSourcesChanged = sourceSignaturesChanged( mOriginal, mSurvey );
    mProjectionChanged = projectionChanged( mOriginal,
      mParent.computeBeddingProjection( mOriginal.attitude(), mSurvey.stationName ) );
    populateSplays( mSplayList );
    bindStations();

    boolean has_three = eligibleCount() >= 3;
    boolean fit_mode = mOriginal.mode == BeddingAttitudePointState.Mode.FIT
      || ( ! mOriginal.configured && has_three );
    mFitMode.setChecked( fit_mode );
    mManualMode.setChecked( ! fit_mode );
    mFitDirty = fit_mode && mOriginal.mode != BeddingAttitudePointState.Mode.FIT;

    mFitMode.setOnClickListener( view -> {
      if ( mDraft == null || mDraft.mode != BeddingAttitudePointState.Mode.FIT ) mFitDirty = true;
      showMode( true );
    } );
    mManualMode.setOnClickListener( view -> showMode( false ) );
    mCalculate.setOnClickListener( view -> calculateFit() );
    mUseManual.setEnabled( mOriginal.mode == BeddingAttitudePointState.Mode.FIT && mOriginal.configured );
    mUseManual.setOnClickListener( view -> useFittedValuesManually() );
    TextWatcher manual_watcher = new TextWatcher() {
      @Override public void beforeTextChanged( CharSequence value, int start, int count, int after ) { }
      @Override public void onTextChanged( CharSequence value, int start, int before, int count )
      {
        updateManualPreview();
      }
      @Override public void afterTextChanged( Editable value ) { }
    };
    mDipDirection.addTextChangedListener( manual_watcher );
    mDip.addTextChangedListener( manual_watcher );
    showMode( fit_mode );
    if ( fit_mode && mOriginal.mode == BeddingAttitudePointState.Mode.FIT ) {
      showState( mOriginal );
    } else if ( fit_mode && has_three ) {
      mRoot.post( this::calculateFit );
    } else {
      updateManualPreview();
    }
  }

  @Override public boolean canApply()
  {
    if ( mRoot == null || ! mTypography.isBound() ) return false;
    if ( mFitMode.isChecked() ) {
      if ( mCalculating ) {
        mDiagnostics.setText( R.string.bedding_calculating );
        return false;
      }
      if ( mFitDirty || mDraft == null || mDraft.mode != BeddingAttitudePointState.Mode.FIT
          || ! mDraft.configured ) {
        mDiagnostics.setText( R.string.bedding_recalculate );
        return false;
      }
      mDraft = mDraft.withStationAndProjection( mSurvey.stationName,
        mParent.computeBeddingProjection( mDraft.attitude(), mSurvey.stationName ) );
      mProjectionChanged = false;
      return true;
    }
    Double dip = parseNumber( mDip );
    if ( dip == null || dip < 0.0 || dip > 90.0 ) {
      mDip.setError( mParent.getString( R.string.bedding_invalid_dip ) );
      return false;
    }
    Double direction = parseNumber( mDipDirection );
    if ( dip > 1.0e-8 && ( direction == null || direction < 0.0 || direction >= 360.0 ) ) {
      mDipDirection.setError( mParent.getString( R.string.bedding_invalid_direction ) );
      return false;
    }
    if ( direction == null ) direction = 0.0;
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
    if ( attitude == null ) return false;
    BeddingProjection projection = mParent.computeBeddingProjection( attitude, mSurvey.stationName );
    SketchTextStyle style = typographyStyle();
    mDraft = BeddingAttitudePointState.manual( true, attitude, mSurvey.stationName,
      projection.viewKind, projection.traceValid, projection.canvasTraceAngleDegrees,
      projection.apparentDipDegrees, projection.extendedReferenceBearingDegrees,
      projection.extendedExtendSign, projection.extendedReferenceAmbiguous,
      mParent.beddingDeclinationSnapshot(), style, mTypography.textScalePercent() );
    return true;
  }

  @Override public void apply()
  {
    if ( mDraft == null || ! mTypography.isBound() ) return;
    mDraft = mDraft.withTypography( mTypography.fontId(), mTypography.bold(),
      mTypography.italic(), mTypography.underline(), mTypography.textScalePercent() );
    BeddingAttitude attitude = mDraft.attitude();
    mPoint.setPointText( Integer.toString( (int)Math.round( attitude.dipDegrees ) ) );
    mPoint.setOrientation( Double.isFinite( attitude.strikeRhrDegrees )
      ? attitude.strikeRhrDegrees : 0.0 );
    mPoint.setSpecialState( mDraft, true );
    mTypography.rememberAsTextDefault( mParent );
  }

  @Override public void cancel()
  {
    if ( ! mOriginal.configured ) mParent.cancelUnconfiguredBeddingPoint( mPoint );
  }

  private void populateSplays( LinearLayout list )
  {
    list.removeAllViews();
    mSplayChecks.clear();
    for ( BeddingSurveyContext.Splay splay : mSurvey.splays ) {
      CheckBox check = new CheckBox( mParent );
      check.setTag( splay );
      check.setText( splay.displayLabel()
        + ( splay.eligible ? "" : " (excluded: " + splay.exclusionReason + ")" ) );
      check.setEnabled( splay.eligible );
      boolean selected = splay.eligible && ( mOriginal.mode != BeddingAttitudePointState.Mode.FIT
        || ! mSurvey.stationName.equals( mOriginal.stationName )
        || contains( mOriginal.sourceShotIds, splay.id ) );
      check.setChecked( selected );
      check.setOnClickListener( view -> {
        ++mCalculationGeneration;
        mFitDirty = true;
        mTransientInfluentialId = -1L;
        mUseManual.setEnabled( false );
        clearSplayAnnotations();
        if ( mFitMode.isChecked() ) mDiagnostics.setText( R.string.bedding_recalculate );
      } );
      list.addView( check );
      mSplayChecks.add( check );
    }
  }

  private void bindStations()
  {
    List< String > names = mParent.beddingStationNames();
    ArrayList< String > display = new ArrayList<>( names );
    if ( display.isEmpty() ) display.add( mParent.getString( R.string.bedding_no_station ) );
    ArrayAdapter< String > adapter = new ArrayAdapter<>( mParent,
      android.R.layout.simple_spinner_item, display );
    adapter.setDropDownViewResource( android.R.layout.simple_spinner_dropdown_item );
    mStation.setAdapter( adapter );
    mStation.setEnabled( ! names.isEmpty() );
    int selected = names.indexOf( mSurvey.stationName );
    if ( selected >= 0 ) mStation.setSelection( selected );
    mStation.setOnItemSelectedListener( new AdapterView.OnItemSelectedListener() {
      @Override public void onItemSelected( AdapterView< ? > parent, View view, int position, long id )
      {
        if ( position < 0 || position >= names.size() ) return;
        String station = names.get( position );
        if ( station.equals( mSurvey.stationName ) ) return;
        ++mCalculationGeneration;
        mSurvey = mParent.computeBeddingSurveyAtStation( station );
        mSourcesChanged = mOriginal.mode == BeddingAttitudePointState.Mode.FIT;
        populateSplays( mSplayList );
        mFitDirty = true;
        mTransientInfluentialId = -1L;
        mUseManual.setEnabled( false );
        if ( mFitMode.isChecked() ) mDiagnostics.setText( R.string.bedding_recalculate );
        else updateManualPreview();
      }
      @Override public void onNothingSelected( AdapterView< ? > parent ) { }
    } );
  }

  private void showMode( boolean fit )
  {
    mFitFields.setVisibility( fit ? View.VISIBLE : View.GONE );
    mManualFields.setVisibility( fit ? View.GONE : View.VISIBLE );
    if ( fit ) {
      if ( mCalculating ) mDiagnostics.setText( R.string.bedding_calculating );
      else if ( mFitDirty ) mDiagnostics.setText( R.string.bedding_recalculate );
      else showState( mDraft );
    } else {
      updateManualPreview();
      mDiagnostics.setText( R.string.bedding_manual_diagnostics );
    }
  }

  private void calculateFit()
  {
    if ( mCalculating ) return;
    ArrayList< BeddingObservation > observations = new ArrayList<>();
    ArrayList< Long > ids = new ArrayList<>();
    ArrayList< Double > lengths = new ArrayList<>();
    ArrayList< Double > bearings = new ArrayList<>();
    ArrayList< Double > clinos = new ArrayList<>();
    boolean has_manual = false;
    for ( CheckBox check : mSplayChecks ) {
      if ( check.isEnabled() && check.isChecked()
          && ((BeddingSurveyContext.Splay)check.getTag()).manualSource ) has_manual = true;
    }
    BeddingMeasurementModel model = has_manual
      ? BeddingMeasurementModel.manualConservativeV1()
      : BeddingMeasurementModel.distoxConservativeV1();
    for ( CheckBox check : mSplayChecks ) {
      if ( ! check.isEnabled() || ! check.isChecked() ) continue;
      BeddingSurveyContext.Splay splay = (BeddingSurveyContext.Splay)check.getTag();
      BeddingObservation observation = BeddingObservation.fromSplay( splay.id,
        splay.displayLabel(), splay.type, splay.lengthMeters, splay.bearingDegrees,
        splay.clinoDegrees, model );
      if ( observation != null ) {
        observations.add( observation );
        ids.add( splay.id );
        lengths.add( splay.lengthMeters );
        bearings.add( splay.bearingDegrees );
        clinos.add( splay.clinoDegrees );
      }
    }
    if ( observations.size() < 3 ) {
      mDiagnostics.setText( R.string.bedding_need_three );
      return;
    }

    final int generation = ++mCalculationGeneration;
    final long[] source_ids = new long[ ids.size() ];
    for ( int i = 0; i < ids.size(); ++i ) source_ids[i] = ids.get( i );
    final double[] source_lengths = doubles( lengths );
    final double[] source_bearings = doubles( bearings );
    final double[] source_clinos = doubles( clinos );
    mCalculating = true;
    mCalculate.setEnabled( false );
    mCalculate.setText( R.string.bedding_calculating );
    mDiagnostics.setText( R.string.bedding_calculating );
    new Thread( () -> {
      BeddingFitResult fit = BeddingPlaneFitter.fit( observations, model );
      mRoot.post( () -> finishCalculation( generation, source_ids, source_lengths,
        source_bearings, source_clinos, model, fit ) );
    }, "bedding-plane-fit" ).start();
  }

  private void finishCalculation( int generation, long[] source_ids, double[] source_lengths,
                                  double[] source_bearings, double[] source_clinos,
                                  BeddingMeasurementModel model, BeddingFitResult fit )
  {
    mCalculating = false;
    mCalculate.setEnabled( true );
    mCalculate.setText( R.string.bedding_calculate );
    if ( generation != mCalculationGeneration ) {
      mFitDirty = true;
      mDiagnostics.setText( R.string.bedding_recalculate );
      return;
    }
    if ( fit == null || fit.status != BeddingFitResult.Status.VALID || fit.attitude == null ) {
      mFitDirty = true;
      mDiagnostics.setText( mParent.getString( R.string.bedding_fit_failed,
        fit == null ? "numerical failure" : issueSummary( fit.issues ) ) );
      return;
    }
    BeddingProjection projection = mParent.computeBeddingProjection( fit.attitude, mSurvey.stationName );
    mDraft = BeddingAttitudePointState.fitted( fit, mSurvey.stationName, source_ids,
      source_lengths, source_bearings, source_clinos, model,
      mParent.beddingDeclinationSnapshot(),
      projection.viewKind, projection.traceValid, projection.canvasTraceAngleDegrees,
      projection.apparentDipDegrees, projection.extendedReferenceBearingDegrees,
      projection.extendedExtendSign, projection.extendedReferenceAmbiguous,
      mTypography.fontId(), mTypography.bold(), mTypography.italic(), mTypography.underline(),
      mTypography.textScalePercent() );
    mTransientInfluentialId = fit.influentialSourceId;
    mSourcesChanged = false;
    mUseManual.setEnabled( true );
    annotateSplays( fit );
    mFitDirty = false;
    showState( mDraft );
  }

  private void updateManualPreview()
  {
    if ( mResult == null ) return;
    Double dip = parseNumber( mDip );
    Double direction = parseNumber( mDipDirection );
    if ( dip == null || dip < 0.0 || dip > 90.0 || ( dip > 1.0e-8
        && ( direction == null || direction < 0.0 || direction >= 360.0 ) ) ) {
      mDerivedStrike.setText( "" );
      return;
    }
    if ( direction == null ) direction = 0.0;
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
    if ( attitude == null ) return;
    mDerivedStrike.setText( Double.isFinite( attitude.strikeRhrDegrees )
      ? mParent.getString( R.string.bedding_strike_rhr, attitude.strikeRhrDegrees ) : "" );
    mResult.setText( formatAttitude( attitude ) );
  }

  private void useFittedValuesManually()
  {
    if ( mDraft == null || mDraft.mode != BeddingAttitudePointState.Mode.FIT ) return;
    BeddingAttitude attitude = mDraft.attitude();
    if ( attitude == null ) return;
    if ( attitude.kind == BeddingAttitude.Kind.HORIZONTAL ) {
      mDipDirection.setText( "" );
    } else {
      double direction = BeddingAttitude.wrap360( Math.toDegrees(
        Math.atan2( attitude.unitNormal.east, attitude.unitNormal.north ) ) );
      mDipDirection.setText( formatInput( direction ) );
    }
    mDip.setText( formatInput( attitude.dipDegrees ) );
    mManualMode.setChecked( true );
    showMode( false );
  }

  private void showState( BeddingAttitudePointState state )
  {
    if ( state == null || state.attitude() == null ) return;
    mResult.setText( formatAttitude( state.attitude() ) );
    if ( state.mode != BeddingAttitudePointState.Mode.FIT ) return;
    String warning = state.fitIssues.length == 0 ? "" : " · " + issueSummary( state.fitIssues );
    if ( mTransientInfluentialId >= 0 ) warning += " (shot #" + mTransientInfluentialId + ")";
    if ( state.sourceShotIds.length < 6 ) warning += mParent.getString( R.string.bedding_few_splays );
    String diagnostics;
    if ( Double.isFinite( state.region68DipMinimum ) && Double.isFinite( state.region68DipMaximum ) ) {
      diagnostics = mParent.getString( R.string.bedding_fit_diagnostics,
        state.fitQuality, state.sourceShotIds.length, state.region68DipMinimum,
        state.region68DipMaximum, warning );
    } else {
      diagnostics = state.fitQuality + " · " + state.sourceShotIds.length + " splays" + warning;
    }
    if ( "BOUNDED".equals( state.region68Status )
        && Double.isFinite( state.region68DirectionStart )
        && Double.isFinite( state.region68DirectionEnd ) ) {
      diagnostics += mParent.getString( R.string.bedding_direction_region,
        state.region68DirectionStart, state.region68DirectionEnd,
        state.region68DirectionWrapsNorth
          ? mParent.getString( R.string.bedding_direction_wraps ) : "" );
    } else if ( state.region68Status.length() > 0 && ! "UNAVAILABLE".equals( state.region68Status ) ) {
      diagnostics += mParent.getString( R.string.bedding_direction_status,
        state.region68Status.toLowerCase( Locale.US ).replace( '_', ' ' ) );
    }
    if ( state.viewKind != BeddingAttitudePointState.ViewKind.PLAN ) {
      if ( state.traceValid && Double.isFinite( state.apparentDipDegrees ) ) {
        String fall = Math.sin( Math.toRadians( state.canvasTraceAngleDegrees ) ) >= 0.0
          ? mParent.getString( R.string.bedding_falling_right )
          : mParent.getString( R.string.bedding_falling_left );
        diagnostics += mParent.getString( R.string.bedding_profile_apparent,
          state.apparentDipDegrees, fall );
      } else {
        diagnostics += mParent.getString( R.string.bedding_profile_unresolved );
      }
    }
    if ( state.extendedReferenceAmbiguous ) {
      diagnostics += mParent.getString( R.string.bedding_extended_ambiguous );
    }
    if ( mSourcesChanged ) diagnostics += mParent.getString( R.string.bedding_sources_changed );
    if ( mProjectionChanged ) diagnostics += mParent.getString( R.string.bedding_projection_changed );
    if ( state.measurementModelId.length() > 0 && Double.isFinite( state.sigmaDistanceMeters ) ) {
      diagnostics += mParent.getString( R.string.bedding_measurement_model,
        state.measurementModelId, state.sigmaDistanceMeters, state.sigmaBearingDegrees,
        state.sigmaClinoDegrees, state.surfaceScatterMeters );
    }
    mDiagnostics.setText( diagnostics );
  }

  private void annotateSplays( BeddingFitResult fit )
  {
    if ( fit == null ) return;
    int result_index = 0;
    for ( CheckBox check : mSplayChecks ) {
      BeddingSurveyContext.Splay splay = (BeddingSurveyContext.Splay)check.getTag();
      String suffix = splay.eligible ? "" : " (excluded: " + splay.exclusionReason + ")";
      if ( check.isEnabled() && check.isChecked() && result_index < fit.normalizedResiduals.length ) {
        suffix = String.format( Locale.US, "  [residual %+.1fσ%s]",
          fit.normalizedResiduals[result_index],
          splay.id == fit.influentialSourceId ? ", influential" : "" );
        ++result_index;
      }
      check.setText( splay.displayLabel() + suffix );
    }
  }

  private void clearSplayAnnotations()
  {
    for ( CheckBox check : mSplayChecks ) {
      BeddingSurveyContext.Splay splay = (BeddingSurveyContext.Splay)check.getTag();
      check.setText( splay.displayLabel()
        + ( splay.eligible ? "" : " (excluded: " + splay.exclusionReason + ")" ) );
    }
  }

  private String formatAttitude( BeddingAttitude attitude )
  {
    if ( attitude.kind == BeddingAttitude.Kind.HORIZONTAL ) {
      return mParent.getString( R.string.bedding_horizontal_result );
    }
    if ( attitude.kind == BeddingAttitude.Kind.VERTICAL ) {
      return mParent.getString( R.string.bedding_vertical_result, attitude.strikeRhrDegrees );
    }
    return mParent.getString( R.string.bedding_fit_result, attitude.strikeRhrDegrees,
      attitude.dipDegrees, attitude.dipDirectionDegrees );
  }

  private SketchTextStyle typographyStyle()
  {
    return SketchTextStyle.of( mTypography.fontId(), SketchTextStyle.SizeMode.AUTO_GRID, 1.0f,
      mTypography.bold(), mTypography.italic(), mTypography.underline(),
      SketchTextStyle.Alignment.CENTER, SketchTextStyle.DEFAULT_COLOR );
  }

  private int eligibleCount()
  {
    int count = 0;
    for ( BeddingSurveyContext.Splay splay : mSurvey.splays ) if ( splay.eligible ) ++count;
    return count;
  }

  private static boolean contains( long[] values, long sought )
  {
    if ( values != null ) for ( long value : values ) if ( value == sought ) return true;
    return false;
  }

  private static Double parseNumber( EditText field )
  {
    if ( field == null ) return null;
    String value = field.getText().toString().trim().replace( ',', '.' );
    if ( value.length() == 0 ) return null;
    try {
      double parsed = Double.parseDouble( value );
      return Double.isFinite( parsed ) ? parsed : null;
    } catch ( NumberFormatException e ) {
      return null;
    }
  }

  private static String formatInput( double value )
  {
    if ( Math.abs( value - Math.rint( value ) ) < 1.0e-6 ) {
      return Long.toString( Math.round( value ) );
    }
    return String.format( Locale.US, "%.1f", value );
  }

  private static String issueSummary( Iterable< BeddingFitResult.Issue > issues )
  {
    ArrayList< String > names = new ArrayList<>();
    if ( issues != null ) for ( BeddingFitResult.Issue issue : issues ) {
      names.add( issue.name().toLowerCase( Locale.US ).replace( '_', ' ' ) );
    }
    return join( names );
  }

  private static String issueSummary( String[] issues )
  {
    ArrayList< String > names = new ArrayList<>();
    if ( issues != null ) for ( String issue : issues ) {
      names.add( issue.toLowerCase( Locale.US ).replace( '_', ' ' ) );
    }
    return join( names );
  }

  private static String join( List< String > names )
  {
    if ( names == null || names.isEmpty() ) return "none";
    StringBuilder text = new StringBuilder();
    for ( String name : names ) {
      if ( text.length() > 0 ) text.append( ", " );
      text.append( name );
    }
    return text.toString();
  }

  private static double[] doubles( List< Double > values )
  {
    double[] result = new double[ values.size() ];
    for ( int i = 0; i < result.length; ++i ) result[i] = values.get( i );
    return result;
  }

  private static boolean sourceSignaturesChanged( BeddingAttitudePointState state,
                                                  BeddingSurveyContext survey )
  {
    if ( state == null || state.mode != BeddingAttitudePointState.Mode.FIT ) return false;
    int count = state.sourceShotIds.length;
    if ( state.sourceLengthsMeters.length != count || state.sourceBearingsDegrees.length != count
        || state.sourceClinosDegrees.length != count ) return true;
    for ( int i = 0; i < count; ++i ) {
      BeddingSurveyContext.Splay match = null;
      for ( BeddingSurveyContext.Splay splay : survey.splays ) {
        if ( splay.id == state.sourceShotIds[i] ) { match = splay; break; }
      }
      if ( match == null || Math.abs( match.lengthMeters - state.sourceLengthsMeters[i] ) > 1.0e-6
          || angularDifference( match.bearingDegrees, state.sourceBearingsDegrees[i] ) > 1.0e-6
          || Math.abs( match.clinoDegrees - state.sourceClinosDegrees[i] ) > 1.0e-6 ) return true;
    }
    return false;
  }

  private static double angularDifference( double first, double second )
  {
    double difference = Math.abs( BeddingAttitude.wrap360( first - second ) );
    return Math.min( difference, 360.0 - difference );
  }

  private static boolean projectionChanged( BeddingAttitudePointState state,
                                            BeddingProjection projection )
  {
    if ( state == null || projection == null || state.viewKind != projection.viewKind
        || state.traceValid != projection.traceValid ) return true;
    if ( state.viewKind == BeddingAttitudePointState.ViewKind.PLAN ) return false;
    if ( state.traceValid && ( angularDifference( state.canvasTraceAngleDegrees,
          projection.canvasTraceAngleDegrees ) > 1.0e-6
        || Math.abs( state.apparentDipDegrees - projection.apparentDipDegrees ) > 1.0e-6 ) ) return true;
    if ( state.viewKind == BeddingAttitudePointState.ViewKind.EXTENDED_PROFILE ) {
      return angularDifference( state.extendedReferenceBearingDegrees,
          projection.extendedReferenceBearingDegrees ) > 1.0e-6
        || Math.abs( state.extendedExtendSign - projection.extendedExtendSign ) > 1.0e-6
        || state.extendedReferenceAmbiguous != projection.extendedReferenceAmbiguous;
    }
    return false;
  }
}
