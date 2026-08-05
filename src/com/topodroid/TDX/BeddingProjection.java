/* @file BeddingProjection.java
 *
 * @brief Immutable projection metadata saved with a bedding point
 */
package com.topodroid.TDX;

import com.topodroid.geo.BeddingFitResult;

final class BeddingProjection
{
  final BeddingAttitudePointState.ViewKind viewKind;
  final boolean traceValid;
  final double canvasTraceAngleDegrees;
  final double apparentDipDegrees;
  final double extendedReferenceBearingDegrees;
  final double extendedExtendSign;
  final boolean extendedReferenceAmbiguous;
  final double region68ApparentDipMinimum;
  final double region68ApparentDipMaximum;
  final String region68FallStatus;
  final double region95ApparentDipMinimum;
  final double region95ApparentDipMaximum;
  final String region95FallStatus;

  BeddingProjection( BeddingAttitudePointState.ViewKind view_kind, boolean trace_valid,
                     double trace_angle, double apparent_dip, double reference_bearing,
                     double extend_sign, boolean reference_ambiguous )
  {
    viewKind = view_kind;
    traceValid = trace_valid;
    canvasTraceAngleDegrees = trace_angle;
    apparentDipDegrees = apparent_dip;
    extendedReferenceBearingDegrees = reference_bearing;
    extendedExtendSign = extend_sign;
    extendedReferenceAmbiguous = reference_ambiguous;
    region68ApparentDipMinimum = Double.NaN;
    region68ApparentDipMaximum = Double.NaN;
    region68FallStatus = BeddingFitResult.ProjectedFallStatus.UNAVAILABLE.name();
    region95ApparentDipMinimum = Double.NaN;
    region95ApparentDipMaximum = Double.NaN;
    region95FallStatus = BeddingFitResult.ProjectedFallStatus.UNAVAILABLE.name();
  }

  private BeddingProjection( BeddingProjection base,
                             BeddingFitResult.ProjectedRegion region68,
                             BeddingFitResult.ProjectedRegion region95 )
  {
    viewKind = base.viewKind;
    traceValid = base.traceValid;
    canvasTraceAngleDegrees = base.canvasTraceAngleDegrees;
    apparentDipDegrees = base.apparentDipDegrees;
    extendedReferenceBearingDegrees = base.extendedReferenceBearingDegrees;
    extendedExtendSign = base.extendedExtendSign;
    extendedReferenceAmbiguous = base.extendedReferenceAmbiguous;
    region68ApparentDipMinimum = minimum( region68 );
    region68ApparentDipMaximum = maximum( region68 );
    region68FallStatus = fallStatus( region68 );
    region95ApparentDipMinimum = minimum( region95 );
    region95ApparentDipMaximum = maximum( region95 );
    region95FallStatus = fallStatus( region95 );
  }

  BeddingProjection withRegions( BeddingFitResult.ProjectedRegion region68,
                                 BeddingFitResult.ProjectedRegion region95 )
  {
    return new BeddingProjection( this, region68, region95 );
  }

  static BeddingProjection plan()
  {
    return new BeddingProjection( BeddingAttitudePointState.ViewKind.PLAN, false,
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false );
  }

  static BeddingProjection unsupported()
  {
    return new BeddingProjection( BeddingAttitudePointState.ViewKind.UNSUPPORTED, false,
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, false );
  }

  private static double minimum( BeddingFitResult.ProjectedRegion region )
  {
    return region == null ? Double.NaN : region.apparentDipMinimum;
  }

  private static double maximum( BeddingFitResult.ProjectedRegion region )
  {
    return region == null ? Double.NaN : region.apparentDipMaximum;
  }

  private static String fallStatus( BeddingFitResult.ProjectedRegion region )
  {
    return region == null ? BeddingFitResult.ProjectedFallStatus.UNAVAILABLE.name()
      : region.fallStatus.name();
  }
}
