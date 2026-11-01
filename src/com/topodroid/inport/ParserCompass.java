/* @file ParserCompass.java
 *
 * @author marco corvi
 * @date nov 2014
 *
 * @brief TopoDroid Compass parser
 *
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * ----------------------------------------------------------
 */
package com.topodroid.inport;

import com.topodroid.util.TDLog;
import com.topodroid.util.TDio;
import com.topodroid.util.TDString;
import com.topodroid.util.TDUtil;
import com.topodroid.prefs.TDSetting;
import com.topodroid.TDX.TDAzimuth;
import com.topodroid.types.ExtendType;
import com.topodroid.types.LegType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;

class ParserCompass extends ImportParser
{
  private final boolean mLrud;
  private final boolean mLegFirst;
  private final boolean mCavewhereUnits;

  /** Settings belong to one survey header, not to the complete DAT file. */
  private static class Survey
  {
    float declination;
    float compassCorrection, clinoCorrection, tapeCorrection;
    float backCompassCorrection, backClinoCorrection;
    float lengthUnit = TDUtil.FT2M;
    float lrudUnit = TDUtil.FT2M;
    boolean backsights;
    boolean lrudTo;
    final HashMap<String, Float> stationBearings = new HashMap<>();
  }

  ParserCompass( InputStreamReader isr, String filename, boolean applyDeclination,
                 boolean lrud, boolean legFirst ) throws ParserException
  {
    this( isr, filename, applyDeclination, lrud, legFirst, false );
  }

  ParserCompass( InputStreamReader isr, String filename, boolean applyDeclination,
                 boolean lrud, boolean legFirst, boolean cavewhereUnits ) throws ParserException
  {
    super( applyDeclination );
    mLrud = lrud;
    mLegFirst = legFirst;
    mCavewhereUnits = cavewhereUnits;
    mDate = null;
    mDeclination = 0;
    readFile( isr, filename );
    checkValid();
  }

  private static float number( String value )
  {
    float result = Float.parseFloat( value );
    if ( Float.isNaN( result ) || Float.isInfinite( result ) ) {
      throw new NumberFormatException( "non-finite reading" );
    }
    return result;
  }

  private static float bearing( float value )
  {
    value %= 360;
    return value < 0 ? value + 360 : value;
  }

  private static float angleReading( String value, float correction, boolean clino )
  {
    float raw = number( value );
    if ( raw < -900 ) return Float.NaN;
    if ( clino ? raw < -90 || raw > 90 : raw < 0 || raw > 360 ) return Float.NaN;
    float corrected = raw + correction;
    if ( Float.isInfinite( corrected ) ) throw new NumberFormatException( "angle correction overflow" );
    if ( clino ) return corrected < -90 || corrected > 90 ? Float.NaN : corrected;
    return bearing( corrected );
  }

  private static float mean( float forward, float backward, boolean circular )
  {
    if ( Float.isNaN( forward ) ) return backward;
    if ( Float.isNaN( backward ) ) return forward;
    float result = (forward + backward) / 2;
    if ( circular && Math.abs( forward - backward ) > 180 ) result += 180;
    return circular ? bearing( result ) : result;
  }

  private static float dimension( String value, float unit )
  {
    try {
      float raw = number( value );
      return raw < 0 ? Float.NaN : raw * unit;
    } catch ( NumberFormatException e ) {
      TDLog.e( "Compass LRUD: " + e.getMessage() );
      return Float.NaN;
    }
  }

  /** FORMAT describes notebook/display units; standard DAT numbers remain feet/degrees. */
  private void readFormat( Survey survey, String format )
  {
    int backIndex = format.length() >= 15 ? 13 : 11;
    survey.backsights = format.length() > backIndex && format.charAt( backIndex ) == 'B';
    survey.lrudTo = format.length() > backIndex + 1 && format.charAt( backIndex + 1 ) == 'T';
    if ( mCavewhereUnits ) {
      // Cavewhere exports native metric numbers. Only use this dialect when explicitly selected.
      survey.lengthUnit = format.length() > 1 && format.charAt( 1 ) == 'M' ? 1 : TDUtil.FT2M;
      survey.lrudUnit = format.length() > 2 && format.charAt( 2 ) == 'M' ? 1 : TDUtil.FT2M;
    }
  }

  private void readHeader( Survey survey, String line )
  {
    String[] fields = splitLine( line );
    for ( int i = 0; i < fields.length; ++i ) {
      switch ( fields[i].toUpperCase( Locale.ROOT ) ) {
        case "DECLINATION:":
          survey.declination = number( fields[++i] );
          if ( ! mApplyDeclination && shots.isEmpty() ) mDeclination = survey.declination;
          break;
        case "FORMAT:": readFormat( survey, fields[++i] ); break;
        case "CORRECTIONS:":
          survey.compassCorrection = number( fields[++i] );
          survey.clinoCorrection = number( fields[++i] );
          survey.tapeCorrection = number( fields[++i] );
          break;
        case "CORRECTIONS2:":
          survey.backCompassCorrection = number( fields[++i] );
          survey.backClinoCorrection = number( fields[++i] );
          break;
        default: break;
      }
    }
  }

  private void readDate( String line )
  {
    if ( mDate != null ) return;
    String[] fields = splitLine( line.substring( line.indexOf( ':' ) + 1 ).trim() );
    if ( fields.length < 3 ) return;
    int year = Integer.parseInt( fields[2] );
    if ( year < 100 ) year += year < 50 ? 2000 : 1900;
    mDate = String.format( Locale.US, "%04d.%02d.%02d", year,
                           Integer.parseInt( fields[0] ), Integer.parseInt( fields[1] ) );
    int comment = line.toUpperCase( Locale.ROOT ).indexOf( "COMMENT:" );
    if ( comment >= 0 ) mTitle = line.substring( comment + 8 ).trim();
  }

  private void readFile( InputStreamReader isr, String filename ) throws ParserException
  {
    BufferedReader reader = TDio.getBufferedReader( isr, filename );
    if ( reader == null ) throw new ParserException();
    Survey survey = null;
    boolean data = false;
    String line;
    try {
      while ( (line = reader.readLine()) != null ) {
        ++mLineCnt;
        int separator = line.lastIndexOf( '\f' );
        if ( separator >= 0 ) {
          survey = null;
          data = false;
          line = line.substring( separator + 1 );
        }
        line = line.trim();
        String upper = line.toUpperCase( Locale.ROOT );
        if ( upper.startsWith( "SURVEY NAME:" ) ) {
          survey = new Survey();
          data = false;
          if ( mName == null ) mName = line.substring( line.indexOf( ':' ) + 1 ).trim();
        } else if ( survey == null || line.isEmpty() ) {
          continue;
        } else if ( upper.startsWith( "SURVEY DATE:" ) ) {
          readDate( line );
        } else if ( upper.startsWith( "SURVEY TEAM:" ) ) {
          String team = reader.readLine();
          ++mLineCnt;
          if ( team != null && mTeam.isEmpty() ) mTeam = team.trim();
        } else if ( upper.startsWith( "DECLINATION:" ) || upper.startsWith( "FORMAT:" )
                 || upper.startsWith( "CORRECTIONS:" ) || upper.startsWith( "CORRECTIONS2:" ) ) {
          readHeader( survey, line );
        } else if ( upper.matches( "FROM\\s+TO\\s+(LEN|LENGTH)\\s+.*" ) ) {
          data = true;
        } else if ( data ) {
          try {
            readShot( survey, splitLine( line ) );
          } catch ( NumberFormatException e ) {
            TDLog.e( "Compass row " + mLineCnt + ": " + e.getMessage() );
          }
        }
      }
    } catch ( IOException | NumberFormatException | IndexOutOfBoundsException e ) {
      TDLog.e( "Compass header " + mLineCnt + ": " + e.getMessage() );
      throw new ParserException();
    }
    if ( mDate == null ) mDate = TDUtil.currentDate();
  }

  private void readShot( Survey survey, String[] fields )
  {
    if ( fields.length < 9 ) throw new NumberFormatException( "incomplete shot" );
    String from = fields[0];
    String to = fields[1];
    float rawLength = number( fields[2] );
    if ( rawLength < 0 ) throw new NumberFormatException( "missing or negative length" );
    float forwardBearing = angleReading( fields[3], survey.compassCorrection, false );
    float forwardClino = angleReading( fields[4], survey.clinoCorrection, true );
    float backwardBearing = Float.NaN, backwardClino = Float.NaN;
    int column = 9;
    if ( survey.backsights && fields.length >= 11 && ! fields[column].startsWith( "#" ) ) {
      backwardBearing = angleReading( fields[column++], survey.backCompassCorrection, false );
      if ( ! Float.isNaN( backwardBearing ) ) backwardBearing = bearing( backwardBearing + 180 );
      backwardClino = -angleReading( fields[column++], survey.backClinoCorrection, true );
    }
    String flags = "";
    if ( column < fields.length && fields[column].startsWith( "#|" ) && fields[column].endsWith( "#" ) ) {
      flags = fields[column++].toUpperCase( Locale.ROOT );
    }
    if ( flags.indexOf( 'X' ) >= 0 ) return;
    String comment = column < fields.length ? TDUtil.concat( fields, column ) : "";
    boolean duplicate = flags.indexOf( 'L' ) >= 0;
    boolean noPlot = flags.indexOf( 'P' ) >= 0;
    boolean stationOnly = from.equals( to ) && rawLength == 0;
    float length = stationOnly ? 0 : (rawLength + survey.tapeCorrection) * survey.lengthUnit;
    if ( length < 0 || Float.isInfinite( length ) ) throw new NumberFormatException( "invalid corrected length" );
    float clino = mean( forwardClino, backwardClino, false );
    float azimuth = mean( forwardBearing, backwardBearing, true );
    if ( stationOnly ) {
      // Exporters use zero-length self records for the final station's LRUD, not traverse legs.
      Float previous = survey.stationBearings.get( from );
      azimuth = previous == null ? Float.NaN : previous;
    } else {
      if ( Float.isNaN( clino ) ) throw new NumberFormatException( "missing inclination" );
      if ( Float.isNaN( azimuth ) ) {
        if ( Math.abs( clino ) != 90 ) throw new NumberFormatException( "missing bearing" );
        azimuth = 0;
      }
      if ( mApplyDeclination ) azimuth = bearing( azimuth + survey.declination );
      survey.stationBearings.put( from, azimuth );
      survey.stationBearings.put( to, azimuth );
    }
    ParserShot leg = null;
    if ( ! stationOnly ) {
      int extend = azimuth < 90 || azimuth > 270 ? 1 : -1;
      leg = new ParserShot( from, to, length, azimuth, clino, 0, extend, LegType.NORMAL,
                            duplicate, false, false, comment );
      leg.noPlot = noPlot;
      if ( mLegFirst ) shots.add( leg );
    }
    if ( mLrud ) {
      String station = survey.lrudTo ? to : from;
      float left = dimension( fields[5], survey.lrudUnit );
      float up = dimension( fields[6], survey.lrudUnit );
      float down = dimension( fields[7], survey.lrudUnit );
      float right = dimension( fields[8], survey.lrudUnit );
      if ( ! Float.isNaN( azimuth ) ) {
        addLrud( station, left, bearing( azimuth + 270 ), 0, noPlot );
        addLrud( station, right, bearing( azimuth + 90 ), 0, noPlot );
      }
      addLrud( station, up, 0, 90, noPlot );
      addLrud( station, down, 0, -90, noPlot );
    }
    if ( leg != null && ! mLegFirst ) shots.add( leg );
  }

  private void addLrud( String station, float length, float azimuth, float clino, boolean noPlot )
  {
    if ( Float.isNaN( length ) || length <= 0 ) return;
    int extend = clino == 0
        ? (TDSetting.mLRExtend ? (int)TDAzimuth.computeSplayExtend( azimuth ) : ExtendType.EXTEND_UNSET)
        : ExtendType.EXTEND_VERT;
    ParserShot shot = new ParserShot( station, TDString.EMPTY, length, azimuth, clino, 0,
                                     extend, LegType.XSPLAY, false, false, false, "" );
    shot.noPlot = noPlot;
    shots.add( shot );
  }
}
