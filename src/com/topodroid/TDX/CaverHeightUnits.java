/* @file CaverHeightUnits.java
 *
 * @brief Exact metric and imperial conversions for the caver-height editor
 */
package com.topodroid.TDX;

final class CaverHeightUnits
{
  static final double METERS_PER_INCH = 0.0254;
  static final int INCHES_PER_FOOT = 12;

  static final class FeetInches
  {
    final int feet;
    final double inches;

    FeetInches( int feet, double inches )
    {
      this.feet = feet;
      this.inches = inches;
    }
  }

  private CaverHeightUnits() { }

  static FeetInches fromMeters( double meters )
  {
    double total_inches = Math.max( 0.0, meters / METERS_PER_INCH );
    int feet = (int)Math.floor( total_inches / INCHES_PER_FOOT + 1.0e-9 );
    double inches = total_inches - feet * INCHES_PER_FOOT;
    if ( inches >= INCHES_PER_FOOT - 1.0e-8 ) {
      ++ feet;
      inches = 0.0;
    } else if ( Math.abs( inches ) < 1.0e-8 ) {
      inches = 0.0;
    }
    return new FeetInches( feet, inches );
  }

  static double toMeters( int feet, double inches )
  {
    return ( feet * INCHES_PER_FOOT + inches ) * METERS_PER_INCH;
  }
}
