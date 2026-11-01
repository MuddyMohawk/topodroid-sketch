package com.topodroid.inport;

import static org.junit.Assert.*;

import com.topodroid.util.TDLog;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ParserCompassTest
{
  private static final float EPS = .0001f;
  private static final String LRUD = " -999 -999 -999 -999";
  private Field logStream, logWriter;
  private Object oldLogStream, oldLogWriter;

  @Before public void captureParserDiagnosticsWithoutAndroid() throws Exception
  {
    logStream = TDLog.class.getDeclaredField( "mLogStream" );
    logWriter = TDLog.class.getDeclaredField( "mLog" );
    logStream.setAccessible( true );
    logWriter.setAccessible( true );
    oldLogStream = logStream.get( null );
    oldLogWriter = logWriter.get( null );
    logStream.setInt( null, TDLog.LOG_FILE );
    logWriter.set( null, new PrintWriter( new StringWriter() ) );
  }

  @After public void restoreLogging() throws Exception
  {
    logStream.set( null, oldLogStream );
    logWriter.set( null, oldLogWriter );
  }

  private static String survey( String name, String header, String rows )
  {
    return "Cave\nSURVEY NAME: " + name + "\nSURVEY DATE: 4 27 2025 COMMENT: Test survey\n" +
           "SURVEY TEAM:\nTest team\n" + header + "\n\n" +
           "FROM TO LENGTH BEARING INC LEFT UP DOWN RIGHT AZM2 INC2 FLAGS COMMENTS\n\n" + rows + "\n\f\n";
  }

  private static ParserCompass parse( String text, boolean lrud, boolean legFirst,
                                      boolean cavewhere, boolean declination ) throws Exception
  {
    try ( InputStreamReader reader = new InputStreamReader( new ByteArrayInputStream(
        text.getBytes( StandardCharsets.UTF_8 ) ), StandardCharsets.UTF_8 ) ) {
      return new ParserCompass( reader, "test.dat", declination, lrud, legFirst, cavewhere );
    }
  }

  private static ParserCompass parse( String header, String rows ) throws Exception
  {
    return parse( survey( "test", header, rows ), false, true, false, true );
  }

  private static void assertShot( ParserShot shot, String from, String to, float length, float bearing, float clino )
  {
    assertEquals( from, shot.from );
    assertEquals( to, shot.to );
    assertEquals( length, shot.len, EPS );
    assertEquals( bearing, shot.ber, EPS );
    assertEquals( clino, shot.cln, EPS );
  }

  @Test public void standardDat_usesFeetEvenWithMetricNotebookHeader() throws Exception
  {
    ParserCompass p = parse( "DECLINATION: 0 FORMAT: DMMDLUDRLADN",
                             "A B 32.8084 90 0" + LRUD );
    assertTrue( p.isValid() );
    assertShot( p.getShots().get(0), "A", "B", 10, 90, 0 );
    assertEquals( "2025.04.27", p.mDate );
    assertEquals( "Test survey", p.mTitle );
  }

  @Test public void cavewhereUnits_areExplicitAndResetForEachSurvey() throws Exception
  {
    String data = survey( "metric", "DECLINATION: 0 FORMAT: DMMDLUDRLADN", "A B 5.25 90 0" + LRUD ) +
                  survey( "feet", "DECLINATION: 0 FORMAT: DDDDLUDRLADN", "B C 10 90 0" + LRUD ) +
                  survey( "metric2", "DECLINATION: 0 FORMAT: DMMDLUDRLADN", "C D 2.55 90 0" + LRUD );
    ArrayList<ParserShot> result = parse( data, false, true, true, true ).getShots();
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "A", "B", 5.25f, 90, 0 );
    assertShot( result.get(1), "B", "C", 3.048f, 90, 0 );
    assertShot( result.get(2), "C", "D", 2.55f, 90, 0 );
    assertEquals( 1.6002f, parse( data, false, true, false, true ).getShots().get(0).len, EPS );
  }

  @Test public void nativeDistanceAndLrudUnits_areIndependent() throws Exception
  {
    ArrayList<ParserShot> result = parse( survey( "mix", "DECLINATION: 0 FORMAT: DMDDLUDRLADN",
        "A B 10 90 0 1 2 3 4" ), true, true, true, true ).getShots();
    assertEquals( 5, result.size() );
    assertEquals( 10, result.get(0).len, EPS );
    assertEquals( .3048f, result.get(1).len, EPS );
    assertEquals( 1.2192f, result.get(2).len, EPS );
  }

  @Test public void redundantBacksights_areReversedAndAveraged() throws Exception
  {
    assertShot( parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB", "A B 10 90 4" + LRUD + " 272 -6" )
        .getShots().get(0), "A", "B", 3.048f, 91, 5 );
  }

  @Test public void circularAverage_andDeclination_areAppliedOnce() throws Exception
  {
    ParserCompass p = parse( "DECLINATION: 10 FORMAT: DDDDLUDRLADadBF",
                             "A B 10 359 0" + LRUD + " 181 0" );
    assertShot( p.getShots().get(0), "A", "B", 3.048f, 10, 0 );
    assertEquals( 0, p.mDeclination, EPS );
  }

  @Test public void backsightOnly_andMixedComponents_keepValidReadings() throws Exception
  {
    ArrayList<ParserShot> result = parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB",
        "A B 10 -999 -999" + LRUD + " 161.4 1.4\n" +
        "B C 10 90 -999" + LRUD + " -999 -5\n" +
        "C D 10 -999 4" + LRUD + " 272 -999" ).getShots();
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "A", "B", 3.048f, 341.4f, -1.4f );
    assertShot( result.get(1), "B", "C", 3.048f, 90, 5 );
    assertShot( result.get(2), "C", "D", 3.048f, 92, 4 );
  }

  @Test public void corrections_applyBeforeReductionAndConvertTapeFeet() throws Exception
  {
    assertShot( parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB CORRECTIONS: 2 3 4 CORRECTIONS2: 5 6",
        "A B 10 90 4" + LRUD + " 272 -6" ).getShots().get(0), "A", "B", 4.2672f, 94.5f, 3.5f );
  }

  @Test public void backsightCorrections_applyToRawBackReadings() throws Exception
  {
    assertShot( parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB CORRECTIONS2: 5 6",
        "A B 10 -999 -999" + LRUD + " 272 -6" ).getShots().get(0), "A", "B", 3.048f, 97, 0 );
  }

  @Test public void eachSurvey_hasItsOwnDeclinationAndCorrections() throws Exception
  {
    String data = survey( "first", "DECLINATION: 10 FORMAT: DDDDLUDRLADB CORRECTIONS: 2 3 4",
                          "A B 10 90 0" + LRUD + " -999 -999" ) +
                  survey( "second", "DECLINATION: -10 FORMAT: DDDDLUDRLADN", "B C 10 90 0" + LRUD );
    ArrayList<ParserShot> result = parse( data, false, true, false, true ).getShots();
    assertShot( result.get(0), "A", "B", 4.2672f, 102, 3 );
    assertShot( result.get(1), "B", "C", 3.048f, 80, 0 );
  }

  @Test public void declinationCanRemainMetadataForOneSurvey() throws Exception
  {
    ParserCompass p = parse( survey( "test", "DECLINATION: 10 FORMAT: DDDDLUDRLADN",
        "A B 10 90 0" + LRUD ), false, true, false, false );
    assertEquals( 10, p.mDeclination, EPS );
    assertEquals( 90, p.getShots().get(0).ber, EPS );
  }

  @Test public void invalidOrMissingMeasurements_doNotCreateShots() throws Exception
  {
    ParserCompass p = parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB",
        "A B 10 -999 -999" + LRUD + " -999 -999\n" +
        "B C -999 90 0" + LRUD + " -999 -999\n" +
        "C D NaN 90 0" + LRUD + " -999 -999\n" +
        "D E 10 Infinity 0" + LRUD + " -999 -999\n" +
        "E F 10 90 999" + LRUD + " -999 -999\n" +
        "F G 10 90 0" + LRUD + " -999 -999" );
    assertEquals( 1, p.getShots().size() );
    assertShot( p.getShots().get(0), "F", "G", 3.048f, 90, 0 );
  }

  @Test public void numericVerticalShots_workWithoutCompassReadings() throws Exception
  {
    ArrayList<ParserShot> result = parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADN",
        "A B 10 -999 90" + LRUD + "\nB C 10 -999 -90" + LRUD ).getShots();
    assertEquals( 2, result.size() );
    assertShot( result.get(0), "A", "B", 3.048f, 0, 90 );
    assertShot( result.get(1), "B", "C", 3.048f, 0, -90 );
  }

  @Test public void flagsAndComments_afterBacksights_areRetained() throws Exception
  {
    ArrayList<ParserShot> result = parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADB",
        "A B 10 90 0" + LRUD + " 270 0 #|LP# keep this comment\n" +
        "B C 10 90 0" + LRUD + " 270 0 #|X# exclude entirely\n" +
        "C D 10 90 0" + LRUD + " 270 0" ).getShots();
    assertEquals( 2, result.size() );
    assertTrue( result.get(0).duplicate );
    assertTrue( result.get(0).noPlot );
    assertEquals( "keep this comment", result.get(0).comment );
    assertFalse( result.get(1).duplicate );
    assertFalse( result.get(1).noPlot );
    assertEquals( "", result.get(1).comment );
  }

  @Test public void numericComments_withoutBacksights_areNotMeasurements() throws Exception
  {
    ParserShot shot = parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADN",
        "A B 10 90 4" + LRUD + " 272 -6 numerical note" ).getShots().get(0);
    assertEquals( 90, shot.ber, EPS );
    assertEquals( 4, shot.cln, EPS );
    assertEquals( "272 -6 numerical note", shot.comment );
  }

  @Test public void toStationLrud_usesFixedColumnOrderAndHonoursLegOrdering() throws Exception
  {
    String data = survey( "to", "DECLINATION: 0 FORMAT: DDDDLRUDLADadBT",
                          "A B 10 90 0 1 2 3 4 270 0 #|P#" );
    ArrayList<ParserShot> result = parse( data, true, false, false, true ).getShots();
    assertEquals( 5, result.size() );
    assertShot( result.get(0), "B", "", .3048f, 0, 0 );
    assertShot( result.get(1), "B", "", 1.2192f, 180, 0 );
    assertShot( result.get(2), "B", "", .6096f, 0, 90 );
    assertShot( result.get(3), "B", "", .9144f, 0, -90 );
    assertShot( result.get(4), "A", "B", 3.048f, 90, 0 );
    for ( ParserShot s : result ) assertTrue( s.noPlot );
  }

  @Test public void terminalStationRecords_preserveLrudWithoutInventingLegs() throws Exception
  {
    String data = survey( "last", "DECLINATION: 0 FORMAT: DDDDLUDRLADB CORRECTIONS: 0 0 -1",
        "A B 10 90 0" + LRUD + " 270 0\nB B 0 -999 -999 1 2 3 4 -999 -999" );
    ArrayList<ParserShot> result = parse( data, true, true, false, true ).getShots();
    assertEquals( 5, result.size() );
    assertShot( result.get(0), "A", "B", 2.7432f, 90, 0 );
    assertShot( result.get(1), "B", "", .3048f, 0, 0 );
    assertShot( result.get(4), "B", "", .9144f, 0, -90 );
  }

  @Test public void malformedLrud_doesNotDropTheValidTraverse() throws Exception
  {
    ArrayList<ParserShot> result = parse( survey( "lrud", "DECLINATION: 0 FORMAT: DDDDLUDRLADN",
        "A B 10 90 0 NaN -999 -999 -999" ), true, true, false, true ).getShots();
    assertEquals( 1, result.size() );
    assertShot( result.get(0), "A", "B", 3.048f, 90, 0 );
  }

  @Test public void invalidHeader_isRejectedInsteadOfUsingOldCorrections() throws Exception
  {
    assertThrows( ParserException.class, () -> parse( "DECLINATION: 0 FORMAT: DDDDLUDRLADN CORRECTIONS: 2",
        "A B 10 90 0" + LRUD ) );
  }
}
