/* @file SelectionSet.java
 *
 * @author marco corvi
 * @date feb 2013
 *
 * @brief set of selected drawing items
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * ----------------------------------------------------
 */
package com.topodroid.TDX;

// import com.topodroid.util.TDLog;

import java.util.ArrayList;
import java.util.IdentityHashMap;

class SelectionSet
{
  private static final float DISTANCE_EPS = 0.01f;

  private int mIndex;   // index of the "hot" item
  SelectionPoint mHotItem; 
  ArrayList< SelectionPoint > mPoints;

  SelectionSet()
  {
    mPoints = new ArrayList<>();
    reset();
  }

  private void reset()
  { 
    // TDLog.v( "selection set reset()");
    clearHotItemRange();
    mIndex = -1;
    mHotItem = null;
  }

  private void clearHotItemRange()
  {
    if ( mHotItem != null ) {
      mHotItem.mRange = null;
      // mHotItem.mLP1 = null;
      // mHotItem.mLP2 = null;
    }
  }

  // shift the hot item and return it (or return null)
  // SelectionPoint shiftHotItem( float dx, float dy, float range )
  SelectionPoint shiftHotItem( float dx, float dy )
  {
    if ( mHotItem == null ) return null;
    DrawingPath path = mHotItem.mItem;
    if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
      DrawingLinePath line = (DrawingLinePath)path;
      if ( BrushManager.isLineSection( line.mLineType ) ) return null;
    }
    // mHotItem.shiftBy( dx, dy, range );
    mHotItem.shiftBy( dx, dy );
    return mHotItem;
  }

  /** rotate the point of the hot item
   * @param dy amount of rotation
   */
  boolean rotateHotItem( float dy )
  {
    return ( mHotItem != null ) && mHotItem.rotateBy( dy );
  }

  SelectionPoint nextHotItem( )
  {
    mHotItem = null; // FIXME-HIDE
    if ( mPoints.size() > 0 ) {
      mIndex = ( mIndex + 1 ) % mPoints.size();
      mHotItem = mPoints.get( mIndex );
    }
    return mHotItem;
  }

  SelectionPoint prevHotItem( )
  {
    // mHotItem = null; // FIXME-HIDE
    if ( mPoints.size() > 0 ) {
      mIndex = ( mIndex + mPoints.size() - 1 ) % mPoints.size();
      mHotItem = mPoints.get( mIndex );
    }
    return mHotItem;
  }

  void addPoint( SelectionPoint pt ) { mPoints.add( pt ); }

  /** Bounds-based text hits precede the bucket query. Remove only the later
   * anchor duplicate while retaining every distinct overlapping text object.
   */
  void removeDuplicateTextItems()
  {
    IdentityHashMap< DrawingPath, Boolean > seen = new IdentityHashMap<>();
    for ( int i = 0; i < mPoints.size(); ) {
      SelectionPoint point = mPoints.get( i );
      if ( point.mItem instanceof DrawingLabelPath ) {
        if ( seen.containsKey( point.mItem ) ) {
          mPoints.remove( i );
          continue;
        }
        seen.put( point.mItem, Boolean.TRUE );
      }
      ++i;
    }
  }

  int size() { return mPoints.size(); }

  void clear() 
  { 
    // TDLog.v( "selection set clear()");
    mPoints.clear(); 
    reset();
  }

  // sort the array by the distances and set the "hot" index
  void sort() 
  {
    int size = mPoints.size();
    if ( size > 0 ) {
      for ( int k1 = 0; k1 < size; ++k1 ) {
        for ( int k2 = k1+1; k2 < size; ++k2 ) {
          SelectionPoint p1 = mPoints.get(k1);
          SelectionPoint p2 = mPoints.get(k2);
          if ( compareSelectionPoints( p1, p2 ) > 0 ) {
            mPoints.set( k1, p2 ); 
            mPoints.set( k2, p1 );
          }
        }
      }
      mIndex = 0;
      mHotItem = mPoints.get( mIndex );
    } else {
      mHotItem = null;
      mIndex = -1;
    }
  }

  private int compareSelectionPoints( SelectionPoint p1, SelectionPoint p2 )
  {
    float d1 = p1.getDistance();
    float d2 = p2.getDistance();
    if ( d1 + DISTANCE_EPS < d2 ) return -1;
    if ( d2 + DISTANCE_EPS < d1 ) return 1;

    int priority1 = getReferenceHandlePriority( p1 );
    int priority2 = getReferenceHandlePriority( p2 );
    if ( priority1 != priority2 ) return priority2 - priority1;
    return 0;
  }

  private int getReferenceHandlePriority( SelectionPoint point )
  {
    if ( point == null || ! point.isReferenceHandle() ) return 0;
    switch ( point.getHandleRole() ) {
      case ReferencePointHelper.HANDLE_ROTATE:
        return 3;
      case ReferencePointHelper.HANDLE_SCALE_NW:
      case ReferencePointHelper.HANDLE_SCALE_NE:
      case ReferencePointHelper.HANDLE_SCALE_SE:
      case ReferencePointHelper.HANDLE_SCALE_SW:
        return 2;
      case ReferencePointHelper.HANDLE_MOVE:
        return 1;
      default:
        return 0;
    }
  }

}
