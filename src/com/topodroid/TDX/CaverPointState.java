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
    WOMAN( "woman" );

    final String persistedName;

    Variant( String persisted_name ) { persistedName = persisted_name; }

    static Variant fromPersistedName( String value )
    {
      return WOMAN.persistedName.equalsIgnoreCase( value ) ? WOMAN : MAN;
    }
  }

  static final double DEFAULT_HEIGHT_METERS = 1.778;

  final Variant variant;
  final double heightMeters;

  CaverPointState( Variant variant, double height_meters )
  {
    this.variant = variant == null ? Variant.MAN : variant;
    this.heightMeters = Double.isFinite( height_meters ) && height_meters > 0.0
      ? height_meters : DEFAULT_HEIGHT_METERS;
  }

  static CaverPointState defaultState()
  {
    return new CaverPointState( Variant.MAN, DEFAULT_HEIGHT_METERS );
  }
}
