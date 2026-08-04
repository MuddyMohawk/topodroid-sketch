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

import org.json.JSONException;

class DrawingSemanticPointPath extends DrawingPointPath
{
  private final String mCanonicalTherionName;
  private final SpecialPointBehavior mBehavior;
  private SpecialPointState mSpecialState;
  private boolean mPlacementInitialized;

  DrawingSemanticPointPath( int type, String therion_name, SpecialPointBehavior behavior,
                            float x, float y, int scale, String text, String options, int scrap )
  {
    super( type, x, y, scale, text, options, scrap );
    mCanonicalTherionName = therion_name;
    mBehavior = behavior;
    mPlacementInitialized = false;
    loadSpecialState( options );
    refreshSpecialBounds();
  }

  SpecialPointBehavior specialBehavior() { return mBehavior; }

  SpecialPointState specialState() { return mSpecialState; }

  boolean hasUsableSpecialState() { return mSpecialState != null; }

  void setSpecialState( SpecialPointState state, boolean persist )
  {
    if ( state == null ) return;
    mSpecialState = state;
    if ( persist ) mOptions = SpecialPointEnvelope.store( mOptions, mBehavior, state );
    refreshSpecialBounds();
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

  void preparePreview()
  {
    if ( mBehavior != null && mSpecialState != null ) {
      mBehavior.preparePreview( this );
      refreshSpecialBounds();
    }
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
    int save = canvas.save();
    try {
      canvas.concat( matrix );
      if ( mLandscape && ! BrushManager.isPointOrientable( mPointType ) ) canvas.rotate( 90, cx, cy );
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
    refreshSpecialBounds();
  }

  @Override boolean setExactPointScale( float scale )
  {
    boolean changed = super.setExactPointScale( scale );
    if ( changed ) refreshSpecialBounds();
    return changed;
  }

  @Override void setSketchBrushStyle( SketchBrushStyle style )
  {
    super.setSketchBrushStyle( style );
    refreshSpecialBounds();
  }

  @Override void setOrientation( double angle )
  {
    super.setOrientation( angle );
    refreshSpecialBounds();
  }

  @Override void scaleBy( float factor, Matrix matrix )
  {
    super.scaleBy( factor, matrix );
    refreshSpecialBounds();
  }

  @Override void affineTransformBy( float[] values, Matrix matrix )
  {
    super.affineTransformBy( values, matrix );
    refreshSpecialBounds();
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
