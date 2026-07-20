/* @file DrawingOutlinePath.java
 *
 * @author marco corvi
 * @date sept 2017
 *
 * @brief TopoDroid drawing: outline-path
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.prefs.TDSetting;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

class DrawingOutlinePath
{
  private String mScrapName;  // scrap name of the xsection
  DrawingLinePath mPath;
  private int mScrapId;       // scrap index of the outline (non-negative)
  private DrawingPointPath mPoint;
  private RectF mBox;
  private List< DrawingReferencePath > mUnderlayPaths;
  private List< DrawingPath > mSketchPaths;
  private List< DrawingPath > mRefPaths;

  /** cstr
   * @param name     xsection scrap name
   * @param path     outline path
   * @param scrap_id ID of the scrap of the section point
   */ 
  DrawingOutlinePath( String name, DrawingLinePath path, int scrap_id )
  {
    mScrapName = name;
    mPath  = path;
    mScrapId = scrap_id;
    mPoint = null;
    mBox = null;
    mUnderlayPaths = null;
    mSketchPaths = null;
    mRefPaths = null;
  }

  /** cstr for a placed section overlay
   * @param name        xsection scrap name
   * @param point       section point
   * @param box         overlay box in scene coordinates
   * @param sketch      cached sketch paths
   * @param refs        cached reference paths
   * @param scrap_id    scrap index of the section point
   */
  DrawingOutlinePath( String name, DrawingPointPath point, RectF box,
                      List< DrawingReferencePath > underlays, List< DrawingPath > sketch, List< DrawingPath > refs, int scrap_id )
  {
    mScrapName = name;
    mPath  = null;
    mScrapId = scrap_id;
    mPoint = point;
    mBox = box;
    mUnderlayPaths = underlays;
    mSketchPaths = sketch;
    mRefPaths = refs;
  }

  // DEBUG
  // String getScrapName() { return (mScrapName != null )? mScrapName : "none"; }

  /** @return true if the given name is the scrap name
   * @param name   given name
   */
  boolean isScrapName( String name ) { return mScrapName.equals( name ); }

  /** @return true if the given ID is the scrap_id of the section point
   * @param id   given id
   */
  boolean isScrapId( int id ) { return id == mScrapId; }

  boolean isPlaced() { return mPoint != null && mBox != null; }

  DrawingPointPath getPoint() { return mPoint; }

  RectF getBox() { return mBox; }

  /** shift the overlay content together with its point
   * @param dx X shift
   * @param dy Y shift
   */
  void shiftBy( float dx, float dy )
  {
    if ( isPlaced() ) {
      if ( mUnderlayPaths != null ) for ( DrawingReferencePath path : mUnderlayPaths ) shiftPath( path, dx, dy );
      if ( mSketchPaths != null ) for ( DrawingPath path : mSketchPaths ) shiftPath( path, dx, dy );
      if ( mRefPaths != null ) for ( DrawingPath path : mRefPaths ) shiftPath( path, dx, dy );
      if ( mBox != null ) mBox.offset( dx, dy );
    } else if ( mPath != null ) {
      mPath.shiftBy( dx, dy );
    }
  }

  private void shiftPath( DrawingPath path, float dx, float dy )
  {
    if ( path == null ) return;
    if ( path instanceof DrawingSplayPath ) {
      path.shiftPathBy( dx, dy );
      DrawingSplayPath splay = (DrawingSplayPath)path;
      splay.xEnd += dx;
      splay.yEnd += dy;
    } else if ( path.mType == DrawingPath.DRAWING_PATH_FIXED || path.mType == DrawingPath.DRAWING_PATH_NORTH ) {
      path.shiftPathBy( dx, dy );
    } else {
      path.shiftBy( dx, dy );
    }
  }

  /** draw the overlay
   * @param canvas   canvas
   * @param matrix   scene-to-canvas transform
   * @param scale    current scale
   * @param bbox     visible scene bounding box
   * @param edit_box whether to show the box
   */
  void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, boolean edit_box )
  {
    if ( ! isPlaced() ) {
      if ( mPath != null ) mPath.draw( canvas, matrix, null );
      return;
    }
    if ( mBox == null ) return;
    if ( bbox != null && ! RectF.intersects( bbox, mBox ) ) return;

    int save = canvas.save();
    RectF clip = new RectF( mBox );
    matrix.mapRect( clip );
    canvas.clipRect( clip );

    drawPaths( canvas, matrix, scale, mSketchPaths );
    drawPaths( canvas, matrix, scale, mRefPaths );
    canvas.restoreToCount( save );

    if ( edit_box ) {
      Path box = new Path();
      box.addRect( mBox, Path.Direction.CW );
      box.transform( matrix );
      canvas.drawPath( box, BrushManager.fixedYellowPaint );
    }
  }

  private void drawPaths( Canvas canvas, Matrix matrix, float scale, List< DrawingPath > paths )
  {
    if ( paths == null ) return;
    boolean area_overlap_darken = TDSetting.mAreaOverlapDarken;
    boolean has_area = false;
    if ( area_overlap_darken ) {
      for ( DrawingPath path : paths ) {
        if ( path != null && path.isArea() && ! Scrap.isPatternedArea( path ) ) {
          has_area = true;
          break;
        }
      }
    }
    if ( has_area ) {
      int area_layer = canvas.saveLayer( 0, 0, canvas.getWidth(), canvas.getHeight(), null );
      for ( DrawingPath path : paths ) {
        if ( path != null && path.isArea() && ! Scrap.isPatternedArea( path ) ) {
          path.draw( canvas, matrix, scale, mBox );
        }
      }
      canvas.restoreToCount( area_layer );
    }
    Scrap.drawPatternedAreaGroups( canvas, matrix, mBox, false, paths );
    for ( DrawingPath path : paths ) {
      if ( path == null ) continue;
      if ( path.isArea() && ( area_overlap_darken || Scrap.isPatternedArea( path ) ) ) continue;
      path.draw( canvas, matrix, scale, mBox );
    }
  }

  void drawUnderlay( Canvas canvas, Matrix matrix, RectF bbox )
  {
    if ( ! isPlaced() || mBox == null || mUnderlayPaths == null || mUnderlayPaths.size() == 0 ) return;
    if ( bbox != null && ! RectF.intersects( bbox, mBox ) ) return;

    int save = canvas.save();
    RectF clip = new RectF( mBox );
    matrix.mapRect( clip );
    canvas.clipRect( clip );
    for ( DrawingReferencePath path : mUnderlayPaths ) {
      if ( path != null ) path.drawUnderlay( canvas, matrix, mBox );
    }
    canvas.restoreToCount( save );
  }

}
