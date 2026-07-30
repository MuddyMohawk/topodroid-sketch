/* @file SketchFontRegistry.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief Stable font registry for Sketch text objects
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import android.content.Context;
import android.graphics.Typeface;

final class SketchFontRegistry
{
  static final String FONT_DEFAULT = "default";
  static final String FONT_ARCHITECTS_DAUGHTER = "architects-daughter";
  static final String FONT_SERIF = "serif";
  static final String FONT_MONOSPACE = "monospace";

  private static final String ARCHITECTS_ASSET = "fonts/ArchitectsDaughter-Regular.ttf";

  private static final String[] FONT_IDS = {
    FONT_DEFAULT,
    FONT_ARCHITECTS_DAUGHTER,
    FONT_SERIF,
    FONT_MONOSPACE
  };

  private static final String[] FONT_LABELS = {
    "TopoDroid Default",
    "Architects Daughter",
    "Serif",
    "Monospace"
  };

  private static volatile Typeface sArchitectsDaughter;
  private static volatile boolean sArchitectsLoadAttempted;
  private static volatile int sGeneration = 1;

  static final class ResolvedFont
  {
    final Typeface typeface;
    final boolean fakeBold;
    final float skewX;

    ResolvedFont( Typeface face, boolean fake_bold, float skew_x )
    {
      typeface = ( face == null ) ? Typeface.DEFAULT : face;
      fakeBold = fake_bold;
      skewX = skew_x;
    }
  }

  private SketchFontRegistry() { }

  static String normalizeFontId( String font_id )
  {
    if ( font_id == null || font_id.length() == 0 ) return FONT_DEFAULT;
    if ( "architects".equals( font_id ) ) return FONT_ARCHITECTS_DAUGHTER;
    if ( "mono".equals( font_id ) ) return FONT_MONOSPACE;
    for ( int i = 0; i < font_id.length(); ++i ) {
      char ch = font_id.charAt( i );
      if ( ! Character.isLetterOrDigit( ch ) && ch != '.' && ch != '_' && ch != '-' ) return FONT_DEFAULT;
    }
    return font_id;
  }

  static String[] fontLabels()
  {
    return FONT_LABELS.clone();
  }

  static String fontIdAt( int index )
  {
    return ( index >= 0 && index < FONT_IDS.length ) ? FONT_IDS[index] : FONT_DEFAULT;
  }

  static int indexOf( String font_id )
  {
    String normalized = normalizeFontId( font_id );
    for ( int i = 0; i < FONT_IDS.length; ++i ) {
      if ( FONT_IDS[i].equals( normalized ) ) return i;
    }
    return 0;
  }

  static int generation()
  {
    return sGeneration;
  }

  static ResolvedFont resolve( String font_id, boolean bold, boolean italic )
  {
    String normalized = normalizeFontId( font_id );
    if ( FONT_ARCHITECTS_DAUGHTER.equals( normalized ) ) {
      Typeface face = loadArchitectsDaughter();
      if ( face != null ) return new ResolvedFont( face, bold, italic ? -0.25f : 0.0f );
      normalized = FONT_DEFAULT;
    }

    Typeface base;
    if ( FONT_SERIF.equals( normalized ) ) {
      base = Typeface.SERIF;
    } else if ( FONT_MONOSPACE.equals( normalized ) ) {
      base = Typeface.MONOSPACE;
    } else {
      base = Typeface.DEFAULT;
    }
    int style = Typeface.NORMAL;
    if ( bold && italic ) style = Typeface.BOLD_ITALIC;
    else if ( bold ) style = Typeface.BOLD;
    else if ( italic ) style = Typeface.ITALIC;
    return new ResolvedFont( Typeface.create( base, style ), false, 0.0f );
  }

  private static Typeface loadArchitectsDaughter()
  {
    if ( sArchitectsLoadAttempted ) return sArchitectsDaughter;
    synchronized ( SketchFontRegistry.class ) {
      if ( sArchitectsLoadAttempted ) return sArchitectsDaughter;
      Context context = TDInstance.context;
      if ( context == null ) return null;
      try {
        sArchitectsDaughter = Typeface.createFromAsset( context.getAssets(), ARCHITECTS_ASSET );
        ++sGeneration;
      } catch ( RuntimeException e ) {
        TDLog.e( "Text font load failed: " + e.getMessage() );
      }
      sArchitectsLoadAttempted = true;
      return sArchitectsDaughter;
    }
  }
}
