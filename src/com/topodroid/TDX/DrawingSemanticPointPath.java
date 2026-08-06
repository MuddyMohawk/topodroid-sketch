/* @file DrawingSemanticPointPath.java
 *
 * @brief Generic drawing path used by every registered special-point behavior
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.app.Dialog;

import org.json.JSONException;

class DrawingSemanticPointPath extends DrawingPointPath
{
  private final String mCanonicalTherionName;
  private final SpecialPointBehavior mBehavior;
  private SpecialPointState mSpecialState;
  private Object mPreparedSpecialState;
  private boolean mPlacementInitialized;

  DrawingSemanticPointPath( int type, String therion_name, SpecialPointBehavior behavior,
                            float x, float y, int scale, String text, String options, int scrap )
  {
    super( type, x, y, scale, text, options, scrap );
    mCanonicalTherionName = therion_name;
    mBehavior = behavior;
    mPlacementInitialized = false;
    loadSpecialState( options );
    if ( mBehavior != null && mSpecialState != null ) {
      mPreparedSpecialState = mBehavior.prepareState( this, mSpecialState );
    }
    refreshSpecialBounds();
  }

  SpecialPointBehavior specialBehavior() { return mBehavior; }

  SpecialPointState specialState() { return mSpecialState; }

  boolean hasUsableSpecialState() { return mSpecialState != null; }

  Object preparedSpecialState() { return mPreparedSpecialState; }

  void setSpecialState( SpecialPointState state, boolean persist )
  {
    if ( state == null ) return;
    Object prepared = mBehavior == null ? null : mBehavior.prepareState( this, state );
    String options = persist ? SpecialPointEnvelope.store( mOptions, mBehavior, state ) : mOptions;
    commitPreparedSpecialState( state, options, prepared );
  }

  String encodeSpecialOptions( SpecialPointState state ) throws JSONException
  {
    return SpecialPointEnvelope.encodeOptions( mOptions, mBehavior, state );
  }

  Object prepareSpecialState( SpecialPointState state )
  {
    return mBehavior == null || state == null ? null : mBehavior.prepareState( this, state );
  }

  void refreshPreparedSpecialState()
  {
    if ( mBehavior == null || mSpecialState == null ) return;
    Object prepared = mBehavior.prepareState( this, mSpecialState );
    synchronized ( TDPath.mCommandsLock ) {
      mPreparedSpecialState = prepared;
      refreshSpecialBounds();
    }
  }

  void commitPreparedSpecialState( SpecialPointState state, String options, Object prepared )
  {
    if ( state == null || options == null ) return;
    synchronized ( TDPath.mCommandsLock ) {
      mSpecialState = state;
      mOptions = options;
      mPreparedSpecialState = prepared;
      refreshSpecialBounds();
    }
  }

  SpecialPointPlacementAction initializePlacement( SpecialPointPlacementContext context )
  {
    if ( mPlacementInitialized || mBehavior == null || mSpecialState == null ) {
      return SpecialPointPlacementAction.NONE;
    }
    mPlacementInitialized = true;
    SpecialPointPlacementAction action = mBehavior.initializePlacement( context, this );
    return ( action == null ) ? SpecialPointPlacementAction.NONE : action;
  }

  SpecialPointEditorController createEditorController( DrawingWindow parent )
  {
    return ( mBehavior == null || mSpecialState == null ) ? null
      : mBehavior.createEditorController( parent, this );
  }

  Dialog createDedicatedEditor( DrawingWindow parent, boolean initial_placement )
  {
    return ( mBehavior == null || mSpecialState == null ) ? null
      : mBehavior.createDedicatedEditor( parent, this, initial_placement );
  }

  void preparePreview()
  {
    if ( mBehavior != null && mSpecialState != null ) {
      mBehavior.preparePreview( this );
      mPreparedSpecialState = mBehavior.prepareState( this, mSpecialState );
      refreshSpecialBounds();
    }
  }

  boolean previewUsesAuthoredGlyph()
  {
    return mBehavior != null && mBehavior.previewUsesAuthoredGlyph();
  }

  RectF exactSpecialBounds( boolean landscape )
  {
    if ( mBehavior == null || mSpecialState == null || mBehavior.renderer() == null ) return null;
    RectF bounds = mBehavior.renderer().sceneBounds( this );
    if ( bounds == null ) return null;
    if ( landscape && ! BrushManager.isPointOrientable( mPointType )
        && ! mBehavior.rendersAbsoluteSceneDirections() ) {
      float left = cx - ( bounds.bottom - cy );
      float top = cy + ( bounds.left - cx );
      float right = cx - ( bounds.top - cy );
      float bottom = cy + ( bounds.right - cx );
      bounds.set( Math.min( left, right ), Math.min( top, bottom ),
                  Math.max( left, right ), Math.max( top, bottom ) );
    }
    return bounds;
  }

  boolean hitSpecialBounds( float x, float y, float slop, boolean landscape )
  {
    RectF bounds = exactSpecialBounds( landscape );
    return bounds != null && x >= bounds.left - slop && x <= bounds.right + slop
                          && y >= bounds.top - slop && y <= bounds.bottom + slop;
  }

  void refreshSpecialBounds()
  {
    if ( mBehavior == null || mSpecialState == null || mBehavior.renderer() == null ) return;
    RectF bounds = mBehavior.renderer().sceneBounds( this );
    if ( bounds == null || bounds.isEmpty() ) return;
    // Landscape presentation rotates non-orientable point ink by 90 degrees. A
    // symmetric culling box remains correct before Scrap stamps mLandscape.
    float half_width = Math.max( Math.abs( bounds.left - cx ), Math.abs( bounds.right - cx ) );
    float half_height = Math.max( Math.abs( bounds.top - cy ), Math.abs( bounds.bottom - cy ) );
    float radius = Math.max( half_width, half_height );
    set( cx - radius, cy - radius, cx + radius, cy + radius );
  }

  @Override public String getThName()
  {
    return ( mCanonicalTherionName == null || mCanonicalTherionName.length() == 0 )
      ? super.getThName() : mCanonicalTherionName;
  }

  @Override public String getFullThName()
  {
    String full_name = ( mBehavior == null ) ? null : mBehavior.fullTherionName();
    if ( full_name != null && full_name.length() > 0 ) return full_name;
    return ( mCanonicalTherionName == null || mCanonicalTherionName.length() == 0 )
      ? super.getFullThName() : mCanonicalTherionName;
  }

  @Override public String getFullThNameEscapedColon()
  {
    String full_name = getFullThName();
    return ( full_name == null ) ? null : full_name.replace( ':', '_' );
  }

  @Override protected boolean pointUsesValue()
  {
    return true;
  }

  @Override public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox )
  {
    if ( mSpecialState == null || mBehavior == null || mBehavior.renderer() == null ) {
      super.draw( canvas, matrix, scale, bbox );
      return;
    }
    drawSpecial( canvas, matrix, bbox, 0 );
  }

  @Override public void draw( Canvas canvas, Matrix matrix, float scale, RectF bbox, int xor_color )
  {
    if ( mSpecialState == null || mBehavior == null || mBehavior.renderer() == null ) {
      super.draw( canvas, matrix, scale, bbox, xor_color );
      return;
    }
    drawSpecial( canvas, matrix, bbox, xor_color );
  }

  private void drawSpecial( Canvas canvas, Matrix matrix, RectF bbox, int xor_color )
  {
    if ( ! intersects( bbox ) ) return;
    if ( TitleLegendPointBehavior.isTitleLegend( this ) ) {
      RectF exact = exactSpecialBounds( mLandscape );
      if ( exact != null && ! RectF.intersects( exact, bbox ) ) return;
    }
    int save = canvas.save();
    try {
      canvas.concat( matrix );
      if ( mLandscape && ! BrushManager.isPointOrientable( mPointType )
          && ! mBehavior.rendersAbsoluteSceneDirections() ) canvas.rotate( 90, cx, cy );
      mBehavior.renderer().draw( this, canvas, xor_color );
    } finally {
      canvas.restoreToCount( save );
    }
  }

  @Override void setPointText( String text )
  {
    super.setPointText( text );
    refreshSpecialBounds();
  }

  @Override void setScale( int scale )
  {
    super.setScale( scale );
    refreshPreparedSpecialState();
  }

  @Override boolean setExactPointScale( float scale )
  {
    boolean changed = super.setExactPointScale( scale );
    if ( changed ) refreshPreparedSpecialState();
    return changed;
  }

  @Override void setSketchBrushStyle( SketchBrushStyle style )
  {
    super.setSketchBrushStyle( style );
    refreshPreparedSpecialState();
  }

  @Override void setOrientation( double angle )
  {
    super.setOrientation( angle );
    refreshSpecialBounds();
  }

  @Override void scaleBy( float factor, Matrix matrix )
  {
    super.scaleBy( factor, matrix );
    refreshPreparedSpecialState();
  }

  @Override void affineTransformBy( float[] values, Matrix matrix )
  {
    super.affineTransformBy( values, matrix );
    refreshPreparedSpecialState();
  }

  Paint specialPointPaint() { return resolvedSketchLinePaint(); }

  float specialPointScale() { return resolvedSketchPointFootprintScale(); }

  private void loadSpecialState( String options )
  {
    if ( mBehavior == null ) return;
    String raw = SketchPrivateOptions.getOptionValue( options, SketchPrivateOptions.OPTION_SPECIAL );
    if ( raw == null ) {
      mSpecialState = mBehavior.defaultState();
      return;
    }
    SpecialPointEnvelope.Decoded decoded = SpecialPointEnvelope.fromOptions( options );
    if ( decoded == null || decoded.envelopeVersion != SpecialPointEnvelope.ENVELOPE_VERSION
        || ! mBehavior.behaviorId().equals( decoded.behaviorId ) || decoded.payload == null ) {
      return;
    }
    try {
      mSpecialState = mBehavior.decodeState( decoded.stateVersion, decoded.payload );
    } catch ( JSONException | RuntimeException e ) {
      TDLog.e( "Special point behavior decode failed: " + e.getMessage() );
      mSpecialState = null;
    }
  }
}
