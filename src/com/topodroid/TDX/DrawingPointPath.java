/* @file DrawingPointPath.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid drawing: points
 *        type DRAWING_PATH_POINT
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */

package com.topodroid.TDX;

import com.topodroid.math.TDVector;
import com.topodroid.util.TDMath;
import com.topodroid.util.TDLog;
import com.topodroid.util.TDFile;
import com.topodroid.util.TDUtil;
import com.topodroid.util.TDString;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PlotType;
import com.topodroid.types.PointScale;

import com.topodroid.num.TDNum;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.graphics.Region;
import android.graphics.PointF;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;

import android.util.Base64;

public class DrawingPointPath extends DrawingPath
{
  // float mXpos;        // scene coords
  // float mYpos;
  public int mPointType;      // symbol point type (index in symbol-point lib)
  protected int mScale;       // symbol scale
  public double mOrientation; // orientation [degrees]
  public volatile String mPointText;   // point text value (if any)
  public IDrawingLink mLink;  // linked drawing item
  private SketchBrushStyle mSketchBrushStyle;
  private SketchAffineTransform mSketchAffineTransform;
  private float mAffineBasePointScale = Float.NaN;
  private String mSketchOcclusionGroup;
  private Path mOcclusionSilhouette;
  private Path mDetailPath;
  private float mDetailStrokeScale = 1.0f;

  // FIXME-COPYPATH
  // @Override
  // DrawingPath copyPath()
  // {
  //   DrawingPointPath ret = new DrawingPointPath( mPointType, cx, cy, mScale, mPointText, mOptions );
  //   copyTo( ret );
  //   return ret;
  // }

  // FIXME SECTION_RENAME
  /** fix the scrap name in the option string replacing the survey-prefix
   * @param survey_name   name of the new survey
   * @return this point path
   * @note only for section point
   */
  public DrawingPointPath fixScrap( String survey_name )
  {
    if ( survey_name != null && BrushManager.isPointSection( mPointType ) ) {
      String scrapname = getOption( TDString.OPTION_SCRAP );
      if ( scrapname != null ) scrapname = TDUtil.replacePrefix( TDInstance.survey, scrapname );
      if ( scrapname != null ) {
        if ( ! scrapname.startsWith(survey_name) ) {
          int pos = scrapname.lastIndexOf('-');
          scrapname = survey_name + "-" + scrapname.substring(pos+1);
        }
        setOption( TDString.OPTION_SCRAP, scrapname );
      } else {
        TDLog.e("section point without scrap-name");
        return null;
      }
    }
    return this;
  }

  // String getTextFromOptions( String options )
  // {
  //   if ( options != null ) {
  //     int len = options.length();
  //     int pos = options.indexOf("-text");
  //     if ( pos > 0 ) {
  //       int start = pos + 5;
  //       while ( start < len && options.charAt( start ) == ' ' ) ++ start;
  //       if ( start < len ) {
  //         int end = start + 1;
  //         while ( end < len && options.charAt( end ) != ' ' ) ++ end;
  //         if ( end < len ) {
  //           mOptions = options.substring(0, start) + options.substring(end);
  //         } else {
  //           mOptions = options.substring(0, start);
  //         }
  //         if ( options.charAt( start ) == '"' ) start ++;
  //         if ( options.charAt( end ) == '"' ) end --;
  //         return options.substring( start, end );
  //       }
  //     }
  //   }
  //   return null;
  // }

  /** cstr
   * @param type    point type
   * @param x       X coord
   * @param y       Y coord
   * @param scale   symbol scale
   * @param scrap   scrap index
   */
  public DrawingPointPath( int type, float x, float y, int scale, int scrap )
  {
    super( DrawingPath.DRAWING_PATH_POINT, null, scrap );
    // TDLog.Log( TDLog.LOG_PATH, "Point " + type + " X " + x + " Y " + y );
    mPointType = type;
    setCenter( x, y );
    // mScale   = PointScale.SCALE_NONE;
    mScale = scale;
    mOrientation = 0.0;
    if ( BrushManager.isPointOrientable( mPointType ) ) mOrientation = BrushManager.getPointOrientation( mPointType );
    setOptions( BrushManager.getPointDefaultOptions( mPointType ) );
    if ( BrushManager.isPointAffine( mPointType ) ) setSketchAffineTransform( SketchAffineTransform.identity() );
    String occlusion_group = BrushManager.pointDefaultOccludeGroup( mPointType );
    if ( occlusion_group != null ) setSketchOcclusionGroup( occlusion_group );
    mPointText = null; // getTextFromOptions( options ); // this can also reset mOptions
    mLevel     = BrushManager.getPointLevel( mPointType );

    setPathPaint( BrushManager.getPointPaint( mPointType ) );
    resetPath( 1.0f );
    mLink = null;
  }

  /** cstr
   * @param type    point type
   * @param x       X coord
   * @param y       Y coord
   * @param scale   symbol scale
   * @param text    text string
   * @param options options string
   * @param scrap   scrap index
   */
  public DrawingPointPath( int type, float x, float y, int scale, String text, String options, int scrap )
  {
    super( DrawingPath.DRAWING_PATH_POINT, null, scrap );
    // TDLog.Log( TDLog.LOG_PATH, "Point " + type + " X " + x + " Y " + y );
    mPointType = type;
    setCenter( x, y );
    // mScale   = PointScale.SCALE_NONE;
    mScale = scale;
    mOrientation = 0.0;
    if ( BrushManager.isPointOrientable( type ) ) mOrientation = BrushManager.getPointOrientation(type);
    setOptions( options );
    mPointText = text; // getTextFromOptions( options ); // this can also reset mOptions
    mLevel     = BrushManager.getPointLevel( type );

    setPathPaint( BrushManager.getPointPaint( mPointType ) );
    resetPath( 1.0f );
    mLink = null;
    // TDLog.v( "Point cstr " + type + " orientation " + mOrientation );
  }

  /** factory: create a point path from the data stream
   * @param version serialize version
   * @param dis     input data stream
   * @param x       offset X coord [scene ?]
   * @param y       offset Y coord
   * @return the deserialized point path
   */
  public static DrawingPointPath loadDataStream( int version, DataInputStream dis, float x, float y /* , SymbolsPalette missingSymbols */ ) 
  {
    float ccx, ccy, orientation;
    int   type;
    int   scale;
    int   level = DrawingLevel.LEVEL_DEFAULT;
    int   scrap = 0;
    String name;  // th-name
    String group = null;
    String options; // = null;
    String text = null;
    try {
      ccx = x + dis.readFloat();
      ccy = y + dis.readFloat();
      name = dis.readUTF( );
      if ( version >= 401147 ) group = dis.readUTF();
      orientation = dis.readFloat();
      scale   = dis.readInt();
      if ( version >= 401090 ) level = dis.readInt();
      if ( version >= 401160 ) scrap = dis.readInt();
      if ( version >= 303066 ) text  = dis.readUTF();
      options = dis.readUTF();

      // BrushManager.tryLoadMissingPoint( name ); // LOAD_MISSING

      type = BrushManager.getPointIndexByThNameOrGroup( name, group );
      // TDLog.Log( TDLog.LOG_PLOT, "P " + name + " " + type + " " + ccx + " " + ccy + " " + orientation + " " + scale + " options (" + options + ")" );
      // TDLog.v( "PLOT " + name + " " + type + " " + ccx + " " + ccy + " " + orientation + " " + scale + " options (" + options + ")" );
      if ( type < 0 ) {
        // FIXME-MISSING if ( missingSymbols != null ) missingSymbols.addPointFilename( name ); 
        type = 0;
        options = (options != null)? options + " -symbol " + name : "-symbol " + name;
      }
      // FIXME SECTION_RENAME
      // if ( BrushManager.isPointSection( type ) ) {
      //   String scrapname = TDUtil.replacePrefix( TDInstance.survey, options.replace( TDString.OPTION_SCRAP + " ", "") );
      //   scrapname = scrapname.replace( mApp.mSurvey + "-", "" ); // remove survey name from options
      //   option = TDString.OPTION_SCRAP + " " + scrapname;
      // }
      DrawingPointPath ret = BrushManager.isPointReference( type )
                           ? new DrawingReferencePath( type, ccx, ccy, scale, text, options, scrap )
                           : new DrawingPointPath( type, ccx, ccy, scale, text, options, scrap );
      ret.mLevel = level;
      ret.setOrientation( orientation );
      return ret;

      // // TODO parse option for "-text"
      // setPathPaint( BrushManager.getPointPaint( mPointType ) );
      // if ( BrushManager.isPointOrientable( mPointType ) ) {
      //   BrushManager.rotateGradPoint( mPointType, mOrientation );
      //   resetPath( 1.0f );
      //   BrushManager.rotateGradPoint( mPointType, -mOrientation );
      // }
    } catch ( IOException e ) {
      TDLog.e( "POINT in error " + e.getMessage() );
      // TDLog.v( "POINT in error " + e.getMessage() );
    }
    return null;
  }

  // static void globDataStream( int version, DataInputStream dis )
  // {
  //   try {
  //     dis.readFloat();
  //     dis.readFloat();
  //     dis.readUTF( );
  //     if ( version >= 401147 ) dis.readUTF();
  //     dis.readFloat();
  //     dis.readInt();
  //     if ( version >= 401090 ) dis.readInt();
  //     if ( version >= 401160 ) dis.readInt();
  //     if ( version >= 303066 ) dis.readUTF();
  //     dis.readUTF();
  //   } catch ( IOException e ) {
  //     TDLog.e( "POINT in error " + e.getMessage() );
  //     // TDLog.v( "POINT in error " + e.getMessage() );
  //   }
  // }

  /** set the point center - move the point to a new center
   * @param x       X coord
   * @param y       Y coord
   */
  void setCenter( float x, float y )
  {
    cx = x;
    cy = y;
    left   = x; 
    right  = x+1;
    top    = y;
    bottom = y+1;
  }

  /** rotate the point orientation
   * @param dt   rotation angle [degrees]
   * @return true if the point can be rotated
   */
  @Override
  boolean rotateBy( float dt )
  {
    if ( ! BrushManager.isPointOrientable( mPointType ) ) return false;
    setOrientation ( mOrientation + dt );
    return true;
  }

  /** shift the point position
   * @param dx       X coord shift
   * @param dy       Y coord shift
   */
  @Override
  void shiftBy( float dx, float dy )
  {
    cx += dx;
    cy += dy;
    mPath.offset( dx, dy );
    if ( mDetailPath != null ) mDetailPath.offset( dx, dy );
    if ( mOcclusionSilhouette != null ) mOcclusionSilhouette.offset( dx, dy );
    left   += dx;
    right  += dx;
    top    += dy;
    bottom += dy;
  }

  /** scale the point symbol - with respect to the scene
   * @param z   scale factor
   * @param m   scaling matrix
   */
  @Override
  void scaleBy( float z, Matrix m )
  {
    cx *= z;
    cy *= z;
    if ( mSketchAffineTransform != null ) {
      SketchAffineTransform scaled = mSketchAffineTransform.scaleBy( z );
      if ( scaled != null ) {
        storeSketchAffineTransform( scaled );
        resetPath( 1.0f );
        return;
      }
    }
    mPath.transform( m );
    if ( mDetailPath != null ) mDetailPath.transform( m );
    if ( mOcclusionSilhouette != null ) mOcclusionSilhouette.transform( m );
    updatePointBounds();
  }

  /** affine transform the point symbol - with respect to the scene
   * @param mm   affine matrix (to transform the center)
   * @param m    transform matrix
   */
  @Override
  void affineTransformBy( float[] mm, Matrix m )
  {
    float x = mm[0] * cx + mm[1] * cy + mm[2];
         cy = mm[3] * cx + mm[4] * cy + mm[5];
         cx = x;
    if ( mSketchAffineTransform != null ) {
      SketchAffineTransform transformed = mSketchAffineTransform.leftMultiply( mm[0], mm[1], mm[3], mm[4] );
      if ( transformed != null ) {
        storeSketchAffineTransform( transformed );
        resetPath( 1.0f );
        return;
      }
    }
    mPath.transform( m );
    if ( mDetailPath != null ) mDetailPath.transform( m );
    if ( mOcclusionSilhouette != null ) mOcclusionSilhouette.transform( m );
    updatePointBounds();
  }

  // from ICanvasCommand
  /** shift the symbol path
   * @param dx       X shift
   * @param dy       Y shift
   */
  @Override
  public void shiftPathBy( float dx, float dy ) 
  {
    // x1 += dx;
    // y1 += dy;
    // x2 += dx;
    // y2 += dy;
    // cx += dx;
    // cy += dy;
    // mPath.offset( dx, dy );
    // left   += dx;
    // right  += dx;
    // top    += dy;
    // bottom += dy;
  }

  // from ICanvasCommand
  // FIXME SCALE
  /** scale the symbol path
   * @param z   scale factor
   * @param m   scaling matrix
   */
  @Override
  public void scalePathBy( float z, Matrix m )
  {
    // x1 *= z;
    // y1 *= z;
    // x2 *= z;
    // y2 *= z;
    // cx *= z;
    // cy *= z;
    // mPath.transform( m );
    // left   *= z;
    // right  *= z;
    // top    *= z;
    // bottom *= z;
  }

  /** Affine transform the symbol path (empty method)
   * @param mm   affine matrix 
   * @param m    transform matrix
   */
  @Override // empty
  public void affineTransformPathBy( float[] mm, Matrix m )
  {
  }

  /** set the link
   * @param link   new link
   */
  void setLink( IDrawingLink link ) { mLink = link; }

  /** @return the linked item (or null)
   */
  IDrawingLink getLink() { return mLink; }

  /** @return the point color
   */
  int pointColor( ) { return BrushManager.getPointColor( mPointType ); }

  /** get the therion name 
   * @return the point type
   */
  public String getThName() { return  BrushManager.getPointThName( mPointType ); }

  /** get the full therion name 
   * @return the point Therion type (possibly incuding the prefix)
   */
  public String getFullThName() { return  BrushManager.getPointFullThName( mPointType ); }

  /** @return the point Therion type (possibly incuding the prefix), with ':' replaced by '_'
   */
  public String getFullThNameEscapedColon() { return  BrushManager.getPointFullThNameEscapedColon( mPointType ); }

  /** draw the point on the canvas
   * @param canvas    canvas
   * @param matrix    transform matrix
   * @param scale     scale factor
   * @param bbox      clipping rectangle
   * @note canvas is guaranteed not null
   */
  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox )
  {
    if ( BrushManager.isPointSection( mPointType ) && SectionPointHelper.isPlaced( this ) ) return;
    if ( intersects( bbox ) ) {
      if ( TDSetting.mUnscaledPoints ) {
        resetPath( 4 * scale );
      }
      // WORLD-SPACE INK: draw in scene space; stroke widths [scene units]
      // magnify with zoom / export scale via the canvas transform
      int save = canvas.save();
      try {
        canvas.concat( matrix );
        if ( mLandscape && ! BrushManager.isPointOrientable( mPointType ) ) {
          canvas.rotate( 90, cx, cy );
        }
        drawSketchStyledPath( mPath, canvas );
        drawSketchDetailPath( canvas );
        if ( mLink != null ) {
          Path link = new Path();
          link.moveTo( cx, cy );
          link.lineTo( mLink.getLinkX(), mLink.getLinkY() );
          canvas.drawPath( link, BrushManager.fixedOrangePaint );
        }
      } finally {
        canvas.restoreToCount( save );
      }
    }
  }

  /** draw the point on the canvas
   * @param canvas    canvas
   * @param matrix    transform matrix
   * @param scale     scale factor
   * @param bbox      clipping rectangle
   * @param xor_color xoring color
   * @note canvas is guaranteed not null
   */
  @Override
  public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
  {
    if ( BrushManager.isPointSection( mPointType ) && SectionPointHelper.isPlaced( this ) ) return;
    if ( intersects( bbox ) ) {
      if ( TDSetting.mUnscaledPoints ) {
        resetPath( 4 * scale );
      }
      int save = canvas.save();
      try {
        canvas.concat( matrix );
        if ( mLandscape && ! BrushManager.isPointOrientable( mPointType ) ) {
          canvas.rotate( 90, cx, cy );
        }
        drawSketchStyledPath( mPath, canvas, xor_color );
        drawSketchDetailPath( canvas, xor_color );
        if ( mLink != null ) {
          Path link = new Path();
          link.moveTo( cx, cy );
          link.lineTo( mLink.getLinkX(), mLink.getLinkY() );
          canvas.drawPath( link, BrushManager.fixedOrangePaint );
        }
      } finally {
        canvas.restoreToCount( save );
      }
    }
  }

  /** set the scale index
   * @param scale   new scale index
   */
  void setScale( int scale )
  {
    if ( ! BrushManager.isPointScalable( mPointType ) ) return;
    if ( mSketchAffineTransform != null ) {
      float exact = SketchPointScale.legacyScaleValue( scale );
      mAffineBasePointScale = exact;
      mSketchBrushStyle = ( mSketchBrushStyle == null ) ? SketchBrushStyle.pointScaleOnly( exact ) : mSketchBrushStyle.withPointScale( exact );
      mOptions = SketchBrushStyleCodec.storeInOptions( mOptions, mSketchBrushStyle );
      syncLegacyFromAffine();
      resetPath( 1.0f );
      return;
    }
    if ( scale != mScale ) {
      mScale = scale;
      if ( mSketchBrushStyle != null ) {
        mSketchBrushStyle = mSketchBrushStyle.withPointScale( SketchPointScale.legacyScaleValue( scale ) );
        mOptions = SketchBrushStyleCodec.storeInOptions( mOptions, mSketchBrushStyle );
      }
      resetPath( 1.0f );
    }
  }

  /** set the exact Sketch point scale and keep the legacy scale bucket as fallback
   * @param scale exact Sketch point scale
   * @return true when the scale was accepted
   */
  boolean setExactPointScale( float scale )
  {
    if ( ! BrushManager.isPointScalable( mPointType ) ) return false;
    float normalized = SketchPointScale.normalize( scale, getSketchPointScaleValue() );
    mSketchBrushStyle = ( mSketchBrushStyle == null ) ? SketchBrushStyle.pointScaleOnly( normalized ) : mSketchBrushStyle.withPointScale( normalized );
    mOptions = SketchBrushStyleCodec.storeInOptions( mOptions, mSketchBrushStyle );
    if ( mSketchAffineTransform != null ) {
      mAffineBasePointScale = normalized;
      syncLegacyFromAffine();
    } else {
      mScale = SketchPointScale.nearestLegacyScale( normalized );
    }
    resetPath( 1.0f );
    return true;
  }

  /** get the scale index
   * @return the scale index
   */
  public int getScale() { return mScale; }

  /** get the scale value
   * @return the scale value
   */
  public float getScaleValue() // FIX Asenov
  {
    return SketchPointScale.legacyScaleValue( mScale );
  }

  /** @return exact Sketch point footprint scale with legacy bucket fallback
   */
  public float getSketchPointScaleValue()
  {
    if ( mSketchAffineTransform != null && Float.isFinite( mAffineBasePointScale ) ) return mAffineBasePointScale;
    return ( mSketchBrushStyle != null ) ? mSketchBrushStyle.pointScaleOr( getScaleValue() ) : getScaleValue();
  }

  /** @return exact Sketch point footprint scale, expanded with W for stroked Sketch point stamps
   */
  private float getSketchPointFootprintScaleValue()
  {
    float scale = getSketchPointScaleValue();
    if ( mSketchBrushStyle != null && shouldScalePointFootprintWithWeight() ) {
      scale *= SketchBrushRenderer.effectScale( mSketchBrushStyle );
    }
    return scale;
  }

  private boolean shouldScalePointFootprintWithWeight()
  {
    String th_name = getThName();
    return th_name == null || ! th_name.equals( SymbolLibrary.SAND );
  }

  /** set Sketch brush-style metadata for this placement
   * @param style  Sketch brush style, or null to clear it
   */
  void setSketchBrushStyle( SketchBrushStyle style )
  {
    if ( mSketchAffineTransform != null && ( style == null || ! style.hasPointScale() ) ) {
      float base = Float.isFinite( mAffineBasePointScale ) ? mAffineBasePointScale : SketchPointScale.legacyScaleValue( mScale );
      style = ( style == null ) ? SketchBrushStyle.pointScaleOnly( base ) : style.withPointScale( base );
    }
    mSketchBrushStyle = style;
    if ( style != null && style.hasPointScale() ) {
      float point_scale = style.pointScaleOr( getScaleValue() );
      if ( mSketchAffineTransform != null ) {
        mAffineBasePointScale = point_scale;
      } else {
        mScale = SketchPointScale.nearestLegacyScale( point_scale );
      }
    }
    mOptions = SketchBrushStyleCodec.storeInOptions( mOptions, style );
    if ( mSketchAffineTransform != null ) syncLegacyFromAffine();
    if ( mPath != null ) resetPath( 1.0f );
  }

  /** @return Sketch brush-style metadata, or null if this placement has none
   */
  SketchBrushStyle getSketchBrushStyle() { return mSketchBrushStyle; }

  /** set the options string and refresh Sketch brush metadata
   * @param options   new options string
   */
  @Override
  public void setOptions( String options )
  {
    super.setOptions( options );
    mSketchBrushStyle = SketchBrushStyleCodec.fromOptions( mOptions );
    SketchAffineTransform parsed_affine = SketchAffineTransformCodec.fromOptions( mOptions );
    mSketchAffineTransform = BrushManager.isPointAffine( mPointType ) ? parsed_affine : null;
    String parsed_group = SketchOcclusionCodec.fromOptions( mOptions );
    String default_group = BrushManager.pointDefaultOccludeGroup( mPointType );
    mSketchOcclusionGroup = ( parsed_group != null && parsed_group.equals( default_group )
        && BrushManager.getPointOrigOcclusionSilhouette( mPointType ) != null ) ? parsed_group : null;
    mAffineBasePointScale = Float.NaN;
    if ( mSketchAffineTransform != null ) {
      mAffineBasePointScale = ( mSketchBrushStyle != null && mSketchBrushStyle.hasPointScale() )
          ? mSketchBrushStyle.pointScaleOr( SketchPointScale.legacyScaleValue( mScale ) )
          : SketchPointScale.legacyScaleValue( mScale );
      syncLegacyFromAffine();
    } else if ( mSketchBrushStyle != null && mSketchBrushStyle.hasPointScale() ) {
      mScale = SketchPointScale.nearestLegacyScale( mSketchBrushStyle.pointScaleOr( getScaleValue() ) );
    }
    if ( mPath != null ) resetPath( 1.0f );
  }
      
  /** recreate the symbol path
   * @param f   post-scale factor
   */
  private void resetPath( float f )
  {
    // TDLog.v( "Reset path " + mOrientation + " scale " + mScale );
    Matrix m = new Matrix();
    if ( ! BrushManager.isPointLabel( mPointType ) ) {
      f *= getSketchPointFootprintScaleValue();
      if ( mSketchAffineTransform != null ) {
        m = mSketchAffineTransform.matrix( f );
      } else {
        if ( BrushManager.isPointOrientable( mPointType ) ) m.postRotate( (float)mOrientation );
        m.postScale(f,f);
      }
      makePath( BrushManager.getPointOrigPath( mPointType ), m, cx, cy );
      Path detail = BrushManager.getPointOrigDetailPath( mPointType );
      if ( detail == null || detail.isEmpty() ) {
        mDetailPath = null;
      } else {
        mDetailPath = new Path( detail );
        mDetailPath.transform( m );
        mDetailPath.offset( cx, cy );
      }
      mDetailStrokeScale = BrushManager.getPointDetailStrokeScale( mPointType );
      Path silhouette = BrushManager.getPointOrigOcclusionSilhouette( mPointType );
      if ( mSketchOcclusionGroup == null || silhouette == null || silhouette.isEmpty() ) {
        mOcclusionSilhouette = null;
      } else {
        mOcclusionSilhouette = new Path( silhouette );
        mOcclusionSilhouette.transform( m );
        mOcclusionSilhouette.offset( cx, cy );
      }
      updatePointBounds();
    }
  }

  private void updatePointBounds()
  {
    RectF bounds = new RectF();
    boolean has_path = mPath != null && ! mPath.isEmpty();
    if ( has_path ) mPath.computeBounds( bounds, true );
    if ( mDetailPath != null && ! mDetailPath.isEmpty() ) {
      RectF detail_bounds = new RectF();
      mDetailPath.computeBounds( detail_bounds, true );
      if ( has_path ) bounds.union( detail_bounds ); else { bounds.set( detail_bounds ); has_path = true; }
    }
    if ( ! has_path ) {
      left = cx;
      right = cx + 1.0f;
      top = cy;
      bottom = cy + 1.0f;
      return;
    }
    Paint point_paint = getSketchPointPaint();
    float stroke = ( point_paint == null ) ? 0.0f : Math.abs( point_paint.getStrokeWidth() );
    float half_stroke = 0.5f * stroke;
    left = bounds.left - half_stroke;
    right = bounds.right + half_stroke;
    top = bounds.top - half_stroke;
    bottom = bounds.bottom + half_stroke;
  }

  boolean hasSketchAffineTransform() { return mSketchAffineTransform != null; }

  SketchAffineTransform getSketchAffineTransform() { return mSketchAffineTransform; }

  boolean setSketchAffineTransform( SketchAffineTransform transform )
  {
    if ( transform == null || ! BrushManager.isPointAffine( mPointType ) ) return false;
    if ( ! Float.isFinite( mAffineBasePointScale ) ) {
      mAffineBasePointScale = ( mSketchBrushStyle != null && mSketchBrushStyle.hasPointScale() )
          ? mSketchBrushStyle.pointScaleOr( SketchPointScale.legacyScaleValue( mScale ) )
          : SketchPointScale.legacyScaleValue( mScale );
    }
    if ( mSketchBrushStyle == null || ! mSketchBrushStyle.hasPointScale() ) {
      mSketchBrushStyle = ( mSketchBrushStyle == null ) ? SketchBrushStyle.pointScaleOnly( mAffineBasePointScale )
                                                        : mSketchBrushStyle.withPointScale( mAffineBasePointScale );
      mOptions = SketchBrushStyleCodec.storeInOptions( mOptions, mSketchBrushStyle );
    }
    storeSketchAffineTransform( transform );
    if ( mPath != null ) resetPath( 1.0f );
    return true;
  }

  private void storeSketchAffineTransform( SketchAffineTransform transform )
  {
    mSketchAffineTransform = transform;
    mOptions = SketchAffineTransformCodec.storeInOptions( mOptions, transform );
    syncLegacyFromAffine();
  }

  private void syncLegacyFromAffine()
  {
    if ( mSketchAffineTransform == null ) return;
    mOrientation = TDMath.in360( mSketchAffineTransform.closestRotationDegrees() );
    if ( mSketchBrushStyle == null || ! mSketchBrushStyle.hasPointScale() ) return;
    float base = Float.isFinite( mAffineBasePointScale ) ? mAffineBasePointScale : SketchPointScale.legacyScaleValue( mScale );
    mScale = SketchPointScale.nearestLegacyScale( base * mSketchAffineTransform.closestUniformScale() );
  }

  boolean hasSketchOcclusion() { return mSketchOcclusionGroup != null && mOcclusionSilhouette != null; }

  String getSketchOcclusionGroup() { return mSketchOcclusionGroup; }

  boolean setSketchOcclusionGroup( String group )
  {
    String default_group = BrushManager.pointDefaultOccludeGroup( mPointType );
    if ( ! SketchOcclusionCodec.isValidGroup( group ) || ! group.equals( default_group )
        || BrushManager.getPointOrigOcclusionSilhouette( mPointType ) == null ) return false;
    mSketchOcclusionGroup = group;
    mOptions = SketchOcclusionCodec.storeInOptions( mOptions, group );
    if ( mPath != null ) resetPath( 1.0f );
    return true;
  }

  void prepareForOcclusionDraw( float scale )
  {
    if ( TDSetting.mUnscaledPoints ) resetPath( 4.0f * scale );
  }

  Path copyOcclusionSilhouette()
  {
    return ( mOcclusionSilhouette == null ) ? null : new Path( mOcclusionSilhouette );
  }

  Path copySketchStructurePath() { return ( mPath == null ) ? null : new Path( mPath ); }

  Path copySketchDetailPath() { return ( mDetailPath == null ) ? null : new Path( mDetailPath ); }

  Paint sketchPointPaintForOcclusion() { return getSketchPointPaint(); }

  Paint sketchDetailPaintForOcclusion() { return getSketchDetailPaint(); }

  float getSketchAffineFootprintScale() { return getSketchPointFootprintScaleValue(); }

  RectF getSketchAffineLocalBounds()
  {
    Path silhouette = BrushManager.getPointOrigOcclusionSilhouette( mPointType );
    if ( silhouette == null ) silhouette = BrushManager.getPointOrigPath( mPointType );
    RectF bounds = new RectF();
    if ( silhouette != null ) silhouette.computeBounds( bounds, true );
    return bounds;
  }

  boolean mapSketchAffineLocalPoint( float local_x, float local_y, PointF output )
  {
    if ( output == null || mSketchAffineTransform == null ) return false;
    float footprint = getSketchAffineFootprintScale();
    output.x = cx + footprint * ( mSketchAffineTransform.m00 * local_x + mSketchAffineTransform.m01 * local_y );
    output.y = cy + footprint * ( mSketchAffineTransform.m10 * local_x + mSketchAffineTransform.m11 * local_y );
    return true;
  }

  boolean inverseSketchAffinePoint( float scene_x, float scene_y, float[] output )
  {
    if ( output == null || output.length < 2 || mSketchAffineTransform == null ) return false;
    float footprint = getSketchAffineFootprintScale();
    float det = mSketchAffineTransform.determinant() * footprint * footprint;
    if ( det < SketchAffineTransform.MIN_DETERMINANT * footprint * footprint ) return false;
    float dx = scene_x - cx;
    float dy = scene_y - cy;
    output[0] = footprint * ( mSketchAffineTransform.m11 * dx - mSketchAffineTransform.m01 * dy ) / det;
    output[1] = footprint * ( -mSketchAffineTransform.m10 * dx + mSketchAffineTransform.m00 * dy ) / det;
    return Float.isFinite( output[0] ) && Float.isFinite( output[1] );
  }

  boolean hitSketchAffineSilhouette( float scene_x, float scene_y, float scene_slop )
  {
    if ( mSketchAffineTransform == null ) return false;
    Path silhouette = BrushManager.getPointOrigOcclusionSilhouette( mPointType );
    if ( silhouette == null || silhouette.isEmpty() ) return false;
    float[] local = new float[2];
    if ( ! inverseSketchAffinePoint( scene_x, scene_y, local ) ) return false;
    float footprint = getSketchAffineFootprintScale();
    float min_axis = Math.min( (float)Math.hypot( mSketchAffineTransform.m00, mSketchAffineTransform.m10 ),
                               (float)Math.hypot( mSketchAffineTransform.m01, mSketchAffineTransform.m11 ) );
    float local_slop = ( footprint > 0.0f && min_axis > 0.0f ) ? Math.max( 0.0f, scene_slop ) / ( footprint * min_axis ) : 0.0f;
    Path hit_path = new Path( silhouette );
    if ( local_slop > 0.0f ) {
      Paint outline = new Paint( Paint.ANTI_ALIAS_FLAG );
      outline.setStyle( Paint.Style.STROKE );
      outline.setStrokeWidth( 2.0f * local_slop );
      outline.setStrokeJoin( Paint.Join.ROUND );
      Path expanded = new Path();
      outline.getFillPath( silhouette, expanded );
      hit_path.addPath( expanded );
    }
    final float region_scale = 128.0f;
    Matrix scale = new Matrix();
    scale.setScale( region_scale, region_scale );
    hit_path.transform( scale );
    RectF hit_bounds = new RectF();
    hit_path.computeBounds( hit_bounds, true );
    Region clip = new Region( (int)Math.floor( hit_bounds.left ) - 2, (int)Math.floor( hit_bounds.top ) - 2,
                              (int)Math.ceil( hit_bounds.right ) + 2, (int)Math.ceil( hit_bounds.bottom ) + 2 );
    Region region = new Region();
    region.setPath( hit_path, clip );
    return region.contains( Math.round( local[0] * region_scale ), Math.round( local[1] * region_scale ) );
  }

  void drawVisibleOcclusionInk( Canvas canvas, Matrix matrix, RectF bbox, Path structure_ink, Path detail_ink,
                                boolean landscape, int xor_color )
  {
    if ( ! intersects( bbox ) ) return;
    int save = canvas.save();
    try {
      canvas.concat( matrix );
      if ( landscape && ! BrushManager.isPointOrientable( mPointType ) ) canvas.rotate( 90, cx, cy );
      Paint structure = sketchPointPaintForOcclusion();
      if ( structure != null && structure_ink != null ) {
        structure = ( xor_color > 0 ) ? DrawingPath.xorPaint( structure, xor_color ) : new Paint( structure );
        structure.setStyle( Paint.Style.FILL );
        canvas.drawPath( structure_ink, structure );
      }
      Paint detail = sketchDetailPaintForOcclusion();
      if ( detail != null && detail_ink != null ) {
        detail = ( xor_color > 0 ) ? DrawingPath.xorPaint( detail, xor_color ) : new Paint( detail );
        detail.setStyle( Paint.Style.FILL );
        canvas.drawPath( detail_ink, detail );
      }
      if ( mLink != null ) {
        Path link = new Path();
        link.moveTo( cx, cy );
        link.lineTo( mLink.getLinkX(), mLink.getLinkY() );
        canvas.drawPath( link, BrushManager.fixedOrangePaint );
      }
    } finally {
      canvas.restoreToCount( save );
    }
  }

  private Paint getSketchPointPaint()
  {
    return SketchBrushRenderer.pointPaint( mPaint, mSketchBrushStyle,
                                           BrushManager.getPointSketchStrokeScale( mPointType ) );
  }

  private void drawSketchStyledPath( Path path, Canvas canvas )
  {
    Paint paint = mPaint;
    mPaint = getSketchPointPaint();
    try {
      drawPath( path, canvas );
    } finally {
      mPaint = paint;
    }
  }

  private void drawSketchStyledPath( Path path, Canvas canvas, int xor_color )
  {
    Paint paint = mPaint;
    mPaint = getSketchPointPaint();
    try {
      drawPath( path, canvas, xor_color );
    } finally {
      mPaint = paint;
    }
  }

  private Paint getSketchDetailPaint()
  {
    Paint point = getSketchPointPaint();
    if ( point == null ) return null;
    Paint detail = new Paint( point );
    detail.setStrokeWidth( point.getStrokeWidth() * mDetailStrokeScale );
    return detail;
  }

  private void drawSketchDetailPath( Canvas canvas )
  {
    if ( mDetailPath == null ) return;
    Paint paint = mPaint;
    mPaint = getSketchDetailPaint();
    try {
      drawPath( mDetailPath, canvas );
    } finally {
      mPaint = paint;
    }
  }

  private void drawSketchDetailPath( Canvas canvas, int xor_color )
  {
    if ( mDetailPath == null ) return;
    Paint paint = mPaint;
    mPaint = getSketchDetailPaint();
    try {
      drawPath( mDetailPath, canvas, xor_color );
    } finally {
      mPaint = paint;
    }
  }

  // void setPos( float x, float y ) 
  // {
  //   setCenter( x, y );
  // }

  /** set the point type and rebind symbol paint/path
   * @param t new point type
   */
  void setPointType( int t )
  {
    mPointType = t;
    if ( BrushManager.hasPoint( mPointType ) ) {
      mLevel = BrushManager.getPointLevel( mPointType );
      setPathPaint( BrushManager.getPointPaint( mPointType ) );
      if ( ! BrushManager.isPointOrientable( mPointType ) ) mOrientation = 0.0;
      setOptions( mOptions );
    }
  }

  /** get the type of the point
   * @return the point type
   */
  public int pointType() { return mPointType; }

  // double xpos() { return cx; }
  // double ypos() { return cy; }

  // double orientation() { return mOrientation; }

  /** set the point orientation
   * @param angle  orientation angle [degrees]
   */
  @Override
  void setOrientation( double angle ) 
  { 
    // TDLog.Log( TDLog.LOG_PATH, "Point " + mPointType + " set Orientation " + angle );
    // TDLog.v( "Point::set Orientation " + angle );
    if ( mSketchAffineTransform != null ) {
      float target = (float)TDMath.in360( angle );
      float delta = target - (float)mOrientation;
      if ( delta > 180.0f ) delta -= 360.0f;
      if ( delta < -180.0f ) delta += 360.0f;
      SketchAffineTransform rotated = mSketchAffineTransform.rotateBy( delta );
      if ( rotated != null ) storeSketchAffineTransform( rotated );
    } else {
      mOrientation = TDMath.in360( angle );
    }
    resetPath( 1.0f );
  }

  /** get the text of the point
   * @return the point text
   */
  public String getPointText() { return mPointText; }

  /** set the text of the point
   * @param text    the point text
   */
  void setPointText( String text )
  {
    mPointText = text;
  }

  // /** move the point to a new position - UNUSED
  //  * @param x   X coord (in the scene)
  //  * @param y   Y coord (in the scene)
  //  */
  // void shiftTo( float x, float y ) // x,y scene coords
  // {
  //   mPath.offset( x-cx, y-cy );
  //   setCenter( x, y );
  // }

//   @Override
//   void toCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind /* , DrawingUtil mDrawingUtil */ )
//   { 
//     int size = mScale - PointScale.SCALE_XS;
//     int layer  = BrushManager.getPointCsxLayer( mPointType );
//     int type   = BrushManager.getPointCsxType( mPointType );
//     int cat    = BrushManager.getPointCsxCategory( mPointType );
//     String csx = BrushManager.getPointCsx( mPointType );
//     pw.format("<item layer=\"%d\" cave=\"%s\" branch=\"%s\" type=\"%d\" category=\"%d\" transparency=\"0.00\" data=\"",
//       layer, cave, branch, type, cat );
//     pw.format("&lt;?xml version=&quot;1.0&quot; encoding=&quot;UTF-8&quot;?&gt;&lt;!DOCTYPE svg PUBLIC &quot;-//W3C//DTD SVG 1.1//EN&quot; &quot;http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd&quot;[]&gt;&lt;svg xmlns=&quot;http://www.w3.org/2000/svg&quot; xml:space=&quot;preserve&quot; style=&quot;shape-rendering:geometricPrecision; text-rendering:geometricPrecision; image-rendering:optimizeQuality; fill-rule:evenodd; clip-rule:evenodd&quot; xmlns:xlink=&quot;http://www.w3.org/1999/xlink&quot;&gt;&lt;defs&gt;&lt;style type=&quot;text/css&quot;&gt;&lt;![CDATA[ .str0 {stroke:#1F1A17;stroke-width:0.2} .fil0 {fill:none} ]]&gt;&lt;/style&gt;&lt;/defs&gt;&lt;g id=&quot;Livello_%d&quot;&gt;", layer );
//     pw.format("%s", csx );
//     pw.format("&lt;/g&gt;&lt;/svg&gt;\" ");
//     if ( bind != null ) pw.format(" bind=\"%s\" ", bind );
//     pw.format(Locale.US, "dataformat=\"0\" signsize=\"%d\" angle=\"%.2f\" >\n", size, mOrientation );
//     pw.format("  <pen type=\"10\" />\n");
//     pw.format("  <brush type=\"7\" />\n");
//     float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
//     float y = DrawingUtil.sceneToWorldY( cx, cy );
//     pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
//     pw.format("  <datarow>\n");
//     pw.format("  </datarow>\n");
//     pw.format("</item>\n");
// 
//     // TDLog.v( "toCSurvey() Point " + mPointType + " (" + x + " " + y + ") orientation " + mOrientation );
//   }

  /** export the point path in cSurvey format
   * @param pw      export writer
   * @param survey  survey name
   * @param cave    cave name
   * @param branch  branch name
   * @param bind    cSurvey binding
   */
  @Override
  void toTCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind )
  { 
    String name = getThName( );
    String options = getExportOptions();
    pw.format("<item type=\"point\" name=\"%s\" cave=\"%s\" branch=\"%s\" text=\"%s\" ", name, cave, branch, ((mPointText == null)? "" : mPointText) );
    if ( bind != null ) pw.format(" bind=\"%s\" ", bind );
    pw.format(Locale.US, "scale=\"%d\" orientation=\"%.2f\" options=\"%s\" >\n", mScale, mOrientation, ((options == null)? "" : options) );
    float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
    float y = DrawingUtil.sceneToWorldY( cx, cy );
    pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
    pw.format("</item>\n");
    // TDLog.v( "toCSurvey() Point " + mPointType + " (" + x + " " + y + ") orientation " + mOrientation );
  }

  /** export the point path in cSurvey format
   * @param pw      export writer
   * @param survey  survey name
   * @param cave    cave name
   * @param branch  branch name
   * @param bind    cSurvey binding
   * @param extra   ...
   * @param section X-Section info
   */
  void toTCsurvey( PrintWriter pw, String survey, String cave, String branch, String bind, String extra, PlotInfo section )
  { 
    String name = getThName( );
    String options = getExportOptions();
    pw.format("<item type=\"point\" name=\"%s\" cave=\"%s\" branch=\"%s\" text=\"%s\" ", name, cave, branch, ((mPointText == null)? "" : mPointText) );
    if ( bind != null ) pw.format("bind=\"%s\" ", bind );
    if ( extra != null ) pw.format("%s ", extra );
    pw.format(Locale.US, "scale=\"%d\" orientation=\"%.2f\" options=\"%s\" >\n", mScale, mOrientation, ((options == null)? "" : options) );
    float x = DrawingUtil.sceneToWorldX( cx, cy ); // convert to world coords.
    float y = DrawingUtil.sceneToWorldY( cx, cy );
    pw.format(Locale.US, " <points data=\"%.2f %.2f \" />\n", x, y );
    if ( section != null ) {
      // include cross-section
      // pw.format("    <crosssection id=\"%s\" design=\"0\" crosssection=\"%d\">\n", section.name, section.csxIndex );
      pw.format("    <crosssection>\n" );
      exportTCsxXSection( pw, section, survey, cave, branch );
      pw.format("    </crosssection>\n" );
      String subdir = TDInstance.survey + "/photo"; // "photo/" + TDInstance.survey;
      String filename = section.name + ".jpg";
      if ( TDFile.hasMSfile( subdir, filename ) ) { // if ( TDFile.hasMSpath( imagefilename ) )
        byte[] buf = TDExporter.readFileBytes( subdir, filename );
        if ( buf != null ) {
          pw.format("    <crosssectionfile>\n" );
          pw.format(" <attachment dataformat=\"0\" data=\"%s\" name=\"\" note=\"%s\" type=\"image/jpeg\" />\n", 
            Base64.encodeToString( buf, Base64.NO_WRAP ),
            ((mPointText==null)?"":mPointText)
          );
          pw.format("    </crosssectionfile>\n" );
        }
      }
    }
    pw.format("</item>\n");
    // TDLog.v( "toCSurvey() Point " + mPointType + " (" + x + " " + y + ") orientation " + mOrientation );
  }

  /** export the X-Section in cSurvey format
   * @param pw      export writer
   * @param section X-Section info
   * @param survey  survey name
   * @param cave    cave name
   * @param branch  branch name
   */
  private static void exportTCsxXSection( PrintWriter pw, PlotInfo section, String survey, String cave, String branch /* , String session */ )
  {
    if ( section == null ) return;
    // open xsection file
    // String filename = TDPath.getSurveyPlotTdrFile( survey, section.name );
    String filename = survey + "-" + section.name + ".tdr";
    DrawingIO.doExportTCsxXSection( pw, filename, survey, cave, branch, /* session, */ section.name /* , drawingUtil */ ); // bind=section.name
  }

  /** serialize point path to therion format
   * @return therion representation of the point
   */ 
  @Override
  String toTherion( )
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw  = new PrintWriter(sw);

    String th_name = getFullThName();
    pw.format(Locale.US, "point %.2f %.2f %s", cx*TDSetting.mToTherion, -cy*TDSetting.mToTherion, th_name );
    toTherionOrientation( pw );
    // FIXME SECTION_RENAME
    // if ( ! BrushManager.isPointSection( mPointType ) )
    toTherionTextOrValue( pw );
    toTherionOptions( pw );
    pw.format("\n");

    return sw.getBuffer().toString();
  }

  /** write orientation in therion format
   * @param pw   output writer
   */
  void toTherionOrientation( PrintWriter pw )
  {
    if ( mOrientation != 0.0 ) {
      pw.format(Locale.US, " -orientation %.2f", mOrientation);
    }
  }

  /** write text/value in therion format
   * @param pw   output writer
   */
  private void toTherionTextOrValue( PrintWriter pw )
  {
    if ( mPointText != null && mPointText.length() > 0 ) {
      if ( BrushManager.pointHasText(mPointType) ) { // label, remark
        pw.format(" -text \"%s\"", mPointText );
      } else if ( BrushManager.pointHasValue(mPointType) ) { // passage-height
        pw.format(" -value %s", mPointText );
      }
    }
  }

  String getExportOptions()
  {
    String options = BrushManager.isPointSection( mPointType ) ? SectionPointHelper.stripPlacementOptions( mOptions ) : mOptions;
    return SketchBrushStyleCodec.exportOptions( options );
  }

  /** write options in therion format
   * @param pw   output writer
   */
  void toTherionOptions( PrintWriter pw )
  {
    if ( mScale != PointScale.SCALE_M ) {
      pw.format( " -scale %s", PointScale.scaleToString( mScale ) );
      // switch ( mScale ) {
      //   case PointScale.SCALE_XS: pw.format( " -scale xs" ); break;
      //   case PointScale.SCALE_S:  pw.format( " -scale s" ); break;
      //   case PointScale.SCALE_L:  pw.format( " -scale l" ); break;
      //   case PointScale.SCALE_XL: pw.format( " -scale xl" ); break;
      // }
    }
    // FIXME SECTION_RENAME
    // if ( BrushManager.isPointSection( type ) ) {
    //   String scrapname = TDUtil.replacePrefix( TDInstance.survey, mOptions.replace( TDString.OPTION_SCRAP + " ", "") );
    //   pw.format(" %s %s-%s", TDString.OPTION_SCRAP, mApp.mSurvey, scrapname );
    // } else {
      String options = getExportOptions();
      if ( options != null && options.length() > 0 ) {
        pw.format(" %s", options );
      }
    // }
  }

  // override mScrap with scrap
  /** export point to data stream
   * @param dos    output stream
   * @param scrap  scrap index
   */
  @Override
  void toDataStream( DataOutputStream dos, int scrap )
  {
    String name  = getThName();
    if ( name == null ) {
      name = SymbolLibrary.USER;
      TDLog.e( "null point name" );
    }
    String group = BrushManager.getPointGroup(mPointType);
    try {
      dos.write( 'P' );
      dos.writeFloat( cx );
      dos.writeFloat( cy );
      dos.writeUTF( name );
      // if ( version >= 401147 )
        dos.writeUTF( (group != null)? group : "" );
      dos.writeFloat( (float)mOrientation );
      dos.writeInt( mScale );
      // if ( version >= 401090 )
        dos.writeInt( mLevel );
      // if ( version >= 401160 )
        dos.writeInt( (scrap >= 0)? scrap : mScrap ); 
      // if ( version >= 303066 ) 
        dos.writeUTF( (mPointText != null)? mPointText : "" );
      dos.writeUTF( (mOptions != null)? mOptions : "" );
      // TDLog.Log( TDLog.LOG_PLOT, "P " + name + " " + cx + " " + cy );
    } catch ( IOException e ) {
      TDLog.e( "POINT out error " + e.toString() );
    }
  }

  /** write point in Cave3D format
   * @param pw    writer
   * @param type  ...
   * @param cmd   command manager
   * @param num   data reduction
   */
  @Override
  void toCave3D( PrintWriter pw, int type, DrawingCommandManager cmd, TDNum num )
  {
    // cx,cy are in pixels divide by 20 to write coords in meters
    String name  = getThName();
    float x = DrawingUtil.sceneToWorldX( cx, cy );
    float y = DrawingUtil.sceneToWorldY( cx, cy );
    float v = 0;
    TDVector vv = cmd.getCave3Dv( cx, cy, num );
    if ( type == PlotType.PLOT_PLAN ) {
      v = vv.z;
    } else {
      v = y;
      x = vv.x;
      y = vv.y;
    }
    pw.format( Locale.US, "POINT %s %.1f %f %f %f\n", name, mOrientation, x, -y, -v );
  }

  /** write point in Cave3D format
   * @param pw    writer
   * @param type  ...
   * @param V1    ...
   * @param V2    ...
   */
  @Override
  void toCave3D( PrintWriter pw, int type, TDVector V1, TDVector V2 )
  {
    // cx,cy are in pixels divide by 20 to write coords in meters
    String name  = getThName();
    TDVector vv = DrawingPath.getCave3D( cx, cy, V1, V2 );
    pw.format( Locale.US, "POINT %s %.1f %f %f %f\n", name, mOrientation, vv.x,  vv.y, -vv.z );
  }

}
