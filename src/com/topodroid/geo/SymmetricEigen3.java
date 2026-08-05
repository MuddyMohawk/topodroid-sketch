/* @file SymmetricEigen3.java
 *
 * @brief Auditable Jacobi eigen-solver for a real symmetric 3x3 matrix
 */
package com.topodroid.geo;

final class SymmetricEigen3
{
  static final class Result
  {
    final boolean converged;
    final double[] values;
    final GeoVector3[] vectors;

    Result( boolean ok, double[] eigenvalues, GeoVector3[] eigenvectors )
    {
      converged = ok;
      values = eigenvalues;
      vectors = eigenvectors;
    }
  }

  private SymmetricEigen3() { }

  static Result solve( double[][] input )
  {
    if ( input == null || input.length != 3 ) return failure();
    double scale = 0.0;
    for ( int i = 0; i < 3; ++i ) {
      if ( input[i] == null || input[i].length != 3 ) return failure();
      for ( int j = 0; j < 3; ++j ) {
        if ( ! Double.isFinite( input[i][j] ) ) return failure();
        scale = Math.max( scale, Math.abs( input[i][j] ) );
      }
    }
    for ( int i = 0; i < 3; ++i ) {
      for ( int j = i + 1; j < 3; ++j ) {
        if ( Math.abs( input[i][j] - input[j][i] )
            > 1.0e-12 * Math.max( 1.0, scale ) ) return failure();
      }
    }
    if ( scale == 0.0 ) {
      return new Result( true, new double[] { 0.0, 0.0, 0.0 },
        new GeoVector3[] { new GeoVector3( 1, 0, 0 ), new GeoVector3( 0, 1, 0 ),
                           new GeoVector3( 0, 0, 1 ) } );
    }

    double[][] a = new double[3][3];
    double[][] v = new double[][] { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } };
    for ( int i = 0; i < 3; ++i ) {
      for ( int j = 0; j < 3; ++j ) a[i][j] = input[i][j] / scale;
    }

    boolean converged = false;
    for ( int sweep = 0; sweep < 64; ++sweep ) {
      int p = 0;
      int q = 1;
      double largest = Math.abs( a[0][1] );
      if ( Math.abs( a[0][2] ) > largest ) { p = 0; q = 2; largest = Math.abs( a[0][2] ); }
      if ( Math.abs( a[1][2] ) > largest ) { p = 1; q = 2; largest = Math.abs( a[1][2] ); }
      double diagonal = Math.abs( a[0][0] ) + Math.abs( a[1][1] ) + Math.abs( a[2][2] );
      if ( largest <= 1.0e-14 * Math.max( 1.0, diagonal ) ) {
        converged = true;
        break;
      }

      double phi = 0.5 * Math.atan2( 2.0 * a[p][q], a[q][q] - a[p][p] );
      double c = Math.cos( phi );
      double s = Math.sin( phi );
      double app = a[p][p];
      double aqq = a[q][q];
      double apq = a[p][q];
      a[p][p] = c * c * app - 2.0 * s * c * apq + s * s * aqq;
      a[q][q] = s * s * app + 2.0 * s * c * apq + c * c * aqq;
      a[p][q] = a[q][p] = 0.0;
      for ( int r = 0; r < 3; ++r ) {
        if ( r == p || r == q ) continue;
        double arp = a[r][p];
        double arq = a[r][q];
        a[r][p] = a[p][r] = c * arp - s * arq;
        a[r][q] = a[q][r] = s * arp + c * arq;
      }
      for ( int r = 0; r < 3; ++r ) {
        double vrp = v[r][p];
        double vrq = v[r][q];
        v[r][p] = c * vrp - s * vrq;
        v[r][q] = s * vrp + c * vrq;
      }
    }
    if ( ! converged ) return failure();

    double[] values = new double[] { a[0][0] * scale, a[1][1] * scale, a[2][2] * scale };
    GeoVector3[] vectors = new GeoVector3[] {
      new GeoVector3( v[0][0], v[1][0], v[2][0] ).normalized(),
      new GeoVector3( v[0][1], v[1][1], v[2][1] ).normalized(),
      new GeoVector3( v[0][2], v[1][2], v[2][2] ).normalized()
    };
    for ( int i = 0; i < 2; ++i ) {
      for ( int j = i + 1; j < 3; ++j ) {
        if ( values[j] < values[i] ) {
          double value = values[i]; values[i] = values[j]; values[j] = value;
          GeoVector3 vector = vectors[i]; vectors[i] = vectors[j]; vectors[j] = vector;
        }
      }
    }
    double tolerance = 1.0e-10 * Math.max( 1.0, scale );
    for ( int i = 0; i < 3; ++i ) {
      if ( vectors[i] == null || Math.abs( vectors[i].norm() - 1.0 ) > 1.0e-10 ) return failure();
      GeoVector3 product = multiply( input, vectors[i] );
      if ( product.minus( vectors[i].times( values[i] ) ).norm() > tolerance ) return failure();
      for ( int j = i + 1; j < 3; ++j ) {
        if ( Math.abs( vectors[i].dot( vectors[j] ) ) > 1.0e-10 ) return failure();
      }
    }
    return new Result( true, values, vectors );
  }

  private static GeoVector3 multiply( double[][] matrix, GeoVector3 vector )
  {
    return new GeoVector3(
      matrix[0][0] * vector.east + matrix[0][1] * vector.north + matrix[0][2] * vector.up,
      matrix[1][0] * vector.east + matrix[1][1] * vector.north + matrix[1][2] * vector.up,
      matrix[2][0] * vector.east + matrix[2][1] * vector.north + matrix[2][2] * vector.up );
  }

  private static Result failure()
  {
    return new Result( false, new double[] { Double.NaN, Double.NaN, Double.NaN },
      new GeoVector3[] { null, null, null } );
  }
}
