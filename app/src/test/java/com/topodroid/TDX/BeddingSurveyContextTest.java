package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.topodroid.types.BlockType;
import com.topodroid.geo.BeddingMeasurementModel;
import com.topodroid.geo.BeddingObservation;

import java.util.Arrays;

import org.junit.Test;

public class BeddingSurveyContextTest
{
  @Test public void adapter_reversesBacksplayAndExcludesScanRows()
  {
    DBlock direct = block( 1, "A", "", 4.0f, 25.0f, 15.0f, BlockType.SPLAY );
    DBlock reverse = block( 2, "", "A", 5.0f, 40.0f, -20.0f, BlockType.SPLAY );
    DBlock scan = block( 3, "A", "", 6.0f, 70.0f, 10.0f, BlockType.SCAN );
    BeddingSurveyContext context = new BeddingSurveyContext( "A", Arrays.asList( direct, reverse, scan ) );

    assertEquals( "A", context.stationName );
    assertEquals( 3, context.splays.size() );
    assertEquals( 25.0, context.splays.get( 0 ).bearingDegrees, 1.0e-8 );
    assertEquals( 15.0, context.splays.get( 0 ).clinoDegrees, 1.0e-8 );
    assertEquals( 220.0, context.splays.get( 1 ).bearingDegrees, 1.0e-8 );
    assertEquals( 20.0, context.splays.get( 1 ).clinoDegrees, 1.0e-8 );
    assertTrue( context.splays.get( 0 ).eligible );
    assertTrue( context.splays.get( 1 ).eligible );
    assertFalse( context.splays.get( 2 ).eligible );
    assertEquals( "scan", context.splays.get( 2 ).exclusionReason );
  }

  @Test public void adapterAndObservationBuild_doNotMutateSurveyShotOrComment()
  {
    DBlock source = block( 8, "A", "", 7.25f, 123.5f, -17.25f, BlockType.SPLAY );
    source.mComment = "field note: keep exactly";
    BeddingSurveyContext context = new BeddingSurveyContext( "A", Arrays.asList( source ) );
    BeddingSurveyContext.Splay splay = context.splays.get( 0 );
    BeddingObservation observation = BeddingObservation.fromSplay( splay.id, splay.displayLabel(),
      splay.type, splay.lengthMeters, splay.bearingDegrees, splay.clinoDegrees,
      BeddingMeasurementModel.distoxConservativeV1() );
    assertTrue( observation != null );
    assertEquals( 7.25f, source.mLength, 0.0f );
    assertEquals( 123.5f, source.mBearing, 0.0f );
    assertEquals( -17.25f, source.mClino, 0.0f );
    assertEquals( "field note: keep exactly", source.mComment );
  }

  private static DBlock block( long id, String from, String to, float length,
                               float bearing, float clino, int type )
  {
    DBlock block = new DBlock();
    block.mId = id;
    block.mFrom = from;
    block.mTo = to;
    block.mLength = length;
    block.mBearing = bearing;
    block.mClino = clino;
    block.resetBlockType( type );
    return block;
  }
}
