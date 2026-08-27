/* @file CaverPointState.java
 *
 * @brief Immutable state for the scale-locked caver special point
 */
package com.topodroid.TDX;

final class CaverPointState implements SpecialPointState
{
  enum Variant
  {
    MAN( "man" ),
    WOMAN( "woman" ),
    BANANA_SLUG( "banana-slug" );

    final String persistedName;

    Variant( String persisted_name ) { persistedName = persisted_name; }

    static Variant fromPersistedName( String value )
    {
      for ( Variant variant : values() ) {
        if ( variant.persistedName.equalsIgnoreCase( value ) ) return variant;
      }
      return MAN;
    }
  }

  static final double DEFAULT_HEIGHT_METERS = 1.778;
  static final double BANANA_SLUG_DEFAULT_HEIGHT_METERS = 0.9144; // exactly 3 ft

  final Variant variant;
  final double heightMeters;

  CaverPointState( Variant variant, double height_meters )
  {
    this.variant = variant == null ? Variant.MAN : variant;
    this.heightMeters = Double.isFinite( height_meters ) && height_meters > 0.0
      ? height_meters : defaultHeightMeters( this.variant );
  }

  static CaverPointState defaultState()
  {
    return new CaverPointState( Variant.MAN, DEFAULT_HEIGHT_METERS );
  }

  static double defaultHeightMeters( Variant variant )
  {
    return variant == Variant.BANANA_SLUG ? BANANA_SLUG_DEFAULT_HEIGHT_METERS
                                         : DEFAULT_HEIGHT_METERS;
  }
}
