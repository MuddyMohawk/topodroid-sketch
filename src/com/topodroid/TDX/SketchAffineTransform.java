/* @file SketchAffineTransform.java
 *
 * @author MuddyMohawk
 * @date aug 2026
 *
 * @brief Validated relative 2x2 transform for Sketch point symbols
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import android.graphics.Matrix;

final class SketchAffineTransform
{
  static final float MIN_AXIS_SCALE = 0.02f;
  static final float MIN_DETERMINANT = 0.0001f;

  final float m00;
  final float m01;
  final float m10;
  final float m11;

  private SketchAffineTransform( float a, float b, float c, float d )
  {
    m00 = a;
    m01 = b;
    m10 = c;
    m11 = d;
  }

  static SketchAffineTransform identity()
  {
    return new SketchAffineTransform( 1.0f, 0.0f, 0.0f, 1.0f );
  }

  static SketchAffineTransform rotationScale( float degrees, float scale )
  {
    if ( ! Float.isFinite( degrees ) || ! Float.isFinite( scale ) || scale < MIN_AXIS_SCALE ) return null;
    double radians = Math.toRadians( degrees );
    float cosine = (float)Math.cos( radians ) * scale;
    float sine = (float)Math.sin( radians ) * scale;
    return create( cosine, -sine, sine, cosine );
  }

  static SketchAffineTransform create( float a, float b, float c, float d )
  {
    if ( ! Float.isFinite( a ) || ! Float.isFinite( b ) || ! Float.isFinite( c ) || ! Float.isFinite( d ) ) return null;
    float det = a * d - b * c;
    if ( ! Float.isFinite( det ) || det < MIN_DETERMINANT ) return null;
    float column0 = (float)Math.hypot( a, c );
    float column1 = (float)Math.hypot( b, d );
    if ( column0 < MIN_AXIS_SCALE || column1 < MIN_AXIS_SCALE ) return null;
    return new SketchAffineTransform( a, b, c, d );
  }

  float determinant() { return m00 * m11 - m01 * m10; }

  float closestRotationDegrees()
  {
    return (float)Math.toDegrees( Math.atan2( m10 - m01, m00 + m11 ) );
  }

  float closestUniformScale()
  {
    return 0.5f * (float)Math.hypot( m00 + m11, m10 - m01 );
  }

  Matrix matrix( float footprintScale )
  {
    Matrix matrix = new Matrix();
    matrix.setValues( new float[] {
      footprintScale * m00, footprintScale * m01, 0.0f,
      footprintScale * m10, footprintScale * m11, 0.0f,
      0.0f, 0.0f, 1.0f
    } );
    return matrix;
  }

  SketchAffineTransform leftMultiply( float a, float b, float c, float d )
  {
    return create( a * m00 + b * m10, a * m01 + b * m11,
                   c * m00 + d * m10, c * m01 + d * m11 );
  }

  SketchAffineTransform rightMultiply( float a, float b, float c, float d )
  {
    return create( m00 * a + m01 * c, m00 * b + m01 * d,
                   m10 * a + m11 * c, m10 * b + m11 * d );
  }

  SketchAffineTransform rotateBy( float degrees )
  {
    double radians = Math.toRadians( degrees );
    float cosine = (float)Math.cos( radians );
    float sine = (float)Math.sin( radians );
    return leftMultiply( cosine, -sine, sine, cosine );
  }

  SketchAffineTransform scaleBy( float factor )
  {
    return leftMultiply( factor, 0.0f, 0.0f, factor );
  }
}
