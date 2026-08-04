/* @file DrawingPointFactory.java
 *
 * @brief Single construction path for ordinary, reference, and survey-aware points
 */
package com.topodroid.TDX;

final class DrawingPointFactory
{
  private DrawingPointFactory() { }

  static DrawingPointPath createPlacement( int type, float x, float y, int scale, int scrap )
  {
    String therion_name = BrushManager.getPointThName( type );
    if ( BrushManager.isPointReference( type ) ) {
      return new DrawingReferencePath( type, x, y, scale, null,
                                       BrushManager.getPointDefaultOptions( type ), scrap );
    }
    SpecialPointBehavior behavior = SpecialPointRegistry.forTherionName( therion_name );
    if ( behavior != null ) {
      return new DrawingSemanticPointPath( type, therion_name, behavior, x, y, scale, null,
                                           BrushManager.getPointDefaultOptions( type ), scrap );
    }
    return new DrawingPointPath( type, x, y, scale, scrap );
  }

  static DrawingPointPath createLoaded( int type, String therion_name, float x, float y, int scale,
                                        String text, String options, int scrap )
  {
    return create( type, therion_name, x, y, scale, text, options, scrap );
  }

  static DrawingPointPath createPreview( int type, float x, float y, int scale, int scrap )
  {
    String therion_name = BrushManager.getPointThName( type );
    SpecialPointBehavior behavior = SpecialPointRegistry.forTherionName( therion_name );
    if ( behavior != null ) {
      DrawingSemanticPointPath point = new DrawingSemanticPointPath( type, therion_name, behavior,
          x, y, scale, null, BrushManager.getPointDefaultOptions( type ), scrap );
      point.preparePreview();
      return point;
    }

    // Preview symbol artwork even when the runtime path, such as reference-image, renders
    // external content rather than the point glyph itself.
    return new DrawingPointPath( type, x, y, scale, null,
                                 BrushManager.getPointDefaultOptions( type ), scrap );
  }

  private static DrawingPointPath create( int type, String therion_name, float x, float y, int scale,
                                          String text, String options, int scrap )
  {
    if ( BrushManager.isPointReference( type ) ) {
      return new DrawingReferencePath( type, x, y, scale, text, options, scrap );
    }
    SpecialPointBehavior behavior = SpecialPointRegistry.forTherionName( therion_name );
    if ( behavior != null ) {
      return new DrawingSemanticPointPath( type, therion_name, behavior, x, y, scale, text, options, scrap );
    }
    return new DrawingPointPath( type, x, y, scale, text, options, scrap );
  }
}
