package com.topodroid.TDX;

import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.types.*;
import com.topodroid.util.TDStatus;
import com.topodroid.num.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(QaJUnit4.class)
@com.topodroid.TDX.qa.TestTier(com.topodroid.TDX.qa.TestTier.Tier.FAST)
@QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DATA})
public class SketchManagementInstrumentedTest {
  @Rule public final QaFixtureRule fixture=new QaFixtureRule();
  private DataHelper data;
  private SketchManagementService service;
  private String name;
  private long sid;
  @Before public void prepare() {
    data=TopoDroidApp.mData;
    name="sketch_management_"+System.nanoTime(); sid=data.setSurvey(name,0);
    TDInstance.sid=sid; TDInstance.survey=name; TDInstance.isSurveyMutable=true; TDPath.setSurveyPaths(name);
    service=new SketchManagementService(data,sid); QaFixtureRule.ensureSymbols();
  }
  private SketchSummary create(String base) throws Exception { return service.create(SketchManagementFixtures.draft(base)); }
  private File drawing(String plot) { return SketchManagementService.drawingFile(name,plot); }
  private void write(PlotInfo plot,String section) throws Exception {
    SketchFileJournal.write(drawing(plot.name),SketchManagementFixtures.drawing(name+"-"+plot.name,plot.type,section,0,0,"A"));
  }
  private long section(String section,int type,String parent) {
    return data.insertPlot(sid,-1,section,type,0,"A","B",0,0,1,45,0,parent,"",0);
  }
  private SketchDraft edit(SketchSummary sketch,String newName,String origin) {
    SketchDraft draft=SketchManagementFixtures.draft(newName); draft.id=sketch.id(); draft.origin=origin; return draft;
  }
  @Test public void pairPersistenceRetainsOrderFilesAndActualIds() throws Exception {
    long plan=data.insertPlot(sid,100,"oldp",PlotType.PLOT_PLAN,0,"A","",0,0,1,0,0,"","",0);
    long profile=data.insertPlot(sid,150,"olds",PlotType.PLOT_EXTENDED,0,"A","",0,0,1,0,0,"","",0);
    SketchSummary old=service.find(plan),second=create("second");
    assertEquals(profile,old.profile.id);
    write(old.plan,name+"-shared"); write(old.profile,name+"-shared");
    byte[] before=SketchFileJournal.read(drawing(old.plan.name));
    service.reorder(Arrays.asList(second.id(),old.id())); service.opened(old.id(),PlotType.PLOT_PLAN);
    service.setDeleted(old.id(),true); assertEquals(-1,service.recent()[0]);
    assertEquals(2,data.selectAllPlots(sid,TDStatus.DELETED).size());
    assertArrayEquals(before,SketchFileJournal.read(drawing(old.plan.name)));
    try { create("OLD"); fail("Deleted names must remain reserved case-insensitively"); } catch(IOException expected) { }
    assertEquals(4,data.selectAllPlots(sid).size());
    // A reopened database and the old recovery entry use the same pair IDs.
    data.closeDatabase(); data=new DataHelper(InstrumentationRegistry.getInstrumentation().getTargetContext()); TopoDroidApp.mData=data;
    service=new SketchManagementService(data,sid); data.undeletePlot(old.id(),sid);
    assertTrue(data.selectAllPlots(sid,TDStatus.DELETED).isEmpty());
    assertEquals(second.id(),service.list().get(0).id()); assertEquals(profile,service.find(plan).profile.id);
    assertArrayEquals(before,SketchFileJournal.read(drawing(old.plan.name)));
    // Roll back the first update when a transaction fails before the second view.
    try { data.writeSketch(sid,() -> { data.updateSketchRow(sid,old.plan,TDStatus.DELETED); throw new IOException("injected"); }); fail(); }
    catch(IOException expected) { }
    assertTrue(data.selectAllPlots(sid,TDStatus.DELETED).isEmpty());
    // Incomplete and file-only pairs must not produce a colliding suggestion.
    data.insertPlot(sid,-1,"1s",PlotType.PLOT_EXTENDED,0,"A","",0,0,1,0,0,"","",0);
    SketchFileJournal.write(drawing("2p"),new byte[]{1});
    assertEquals("3",service.suggestedName());
  }
  @Test public void renamePreservesSectionsBackupsAndRollsBackFileFailure() throws Exception {
    SketchSummary sketch=create("old"),retained=create("retained");
    long privateId=section("xs-A-oldp",(int)PlotType.PLOT_X_SECTION,"oldp");
    long lineId=section("xx0",(int)PlotType.PLOT_SECTION,"oldp");
    long sharedId=section("xs-B",(int)PlotType.PLOT_X_SECTION,"");
    SectionReferenceSelection choices=new SectionReferenceSelection(); choices.setStation("C",true);
    assertTrue(SectionReferenceRepository.save(data,sid,privateId,choices));
    write(sketch.plan,name+"-xs-A-oldp"); write(sketch.profile,name+"-xs-B"); write(retained.plan,name+"-xs-A-oldp");
    PlotInfo privateSection=data.getPlotInfo(sid,privateId); write(privateSection,name+"-xs-B");
    byte[] before=SketchFileJournal.read(drawing("oldp"));
    File backup=new File(drawing("oldp").getPath()+".bck2"); SketchFileJournal.write(backup,before);
    File photos=new File(TDPath.getSurveyDir(name),"photo");
    File photo=new File(photos,"xs-A-oldp.jpg"); SketchFileJournal.write(photo,new byte[]{1,2,3});
    service.setDeleted(retained.id(),true);
    service.fileHook=index -> { throw new IOException("injected file failure"); };
    try { service.edit(edit(sketch,"new","A")); fail(); } catch(IOException expected) { }
    service.fileHook=null;
    assertNotNull(data.getPlotInfo(sid,"oldp")); assertNull(data.getPlotInfo(sid,"newp"));
    assertArrayEquals(before,SketchFileJournal.read(drawing("oldp"))); assertFalse(drawing("newp").exists());
    service.edit(edit(sketch,"new","A"));
    byte[] expected=SketchManagementFixtures.drawing(name+"-newp",1,name+"-xs-A-newp",0,0,"A");
    assertArrayEquals(expected,SketchFileJournal.read(drawing("newp")));
    assertArrayEquals(expected,SketchFileJournal.read(new File(drawing("newp")+".bck2")));
    assertArrayEquals(SketchManagementFixtures.drawing(name+"-retainedp",1,name+"-xs-A-newp",0,0,"A"),SketchFileJournal.read(drawing("retainedp")));
    assertEquals(privateId,data.getPlotInfo(sid,"xs-A-newp").id); assertTrue(drawing("xs-A-newp").exists());
    assertEquals("newp",data.getPlotInfo(sid,lineId).hide); assertEquals("",data.getPlotInfo(sid,sharedId).hide);
    assertNotNull(data.getValue(SectionReferenceRepository.key(sid,privateId)));
    assertFalse(backup.exists()); assertFalse(drawing("oldp").exists());
    assertFalse(photo.exists()); assertArrayEquals(new byte[]{1,2,3},SketchFileJournal.read(new File(photos,"xs-A-newp.jpg")));
    // Reopen through the production point reader, not just a byte comparison.
    try (DataInputStream input=new DataInputStream(new FileInputStream(drawing("newp")))) {
      int version=DrawingIO.skipTdrHeader(input); assertEquals('N',input.read()); input.readInt();
      assertEquals('P',input.read()); DrawingPointPath ordinary=DrawingPointPath.loadDataStream(version,input,0,0);
      assertNotNull(ordinary); assertEquals(10,ordinary.cx,0); assertEquals(20,ordinary.cy,0);
      assertEquals('P',input.read()); DrawingPointPath placement=DrawingPointPath.loadDataStream(version,input,0,0);
      assertNotNull(placement); assertTrue(placement.getOption("-scrap").contains("xs-A-newp"));
    }
  }
  @Test public void originChangesPreserveStrokesAndExistingSectionAdjustment() throws Exception {
    DBlock leg=new DBlock("A","B",10,90,15,0,1,BlockType.MAIN_LEG,0);
    assertTrue(data.insertDBlockShot(sid,leg)>=0);
    TDNum number=new TDNum(data.selectAllShots(sid,TDStatus.NORMAL),"B",0,null); NumStation old=number.getStation("A"); assertNotNull(old);
    for (int type : new int[]{(int)PlotType.PLOT_EXTENDED,(int)PlotType.PLOT_PROJECTED}) {
      SketchDraft create=SketchManagementFixtures.draft("type"+type); create.projected=type==PlotType.PLOT_PROJECTED; create.azimuth=45;
      SketchSummary sketch=service.create(create);
      write(sketch.plan,name+"-xs-A"); write(sketch.profile,name+"-xs-A");
      service.edit(edit(sketch,sketch.name,"B"));
      assertEquals("B",service.find(sketch.id()).origin());
      assertArrayEquals(SketchManagementFixtures.drawing(name+"-"+sketch.plan.name,1,name+"-xs-A",(float)(20*old.e),(float)(20*old.s),"B"),SketchFileJournal.read(drawing(sketch.plan.name)));
      assertArrayEquals(SketchManagementFixtures.drawing(name+"-"+sketch.profile.name,type,name+"-xs-A",(float)(20*old.h),(float)(20*old.v),"B"),SketchFileJournal.read(drawing(sketch.profile.name)));
    }
  }
}
