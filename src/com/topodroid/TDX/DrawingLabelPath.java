/* @file DrawingLabelPath.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid drawing: label-point
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDMath;
import com.topodroid.util.TDLog;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PointScale;


import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Matrix;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

public class DrawingLabelPath extends DrawingPointPath
{
  private volatile SketchTextLayoutSnapshot mTextLayout;
  private volatile SketchTextStyle mTextStyle;

  /** cstr
   * @param text     label text
   * @param off_x    X coord
   * @param off_y    Y coord
   * @param scale    text scale (Therion point scale)
   * @param options  additional Therion point options
   * @param scrap    point scrap index
   */
  public DrawingLabelPath( String text, float off_x, float off_y, int scale, String options, int scrap ) // TH2EDIT package
  {
    super( BrushManager.getPointLabelIndex(), off_x, off_y, scale, text, options, scrap );
    // Labels render through SketchTextRenderer rather than a point-symbol path.
    // Keep an empty path so inherited point movement/transform operations remain safe.
    if ( mPath == null ) mPath = new Path();
    mTextStyle = SketchTextStyleCodec.fromOptions( options );
    refreshPaint();
    updateSceneBounds( 1.0f );
  }

  DrawingLabelPath( String text, float off_x, float off_y, int scale, String options, int scrap,
                    SketchTextStyle style )
  {
    this( text, off_x, off_y, scale, options, scrap );
    setTextStyle( ( style == null ) ? SketchTextStyle.defaultStyle() : style );
  }

  /** deserialize from an input stream
   * @param version   stream version
   * @param dis       input stream
   * @param x         offset X coord [scene ?]
   * @param y         offset Y coord
   */
  public static DrawingLabelPath loadDataStream( int version, DataInputStream dis, float x, float y )
  {
    float ccx, ccy;
    int scale;
    int level = DrawingLevel.LEVEL_DEFAULT;
    int scrap = 0;
    float orientation = 0;
    // int type;
    String text, options;
    try {
      ccx = x + dis.readFloat( );
      ccy = y + dis.readFloat( );
      // String th_name = dis.readUTF( );
      // type = BrushManager.getPointLabelIndex();
      // if ( version > 401147 ) group = dis.readUTF() // label has null group and is guaranteed to exist
      if ( version > 207043 ) orientation = dis.readFloat( );
      scale = dis.readInt( );
      if ( version > 401090 ) level = dis.readInt();
      if ( version > 401160 ) scrap = dis.readInt();
      text = dis.readUTF();
      options = dis.readUTF();

      // TDLog.Log( TDLog.LOG_PLOT, "Label <" + text + " " + ccx + " " + ccy + " scale " + scale + " (" + options + ")" );
      // TDLog.v( "Label <" + text + "> " + ccx + " " + ccy + " scale " + scale + " (" + options + ")" );

      DrawingLabelPath ret = new DrawingLabelPath( text, ccx, ccy, scale, options, scrap );
      ret.mLevel = level;
      ret.setOrientation( orientation );
      return ret;
    } catch ( IOException e ) {
      TDLog.e( "LABEL in error " + e.getMessage() );
      // TDLog.v( "LABEL in error " + e.getMessage() );
    }
    return null;
  }

  // static void globDataStream( int version, DataInputStream dis )
  // {
  //   try {
  //     dis.readFloat( );
  //     dis.readFloat( );
  //     if ( version > 207043 ) dis.readFloat( );
  //     dis.readInt( );
  //     if ( version > 401090 ) dis.readInt();
  //     if ( version > 401160 ) dis.readInt();
  //     dis.readUTF();
  //     dis.readUTF();
  //   } catch ( IOException e ) {
  //     TDLog.e( "LABEL in error " + e.getMessage() );
  //     // TDLog.v( "LABEL in error " + e.getMessage() );
  //   }
  // }

  /** draw the label on the screen
   * @param canvas   canvas
   * @param bbox     clipping rectangle
   */
  @Override
  public void draw( Canvas canvas, RectF bbox )
  {
    Matrix identity = new Matrix();
    drawText( canvas, identity, 1.0f, bbox, 0 );
  }

  /** draw the label on the screen
   * @param canvas   canvas
   * @param matrix   transform matrix
   * @param scale    scaling factor, (smaller = zoom-in, larger = zoom_out) - not-used
   * @param bbox     clipping rectangle
   * @note scale is not used but this signature is necessary because DrawingLabelPath extends DrawingPointPath
   */
  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox )
  {
    drawText( canvas, matrix, scale, bbox, 0 );
  }

  /** draw the label on the screen
   * @param canvas    canvas
   * @param matrix    transform matrix
   * @param scale     scaling factor - not used
   * @param bbox      clipping rectangle
   * @param xor_color xor color
   * @note scale is not used but this signature is necessary because DrawingLabelPath extends DrawingPointPath
   * @note text size is reduced by 25 %
   */
  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
  {
    drawText( canvas, matrix, scale, bbox, xor_color );
  }

  private void drawText( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
  {
    if ( canvas == null || matrix == null ) return;
    String text = mPointText;
    SketchTextStyle explicit_style = mTextStyle;
    SketchTextStyle style = ( explicit_style == null ) ? legacyStyleForEditor( text ) : explicit_style;
    SketchTextLayoutSnapshot layout = layoutFor( text, style );
    RectF scene_bounds = SketchTextRenderer.rotatedBounds(
      cx, cy, effectiveOrientation(),
      SketchTextRenderer.localBounds(
        layout, lineHeightScene( scale, layout, style, explicit_style != null ) ) );
    if ( bbox != null && ! RectF.intersects( scene_bounds, bbox ) ) return;
    float line_height = lineHeightPixels( matrix, scale, layout, style, explicit_style != null );
    float orientation = effectiveOrientation();
    SketchTextRenderer.drawScene( canvas, matrix, cx, cy, orientation,
                                  style, layout, line_height, xor_color );
  }

  private SketchTextLayoutSnapshot layoutFor( String text, SketchTextStyle style )
  {
    SketchTextLayoutSnapshot snapshot = mTextLayout;
    if ( snapshot != null && snapshot.matches( text, style ) ) return snapshot;
    snapshot = SketchTextLayoutSnapshot.create( text, style );
    mTextLayout = snapshot;
    return snapshot;
  }

  private SketchTextStyle renderStyle()
  {
    SketchTextStyle style = mTextStyle;
    return ( style == null ) ? legacyStyleForEditor( mPointText ) : style;
  }

  private float lineHeightPixels( Matrix matrix, float scale, SketchTextLayoutSnapshot layout,
                                  SketchTextStyle style, boolean explicit )
  {
    if ( ! explicit ) {
      float paint_size = TDSetting.mLabelSize * pointScaleFactor();
      if ( TDSetting.mScalableLabel && ! DrawingCommandManager.sExportUnscaledLabels.get() ) {
        paint_size = TDSetting.mLabelSize * 0.1f * pointScaleFactor() / safeScale( scale );
      }
      return paint_size / layout.paintSizePerLineHeight;
    }
    if ( style.sizeMode() == SketchTextStyle.SizeMode.SCREEN ) {
      float export_scale = DrawingCommandManager.getExportTextScale();
      return style.height() * TopoDroidApp.getDisplayDensity() * export_scale;
    }
    float scene_height = worldLineHeightScene( style );
    return scene_height * matrixScale( matrix );
  }

  float lineHeightScene( float scale )
  {
    String text = mPointText;
    SketchTextStyle explicit_style = mTextStyle;
    SketchTextStyle style = ( explicit_style == null ) ? legacyStyleForEditor( text ) : explicit_style;
    SketchTextLayoutSnapshot layout = layoutFor( text, style );
    return lineHeightScene( scale, layout, style, explicit_style != null );
  }

  private float lineHeightScene( float scale, SketchTextLayoutSnapshot layout,
                                 SketchTextStyle style, boolean explicit )
  {
    if ( ! explicit ) {
      float paint_size = TDSetting.mLabelSize * pointScaleFactor();
      if ( TDSetting.mScalableLabel && ! DrawingCommandManager.sExportUnscaledLabels.get() ) {
        paint_size = TDSetting.mLabelSize * 0.1f * pointScaleFactor() / safeScale( scale );
      }
      return paint_size * safeScale( scale ) / layout.paintSizePerLineHeight;
    }
    if ( style.sizeMode() == SketchTextStyle.SizeMode.SCREEN ) {
      return style.height() * TopoDroidApp.getDisplayDensity()
          * DrawingCommandManager.getExportTextScale() * safeScale( scale );
    }
    return worldLineHeightScene( style );
  }

  RectF textBoundsScene( float scale )
  {
    String text = mPointText;
    SketchTextStyle explicit_style = mTextStyle;
    SketchTextStyle style = ( explicit_style == null ) ? legacyStyleForEditor( text ) : explicit_style;
    SketchTextLayoutSnapshot layout = layoutFor( text, style );
    RectF local = SketchTextRenderer.localBounds(
      layout, lineHeightScene( scale, layout, style, explicit_style != null ) );
    return SketchTextRenderer.rotatedBounds( cx, cy, effectiveOrientation(), local );
  }

  boolean hitText( float x, float y, float scale, float touch_slop )
  {
    String text = mPointText;
    SketchTextStyle explicit_style = mTextStyle;
    SketchTextStyle style = ( explicit_style == null ) ? legacyStyleForEditor( text ) : explicit_style;
    SketchTextLayoutSnapshot layout = layoutFor( text, style );
    RectF local = SketchTextRenderer.localBounds(
      layout, lineHeightScene( scale, layout, style, explicit_style != null ) );
    return SketchTextRenderer.hitTest( x, y, cx, cy, effectiveOrientation(), local, touch_slop );
  }

  Path textBoundsPath( float scale )
  {
    String text = mPointText;
    SketchTextStyle explicit_style = mTextStyle;
    SketchTextStyle style = ( explicit_style == null ) ? legacyStyleForEditor( text ) : explicit_style;
    SketchTextLayoutSnapshot layout = layoutFor( text, style );
    RectF local = SketchTextRenderer.localBounds(
      layout, lineHeightScene( scale, layout, style, explicit_style != null ) );
    Path path = new Path();
    path.addRect( local, Path.Direction.CW );
    Matrix transform = new Matrix();
    transform.postRotate( effectiveOrientation() );
    transform.postTranslate( cx, cy );
    path.transform( transform );
    return path;
  }

  private float effectiveOrientation()
  {
    return (float)mOrientation + ( mLandscape ? 90.0f : 0.0f );
  }

  private void updateSceneBounds( float scale )
  {
    RectF bounds = textBoundsScene( scale );
    setBBox( bounds.left, bounds.right, bounds.top, bounds.bottom );
  }

  @Override
  public void computeBounds( RectF bounds, boolean exact )
  {
    RectF text_bounds = textBoundsScene( 1.0f );
    bounds.set( text_bounds );
  }

  private static float worldLineHeightScene( SketchTextStyle style )
  {
    if ( style == null ) return 0.0f;
    float metres = ( style.sizeMode() == SketchTextStyle.SizeMode.AUTO_GRID )
      ? TDSetting.mUnitGrid * style.height()
      : style.height();
    return metres * DrawingUtil.SCALE_FIX;
  }

  private float pointScaleFactor()
  {
    switch ( mScale ) {
      case PointScale.SCALE_XS: return 0.50f;
      case PointScale.SCALE_S:  return 0.72f;
      case PointScale.SCALE_L:  return 1.41f;
      case PointScale.SCALE_XL: return 2.00f;
    }
    return 1.0f;
  }

  private static float matrixScale( Matrix matrix )
  {
    float[] vector = { 1.0f, 0.0f };
    matrix.mapVectors( vector );
    float value = (float)Math.sqrt( vector[0] * vector[0] + vector[1] * vector[1] );
    return ( value > 0.0f ) ? value : 1.0f;
  }

  private static float safeScale( float scale )
  {
    return ( scale > 0.0f && ! Float.isNaN( scale ) && ! Float.isInfinite( scale ) ) ? scale : 1.0f;
  }

  public boolean hasExplicitTextStyle() { return mTextStyle != null; }

  public SketchTextStyle getTextStyle()
  {
    return mTextStyle;
  }

  public SketchTextStyle getTextStyleForEditor()
  {
    return renderStyle();
  }

  /** normalized Android paint size needed for one unit of requested line height */
  public float getTextPaintSizePerLineHeight()
  {
    String text = mPointText;
    SketchTextStyle style = renderStyle();
    return layoutFor( text, style ).paintSizePerLineHeight;
  }

  /** requested scene-space line height; screen-sized text uses its vector fallback scale */
  public float getTextExportLineHeightScene()
  {
    SketchTextStyle style = mTextStyle;
    if ( style != null && style.sizeMode() != SketchTextStyle.SizeMode.SCREEN ) {
      return worldLineHeightScene( style );
    }
    return TDSetting.mSvgLabelSize * getScaleValue()
      / Math.max( 0.0001f, TDSetting.mToSvg )
      / Math.max( 0.0001f, getTextPaintSizePerLineHeight() );
  }

  String getPublicOptions()
  {
    return SketchPrivateOptions.stripAll( mOptions );
  }

  void setTextStyle( SketchTextStyle style )
  {
    mTextStyle = ( style == null ) ? null : style;
    mOptions = SketchTextStyleCodec.storeInOptions( mOptions, mTextStyle );
    if ( mTextStyle != null ) syncFallbackScale();
    invalidateLayout();
  }

  void applyTextEdit( String text, SketchTextStyle style, boolean make_explicit,
                      double orientation, int level, String public_options )
  {
    mPointText = ( text == null ) ? "" : text;
    mOrientation = TDMath.in360( orientation );
    mLevel = level;
    if ( mTextStyle != null || make_explicit ) {
      mTextStyle = ( style == null ) ? SketchTextStyle.defaultStyle() : style;
      mOptions = SketchTextStyleCodec.storeInOptions( public_options, mTextStyle );
      syncFallbackScale();
    } else {
      mOptions = SketchTextStyleCodec.stripOptions( public_options );
    }
    refreshPaint();
    invalidateLayout();
  }

  @Override
  void setPointText( String text )
  {
    mPointText = text;
    invalidateLayout();
  }

  @Override
  void setScale( int scale )
  {
    if ( scale == mScale ) return;
    mScale = scale;
    if ( mTextStyle == null ) invalidateLayout();
    updateSceneBounds( 1.0f );
  }

  @Override
  public void setOrientation( double angle )
  {
    mOrientation = TDMath.in360( angle );
    updateSceneBounds( 1.0f );
  }

  private SketchTextStyle legacyStyleForEditor( String text )
  {
    SketchTextStyle base = SketchTextStyle.defaultStyle();
    SketchTextLayoutSnapshot layout = SketchTextLayoutSnapshot.create( text, base );
    float paint_size = TDSetting.mLabelSize * pointScaleFactor();
    int color = ( BrushManager.labelPaint == null ) ? SketchTextStyle.DEFAULT_COLOR : BrushManager.labelPaint.getColor();
    if ( TDSetting.mScalableLabel ) {
      float line_height_metres =
        ( TDSetting.mLabelSize * 0.1f * pointScaleFactor() / layout.paintSizePerLineHeight ) / DrawingUtil.SCALE_FIX;
      return SketchTextStyle.of( SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.WORLD,
                                 line_height_metres, false, false, false,
                                 SketchTextStyle.Alignment.LEFT, color );
    }
    float line_height_dp = paint_size
      / layout.paintSizePerLineHeight
      / Math.max( 0.1f, TopoDroidApp.getDisplayDensity() );
    return SketchTextStyle.of( SketchFontRegistry.FONT_DEFAULT, SketchTextStyle.SizeMode.SCREEN,
                               line_height_dp, false, false, false,
                               SketchTextStyle.Alignment.LEFT, color );
  }

  private void syncFallbackScale()
  {
    float ratio;
    if ( mTextStyle.sizeMode() == SketchTextStyle.SizeMode.AUTO_GRID ) {
      ratio = mTextStyle.height();
    } else if ( mTextStyle.sizeMode() == SketchTextStyle.SizeMode.WORLD ) {
      ratio = mTextStyle.height() / Math.max( 0.0001f, TDSetting.mUnitGrid );
    } else {
      ratio = mTextStyle.height() / SketchTextStyle.DEFAULT_SCREEN_HEIGHT;
    }
    if ( ratio < 0.61f ) mScale = PointScale.SCALE_XS;
    else if ( ratio < 0.86f ) mScale = PointScale.SCALE_S;
    else if ( ratio < 1.20f ) mScale = PointScale.SCALE_M;
    else if ( ratio < 1.70f ) mScale = PointScale.SCALE_L;
    else mScale = PointScale.SCALE_XL;
  }

  private void refreshPaint()
  {
    mPaint = SketchTextRenderer.newPaint( renderStyle() );
  }

  private void invalidateLayout()
  {
    mTextLayout = null;
    refreshPaint();
    updateSceneBounds( 1.0f );
  }

  /** @return Therion string representation of the label point
   */
  @Override
  String toTherion( )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw  = new PrintWriter(sw);
    pw.format(Locale.US, "point %.2f %.2f label -text \"%s\"",
              cx*TDSetting.mToTherion, -cy*TDSetting.mToTherion, therionText() );
    toTherionOrientation( pw );
    if ( mTextStyle != null ) {
      String align = "l";
      if ( mTextStyle.alignment() == SketchTextStyle.Alignment.CENTER ) align = "c";
      else if ( mTextStyle.alignment() == SketchTextStyle.Alignment.RIGHT ) align = "r";
      pw.format( " -align %s", align );
    }
    toTherionOptions( pw );
    pw.format("\n");
    return sw.getBuffer().toString();
  }

//   @Override
//   void toCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind /* , DrawingUtil mDrawingUtil */ )
//   { 
//     // int size = mScale - PointScale.SCALE_XS;
//     // int layer  = 6;
//     // int type   = 8;
//     // int cat    = 81;
//     pw.format("<item layer=\"6\" cave=\"%s\" branch=\"%s\" type=\"8\" category=\"81\" transparency=\"0.00\"",
//       cave, branch );
//     if ( bind != null ) pw.format( " bind=\"%s\"", bind );
//     pw.format(" text=\"%s\" textrotatemode=\"1\" >\n", mPointText );
//     pw.format("  <pen type=\"10\" />\n");
//     pw.format("  <brush type=\"7\" />\n");
//     float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
//     float y = DrawingUtil.sceneToWorldY( cx, cy );
//     pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
//     pw.format("  <font type=\"0\" />\n");
//     pw.format("</item>\n");
//   }

  /** write the label point in cSurvey format
   * @param pw        output writer
   * @param survey    (cSurvey) survey name
   * @param cave      cSurvey cave name
   * @param branch    cSurvey branch name
   * @param bind      cSurvey binding
   */
  @Override
  void toTCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind /* , DrawingUtil mDrawingUtil */ )
  { 
    // int size = mScale - PointScale.SCALE_XS;
    pw.format("<item type=\"point\" name=\"label\" cave=\"%s\" branch=\"%s\" text=\"%s\" ",
      xmlAttribute( cave ), xmlAttribute( branch ), xmlAttribute( mPointText ) );
    if ( bind != null ) pw.format( " bind=\"%s\" ", xmlAttribute( bind ) );
    String options = getExportOptions();
    pw.format(Locale.US, "scale=\"%d\" orientation=\"%.2f\" options=\"%s\" >\n",
      mScale, mOrientation, xmlAttribute( (options == null)? "" : options ) );
    float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
    float y = DrawingUtil.sceneToWorldY( cx, cy );
    pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
    pw.format("</item>\n");
  }

  private String therionText()
  {
    String text = ( mPointText == null ) ? "" : mPointText;
    text = text.replace( "&", "&amp;" )
               .replace( "<", "&lt;" )
               .replace( ">", "&gt;" )
               .replace( "\\", "\\\\" )
               .replace( "\"", "\\\"" )
               .replace( "\r\n", "\n" )
               .replace( '\r', '\n' )
               .replace( "\n", "<br>" );
    if ( mTextStyle == null ) return text;
    StringBuilder prefix = new StringBuilder();
    if ( mTextStyle.bold() ) prefix.append( "<bf>" );
    if ( mTextStyle.italic() ) prefix.append( "<it>" );
    if ( mPointText != null && mPointText.indexOf( '\n' ) >= 0 ) {
      if ( mTextStyle.alignment() == SketchTextStyle.Alignment.CENTER ) prefix.append( "<centre>" );
      else if ( mTextStyle.alignment() == SketchTextStyle.Alignment.RIGHT ) prefix.append( "<right>" );
      else prefix.append( "<left>" );
    }
    return prefix.append( text ).toString();
  }

  private static String xmlAttribute( String text )
  {
    if ( text == null ) return "";
    return text.replace( "&", "&amp;" )
               .replace( "<", "&lt;" )
               .replace( ">", "&gt;" )
               .replace( "\"", "&quot;" )
               .replace( "'", "&apos;" )
               .replace( "\r\n", "&#10;" )
               .replace( "\r", "&#10;" )
               .replace( "\n", "&#10;" );
  }

  /** serialize the label point
   * @param dos    output stream
   * @param scrap  scrap index
   */
  @Override
  void toDataStream( DataOutputStream dos, int scrap )
  {
    // label has null group
    try {
      dos.write( 'T' );
      dos.writeFloat( cx );
      dos.writeFloat( cy );
      // dos.writeUTF( BrushManager.getPointThName(mPointType) );
      // if ( version >= 401147 ) dos.writeUTF( "" ); // label has null group
      dos.writeFloat( (float)mOrientation ); // from version 2.7.4e
      dos.writeInt( mScale );
      // if ( version >= 401090 ) 
        dos.writeInt( mLevel );
      // if ( version >= 401160 ) 
        dos.writeInt( (scrap >= 0)? scrap : mScrap );
      dos.writeUTF( ( mPointText != null )? mPointText : "" );
      dos.writeUTF( ( mOptions != null )? mOptions : "" );
      // TDLog.Log( TDLog.LOG_PLOT, "T " + " " + cx + " " + cy );
    } catch ( IOException e ) {
      TDLog.e( "LABEL out error " + e.toString() );
    }
  }

}
