/* @file DrawingSurface.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid drawing: drawing surface (canvas)
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.util.TDFile;
import com.topodroid.util.TDString;
import com.topodroid.num.NumStation;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PlotType;

import android.content.Context;

import android.app.Activity;
// import android.os.Handler;
// import android.os.Message;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
// import android.view.View;

// import android.view.MotionEvent;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PointF;
import android.graphics.PorterDuff;

import java.util.ArrayList;
// import java.util.TreeSet;
// import java.util.Collections;
// import java.util.Iterator;
import java.util.List;
import java.util.HashMap;

import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.DataInputStream;

// import java.util.Timer;
// import java.util.TimerTask;

/**
 */
public class DrawingSurface extends SurfaceView // TH2EDIT was package
                            implements SurfaceHolder.Callback
                            , IDrawingSurface
{
  static final int DRAWING_PLAN     = 1;
  static final int DRAWING_PROFILE  = 2;
  static final int DRAWING_SECTION  = 3;
  static final int DRAWING_OVERVIEW = 4;

  protected DrawThread mDrawThread;

  private boolean mSurfaceCreated = false;

  // RENDER-ON-DEMAND: the draw thread renders a frame only when the scene is
  // dirty (requestRender), during a full-rate burst window (dialog returns,
  // resume, focus regain), or when the failsafe heartbeat expires. Previously
  // the surface re-rendered continuously at ~1ms intervals while resumed,
  // holding the draw locks against the UI thread and loading the CPU even
  // when nothing changed.
  private volatile boolean mResumedRender = true;             // resume/pause render gate
  private final java.util.concurrent.atomic.AtomicBoolean mDirty = new java.util.concurrent.atomic.AtomicBoolean( true );
  private volatile long mLastFrameMs = 0;                     // last rendered frame [ms epoch]
  private volatile long mBurstUntilMs = 0;                    // full-rate rendering until [ms epoch]
  private static final long RENDER_HEARTBEAT_MS = 5000;       // failsafe frame period when idle (field-tuned on Tab Active 3)
  private static final long RENDER_BURST_MS     = 7500;       // burst duration on resume/focus (field-tuned on Tab Active 3)
  static final long GESTURE_BURST_MS            = 400;        // burst per touch event: steady frame pacing through gestures

  // SCENE CACHE: the committed scene is rendered into an off-screen bitmap
  // (1.25x the viewport) by a background pipeline; frames blit it with the
  // delta between the live transform and the cache transform. At rest the
  // blit is 1:1 and unfiltered - pixel-identical by construction. During
  // pan/zoom gestures the blit is a filtered transform of the cached pixels
  // (content briefly resamples; screen-constant items scale) and the
  // pipeline keeps rebuilding toward the live transform, converging to a
  // sharp frame when the gesture settles. Scene mutations bump the version
  // and force a direct render + rebuild. Exports and drawCanvas bypass the
  // cache entirely.
  static volatile boolean sSceneCacheEnabled = true;           // kill-switch
  private static final float CACHE_MARGIN_FACTOR = 0.125f;     // extra margin per side, fraction of viewport
  private static final java.util.concurrent.ExecutorService sCacheBuilder =
    java.util.concurrent.Executors.newSingleThreadExecutor( r -> {
      Thread t = new Thread( r, "scene-cache" );
      t.setDaemon( true );
      return t;
    } );
  private final java.util.concurrent.atomic.AtomicLong mSceneVersion = new java.util.concurrent.atomic.AtomicLong( 0 );
  private final Object mCacheLock = new Object();              // guards the front-cache record below
  private Bitmap mCacheFront = null;
  private final Matrix mCacheMatrix = new Matrix();            // scene -> cache-pixel transform of the front
  private long   mCacheVersion = -1;                           // scene version the front was built from
  private DrawingCommandManager mCacheOwner = null;            // manager the front belongs to
  private int    mCacheMx = 0, mCacheMy = 0;                   // cache margins [px]
  private int    mCacheForW = 0, mCacheForH = 0;               // viewport size the front was built for
  private float  mCacheDx = 0, mCacheDy = 0, mCacheZoom = 1;   // view params at build
  private boolean mCacheLandscape = false;
  private Bitmap mCacheBack = null;                            // builder-thread private
  private volatile boolean mCacheBuilding = false;
  private final Matrix mBlitInverse = new Matrix();            // render-thread scratch
  private final Matrix mBlitDelta   = new Matrix();            // render-thread scratch
  private final Paint  mBlitPaint   = new Paint( Paint.FILTER_BITMAP_FLAG );
  // live view transform snapshot (written by setTransform, read by the builder)
  private volatile float mViewDx = 0, mViewDy = 0, mViewZoom = 1;
  private volatile boolean mViewLandscape = false;

  private DrawingPath mPreviewPath;
  // private SurfaceHolder mHolder; // canvas holder
  // private final Context mContext;
  // private AttributeSet mAttrs;
  private IZoomer mZoomer = null;
  private int mWidth;            // canvas width
  private int mHeight;           // canvas height
  private long mType; 

  static private DrawingCommandManager commandManager = null; 

  static private DrawingCommandManager mCommandManager1 = null;
  static private DrawingCommandManager mCommandManager2 = null;
  static private DrawingCommandManager mCommandManager3 = null;

  private DrawingStationSplay mStationSplay; // splays on/off at stations


  // -----------------------------------------------------
  // SCRAPS 

  /** get the current scrap index
   */
  int scrapIndex() { return ( commandManager == null )? 0 : commandManager.scrapIndex(); }

  // /** get the maximum scrap index
  //  */
  // int scrapMaxIndex() { return ( commandManager == null )? 0 : commandManager.scrapMaxIndex(); }

  /** get the number of scraps
   */
  int scrapNumber() { return ( commandManager == null )? -1 : commandManager.scrapNumber(); }

  /** get the number (index in the list) of the current scrap
   */
  int currentScrapNumber() { return ( commandManager == null )? -1 : commandManager.currentScrapNumber(); }

  /** get a new scrap index 
   * @param force   whether to do also for command-3 // TH2EDIT no force param
   */
  public int newScrapIndex( boolean force )
  {
    if ( commandManager == null ) return 0;
    int ret = commandManager.newScrapIndex( force );
    requestSceneRender(); // the current scrap changed: the whole rendered content is different
    return ret;
  }

  /** toggle the scrap index
   * @param force   whether to do also for command-3 // TH2EDIT no force param
   * @param k       advance step
   */
  int toggleScrapIndex( boolean force, int k )
  {
    if ( commandManager == null ) return 0;
    int ret = commandManager.toggleScrapIndex( force, k );
    requestSceneRender(); // the current scrap changed: the whole rendered content is different
    return ret;
  }

  /** delete the current scrap
   * @param force   whether to do also for command-3 // TH2EDIT no force param
   */
  void deleteCurrentScrap( boolean force )
  {
    if ( commandManager == null ) return;
    if ( ( ! force ) && commandManager == mCommandManager3 ) return;
    commandManager.deleteCurrentScrap( force );
    requestSceneRender();
  }

  // TH2EDIT
  public boolean setScrapOptions( int idx, String options )
  {
    boolean ret = commandManager != null && commandManager.setScrapOptions(idx, options);
    if ( ret ) requestSceneRender();
    return ret;
  }

  // -----------------------------------------------------
  // MANAGER CACHE
  static private HashMap<String, DrawingCommandManager> mCache = new HashMap<String, DrawingCommandManager>();

  /** clear the cache of managers
   * @note the cache is cleared when the survey is renamed
   */
  static void clearManagersCache()
  {
    mCache.clear();
    // TDLog.Log( TDLog.LOG_IO, "clear managers cache");
  }

  // static void dumpCacheKeys()
  // {
  //   for ( String key : mCache.keySet() ) TDLog.Log( TDLog.LOG_IO, "Key: " + key );
  // }

  /** add a manager to the cache
   * @param fullname    fullname of the plot (of the manager)
   */
  private static void addManagerToCache( String fullname ) 
  { 
    if ( TDSetting.mPlotCache && commandManager != null ) {
      // if ( mCache.get( fullname ) != null ) {
      //   TDLog.Log( TDLog.LOG_IO, "replace manager into cache " + fullname );
      // } else {
      //   TDLog.Log( TDLog.LOG_IO, "add manager to cache " + fullname );
      // }
      mCache.put( fullname, commandManager );
      // if ( TDLog.LOG_IO ) dumpCacheKeys();
    }
  }

  /** reset a manager
   * @param mode        command-manager mode (plot type)
   * @param fullname    ...
   * @param is_extended whether it is an extended profile
   * @return true if saved manager can be used
   */
  boolean resetManager( int mode, String fullname, boolean is_extended )
  {
    boolean ret = false;
    DrawingCommandManager manager = null;

    // TDLog.v( "cache size " + mCache.size() );

    if ( mode == DRAWING_PLAN ) {
      if ( TDSetting.mPlotCache && fullname != null ) manager = mCache.get( fullname );
      // TDLog.Log( TDLog.LOG_IO, "check out PLAN from cache " + fullname + " found: " + (manager!=null) );
      if ( manager == null ) {
        mCommandManager1 = new DrawingCommandManager( DRAWING_PLAN, fullname );
      } else {
        mCommandManager1 = manager;
        mCommandManager1.setDisplayPoints( false );
        ret = true;
      }
      commandManager = mCommandManager1;
    } else if ( mode == DRAWING_PROFILE ) {
      if ( TDSetting.mPlotCache && fullname != null ) manager = mCache.get( fullname );
      // TDLog.Log( TDLog.LOG_IO, "check out PROFILE from cache " + fullname + " found: " + (manager!=null) );
      if ( manager == null ) {
        mCommandManager2 = new DrawingCommandManager( DRAWING_PROFILE, fullname );
	if ( is_extended ) mCommandManager2.mIsExtended = true;
      } else {
        mCommandManager2 = manager;
        mCommandManager2.setDisplayPoints( false );
        ret = true;
      }
      commandManager = mCommandManager2;
    } else {
      if ( mCommandManager3 == null ) {
        mCommandManager3 = new DrawingCommandManager( mode, fullname );
      } else {
        mCommandManager3.clearDrawing();
      }
      commandManager = mCommandManager3;
    }
    return ret;
  }

  // -----------------------------------------------------

  /** set the mode of selection
   * @param mode   selection mode
   */
  void setSelectMode( int mode )
  {
    if ( commandManager != null ) commandManager.setSelectMode( mode );
    requestSceneRender();
  }

  /** set the manager
   * @param mode    manager mode
   * @param type    plot type
   */
  void setManager( int mode, int type )
  {
    mType = type;
    // TDLog.v( " set manager type " + type );
    if ( mode == DRAWING_PROFILE ) {
      commandManager = mCommandManager2;
    } else if ( mode == DRAWING_PLAN ) {
      commandManager = mCommandManager1;
    } else {
      commandManager = mCommandManager3;
    }
  }

  /** start to create the reference of a manager (and set the manager)
   * @param mode    manager mode
   * @param type    plot type
   */
  void newReferences( int mode, int type )
  {
    setManager( mode, type );
    commandManager.newReferences();
    requestSceneRender();
  }

  /** commit the reference
   */
  void commitReferences()
  {
    commandManager.commitReferences();
    requestSceneRender();
  }


  // -----------------------------------------------------

  /** @return the canvas width
   */
  public int width()  { return mWidth; }

  // /** @return the canvas height - UNUSED
  //  */
  // public int height() { return mHeight; }

  /** @return the sketch drawing scale
   */
  float getScale() { return (commandManager == null) ? 1.0f  : commandManager.getScale(); }

  // private Timer mTimer;
  // private TimerTask mTask;

  // /** test if the surface is selectable - UNUSED
  //  * @return true if the surface items are selectable
  //  */
  // boolean isSelectable() { return commandManager != null && commandManager.isSelectable(); }

  /** set the zoomer
   * @param zoomer   zoomer
   */
  void setZoomer( IZoomer zoomer ) { mZoomer = zoomer; }

  /** cstr
   * @param context context
   * @param attrs   attributes
   */
  public DrawingSurface(Context context, AttributeSet attrs) 
  {
    super(context, attrs);
    mWidth = 0;
    mHeight = 0;

    mDrawThread = null;
    // mContext = context;
    // mAttrs   = attrs;
    // mHolder = getHolder();
    // mHolder.addCallback(this);
    getHolder().addCallback(this);
    // mCommandManager1 = new DrawingCommandManager();
    // mCommandManager2 = new DrawingCommandManager();
    commandManager = mCommandManager3;
    mStationSplay  = new DrawingStationSplay();
  }

  // -------------------------------------------------------------------

  /** set whether to display points, in the current manager
   * @param display  whether to display the points
   */
  void setDisplayPoints( boolean display )
  {
    commandManager.setDisplayPoints( display );
    if ( display ) {
    } else {
      commandManager.syncClearSelected();
    }
    requestSceneRender();
  }

  /** get the index for the next area item, in the current manager
   * @return index for the next area item
   */
  int getNextAreaIndex() { return commandManager.getNextAreaIndex(); }

  // void setScaleBar( float x0, float y0 ) 
  // { 
  //   commandManager.setScaleBar(x0,y0);
  // }
  
  /** get the shots that intersect a line portion, in the current manager
   * @param p1 first point of the line portion
   * @param p2 second point of the line portion
   * @return the list of shots that intersects the segment (p1--p2)
   */
  List< DrawingPathIntersection > getIntersectionShot( LinePoint p1, LinePoint p2 )
  {
    return commandManager.getIntersectionShot(p1, p2);
  }

  /* FIXME_HIGHLIGHT
  void highlights( TopoDroidApp app ) { 
    // TDLog.v( "surface [3] highlight: nr. " + app.getHighlightedSize() );
    if ( mCommandManager2 != null ) {
      mCommandManager1.highlights( app );
      mCommandManager2.highlights( app );
    }
  }
  */

  // -----------------------------------------------------------

  /** set the global display mode
   * @param mode   display mode
   */
  public void setDisplayMode( int mode ) { DrawingCommandManager.setDisplayMode(mode); }

  /** get the global display mode
   * @return the global display mode
   */
  public int getDisplayMode( ) { return DrawingCommandManager.getDisplayMode(); }

  /** set the transform in the current manager
   * @param act    activity
   * @param dx     X shift
   * @param dy     Y shift
   * @param s      scale
   * @param landscape whether landscape-presentation
   * 
   * the transformation is
   *  X -> (x+dx)*s = x*s + dx*s
   *  Y -> (y+dy)*s = y*s + dy*s
   */
  public void setTransform( Activity act, float dx, float dy, float s, boolean landscape )
  {
    mViewDx = dx;
    mViewDy = dy;
    mViewZoom = s;
    mViewLandscape = landscape;
    if ( commandManager != null ) // test for Xiaomi redmi note
      commandManager.setTransform( act, dx, dy, s, landscape );
    requestRender(); // transform changes never invalidate the scene cache - that is the point of the cache
  }

  /** split a line at a point, in the current manager
   * @param line   line
   * @param lp     line point where to split
   */
  void splitLine( DrawingLinePath line, LinePoint lp ) { commandManager.splitLine( line, lp ); requestSceneRender(); }

  /** remove a line point, in the current manager
   * @param line   line
   * @param point  line point
   * @param sp     selection point
   * @return true if the point was removed
   */
  boolean removeLinePoint( DrawingPointLinePath line, LinePoint point, SelectionPoint sp )
  { boolean ret = commandManager.removeLinePoint(line, point, sp); requestSceneRender(); return ret; }

  /** remove a line point from the selection, in the current manager
   * @param line   line
   * @param point  line point
   * @return true if the point was removed
   */
  boolean removeLinePointFromSelection( DrawingLinePath line, LinePoint point )
  { boolean ret = commandManager.removeLinePointFromSelection( line, point ); requestSceneRender(); return ret; }

  /** remove a splay path, in the plan and profile manager
   * @param p   splay path
   * @param sp  selection point for the path p
   *
   * @note this must be called only by plan or profile
   */
  void deleteSplay( DrawingSplayPath p, SelectionPoint sp )
  {
    mCommandManager1.deleteSplay( p, sp );
    if ( mCommandManager2 != null ) {
      mCommandManager2.deleteSplay( p, sp );
    }
    requestSceneRender();
  }

  /** remove a path, from the current manager
   * @param path   path to remove
   */
  void deletePath( DrawingPath path )
  {
    EraseCommand cmd = new EraseCommand();
    commandManager.deletePath( path, cmd );
    commandManager.addEraseCommand( cmd );
    requestSceneRender();
  }

  // PATH_MULTISELECTION
  /** @return true if a multiselection is going on
   */ 
  boolean isMultiselection()  { return commandManager.isMultiselection(); }

  /** @return the multiselection type (-1 none)
   */
  int getMultiselectionType() { return commandManager.getMultiselectionType(); }

  /** start the multiselection
   */
  void startMultiselection()  { commandManager.startMultiselection(); requestSceneRender(); }

  /** clear the multiselection
   */
  void resetMultiselection() { commandManager.resetMultiselection(); requestSceneRender(); }

  /** store the current multiselection
   */
  void storeMultiselection() { commandManager.storeMultiselection(); requestSceneRender(); }

  /** join the lines in the current multiselection
   * @param dcim     maximum gap for join
   */
  void joinMultiselection( float dmin ) { commandManager.joinMultiselection( dmin ); requestSceneRender(); }

  /** delete the items in the current multiselection
   */
  void deleteMultiselection() { commandManager.deleteMultiselection(); requestSceneRender(); }

  /** decimate the lines in the current multiselection
   */
  void decimateMultiselection() { commandManager.decimateMultiselection(); requestSceneRender(); }

  /** restore the stored multiselection
   */
  void restoreMultiselection() { commandManager.restoreMultiselection(); requestSceneRender(); }

  /** @return true if there is a stored multiselection
   */
  boolean hasStoredMultiselection() { return commandManager.hasStoredMultiselection(); }
  // end PATH_MULTISELECTION

  /** sharpen a line, in the current manager
   * @param line   line
   */
  void sharpenPointLine( DrawingPointLinePath line ) { commandManager.sharpenPointLine( line ); requestSceneRender(); }

  /** decimate a line, in the current manager
   * @param line   line
   * @param decimation   log-decimation 
   */
  void reducePointLine( DrawingPointLinePath line, int decimation ) { commandManager.reducePointLine( line, decimation ); requestSceneRender(); }

  /** make a line rock-like, in the current manager
   * @param line   line
   * @note called by drawing window, forward to comman manager
   */
  void rockPointLine( DrawingPointLinePath line ) { commandManager.rockPointLine( line ); requestSceneRender(); }

  /** close a line, in the current manager
   * @param line   line
   */
  void closePointLine( DrawingPointLinePath line ) { commandManager.closePointLine( line ); requestSceneRender(); }

  /** finish an erase command, in the current manager
   */
  void endEraser() { commandManager.endEraser(); requestRender(); } // eraser is a live overlay, not cached scene content

  /** set the eraser circle, in the current manager
   * @param x    X canvas coords
   * @param y    Y canvas coords
   * @param r    circle radius
   */
  void setEraser( float x, float y, float r ) { commandManager.setEraser(x, y, r); requestRender(); } // canvas x,y, r - live overlay, not cached

  /** erase at a position, in the current manager
   * @param x    X scene coords
   * @param y    Y scene coords
   * @param zoom current zoom (the larger the zoom, the bigger the sketch on the display)
   * @param cmd  erase command
   * @param erase_mode  erasing mode
   * @param erase_size  eraser size
   */
  void eraseAt( float x, float y, float zoom, EraseCommand cmd, int erase_mode, float erase_size )
  { commandManager.eraseAt( x, y, zoom, cmd, erase_mode, erase_size ); requestSceneRender(); }
  
  /** add an erase command in the current manager
   * @param cmd   erase command
   */
  void addEraseCommand( EraseCommand cmd )
  {
    commandManager.addEraseCommand( cmd );
    requestSceneRender();
  }

  /**
   * @param mode   drawing mode 
   * @param type   plot type ( unused )
   * @param decl   declination [degrees]
   */
  void addScaleRef( int mode, int type, float decl )
  {
    switch ( mode ) {
      case DRAWING_PLAN: 
        mCommandManager1.addScaleRef( decl ); // true ); // true = with extendAzimuth
        break;
      case DRAWING_PROFILE:
        mCommandManager2.addScaleRef( decl );
        break;
      default:
        mCommandManager3.addScaleRef( decl );
    }
    requestSceneRender();
  }

  /** clear shots and stations - only extended profile
   * @param type   plot type
   */
  void clearShotsAndStations( int type )
  {
    if ( PlotType.isExtended( type ) ) {
      mCommandManager2.clearShotsAndStations();
    }
    requestSceneRender();
  }

  /** clear shots and stations - both plan and profile
   */
  void clearShotsAndStations( )
  {
    mCommandManager1.clearShotsAndStations();
    mCommandManager2.clearShotsAndStations();
    requestSceneRender();
  }

  // /** clear the reference - UNUSED
  //  * @param type   plot type
  //  */
  // void clearReferences( int type ) 
  // {
  //   if ( PlotType.isProfile( type ) ) {
  //     mCommandManager2.clearReferences();
  //   } else if ( type == PlotType.PLOT_PLAN ) {
  //     mCommandManager1.clearReferences();
  //   } else {
  //     mCommandManager3.clearReferences();
  //   }
  // }

  /** flip the profile - only profile manager
   * @param z   ???
   * @param scrap  whether ti flip only the current scrap
   */
  void flipProfile( float z, boolean scrap )
  {
    if ( mCommandManager2 == null ) return;
    mCommandManager2.flipXAxis( z, scrap );
    requestSceneRender();
  }

  // static Handler previewDoneHandler = new Handler()
  // {
  //   @Override
  //   public void handleMessage(Message msg) {
  //     isDrawing = false;
  //   }
  // };

  // synchronized void clearPreviewPath() { mPreviewPath = null; }

  /** reset the preview path: if null create an empty path
   * @note also called by DrawingWindow on split
   */
  synchronized void resetPreviewPath() { if ( mPreviewPath != null ) mPreviewPath.mPath = new Path(); requestRender(); } // preview draws over the cache

  /** create the preview path
   * @param type   path type
   * @param paint  path paint
   */
  synchronized void makePreviewPath( int type, Paint paint ) // type = kind of the path
  {
    mPreviewPath = new DrawingPath( type, null, -1 );
    mPreviewPath.mPath = new Path();
    mPreviewPath.setPathPaint( paint );
    requestRender(); // preview draws over the cache
  }

  /** get the preview path
   * @return the preview path, or null
   * @note callers mutate the returned Path directly (brush mouse-move), so a
   *       render is requested here as well - the touch handler also requests
   *       one per event
   */
  Path getPreviewPath() { requestRender(); return (mPreviewPath != null)? mPreviewPath.mPath : null; } // preview draws over the cache

  // ------------------ IDrawingSurface -----------------------

  /** draw a canvas - for screenshot 
   * @param canvas canvas
   * @return true on success
   */
  boolean drawCanvas( Canvas canvas )
  {
    try {
      canvas.drawColor(0, PorterDuff.Mode.CLEAR);
      commandManager.executeAll( canvas, mZoomer.zoom(), mStationSplay, false ); // false = no inverted_color
      return true;
    } catch ( Throwable e ) {
      e.printStackTrace();
    }
    return false;
  }
      

  /** refresh the surface
   * @param holder   surface holder
   */
  public void refresh( SurfaceHolder holder )
  {
    // if ( mZoomer != null ) mZoomer.checkZoomBtnsCtrl();
    Canvas canvas = null;
    try {
      canvas = holder.lockCanvas();
      // canvas.drawColor(0, PorterDuff.Mode.CLEAR);
      if ( canvas != null && commandManager != null ) {
        mDirty.set( false ); // cleared before drawing: mutations landing mid-frame re-dirty and get the next frame
        mWidth  = canvas.getWidth();
        mHeight = canvas.getHeight();
        RenderPerf.frameStart();
        canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        if ( ! drawFromSceneCache( canvas, commandManager, mZoomer.zoom() ) ) {
          commandManager.executeAll( canvas, mZoomer.zoom(), mStationSplay, false ); // false = no inverted_color
          scheduleSceneCacheBuild(); // direct render happened: refresh the cache in background
        }
        if ( mPreviewPath != null ) mPreviewPath.draw(canvas, null);
        DrawingPath live = mLiveItem;
        if ( live != null ) { // item being placed: overlay on top of the (cached or direct) scene
          live.draw( canvas, commandManager.getMatrixRef(), commandManager.getScale(), null );
        }
        RenderPerf.frameEnd();
      }
    } finally {
      if ( canvas != null ) {
        holder.unlockCanvasAndPost( canvas ); // FIXME IllegalArgumentException ???
      }
      mLastFrameMs = System.currentTimeMillis();
    }
  }

  /** draw the frame from the scene cache if it is usable
   * @param canvas   target canvas
   * @param manager  current command manager
   * @param zoom     current canvas zoom (for the scalebar)
   * @return true if the frame was drawn from the cache
   * @note runs on the render thread; the bitmap is drawn under mCacheLock so
   *       the builder can never swap-and-overwrite a bitmap mid-blit
   */
  private boolean drawFromSceneCache( Canvas canvas, DrawingCommandManager manager, float zoom )
  {
    if ( ! sSceneCacheEnabled ) return false;
    if ( System.currentTimeMillis() < mCacheSuppressedUntilMs ) return false; // export in progress: direct render
    synchronized ( mCacheLock ) {
      if ( mCacheFront == null || mCacheFront.isRecycled() ) return false;
      if ( mCacheOwner != manager ) return false;                          // plot/manager switched
      if ( mCacheVersion != mSceneVersion.get() ) return false;            // scene mutated
      if ( mCacheForW != mWidth || mCacheForH != mHeight ) return false;   // viewport resized
      manager.drawUnderDecos( canvas );
      if ( mCacheDx == mViewDx && mCacheDy == mViewDy && mCacheZoom == mViewZoom && mCacheLandscape == mViewLandscape ) {
        // at-rest 1:1 blit: the cache pixels ARE the rendered frame (pixel-identical, unfiltered)
        canvas.drawBitmap( mCacheFront, -mCacheMx, -mCacheMy, null );
      } else {
        // gesture blit: cache pixels through (live transform) x (cache transform)^-1
        if ( ! mCacheMatrix.invert( mBlitInverse ) ) return false;
        mBlitDelta.set( mBlitInverse );
        mBlitDelta.postConcat( manager.getMatrixRef() );
        int save = canvas.save();
        canvas.concat( mBlitDelta );
        canvas.drawBitmap( mCacheFront, 0, 0, mBlitPaint );
        canvas.restoreToCount( save );
        scheduleSceneCacheBuild(); // converge toward the live transform
      }
    }
    manager.drawOverDecos( canvas, zoom );
    return true;
  }

  /** kick a background scene-cache build (single-flight; the builder loops
   *  until the built cache matches the live transform and scene version) */
  void scheduleSceneCacheBuild()
  {
    if ( ! sSceneCacheEnabled || mCacheBuilding ) return;
    if ( System.currentTimeMillis() < mCacheSuppressedUntilMs ) return; // export in progress: keep the heap free
    if ( mWidth <= 0 || mHeight <= 0 ) return;
    mCacheBuilding = true;
    sCacheBuilder.execute( this::buildSceneCache );
  }

  private void buildSceneCache()
  {
    try {
      boolean again = true;
      while ( again && sSceneCacheEnabled ) {
        again = false;
        DrawingCommandManager manager = commandManager;
        int w = mWidth;
        int h = mHeight;
        if ( manager == null || w <= 0 || h <= 0 ) return;
        long version = mSceneVersion.get();
        float dx = mViewDx, dy = mViewDy, s = mViewZoom;
        boolean land = mViewLandscape;
        if ( s <= 0 ) return;

        int mx = Math.round( w * CACHE_MARGIN_FACTOR );
        int my = Math.round( h * CACHE_MARGIN_FACTOR );
        int cw = w + 2 * mx;
        int ch = h + 2 * my;
        if ( mCacheBack == null || mCacheBack.isRecycled()
          || mCacheBack.getWidth() != cw || mCacheBack.getHeight() != ch ) {
          mCacheBack = Bitmap.createBitmap( cw, ch, Bitmap.Config.ARGB_8888 );
        } else {
          mCacheBack.eraseColor( 0 );
        }

        Matrix matrix = new Matrix();
        RectF  bbox   = new RectF();
        DrawingCommandManager.buildSceneTransform( dx, dy, s, land, cw, ch, mx, my, matrix, bbox );
        Canvas canvas = new Canvas( mCacheBack );
        manager.executeAll( canvas, s, mStationSplay, false, matrix, 1 / s, bbox, false ); // no screen decos

        synchronized ( mCacheLock ) {
          Bitmap old = mCacheFront;
          mCacheFront = mCacheBack;
          mCacheBack  = old; // reused as the next build target once no frame references it
          mCacheMatrix.set( matrix );
          mCacheVersion   = version;
          mCacheOwner     = manager;
          mCacheMx = mx;  mCacheMy = my;
          mCacheForW = w; mCacheForH = h;
          mCacheDx = dx;  mCacheDy = dy;
          mCacheZoom = s; mCacheLandscape = land;
        }
        requestRender(); // present the fresh cache

        // converge: view moved or scene changed while rendering
        if ( version != mSceneVersion.get()
          || dx != mViewDx || dy != mViewDy || s != mViewZoom || land != mViewLandscape
          || w != mWidth || h != mHeight ) again = true;
      }
    } catch ( OutOfMemoryError e ) {
      TDLog.e( "scene cache oom " + e.getMessage() );
      synchronized ( mCacheLock ) { mCacheFront = null; }
      mCacheBack = null;
    } finally {
      mCacheBuilding = false;
    }
  }

  /** invalidate the scene cache: call after any change to the rendered scene
   *  content (the transform alone does NOT invalidate - that is the point) */
  private void invalidateSceneCache() { mSceneVersion.incrementAndGet(); }

  // LIVE ITEM: a drawing item being placed/adjusted by an active gesture
  // (drag-placed point symbol being scaled/rotated). It is NOT in the command
  // stack and NOT in the scene cache - it draws as an overlay on every frame,
  // so per-event mutations need only a plain frame request and gestures keep
  // the cheap blit path. On stylus-up (or any interruption) it is committed
  // into the scene via addDrawingPath, which invalidates the cache once.
  private volatile DrawingPath mLiveItem = null;

  /** set the live overlay item (placement drag in progress) */
  void setLiveItem( DrawingPath item )
  {
    mLiveItem = item;
    requestRender();
  }

  /** commit the pending live item into the scene
   * @return true if an item was committed
   */
  boolean commitLiveItem()
  {
    DrawingPath live = mLiveItem;
    mLiveItem = null;
    if ( live == null ) return false;
    addDrawingPath( live ); // bakes it: invalidates the scene cache once
    return true;
  }

  private volatile long mCacheSuppressedUntilMs = 0;

  /** suppress the scene cache and free its front bitmap for a while: called
   *  around big exports so the export bitmap + PNG compression get maximum
   *  heap headroom (a mid-compress OOM used to truncate the export file).
   *  Frames direct-render during the suppression window.
   * @param ms   suppression duration [ms]
   */
  void suppressSceneCache( long ms )
  {
    long until = System.currentTimeMillis() + ms;
    if ( until > mCacheSuppressedUntilMs ) mCacheSuppressedUntilMs = until;
    synchronized ( mCacheLock ) {
      mCacheFront = null; // dropped, not recycled: a frame may still be blitting it
    }
    requestRender();
  }

  /** request a frame after a scene-content mutation */
  public void requestSceneRender()
  {
    invalidateSceneCache();
    requestRender();
  }

  /** invalidate now AND after the current UI-thread work completes: used by
   *  the blanket button/menu handlers, whose mutations run after the handler
   *  entry - the posted second invalidation closes the race with a cache
   *  build that starts mid-handler
   */
  public void requestSceneRenderPosted()
  {
    requestSceneRender();
    post( this::requestSceneRender );
  }

  /** request a render: the draw thread paints one frame as soon as possible
   * @note for scene-content changes use requestSceneRender (invalidates the
   *       scene cache); this plain request is for transform/preview updates
   */
  public void requestRender() { mDirty.set( true ); }

  /** request full-rate rendering for a short burst (resume, focus regain,
   *  dialog dismissal): covers scene mutations that do not go through the
   *  surface, without auditing every dialog. The scene cache is invalidated
   *  for the same reason - the dialog may have changed anything.
   */
  public void requestRenderBurst()
  {
    invalidateSceneCache();
    requestRenderBurst( RENDER_BURST_MS );
  }

  /** request full-rate rendering for the given duration
   * @param ms   burst duration [ms]
   * @note during a burst frames render back-to-back at a steady cadence -
   *       used per touch event so gestures pace regularly instead of the
   *       jittery render/idle alternation of pure dirty-driven frames
   */
  public void requestRenderBurst( long ms )
  {
    long until = System.currentTimeMillis() + ms;
    if ( until > mBurstUntilMs ) mBurstUntilMs = until;
    mDirty.set( true );
  }

  /** check if the surface needs a frame (called by the draw thread)
   * @return true if a frame should be rendered now
   */
  public boolean isDrawing()
  {
    if ( ! mResumedRender ) return false;
    if ( mDirty.get() ) return true;
    long now = System.currentTimeMillis();
    return now < mBurstUntilMs || now - mLastFrameMs > RENDER_HEARTBEAT_MS;
  }

  /** enable/disable rendering (activity resume/pause)
   * @param drawing   new value
   */
  public void setDrawing( boolean drawing )
  {
    mResumedRender = drawing;
    if ( drawing ) requestRenderBurst();
  }

  // ----------------------------------------------------------

  /** split the drawing, in the current manager
   * @param border    splitting border
   * @param remove    whether to remove split items
   * @return the list of (a copy of the) split items
   */
  List< DrawingPath > splitPaths( ArrayList< PointF > border, boolean remove )
  {
    List< DrawingPath > ret = commandManager.splitPaths( border, remove );
    requestSceneRender();
    return ret;
  }

  // TH2EDIT this method was commented
  /** clear the drawing (only for mSkipSaving)
   */
  void clearDrawing() {
    assert( commandManager == mCommandManager3 );
    commandManager.clearDrawing();
    requestSceneRender();
  }

  /** set the paint of a station-name
   * @param st      station-name item
   * @param saved   list of saved stations
   * @param manager drawing command manager
   * @note used to return true if the station is the current active
   */
  private void setStationPaint( DrawingStationName st, List< StationInfo > saved, DrawingCommandManager manager )
  {
    if ( st == null ) return; // 20191010 should not be necessary: crash called from setCurrentStation
    String name = st.getName();
    NumStation num_st = st.getNumStation();

    if ( StationName.isCurrentStationName( name ) ) {
      if ( manager != null ) {
        // TDLog.v("Current station is " + name + " GREEN" );
        manager.setCurrentStationName( st );
      }
      st.setPathPaint( BrushManager.fixedStationActivePaint );
    } else if ( num_st.mHidden == 1 ) {
      st.setPathPaint( BrushManager.fixedStationHiddenPaint );
    } else if ( num_st.mHidden == -1 || num_st.mBarrierAndHidden ) {
      st.setPathPaint( BrushManager.fixedStationBarrierPaint );
    } else {
      st.setPathPaint( BrushManager.fixedStationPaint );
      if ( TDSetting.mSavedStations && saved != null ) { // the test on mSavedStations is extra care
	for ( StationInfo sst : saved ) {
	  if ( sst.mName.equals( num_st.name ) ) {
            st.setPathPaint( BrushManager.fixedStationSavedPaint );
	    break;
	  }
	}
      }
    }
  }

  /** clear the current station - reset its paint
   */
  private void clearCurrentStation() 
  {
    // TDLog.v("DRAW SURFACE Clear current station name ");
    DrawingStationName st = mCommandManager1.getCurrentStationName();
    if ( st != null ) {
      // TDLog.v("Clear current station name Manager 1");
      setStationPaint( st, null, mCommandManager1 );
      mCommandManager1.setCurrentStationName( null );
    }
    st = mCommandManager2.getCurrentStationName();
    if ( st != null ) {
      // TDLog.v("Clear current station name Manager 1");
      setStationPaint( st, null, mCommandManager2 );
      mCommandManager2.setCurrentStationName( null );
    }
  }

  // /** @return true if the command manager has a current station
  //  */
  // boolean hasCurrentStation() { return commandManager.hasCurrentStation(); }

  /** set the current station
   * @param st      station-name item - can be null to clear the current station 
   * @param saved   list of saved stations
   */
  void setCurrentStation( DrawingStationName st, List< StationInfo > saved )
  {
    DrawingStationName st0 = commandManager.getCurrentStationName();
    if ( st0 != null /* && st0 != st */ ) {
      TDLog.v("Set current station - old " + st0.getName() );
      DrawingStationName st1 = mCommandManager1.getCurrentStationName();
      mCommandManager1.setCurrentStationName( null );
      setStationPaint( st1, saved, mCommandManager1 );
      st1 = mCommandManager2.getCurrentStationName();
      mCommandManager2.setCurrentStationName( null );
      setStationPaint( st1, saved, mCommandManager2 );
      if ( st == st0 ) {
        TDLog.v("Set current station - same as old " + st0.getName() );
        return;
      }
    } else {
      TDLog.v("Current station null");
    }
    if ( st != null ) {
      TDLog.v("Set current station - new " + st.getName() );
      st.setPathPaint( BrushManager.fixedStationActivePaint );
      commandManager.setCurrentStationName( st );
      // setStationPaint( st, saved, commandManager );
      if ( commandManager == mCommandManager1 ) {
        st0 = mCommandManager2.getStation( st.getName() );
        st0.setPathPaint( BrushManager.fixedStationActivePaint );
        mCommandManager2.setCurrentStationName( st0 );
        // setStationPaint( st0, saved, mCommandManager2 );
      } else {
        st0 = mCommandManager1.getStation( st.getName() );
        st0.setPathPaint( BrushManager.fixedStationActivePaint );
        mCommandManager1.setCurrentStationName( st0 );
        // setStationPaint( st0, saved, mCommandManager1 );
      }
    }
    requestSceneRender();
  }

  /** add a station-name drawing item
   * @param parent     name of the parent plot
   * @param num_st     station
   * @param x          X coord (scene)
   * @param y          Y coord (scene)
   * @param selectable whether the station is selectable
   * @param xsections  list of survey xsections
   * @param saved      list of saved stations
   * @return the new station-name item
   * @note called by DrawingWindow::computeReference
   */
  public DrawingStationName addDrawingStationName ( String parent, NumStation num_st, float x, float y, boolean selectable, 
		                             List< PlotInfo > xsections, List< StationInfo > saved ) // TH2EDIT was package
  {
    // TDLog.v( "add Drawing Station Name [1] " + num_st.name + " " + x + " " + y );
    // FIXME STATION_XSECTION
    // DO as when loaded

    DrawingStationName st = new DrawingStationName( num_st, x, y, scrapIndex() );
    setStationPaint( st, saved, commandManager );

    if ( xsections != null && parent != null ) {
      for ( PlotInfo plot : xsections ) {
        if ( plot.start.equals( st.getName() ) ) {
          // if ( plot.isXSectionShared() || parent.equals(plot.getXSectionParent()) ) { 
            st.setXSection( plot.azimuth, plot.clino, mType );
          // }
          break;
        }
      }
    }
    commandManager.addTmpStation( st, selectable ); // NOTE make this always true if you want station selectable on all sections
    requestSceneRender();
    return st;
  }

  /** add a station-name drawing item
   * @param type       ???
   * @param num_st     station
   * @param x          X coord (scene)
   * @param y          Y coord (scene)
   * @param selectable whether the station is selectable
   * @return the new station-name item
   */
  DrawingStationName appendDrawingStationName ( long type, NumStation num_st, float x, float y, boolean selectable )
  {
    // TDLog.v( "append Drawing Station Name [3] " + num_st.name + " " + x + " " + y );
    DrawingCommandManager cmd = ( type == DRAWING_PLAN )? mCommandManager1 : mCommandManager2;
    clearCurrentStation();
    DrawingStationName st = new DrawingStationName( num_st, x, y, scrapIndex() );
    setStationPaint( st, null, cmd );

    cmd.appendStation( st, selectable ); // NOTE make this always true if you want station selectable on all sections
    requestSceneRender();
    return st;
  }

  /** add a station-name drawing item
   * @param name       station name
   * @param x          X coord (scene)
   * @param y          Y coord (scene)
   * @return the new station-name item
   * @note called by DrawingWindow (for SECTION)
   * @note not selectable
   */
  public DrawingStationName addDrawingStationName( String name, float x, float y ) // TH2EDIT was package
  {
    // TDLog.v( "add Drawing Station Name [2] " + name + " " + x + " " + y );
    // NOTE No station_XSection in X-Sections
    DrawingStationName st = new DrawingStationName( name, x, y, scrapIndex() );
    st.setPathPaint( BrushManager.fixedStationPaint );
    commandManager.addTmpStation( st, false ); // NOTE make this true for selectable station in all sections
    requestSceneRender();
    return st;
  }

  /** highlight a station name
   * @param name  name to highight, or null to clear
   */
  void highlightStation( String name ) { commandManager.highlightStation( name ); requestSceneRender(); }

  /** reset the "fixed" paint
   * @param app   application
   * @param paint new paint
   */
  void resetFixedPaint( TopoDroidApp app, Paint paint )
  {
    mCommandManager1.resetFixedPaint( app, false, paint );
    mCommandManager2.resetFixedPaint( app, true,  paint );
    requestSceneRender();
  }

  /** set the zoom "fixed"
   * @param fixed_zoom  value if fixed-zoom (0 = non-fixed)
   */
  void setFixedZoom( int fixed_zoom ) { commandManager.setFixedZoom( fixed_zoom ); requestSceneRender(); }

  /** @return the value of current fixed-zoom (0 = non-fixed)
   */
  int getFixedZoom( ) { return commandManager.getFixedZoom(); }

  /** @return true if the zoom of the current manager is fixed
   */
  boolean isFixedZoom() { return commandManager.isFixedZoom(); }

  // UNUSED : only for X-Sections autowalls
  // List< DrawingSplayPath > getSplays() { return commandManager.getSplays(); }

  // called by DrawingActivity::addFixedLine
  void addFixedSplayPath( DrawingSplayPath path, boolean selectable )
  {
    commandManager.addTmpSplayPath( path, selectable );
    requestSceneRender();
  }

  void addFixedLegPath( DrawingPath path, boolean selectable )
  {
    commandManager.addTmpLegPath( path, selectable );
    requestSceneRender();
  }

  void appendFixedSplayPath( int type, DrawingSplayPath path, boolean selectable )
  {
    DrawingCommandManager cmd = ( type == DRAWING_PLAN )? mCommandManager1 : mCommandManager2;
    cmd.appendSplayPath( path, selectable );
    requestSceneRender();
  }

  void appendFixedLegPath( int type, DrawingPath path, boolean selectable )
  {
    DrawingCommandManager cmd = ( type == DRAWING_PLAN )? mCommandManager1 : mCommandManager2;
    cmd.appendLegPath( path, selectable );
    requestSceneRender();
  }

  void dropLastSplayPath( int type )
  {
    DrawingCommandManager cmd = ( type == DRAWING_PLAN )? mCommandManager1 : mCommandManager2;
    cmd.dropLastSplayPath( );
    requestSceneRender();
  }

  /** set the north line
   * @param path  new north line 
   * @note  used only by DrawingWindow for H-Section
   */
  public void setNorthPath( DrawingPath path ) { commandManager.setNorthLine( path ); requestSceneRender(); }

  /** set the path for the first point of a measurement
   * @param path   path for the first point
   */
  public void setFirstReference( DrawingMeasureStartPath path ) { commandManager.setFirstReference( path ); requestSceneRender(); }

  /** set the path for the second point of a measurement
   * @param path   path for the second point
   */
  public void setSecondReference( DrawingMeasureEndPath path ) { commandManager.setSecondReference( path ); requestSceneRender(); }

  /** add the path for the second point of a measurement
   * @param x  X coord of the point added to the second reference
   * @param y  Y coord of the point added to the second reference
   */
  public void addSecondReference( float x, float y ) { commandManager.addSecondReference( x, y ); requestSceneRender(); }

  // k : grid type 1, 10, 100
  public void addGridPath( DrawingPath path, int k ) { commandManager.addTmpGrid( path, k ); requestSceneRender(); }

  // DEBUG
  // public int getGrid1Size() { return commandManager.getGrid1().size(); }
  // public int getGrid10Size() { return commandManager.getGrid10().size(); }

  /** add a drawing item
   * @param drawingPath  drawing item
   */
  public void addDrawingPath (DrawingPath drawingPath) { commandManager.addCommand(drawingPath); requestSceneRender(); }

  // public void addDrawingDotPath (DrawingPath drawingPath) { commandManager.addDotCommand(drawingPath); }
  public void addDrawingDotPath (DrawingPath drawingPath) { commandManager.addDotCommand(drawingPath); requestSceneRender(); }

  public void addScrapOutlinePath( DrawingLinePath path ) { commandManager.addScrapOutlinePath( path ); requestSceneRender(); }

  public void addXSectionOutlinePath( DrawingOutlinePath path ) { commandManager.addXSectionOutlinePath( path ); requestSceneRender(); }

  // return true if point has been deleted
  void deleteSectionPoint( String scrap_name )
  {
    commandManager.deleteSectionPoint( scrap_name, null ); // null eraseCommand
    requestSceneRender();
  }
  
  // void setBounds( float x1, float x2, float y1, float y2 ) { commandManager.setBounds( x1, x2, y1, y2 ); }

  void redo()
  {
    commandManager.redo();
    requestSceneRender();
  }

  void undo()
  {
    commandManager.undo();
    requestSceneRender();
  }

  boolean hasMoreRedo()
  { return commandManager!= null && commandManager.hasMoreRedo(); }

  boolean hasMoreUndo()
  { return commandManager!= null && commandManager.hasMoreUndo(); }

  // public boolean hasStationName( String name ) { return commandManager.hasUserStation( name ); }

  DrawingStationUser getStationPath( String name )
  {
    if ( commandManager == null ) return null;
    return commandManager.getUserStation( name );
  }

  public void addDrawingStationUser( DrawingStationUser path )
  {
    if ( commandManager == null ) return;
    commandManager.addUserStation( path );
    requestSceneRender();
  }

  void removeDrawingStationUser( DrawingStationUser path )
  {
    if ( commandManager == null ) return;
    commandManager.removeUserStation( path );
    requestSceneRender();
  }

  RectF getBitmapBounds( float scale )
  { 
    if ( commandManager == null ) return null;
    return commandManager.getBitmapBounds( scale );
  }

  /** try to continue an area
   * @param ap   area path
   * @param lp1  first point
   * @param lp2  last point
   * @param type area type
   * @param zoom canvas zoom (the larger the zoom, the bigger the sketch on the display)
   * @param size ???
   * @return true if the area ap1 has been added to an area in the sketch
   */
  boolean getAreaToContinue( DrawingAreaPath ap, LinePoint lp1, LinePoint lp2,  int type, float zoom, float size )
  {
    boolean ret = commandManager.getAreaToContinue( ap, lp1, lp2, type, zoom, size );
    if ( ret ) requestSceneRender(); // on success the stroke merged into an existing (cached) area
    return ret;
  }

  /** try to continue a line
   * @param lp   line path
   * @param lp1  first point
   * @param lp2  last point
   * @param type line type
   * @param zoom canvas zoom (the larger the zoom, the bigger the sketch on the display)
   * @param size ???
   * @return true if the line lp1 has been added to a line in the sketch
   */
  boolean getLineToContinue( DrawingLinePath lp, LinePoint lp1, LinePoint lp2,  int type, float zoom, float size )
  {
    boolean ret = commandManager.getLineToContinue( lp, lp1, lp2, type, zoom, size );
    if ( ret ) requestSceneRender(); // on success the stroke merged into an existing (cached) line
    return ret;
  }

  /** get the line to continue
   * @param lp   point
   * @param type line type
   * @param zoom canvas zoom (the larger the zoom, the bigger the sketch on the display)
   * @param size ???
   * @return the line to continue or null
   */
  DrawingLinePath getLineToContinue( LinePoint lp, int type, float zoom, float size ) 
  {
    return commandManager.getLineToContinue( lp, type, zoom, size );
  }

  // @param zoom canvas zoom (the larger the zoom, the bigger the sketch on the display)
  boolean modifyLine( DrawingLinePath line, DrawingLinePath lp2, float zoom, float size )
  {
    boolean ret = commandManager.modifyLine( line, lp2, zoom, size );
    if ( ret ) requestSceneRender(); // on success the target (cached) line geometry changed
    return ret;
  }
 
  /** add the points of the first line to the second line
   */
  void addLineToLine( DrawingLinePath line, DrawingLinePath line0 ) { commandManager.addLineToLine( line, line0 ); requestSceneRender(); }

  // ---------------------------------------------------------------------
  // SELECT - EDIT

  // public SelectionPoint getPointAt( float x, float y ) { return commandManager.getPointAt( x, y ); }
  // public SelectionPoint getLineAt( float x, float y ) { return commandManager.getLineAt( x, y ); }
  // public SelectionPoint getAreaAt( float x, float y ) { return commandManager.getAreaAt( x, y ); }
  // public SelectionPoint getShotAt( float x, float y ) { return commandManager.getShotAt( x, y ); }

  // UNUSED
  // x,y canvas coords
  // DrawingStationName getStationAt( float x, float y, float size ) { return commandManager.getStationAt( x, y, size ); }

  // UNUSED
  // DrawingStationName getStation( String name ) { return commandManager.getStation( name ); }

  SelectionSet getItemsAt( float x, float y, float zoom, int mode, float size )
  {
    SelectionSet ret = commandManager.getItemsAt( x, y, zoom, mode, size, mStationSplay );
    requestSceneRender(); // changed the selection highlight, which is part of the cached scene
    return ret;
  }

  // add item to multiselection
  void addItemAt( float x, float y, float zoom, float size )
  {
    commandManager.addItemAt( x, y, zoom, size );
    requestSceneRender();
  }

  // set line range at the hot-item
  // type = range type
  boolean setRangeAt( float x, float y, float zoom, int type, float size )
  {
    boolean ret = commandManager.setRangeAt( x, y, zoom, type, size );
    requestSceneRender();
    return ret;
  }

  DrawingAudioPath getAudioPoint( long bid ) { return commandManager.getAudioPoint( bid ); }

  boolean moveHotItemToNearestPoint( float dmin ) { return commandManager.moveHotItemToNearestPoint( dmin ); }
  boolean appendHotItemToNearestLine() { return commandManager.appendHotItemToNearestLine(); }
  
  int snapHotItemToNearestLine() { return commandManager.snapHotItemToNearestLine(); }
  int snapHotItemToNearestSplays( float dthr ) { return commandManager.snapHotItemToNearestSplays( dthr, mStationSplay ); }

  void splitPointHotItem() { commandManager.splitPointHotItem(); requestSceneRender(); }
  void insertPointsHotItem() { commandManager.insertPointsHotItem(); requestSceneRender(); }
  
  SelectionPoint hotItem() { return commandManager.hotItem(); }

  boolean hasSelected() { return commandManager.hasSelected(); }

  // void shiftHotItem( float dx, float dy, float range ) { commandManager.shiftHotItem( dx, dy, range ); }
  void shiftHotItem( float dx, float dy ) { commandManager.shiftHotItem( dx, dy ); requestSceneRender(); }

  void refreshReferencePath( DrawingReferencePath path ) { commandManager.refreshReferencePath( path ); requestSceneRender(); }

  /** rotate the hot item 
   * @param dy   amount of rotation [degrees]
   */
  void rotateHotItem( float dy ) { commandManager.rotateHotItem( dy ); requestSceneRender(); }

  /** @return the next selected point
   */
  SelectionPoint nextHotItem() { return commandManager.nextHotItem(); }

  /** @return the previous selected point
   */
  SelectionPoint prevHotItem() { return commandManager.prevHotItem(); }

  /** clear the selection
   */
  void clearSelected() { commandManager.syncClearSelected(); requestSceneRender(); }

  /** shift the drawing
   * @param x   X-shift
   * @param y   Y-shift
   * @param scrap whether to shift only the current scrap
   */
  void shiftDrawing( float x, float y, boolean scrap ) { commandManager.shiftDrawing( x, y, scrap ); requestSceneRender(); }

  // /** scrap drawing by a factor
  //  * @param z  factor
  //  * @param scrap whether to scale only the current scrap
  //  */
  // void scaleDrawing( float z, boolean scrap ) { commandManager.scaleDrawing( z, scrap ); }

  void affineTransformDrawing( float a, float b, float c, float d, float e, float f, boolean scrap )
  { commandManager.affineTransformDrawing( a,b,c, d,e,f, scrap ); requestSceneRender(); }

  // ---------------------------------------------------------------------

  public void surfaceChanged( SurfaceHolder holder, int format, int width,  int height)
  {
    // TDLog.Log( TDLog.LOG_PLOT, "surfaceChanged " );
    // TODO Auto-generated method stub
    mDrawThread.setHolder( holder );
    requestSceneRender();
  }


  public void surfaceCreated( SurfaceHolder holder ) 
  {
    // TDLog.Log( TDLog.LOG_PLOT, "surfaceCreated " );
    if ( mDrawThread == null ) {
      mDrawThread = new DrawThread(this, holder);
    } else {
      mDrawThread.setHolder( holder );
    }
    // mDrawThread.setRunning(true); // not necessary: done by start
    mDrawThread.start();
    requestSceneRender();
    mSurfaceCreated = true;
  }

  public void surfaceDestroyed( SurfaceHolder holder ) 
  {
    mSurfaceCreated = false;
    // TDLog.Log( TDLog.LOG_PLOT, "surfaceDestroyed " );
    // mDrawThread.setHolder( null );
    mDrawThread.setRunning(false);
    boolean retry = true;
    while (retry) {
      try {
        mDrawThread.join();
        retry = false;
      } catch (InterruptedException e) {
        // we will try it again and again...
      }
    }
    mDrawThread = null;
  }

  DrawingCommandManager getManager( long type )
  {
    if ( PlotType.isProfile( type ) ) return mCommandManager2;
    if ( type == PlotType.PLOT_PLAN ) return mCommandManager1;
    return mCommandManager3;
  }

  Bitmap renderExportBitmap( long type, SketchPngExportOptions options )
  {
    DrawingCommandManager manager = getManager( type );
    return ( manager == null ) ? null : manager.renderExportBitmap( mStationSplay, options );
  }

  /** rebind line paints in every cached drawing manager
   */
  void refreshLinePaints()
  {
    refreshLinePaints( null );
  }

  /** rebind line paints in every cached drawing manager
   * @param indexMap old line index to new line index map
   */
  void refreshLinePaints( int[] indexMap )
  {
    if ( commandManager != null ) commandManager.refreshLinePaints( indexMap );
    if ( mCommandManager1 != null && mCommandManager1 != commandManager ) mCommandManager1.refreshLinePaints( indexMap );
    if ( mCommandManager2 != null && mCommandManager2 != commandManager ) mCommandManager2.refreshLinePaints( indexMap );
    if ( mCommandManager3 != null && mCommandManager3 != commandManager ) mCommandManager3.refreshLinePaints( indexMap );
    requestSceneRender();
  }

  /** rebind point paints in every cached drawing manager
   * @param indexMap old point index to new point index map
   */
  void refreshPointPaints( int[] indexMap )
  {
    if ( commandManager != null ) commandManager.refreshPointPaints( indexMap );
    if ( mCommandManager1 != null && mCommandManager1 != commandManager ) mCommandManager1.refreshPointPaints( indexMap );
    if ( mCommandManager2 != null && mCommandManager2 != commandManager ) mCommandManager2.refreshPointPaints( indexMap );
    if ( mCommandManager3 != null && mCommandManager3 != commandManager ) mCommandManager3.refreshPointPaints( indexMap );
    requestSceneRender();
  }

  /** rebind area paints in every cached drawing manager
   * @param indexMap old area index to new area index map
   */
  void refreshAreaPaints( int[] indexMap )
  {
    if ( commandManager != null ) commandManager.refreshAreaPaints( indexMap );
    if ( mCommandManager1 != null && mCommandManager1 != commandManager ) mCommandManager1.refreshAreaPaints( indexMap );
    if ( mCommandManager2 != null && mCommandManager2 != commandManager ) mCommandManager2.refreshAreaPaints( indexMap );
    if ( mCommandManager3 != null && mCommandManager3 != commandManager ) mCommandManager3.refreshAreaPaints( indexMap );
    requestSceneRender();
  }

  // -------------------------------------------------------------------
  // LOAD

  // called by OverviewWindow
  // @pre th2 != null
  // boolean addloadTherion( String th2, float xdelta, float ydelta, SymbolsPalette missingSymbols )
  // {
  //   SymbolsPalette localPalette = BrushManager.preparePalette();
  //   if ( (TDFile.getTopoDroidFile(th2)).exists() ) {
  //     return DrawingIO.doLoadTherion( this, th2, xdelta, ydelta, missingSymbols, localPalette );
  //   }
  //   return false;
  // }

  // @note th21 can be null
  // boolean modeloadTherion( String th21, SymbolsPalette missingSymbols )
  // {
  //   SymbolsPalette localPalette = BrushManager.preparePalette();
  //   if ( missingSymbols != null ) missingSymbols.resetSymbolLists();
  //   return DrawingIO.doLoadTherion( this, th21, 0, 0, missingSymbols, localPalette );
  // }

  /** add a sketch loaded from file
   * @param tdr        tdr file pathname
   * @param xdelta     X shift
   * @param ydelta     Y shift
   * @param plotname   sketch name (null for merging)
   * @note called by OverviewWindow
   * @pre tdr != null
   */
  boolean addLoadDataStream( String tdr, float xdelta, float ydelta, /* SymbolsPalette missingSymbols, */ String plotname )
  {
    boolean ret = false;
    SymbolsPalette localPalette = BrushManager.preparePalette();
    if ( (TDFile.getTopoDroidFile(tdr)).exists() ) {
      // TDLog.v( "add file " + tdr + " loading ... " + plotname );
      ret = DrawingIO.doLoadDataStream( this, tdr, xdelta, ydelta, /* missingSymbols, */ localPalette, null, false, plotname );
    }
    if ( ret && plotname != null ) {
      if ( plotname.startsWith( TDInstance.survey ) ) {
        int len = TDInstance.survey.length() + 1;
        if ( len < plotname.length() ) {
          linkSections( plotname.substring( len ) );
        }
      } else {
        linkSections( plotname );
      }
    }
    requestSceneRender();
    return ret;
  }

  /** load a sketch from file
   * @param tdr1           tdr file pathname
   * @param fullname       sketch fullname (= survey-plot)
   * @param link_sections  whether to link xsections to station names
   * @note called only by DrawingWindow
   */
  boolean modeloadDataStream( String tdr1, String fullname, boolean link_sections /*, SymbolsPalette missingSymbols */ )
  {
    boolean ret = false;
    SymbolsPalette localPalette = BrushManager.preparePalette();
    // FIXME-MISSING if ( missingSymbols != null ) missingSymbols.resetSymbolLists();
    if ( tdr1 != null ) {
      if ( (TDFile.getTopoDroidFile( tdr1 )).exists() ) {
        // TDLog.v( "file " + tdr1 + " exists: loading ... " + fullname );
        ret = DrawingIO.doLoadDataStream( this, tdr1, 0, 0, /* missingSymbols, */ localPalette, null, false, null ); // no plot_name
        if ( ret ) {
          BrushManager.makeEnabledListFromPalette( localPalette, false ); // ENABLED_LIST false: do not reset symbols "enabled"
          if ( link_sections ) {
            if ( fullname.startsWith( TDInstance.survey ) ) {
              int len = TDInstance.survey.length() + 1;
              if ( len < fullname.length() ) {
                linkSections( fullname.substring( len ) );
              }
            } else {
              linkSections( fullname );
            }
          }
          if ( fullname != null ) addManagerToCache( fullname );
        } else {
          TDLog.e( "file " + tdr1 + " failed to load" );
          BrushManager.makeEnabledListFromConfig();
          ItemDrawer.resetRecentSymbols();
        }
      } else {
        // TDLog.v( "file " + tdr1 + " does not exist:  make enabled list from config");
        BrushManager.makeEnabledListFromConfig();
        ItemDrawer.resetRecentSymbols();
      }
    }
    requestSceneRender();
    return ret;
  }

  /** load a sketch from file input stream
   * @param fis            file input stream
   * @param fullname       sketch fullname (= survey-plot)
   * @note called only by DrawingWindow THEDIT
   */
  boolean modeloadFileStream( FileInputStream fis, String fullname )
  {
    boolean ret = false;
    SymbolsPalette localPalette = BrushManager.preparePalette();
    // FIXME-MISSING if ( missingSymbols != null ) missingSymbols.resetSymbolLists();
    if ( fis != null ) {
      DataInputStream dis = new DataInputStream( fis );
      // TDLog.v( "file " + tdr1 + " exists: loading ... " + fullname );
      String filename = fullname;
      String survey_name = "survey";
      String plot_name   = "plot";
      ret = DrawingIO.doLoadDataInputStream( this, dis, 0, 0, survey_name, filename, localPalette, null, true, plot_name );
      if ( ret ) {
        BrushManager.makeEnabledListFromPalette( localPalette, false ); // ENABLED_LIST false: do not reset symbols "enabled"
      } else {
        TDLog.e( "file " + fullname + " failed to load" );
        BrushManager.makeEnabledListFromConfig();
        ItemDrawer.resetRecentSymbols();
      }
    }
    requestSceneRender();
    return ret;
  }

  /** link xsections to station names
   * @param name   name of xsections parent plot
   */
  private void linkSections( String name ) { commandManager.linkSections( name ); }

  void linkAllSections( String name1, String name2 ) 
  {
    mCommandManager1.linkSections( name1 );
    mCommandManager2.linkSections( name2 );
  }

  // -----------------------------------------------------------------------------
  // EXPORT

  static void exportAsTCsx( PrintWriter pw, long type, String survey, String cave, String branch, /* String session, */
                           DrawingCommandManager cm, List< PlotInfo > all_sections, List< PlotInfo > sections )
  {
    if ( PlotType.isProfile( type ) ) {
      // FIXME OK PROFILE to check
      if ( cm != null ) {
        cm.exportAsTCsx( pw, survey, cave, branch, /* session, */ all_sections, sections );
      }
    } else if ( type == PlotType.PLOT_PLAN ) {
      if ( cm != null ) {
        cm.exportAsTCsx( pw, survey, cave, branch, /* session, */ all_sections, sections );
      }
    }
  }


  // static void exportAsCsx( PrintWriter pw, long type, String survey, String cave, String branch, /* String session, */
  //                          DrawingCommandManager cm, List< PlotInfo > all_sections, List< PlotInfo > sections )
  // {
  //   if ( PlotType.isProfile( type ) ) {
  //     // FIXME OK PROFILE to check
  //     if ( cm != null ) {
  //       cm.exportAsCsx( pw, survey, cave, branch, /* session, */ all_sections, sections );
  //     }
  //   } else if ( type == PlotType.PLOT_PLAN ) {
  //     if ( cm != null ) {
  //       cm.exportAsCsx( pw, survey, cave, branch, /* session, */ all_sections, sections );
  //     }
  //   } else { // should never happen, but it happens for X-Sections
  //     pw.format("    <layers>\n");
  //     pw.format("      <layer name=\"Base\" type=\"0\">\n");
  //     pw.format("         <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Soil\" type=\"1\">\n");
  //     pw.format("        <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Water and floor morphologies\" type=\"2\">\n");
  //     pw.format("        <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Rocks and concretions\" type=\"3\">\n");
  //     pw.format("        <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Ceiling morphologies\" type=\"4\">\n");
  //     pw.format("        <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Borders\" type=\"5\">\n");
  //     pw.format("        <items>\n");
//       pw.format("          <item layer=\"5\" name=\"Esempio bezier\" type=\"4\" category=\"1\" linetype=\"2\" mergemode=\"0\">\n");
//       pw.format("            <pen type="1" />
//       pw.format("            <points data="-6.69 1.04 B -6.51 1.58 -5.85 2.21 -5.04 2.63 -3.81 2.93 -1.56 2.57 -0.45 2.06 0.00 1.46 0.87 1.31 1.20 -0.17 1.29 -1.13 1.17 -2.24 0.93 -2.75 0.18 -4.85 1.83 -5.09 2.76 -5.78 3.21 -5.93 " />
//       pw.format("          </item>
//       pw.format("          <item layer="5" name="Esempio spline" type="4" category="1" linetype="1" mergemode="0">
//       pw.format("            <pen type="1" />
//       pw.format("            <points data="-3.30 6.26 B -3.12 6.80 -2.46 7.43 -1.65 7.85 -0.42 8.15 1.83 7.79 2.94 7.28 3.39 6.68 4.26 6.53 4.68 5.08 4.68 4.09 4.56 2.98 4.32 2.47 3.57 0.37 5.22 0.13 6.15 -0.56 6.60 -0.71 " />
//       pw.format("          </item>
//       pw.format("          <item layer="5" name="Esempio rette" type="4" category="1" linetype="0" mergemode="0">
//       pw.format("            <pen type="1" />
//       pw.format("            <points data="-9.60 -3.47 B -8.97 -2.81 -7.71 -2.27 -6.45 -2.21 -4.92 -2.75 -4.38 -3.11 -3.69 -3.92 -3.45 -4.70 -3.36 -6.80 -2.79 -8.06 -2.34 -8.39 -0.42 -8.93 " />
//       pw.format("          </item>
  //     pw.format("        </items>\n");
  //     pw.format("      </layer>\n");
  //     pw.format("      <layer name=\"Signs\" type=\"6\">\n");
  //     pw.format("        <items />\n");
  //     pw.format("      </layer>\n");
  //     pw.format("    </layers>\n");
  //   }
  // }

  void setSplayAlpha( boolean on ) { if ( mCommandManager3 != null ) mCommandManager3.setSplayAlpha(on); }
  
  // ----------------------------------------------------------------
  // station splays

  /** toggle splay display at a station
   * @param st_name   station name
   * @param on        whether to add the station to the ON list
   * @param off       whether to add the station to the OFF list
   */
  void toggleStationSplays( String st_name, boolean on, boolean off ) { mStationSplay.toggleStationSplays( st_name, on, off ); }

  /** @return true if the stations on the ON list
   * @param st_name   station name
   */
  boolean isStationSplaysOn( String st_name ) { return mStationSplay.isStationSplaysOn( st_name ); }

  /** @return true if the stations on the OFF list
   * @param st_name   station name
   */
  boolean isStationSplaysOff( String st_name ) { return mStationSplay.isStationSplaysOff( st_name ); }

  /** show splays at a station
   * @param station    station
   */
  void showStationSplays( String station ) { mStationSplay.showStationSplays( station ); requestSceneRender(); }

  /** hide splays at a station
   * @param station    station
   */
  void hideStationSplays( String station ) { mStationSplay.hideStationSplays( station ); requestSceneRender(); }

  void setStationXSections( List< PlotInfo > xsection_plan, List< PlotInfo > xsection_ext, long type2 )
  {
    mCommandManager1.setStationXSections( xsection_plan, PlotType.PLOT_PLAN );
    mCommandManager2.setStationXSections( xsection_ext,  type2 );
    requestSceneRender();
  }

  // only for sections
  float computeSectionArea()
  {
    return commandManager.computeSectionArea();
  }

  void deleteSectionLine( DrawingLinePath line, String scrap )
  {
    EraseCommand cmd = new EraseCommand();
    commandManager.deleteSectionLine( line, scrap, cmd );
    commandManager.deleteSectionPoint( scrap, cmd );
    commandManager.addEraseCommand( cmd );
    requestSceneRender();
  }

  /** @return true if there are plot outlines
   */
  boolean hasPlotOutline() { return commandManager.hasPlotOutline(); }
  
  /** clear the plot outlines
   */
  void clearPlotOutline() { commandManager.clearPlotOutline(); requestSceneRender(); }

  void addScrapDataStream( String tdr, float xdelta, float ydelta )
  {
    commandManager.clearPlotOutline( );
    DrawingIO.doLoadOutlineDataStream( this, tdr, xdelta, ydelta, null, -1 );
    requestSceneRender();
  }

  // @param name xsection scrap name ( survey_name + "-" + xsection_id )
  boolean hasXSectionOutline( String name ) { return commandManager.hasXSectionOutline( name ); }

  /** insert the outline of a xsection
   * @param name       xsection scrap name ( survey_name + "-" + xsection_id )
   * @param scrap_id   id of the scrap of the section point
   * @param tdr        xsection tdr pathname
   * @param xdelta     ???
   * @param ydelta     ???
   */
  void setXSectionOutline( String name, int scrap_id, String tdr, float xdelta, float ydelta )
  {
    DrawingIO.doLoadOutlineDataStream( this, tdr, xdelta, ydelta, name, scrap_id );
    requestSceneRender();
  }

  /** @return the section point of a given x-section
   * @param scrap_name    X-section fullname
   */
  DrawingPointPath findSectionPoint( String scrap_name )
  {
    return commandManager.findSectionPoint( scrap_name );
  }


  void setAllXSectionOutlines( DrawingWindow window, int cm )
  {
    DrawingCommandManager manager = ( cm == 1 ) ? mCommandManager1 : mCommandManager2;
    if ( manager == null ) return;

    DrawingCommandManager old = commandManager;
    try {
      commandManager = manager;
      manager.clearXSectionsOutline();

      List< DrawingPointPath > pts = manager.getSectionPoints();
      for ( DrawingPointPath pt : pts ) {
        String name = pt.getOption( TDString.OPTION_SCRAP );
        if ( name != null ) {
          window.setXSectionOutline( name, pt.mScrap, true, pt.cx, pt.cy );
        }
      }
    } finally {
      commandManager = old;
    }
  }
    
  // @param name xsection scrap name ( survey_name + "-" + xsection_id )
  void clearXSectionOutline( String name )
  {
    commandManager.clearXSectionOutline( name );
    requestSceneRender();
  }

  // shift X-Sections in the plan and in the profile
  void shiftXSections( NumStation st )
  {
    // TDLog.v("shift X-Sections: " + st.e + " " + st.s + " " + st.h + " " + st.v );
    mCommandManager1.shiftXSections( 20*(float)st.e, 20*(float)st.s );
    mCommandManager2.shiftXSections( 20*(float)st.h, 20*(float)st.v );
    requestSceneRender();
  }

}
