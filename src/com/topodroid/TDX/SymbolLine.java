/* @file SymbolLine.java
 *
 * @author marco corvi
 * @date dec 2012
 *
 * @brief TopoDroid drawing: line symbol (world-space ink model)
 *
 * Pattern geometry in symbol files (effect, sketch_effect, dash) is authored in
 * LINE-WIDTH UNITS: a value of 1 equals one ink thickness. Rendering scales the
 * pattern by the placement stroke width (scene units), so patterns follow the
 * line weight, and follow zoom / export scale via the canvas transform.
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.util.TDLog;
import com.topodroid.util.TDFile;
import com.topodroid.util.TDColor;
import com.topodroid.prefs.TDSetting;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.ComposePathEffect;
import android.graphics.DashPathEffect;
import android.graphics.PathDashPathEffect;
import android.graphics.RectF;
import android.graphics.Matrix;

public class SymbolLine extends Symbol
{
  // preview pattern unit [scene units per line-width unit] - toolbar/palette eye-candy only
  private static final float PREVIEW_PATTERN_UNIT_LINES = 1.4f;
  private static final float PREVIEW_TARGET_FILL = 3.0f / 8.0f;
  private static final float PREVIEW_MAX_SCALE = 3.25f;
  private static final float PREVIEW_BUTTON_HALF_HEIGHT_UNITS = 4.0f; // ItemButton.H4
  private static final float FLAT_PREVIEW_STROKE_SCALE = 1.5f;
  private static final float FLAT_PREVIEW_MIN_STROKE_LINES = 2.0f;

  String mName;       // local name
  Paint  mPaint;      // forward paint - stroke width [scene units] = width * TDSetting.mLineThickness
  Paint  mRevPaint;   // reverse paint
  Paint  mPreviewPaint; // preview paint independent of scene scaling
  Paint  mRevPreviewPaint; // reverse preview paint
  boolean mHasEffect; // whether the line has a pattern effect
  LineSymbolEffect mLineEffect;
  Path mPath;
  Path mPreviewPath;
  boolean mStyleStraight;
  boolean mClosed;
  int mStyleX;            // X times (one out of how many point to use)
  private float[] mDashBase; // dash intervals [line-width units], or null
  private float mWidth = 1;  // declared line width [line-width units]

  @Override public String getName()  { return mName; }
  // @Override public String getThName( ) { return mThName; } // same as in Symbol.java

  @Override public Paint  getPaint() { return mPaint; }

  @Override public Paint  getPreviewPaint()
  {
    return ( mPreviewPaint != null )? mPreviewPaint : mPaint;
  }

  /** @return dash intervals in line-width units, or null */
  float[] getDashBase() { return mDashBase; }

  /** @return the declared line width [line-width units] */
  float getWidth() { return mWidth; }

  // /** @return the line color - default to black = use Symbol::getColor
  //  */
  // @Override public int getColor() { return (mPaint == null)? 0 : mPaint.getColor(); }

  @Override public Path   getPath()  { return ( mPreviewPath != null )? mPreviewPath : mPath; }

  @Override public Path getScaledPath()
  {
    Path path = getPath();
    if ( path == null ) return null;
    RectF bounds = new RectF();
    path.computeBounds( bounds, true );
    float scale = previewScaleForHeight( bounds.height() );
    if ( scale <= 1.0f ) return path;

    Path ret = new Path( path );
    Matrix matrix = new Matrix();
    matrix.setScale( scale, scale, bounds.centerX(), bounds.centerY() );
    ret.transform( matrix );
    return ret;
  }

  // width = 1;
  // no effect
  SymbolLine( String name, String th_name, String group, String fname, int color, int level, int rt )
  {
    super( Symbol.TYPE_LINE, th_name, group, fname, rt );
    init( name, color, 1 );
    makeLinePath();
    mLevel = level;
  }

  // no effect
  SymbolLine( String name, String th_name, String group, String fname, int color, float width, int level, int rt )
  {
    super( Symbol.TYPE_LINE, th_name, group, fname, rt );
    init( name, color, width );
    makeLinePath();
    mLevel = level;
  }

  /** dashed line
   * @param dash_lw  dash intervals [line-width units]
   */
  SymbolLine( String name, String th_name, String group, String fname, int color, float width, float[] dash_lw, int level, int rt )
  {
    super( Symbol.TYPE_LINE, th_name, group, fname, rt );
    init( name, color, width );
    setDash( dash_lw );
    makeLinePath();
    mLevel = level;
  }

  private void init( String name, int color, float width )
  {
    mName   = name;
    mWidth  = ( width > 0 )? width : 1;
    mPaint  = new Paint();
    mPaint.setDither(true);
    mPaint.setColor( color );
    mPaint.setStyle(Paint.Style.STROKE);
    mPaint.setStrokeJoin(Paint.Join.ROUND);
    mPaint.setStrokeCap(Paint.Cap.ROUND);
    mPaint.setStrokeWidth( mWidth * TDSetting.mLineThickness );
    mRevPaint = new Paint (mPaint );
    mPreviewPaint = null;
    mRevPreviewPaint = null;
    mPreviewPath = null;
    mHasEffect = false;
    mLineEffect = null;
    mDashBase = null;
    mStyleStraight = false;
    mClosed = false;
    mStyleX = 1;
    setFlatPreviewPaints( null );
  }

  private void setDash( float[] dash_lw )
  {
    if ( dash_lw == null || dash_lw.length < 2 ) return;
    mDashBase = dash_lw;
    mHasEffect = true;
    float unit = mPaint.getStrokeWidth();
    mPaint.setPathEffect( scaledDashEffect( dash_lw, unit ) );
    mRevPaint.setPathEffect( scaledDashEffect( dash_lw, unit ) );
    setFlatPreviewPaints( dash_lw );
  }

  /** @return a DashPathEffect with intervals scaled by the given unit, or null */
  static DashPathEffect scaledDashEffect( float[] dash_lw, float unit )
  {
    if ( dash_lw == null || dash_lw.length < 2 || unit <= 0 ) return null;
    float[] x = new float[ dash_lw.length ];
    for ( int k = 0; k < dash_lw.length; ++k ) x[k] = Math.max( 0.01f, dash_lw[k] * unit );
    return new DashPathEffect( x, 0 );
  }

  SymbolLine( String filepath, String fname, String locale, String iso )
  {
    super( Symbol.TYPE_LINE, null, null, fname, Symbol.W2D_DETAIL_SHP );
    mStyleStraight = false;
    mClosed = false;
    mStyleX = 1;
    mPreviewPath = null;
    readFile( filepath, locale, iso );
    makeLinePath();
  }

  private void makeLinePath()
  {
    mPath = new Path();
    mPath.moveTo(-50, 0 );
    mPath.lineTo( 50, 0 );
  }


  private int k_val; // index in array vals[]

  private void setPreviewPatternPaints( PathEffect effect_dir, PathEffect effect_rev, float stroke_width )
  {
    mPreviewPaint = new Paint( mPaint );
    mPreviewPaint.setStrokeWidth( stroke_width );
    mPreviewPaint.setPathEffect( effect_dir );
    mRevPreviewPaint = new Paint( mRevPaint );
    mRevPreviewPaint.setStrokeWidth( stroke_width );
    mRevPreviewPaint.setPathEffect( effect_rev );
  }

  private void setFlatPreviewPaints( float[] dash_lw )
  {
    if ( mPaint == null ) return;

    float preview_stroke = Math.max( mPaint.getStrokeWidth() * FLAT_PREVIEW_STROKE_SCALE,
                                     TDSetting.mLineThickness * FLAT_PREVIEW_MIN_STROKE_LINES );
    PathEffect preview_dash = scaledDashEffect( dash_lw, preview_stroke );

    mPreviewPaint = new Paint( mPaint );
    mPreviewPaint.setStrokeWidth( preview_stroke );
    mPreviewPaint.setPathEffect( preview_dash );

    mRevPreviewPaint = new Paint( mRevPaint );
    mRevPreviewPaint.setStrokeWidth( preview_stroke );
    mRevPreviewPaint.setPathEffect( preview_dash );
  }

  private void setSketchPreview( SketchEffectData sketch_effect, float preview_unit, float stroke_width, float advance_lw,
                                 float[] dash_lw )
  {
    if ( sketch_effect == null || sketch_effect.path_dir == null ) return;

    float advance = Math.max( 1.0f, advance_lw * preview_unit );
    float repeat = previewRepeatLength( dash_lw, advance, preview_unit );
    Path unit_path = new Path();

    if ( sketch_effect.carriers != null && sketch_effect.carriers.size() > 0 && sketch_effect.strokeStamp ) {
      addPreviewCarrierCenterLines( unit_path, sketch_effect.carriers, dash_lw, advance, preview_unit );
    } else if ( sketch_effect.carriers != null && sketch_effect.carriers.size() > 0 ) {
      addPreviewCarriers( unit_path, sketch_effect.carriers, dash_lw, advance, preview_unit );
    }

    Path scaled_stamp = scaledPath( sketch_effect.path_dir, preview_unit );
    unit_path.addPath( scaled_stamp );

    RectF bounds = new RectF();
    unit_path.computeBounds( bounds, true );
    if ( bounds.isEmpty() ) return;

    Path preview = new Path();
    float y_offset = -0.5f * ( bounds.top + bounds.bottom );
    for ( float x = -50.0f; x < 50.0f; x += repeat ) {
      Path sample = new Path( unit_path );
      sample.offset( x, y_offset );
      preview.addPath( sample );
    }
    mPreviewPath = preview;

    mPreviewPaint = new Paint( mPaint );
    mPreviewPaint.setPathEffect( null );
    if ( sketch_effect.strokeStamp ) {
      mPreviewPaint.setStyle( Paint.Style.STROKE );
      mPreviewPaint.setStrokeCap( Paint.Cap.ROUND );
      mPreviewPaint.setStrokeJoin( Paint.Join.ROUND );
      mPreviewPaint.setStrokeWidth( Math.max( 1.0f, stroke_width ) );
    } else {
      mPreviewPaint.setStyle( Paint.Style.FILL );
      mPreviewPaint.setStrokeWidth( 0 );
    }
    mRevPreviewPaint = new Paint( mPreviewPaint );
  }

  private static void addPreviewCarriers( Path path, ArrayList< LineSymbolEffect.Carrier > carriers, float[] dash_lw,
                                          float advance, float unit )
  {
    if ( dash_lw == null || dash_lw.length < 2 ) {
      addPreviewCarrierRun( path, carriers, 0.0f, advance, unit );
      return;
    }

    float offset = 0.0f;
    for ( int k = 0; k < dash_lw.length; ++k ) {
      float next = offset + Math.max( 0.0f, dash_lw[k] * unit );
      if ( next > offset && ( k % 2 ) == 0 ) addPreviewCarrierRun( path, carriers, offset, next, unit );
      offset = next;
    }
  }

  private static void addPreviewCarrierCenterLines( Path path, ArrayList< LineSymbolEffect.Carrier > carriers,
                                                   float[] dash_lw, float advance, float unit )
  {
    if ( dash_lw == null || dash_lw.length < 2 ) {
      addPreviewCarrierCenterRun( path, carriers, 0.0f, advance, unit );
      return;
    }

    float offset = 0.0f;
    for ( int k = 0; k < dash_lw.length; ++k ) {
      float next = offset + Math.max( 0.0f, dash_lw[k] * unit );
      if ( next > offset && ( k % 2 ) == 0 ) addPreviewCarrierCenterRun( path, carriers, offset, next, unit );
      offset = next;
    }
  }

  private static void addPreviewCarrierCenterRun( Path path, ArrayList< LineSymbolEffect.Carrier > carriers, float start,
                                                  float end, float unit )
  {
    if ( end <= start ) return;
    for ( int k = 0; k < carriers.size(); ++k ) {
      LineSymbolEffect.Carrier carrier = carriers.get( k );
      float y = 0.5f * ( carrier.y0 + carrier.y1 ) * unit;
      path.moveTo( start, y );
      path.lineTo( end, y );
    }
  }

  private static void addPreviewCarrierRun( Path path, ArrayList< LineSymbolEffect.Carrier > carriers, float start,
                                            float end, float unit )
  {
    if ( end <= start ) return;
    for ( int k = 0; k < carriers.size(); ++k ) {
      LineSymbolEffect.Carrier carrier = carriers.get( k );
      path.addRect( start, carrier.y0 * unit, end, carrier.y1 * unit, Path.Direction.CW );
    }
  }

  private static float previewRepeatLength( float[] dash_lw, float fallback, float unit )
  {
    if ( dash_lw == null || dash_lw.length < 2 ) return fallback;
    float cycle = 0.0f;
    for ( int k = 0; k < dash_lw.length; ++k ) cycle += Math.max( 0.0f, dash_lw[k] * unit );
    return ( cycle > 0.0f ) ? Math.max( 1.0f, cycle ) : fallback;
  }

  private static float previewScaleForHeight( float height )
  {
    if ( height <= 0.0f || Float.isNaN( height ) || Float.isInfinite( height ) ) return 1.0f;
    float target = 2.0f * PREVIEW_BUTTON_HALF_HEIGHT_UNITS * TDSetting.mItemButtonSize * PREVIEW_TARGET_FILL;
    if ( target <= height ) return 1.0f;
    return Math.min( PREVIEW_MAX_SCALE, target / height );
  }

  private Path scaledPath( Path path, float scale )
  {
    Path ret = new Path( path );
    Matrix matrix = new Matrix();
    matrix.setScale( scale, scale );
    ret.transform( matrix );
    return ret;
  }

  private float nextFloat( String[] vals, int s, float unit ) throws NumberFormatException
  {
    ++k_val; while ( k_val < s && vals[k_val].length() == 0 ) ++k_val;
    if ( k_val < s ) {
      return Float.parseFloat( vals[k_val] ) * unit;
    }
    throw new NumberFormatException();
  }

  private int nextInt( String[] vals, int s ) throws NumberFormatException
  {
    ++k_val; while ( k_val < s && vals[k_val].length() == 0 ) ++k_val;
    if ( k_val < s ) {
      try {
        return Integer.parseInt( vals[k_val] );
      } catch( NumberFormatException e ) {
        TDLog.e("Non-integer value");
      }
    }
    throw new NumberFormatException();
  }

  private static class SketchEffectData
  {
    final Path path_dir = new Path();
    final Path path_rev = new Path();
    final ArrayList< LineSymbolEffect.Carrier > carriers = new ArrayList<>();
    boolean strokeStamp = false;
  }

  private SketchEffectData readSketchEffect( BufferedReader br, String filename ) throws IOException
  {
    SketchEffectData data = new SketchEffectData();
    boolean in_stamp = false;
    String line;
    while ( (line = br.readLine()) != null ) {
      line = line.trim();
      String[] vals = line.split(" ");
      int s = vals.length;
      int k = 0;
      while ( k < s && vals[k].length() == 0 ) ++k;
      if ( k >= s || vals[k].startsWith( "#" ) ) continue;

      if ( vals[k].equals("stamp") ) {
        in_stamp = true;
      } else if ( vals[k].equals("stroke") ) {
        data.strokeStamp = true;
      } else if ( vals[k].equals("endstamp") ) {
        in_stamp = false;
      } else if ( vals[k].equals("endsketch_effect") ) {
        break;
      } else if ( vals[k].equals("carrier") ) {
        try {
          k_val = k;
          float y0 = nextFloat( vals, s, 1.0f );
          float y1 = nextFloat( vals, s, 1.0f );
          data.carriers.add( new LineSymbolEffect.Carrier( y0, y1 ) );
        } catch ( NumberFormatException e ) {
          TDLog.e( filename + " parse sketch carrier error: " + line );
        }
      } else if ( in_stamp ) {
        readSketchEffectPathCommand( filename, line, vals, s, k, data.path_dir, data.path_rev );
      }
    }
    return data;
  }

  private void readSketchEffectPathCommand( String filename, String line, String[] vals, int s, int k,
                                            Path path_dir, Path path_rev )
  {
    try {
      k_val = k;
      if ( vals[k].equals("moveTo") ) {
        float x = nextFloat( vals, s, 1.0f );
        float y = nextFloat( vals, s, 1.0f );
        path_dir.moveTo( x, y );
        path_rev.moveTo( x, -y );
      } else if ( vals[k].equals("lineTo") ) {
        float x = nextFloat( vals, s, 1.0f );
        float y = nextFloat( vals, s, 1.0f );
        path_dir.lineTo( x, y );
        path_rev.lineTo( x, -y );
      } else if ( vals[k].equals("cubicTo") ) {
        float x1 = nextFloat( vals, s, 1.0f );
        float y1 = nextFloat( vals, s, 1.0f );
        float x2 = nextFloat( vals, s, 1.0f );
        float y2 = nextFloat( vals, s, 1.0f );
        float x3 = nextFloat( vals, s, 1.0f );
        float y3 = nextFloat( vals, s, 1.0f );
        path_dir.cubicTo( x1,  y1, x2,  y2, x3,  y3 );
        path_rev.cubicTo( x1, -y1, x2, -y2, x3, -y3 );
      } else if ( vals[k].equals("addCircle") ) {
        float x = nextFloat( vals, s, 1.0f );
        float y = nextFloat( vals, s, 1.0f );
        float r = nextFloat( vals, s, 1.0f );
        path_dir.addCircle( x,  y, r, Path.Direction.CCW );
        path_rev.addCircle( x, -y, r, Path.Direction.CCW );
      }
    } catch ( NumberFormatException e ) {
      TDLog.e( filename + " parse sketch stamp error: " + line );
    }
  }

  /** create a symbol reading it from a file
   *  The file syntax is
   *      symbol line
   *      name NAME
   *      th_name THERION_NAME
   *      group GROUP_NAME
   *      color 0xHHHHHH_COLOR 0xAA_ALPHA
   *      width WIDTH
   *      dash x1 y1 x2 y2 ...        [line-width units]
   *      style straight | xN
   *      effect                      [line-width units]
   *        command: moveTo lineTo cubicTo addCircle
   *      endeffect
   *      sketch_effect 1             [line-width units]
   *        stroke
   *        carrier Y0 Y1
   *        stamp
   *          command: moveTo lineTo cubicTo addCircle
   *        endstamp
   *      endsketch_effect
   *      endsymbol
   */
  private void readFile( String filename, String locale, String iso )
  {
    // TDLog.v( "SL load line file " + filename );
    float preview_unit = PREVIEW_PATTERN_UNIT_LINES * TDSetting.mLineThickness;
    String name    = null;
    String th_name = null;
    String group   = null;
    mHasEffect = false;
    int color  = 0;
    int alpha  = 0xcc;
    float width  = 1;
    Path path_dir = null;
    Path path_rev = null;
    SketchEffectData sketch_effect = null;
    float[] dash_values = null;
    PathDashPathEffect preview_effect = null;
    PathDashPathEffect preview_rev_effect = null;
    float preview_effect_unit = preview_unit;
    float xmin=0, xmax=0;
    float ymin=0, ymax=0;
    String options = null;

    try {
      // TDLog.Log( TDLog.LOG_IO, "read symbol line file <" + filename + ">" );
      FileInputStream fr = TDFile.getFileInputStream( filename );
      BufferedReader br = new BufferedReader( new InputStreamReader( fr, iso ) );
      String line;
      boolean in_symbol = false;
      while ( (line = br.readLine()) != null ) {
        line = line.trim();
        String[] vals = line.split(" ");
        int s = vals.length;
        for (int k=0; k<s; ++k ) {
  	  if ( vals[k].startsWith( "#" ) ) break;
          if ( vals[k].length() == 0 ) continue;
          if ( ! in_symbol ) {
  	    if ( vals[k].equals("symbol" ) ) {
  	      name    = null;
  	      th_name = null;
              group   = null;
  	      color   = TDColor.TRANSPARENT;
              in_symbol = true;
              break;
            }
          } else {
  	    if ( vals[k].equals("name") || vals[k].equals(locale) ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
                name = (new String( vals[k].getBytes(iso) )).replace("_", " ");
  	      }
  	    } else if ( vals[k].equals("th_name") ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
                th_name = vals[k];
  	      }
  	    } else if ( vals[k].equals("group") ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
  	        group = vals[k];
  	      }
            } else if ( vals[k].equals("options") ) {
              StringBuilder sb = new StringBuilder();
              boolean space = false;
              for ( ++k; k < s; ++k ) {
                if ( vals[k].length() > 0 ) {
                  if ( space ) { sb.append(" "); } else { space = true; }
                  sb.append( vals[k] );
                }
              }
              options = sb.toString();
            } else if ( vals[k].equals("level") ) {
              ++k; while ( k < s && vals[k].length() == 0 ) ++k;
              if ( k < s ) {
                try {
                  mLevel = ( Integer.parseInt( vals[k] ) );
                } catch( NumberFormatException e ) {
                  TDLog.e("Non-integer level");
                }
              }
            } else if ( vals[k].equals("roundtrip") ) {
              ++k; while ( k < s && vals[k].length() == 0 ) ++k;
              if ( k < s ) {
                try {
                  mRoundTrip = ( Integer.parseInt( vals[k] ) );
                } catch( NumberFormatException e ) {
                  TDLog.e("Non-integer roundtrip");
                }
              }
  	    } else if ( vals[k].equals("closed") ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s && vals[k].equals("yes") ) {
                mClosed = true;
              }
            } else if ( vals[k].equals("csurvey") ) {
              // syntax: csurvey <layer> <type> <category> <pen>
  	    } else if ( vals[k].equals("color") ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
                try {
  	          color = Integer.decode( vals[k] );
                } catch ( NumberFormatException e ) {
                  TDLog.e("Non-integer color");
                }
              }
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
                try {
  	          alpha = Integer.decode( vals[k] );
                } catch ( NumberFormatException e ) {
                  TDLog.e("Non-integer alpha");
                }
  	      }
  	    } else if ( vals[k].equals("width") ) {
              try {
                k_val = k;
                width = nextFloat( vals, s, 1.0f );
              } catch ( NumberFormatException e ) {
                TDLog.e( filename + " parse width error: " + line );
              }
  	    } else if ( vals[k].equals("dash") ) {
  	      ++k; while ( k < s && vals[k].length() == 0 ) ++k;
  	      if ( k < s ) {
                int k1 = k;
                int n_dash = 0; // number of dash+space values
                while ( k1 < s ) {
                  while ( k1 < s && vals[k1].length() == 0 ) ++k1;
                  ++ n_dash;
                  ++k1;
                }
                n_dash = n_dash - (n_dash % 2);
                if ( n_dash > 0 ) {
                  try {
                    float[] x = new float[n_dash];
  	            x[0] = Float.parseFloat( vals[k] );
                    k_val = k;
                    for (int n=1; n<n_dash; ++n ) {
  	              x[n] = nextFloat( vals, s, 1.0f );
                    }
                    dash_values = x;
                  } catch ( NumberFormatException e ) {
                   TDLog.e( filename + " parse dash error: " + line );
                  }
                }
              }
  	    } else if ( vals[k].equals("style") ) { // STYLE
              for ( ++ k; k < s; ++k ) {
  	        if ( vals[k].length() == 0 ) continue;
                if ( vals[k].equals("straight") ) {
                  mStyleStraight = true;
                } else if ( vals[k].startsWith("x") ) {
                  try {
                    mStyleX = Integer.parseInt( vals[k].substring(1) );
                    if ( mStyleX <= 0 ) mStyleX = ItemDrawer.POINT_MAX; // FIXME INT_MAX
                  } catch ( NumberFormatException e ) {
                    TDLog.e("Non-integer style X");
                  }
                }
              }
  	    } else if ( vals[k].equals("effect") ) {
              path_dir = new Path();
              path_rev = new Path();
              boolean moved_to = false;
              while ( (line = br.readLine() ) != null ) {
                line = line.trim();
                vals = line.split(" ");
                s = vals.length;
                k = 0;
  	        while ( k < s && vals[k].length() == 0 ) ++k;
                if ( k < s ) {
                  if ( vals[k].equals("moveTo") ) {
                    try {
                      k_val = k;
                      float x = nextFloat( vals, s, 1.0f );
                      float y = nextFloat( vals, s, 1.0f );
                      path_dir.moveTo( x, y );
                      path_rev.moveTo( x, -y );
                      if ( ! moved_to ) {
                        xmin = xmax = x;
                        moved_to = true;
                      }
	              if ( y > ymax ) { ymax = y; } else if ( y < ymin ) { ymin = y; }
                    } catch ( NumberFormatException e ) {
                      TDLog.e( filename + " parse moveTo point error: " + line );
                    }
                  } else if ( vals[k].equals("lineTo") ) {
                    try {
                      k_val = k;
                      float x = nextFloat( vals, s, 1.0f );
                      float y = nextFloat( vals, s, 1.0f );
                      path_dir.lineTo( x, y );
                      path_rev.lineTo( x, -y );
                      if ( x < xmin ) xmin = x; else if ( x > xmax ) xmax = x;
	              if ( y > ymax ) { ymax = y; } else if ( y < ymin ) { ymin = y; }
                    } catch ( NumberFormatException e ) {
                      TDLog.e( filename + " parse lineTo point error: " + line );
                    }
                  } else if ( vals[k].equals("cubicTo") ) {
                    try {
                      k_val = k;
                      float x1 = nextFloat( vals, s, 1.0f );
                      float y1 = nextFloat( vals, s, 1.0f );
                      float x2 = nextFloat( vals, s, 1.0f );
                      float y2 = nextFloat( vals, s, 1.0f );
                      float x3 = nextFloat( vals, s, 1.0f );
                      float y3 = nextFloat( vals, s, 1.0f );
                      path_dir.cubicTo( x1,  y1, x2,  y2, x3,  y3 );
                      path_rev.cubicTo( x1, -y1, x2, -y2, x3, -y3 );
                      if ( x1 < xmin ) xmin = x1; else if ( x1 > xmax ) xmax = x1;
                      if ( x2 < xmin ) xmin = x2; else if ( x2 > xmax ) xmax = x2;
                      if ( x3 < xmin ) xmin = x3; else if ( x3 > xmax ) xmax = x3;
	              if ( y1 > ymax ) { ymax = y1; } else if ( y1 < ymin ) { ymin = y1; }
	              if ( y2 > ymax ) { ymax = y2; } else if ( y2 < ymin ) { ymin = y2; }
	              if ( y3 > ymax ) { ymax = y3; } else if ( y3 < ymin ) { ymin = y3; }
                    } catch ( NumberFormatException e ) {
                      TDLog.e( filename + " parse cubicTo point error: " + line );
                    }
                  } else if ( vals[k].equals("addCircle") ) {
                    try {
                      k_val = k;
                      float x = nextFloat( vals, s, 1.0f );
                      float y = nextFloat( vals, s, 1.0f );
                      float r = nextFloat( vals, s, 1.0f );
                      path_dir.addCircle( x,  y, r, Path.Direction.CCW );
                      path_rev.addCircle( x, -y, r, Path.Direction.CCW );
                      if ( x-r < xmin ) xmin = x-r;
                      if ( x+r > xmax ) xmax = x+r;
	              if ( y+r > ymax ) { ymax = y+r; } else if ( y-r < ymin ) { ymin = y-r; }
                    } catch ( NumberFormatException e ) {
                      TDLog.e( filename + " parse addCircle point error: " + line );
                    }
                  } else if ( vals[k].equals("endeffect") ) {
                    preview_effect_unit = preview_unit * previewScaleForHeight( ( ymax - ymin ) * preview_unit );
                    preview_effect     = new PathDashPathEffect( scaledPath( path_dir, preview_effect_unit ), (xmax-xmin) * preview_effect_unit, 0, PathDashPathEffect.Style.MORPH );
                    preview_rev_effect = new PathDashPathEffect( scaledPath( path_rev, preview_effect_unit ), (xmax-xmin) * preview_effect_unit, 0, PathDashPathEffect.Style.MORPH );
                    mLineEffect = new LineSymbolEffect( path_dir, path_rev, xmax - xmin, dash_values );
                    if ( sketch_effect != null ) {
                      mLineEffect.setSketchEffect( sketch_effect.path_dir, sketch_effect.path_rev,
                                                   sketch_effect.carriers, sketch_effect.strokeStamp );
                    }
                    break;
                  }
                }
              }
  	    } else if ( vals[k].equals("sketch_effect") ) {
              sketch_effect = readSketchEffect( br, filename );
              if ( mLineEffect != null ) {
                mLineEffect.setSketchEffect( sketch_effect.path_dir, sketch_effect.path_rev,
                                             sketch_effect.carriers, sketch_effect.strokeStamp );
              }
  	    } else if ( vals[k].equals("endsymbol") ) {
  	      if ( name != null && th_name != null ) {
                mName   = name;
                setThName( th_name );
                mGroup  = group;
                mDefaultOptions = options;
                mWidth  = ( width > 0 )? width : 1;
                float unit = mWidth * TDSetting.mLineThickness; // default ink thickness [scene units]
                mPaint  = new Paint();
                mPaint.setDither(true);
                mPaint.setColor( color );
                mPaint.setAlpha( alpha );
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeJoin(Paint.Join.ROUND);
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setStrokeWidth( unit );
                mRevPaint = new Paint( mPaint );
                mDashBase = dash_values;
                mHasEffect = ( mLineEffect != null ) || ( dash_values != null );
                if ( mLineEffect == null && dash_values != null ) {
                  // dash-only line: default dash effect at the symbol's own width
                  mPaint.setPathEffect( scaledDashEffect( dash_values, unit ) );
                  mRevPaint.setPathEffect( scaledDashEffect( dash_values, unit ) );
                }
                // preview paints (palette buttons): screen-space eye-candy
                float preview_dy = (ymax - ymin) * preview_unit + 1;
                float preview_stroke = preview_dy * mWidth * TDSetting.mLineThickness;
                if ( preview_effect != null ) {
                  DashPathEffect preview_dash = scaledDashEffect( dash_values, preview_effect_unit );
                  if ( preview_dash != null ) {
                    setPreviewPatternPaints( new ComposePathEffect( preview_effect, preview_dash ),
                                             new ComposePathEffect( preview_rev_effect, preview_dash ),
                                             preview_stroke );
                  } else {
                    setPreviewPatternPaints( preview_effect, preview_rev_effect, preview_stroke );
                  }
                } else if ( dash_values != null ) {
                  setFlatPreviewPaints( dash_values );
                } else {
                  setFlatPreviewPaints( null );
                }
                setSketchPreview( sketch_effect, preview_unit, preview_stroke, xmax - xmin, dash_values );
  	      }
              in_symbol = false;
            }
          }
        }
      }
    } catch ( FileNotFoundException e ) {
      // FIXME
    } catch( IOException e ) {
      // FIXME
    }
  }

}
