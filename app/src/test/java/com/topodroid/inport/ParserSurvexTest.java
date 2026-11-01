package com.topodroid.inport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.junit.Test;

public class ParserSurvexTest
{
  private static final float EPS = 0.0001f;
  private static final String NORMAL = "*data normal from to tape compass backcompass clino backclino\n";

  private static ParserSurvex parse( String data, boolean declination ) throws Exception
  {
    try ( InputStreamReader reader = new InputStreamReader(
        new ByteArrayInputStream( data.getBytes( StandardCharsets.UTF_8 ) ), StandardCharsets.UTF_8 ) ) {
      ParserSurvex parser = new ParserSurvex( reader, "regression.svx", declination );
      assertTrue( parser.isValid() );
      return parser;
    }
  }

  private static ArrayList<ParserShot> shots( String data ) throws Exception
  {
    return parse( data, false ).getShots();
  }

  private static void assertShot( ParserShot shot, String from, String to, float length, float bearing, float clino )
  {
    assertEquals( from, shot.from );
    assertEquals( to, shot.to );
    assertEquals( length, shot.len, EPS );
    assertEquals( bearing, shot.ber, EPS );
    assertEquals( clino, shot.cln, EPS );
  }

  @Test public void anonymousTrips_restoreMetresAfterFeetAndPassageData() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*begin cave\n*begin ; Imperial trip\n*units tape feet\n" + NORMAL +
      "a b 10 0 - 0 -\n*data passage station left right up down ignoreall\n*end\n" +
      "*begin ; Metric trip\n" + NORMAL + "b c 10 0 - 0 -\n*end\n*end cave\n" );
    assertEquals( 2, result.size() );
    assertShot( result.get(0), "a", "b", 3.048f, 0, 0 );
    assertShot( result.get(1), "b", "c", 10, 0, 0 );
  }

  @Test public void nestedNamedAndAnonymousBlocks_restoreUnitsCalibrationAndColumnOrder() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*begin outer\n*units tape feet\n*calibrate tape 1\n" +
      "*data normal from to compass tape clino\na b 90 11 2\n" +
      "*begin\n*units tape metres\n*calibrate default\n*data normal from to tape clino compass\n" +
      "b c 10 4 180\n*begin inner\n*units tape 0.1 metres\nc d 10 6 270\n*end inner\n" +
      "d e 10 8 0\n*end\ne f 90 11 10\n*end outer\nf g 10 180 12\n" );
    assertEquals( 6, result.size() );
    assertShot( result.get(0), "a", "b", 3.048f, 90, 2 );
    assertShot( result.get(1), "b", "c", 10, 180, 4 );
    assertShot( result.get(2), "c", "d", 1, 270, 6 );
    assertShot( result.get(3), "d", "e", 10, 0, 8 );
    assertShot( result.get(4), "e", "f", 3.048f, 90, 10 );
    assertShot( result.get(5), "f", "g", 10, 180, 12 );
  }

  @Test public void backsightOnly_retainsStationsAndReversesAngles() throws Exception
  {
    ArrayList<ParserShot> result = shots( "*units tape feet\n" + NORMAL + "a28 a29 12.7 - 161.4 - 1.4\n" );
    assertEquals( 1, result.size() );
    assertShot( result.get(0), "a28", "a29", 3.87096f, 341.4f, -1.4f );
  }

  @Test public void forwardAndBackReadings_areSelectedIndependently() throws Exception
  {
    ArrayList<ParserShot> result = shots( NORMAL +
      "a b 10 90 - - -5\nb c 10 - 270 5 -\nc d 10 90 - - -\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "b", 10, 90, 5 );
    assertShot( result.get(1), "b", "c", 10, 90, 5 );
    assertShot( result.get(2), "c", "d", 10, 90, 0 );
  }

  @Test public void omittedForwardColumnsAndCompassFreePlumbs_areAccepted() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*data normal from to tape backcompass backclino\na b 10 270 -5\n" +
      "*data normal from to tape clino\nb c 10 UP\nc d 10 DOWN\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "b", 10, 90, 5 );
    assertShot( result.get(1), "b", "c", 10, 0, 90 );
    assertShot( result.get(2), "c", "d", 10, 0, -90 );
  }

  @Test public void pairedReadings_averageAfterReversalAndWrapAtNorth() throws Exception
  {
    ArrayList<ParserShot> result = shots( NORMAL +
      "a b 10 359 181 10 -12\nb c 10 1 179 -10 12\nc d 10 90 272 4 -6\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "b", 10, 0, 11 );
    assertShot( result.get(1), "b", "c", 10, 0, -11 );
    assertShot( result.get(2), "c", "d", 10, 91, 5 );
  }

  @Test public void backsightUnitsCalibrationAndAliases_areIndependentAndScoped() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*data normal from to tape bearing backbearing gradient backgradient\n" +
      "*begin\n*units backbearing backgradient grads\n" +
      "*calibrate backbearing 2 grads\n*calibrate backgradient 1 grads 2\n" +
      "a b 10 - 102 - 6\n*end\nb c 10 - 270 - -9\n" );
    assertEquals( 2, result.size() );
    assertShot( result.get(0), "a", "b", 10, 270, -9 );
    assertShot( result.get(1), "b", "c", 10, 90, 9 );
  }

  @Test public void backTape_usesItsOwnUnitsAndCombinesWithForwardTape() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*units backtape feet\n*data normal from to tape backtape compass clino\n" +
      "a b - 10 90 0\nb c 3.048 10 90 0\n" );
    assertEquals( 2, result.size() );
    assertShot( result.get(0), "a", "b", 3.048f, 90, 0 );
    assertShot( result.get(1), "b", "c", 3.048f, 90, 0 );
  }

  @Test public void upDownAndAliases_areVerticalRegardlessOfAngleUnitsOrCalibration() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*units tape feet\n*units clino grads\n*calibrate clino 5 2\n" + NORMAL +
      "a b 40 0 - UP -\nb c 10 - - down -\nc d 10 - - U -\n" +
      "d e 10 - - D -\ne f 10 - - +V -\nf g 10 - - -V -\n" );
    assertEquals( 6, result.size() );
    assertShot( result.get(0), "a", "b", 12.192f, 0, 90 );
    for ( int i = 1; i < result.size(); ++i ) {
      assertEquals( 3.048f, result.get(i).len, EPS );
      assertEquals( i % 2 == 0 ? 90 : -90, result.get(i).cln, EPS );
    }
  }

  @Test public void backPlumbs_areReversedAndPairedPlumbsAgree() throws Exception
  {
    ArrayList<ParserShot> result = shots( NORMAL +
      "a b 10 - - - UP\nb c 10 - - - DOWN\nc d 10 - - UP DOWN\nd e 10 - - DOWN UP\n" );
    assertEquals( 4, result.size() );
    assertShot( result.get(0), "a", "b", 10, 0, -90 );
    assertShot( result.get(1), "b", "c", 10, 0, 90 );
    assertShot( result.get(2), "c", "d", 10, 0, 90 );
    assertShot( result.get(3), "d", "e", 10, 0, -90 );
  }

  @Test public void interleavedData_reducesBacksightsAndPlumbs() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*data normal station newline tape compass backcompass clino backclino\n" +
      "a\n10 - 270 - -5\nb\n10 359 181 4 -6\nc\n10 - - UP -\nd\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "b", 10, 90, 5 );
    assertShot( result.get(1), "b", "c", 10, 0, 5 );
    assertShot( result.get(2), "c", "d", 10, 0, 90 );
  }

  @Test public void declination_isAppliedOnceAfterCombiningBearings() throws Exception
  {
    ArrayList<ParserShot> result = parse(
      "*declination 10\n" + NORMAL + "a b 10 359 181 0 0\nb c 10 - 270 - -5\n", true ).getShots();
    assertShot( result.get(0), "a", "b", 10, 10, 0 );
    assertShot( result.get(1), "b", "c", 10, 100, 5 );
  }

  @Test public void interleavedUnitScopes_preserveStationsAndBareDataResetsOnlyTheTraverse() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*data normal station newline tape compass backcompass clino backclino\n" +
      "a\n*begin\n*units tape feet\n10 - 270 - -5\nb\n*end\n10 - 270 - -5\nc\n" +
      "*data\nx\n10 - 270 - -5\ny\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "b", 3.048f, 90, 5 );
    assertShot( result.get(1), "b", "c", 10, 90, 5 );
    assertShot( result.get(2), "x", "y", 10, 90, 5 );
  }

  @Test public void unitsAndDataDefaults_resetBacksightSettingsAndColumns() throws Exception
  {
    ArrayList<ParserShot> result = shots(
      "*units tape feet\n*units backcompass backclino 2 grads\n*units default\n" + NORMAL +
      "a b 10 - 270 - -5\n*data default\nb c 10 90 5\n" );
    assertEquals( 2, result.size() );
    assertShot( result.get(0), "a", "b", 10, 90, 5 );
    assertShot( result.get(1), "b", "c", 10, 90, 5 );
  }

  @Test public void anonymousSplays_keepBacksightAndVerticalMeasurements() throws Exception
  {
    ArrayList<ParserShot> result = shots( NORMAL +
      "a - 10 - 270 - -5\na . 2 - - UP -\na b 3 90 - 0 -\n" );
    assertEquals( 3, result.size() );
    assertShot( result.get(0), "a", "", 10, 90, 5 );
    assertShot( result.get(1), "a", "", 2, 0, 90 );
    assertShot( result.get(2), "a", "b", 3, 90, 0 );
  }
}
