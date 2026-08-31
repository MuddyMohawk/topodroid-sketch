/* @file ToolsetCategory.java
 *
 * Stable picker category and section identifiers. Symbol files own membership;
 * this class owns presentation order and labels.
 */
package com.topodroid.TDX;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ToolsetCategory
{
  static final String PASSAGES = "passages";
  static final String SPELEOTHEMS = "speleothems";
  static final String SPELEOCLASTS = "speleoclasts";
  static final String HYDROLOGY = "hydrology";
  static final String GEOLOGY = "geology";
  static final String BIOLOGY = "biology";
  static final String ARCHAEO = "archaeo";
  static final String EXTRAS = "extras";
  static final String TEXT_MARKS = "text-marks";

  static final String SECTION_FORMATIONS = "formations";
  static final String SECTION_MINERAL_POOL = "mineral-pool";
  static final String SECTION_GYPSUM = "gypsum";

  private static final List< String > ORDER = Collections.unmodifiableList( Arrays.asList(
    PASSAGES, SPELEOTHEMS, SPELEOCLASTS, HYDROLOGY, GEOLOGY,
    BIOLOGY, ARCHAEO, EXTRAS, TEXT_MARKS
  ) );

  private ToolsetCategory() { }

  static List< String > orderedIds() { return ORDER; }

  static String normalizeId( String id )
  {
    if ( id == null ) return EXTRAS;
    String value = id.trim().toLowerCase();
    return ORDER.contains( value ) ? value : EXTRAS;
  }

  static String label( String id )
  {
    switch ( normalizeId( id ) ) {
      case PASSAGES: return "Passages";
      case SPELEOTHEMS: return "Speleothems";
      case SPELEOCLASTS: return "Speleoclasts";
      case HYDROLOGY: return "Hydrology";
      case GEOLOGY: return "Geology";
      case BIOLOGY: return "Biology";
      case ARCHAEO: return "Archaeo";
      case TEXT_MARKS: return "Text & marks";
      default: return "Extras";
    }
  }

  static String sectionLabel( String id )
  {
    if ( SECTION_FORMATIONS.equals( id ) ) return "Formations";
    if ( SECTION_MINERAL_POOL.equals( id ) ) return "Mineral & pool deposits";
    if ( SECTION_GYPSUM.equals( id ) ) return "Gypsum";
    return null;
  }
}
