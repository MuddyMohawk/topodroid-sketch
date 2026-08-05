/* @file BeddingAttitudePointState.java
 *
 * @brief Immutable geological and presentation state for a bedding attitude point
 */
package com.topodroid.TDX;

import com.topodroid.geo.BeddingAttitude;
import com.topodroid.geo.BeddingFitResult;
import com.topodroid.geo.BeddingMeasurementModel;
import com.topodroid.geo.GeoVector3;

import java.util.Set;

final class BeddingAttitudePointState implements FramedTextPointState
{
  enum Mode { MANUAL, FIT }
  enum ViewKind { PLAN, PROJECTED_PROFILE, EXTENDED_PROFILE, UNSUPPORTED }
  enum PlanGlyphOverride { AUTO, HORIZONTAL, VERTICAL }

  static final int MIN_TEXT_SCALE = FramedTextPointState.MIN_TEXT_SCALE;
  static final int MAX_TEXT_SCALE = FramedTextPointState.MAX_TEXT_SCALE;
  static final int DEFAULT_TEXT_SCALE = 125;

  final boolean configured;
  final Mode mode;
  final double normalEast;
  final double normalNorth;
  final double normalUp;
  final String stationName;
  final long[] sourceShotIds;
  final double[] sourceLengthsMeters;
  final double[] sourceBearingsDegrees;
  final double[] sourceClinosDegrees;
  final String azimuthReference;
  final double declinationDegrees;
  final String measurementModelId;
  final double sigmaDistanceMeters;
  final double sigmaBearingDegrees;
  final double sigmaClinoDegrees;
  final double surfaceScatterMeters;
  final double bearingCosineFloor;
  final String fitQuality;
  final String[] fitIssues;
  final double region68DipMinimum;
  final double region68DipMaximum;
  final double region68DirectionStart;
  final double region68DirectionEnd;
  final boolean region68DirectionWrapsNorth;
  final String region68Status;
  final double region95DipMinimum;
  final double region95DipMaximum;
  final double region95DirectionStart;
  final double region95DirectionEnd;
  final boolean region95DirectionWrapsNorth;
  final String region95Status;
  final ViewKind viewKind;
  final boolean traceValid;
  final double canvasTraceAngleDegrees;
  final double apparentDipDegrees;
  final double extendedReferenceBearingDegrees;
  final double extendedExtendSign;
  final boolean extendedReferenceAmbiguous;
  final PlanGlyphOverride planGlyphOverride;
  final double region68ApparentDipMinimum;
  final double region68ApparentDipMaximum;
  final String region68FallStatus;
  final double region95ApparentDipMinimum;
  final double region95ApparentDipMaximum;
  final String region95FallStatus;

  private final String mFontId;
  private final boolean mBold;
  private final boolean mItalic;
  private final boolean mUnderline;
  private final int mTextScalePercent;

  BeddingAttitudePointState( boolean is_configured, Mode source_mode,
                             double normal_east, double normal_north, double normal_up,
                             String station, long[] shot_ids, double[] source_lengths,
                             double[] source_bearings, double[] source_clinos,
                             String azimuth_reference,
                             double declination_degrees, String model_id,
                             double sigma_distance, double sigma_bearing_degrees,
                             double sigma_clino_degrees, double surface_scatter,
                             double bearing_cosine_floor,
                             String quality, String[] issues,
                             double region68_dip_minimum, double region68_dip_maximum,
                             double region68_direction_start, double region68_direction_end,
                             boolean region68_wraps, String region68_status,
                             double region95_dip_minimum, double region95_dip_maximum,
                             double region95_direction_start, double region95_direction_end,
                             boolean region95_wraps, String region95_status,
                             ViewKind view_kind, boolean trace_valid,
                             double canvas_trace_angle, double apparent_dip,
                             double extended_reference_bearing, double extended_extend_sign,
                             boolean extended_reference_ambiguous,
                             PlanGlyphOverride plan_glyph_override,
                             double region68_apparent_dip_minimum,
                             double region68_apparent_dip_maximum,
                             String region68_fall_status,
                             double region95_apparent_dip_minimum,
                             double region95_apparent_dip_maximum,
                             String region95_fall_status,
                             String font_id, boolean bold, boolean italic, boolean underline,
                             int text_scale_percent )
  {
    configured = is_configured;
    mode = source_mode == null ? Mode.MANUAL : source_mode;
    GeoVector3 normal = new GeoVector3( normal_east, normal_north, normal_up ).normalized();
    if ( normal == null ) normal = new GeoVector3( 0.0, 0.0, 1.0 );
    if ( normal.up < 0.0 ) normal = normal.times( -1.0 );
    normalEast = normal.east;
    normalNorth = normal.north;
    normalUp = normal.up;
    stationName = station == null ? "" : station;
    sourceShotIds = shot_ids == null ? new long[0] : shot_ids.clone();
    sourceLengthsMeters = source_lengths == null ? new double[0] : source_lengths.clone();
    sourceBearingsDegrees = source_bearings == null ? new double[0] : source_bearings.clone();
    sourceClinosDegrees = source_clinos == null ? new double[0] : source_clinos.clone();
    azimuthReference = azimuth_reference == null || azimuth_reference.length() == 0
      ? "SURVEY_MAGNETIC" : azimuth_reference;
    declinationDegrees = Double.isFinite( declination_degrees ) ? declination_degrees : Double.NaN;
    measurementModelId = model_id == null ? "" : model_id;
    sigmaDistanceMeters = sigma_distance;
    sigmaBearingDegrees = sigma_bearing_degrees;
    sigmaClinoDegrees = sigma_clino_degrees;
    surfaceScatterMeters = surface_scatter;
    bearingCosineFloor = bearing_cosine_floor;
    fitQuality = quality == null ? "" : quality;
    fitIssues = issues == null ? new String[0] : issues.clone();
    region68DipMinimum = region68_dip_minimum;
    region68DipMaximum = region68_dip_maximum;
    region68DirectionStart = region68_direction_start;
    region68DirectionEnd = region68_direction_end;
    region68DirectionWrapsNorth = region68_wraps;
    region68Status = region68_status == null ? "" : region68_status;
    region95DipMinimum = region95_dip_minimum;
    region95DipMaximum = region95_dip_maximum;
    region95DirectionStart = region95_direction_start;
    region95DirectionEnd = region95_direction_end;
    region95DirectionWrapsNorth = region95_wraps;
    region95Status = region95_status == null ? "" : region95_status;
    viewKind = view_kind == null ? ViewKind.UNSUPPORTED : view_kind;
    traceValid = trace_valid;
    canvasTraceAngleDegrees = canvas_trace_angle;
    apparentDipDegrees = apparent_dip;
    extendedReferenceBearingDegrees = extended_reference_bearing;
    extendedExtendSign = extended_extend_sign;
    extendedReferenceAmbiguous = extended_reference_ambiguous;
    planGlyphOverride = plan_glyph_override == null ? PlanGlyphOverride.AUTO : plan_glyph_override;
    region68ApparentDipMinimum = region68_apparent_dip_minimum;
    region68ApparentDipMaximum = region68_apparent_dip_maximum;
    region68FallStatus = region68_fall_status == null ? "UNAVAILABLE" : region68_fall_status;
    region95ApparentDipMinimum = region95_apparent_dip_minimum;
    region95ApparentDipMaximum = region95_apparent_dip_maximum;
    region95FallStatus = region95_fall_status == null ? "UNAVAILABLE" : region95_fall_status;
    mFontId = SketchFontRegistry.normalizeFontId( font_id );
    mBold = bold;
    mItalic = italic;
    mUnderline = underline;
    mTextScalePercent = Math.max( MIN_TEXT_SCALE, Math.min( MAX_TEXT_SCALE, text_scale_percent ) );
  }

  static BeddingAttitudePointState defaultState()
  {
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( 90.0, 60.0 );
    return manual( false, attitude, "", ViewKind.PLAN, false, Double.NaN, Double.NaN,
      Double.NaN, Double.NaN, false, Double.NaN, SketchTextStyle.defaultStyle(), DEFAULT_TEXT_SCALE );
  }

  static BeddingAttitudePointState manual( boolean configured, BeddingAttitude attitude,
                                           String station, ViewKind view_kind,
                                           boolean trace_valid, double trace_angle, double apparent_dip,
                                           double extended_bearing, double extended_sign,
                                           boolean extended_ambiguous, double declination_degrees,
                                           SketchTextStyle style,
                                           int text_scale )
  {
    GeoVector3 normal = attitude == null ? new GeoVector3( 0, 0, 1 ) : attitude.unitNormal;
    SketchTextStyle typography = style == null ? SketchTextStyle.defaultStyle() : style;
    return new BeddingAttitudePointState( configured, Mode.MANUAL,
      normal.east, normal.north, normal.up, station, null, null, null, null, "SURVEY_MAGNETIC",
      declination_degrees, "", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
      "", null,
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false, "UNAVAILABLE",
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false, "UNAVAILABLE",
      view_kind, trace_valid,
      trace_angle, apparent_dip, extended_bearing, extended_sign, extended_ambiguous,
      PlanGlyphOverride.AUTO,
      Double.NaN, Double.NaN, "UNAVAILABLE", Double.NaN, Double.NaN, "UNAVAILABLE",
      typography.fontId(), typography.bold(), typography.italic(), typography.underline(), text_scale );
  }

  static BeddingAttitudePointState fitted( BeddingFitResult fit, String station, long[] shot_ids,
                                           double[] source_lengths, double[] source_bearings,
                                           double[] source_clinos,
                                           BeddingMeasurementModel model, double declination_degrees,
                                           BeddingProjection projection, String font_id,
                                           boolean bold, boolean italic, boolean underline,
                                           int text_scale )
  {
    GeoVector3 normal = fit.attitude.unitNormal;
    BeddingProjection resolved = projection == null ? BeddingProjection.unsupported() : projection;
    return new BeddingAttitudePointState( true, Mode.FIT,
      normal.east, normal.north, normal.up, station, shot_ids, source_lengths,
      source_bearings, source_clinos, "SURVEY_MAGNETIC",
      declination_degrees, model == null ? "" : model.id,
      model == null ? Double.NaN : model.sigmaDistanceMeters,
      model == null ? Double.NaN : Math.toDegrees( model.sigmaBearingBaseRadians ),
      model == null ? Double.NaN : Math.toDegrees( model.sigmaClinoRadians ),
      model == null ? Double.NaN : model.surfaceScatterMeters,
      model == null ? Double.NaN : model.bearingCosineFloor,
      fit.quality.name(), issueNames( fit.issues ),
      regionMinimum( fit.region68 ), regionMaximum( fit.region68 ),
      regionDirectionStart( fit.region68 ), regionDirectionEnd( fit.region68 ),
      regionWraps( fit.region68 ), regionStatus( fit.region68 ),
      regionMinimum( fit.region95 ), regionMaximum( fit.region95 ),
      regionDirectionStart( fit.region95 ), regionDirectionEnd( fit.region95 ),
      regionWraps( fit.region95 ), regionStatus( fit.region95 ),
      resolved.viewKind, resolved.traceValid, resolved.canvasTraceAngleDegrees,
      resolved.apparentDipDegrees, resolved.extendedReferenceBearingDegrees,
      resolved.extendedExtendSign, resolved.extendedReferenceAmbiguous,
      PlanGlyphOverride.AUTO,
      resolved.region68ApparentDipMinimum, resolved.region68ApparentDipMaximum,
      resolved.region68FallStatus,
      resolved.region95ApparentDipMinimum, resolved.region95ApparentDipMaximum,
      resolved.region95FallStatus,
      font_id, bold, italic, underline, text_scale );
  }

  BeddingAttitude attitude()
  {
    return BeddingAttitude.fromNormal( new GeoVector3( normalEast, normalNorth, normalUp ) );
  }

  BeddingAttitude.Kind planGlyphKind()
  {
    if ( planGlyphOverride == PlanGlyphOverride.HORIZONTAL ) return BeddingAttitude.Kind.HORIZONTAL;
    if ( planGlyphOverride == PlanGlyphOverride.VERTICAL ) return BeddingAttitude.Kind.VERTICAL;
    BeddingAttitude attitude = attitude();
    return attitude == null ? BeddingAttitude.Kind.INCLINED : attitude.kind;
  }

  BeddingAttitudePointState withPlanGlyphOverride( PlanGlyphOverride override )
  {
    return copy( mFontId, mBold, mItalic, mUnderline, mTextScalePercent,
      override == null ? PlanGlyphOverride.AUTO : override );
  }

  BeddingAttitudePointState withTypography( SketchTextStyle style )
  {
    if ( style == null ) return this;
    return copy( style.fontId(), style.bold(), style.italic(), style.underline(), mTextScalePercent );
  }

  BeddingAttitudePointState withTypography( String font_id, boolean bold, boolean italic,
                                            boolean underline, int text_scale )
  {
    return copy( font_id, bold, italic, underline, text_scale );
  }

  BeddingAttitudePointState withStationAndProjection( String station, BeddingProjection projection )
  {
    BeddingProjection resolved = projection == null ? BeddingProjection.unsupported() : projection;
    return new BeddingAttitudePointState( configured, mode, normalEast, normalNorth, normalUp,
      station, sourceShotIds, sourceLengthsMeters, sourceBearingsDegrees, sourceClinosDegrees,
      azimuthReference, declinationDegrees, measurementModelId,
      sigmaDistanceMeters, sigmaBearingDegrees, sigmaClinoDegrees, surfaceScatterMeters,
      bearingCosineFloor, fitQuality, fitIssues,
      region68DipMinimum, region68DipMaximum, region68DirectionStart, region68DirectionEnd,
      region68DirectionWrapsNorth, region68Status,
      region95DipMinimum, region95DipMaximum, region95DirectionStart, region95DirectionEnd,
      region95DirectionWrapsNorth, region95Status,
      resolved.viewKind, resolved.traceValid, resolved.canvasTraceAngleDegrees,
      resolved.apparentDipDegrees, resolved.extendedReferenceBearingDegrees,
      resolved.extendedExtendSign, resolved.extendedReferenceAmbiguous,
      planGlyphOverride,
      resolved.region68ApparentDipMinimum, resolved.region68ApparentDipMaximum,
      resolved.region68FallStatus,
      resolved.region95ApparentDipMinimum, resolved.region95ApparentDipMaximum,
      resolved.region95FallStatus,
      mFontId, mBold, mItalic, mUnderline, mTextScalePercent );
  }

  private BeddingAttitudePointState copy( String font_id, boolean bold, boolean italic,
                                          boolean underline, int text_scale )
  {
    return copy( font_id, bold, italic, underline, text_scale, planGlyphOverride );
  }

  private BeddingAttitudePointState copy( String font_id, boolean bold, boolean italic,
                                          boolean underline, int text_scale,
                                          PlanGlyphOverride glyph_override )
  {
    return new BeddingAttitudePointState( configured, mode, normalEast, normalNorth, normalUp,
      stationName, sourceShotIds, sourceLengthsMeters, sourceBearingsDegrees, sourceClinosDegrees,
      azimuthReference, declinationDegrees, measurementModelId,
      sigmaDistanceMeters, sigmaBearingDegrees, sigmaClinoDegrees, surfaceScatterMeters,
      bearingCosineFloor, fitQuality, fitIssues,
      region68DipMinimum, region68DipMaximum, region68DirectionStart, region68DirectionEnd,
      region68DirectionWrapsNorth, region68Status,
      region95DipMinimum, region95DipMaximum, region95DirectionStart, region95DirectionEnd,
      region95DirectionWrapsNorth, region95Status,
      viewKind, traceValid, canvasTraceAngleDegrees, apparentDipDegrees,
      extendedReferenceBearingDegrees, extendedExtendSign, extendedReferenceAmbiguous,
      glyph_override,
      region68ApparentDipMinimum, region68ApparentDipMaximum, region68FallStatus,
      region95ApparentDipMinimum, region95ApparentDipMaximum, region95FallStatus,
      font_id, bold, italic, underline, text_scale );
  }

  BeddingFitResult.AttitudeRegion persistedRegion68()
  {
    return persistedRegion( 0.68, region68DipMinimum, region68DipMaximum,
      region68DirectionStart, region68DirectionEnd, region68DirectionWrapsNorth, region68Status );
  }

  BeddingFitResult.AttitudeRegion persistedRegion95()
  {
    return persistedRegion( 0.95, region95DipMinimum, region95DipMaximum,
      region95DirectionStart, region95DirectionEnd, region95DirectionWrapsNorth, region95Status );
  }

  private static BeddingFitResult.AttitudeRegion persistedRegion( double coverage,
      double dip_minimum, double dip_maximum, double direction_start, double direction_end,
      boolean wraps, String status )
  {
    BeddingFitResult.RegionStatus parsed;
    try {
      parsed = BeddingFitResult.RegionStatus.valueOf( status );
    } catch ( IllegalArgumentException | NullPointerException exception ) {
      parsed = BeddingFitResult.RegionStatus.UNAVAILABLE;
    }
    return BeddingFitResult.AttitudeRegion.fromBounds( coverage, dip_minimum, dip_maximum,
      direction_start, direction_end, wraps, parsed );
  }

  private static String[] issueNames( Set< BeddingFitResult.Issue > issues )
  {
    if ( issues == null || issues.isEmpty() ) return new String[0];
    String[] names = new String[ issues.size() ];
    int index = 0;
    for ( BeddingFitResult.Issue issue : issues ) names[index++] = issue.name();
    return names;
  }

  private static double regionMinimum( BeddingFitResult.AttitudeRegion region )
  {
    return region == null ? Double.NaN : region.dipMinimum;
  }

  private static double regionMaximum( BeddingFitResult.AttitudeRegion region )
  {
    return region == null ? Double.NaN : region.dipMaximum;
  }

  private static double regionDirectionStart( BeddingFitResult.AttitudeRegion region )
  {
    return region == null ? Double.NaN : region.directionStart;
  }

  private static double regionDirectionEnd( BeddingFitResult.AttitudeRegion region )
  {
    return region == null ? Double.NaN : region.directionEnd;
  }

  private static boolean regionWraps( BeddingFitResult.AttitudeRegion region )
  {
    return region != null && region.directionWrapsNorth;
  }

  private static String regionStatus( BeddingFitResult.AttitudeRegion region )
  {
    return region == null ? "UNAVAILABLE" : region.status.name();
  }

  @Override public String[] displayRows( String primary_text ) { return new String[] { primary_text }; }
  @Override public Separator separator() { return Separator.NONE; }
  @Override public String fontId() { return mFontId; }
  @Override public boolean bold() { return mBold; }
  @Override public boolean italic() { return mItalic; }
  @Override public boolean underline() { return mUnderline; }
  @Override public int textScalePercent() { return mTextScalePercent; }
}
