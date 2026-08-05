/* @file BeddingFitResult.java
 *
 * @brief Immutable outcome of fitting a bedding plane
 */
package com.topodroid.geo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class BeddingFitResult
{
  public enum Status { VALID, INVALID }
  public enum Quality { GOOD, CAUTION, INVALID }
  public enum RegionStatus { BOUNDED, DIRECTION_UNRESOLVED, AXIAL_VERTICAL, MULTIMODAL, UNAVAILABLE }
  public enum Issue {
    TOO_FEW_POINTS,
    DUPLICATE_SOURCE,
    COINCIDENT_POINTS,
    COLLINEAR_POINTS,
    NON_FINITE_INPUT,
    NUMERICAL_FAILURE,
    NO_REDUNDANCY,
    PATCH_TOO_SMALL,
    RESIDUALS_HIGH,
    INFLUENTIAL_POINT,
    DIRECTION_UNRESOLVED,
    VERTICAL_AXIAL,
    MULTIMODAL,
    VERTICAL_AZIMUTH_WEAK
  }

  public static final class AttitudeRegion
  {
    public final double coverage;
    public final double dipMinimum;
    public final double dipMaximum;
    public final double directionStart;
    public final double directionEnd;
    public final boolean directionWrapsNorth;
    public final RegionStatus status;

    AttitudeRegion( double region_coverage, double dip_minimum, double dip_maximum,
                    double direction_start, double direction_end, boolean wraps,
                    RegionStatus region_status )
    {
      coverage = region_coverage;
      dipMinimum = dip_minimum;
      dipMaximum = dip_maximum;
      directionStart = direction_start;
      directionEnd = direction_end;
      directionWrapsNorth = wraps;
      status = region_status;
    }
  }

  public final Status status;
  public final Quality quality;
  public final BeddingAttitude attitude;
  public final GeoVector3 centroid;
  public final double planeOffset;
  public final double objective;
  public final double[] eigenvalues;
  public final double[] residualsMeters;
  public final double[] normalizedResiduals;
  public final double leaveOneOutMaximumAngleDegrees;
  public final double leaveOneOutMedianAngleDegrees;
  public final long influentialSourceId;
  public final int leaveOneOutInvalidCount;
  public final AttitudeRegion region68;
  public final AttitudeRegion region95;
  public final Set< Issue > issues;

  BeddingFitResult( Status fit_status, Quality fit_quality, BeddingAttitude fitted_attitude,
                    GeoVector3 fit_centroid, double offset, double fit_objective,
                    double[] fit_eigenvalues, double[] residuals, double[] normalized_residuals,
                    double influence_maximum, double influence_median, long influential_source_id,
                    int influence_invalid_count,
                    AttitudeRegion region_68, AttitudeRegion region_95, EnumSet< Issue > fit_issues )
  {
    status = fit_status;
    quality = fit_quality;
    attitude = fitted_attitude;
    centroid = fit_centroid;
    planeOffset = offset;
    objective = fit_objective;
    eigenvalues = fit_eigenvalues == null ? new double[0] : fit_eigenvalues.clone();
    residualsMeters = residuals == null ? new double[0] : residuals.clone();
    normalizedResiduals = normalized_residuals == null ? new double[0] : normalized_residuals.clone();
    leaveOneOutMaximumAngleDegrees = influence_maximum;
    leaveOneOutMedianAngleDegrees = influence_median;
    influentialSourceId = influential_source_id;
    leaveOneOutInvalidCount = influence_invalid_count;
    region68 = region_68;
    region95 = region_95;
    issues = Collections.unmodifiableSet( fit_issues == null
      ? EnumSet.noneOf( Issue.class ) : EnumSet.copyOf( fit_issues ) );
  }

  static BeddingFitResult invalid( Issue issue )
  {
    EnumSet< Issue > issues = EnumSet.noneOf( Issue.class );
    issues.add( issue );
    return new BeddingFitResult( Status.INVALID, Quality.INVALID, null, null, Double.NaN,
      Double.NaN, null, null, null, Double.NaN, Double.NaN, -1L, 0,
      null, null, issues );
  }
}
