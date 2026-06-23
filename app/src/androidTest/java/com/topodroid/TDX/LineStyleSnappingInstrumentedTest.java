package com.topodroid.TDX;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.topodroid.math.Point2D;
import com.topodroid.util.TDMath;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class LineStyleSnappingInstrumentedTest
{
  @Test
  public void snapEndpoint90_allowsOnlyCardinalAngles()
  {
    Point2D p = DrawingPointLineFilter.snapEndpoint( 0.0f, 0.0f, 30.0f, 100.0f, 90.0f );

    assertEquals( 90.0f, angleFromOrigin( p ), 0.001f );
    assertEquals( 0.0f, p.x, 0.001f );
    assertEquals( length( 30.0f, 100.0f ), length( p.x, p.y ), 0.001f );
  }

  @Test
  public void snapEndpoint45_roundsToNearestDiagonal()
  {
    Point2D p = DrawingPointLineFilter.snapEndpoint( 0.0f, 0.0f, 50.0f, 86.60254f, 45.0f );

    assertEquals( 45.0f, angleFromOrigin( p ), 0.001f );
    assertEquals( length( 50.0f, 86.60254f ), length( p.x, p.y ), 0.001f );
  }

  @Test
  public void snapEndpoint22_5_roundsToNearestHalfOctant()
  {
    Point2D p = DrawingPointLineFilter.snapEndpoint( 0.0f, 0.0f, 86.60254f, 50.0f, 22.5f );

    assertEquals( 22.5f, angleFromOrigin( p ), 0.001f );
    assertEquals( length( 86.60254f, 50.0f ), length( p.x, p.y ), 0.001f );
  }

  @Test
  public void snapPointList_keepsOnlyStartAndSnappedEnd()
  {
    ArrayList< Point2D > points = new ArrayList<>();
    points.add( new Point2D( 10.0f, 20.0f ) );
    points.add( new Point2D( 40.0f, 55.0f ) );
    points.add( new Point2D( 80.0f, 120.0f ) );

    ArrayList< Point2D > snapped = DrawingPointLineFilter.snap( points, 90.0f );

    assertEquals( 2, snapped.size() );
    assertEquals( 10.0f, snapped.get( 0 ).x, 0.001f );
    assertEquals( 20.0f, snapped.get( 0 ).y, 0.001f );
    assertEquals( 90.0f, angle( snapped.get( 0 ), snapped.get( 1 ) ), 0.001f );
  }

  private static float angleFromOrigin( Point2D p )
  {
    return angle( new Point2D( 0.0f, 0.0f ), p );
  }

  private static float angle( Point2D start, Point2D end )
  {
    return TDMath.in360( TDMath.atan2d( end.y - start.y, end.x - start.x ) );
  }

  private static float length( float x, float y )
  {
    return TDMath.sqrt( x * x + y * y );
  }
}
