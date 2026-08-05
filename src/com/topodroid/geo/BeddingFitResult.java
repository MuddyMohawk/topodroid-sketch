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
  public enum ProjectedFallStatus { POSITIVE_X, NEGATIVE_X, UNRESOLVED, UNAVAILABLE }
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
    private final double[] mAcceptedNormals;

    AttitudeRegion( double region_coverage, double dip_minimum, double dip_maximum,
                    double direction_start, double direction_end, boolean wraps,
                    RegionStatus region_status, double[] accepted_normals )
    {
      coverage = region_coverage;
      dipMinimum = dip_minimum;
      dipMaximum = dip_maximum;
      directionStart = direction_start;
      directionEnd = direction_end;
      directionWrapsNorth = wraps;
      status = region_status;
      mAcceptedNormals = accepted_normals == null ? new double[0] : accepted_normals.clone();
    }

    AttitudeRegion( double region_coverage, double dip_minimum, double dip_maximum,
                    double direction_start, double direction_end, boolean wraps,
                    RegionStatus region_status )
    {
      this( region_coverage, dip_minimum, dip_maximum, direction_start, direction_end,
        wraps, region_status, null );
    }

    /** Rebuild a conservative rectangular/circular envelope for a persisted fit.
     *  Fresh fits retain their accepted likelihood samples; this fallback is used
     *  only when a saved fit must be projected through a changed profile basis. */
    public static AttitudeRegion fromBounds( double region_coverage,
                                             double dip_minimum, double dip_maximum,
                                             double direction_start, double direction_end,
                                             boolean wraps, RegionStatus region_status )
    {
      return new AttitudeRegion( region_coverage, dip_minimum, dip_maximum,
        direction_start, direction_end, wraps,
        region_status == null ? RegionStatus.UNAVAILABLE : region_status, null );
    }

    public ProjectedRegion project( ProjectionBasis basis )
    {
      if ( basis == null || ! Double.isFinite( dipMinimum ) || ! Double.isFinite( dipMaximum ) ) {
        return ProjectedRegion.unavailable();
      }
      ProjectedAccumulator projected = new ProjectedAccumulator();
      if ( mAcceptedNormals.length >= 3 ) {
        for ( int i = 0; i + 2 < mAcceptedNormals.length; i += 3 ) {
          projected.add( basis.trace( new GeoVector3( mAcceptedNormals[i],
            mAcceptedNormals[i + 1], mAcceptedNormals[i + 2] ) ) );
        }
      } else {
        projectEnvelope( basis, projected );
      }
      return projected.result();
    }

    private void projectEnvelope( ProjectionBasis basis, ProjectedAccumulator projected )
    {
      double bounded_dip_min = Math.max( 0.0, dipMinimum );
      double bounded_dip_max = Math.min( 90.0, dipMaximum );
      int dip_steps = Math.max( 1, (int)Math.ceil( bounded_dip_max - bounded_dip_min ) );
      boolean all_directions = status == RegionStatus.DIRECTION_UNRESOLVED
        || ! Double.isFinite( directionStart ) || ! Double.isFinite( directionEnd );
      double direction_span = all_directions ? 360.0
        : ( directionWrapsNorth ? directionEnd + 360.0 - directionStart
                                : directionEnd - directionStart );
      int direction_steps = Math.max( 1, (int)Math.ceil( Math.max( 0.0, direction_span ) ) );
      for ( int dip_index = 0; dip_index <= dip_steps; ++dip_index ) {
        double dip = bounded_dip_min
          + ( bounded_dip_max - bounded_dip_min ) * dip_index / dip_steps;
        for ( int direction_index = 0; direction_index <= direction_steps; ++direction_index ) {
          double direction = all_directions
            ? 360.0 * direction_index / direction_steps
            : directionStart + direction_span * direction_index / direction_steps;
          BeddingAttitude attitude = BeddingAttitude.fromDipDirection(
            BeddingAttitude.wrap360( direction ), dip );
          if ( attitude != null ) projected.add( basis.trace( attitude.unitNormal ) );
        }
      }
    }
  }

  public static final class ProjectedRegion
  {
    public final double apparentDipMinimum;
    public final double apparentDipMaximum;
    public final ProjectedFallStatus fallStatus;

    ProjectedRegion( double minimum, double maximum, ProjectedFallStatus fall_status )
    {
      apparentDipMinimum = minimum;
      apparentDipMaximum = maximum;
      fallStatus = fall_status;
    }

    static ProjectedRegion unavailable()
    {
      return new ProjectedRegion( Double.NaN, Double.NaN, ProjectedFallStatus.UNAVAILABLE );
    }
  }

  private static final class ProjectedAccumulator
  {
    private double mMinimum = Double.POSITIVE_INFINITY;
    private double mMaximum = Double.NEGATIVE_INFINITY;
    private boolean mPositive;
    private boolean mNegative;
    private boolean mHorizontal;

    void add( ProjectionBasis.Trace trace )
    {
      if ( trace == null || ! trace.valid || ! Double.isFinite( trace.geologicalApparentDipDegrees ) ) return;
      mMinimum = Math.min( mMinimum, trace.geologicalApparentDipDegrees );
      mMaximum = Math.max( mMaximum, trace.geologicalApparentDipDegrees );
      if ( trace.geologicalApparentDipDegrees <= 0.05 ) mHorizontal = true;
      else if ( trace.fallsTowardPositiveX ) mPositive = true;
      else mNegative = true;
    }

    ProjectedRegion result()
    {
      if ( ! Double.isFinite( mMinimum ) ) return ProjectedRegion.unavailable();
      ProjectedFallStatus status = mHorizontal || ( mPositive && mNegative )
        ? ProjectedFallStatus.UNRESOLVED
        : ( mPositive ? ProjectedFallStatus.POSITIVE_X
                      : ( mNegative ? ProjectedFallStatus.NEGATIVE_X
                                    : ProjectedFallStatus.UNRESOLVED ) );
      return new ProjectedRegion( mMinimum, mMaximum, status );
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
