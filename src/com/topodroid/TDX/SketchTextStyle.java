/* @file SketchTextStyle.java
 *
 * @author MuddyMohawk
 * @date jul 2026
 *
 * @brief TopoDroid Sketch text-object style metadata
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SketchTextStyle
{
  public enum SizeMode {
    AUTO_GRID,
    WORLD,
    SCREEN
  }

  public enum Alignment {
    LEFT,
    CENTER,
    RIGHT
  }

  static final int CODEC_VERSION = 1;
  public static final float DEFAULT_AUTO_GRID_MULTIPLIER = 1.0f;
  public static final float DEFAULT_SCREEN_HEIGHT = 24.0f;
  public static final float DEFAULT_LINE_WEIGHT = SketchBrushStyle.DEFAULT_WEIGHT_STANDARD;
  public static final int DEFAULT_COLOR = 0xffffffff;

  private static final float MIN_AUTO_MULTIPLIER = 0.05f;
  private static final float MAX_AUTO_MULTIPLIER = 20.0f;
  private static final float MIN_WORLD_HEIGHT_M = 0.005f;
  private static final float MAX_WORLD_HEIGHT_M = 100.0f;
  private static final float MIN_SCREEN_HEIGHT = 2.0f;
  private static final float MAX_SCREEN_HEIGHT = 512.0f;

  private final int mCodecVersion;
  private final String mFontId;
  private final SizeMode mSizeMode;
  private final float mHeight;
  private final float mLineWeight;
  private final boolean mBold;
  private final boolean mItalic;
  private final boolean mUnderline;
  private final Alignment mAlignment;
  private final int mColor;
  private final Map< String, String > mUnknownFields;

  private SketchTextStyle( int codec_version, String font_id, SizeMode size_mode, float height,
                           float line_weight,
                           boolean bold, boolean italic, boolean underline,
                           Alignment alignment, int color, Map< String, String > unknown_fields )
  {
    mCodecVersion = Math.max( 1, codec_version );
    mFontId = SketchFontRegistry.normalizeFontId( font_id );
    mSizeMode = ( size_mode == null ) ? SizeMode.AUTO_GRID : size_mode;
    mHeight = normalizeHeight( mSizeMode, height );
    mLineWeight = normalizeLineWeight( line_weight );
    mBold = bold;
    mItalic = italic;
    mUnderline = underline;
    mAlignment = ( alignment == null ) ? Alignment.LEFT : alignment;
    mColor = color;
    if ( unknown_fields == null || unknown_fields.isEmpty() ) {
      mUnknownFields = Collections.emptyMap();
    } else {
      mUnknownFields = Collections.unmodifiableMap( new LinkedHashMap<>( unknown_fields ) );
    }
  }

  public static SketchTextStyle defaultStyle()
  {
    return of( SketchFontRegistry.FONT_DEFAULT, SizeMode.AUTO_GRID, DEFAULT_AUTO_GRID_MULTIPLIER,
               false, false, false, Alignment.LEFT, DEFAULT_COLOR );
  }

  public static SketchTextStyle of( String font_id, SizeMode size_mode, float height,
                                    boolean bold, boolean italic, boolean underline,
                                    Alignment alignment, int color )
  {
    return new SketchTextStyle( CODEC_VERSION, font_id, size_mode, height, DEFAULT_LINE_WEIGHT,
                                bold, italic, underline, alignment, color, null );
  }

  public static SketchTextStyle of( String font_id, SizeMode size_mode, float height,
                                    float line_weight,
                                    boolean bold, boolean italic, boolean underline,
                                    Alignment alignment, int color )
  {
    return new SketchTextStyle( CODEC_VERSION, font_id, size_mode, height, line_weight,
                                bold, italic, underline, alignment, color, null );
  }

  static SketchTextStyle decoded( int codec_version, String font_id, SizeMode size_mode, float height,
                                  float line_weight,
                                  boolean bold, boolean italic, boolean underline,
                                  Alignment alignment, int color,
                                  Map< String, String > unknown_fields )
  {
    return new SketchTextStyle( codec_version, font_id, size_mode, height, line_weight,
                                bold, italic, underline, alignment, color, unknown_fields );
  }

  int codecVersion() { return mCodecVersion; }
  public String fontId() { return mFontId; }
  public SizeMode sizeMode() { return mSizeMode; }
  public float height() { return mHeight; }
  public float lineWeight() { return mLineWeight; }
  public boolean bold() { return mBold; }
  public boolean italic() { return mItalic; }
  public boolean underline() { return mUnderline; }
  public Alignment alignment() { return mAlignment; }
  public int color() { return mColor; }
  Map< String, String > unknownFields() { return mUnknownFields; }

  public float opacity()
  {
    return ( ( mColor >>> 24 ) & 0xff ) / 255.0f;
  }

  public SketchTextStyle withFontId( String font_id )
  {
    return copy( font_id, mSizeMode, mHeight, mLineWeight,
                 mBold, mItalic, mUnderline, mAlignment, mColor );
  }

  public SketchTextStyle withSize( SizeMode size_mode, float height )
  {
    return copy( mFontId, size_mode, height, mLineWeight,
                 mBold, mItalic, mUnderline, mAlignment, mColor );
  }

  public SketchTextStyle withLineWeight( float line_weight )
  {
    return copy( mFontId, mSizeMode, mHeight, line_weight,
                 mBold, mItalic, mUnderline, mAlignment, mColor );
  }

  public SketchTextStyle withEmphasis( boolean bold, boolean italic, boolean underline )
  {
    return copy( mFontId, mSizeMode, mHeight, mLineWeight,
                 bold, italic, underline, mAlignment, mColor );
  }

  public SketchTextStyle withAlignment( Alignment alignment )
  {
    return copy( mFontId, mSizeMode, mHeight, mLineWeight,
                 mBold, mItalic, mUnderline, alignment, mColor );
  }

  public SketchTextStyle withColor( int color )
  {
    return copy( mFontId, mSizeMode, mHeight, mLineWeight,
                 mBold, mItalic, mUnderline, mAlignment, color );
  }

  private SketchTextStyle copy( String font_id, SizeMode size_mode, float height, float line_weight,
                                boolean bold, boolean italic, boolean underline,
                                Alignment alignment, int color )
  {
    return new SketchTextStyle( mCodecVersion, font_id, size_mode, height, line_weight,
                                bold, italic, underline, alignment, color, mUnknownFields );
  }

  public boolean formattingEquals( SketchTextStyle other )
  {
    if ( other == null ) return false;
    return mFontId.equals( other.mFontId )
        && mSizeMode == other.mSizeMode
        && Float.compare( mHeight, other.mHeight ) == 0
        && Float.compare( mLineWeight, other.mLineWeight ) == 0
        && mBold == other.mBold
        && mItalic == other.mItalic
        && mUnderline == other.mUnderline
        && mAlignment == other.mAlignment
        && mColor == other.mColor;
  }

  @Override
  public boolean equals( Object object )
  {
    if ( this == object ) return true;
    if ( ! ( object instanceof SketchTextStyle ) ) return false;
    SketchTextStyle other = (SketchTextStyle)object;
    return mCodecVersion == other.mCodecVersion
        && formattingEquals( other )
        && mUnknownFields.equals( other.mUnknownFields );
  }

  @Override
  public int hashCode()
  {
    int result = mCodecVersion;
    result = 31 * result + mFontId.hashCode();
    result = 31 * result + mSizeMode.hashCode();
    result = 31 * result + Float.floatToIntBits( mHeight );
    result = 31 * result + Float.floatToIntBits( mLineWeight );
    result = 31 * result + ( mBold ? 1 : 0 );
    result = 31 * result + ( mItalic ? 1 : 0 );
    result = 31 * result + ( mUnderline ? 1 : 0 );
    result = 31 * result + mAlignment.hashCode();
    result = 31 * result + mColor;
    result = 31 * result + mUnknownFields.hashCode();
    return result;
  }

  private static float normalizeHeight( SizeMode mode, float height )
  {
    float fallback = ( mode == SizeMode.SCREEN ) ? DEFAULT_SCREEN_HEIGHT : DEFAULT_AUTO_GRID_MULTIPLIER;
    if ( Float.isNaN( height ) || Float.isInfinite( height ) || height <= 0.0f ) height = fallback;
    if ( mode == SizeMode.AUTO_GRID ) return clamp( height, MIN_AUTO_MULTIPLIER, MAX_AUTO_MULTIPLIER );
    if ( mode == SizeMode.WORLD ) return clamp( height, MIN_WORLD_HEIGHT_M, MAX_WORLD_HEIGHT_M );
    return clamp( height, MIN_SCREEN_HEIGHT, MAX_SCREEN_HEIGHT );
  }

  private static float normalizeLineWeight( float line_weight )
  {
    return ( line_weight > 0.0f && ! Float.isNaN( line_weight ) && ! Float.isInfinite( line_weight ) )
      ? line_weight
      : DEFAULT_LINE_WEIGHT;
  }

  private static float clamp( float value, float minimum, float maximum )
  {
    return Math.max( minimum, Math.min( maximum, value ) );
  }
}
