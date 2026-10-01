package com.topodroid.TDX;

import static org.junit.Assert.*;

import com.topodroid.num.TDNum;
import com.topodroid.types.LegType;
import com.topodroid.util.TDUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ShotSiblingCheckTest
{
  @Test public void correctReverseAcrossNorthPasses()
  {
    DBlock fore = shot( 1, "A", "B", 10, 359, 12 );
    DBlock back = shot( 2, "B", "A", 10, 179, -12 );
    assertTrue( check( fore, back ).isEmpty() );
  }

  @Test public void flagsIndividualReverseShotsRegardlessOfBackLegCheckbox()
  {
    DBlock fore = shot( 1, "A", "B", 10, 90, 5 );
    DBlock forwardRepeat = shot( 2, "A", "B", 15, 90, 5 );
    DBlock good = shot( 3, "B", "A", 10, 270, -5 );
    DBlock badDistance = shot( 4, "B", "A", 12, 270, -5 );
    DBlock badBearing = shot( 5, "B", "A", 10, 250, -5 );
    DBlock badClino = shot( 6, "B", "A", 10, 270, 5 );
    badDistance.setTypeBackLeg();
    Map< Long, ShotSiblingCheck.ExpectedBacksight > warnings = check( fore, forwardRepeat, good, badDistance, badBearing, badClino );
    assertEquals( 3, warnings.size() );
    assertTrue( warnings.containsKey( 4L ) );
    assertTrue( warnings.containsKey( 5L ) );
    assertTrue( warnings.containsKey( 6L ) );
  }

  @Test public void thresholdIsStrictAndUsesCombinedRelativeVectorDistance()
  {
    DBlock fore = shot( 1, "A", "B", 10, 0, 0 );
    DBlock back = shot( 2, "B", "A", 11, 180, 0 );
    float difference = ShotSiblingCheck.relativeDifference( fore, back, true, 0 );
    assertEquals( 1.0f / 10 + 1.0f / 11, difference, 0.000001f );
    List< DBlock > shots = Arrays.asList( fore, back );
    assertTrue( ShotSiblingCheck.badBacksights( shots, 0, difference, false, false ).isEmpty() );
    assertEquals( 1, ShotSiblingCheck.badBacksights( shots, 0, Math.nextDown( difference ), false, false ).size() );
  }

  @Test public void retainsFirstReferenceRatherThanRepeatedMeasurementAverage()
  {
    DBlock fore = shot( 1, "A", "B", 10, 350, 12 );
    DBlock extra = shot( 2, "", "", 15, 340, 15 );
    extra.setBlockTypeFromLegType( LegType.EXTRA );
    DBlock later = shot( 3, "A", "B", 12, 340, 15 );
    DBlock back = shot( 4, "B", "A", 12, 160, -15 );
    ShotSiblingCheck.ExpectedBacksight expected = check( fore, extra, later, back ).get( 4L );
    assertNotNull( expected );
    assertEquals( 10, expected.length, 0 );
    assertEquals( 170, expected.bearing, 0 );
    assertEquals( -12, expected.clino, 0 );
  }

  @Test public void magneticAnomalySuppressesWarnings()
  {
    List< DBlock > shots = Arrays.asList( shot( 1, "A", "B", 10, 0, 0 ), shot( 2, "B", "A", 20, 180, 0 ) );
    assertTrue( ShotSiblingCheck.badBacksights( shots, 0, 0.05f, true, false ).isEmpty() );
  }

  @Test public void missingPairsSplaysAndSecondaryShotsDoNotWarn()
  {
    DBlock splay = shot( 1, "A", "", 10, 0, 0 );
    DBlock missing = shot( 2, "B", "A", 20, 180, 0 );
    DBlock secondary = shot( 3, "A", "B", 10, 0, 0 );
    secondary.setTypeSecLeg();
    assertTrue( check( splay, missing, secondary, new DBlock() ).isEmpty() );
  }

  @Test public void formatsExplicitUnitsAndNormalizesReverseZero()
  {
    ShotSiblingCheck.ExpectedBacksight expected = check(
      shot( 1, "A", "B", 10, 180, 0 ), shot( 2, "B", "A", 20, 0, 0 ) ).get( 2L );
    assertEquals( "10.00 m, 0.0\u00b0, 0.0\u00b0", expected.format( 1, "m", 1, "deg" ) );
    expected = new ShotSiblingCheck.ExpectedBacksight( 10, 270, -5 );
    assertEquals( "32.81 ft, 270.0\u00b0, -5.0\u00b0", expected.format( TDUtil.M2FT, "ft", 1, "deg" ) );
    assertEquals( "10.00 m, 300.0 grad, -5.6 grad", expected.format( 1, "m", TDUtil.DEG2GRAD, "grad" ) );
    expected = check( shot( 1, "A", "B", 10, -200, 5 ), shot( 2, "B", "A", 20, 340, -5 ) ).get( 2L );
    assertEquals( 340, expected.bearing, 0 );
    expected = check( shot( 1, "A", "B", 10, 720, 5 ), shot( 2, "B", "A", 20, 180, -5 ) ).get( 2L );
    assertEquals( 180, expected.bearing, 0 );
  }

  @Test public void checkingDoesNotModifyCommentsMeasurementsOrSketchFlags()
  {
    DBlock fore = shot( 1, "A", "B", 10, 0, 0 );
    DBlock back = shot( 2, "B", "A", 20, 180, 0 );
    back.mComment = "Error. M: 1.0000 2.0000; surveyor note";
    fore.setMultiBad( true );
    assertEquals( 1, check( fore, back ).size() );
    assertEquals( "Error. M: 1.0000 2.0000; surveyor note", back.mComment );
    assertEquals( 20, back.mLength, 0 );
    assertEquals( 180, back.mBearing, 0 );
    assertTrue( fore.isMultiBad() );
    assertFalse( back.isMultiBad() );
    back.mLength = 10;
    assertTrue( check( fore, back ).isEmpty() );
  }

  @Test public void divingUsesSketchDepthPreparationWithoutChangingStoredClino()
  {
    DBlock fore = shot( 1, "A", "B", 10, 0, 83 );
    DBlock back = shot( 2, "B", "A", 20, 180, -72 );
    fore.mDepth = 6;
    back.mDepth = 0;
    ShotSiblingCheck.ExpectedBacksight expected = ShotSiblingCheck.badBacksights(
      Arrays.asList( fore, back ), 0, 0.05f, false, true ).get( 2L );
    assertNotNull( expected );
    assertEquals( 36.8699f, expected.clino, 0.001f );
    assertEquals( 83, fore.mClino, 0 );
    assertEquals( -72, back.mClino, 0 );
  }

  @Test public void sketchKeepsItsReferenceWarningAfterSharingCalculation()
  {
    int previousMode = TDInstance.datamode;
    TDInstance.datamode = SurveyInfo.DATAMODE_NORMAL;
    try {
      DBlock fore = shot( 1, "A", "B", 10, 0, 0 );
      DBlock back = shot( 2, "B", "A", 20, 180, 0 );
      List< DBlock > shots = Arrays.asList( fore, back );
      new TDNum( shots, "A", 0, "", true );
      assertTrue( fore.isMultiBad() );
      assertFalse( back.isMultiBad() );
      assertEquals( 1, check( fore, back ).size() );
      back.mLength = 10;
      new TDNum( shots, "A", 0, "", true );
      assertFalse( fore.isMultiBad() );
    } finally {
      TDInstance.datamode = previousMode;
    }
  }

  private static Map< Long, ShotSiblingCheck.ExpectedBacksight > check( DBlock... shots )
  {
    return ShotSiblingCheck.badBacksights( Arrays.asList( shots ), 0, 0.05f, false, false );
  }

  private static DBlock shot( long id, String from, String to, float length, float bearing, float clino )
  {
    DBlock block = new DBlock();
    block.mId = id;
    block.setBlockName( from, to, false );
    block.mLength = length;
    block.mBearing = bearing;
    block.mClino = clino;
    return block;
  }
}
