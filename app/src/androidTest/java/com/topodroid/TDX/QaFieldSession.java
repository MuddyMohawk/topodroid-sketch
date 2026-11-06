package com.topodroid.TDX;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import android.app.Activity;
import android.graphics.*;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.widget.ListView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import androidx.test.uiautomator.UiDevice;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.LegType;
import com.topodroid.util.TDStatus;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/** One real field session; assertions concern data and exported pixels, not UI cosmetics. */
final class QaFieldSession {
  private static final String COMMENT = "Decimal, \"checked\" café";
  private final VisualTestSupport support;
  private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
  QaFieldSession(VisualTestSupport support) { this.support=support; }
  private interface Work { void run() throws Exception; }
  private static void main(Work work) {
    final Throwable[] failure={null};
    InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {try {work.run();}catch(Throwable t){failure[0]=t;}});
    if(failure[0]!=null)throw new AssertionError("Field-session operation failed",failure[0]);
  }
  private static DrawingWindow window() {assertNotNull(TopoDroidApp.mDrawingWindow);return TopoDroidApp.mDrawingWindow;}
  private static DrawingSurface surface() {return (DrawingSurface)window().findViewById(R.id.drawingSurface);}
  private static DrawingCommandManager manager() {return surface().getManager(window().getPlotType());}
  private static void enterField(int id,String value) {
    onView(withId(id)).inRoot(isDialog()).perform(replaceText(value)).check(matches(withText(value)));
  }
  private static void saveDialog(int id) {
    closeSoftKeyboard();
    // Resolve the live dialog view after the IME relayout. Cached accessibility
    // coordinates can otherwise hit a different field and reopen the keyboard.
    onView(withId(id)).inRoot(isDialog()).perform(click());
  }

  void enterShotsThroughUi() {
    main(() -> {TDSetting.mBacksightInput=true;TDSetting.mEditableShots=true;});
    support.openManualShotDialogFromShotToolbar();
    int[] ids={R.id.shot_from,R.id.shot_to,R.id.shot_distance,R.id.shot_bearing,R.id.shot_clino,R.id.shot_backdistance,R.id.shot_backbearing,R.id.shot_backclino};
    String[] values={"1","2","12.34","95.25","-3.50","12.36","275.20","3.45"};
    for(int i=0;i<ids.length;i++)enterField(ids[i],values[i]);
    saveDialog(R.id.button_ok_shot_name);
    support.waitForShotWindow();
    support.reportStep("foresight and backsight entered through UI");
    support.openManualShotDialogFromShotToolbar();
    String[] next={"2","3","6.78","5.50","2.25"};
    for(int i=0;i<next.length;i++)enterField(ids[i],next[i]);
    saveDialog(R.id.button_ok_shot_name);
    support.waitForShotWindow();
    assertShots("");
    support.reportStep("all entered measurements verified before editing the comment");
    main(() -> {
      ShotWindow shots=null;
      for(Activity activity:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
        if(activity instanceof ShotWindow)shots=(ShotWindow)activity;
      assertNotNull("Shot editor must be opened from the actual shot list",shots);
      ListView list=shots.findViewById(R.id.list);
      DBlockAdapter adapter=(DBlockAdapter)list.getAdapter();
      boolean opened=false;
      for(int i=0;i<adapter.getCount();i++) {
        DBlock block=adapter.get(i);
        if("1".equals(block.mFrom)&&"2".equals(block.mTo)) {
          shots.itemClick(adapter.getView(i,null,list),i);opened=true;break;
        }
      }
      assertTrue("Entered foresight must exist in the real list",opened);
    });
    enterField(R.id.shot_comment,COMMENT);
    saveDialog(R.id.btn_ok);
    support.waitForShotWindow();
    support.reportStep("comment saved through the active shot editor");
  }

  void assertShots() {
    assertShots(COMMENT);
  }
  private void assertShots(String foreComment) {
    List<DBlock> shots=TopoDroidApp.mData.selectAllShots(TDInstance.sid,TDStatus.NORMAL);
    assertEquals("Exactly the three entered measurements",3,shots.size());
    checkShot(shots,"1","2",12.34*.3048,95.25,-3.50,false,foreComment);
    checkShot(shots,"2","1",12.36*.3048,275.20,3.45,true,"");
    checkShot(shots,"2","3",6.78*.3048,5.50,2.25,false,"");
  }
  private static void checkShot(List<DBlock> shots,String from,String to,double metres,double bearing,double clino,boolean back,String comment) {
    DBlock found=null;
    for(DBlock shot:shots)if(from.equals(shot.mFrom)&&to.equals(shot.mTo)){assertNull("Duplicate shot "+from+"-"+to,found);found=shot;}
    assertNotNull("Missing shot "+from+"-"+to,found);
    // Independent international-foot conversion, not the production conversion helper.
    assertEquals("Length "+from+"-"+to,metres,found.mLength,.00001);
    // DBlock stores 32-bit angles. Permit one representable rounding step,
    // including at large bearings, while rejecting decimal/display rounding.
    assertEquals("Bearing",bearing,found.mBearing,Math.ulp((float)bearing));
    assertEquals("Clino",clino,found.mClino,Math.ulp((float)clino));
    assertEquals("Leg type",LegType.NORMAL,found.getLegType());
    assertEquals("Instrument direction flag",back,found.isBackshot());
    assertEquals("Comment",comment,found.mComment);
  }

  private Rect canvas() {
    Rect bounds=new Rect();main(() -> assertTrue(surface().getGlobalVisibleRect(bounds)));return bounds;
  }
  private static Point point(Rect r,double x,double y){return new Point(r.left+(int)(r.width()*x),r.top+(int)(r.height()*y));}
  void drawObjectsThroughInput() {
    support.enterDrawMode();support.tapPresetButton(1);support.tapStyleButton(3);
    support.clickRecentLineByThName(SymbolLibrary.USER);
    support.drawCurveStrokeNormalized(.12,.23,.43,.26,.025,16,5);
    awaitObjects(1);
    int ordinary=BrushManager.getPointIndexByThName("stalactite");assertTrue("Packaged ordinary point",ordinary>=0);
    main(() -> window().pointSelected(ordinary,true));
    support.tapStyleButton(3);
    Point at=point(canvas(),.66,.29);assertTrue(device.click(at.x,at.y));awaitObjects(2);
    int solid=BrushManager.getAreaIndexByThName(SymbolLibrary.USER);assertTrue(solid>=0);
    assertNull("Smoke fixture must be a solid fill",BrushManager.getAreaLinePattern(solid));
    main(() -> window().areaSelected(solid,true));support.tapStyleButton(3);
    Rect bounds=canvas();List<Point> polygon=new ArrayList<>();
    double[][] corners={{.15,.55},{.43,.55},{.43,.72},{.15,.72},{.15,.55}};
    for(int edge=0;edge<4;edge++)for(int i=0;i<8;i++) {
      double t=i/8.0;polygon.add(point(bounds,corners[edge][0]*(1-t)+corners[edge+1][0]*t,corners[edge][1]*(1-t)+corners[edge+1][1]*t));
    }
    polygon.add(point(bounds,.15,.55));assertTrue(device.swipe(polygon.toArray(new Point[0]),4));awaitObjects(3);
    support.reportStep("line, ordinary point and solid area committed through input");
  }
  private static List<DrawingPath> paths() {
    List<DrawingPath> result=new ArrayList<>();
    for(Scrap scrap:manager().getScraps())synchronized(TDPath.mCommandsLock) {
      for(ICanvasCommand command:scrap.mCurrentStack)if(command instanceof DrawingPath)result.add((DrawingPath)command);
    }
    return result;
  }
  private static void awaitObjects(int count) {
    long deadline=SystemClock.uptimeMillis()+5000;int[] actual={-1};
    do {main(() -> actual[0]=paths().size());if(actual[0]==count)return;SystemClock.sleep(50);}while(SystemClock.uptimeMillis()<deadline);
    assertEquals("Dispatched gesture must commit an object",count,actual[0]);
  }
  static final class ObjectState {
    String kind,symbol;final List<Double> values=new ArrayList<>();RectF bounds;
    void add(double... numbers){for(double number:numbers)values.add(number);}
  }
  static final class Snapshot {final List<ObjectState> objects=new ArrayList<>();final RectF exportBounds=new RectF(-1,-1,1,1);}
  Snapshot snapshot() {
    Snapshot snapshot=new Snapshot();
    main(() -> {
      DrawingStationName station=manager().getStation("1");assertNotNull("Station anchor",station);
      for(DrawingPath path:paths()) {
        ObjectState object=new ObjectState();SketchBrushStyle style;
        if(path instanceof DrawingLinePath) {object.kind="line";object.symbol=((DrawingLinePath)path).getThName();style=((DrawingLinePath)path).getSketchBrushStyle();}
        else if(path instanceof DrawingAreaPath) {object.kind="area";object.symbol=((DrawingAreaPath)path).getThName();style=((DrawingAreaPath)path).getSketchBrushStyle();object.add(((DrawingAreaPath)path).mOrientation);}
        else {assertTrue(path instanceof DrawingPointPath);DrawingPointPath p=(DrawingPointPath)path;object.kind="point";object.symbol=p.getThName();style=p.getSketchBrushStyle();object.add(p.cx-station.cx,p.cy-station.cy,p.mScale,p.mOrientation);}
        assertNotNull("Persist explicit brush metadata",style);
        assertEquals("Thick weight",5,style.weightOr(-1),.0001);
        object.add(style.hasWeight()?1:0,style.weightOr(-1),style.hasPointScale()?1:0,style.pointScaleOr(-1),style.hasOpacity()?1:0,style.opacityOr(-1),style.hasColor()?1:0,style.colorOr(-1));
        if(path instanceof DrawingPointLinePath)for(LinePoint p=((DrawingPointLinePath)path).mFirst;p!=null;p=p.mNext) {
          object.add(p.x-station.cx,p.y-station.cy,p.has_cp?1:0);
          if(p.has_cp)object.add(p.x1-station.cx,p.y1-station.cy,p.x2-station.cx,p.y2-station.cy);
        }
        object.bounds=new RectF();path.computeBounds(object.bounds,true);snapshot.exportBounds.union(object.bounds);snapshot.objects.add(object);
      }
      // Export always includes the north-line bounds, even when its overlay is disabled.
      Field field=DrawingCommandManager.class.getDeclaredField("mNorthLine");field.setAccessible(true);
      DrawingPath north=(DrawingPath)field.get(manager());
      if(north!=null){RectF b=new RectF();north.computeBounds(b,true);snapshot.exportBounds.union(b);}
    });
    assertEquals(3,snapshot.objects.size());
    assertEquals("line",snapshot.objects.get(0).kind);assertEquals("point",snapshot.objects.get(1).kind);assertEquals("area",snapshot.objects.get(2).kind);
    for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)
      assertFalse("Object export regions must be disjoint so another object cannot supply missing ink",RectF.intersects(snapshot.objects.get(i).bounds,snapshot.objects.get(j).bounds));
    return snapshot;
  }
  void assertSameSnapshot(Snapshot expected,Snapshot actual) {
    for(int i=0;i<expected.objects.size();i++) {
      ObjectState a=expected.objects.get(i),b=actual.objects.get(i);
      assertEquals("Object type",a.kind,b.kind);assertEquals("Symbol",a.symbol,b.symbol);assertEquals("Geometry/style field count",a.values.size(),b.values.size());
      for(int j=0;j<a.values.size();j++)assertEquals(a.kind+" relative geometry/style field "+j,a.values.get(j),b.values.get(j),.0001);
    }
  }
  void coldReload(String survey,String plot) throws Exception {
    DrawingCommandManager[] previous={null};main(() -> previous[0]=manager());
    support.pressBackToShotWindow();
    CountDownLatch saved=new CountDownLatch(1);
    // SavePlotFileTask uses the serial executor. A barrier observes completion without forcing a save.
    main(() -> new AsyncTask<Void,Void,Void>() {
      protected Void doInBackground(Void... ignored){return null;}
      protected void onPostExecute(Void ignored){saved.countDown();}
    }.execute());
    assertTrue("Navigation save must complete within ten seconds",saved.await(10,TimeUnit.SECONDS));
    QaFixtureRule.closeActivities();
    main(() -> {
      TopoDroidApp.mData.closeDatabase();TopoDroidApp.mData=null;
      ((TopoDroidApp)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext()).clearSurveyReferences();
      TopoDroidApp.mDrawingWindow=null;TDInstance.sid=-1;TDInstance.survey=null;DrawingSurface.clearManagersCache();
      TopoDroidApp.mData=new DataHelper(InstrumentationRegistry.getInstrumentation().getTargetContext());
    });
    support.relaunchMainWindow();support.openSurveyFromMainList(survey);support.openExistingPlanPlot(plot);
    main(() -> assertNotSame("Reload must not reuse the old drawing manager",previous[0],manager()));
    support.reportStep("navigation save and cold plot reload completed");
  }
  void assertPngObjects(Snapshot expected,File file) {
    Bitmap image=BitmapFactory.decodeFile(file.getAbsolutePath());assertNotNull("PNG decodes",image);
    try {
      RectF all=expected.exportBounds;
      assertEquals("PNG width",(int)Math.ceil((all.right+20-(all.left-20))*10),image.getWidth());
      assertEquals("PNG height",(int)Math.ceil((all.bottom+20-(all.top-20))*10),image.getHeight());
      assertEquals("Transparent background",0,Color.alpha(image.getPixel(0,0)));
      for(ObjectState object:expected.objects) {
        RectF region=new RectF(object.bounds);
        if(object.kind.equals("area"))region.inset(region.width()*.3f,region.height()*.3f);
        int left=Math.max(0,(int)((region.left-all.left+20)*10)-2),top=Math.max(0,(int)((region.top-all.top+20)*10)-2);
        int right=Math.min(image.getWidth(),(int)((region.right-all.left+20)*10)+3),bottom=Math.min(image.getHeight(),(int)((region.bottom-all.top+20)*10)+3);
        assertTrue("Nonempty expected "+object.kind+" region",right>left&&bottom>top);
        int ink=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++) {
          int color=image.getPixel(x,y);
          if(Color.alpha(color)>20 && Math.max(Color.red(color),Math.max(Color.green(color),Color.blue(color)))>50)ink++;
        }
        if(object.kind.equals("area"))assertTrue("PNG must contain interior fill, not just the outline: "+ink+"/"+((right-left)*(bottom-top)),ink>.8*(right-left)*(bottom-top));
        else assertTrue("PNG ink at expected "+object.kind+" position: "+ink,ink>=20);
      }
    } finally {image.recycle();}
  }
}
