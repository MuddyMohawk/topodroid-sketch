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
import android.graphics.DashPathEffect;

public class SymbolLine extends Symbol
{
  String mName;       // local name
  Paint  mPaint;      // forward paint - stroke width [scene units] = width * TDSetting.inkUnit()
  Paint  mRevPaint;   // reverse paint
  boolean mHasEffect; // whether the line has a pattern effect
  LineSymbolEffect mLineEffect;
  Path mPath;
  boolean mStyleStraight;
  boolean mClosed;
  int mStyleX;            // X times (one out of how many point to use)
  private float[] mDashBase; // dash intervals [line-width units], or null
  private float mWidth = 1;  // declared line width [line-width units]

  @Override public String getName()  { return mName; }
  // @Override public String getThName( ) { return mThName; } // same as in Symbol.java

  @Override public Paint  getPaint() { return mPaint; }

  /** @return dash intervals in line-width units, or null */
  float[] getDashBase() { return mDashBase; }

  /** @return the declared line width [line-width units] */
  float getWidth() { return mWidth; }

  // /** @return the line color - default to black = use Symbol::getColor
  //  */
  // @Override public int getColor() { return (mPaint == null)? 0 : mPaint.getColor(); }

  @Override public Path   getPath()  { return mPath; }

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
    mPaint.setStrokeWidth( mWidth * TDSetting.inkUnit() );
    mRevPaint = new Paint (mPaint );
    mHasEffect = false;
    mLineEffect = null;
    mDashBase = null;
    mStyleStraight = false;
    mClosed = false;
    mStyleX = 1;
  }

  private void setDash( float[] dash_lw )
  {
    if ( dash_lw == null || dash_lw.length < 2 ) return;
    mDashBase = dash_lw;
    mHasEffect = true;
    float unit = mPaint.getStrokeWidth();
    mPaint.setPathEffect( scaledDashEffect( dash_lw, unit ) );
    mRevPaint.setPathEffect( scaledDashEffect( dash_lw, unit ) );
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
    boolean cosineEnvelope = false;
    float envelopeDefault = 1.0f;
    float envelopeMin = 1.0f;
    float envelopeMax = 1.0f;
  }

  private static void applySketchEffect( LineSymbolEffect effect, SketchEffectData data )
  {
    if ( effect == null || data == null ) return;
    effect.setSketchEffect( data.path_dir, data.path_rev, data.carriers, data.strokeStamp );
    if ( data.cosineEnvelope ) {
      effect.setCosineEnvelope( data.envelopeDefault, data.envelopeMin, data.envelopeMax );
    }
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
      } else if ( vals[k].equals("envelope") ) {
        try {
          ++k;
          while ( k < s && vals[k].length() == 0 ) ++k;
          if ( k >= s || ! vals[k].equals("cosine") ) throw new NumberFormatException();
          k_val = k;
          data.envelopeDefault = nextFloat( vals, s, 1.0f );
          data.envelopeMin = nextFloat( vals, s, 1.0f );
          data.envelopeMax = nextFloat( vals, s, 1.0f );
          data.cosineEnvelope = true;
        } catch ( NumberFormatException e ) {
          TDLog.e( filename + " parse sketch envelope error: " + line );
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
   *        envelope cosine DEFAULT MIN MAX
   *        stamp
   *          command: moveTo lineTo cubicTo addCircle
   *        endstamp
   *      endsketch_effect
   *      endsymbol
   */
  private void readFile( String filename, String locale, String iso )
  {
    // TDLog.v( "SL load line file " + filename );
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
    float xmin=0, xmax=0;
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
                      } else {
                        // Disconnected move commands can intentionally reserve empty
                        // space at either edge of a repeating pattern.
                        if ( x < xmin ) xmin = x; else if ( x > xmax ) xmax = x;
                      }
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
                    } catch ( NumberFormatException e ) {
                      TDLog.e( filename + " parse addCircle point error: " + line );
                    }
                  } else if ( vals[k].equals("endeffect") ) {
                    mLineEffect = new LineSymbolEffect( path_dir, path_rev, xmax - xmin, dash_values );
                    applySketchEffect( mLineEffect, sketch_effect );
                    break;
                  }
                }
              }
  	    } else if ( vals[k].equals("sketch_effect") ) {
              sketch_effect = readSketchEffect( br, filename );
              if ( mLineEffect != null ) {
                applySketchEffect( mLineEffect, sketch_effect );
              }
  	    } else if ( vals[k].equals("endsymbol") ) {
  	      if ( name != null && th_name != null ) {
                mName   = name;
                setThName( th_name );
                mGroup  = group;
                mDefaultOptions = options;
                mWidth  = ( width > 0 )? width : 1;
                float unit = mWidth * TDSetting.inkUnit(); // default ink thickness [scene units]
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
