package com.topodroid.TDX;

import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.TDX.qa.TestTier;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Negative controls use the approved fixture, not a synthetic comparator-only image. */
@RunWith(com.topodroid.TDX.QaJUnit4.class)
@TestTier(TestTier.Tier.FAST)
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.RUNNER, com.topodroid.qa.TestFeatures.Feature.SYMBOLS})
public class VisualComparatorInstrumentedTest {
  @Rule public final QaFixtureRule fixture=new QaFixtureRule();
  private Bitmap approved;
  @Before public void load() throws Exception {
    try (java.io.InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(VisualTestSupport.GOLDEN_PROFILE_DIR+"/sketch_screen.png")) {
      approved=BitmapFactory.decodeStream(stream);
    }
    assertNotNull(approved);
  }
  @After public void close() {if(approved!=null)approved.recycle();}
  private void reject(Bitmap changed,Map<String,Rect> regions) {
    boolean rejected=false;
    try {QaBitmapChecks.assertRegions(approved,changed,regions);} catch(AssertionError error){rejected=true;}
    finally{changed.recycle();}
    assertTrue("Corrupted fixture must fail visual comparison",rejected);
  }
  private Bitmap erase(Collection<Rect> rectangles) {
    Bitmap changed=approved.copy(Bitmap.Config.ARGB_8888,true);
    Canvas canvas=new Canvas(changed);Paint paint=new Paint();paint.setColor(Color.BLACK);
    for(Rect rect:rectangles)canvas.drawRect(rect,paint);
    return changed;
  }
  private Bitmap eraseStrokeInk(Collection<Rect> rectangles) {
    Bitmap changed=approved.copy(Bitmap.Config.ARGB_8888,true);
    for(Rect region:rectangles)for(int y=region.top;y<region.bottom;y++)for(int x=region.left;x<region.right;x++) {
      int pixel=approved.getPixel(x,y);
      if(Color.red(pixel)>200 && Color.green(pixel)>200 && Color.blue(pixel)>200)changed.setPixel(x,y,Color.BLACK);
    }
    return changed;
  }
  @Test public void identicalFixture_passes() {QaBitmapChecks.assertRegions(approved,approved,QaBitmapChecks.canonicalStrokeRegions());}
  @Test public void oneMissingStroke_isRejected() throws Exception {
    Map<String,Rect> r=QaBitmapChecks.canonicalStrokeRegions();reject(eraseStrokeInk(Collections.singleton(r.get("standard-stroke"))),r);
    withCanonicalPng(() -> {Map<String,Rect> png=QaBitmapChecks.canonicalPngStrokeRegions();reject(eraseStrokeInk(Collections.singleton(png.get("thin-stroke"))),png);});
  }
  @Test public void allMissingStrokes_areRejected() throws Exception {
    Map<String,Rect> r=QaBitmapChecks.canonicalStrokeRegions();reject(eraseStrokeInk(r.values()),r);
    withCanonicalPng(() -> {Map<String,Rect> png=QaBitmapChecks.canonicalPngStrokeRegions();reject(eraseStrokeInk(png.values()),png);});
  }
  @Test public void wrongWeight_isRejected() throws Exception {
    rejectWrongWeight(QaBitmapChecks.canonicalStrokeRegions());
    withCanonicalPng(() -> rejectWrongWeight(QaBitmapChecks.canonicalPngStrokeRegions()));
  }
  private void rejectWrongWeight(Map<String,Rect> r) {
    Bitmap changed=approved.copy(Bitmap.Config.ARGB_8888,true);
    Canvas canvas=new Canvas(changed);Paint paint=new Paint();paint.setColor(Color.WHITE);paint.setStrokeWidth(18);
    Rect thin=r.get("thin-stroke");
    for(int y=thin.top;y<thin.bottom;y++)for(int x=thin.left;x<thin.right;x++) {
      int pixel=approved.getPixel(x,y);
      if(Color.red(pixel)>200 && Color.green(pixel)>200 && Color.blue(pixel)>200)canvas.drawCircle(x,y,6,paint);
    }
    reject(changed,r);
  }
  private void withCanonicalPng(Runnable check) throws Exception {
    Bitmap screen=approved;
    try(java.io.InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(VisualTestSupport.GOLDEN_PROFILE_DIR+"/export_png.png")) {
      approved=BitmapFactory.decodeStream(stream);assertNotNull(approved);check.run();
    } finally {if(approved!=null && approved!=screen)approved.recycle();approved=screen;}
  }
  @Test public void missingToolbarRow_isRejected() {Rect row=new Rect(0,1200,2560,1330);Map<String,Rect> r=Collections.singletonMap("toolbar-row",row);reject(erase(r.values()),r);}
  @Test public void wrongSelectionHighlight_isRejected() {
    Rect row=new Rect(0,1368,2276,1452);Bitmap changed=approved.copy(Bitmap.Config.ARGB_8888,true);
    int selected=approved.getPixel(100,1400);
    assertTrue("Approved fixture must select Fine",Color.green(selected)>100 && Color.blue(selected)>100);
    for(int y=row.top;y<row.bottom;y++)for(int x=0;x<1138;x++) {
      int pixel=approved.getPixel(x,y);
      if(x<569 && pixel==selected)changed.setPixel(x,y,Color.BLACK);
      if(x>=569 && pixel==Color.BLACK)changed.setPixel(x,y,selected);
    }
    reject(changed,Collections.singletonMap("Fine-to-Smooth-selection",row));
  }
}
