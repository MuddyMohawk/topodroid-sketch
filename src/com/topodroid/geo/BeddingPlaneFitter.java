/* @file BeddingPlaneFitter.java
 *
 * @brief Deterministic station-relative bedding plane fit
 */
package com.topodroid.geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BeddingPlaneFitter
{
  private static final double COARSE_STEP = 2.0;
  private static final double REGION_68_DELTA = 2.30;
  private static final double REGION_95_DELTA = 5.99;
  private static final double CANDIDATE_DELTA = 8.0;
  private static final int MAX_BASINS = 16;
  // A five-degree leave-one-out swing is already large enough to change the
  // map symbol materially and merits inspection; it never removes the shot.
  private static final double INFLUENCE_CAUTION_DEGREES = 5.0;

  private BeddingPlaneFitter() { }

  public static BeddingFitResult fit( List< BeddingObservation > observations,
                                      BeddingMeasurementModel model )
  {
    return fitInternal( observations, model, true );
  }

  /** Coverage tests exercise the production estimator/regions without paying
   *  for the unrelated leave-one-out diagnostic on every simulated dataset. */
  static BeddingFitResult fitForCoverage( List< BeddingObservation > observations,
                                          BeddingMeasurementModel model )
  {
    return fitInternal( observations, model, false );
  }

  private static BeddingFitResult fitInternal( List< BeddingObservation > observations,
                                               BeddingMeasurementModel model,
                                               boolean compute_influence )
  {
    if ( observations == null || observations.size() < 3 ) {
      return BeddingFitResult.invalid( BeddingFitResult.Issue.TOO_FEW_POINTS );
    }
    if ( model == null || ! model.isValid() ) {
      return BeddingFitResult.invalid( BeddingFitResult.Issue.NON_FINITE_INPUT );
    }

    EnumSet< BeddingFitResult.Issue > issues = EnumSet.noneOf( BeddingFitResult.Issue.class );
    Set< Long > ids = new HashSet<>();
    GeoVector3 sum = new GeoVector3( 0, 0, 0 );
    double mean_range = 0.0;
    for ( BeddingObservation observation : observations ) {
      if ( observation == null || observation.endpoint == null || ! observation.endpoint.isFinite()
          || ! Double.isFinite( observation.lengthMeters ) ) {
        return BeddingFitResult.invalid( BeddingFitResult.Issue.NON_FINITE_INPUT );
      }
      if ( ! ids.add( observation.sourceId ) ) issues.add( BeddingFitResult.Issue.DUPLICATE_SOURCE );
      if ( Math.abs( Math.cos( Math.toRadians( observation.clinoDegrees ) ) )
          < model.bearingCosineFloor ) issues.add( BeddingFitResult.Issue.VERTICAL_AZIMUTH_WEAK );
      sum = sum.plus( observation.endpoint );
      mean_range += observation.lengthMeters;
    }
    if ( issues.contains( BeddingFitResult.Issue.DUPLICATE_SOURCE ) ) {
      return invalidWith( BeddingFitResult.Issue.DUPLICATE_SOURCE, issues );
    }
    int count = observations.size();
    mean_range /= count;
    double coincident_tolerance = Math.max( 1.0e-6, 2.0 * model.sigmaDistanceMeters );
    ArrayList< GeoVector3 > distinct_points = new ArrayList<>();
    for ( BeddingObservation observation : observations ) {
      boolean distinct = true;
      for ( GeoVector3 prior : distinct_points ) {
        if ( observation.endpoint.minus( prior ).norm() <= coincident_tolerance ) {
          distinct = false;
          issues.add( BeddingFitResult.Issue.COINCIDENT_POINTS );
          break;
        }
      }
      if ( distinct ) distinct_points.add( observation.endpoint );
    }
    if ( distinct_points.size() < 3 ) {
      return invalidWith( BeddingFitResult.Issue.COINCIDENT_POINTS, issues );
    }
    GeoVector3 centroid = sum.times( 1.0 / count );
    double[][] scatter = new double[3][3];
    for ( BeddingObservation observation : observations ) {
      GeoVector3 q = observation.endpoint.minus( centroid );
      accumulateOuter( scatter, q, 1.0 / count );
    }

    SymmetricEigen3.Result eigen = SymmetricEigen3.solve( scatter );
    if ( ! eigen.converged || eigen.vectors[0] == null ) {
      return invalidWith( BeddingFitResult.Issue.NUMERICAL_FAILURE, issues );
    }
    double lambda2 = Math.max( 0.0, eigen.values[2] );
    double lambda1 = Math.max( 0.0, eigen.values[1] );
    if ( lambda2 <= 1.0e-14 ) return invalidWith( BeddingFitResult.Issue.COINCIDENT_POINTS, issues );
    if ( lambda1 / lambda2 <= 1.0e-8 ) {
      return invalidWith( BeddingFitResult.Issue.COLLINEAR_POINTS, issues );
    }
    if ( count == 3 ) issues.add( BeddingFitResult.Issue.NO_REDUNDANCY );

    double expected_angular = Math.max( model.sigmaBearingBaseRadians, model.sigmaClinoRadians );
    if ( Math.sqrt( lambda1 ) < 2.0 * mean_range * expected_angular ) {
      issues.add( BeddingFitResult.Issue.PATCH_TOO_SMALL );
    }

    ArrayList< Candidate > coarse = globalGrid( observations, model );
    if ( coarse.isEmpty() ) return invalidWith( BeddingFitResult.Issue.NUMERICAL_FAILURE, issues );
    Collections.sort( coarse, Candidate.BY_OBJECTIVE );
    Candidate coarse_best = coarse.get( 0 );
    ArrayList< Candidate > basin_seeds = selectBasins( coarse, coarse_best.objective );
    ArrayList< Candidate > refined = new ArrayList<>();
    for ( Candidate seed : basin_seeds ) refined.add( refine( seed, observations, model ) );
    Collections.sort( refined, Candidate.BY_OBJECTIVE );
    Candidate best = refined.get( 0 );
    if ( best == null || ! Double.isFinite( best.objective ) ) {
      return invalidWith( BeddingFitResult.Issue.NUMERICAL_FAILURE, issues );
    }

    ArrayList< Candidate > region_samples = new ArrayList<>();
    for ( Candidate sample : coarse ) {
      if ( sample.objective <= best.objective + CANDIDATE_DELTA ) region_samples.add( sample );
    }
    for ( Candidate center : refined ) {
      if ( center.objective <= best.objective + CANDIDATE_DELTA ) {
        addLocalSamples( region_samples, center, 2.0, 0.1, observations, model );
        addLocalSamples( region_samples, center, 0.25, 0.01, observations, model );
        region_samples.add( center );
      }
    }

    BeddingFitResult.AttitudeRegion region68 = makeRegion( region_samples, refined, best,
      REGION_68_DELTA, 0.68 );
    BeddingFitResult.AttitudeRegion region95 = makeRegion( region_samples, refined, best,
      REGION_95_DELTA, 0.95 );
    addRegionIssues( issues, region95 );

    double[] residuals = new double[count];
    double[] normalized = new double[count];
    boolean high_residual = false;
    for ( int i = 0; i < count; ++i ) {
      BeddingObservation observation = observations.get( i );
      double residual = best.normal.dot( observation.endpoint ) + best.offset;
      double variance = observation.normalVariance( best.normal, model.surfaceScatterMeters );
      residuals[i] = residual;
      normalized[i] = residual / Math.sqrt( variance );
      if ( Math.abs( normalized[i] ) > 3.0 ) high_residual = true;
    }
    int dof = count - 3;
    if ( high_residual || ( dof >= 3 && best.objective / dof > 4.0 ) ) {
      issues.add( BeddingFitResult.Issue.RESIDUALS_HIGH );
    }

    BeddingAttitude attitude = BeddingAttitude.fromNormal( best.normal );
    if ( attitude == null ) return invalidWith( BeddingFitResult.Issue.NUMERICAL_FAILURE, issues );
    InfluenceSummary influence = compute_influence
      ? leaveOneOutInfluence( observations, model, best ) : InfluenceSummary.unavailable();
    if ( influence.invalidCount > 0
        || ( Double.isFinite( influence.maximumAngleDegrees )
             && influence.maximumAngleDegrees > INFLUENCE_CAUTION_DEGREES ) ) {
      issues.add( BeddingFitResult.Issue.INFLUENTIAL_POINT );
    }
    BeddingFitResult.Quality quality = issues.isEmpty()
      ? BeddingFitResult.Quality.GOOD : BeddingFitResult.Quality.CAUTION;
    return new BeddingFitResult( BeddingFitResult.Status.VALID, quality, attitude,
      best.centroid, best.offset, best.objective, eigen.values, residuals, normalized,
      influence.maximumAngleDegrees, influence.medianAngleDegrees,
      influence.sourceId, influence.invalidCount,
      region68, region95, issues );
  }

  private static BeddingFitResult invalidWith( BeddingFitResult.Issue issue,
                                               EnumSet< BeddingFitResult.Issue > existing )
  {
    EnumSet< BeddingFitResult.Issue > issues = existing == null
      ? EnumSet.noneOf( BeddingFitResult.Issue.class ) : EnumSet.copyOf( existing );
    issues.add( issue );
    return new BeddingFitResult( BeddingFitResult.Status.INVALID, BeddingFitResult.Quality.INVALID,
      null, null, Double.NaN, Double.NaN, null, null, null,
      Double.NaN, Double.NaN, -1L, 0, null, null, issues );
  }

  private static InfluenceSummary leaveOneOutInfluence( List< BeddingObservation > observations,
                                                         BeddingMeasurementModel model,
                                                         Candidate full )
  {
    if ( observations.size() < 5 ) return InfluenceSummary.unavailable();
    ArrayList< Double > changes = new ArrayList<>();
    double maximum = -1.0;
    long maximum_id = -1L;
    int invalid = 0;
    for ( int omitted = 0; omitted < observations.size(); ++omitted ) {
      ArrayList< BeddingObservation > subset = new ArrayList<>( observations.size() - 1 );
      for ( int i = 0; i < observations.size(); ++i ) if ( i != omitted ) subset.add( observations.get( i ) );
      Candidate seed = tlsSeed( subset, model );
      Candidate fitted = seed == null ? null : refine( seed, subset, model );
      if ( fitted == null || ! Double.isFinite( fitted.objective ) ) { ++invalid; continue; }
      double change = planeAngleDegrees( full.normal, fitted.normal );
      changes.add( change );
      if ( change > maximum ) {
        maximum = change;
        maximum_id = observations.get( omitted ).sourceId;
      }
    }
    if ( changes.isEmpty() ) {
      return new InfluenceSummary( Double.NaN, Double.NaN, -1L, invalid );
    }
    Collections.sort( changes );
    int middle = changes.size() / 2;
    double median = changes.size() % 2 == 0
      ? 0.5 * ( changes.get( middle - 1 ) + changes.get( middle ) ) : changes.get( middle );
    return new InfluenceSummary( maximum, median, maximum_id, invalid );
  }

  private static Candidate tlsSeed( List< BeddingObservation > observations,
                                    BeddingMeasurementModel model )
  {
    if ( observations == null || observations.size() < 3 ) return null;
    GeoVector3 sum = new GeoVector3( 0, 0, 0 );
    for ( BeddingObservation observation : observations ) sum = sum.plus( observation.endpoint );
    GeoVector3 centroid = sum.times( 1.0 / observations.size() );
    double[][] scatter = new double[3][3];
    for ( BeddingObservation observation : observations ) {
      accumulateOuter( scatter, observation.endpoint.minus( centroid ), 1.0 / observations.size() );
    }
    SymmetricEigen3.Result eigen = SymmetricEigen3.solve( scatter );
    if ( ! eigen.converged ) return null;
    double lambda2 = Math.max( 0.0, eigen.values[2] );
    double lambda1 = Math.max( 0.0, eigen.values[1] );
    if ( lambda2 <= 1.0e-14 || lambda1 / lambda2 <= 1.0e-8 ) return null;
    BeddingAttitude attitude = BeddingAttitude.fromNormal( eigen.vectors[0] );
    if ( attitude == null ) return null;
    double direction = attitude.dipDirectionDegrees;
    if ( ! Double.isFinite( direction ) ) {
      direction = BeddingAttitude.wrap360( Math.toDegrees(
        Math.atan2( attitude.unitNormal.east, attitude.unitNormal.north ) ) );
    }
    return evaluate( attitude.dipDegrees, direction, observations, model );
  }

  private static final class InfluenceSummary
  {
    final double maximumAngleDegrees;
    final double medianAngleDegrees;
    final long sourceId;
    final int invalidCount;

    InfluenceSummary( double maximum, double median, long source_id, int invalid_count )
    {
      maximumAngleDegrees = maximum;
      medianAngleDegrees = median;
      sourceId = source_id;
      invalidCount = invalid_count;
    }

    static InfluenceSummary unavailable()
    {
      return new InfluenceSummary( Double.NaN, Double.NaN, -1L, 0 );
    }
  }

  private static void accumulateOuter( double[][] matrix, GeoVector3 vector, double weight )
  {
    double[] p = new double[] { vector.east, vector.north, vector.up };
    for ( int i = 0; i < 3; ++i ) {
      for ( int j = i; j < 3; ++j ) {
        matrix[i][j] += weight * p[i] * p[j];
        if ( i != j ) matrix[j][i] = matrix[i][j];
      }
    }
  }

  private static ArrayList< Candidate > globalGrid( List< BeddingObservation > observations,
                                                     BeddingMeasurementModel model )
  {
    ArrayList< Candidate > samples = new ArrayList<>();
    for ( double dip = 0.0; dip <= 90.0001; dip += COARSE_STEP ) {
      double direction_limit = dip >= 90.0 ? 180.0 : 360.0;
      double direction_step = dip <= 0.0 ? direction_limit : COARSE_STEP;
      for ( double direction = 0.0; direction < direction_limit; direction += direction_step ) {
        Candidate candidate = evaluate( dip, direction, observations, model );
        if ( candidate != null ) samples.add( candidate );
      }
    }
    return samples;
  }

  private static ArrayList< Candidate > selectBasins( List< Candidate > sorted, double best_objective )
  {
    ArrayList< Candidate > selected = new ArrayList<>();
    for ( Candidate candidate : sorted ) {
      if ( candidate.objective > best_objective + CANDIDATE_DELTA ) break;
      boolean distinct = true;
      for ( Candidate prior : selected ) {
        if ( planeAngleDegrees( candidate.normal, prior.normal ) < 3.0 ) {
          distinct = false;
          break;
        }
      }
      if ( distinct ) selected.add( candidate );
      if ( selected.size() >= MAX_BASINS ) break;
    }
    if ( selected.isEmpty() && ! sorted.isEmpty() ) selected.add( sorted.get( 0 ) );
    return selected;
  }

  private static Candidate refine( Candidate seed, List< BeddingObservation > observations,
                                   BeddingMeasurementModel model )
  {
    Candidate best = seed;
    double radius = COARSE_STEP;
    double[] steps = new double[] { 0.2, 0.02, 0.002 };
    for ( double step : steps ) {
      Candidate stage_best = best;
      for ( double dip = best.dip - radius; dip <= best.dip + radius + 0.5 * step; dip += step ) {
        for ( double direction = best.direction - radius;
              direction <= best.direction + radius + 0.5 * step; direction += step ) {
          Candidate candidate = evaluate( dip, direction, observations, model );
          if ( candidate != null && candidate.objective < stage_best.objective ) stage_best = candidate;
        }
      }
      best = stage_best;
      radius = step;
    }
    return best;
  }

  private static void addLocalSamples( List< Candidate > destination, Candidate center,
                                       double radius, double step,
                                       List< BeddingObservation > observations,
                                       BeddingMeasurementModel model )
  {
    for ( double dip = center.dip - radius; dip <= center.dip + radius + 0.5 * step; dip += step ) {
      for ( double direction = center.direction - radius;
            direction <= center.direction + radius + 0.5 * step; direction += step ) {
        Candidate candidate = evaluate( dip, direction, observations, model );
        if ( candidate != null ) destination.add( candidate );
      }
    }
  }

  private static Candidate evaluate( double raw_dip, double raw_direction,
                                     List< BeddingObservation > observations,
                                     BeddingMeasurementModel model )
  {
    double dip = raw_dip;
    double direction = raw_direction;
    while ( dip < 0.0 || dip > 90.0 ) {
      if ( dip < 0.0 ) { dip = -dip; direction += 180.0; }
      if ( dip > 90.0 ) { dip = 180.0 - dip; direction += 180.0; }
    }
    direction = BeddingAttitude.wrap360( direction );
    if ( dip <= 1.0e-12 ) direction = 0.0;
    if ( dip >= 90.0 ) direction = BeddingAttitude.wrap180( direction );
    BeddingAttitude attitude = BeddingAttitude.fromDipDirection( direction, dip );
    if ( attitude == null ) return null;
    GeoVector3 normal = attitude.unitNormal;

    double weight_sum = 0.0;
    GeoVector3 weighted_sum = new GeoVector3( 0, 0, 0 );
    for ( BeddingObservation observation : observations ) {
      double variance = observation.normalVariance( normal, model.surfaceScatterMeters );
      if ( ! Double.isFinite( variance ) || variance <= 1.0e-16 ) return null;
      double weight = 1.0 / variance;
      weight_sum += weight;
      weighted_sum = weighted_sum.plus( observation.endpoint.times( weight ) );
    }
    if ( ! Double.isFinite( weight_sum ) || weight_sum <= 0.0 ) return null;
    GeoVector3 centroid = weighted_sum.times( 1.0 / weight_sum );
    double offset = -normal.dot( centroid );
    double objective = 0.0;
    for ( BeddingObservation observation : observations ) {
      double residual = normal.dot( observation.endpoint ) + offset;
      double variance = observation.normalVariance( normal, model.surfaceScatterMeters );
      objective += residual * residual / variance;
    }
    return Double.isFinite( objective )
      ? new Candidate( dip, direction, normal, centroid, offset, objective ) : null;
  }

  private static BeddingFitResult.AttitudeRegion makeRegion( List< Candidate > samples,
                                                              List< Candidate > basins,
                                                              Candidate best, double delta,
                                                              double coverage )
  {
    ArrayList< Double > directions = new ArrayList<>();
    double dip_min = Double.POSITIVE_INFINITY;
    double dip_max = Double.NEGATIVE_INFINITY;
    boolean horizontal = false;
    boolean vertical = false;
    for ( Candidate candidate : samples ) {
      if ( candidate.objective > best.objective + delta ) continue;
      dip_min = Math.min( dip_min, candidate.dip );
      dip_max = Math.max( dip_max, candidate.dip );
      if ( candidate.dip <= 0.05 ) horizontal = true;
      if ( candidate.dip >= 89.95 ) vertical = true;
      if ( candidate.dip > 0.05 && candidate.dip < 89.95 ) directions.add( candidate.direction );
    }
    if ( ! Double.isFinite( dip_min ) ) {
      return new BeddingFitResult.AttitudeRegion( coverage, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, false, BeddingFitResult.RegionStatus.UNAVAILABLE );
    }

    int basin_count = 0;
    ArrayList< Candidate > accepted_basins = new ArrayList<>();
    for ( Candidate candidate : basins ) {
      if ( candidate.objective > best.objective + delta ) continue;
      boolean distinct = true;
      for ( Candidate prior : accepted_basins ) {
        if ( planeAngleDegrees( candidate.normal, prior.normal ) < 4.0 ) { distinct = false; break; }
      }
      if ( distinct ) { accepted_basins.add( candidate ); ++basin_count; }
    }

    CircularInterval circular = circularInterval( directions );
    BeddingFitResult.RegionStatus status;
    if ( basin_count > 1 ) status = BeddingFitResult.RegionStatus.MULTIMODAL;
    else if ( horizontal || circular.span >= 350.0 ) status = BeddingFitResult.RegionStatus.DIRECTION_UNRESOLVED;
    else if ( vertical ) status = BeddingFitResult.RegionStatus.AXIAL_VERTICAL;
    else status = BeddingFitResult.RegionStatus.BOUNDED;
    double start = status == BeddingFitResult.RegionStatus.DIRECTION_UNRESOLVED
      ? Double.NaN : circular.start;
    double end = status == BeddingFitResult.RegionStatus.DIRECTION_UNRESOLVED
      ? Double.NaN : circular.end;
    return new BeddingFitResult.AttitudeRegion( coverage, dip_min, dip_max, start, end,
      circular.wraps, status );
  }

  private static void addRegionIssues( EnumSet< BeddingFitResult.Issue > issues,
                                       BeddingFitResult.AttitudeRegion region )
  {
    if ( region == null ) return;
    switch ( region.status ) {
      case DIRECTION_UNRESOLVED: issues.add( BeddingFitResult.Issue.DIRECTION_UNRESOLVED ); break;
      case AXIAL_VERTICAL: issues.add( BeddingFitResult.Issue.VERTICAL_AXIAL ); break;
      case MULTIMODAL: issues.add( BeddingFitResult.Issue.MULTIMODAL ); break;
      default: break;
    }
  }

  private static CircularInterval circularInterval( List< Double > values )
  {
    if ( values == null || values.isEmpty() ) {
      return new CircularInterval( Double.NaN, Double.NaN, false, 360.0 );
    }
    Collections.sort( values );
    double largest_gap = -1.0;
    int gap_after = 0;
    for ( int i = 0; i < values.size(); ++i ) {
      double current = values.get( i );
      double next = ( i + 1 < values.size() ) ? values.get( i + 1 ) : values.get( 0 ) + 360.0;
      double gap = next - current;
      if ( gap > largest_gap ) { largest_gap = gap; gap_after = i; }
    }
    double start = values.get( ( gap_after + 1 ) % values.size() );
    double end = values.get( gap_after );
    double span = 360.0 - largest_gap;
    return new CircularInterval( start, end, start > end, span );
  }

  private static double planeAngleDegrees( GeoVector3 a, GeoVector3 b )
  {
    double dot = Math.abs( a.dot( b ) );
    dot = Math.max( -1.0, Math.min( 1.0, dot ) );
    return Math.toDegrees( Math.acos( dot ) );
  }

  private static final class CircularInterval
  {
    final double start;
    final double end;
    final boolean wraps;
    final double span;

    CircularInterval( double interval_start, double interval_end, boolean interval_wraps,
                      double interval_span )
    {
      start = interval_start;
      end = interval_end;
      wraps = interval_wraps;
      span = interval_span;
    }
  }

  private static final class Candidate
  {
    static final Comparator< Candidate > BY_OBJECTIVE = new Comparator< Candidate >() {
      @Override public int compare( Candidate first, Candidate second )
      {
        return Double.compare( first.objective, second.objective );
      }
    };

    final double dip;
    final double direction;
    final GeoVector3 normal;
    final GeoVector3 centroid;
    final double offset;
    final double objective;

    Candidate( double candidate_dip, double candidate_direction, GeoVector3 candidate_normal,
               GeoVector3 candidate_centroid, double candidate_offset, double candidate_objective )
    {
      dip = candidate_dip;
      direction = candidate_direction;
      normal = candidate_normal;
      centroid = candidate_centroid;
      offset = candidate_offset;
      objective = candidate_objective;
    }
  }
}
