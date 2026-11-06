package com.topodroid.TDX;

import android.graphics.Bitmap;
import android.graphics.Rect;
import static org.junit.Assert.*;
import java.util.*;

/** Pixel comparisons are local so large empty backgrounds cannot hide missing ink. */
final class QaBitmapChecks {
  static final int CHANNEL_TOLERANCE = 8;
  static final double MAX_CHANGED_RATIO = .005;
  static double difference(Bitmap expected, Bitmap actual, Rect region) {
    assertEquals("Image width",expected.getWidth(),actual.getWidth());
    assertEquals("Image height",expected.getHeight(),actual.getHeight());
    assertTrue("Empty/out-of-bounds comparison region " + region,
      !region.isEmpty() && region.left>=0 && region.top>=0 && region.right<=actual.getWidth() && region.bottom<=actual.getHeight());
    long changed=0;
    int[] e=new int[region.width()], a=new int[region.width()];
    for (int y=region.top;y<region.bottom;y++) {
      expected.getPixels(e,0,e.length,region.left,y,e.length,1);
      actual.getPixels(a,0,a.length,region.left,y,a.length,1);
      for (int x=0;x<e.length;x++) {
        for (int shift=0;shift<=24;shift+=8) if (Math.abs(((e[x]>>>shift)&255)-((a[x]>>>shift)&255))>CHANNEL_TOLERANCE) {changed++;break;}
      }
    }
    return changed/(double)((long)region.width()*region.height());
  }
  static void assertRegions(Bitmap expected, Bitmap actual, Map<String,Rect> regions) {
    List<String> errors=new ArrayList<>();
    for (Map.Entry<String,Rect> entry : regions.entrySet()) {
      double difference=difference(expected,actual,entry.getValue());
      if (difference>MAX_CHANGED_RATIO) errors.add(entry.getKey()+" changed="+difference+" region="+entry.getValue());
    }
    assertTrue("Visual mismatch (channel tolerance 8; each region <=0.005): " + errors,errors.isEmpty());
  }
  static int foreground(Bitmap image,Rect region) {
    int count=0;
    for (int y=region.top;y<region.bottom;y++) for(int x=region.left;x<region.right;x++) {
      int c=image.getPixel(x,y);
      if (((c>>>16)&255)>80 || ((c>>>8)&255)>80 || (c&255)>80) count++;
    }
    return count;
  }
  static Map<String,Rect> canonicalStrokeRegions() {
    Map<String,Rect> regions=new LinkedHashMap<>();
    regions.put("thin-stroke",new Rect(190,340,1250,440));
    regions.put("standard-stroke",new Rect(190,600,1250,710));
    regions.put("thick-stroke",new Rect(190,910,1250,1020));
    return regions;
  }
  static Map<String,Rect> canonicalPngStrokeRegions() {
    Map<String,Rect> regions=new LinkedHashMap<>();
    regions.put("thin-stroke",new Rect(180,850,1285,925));
    regions.put("standard-stroke",new Rect(180,1100,1285,1190));
    regions.put("thick-stroke",new Rect(180,1435,1285,1530));
    return regions;
  }
  static void assertApprovedCrop(Bitmap image,Rect region,String asset) throws Exception {
    try(java.io.InputStream input=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(VisualTestSupport.GOLDEN_PROFILE_DIR+"/"+asset)) {
      Bitmap approved=android.graphics.BitmapFactory.decodeStream(input);
      Bitmap crop=Bitmap.createBitmap(image,region.left,region.top,region.width(),region.height());
      try {assertRegions(approved,crop,Collections.singletonMap(asset,new Rect(0,0,crop.getWidth(),crop.getHeight())));}
      finally {crop.recycle();approved.recycle();}
    }
  }
}
