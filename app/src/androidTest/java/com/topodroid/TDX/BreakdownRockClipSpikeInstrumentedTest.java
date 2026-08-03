package com.topodroid.TDX;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

@RunWith( AndroidJUnit4.class )
@LargeTest
public class BreakdownRockClipSpikeInstrumentedTest
{
  private static final int QUALITY_WIDTH = 1200;
  private static final int QUALITY_HEIGHT = 900;
  private static final int QUALITY_CELL_WIDTH = 400;
  private static final int QUALITY_CELL_HEIGHT = 280;
  private static final int PERF_WIDTH = 1600;
  private static final int PERF_HEIGHT = 1200;
  private static final int PERF_SAMPLES = 20;

  private Context mContext;
  private Instrumentation mInstrumentation;

  @Before public void setUp()
  {
    mInstrumentation = InstrumentationRegistry.getInstrumentation();
    mContext = mInstrumentation.getTargetContext().getApplicationContext();
  }

  @Test public void bakedInkQuality_weightByZoomMatrix_writesProofArtifact() throws Exception
  {
    Bitmap bitmap = Bitmap.createBitmap( QUALITY_WIDTH, QUALITY_HEIGHT, Bitmap.Config.ARGB_8888 );
    bitmap.eraseColor( Color.BLACK );
    Canvas canvas = new Canvas( bitmap );
    Paint label = labelPaint();
    float[] weights = { 1.0f, 2.0f, 5.0f };
    float[] zooms = { 1.0f, 4.0f, 16.0f };
    String[] weight_names = { "Thin W=1", "Standard W=2", "Thick W=5" };
    String[] zoom_names = { "heavy zoom-out", "working zoom", "4x export / close-up" };

    for ( int col = 0; col < weights.length; ++col ) {
      canvas.drawText( weight_names[col], col * QUALITY_CELL_WIDTH + 130.0f, 28.0f, label );
    }
    for ( int row = 0; row < zooms.length; ++row ) {
      canvas.drawText( zoom_names[row], 8.0f, row * QUALITY_CELL_HEIGHT + 62.0f, label );
      for ( int col = 0; col < weights.length; ++col ) {
        drawQualityCell( canvas, row, col, weights[col], zooms[row] );
      }
    }

    assertTrue( "Baked-ink spike proof is unexpectedly empty", countForeground( bitmap ) > 12000 );
    byte[] png = encodeBitmap( bitmap );
    File artifact = new File( artifactDir(), "breakdown-baked-ink-quality-matrix.png" );
    saveBytes( png, artifact );
    reportStream( "BREAKDOWN_CLIP_ARTIFACT " + artifact.getAbsolutePath() + "\n" );
    reportBase64( png );
    bitmap.recycle();
  }

  @Test public void cachedBakedInk_meetsPlannedFieldThresholds()
  {
    Bitmap bitmap = Bitmap.createBitmap( PERF_WIDTH, PERF_HEIGHT, Bitmap.Config.ARGB_8888 );
    Canvas canvas = new Canvas( bitmap );
    Paint paint = rockPaint( 2.0f );

    ArrayList< Rock > distributed = distributedFixture();
    long distributed_snapshot_ns = buildOccluderSnapshot( distributed );
    ArrayList< ArrayList< Rock > > distributed_clusters = buildOverlapClusters( distributed );
    long distributed_baked_snapshot_ns = buildBakedInkSnapshot( distributed_clusters, paint );
    Metrics distributed_metrics = measure( canvas, paint, distributed, distributed_clusters );

    ArrayList< Rock > dense = denseFixture();
    long dense_snapshot_ns = buildOccluderSnapshot( dense );
    ArrayList< ArrayList< Rock > > dense_clusters = buildOverlapClusters( dense );
    long dense_baked_snapshot_ns = buildBakedInkSnapshot( dense_clusters, paint );
    Metrics dense_metrics = measure( canvas, paint, dense, dense_clusters );
    bitmap.recycle();

    String metrics = String.format( Locale.US,
        "BREAKDOWN_CLIP_METRICS distributed_count=%d clusters=%d control_ms=%.3f clip_ms=%.3f clip_ratio=%.3f clear_ms=%.3f clear_ratio=%.3f baked_ms=%.3f baked_ratio=%.3f snapshot_ms=%.3f baked_snapshot_ms=%.3f "
      + "dense_count=%d clusters=%d control_ms=%.3f clip_ms=%.3f clip_ratio=%.3f clear_ms=%.3f clear_ratio=%.3f baked_ms=%.3f baked_ratio=%.3f snapshot_ms=%.3f baked_snapshot_ms=%.3f\n",
        distributed.size(), distributed_clusters.size(), distributed_metrics.controlMs(), distributed_metrics.clippedMs(), distributed_metrics.clipRatio(),
        distributed_metrics.clearedMs(), distributed_metrics.clearRatio(), distributed_metrics.bakedMs(), distributed_metrics.bakedRatio(),
        distributed_snapshot_ns / 1.0e6, distributed_baked_snapshot_ns / 1.0e6,
        dense.size(), dense_clusters.size(), dense_metrics.controlMs(), dense_metrics.clippedMs(), dense_metrics.clipRatio(),
        dense_metrics.clearedMs(), dense_metrics.clearRatio(), dense_metrics.bakedMs(), dense_metrics.bakedRatio(),
        dense_snapshot_ns / 1.0e6, dense_baked_snapshot_ns / 1.0e6 );
    reportStream( metrics );
    System.out.print( metrics );

    assertTrue( "Distributed 500-rock baked-ink render exceeds 500 ms", distributed_metrics.bakedMs() < 500.0 );
    assertTrue( "Dense 50-rock baked-ink render exceeds 500 ms", dense_metrics.bakedMs() < 500.0 );
    assertTrue( "Distributed baked-ink render exceeds 2x control: " + distributed_metrics.bakedRatio(), distributed_metrics.bakedRatio() <= 2.0 );
    assertTrue( "Dense baked-ink render exceeds 2x control: " + dense_metrics.bakedRatio(), dense_metrics.bakedRatio() <= 2.0 );
  }

  private void drawQualityCell( Canvas canvas, int row, int col, float weight, float zoom )
  {
    int left = col * QUALITY_CELL_WIDTH;
    int top = row * QUALITY_CELL_HEIGHT + 42;
    int save = canvas.save();
    canvas.clipRect( left, top, left + QUALITY_CELL_WIDTH, top + QUALITY_CELL_HEIGHT - 4 );

    float footprint = weight / 2.0f;
    float cx = left + 0.57f * QUALITY_CELL_WIDTH;
    float cy = top + 0.54f * ( QUALITY_CELL_HEIGHT - 42 );
    Matrix view = values( zoom, 0.0f, cx, 0.0f, zoom, cy );
    Rock old_rock = transformedRock( angularStructure(), angularSilhouette(), footprint, -12.0f, -5.0f * footprint, 2.0f * footprint, view );
    Rock new_rock = transformedRock( roundStructure(), roundSilhouette(), 0.92f * footprint, 17.0f, 4.0f * footprint, -1.0f * footprint, view );

    Paint paint = rockPaint( weight );
    paint.setStrokeWidth( paint.getStrokeWidth() * zoom );
    ArrayList< Rock > cluster = new ArrayList<>();
    cluster.add( old_rock );
    cluster.add( new_rock );
    ArrayList< ArrayList< Rock > > clusters = new ArrayList<>();
    clusters.add( cluster );
    buildBakedInkSnapshot( clusters, paint );
    drawBakedClusters( canvas, paint, clusters );
    canvas.restoreToCount( save );
  }

  private Metrics measure( Canvas canvas, Paint paint, ArrayList< Rock > rocks, ArrayList< ArrayList< Rock > > clusters )
  {
    for ( int i = 0; i < 4; ++i ) {
      renderControl( canvas, paint, rocks );
      renderClipped( canvas, paint, rocks );
      renderCleared( canvas, paint, clusters );
      renderBaked( canvas, paint, clusters );
    }
    long[] control = new long[ PERF_SAMPLES ];
    long[] clipped = new long[ PERF_SAMPLES ];
    long[] cleared = new long[ PERF_SAMPLES ];
    long[] baked = new long[ PERF_SAMPLES ];
    for ( int i = 0; i < PERF_SAMPLES; ++i ) {
      long start = System.nanoTime();
      renderControl( canvas, paint, rocks );
      control[i] = System.nanoTime() - start;
      start = System.nanoTime();
      renderClipped( canvas, paint, rocks );
      clipped[i] = System.nanoTime() - start;
      start = System.nanoTime();
      renderCleared( canvas, paint, clusters );
      cleared[i] = System.nanoTime() - start;
      start = System.nanoTime();
      renderBaked( canvas, paint, clusters );
      baked[i] = System.nanoTime() - start;
    }
    Arrays.sort( control );
    Arrays.sort( clipped );
    Arrays.sort( cleared );
    Arrays.sort( baked );
    return new Metrics( median( control ), median( clipped ), median( cleared ), median( baked ) );
  }

  private void renderControl( Canvas canvas, Paint paint, ArrayList< Rock > rocks )
  {
    canvas.drawColor( Color.BLACK );
    for ( Rock rock : rocks ) canvas.drawPath( rock.structure, paint );
  }

  private void renderClipped( Canvas canvas, Paint paint, ArrayList< Rock > rocks )
  {
    canvas.drawColor( Color.BLACK );
    for ( Rock rock : rocks ) {
      if ( rock.occluders == null || rock.occluders.isEmpty() ) {
        canvas.drawPath( rock.structure, paint );
      } else {
        int save = canvas.save();
        clipOutPath( canvas, rock.occluders );
        canvas.drawPath( rock.structure, paint );
        canvas.restoreToCount( save );
      }
    }
  }

  private void renderCleared( Canvas canvas, Paint paint, ArrayList< ArrayList< Rock > > clusters )
  {
    canvas.drawColor( Color.BLACK );
    Paint clear = clearPaint();
    for ( ArrayList< Rock > cluster : clusters ) {
      if ( cluster.size() == 1 ) {
        canvas.drawPath( cluster.get( 0 ).structure, paint );
        continue;
      }
      RectF bounds = clusterBounds( cluster );
      int save = canvas.saveLayer( bounds, null );
      for ( int i = 0; i < cluster.size(); ++i ) {
        Rock rock = cluster.get( i );
        if ( i > 0 ) canvas.drawPath( rock.silhouette, clear );
        canvas.drawPath( rock.structure, paint );
      }
      canvas.restoreToCount( save );
    }
  }

  private void renderBaked( Canvas canvas, Paint paint, ArrayList< ArrayList< Rock > > clusters )
  {
    canvas.drawColor( Color.BLACK );
    drawBakedClusters( canvas, paint, clusters );
  }

  private void drawBakedClusters( Canvas canvas, Paint paint, ArrayList< ArrayList< Rock > > clusters )
  {
    Paint fill = new Paint( paint );
    fill.setStyle( Paint.Style.FILL );
    for ( ArrayList< Rock > cluster : clusters ) {
      if ( cluster.size() == 1 ) {
        canvas.drawPath( cluster.get( 0 ).structure, paint );
      } else {
        int top = cluster.size() - 1;
        for ( int i = 0; i < top; ++i ) canvas.drawPath( cluster.get( i ).visibleInk, fill );
        canvas.drawPath( cluster.get( top ).structure, paint );
      }
    }
  }

  private long buildBakedInkSnapshot( ArrayList< ArrayList< Rock > > clusters, Paint paint )
  {
    long start = System.nanoTime();
    for ( ArrayList< Rock > cluster : clusters ) {
      if ( cluster.size() == 1 ) continue;
      Path newer_silhouettes = new Path();
      boolean has_newer = false;
      for ( int i = cluster.size() - 1; i >= 0; --i ) {
        Rock rock = cluster.get( i );
        Path ink = new Path();
        paint.getFillPath( rock.structure, ink );
        if ( has_newer ) ink.op( newer_silhouettes, Path.Op.DIFFERENCE );
        rock.visibleInk = ink;
        if ( has_newer ) {
          newer_silhouettes.op( rock.silhouette, Path.Op.UNION );
        } else {
          newer_silhouettes.set( rock.silhouette );
          has_newer = true;
        }
      }
    }
    return System.nanoTime() - start;
  }

  private long buildOccluderSnapshot( ArrayList< Rock > rocks )
  {
    long start = System.nanoTime();
    for ( int i = 0; i < rocks.size(); ++i ) {
      Rock rock = rocks.get( i );
      Path union = null;
      for ( int j = i + 1; j < rocks.size(); ++j ) {
        Rock newer = rocks.get( j );
        if ( ! RectF.intersects( rock.bounds, newer.bounds ) ) continue;
        if ( union == null ) {
          union = new Path( newer.silhouette );
        } else {
          union.op( newer.silhouette, Path.Op.UNION );
        }
      }
      rock.occluders = union;
    }
    return System.nanoTime() - start;
  }

  private ArrayList< ArrayList< Rock > > buildOverlapClusters( ArrayList< Rock > rocks )
  {
    ArrayList< ArrayList< Rock > > clusters = new ArrayList<>();
    boolean[] used = new boolean[ rocks.size() ];
    for ( int seed = 0; seed < rocks.size(); ++seed ) {
      if ( used[seed] ) continue;
      ArrayList< Rock > cluster = new ArrayList<>();
      ArrayList< Integer > pending = new ArrayList<>();
      pending.add( seed );
      used[seed] = true;
      for ( int cursor = 0; cursor < pending.size(); ++cursor ) {
        int index = pending.get( cursor );
        Rock rock = rocks.get( index );
        cluster.add( rock );
        for ( int other = 0; other < rocks.size(); ++other ) {
          if ( used[other] || ! RectF.intersects( rock.bounds, rocks.get( other ).bounds ) ) continue;
          used[other] = true;
          pending.add( other );
        }
      }
      clusters.add( cluster );
    }
    return clusters;
  }

  private static RectF clusterBounds( ArrayList< Rock > cluster )
  {
    RectF bounds = new RectF( cluster.get( 0 ).bounds );
    for ( int i = 1; i < cluster.size(); ++i ) bounds.union( cluster.get( i ).bounds );
    bounds.inset( -4.0f, -4.0f );
    return bounds;
  }

  private ArrayList< Rock > distributedFixture()
  {
    ArrayList< Rock > rocks = new ArrayList<>();
    Path structure = roundStructure();
    Path silhouette = roundSilhouette();
    for ( int i = 0; i < 500; ++i ) {
      int col = i % 25;
      int row = i / 25;
      Matrix matrix = values( 1.0f, 0.0f, 34.0f + col * 62.0f, 0.0f, 1.0f, 32.0f + row * 56.0f );
      rocks.add( transformedRock( structure, silhouette, matrix ) );
    }
    return rocks;
  }

  private ArrayList< Rock > denseFixture()
  {
    ArrayList< Rock > rocks = new ArrayList<>();
    for ( int i = 0; i < 50; ++i ) {
      float angle = -18.0f + ( i % 13 ) * 3.0f;
      float scale = 8.0f + ( i % 5 ) * 0.18f;
      float tx = PERF_WIDTH * 0.5f + ( i % 10 - 4.5f ) * 4.0f;
      float ty = PERF_HEIGHT * 0.5f + ( i / 10 - 2.0f ) * 5.0f;
      rocks.add( transformedRock( ( i % 2 == 0 ) ? roundStructure() : angularStructure(),
                                  ( i % 2 == 0 ) ? roundSilhouette() : angularSilhouette(),
                                  scale, angle, tx, ty, null ) );
    }
    return rocks;
  }

  private Rock transformedRock( Path structure, Path silhouette, float scale, float angle, float tx, float ty, Matrix view )
  {
    float radians = (float)Math.toRadians( angle );
    float c = (float)Math.cos( radians ) * scale;
    float s = (float)Math.sin( radians ) * scale;
    Matrix local = values( c, -s, tx, s, c, ty );
    Rock rock = transformedRock( structure, silhouette, local );
    if ( view != null ) {
      rock.structure.transform( view );
      rock.silhouette.transform( view );
      rock.updateBounds();
    }
    return rock;
  }

  private Rock transformedRock( Path structure, Path silhouette, Matrix matrix )
  {
    Path transformed_structure = new Path( structure );
    Path transformed_silhouette = new Path( silhouette );
    transformed_structure.transform( matrix );
    transformed_silhouette.transform( matrix );
    return new Rock( transformed_structure, transformed_silhouette );
  }

  private static Matrix values( float m00, float m01, float tx, float m10, float m11, float ty )
  {
    Matrix matrix = new Matrix();
    matrix.setValues( new float[] { m00, m01, tx, m10, m11, ty, 0.0f, 0.0f, 1.0f } );
    return matrix;
  }

  private static Path roundSilhouette()
  {
    Path path = new Path();
    path.moveTo( 8.65f, -3.98f );
    path.cubicTo( 8.79f, -2.95f, 9.0f, -1.80f, 8.90f, -0.43f );
    path.cubicTo( 8.79f, 1.59f, 8.26f, 3.39f, 6.92f, 4.58f );
    path.cubicTo( 5.39f, 5.74f, 3.24f, 6.30f, 0.54f, 6.34f );
    path.cubicTo( -2.31f, 6.38f, -4.81f, 5.82f, -6.52f, 4.81f );
    path.cubicTo( -8.20f, 3.82f, -9.0f, 2.33f, -8.75f, 0.25f );
    path.cubicTo( -8.48f, -1.88f, -7.08f, -3.28f, -5.04f, -4.40f );
    path.cubicTo( -3.03f, -5.49f, -0.68f, -6.19f, 1.76f, -6.32f );
    path.cubicTo( 4.27f, -6.38f, 6.52f, -5.97f, 7.76f, -5.20f );
    path.cubicTo( 8.26f, -4.87f, 8.53f, -4.44f, 8.65f, -3.98f );
    path.close();
    return path;
  }

  private static Path roundStructure()
  {
    Path path = roundSilhouette();
    path.moveTo( -7.58f, 2.97f );
    path.cubicTo( -7.76f, 1.82f, -7.39f, 0.74f, -6.52f, -0.23f );
    path.cubicTo( -5.0f, -1.90f, -2.81f, -3.06f, -0.12f, -3.86f );
    path.cubicTo( 2.75f, -4.69f, 5.51f, -4.64f, 8.03f, -3.53f );
    return path;
  }

  private static Path angularSilhouette()
  {
    Path path = new Path();
    path.moveTo( -7.34f, -7.37f );
    path.cubicTo( -2.93f, -7.08f, 3.0f, -5.86f, 9.0f, -4.32f );
    path.cubicTo( 7.54f, -0.77f, 5.78f, 3.41f, 4.18f, 7.37f );
    path.cubicTo( 0.22f, 7.37f, -4.34f, 7.34f, -6.98f, 6.91f );
    path.cubicTo( -7.85f, 6.72f, -8.54f, 6.26f, -9.0f, 5.64f );
    path.cubicTo( -8.69f, 2.71f, -8.52f, -0.55f, -8.28f, -3.74f );
    path.lineTo( -7.34f, -7.37f );
    path.close();
    return path;
  }

  private static Path angularStructure()
  {
    Path path = angularSilhouette();
    path.moveTo( -8.09f, -1.27f );
    path.cubicTo( -7.97f, 0.82f, -7.49f, 2.86f, -6.72f, 4.27f );
    path.cubicTo( -3.19f, 4.49f, 0.58f, 4.92f, 3.62f, 4.94f );
    path.cubicTo( 5.18f, 3.0f, 6.60f, 0.29f, 7.80f, -2.45f );
    return path;
  }

  private static Paint rockPaint( float weight )
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setColor( Color.WHITE );
    paint.setStyle( Paint.Style.STROKE );
    paint.setStrokeCap( Paint.Cap.ROUND );
    paint.setStrokeJoin( Paint.Join.ROUND );
    paint.setStrokeWidth( weight * 0.3f * 0.5f );
    return paint;
  }

  private static Paint labelPaint()
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setColor( 0xffbbbbbb );
    paint.setTextSize( 20.0f );
    return paint;
  }

  private static Paint clearPaint()
  {
    Paint paint = new Paint( Paint.ANTI_ALIAS_FLAG );
    paint.setStyle( Paint.Style.FILL );
    paint.setXfermode( new PorterDuffXfermode( PorterDuff.Mode.CLEAR ) );
    return paint;
  }

  @SuppressWarnings( "deprecation" )
  private static void clipOutPath( Canvas canvas, Path path )
  {
    if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
      canvas.clipOutPath( path );
    } else {
      canvas.clipPath( path, android.graphics.Region.Op.DIFFERENCE );
    }
  }

  private static long median( long[] values )
  {
    int middle = values.length / 2;
    return ( values.length % 2 == 0 ) ? ( values[middle-1] + values[middle] ) / 2L : values[middle];
  }

  private File artifactDir()
  {
    File root = mContext.getExternalFilesDir( "test-artifacts" );
    assertNotNull( root );
    File dir = new File( root, "breakdown-clip-spike" );
    assertTrue( dir.exists() || dir.mkdirs() );
    return dir;
  }

  private void reportBase64( byte[] png )
  {
    String encoded = android.util.Base64.encodeToString( png, android.util.Base64.NO_WRAP );
    reportStream( "BREAKDOWN_CLIP_B64_BEGIN bytes=" + png.length + "\n" );
    for ( int offset = 0; offset < encoded.length(); offset += 4000 ) {
      int end = Math.min( offset + 4000, encoded.length() );
      reportStream( "BREAKDOWN_CLIP_B64 " + offset + " " + encoded.substring( offset, end ) + "\n" );
    }
    reportStream( "BREAKDOWN_CLIP_B64_END\n" );
  }

  private void reportStream( String message )
  {
    Bundle status = new Bundle();
    status.putString( Instrumentation.REPORT_KEY_STREAMRESULT, message );
    mInstrumentation.sendStatus( 0, status );
  }

  private static byte[] encodeBitmap( Bitmap bitmap ) throws Exception
  {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    assertTrue( bitmap.compress( Bitmap.CompressFormat.PNG, 100, output ) );
    return output.toByteArray();
  }

  private static void saveBytes( byte[] bytes, File file ) throws Exception
  {
    OutputStream output = new FileOutputStream( file );
    try {
      output.write( bytes );
    } finally {
      output.close();
    }
  }

  private static int countForeground( Bitmap bitmap )
  {
    int count = 0;
    for ( int y = 0; y < bitmap.getHeight(); ++y ) {
      for ( int x = 0; x < bitmap.getWidth(); ++x ) {
        if ( bitmap.getPixel( x, y ) != Color.BLACK ) ++count;
      }
    }
    return count;
  }

  private static class Rock
  {
    final Path structure;
    final Path silhouette;
    final RectF bounds = new RectF();
    Path occluders;
    Path visibleInk;

    Rock( Path structure, Path silhouette )
    {
      this.structure = structure;
      this.silhouette = silhouette;
      updateBounds();
    }

    void updateBounds() { silhouette.computeBounds( bounds, true ); }
  }

  private static class Metrics
  {
    final long controlNs;
    final long clippedNs;
    final long clearedNs;
    final long bakedNs;
    Metrics( long control_ns, long clipped_ns, long cleared_ns, long baked_ns ) { controlNs = control_ns; clippedNs = clipped_ns; clearedNs = cleared_ns; bakedNs = baked_ns; }
    double controlMs() { return controlNs / 1.0e6; }
    double clippedMs() { return clippedNs / 1.0e6; }
    double clearedMs() { return clearedNs / 1.0e6; }
    double bakedMs() { return bakedNs / 1.0e6; }
    double clipRatio() { return ( controlNs == 0L ) ? Double.POSITIVE_INFINITY : (double)clippedNs / (double)controlNs; }
    double clearRatio() { return ( controlNs == 0L ) ? Double.POSITIVE_INFINITY : (double)clearedNs / (double)controlNs; }
    double bakedRatio() { return ( controlNs == 0L ) ? Double.POSITIVE_INFINITY : (double)bakedNs / (double)controlNs; }
  }
}
