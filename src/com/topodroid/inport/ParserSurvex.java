/* @file ParserSurvex.java
 *
 * @author marco corvi
 * @date jav 2019
 *
 * @brief TopoDroid Survex parser
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * ----------------------------------------------------------
 */
package com.topodroid.inport;

import com.topodroid.util.TDMath;
import com.topodroid.util.TDLog;
import com.topodroid.util.TDio;
import com.topodroid.util.TDString;
import com.topodroid.util.TDUtil;
// import com.topodroid.prefs.TDSetting;
import com.topodroid.types.ExtendType;
import com.topodroid.types.LegType;

import static com.topodroid.inport.ParserSurvexState.LENGTH;
import static com.topodroid.inport.ParserSurvexState.BEARING;
import static com.topodroid.inport.ParserSurvexState.CLINO;
import static com.topodroid.inport.ParserSurvexState.BACK_LENGTH;
import static com.topodroid.inport.ParserSurvexState.BACK_BEARING;
import static com.topodroid.inport.ParserSurvexState.BACK_CLINO;

import java.io.IOException;
// import java.io.FileNotFoundException;
// import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.Locale;

// TODO this class can be made extend ImportParser
//
// units, calibrate, sd supported quantities:
//   length tape bearing compass gradient clino counter depth x y z declination

class ParserSurvex extends ImportParser
{
  // final static private String EMPTY = "";

  // private Stack< ParserSurvexState > mStates; // states stack (LIFO)
  // private void pushState( ParserSurvexState state )
  // {
  //   mStates.push( state );
  // }
  // private ParserSurvexState popState()
  // {
  //   return mStates.pop();
  // }


  /** fix station:
   * fix stations are supposed to be referred to the same coord system
   */
  class Fix 
  {
    // private CS cs;
    String name;  // station name
    float e, n, z; // north east, vertical (upwards)

    Fix( String nm, float e0, float n0, float z0 )
    {
      name = nm;
      e = e0;
      n = n0;
      z = z0;
    }
  }

  private ArrayList< Fix > fixes;
  private ArrayList< String > stations;
  private HashMap<String,String> aliases;  // station aliases

  // public int getShotNumber()    { return shots.size(); }
  // public int getSplayNumber()   { return splays.size(); }

  ArrayList< ParserShot > getShots()    { return shots; }
  ArrayList< ParserShot > getSplays()   { return splays; }
  ArrayList< String >     getStations() { return stations; }
  ArrayList< Fix >        getFixes()    { return fixes; }

  // same as in ImportParser.java
  String initStation()
  {
    for ( ParserShot sh : shots ) {
      if ( sh.from != null && sh.from.length() > 0 ) return sh.from;
    }
    return TDString.ZERO;
  }

  float surveyDeclination( ) { return mDeclination; }
  // ---------------------------------------------------------

  ParserSurvex( InputStreamReader isr, String filename, boolean apply_declination ) throws ParserException
  {
    super( apply_declination );
    mName    = TDio.extractName( filename );
    fixes    = new ArrayList<>();
    stations = new ArrayList<>();
    aliases  = new HashMap<>();
    // mStates  = new Stack< ParserSurvexState >();
    ParserSurvexState state = new ParserSurvexState("."); // root of the linked list of states
    readFile( isr, filename, state );
    checkValid();
  }

  // private String nextLine( BufferedReader br ) throws IOException // from ImportParser
  // {
  //   StringBuilder ret = new StringBuilder();
  //   {
  //     String line = br.readLine();
  //     if ( line == null ) return null; // EOF
  //     while ( line != null && line.endsWith( "\\" ) ) {
  //       ret.append( line.replace( '\\', ' ' ) ); // FIXME
  //       line = br.readLine();
  //     }
  //     if ( line != null ) ret.append( line );
  //   }
  //   return ret.toString();
  // }

  // station fullname:  cave.survey.station
  // private String extractStationName( String fullname )
  // {
  //   int idx = fullname.lastIndexOf('.');
  //   if ( idx >= 0 ) {
  //      return fullname.substring(idx+1); 
  //   }
  //   return fullname;
  // }

  /** read input file
   * @param filename name of the file to parse
   * @param state    state of the parser
   */
  private void readFile( InputStreamReader isr, String filename, ParserSurvexState state ) throws ParserException
  {
    boolean in_station = true;
    boolean haveMeasurements = false;
    String from = null;
    String to;
    int line_nr = 0; // debug line number

    float len=0, ber=0, cln=0;

    Pattern pattern = Pattern.compile( "\\s+" );

    try {
      String dirname = "./";
      int i = filename.lastIndexOf('/');
      if ( i > 0 ) dirname = filename.substring(0, i+1);
      // System.out.println("readFile dir " + dirname + " filename " + filename );
      // TDLog.Log( TDLog.LOG_THERION, "reading file " + filename + " dir " + dirname );
      // TDLog.Log( TDLog.LOG_IO, "import read Survex file <" + filename + ">" );

      BufferedReader br = TDio.getBufferedReader( isr, filename );
      String line = nextLine( br );
      while ( line != null ) {
        ++line_nr; // debug
        // TDLog.v( "Parser Survex " + state.in_survey + " " + state.in_centerline + " " + state.in_data + " : " + line );
        line = line.trim();
        int pos = line.indexOf( ';' );
        if ( pos >= 0 ) {
          line = line.substring( 0, pos ).trim();
        }
        if ( line.length() > 0 ) {
          String[] vals = pattern.split(line); // line.split( "\\s+" );
          int vals_len = vals.length;
          if ( vals[0].equalsIgnoreCase("*begin") ) {
            state = new ParserSurvexState( state, vals_len > 1 ? vals[1] : null );
          } else if ( vals[0].equalsIgnoreCase("*end") ) {
            if ( state.mParent != null ) {
              state = state.mParent;
            } else {
              TDLog.e( "SVX [E] " + line_nr + " end outside a block" );
            }
          } else if ( vals_len == 1 ) {
	    if ( vals[0].startsWith("*") ) {
	        String cmd = vals[0].substring(1).toLowerCase( Locale.ROOT );
	        if ( cmd.equals("solve" ) ) {
                // ignore
	      } else if ( cmd.equals("data" ) ) { // reset the traverse, retaining its format
                in_station = true;
                haveMeasurements = false;
                from = null;
	      }
	    } else { 
              // interleaved station
	      if ( state.interleaved && in_station ) { // read station
                to = checkAlias( ParserUtil.applyCase( state.mCase, vals[0] ) );
		if ( from != null && haveMeasurements ) { // add shot
                  shots.add( new ParserShot( from, to, len, ber, cln, 0.0f, ExtendType.EXTEND_RIGHT, LegType.NORMAL, state.mDuplicate, state.mSurface, false, "" ) );
		}
		from = to;
		in_station = false; // in_data
	      }
	    }
	  } else if ( vals_len > 1 ) {
            // all commands have parameters, except "*solve" which is ignored
	    // and data line have at least two values
	    if ( vals[0].startsWith("*") ) {
              String cmd = vals[0].substring(1).toLowerCase( Locale.ROOT );
              if ( cmd.equals("alias" ) ) { 
                // alias station <alias> <target>
		if ( "station".equals( vals[1] ) ) {
                  if ( vals_len >= 4 ) {
		    String target = vals[vals_len - 1];
		    for ( int j=1; j<vals_len-1; ++j ) {
                      aliases.put( vals[j], target );
		    }
		  } else if ( vals_len == 3 ) {
		    aliases.remove( vals[1] );
		  }
		// } else {
                //   TDLog.Log(  , "survex parser: unsupported alias " + vals[1] );
		}
              } else if ( cmd.equals("include") ) {
                TDLog.v("SVX " + line_nr + " ignore include " );
              } else if ( cmd.equals("copyright") ) {
                TDLog.v("SVX " + line_nr + " ignore copyright " );
              } else if ( cmd.equals("entrance") ) {
                TDLog.v("SVX " + line_nr + " ignore entrance " );
              } else if ( cmd.equals("export") ) {
                TDLog.v("SVX " + line_nr + " ignore export " );
              } else if ( cmd.equals("infer") ) {
                TDLog.v("SVX " + line_nr + " ignore infer " );
              } else if ( cmd.equals("instrument") ) {
                TDLog.v("SVX " + line_nr + " ignore instrument " );
              } else if ( cmd.equals("prefix") ) {
                TDLog.v("SVX " + line_nr + " ignore prefix " );
              } else if ( cmd.equals("ref") ) {
                TDLog.v("SVX " + line_nr + " ignore ref " );
              } else if ( cmd.equals("require") ) {
                TDLog.v("SVX " + line_nr + " ignore require " );
              } else if ( cmd.equals("sd") ) {
                TDLog.v("SVX " + line_nr + " ignore sd " );
              } else if ( cmd.equals("truncate") ) {
                TDLog.v("SVX " + line_nr + " ignore truncate " );
              } else if ( cmd.equals("case") ) {
                TDLog.v("SVX " + line_nr + " TODO case " );
              } else if ( cmd.equals("set") ) {
                // set blank x09x20
		// set decimal ,
                TDLog.v("SVX " + line_nr + " TODO set " );
              } else if ( cmd.equals("default") ) {
                // default calibrate|data|units|all
		if ( vals_len > 1 ) { // always true
		  String what = vals[1].toLowerCase( Locale.getDefault() );
		  if ( what.equals("calibrate") ) {
                    state.setCalibrateDefault();
		  } else if ( what.equals("data") ) {
                    state.setDataDefault();
		  } else if ( what.equals("units") ) {
                    state.setUnitsDefault();
		  } else if ( what.equals("all") ) {
                    state.setCalibrateDefault();
                    state.setDataDefault();
                    state.setUnitsDefault();
		  }
		}
              } else if ( cmd.equals("title") ) {
                if ( mTitle == null && vals_len > 1 ) {
                  for ( int j = 1; j<vals_len; ++j ) {
                    if ( vals[j].length() == 0 ) continue;
                    if ( vals[j].startsWith("\"") ) {
                      if ( vals[j].endsWith( "\"" ) ) { 
                        mTitle = vals[j].substring(1, vals[j].length()-1 );
                      } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append( vals[j].substring(1) );
                        for ( ++j; j<vals_len; ++j ) {
                          if ( vals[j].length() == 0 ) continue;
                          if ( vals[j].endsWith( "\"" ) ) { // skip final " and end loop
                            sb.append(" ").append(vals[j].substring(0, vals[j].length()-1));
                            break;
                          } else {
                            sb.append(" ").append(vals[j] );
                          }
                        }
                        mTitle = sb.toString();
                      }
                    } else {
                      mTitle = vals[j];
                    }
                    break;
                  }
                }
              } else if ( cmd.equals("date") ) {
                String date = vals[1];
                if ( mDate == null ) mDate = date; // save date
                TDLog.v("SVX " + line_nr + " date " + mDate );
              } else if ( cmd.equals("team") ) {
                if ( mTeam.length() > 0 ) {
                  mTeam = mTeam + " " + TDUtil.concat( vals, 1 );
                } else {
                  mTeam = mTeam + TDUtil.concat( vals, 1 );
                }
                TDLog.v("SVX " + line_nr + " team " + mTeam );
              } else if ( cmd.equals("calibrate") ) {
                try {
                  setCalibration( state, vals );
                } catch ( NumberFormatException e ) {
                  TDLog.e( "SVX [E] " + line_nr + " calibrate " + line );
                }
              } else if ( cmd.equals("units") ) {
                try {
                  setUnits( state, vals );
                } catch ( NumberFormatException e ) {
                  TDLog.e( "SVX [E] " + line_nr + " units " + line );
                }
              } else if ( cmd.equals("declination") ) { 
                // declination auto [<x> <y> <z>]
		// declination <value> [<units>]
                if ( vals_len > 1 ) {
	          if ( vals[1].equals("auto") ) { // declination reset
                    state.mDeclination = ( state.mParent == null )? 0 : state.mParent.mDeclination;
                    if ( ! mApplyDeclination ) mDeclination = state.mDeclination;
	          } else {
                    try {
                      float declination = Float.parseFloat( vals[1] );
                      if ( vals_len > 2 ) {
                        declination *= ParserUtil.parseAngleUnit( vals[2] );
                      }
                      state.mDeclination = declination;
                      if ( ! mApplyDeclination ) mDeclination = state.mDeclination;
                    } catch ( NumberFormatException e ) {
                      TDLog.e( "SVX [E] " + line_nr + " declination " + line );
	            }
                  }      
                }      
              } else if ( cmd.equals("flags") ) {
                int k = 1;
                if ( k < vals_len ) {
	          boolean val = true;
                  String what = vals[k].toLowerCase( Locale.getDefault() );
                  if ( what.equals("not") ) {
                    val = false;
	            ++k;
	          }
	          if ( k < vals_len ) {
                    what = vals[k].toLowerCase( Locale.getDefault() );
                    if ( what.equals("duplicate") ) {
                      state.mDuplicate = val;
	            } else if ( what.equals("splay") ) {
                      state.mSplay = val;
                    } else if ( what.equals("surface") ) {
                      state.mSurface = val;
                    }
                  }
                }
              } else if ( cmd.equals("cs") ) { 
                TDLog.v("SVX " + line_nr + " TODO cs " + line );
              } else if ( cmd.equals("fix") ) { // ***** fix station east north Z (ignored std-dev's)
                if ( vals_len > 4 ) {
                  String name = checkAlias( ParserUtil.applyCase( state.mCase, vals[1] ) );
                  try {
	            fixes.add( new Fix( name,
                                        Float.parseFloat( vals[2] ),
                                        Float.parseFloat( vals[3] ),
                                        Float.parseFloat( vals[4] ) ) );
                  } catch ( NumberFormatException e ) {
                    TDLog.e( "SVX [E] " + line_nr + " fix " + line );
                  }
                }
              } else if ( cmd.equals("equate") ) {
                // equate station station ...
                if ( vals_len > 2 ) {
                  from = checkAlias( ParserUtil.applyCase( state.mCase, vals[1] ) );
                  for ( int j=2; j<vals_len; ++j ) {
                    to = checkAlias( ParserUtil.applyCase( state.mCase, vals[j] ) ); 
                    shots.add( new ParserShot( from, to, 0.0f, 0.0f, 0.0f, 0.0f, 0, LegType.NORMAL, true, false, false, "" ) );
                  }
                }
              } else if ( cmd.equals("data") ) {
                haveMeasurements = false;
                // data normal from to length compass clino ...
	        // data default
	        String what = vals[1].toLowerCase( Locale.getDefault() );
                if ( what.equals("normal") ) {
                  state.data_type = ParserUtil.DATA_NORMAL;
	          if ( setJIndices( state, vals, vals_len ) ) {
                    state.interleaved = true;
		    in_station = true;
		    from = null;
		    len = ber = cln = 0;
		  } else {
                    state.interleaved = false;
		  }
                } else if ( what.equals("topofil") ) {
                  state.data_type = ParserUtil.DATA_TOPOFIL;
	          if ( setJIndices( state, vals, vals_len ) ) {
                    state.interleaved = true;
		    in_station = true;
		    from = null;
		    len = ber = cln = 0;
		  } else {
                    state.interleaved = false;
		  }
                // TODO other style syntax
                } else if ( what.equals("diving") ) {
                  state.data_type = ParserUtil.DATA_DIVING;
                } else if ( what.equals("cartesian") ) {
                  state.data_type = ParserUtil.DATA_CARTESIAN;
                } else if ( what.equals("cylpolar") ) {
                  state.data_type = ParserUtil.DATA_CYLPOLAR;
                } else if ( what.equals("passage") ) {
                  state.data_type = ParserUtil.DATA_PASSAGE;
	          setJIndicesPassage( state, vals, vals_len );
                  state.interleaved = false;
                } else if ( what.equals("nosurvey") ) {
                  state.data_type = ParserUtil.DATA_NOSURVEY;
                } else { // "default"
                  state.setDataDefault();
                }
	      }
            } else {
	      if ( state.interleaved ) {
		if ( in_station ) {
                  to = checkAlias( ParserUtil.applyCase( state.mCase, vals[0] ) );
		  if ( from != null && haveMeasurements ) { // add shot
                    shots.add( new ParserShot( from, to, len, ber, cln, 0.0f, ExtendType.EXTEND_RIGHT, LegType.NORMAL, state.mDuplicate, state.mSurface, false, "" ) );
		  }
		  from = to;
                  in_station = false;
		} else {
                  try {
                    float[] measurements = readMeasurements( state, vals );
                    len = measurements[0];
                    ber = measurements[1];
                    cln = measurements[2];
                    haveMeasurements = true;

                  } catch ( NumberFormatException e ) {
                    haveMeasurements = false;
                    TDLog.e( "SVX [E] " + line_nr + " data " + line );
                  }
                  in_station = true;
		}
	      } else {
	        if ( vals_len >= 2 ) {
                  if ( state.data_type == ParserUtil.DATA_NORMAL
                    || state.data_type == ParserUtil.DATA_TOPOFIL ) {
                    // TDLog.v("SVX " + line_nr + " data " + line );
                    try {
                      from = checkAlias( ParserUtil.applyCase( state.mCase, requiredField( vals, state.jFrom ) ) );
                      to   = checkAlias( ParserUtil.applyCase( state.mCase, requiredField( vals, state.jTo ) ) );
                      float[] measurements = readMeasurements( state, vals );
                      len = measurements[0];
                      ber = measurements[1];
                      cln = measurements[2];
                      float zLen = state.mZero[LENGTH];
                      float sLen = state.mScale[LENGTH] * state.mUnit[LENGTH];

                      float dist, b;
                      if ( state.jLeft >= 0 && state.jLeft < vals_len) {
                        try {
                          dist = Float.parseFloat( vals[state.jLeft] ) * sLen - zLen;
                          // b = ber - 90; if ( b < 0 ) b += 360;
                          b = TDMath.in360( ber - 90 );
                          shots.add( new ParserShot( from, TDString.EMPTY, dist, b, 0, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                        } catch ( NumberFormatException e ) { }
                      }
                      if ( state.jRight >= 0 && state.jRight < vals_len) {
                        try {
                          dist = Float.parseFloat( vals[state.jRight] ) * sLen - zLen;
                          // b = ber + 90; if ( b >= 360 ) b -= 360;
                          b = TDMath.add90( ber );
                          shots.add( new ParserShot( from, TDString.EMPTY, dist, b, 0, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                        } catch ( NumberFormatException e ) { }
                      }
                      if ( state.jUp >= 0 && state.jUp < vals_len) {
                        try {
                          dist = Float.parseFloat( vals[state.jUp] ) * sLen - zLen;
                          shots.add( new ParserShot( from, TDString.EMPTY,
                                     dist, 0, 90, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                        } catch ( NumberFormatException e ) { }
                      }
                      if ( state.jDown >= 0 && state.jDown < vals_len) {
                        try {
                          dist = Float.parseFloat( vals[state.jDown] ) * sLen - zLen;
                          shots.add( new ParserShot( from, TDString.EMPTY,
                                     dist, 0, -90, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                        } catch ( NumberFormatException e ) { }
                      }

                      // TODO add shot
                      if ( to.isEmpty() ) { // anonymous splay station
                        // FIXME splays
                        shots.add( new ParserShot( from, TDString.EMPTY, len, ber, cln, 0.0f, ExtendType.EXTEND_UNSET, LegType.NORMAL, state.mDuplicate, state.mSurface, false, "" ) );
                      } else {
                        // TDLog.v( "Parser Survex add shot " + from + " -- " + to);
                        shots.add( new ParserShot( from, to, len, ber, cln, 0.0f, ExtendType.EXTEND_RIGHT, LegType.NORMAL, state.mDuplicate, state.mSurface, false, "" ) );
                      }
                    } catch ( NumberFormatException e ) {
                      TDLog.e( "SVX [E] " + line_nr + " data " + line );
                    }
                  } else if ( state.data_type == ParserUtil.DATA_PASSAGE ) {
                    from = checkAlias( ParserUtil.applyCase( state.mCase, vals[state.jStation] ) );
	            float zLen = state.mZero[LENGTH];
	            float sLen = state.mScale[LENGTH] * state.mUnit[LENGTH];
                    float dist;
                    // if ( state.jLeft >= 0 && state.jLeft < vals_len) {
                    //   dist = Float.parseFloat( vals[state.jLeft] ) * sLen - zLen;
                    // }
                    // if ( state.jRight >= 0 && state.jRight < vals_len) {
                    //   dist = Float.parseFloat( vals[state.jRight] ) * sLen - zLen;
                    // }
                    if ( state.jUp >= 0 && state.jUp < vals_len) {
                      try {
                        dist = Float.parseFloat( vals[state.jUp] ) * sLen - zLen;
                        shots.add( new ParserShot( from, TDString.EMPTY,
                                 dist, 0, 90, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                      } catch ( NumberFormatException e ) { }
                    }
                    if ( state.jDown >= 0 && state.jDown < vals_len) {
                      try {
                        dist = Float.parseFloat( vals[state.jDown] ) * sLen - zLen;
                        shots.add( new ParserShot( from, TDString.EMPTY,
                                 dist, 0, -90, 0.0f, ExtendType.EXTEND_UNSET, LegType.XSPLAY, state.mDuplicate, state.mSurface, false, "" ) );
                      } catch ( NumberFormatException e ) { }
                    }
                  }
                }
                // FIXME other data types
              }
            }            
          }
        }
        line = nextLine( br );
      }
    } catch ( IOException e ) {
      throw new ParserException();
    }

    if ( mDate == null ) mDate = TDUtil.currentDate();
    if ( mTitle == null ) mTitle = TDString.EMPTY;
    // TDLog.Log( TDLog.LOG_THERION, "Parser Survex shots "+ shots.size() +" splays "+ splays.size() +" fixes "+  fixes.size() );
    // TDLog.v( "Parser Survex shots "+ shots.size() + " splays "+ splays.size() +" fixes "+  fixes.size() );
  }

  /** Map instruments and measurement aliases to independent settings. */
  private static int quantity( String name )
  {
    switch ( name.toLowerCase( Locale.ROOT ) ) {
      case "length": case "tape": return LENGTH;
      case "bearing": case "compass": return BEARING;
      case "gradient": case "clino": return CLINO;
      case "backlength": case "backtape": return BACK_LENGTH;
      case "backbearing": case "backcompass": return BACK_BEARING;
      case "backgradient": case "backclino": return BACK_CLINO;
      default: return -1;
    }
  }

  private static float unit( int q, String name )
  {
    return q == LENGTH || q == BACK_LENGTH ? ParserUtil.parseLengthUnit( name )
                                          : ParserUtil.parseAngleUnit( name );
  }

  private static float number( String value )
  {
    float result = Float.parseFloat( value );
    if ( Float.isNaN( result ) || Float.isInfinite( result ) ) throw new NumberFormatException( value );
    return result;
  }

  private static void setUnits( ParserSurvexState state, String[] vals )
  {
    if ( vals[1].equalsIgnoreCase("default") ) {
      state.setUnitsDefault();
      return;
    }
    int end = 1;
    while ( end < vals.length && quantity( vals[end] ) >= 0 ) ++end;
    int remaining = vals.length - end;
    if ( end == 1 || (remaining != 1 && remaining != 2) ) throw new NumberFormatException("units");
    float factor = remaining == 2 ? number( vals[end] ) : 1.0f;
    if ( factor == 0 ) throw new NumberFormatException("zero unit factor");
    for ( int i = 1; i < end; ++i ) {
      int q = quantity( vals[i] );
      state.mUnit[q] = factor * unit( q, vals[vals.length - 1] );
    }
  }

  private void setCalibration( ParserSurvexState state, String[] vals )
  {
    if ( vals[1].equalsIgnoreCase("default") ) {
      state.setCalibrateDefault();
      return;
    }
    int end = 1;
    while ( end < vals.length && (quantity( vals[end] ) >= 0 || vals[end].equalsIgnoreCase("declination")) ) ++end;
    if ( end == 1 || end == vals.length ) throw new NumberFormatException("calibration");
    int k = end;
    float zero = number( vals[k++] );
    float scale = 1.0f;
    String zeroUnit = null;
    if ( k < vals.length ) {
      try {
        scale = number( vals[k] );
      } catch ( NumberFormatException e ) {
        zeroUnit = vals[k];
      }
      ++k;
      if ( zeroUnit != null && k < vals.length ) scale = number( vals[k++] );
    }
    if ( k != vals.length || scale == 0 ) throw new NumberFormatException("calibration scale");
    for ( int i = 1; i < end; ++i ) {
      int q = quantity( vals[i] );
      if ( q >= 0 ) {
        float units = zeroUnit == null ? state.mUnit[q] : unit( q, zeroUnit );
        state.mZero[q] = zero * scale * units;
        state.mScale[q] = scale;
      } else {
        state.mDeclination = zero * scale * (zeroUnit == null ? 1.0f : ParserUtil.parseAngleUnit( zeroUnit ));
        if ( ! mApplyDeclination ) mDeclination = state.mDeclination;
      }
    }
  }

  private static String field( String[] vals, int index )
  {
    if ( index < 0 ) return null;
    if ( index >= vals.length ) throw new NumberFormatException("missing data column");
    return vals[index].equals("-") ? null : vals[index];
  }

  private static String requiredField( String[] vals, int index )
  {
    if ( index < 0 || index >= vals.length ) throw new NumberFormatException("missing station column");
    return vals[index];
  }

  private static float reading( ParserSurvexState state, int q, String value )
  {
    return value == null ? Float.NaN : number( value ) * state.mUnit[q] * state.mScale[q] - state.mZero[q];
  }

  private static float mean( float forward, float backward )
  {
    if ( Float.isNaN( forward ) ) return backward;
    if ( Float.isNaN( backward ) ) return forward;
    return (forward + backward) * 0.5f;
  }

  /** Explicit plumbs bypass instrument calibration, including angular units. */
  private static int plumb( String value )
  {
    if ( value == null ) return 0;
    switch ( value.toUpperCase( Locale.ROOT ) ) {
      case "UP": case "U": case "+V": return 1;
      case "DOWN": case "D": case "-V": return -1;
      default: return 0;
    }
  }

  /** Reduce fore/back observations to one leg in the declared from-to direction. */
  private float[] readMeasurements( ParserSurvexState state, String[] vals )
  {
    float length = mean( reading( state, LENGTH, field( vals, state.jLength ) ),
                         reading( state, BACK_LENGTH, field( vals, state.jBackLength ) ) );
    if ( Float.isNaN( length ) ) throw new NumberFormatException("missing tape");
    String clino = field( vals, state.jClino );
    String backClino = field( vals, state.jBackClino );
    int up = plumb( clino );
    int backUp = plumb( backClino );
    float bearing = reading( state, BEARING, field( vals, state.jCompass ) );
    float backBearing = reading( state, BACK_BEARING, field( vals, state.jBackCompass ) );
    if ( up != 0 || backUp != 0 ) {
      if ( (clino != null && up == 0) || (backClino != null && backUp == 0)
           || (up != 0 && backUp != 0 && up == backUp) ) {
        throw new NumberFormatException("inconsistent plumb readings");
      }
      return new float[] { length, 0.0f, up != 0 ? up * 90.0f : -backUp * 90.0f };
    }
    if ( ! Float.isNaN( bearing ) ) bearing = TDMath.in360( bearing );
    if ( ! Float.isNaN( backBearing ) ) backBearing = TDMath.in360( backBearing + 180.0f );
    float resultBearing = mean( bearing, backBearing );
    if ( Float.isNaN( resultBearing ) ) throw new NumberFormatException("missing compass");
    if ( ! Float.isNaN( bearing ) && ! Float.isNaN( backBearing ) && Math.abs( bearing - backBearing ) > 180.0f ) {
      resultBearing += 180.0f;
    }
    if ( mApplyDeclination ) resultBearing += state.mDeclination;
    float resultClino = mean( reading( state, CLINO, clino ), -reading( state, BACK_CLINO, backClino ) );
    // Survex permits an omitted clino; its nominal vertical component is zero.
    if ( Float.isNaN( resultClino ) ) resultClino = 0.0f;
    return new float[] { length, TDMath.in360( resultBearing ), resultClino };
  }

  /** @return true for interleaved station/measurement rows. */
  private boolean setJIndices( ParserSurvexState state, String[] vals, int vals_len )
  {
    state.clearDataIndices();
    boolean interleaved = vals_len > 2 && vals[2].equalsIgnoreCase("station");
    int start = 2;
    if ( interleaved ) {
      while ( start < vals_len && ! vals[start].equalsIgnoreCase("newline") ) ++start;
      ++start;
    }
    for ( int j = start, column = 0; j < vals_len; ++j, ++column ) {
      switch ( vals[j].toLowerCase( Locale.ROOT ) ) {
        case "from": state.jFrom = column; break;
        case "to": state.jTo = column; break;
        case "length": case "tape": state.jLength = column; break;
        case "bearing": case "compass": state.jCompass = column; break;
        case "gradient": case "clino": state.jClino = column; break;
        case "backlength": case "backtape": state.jBackLength = column; break;
        case "backbearing": case "backcompass": state.jBackCompass = column; break;
        case "backgradient": case "backclino": state.jBackClino = column; break;
        case "left": state.jLeft = column; break;
        case "right": state.jRight = column; break;
        case "up": state.jUp = column; break;
        case "down": state.jDown = column; break;
      }
    }
    return interleaved;
  }

  private void setJIndicesPassage( ParserSurvexState state, String[] vals, int vals_len )
  {
    state.clearDataIndices();
    for ( int j = 2, column = 0; j < vals_len; ++j, ++column ) {
      switch ( vals[j].toLowerCase( Locale.ROOT ) ) {
        case "station": state.jStation = column; break;
        case "left": state.jLeft = column; break;
        case "right": state.jRight = column; break;
        case "up": state.jUp = column; break;
        case "down": state.jDown = column; break;
      }
    }
  }

  private String checkAlias( String name )
  {
    if ( name.equals(".") || name.equals("..") || name.equals("-") ) return "";
    if ( aliases.containsKey( name ) ) return aliases.get( name );
    return name;
  }
}
