package com.topodroid.TDX;

import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.DATA})
public class SketchManagementTest {
  @Rule public TemporaryFolder temporary=new TemporaryFolder();
  private List<SketchSummary> pairs() {
    return SketchSummary.pair(Arrays.asList(SketchManagementFixtures.plot(2,"onep",1),SketchManagementFixtures.plot(7,"ones",2),
      SketchManagementFixtures.plot(9,"twop",1),SketchManagementFixtures.plot(20,"twos",8)),Collections.emptySet());
  }
  @Test public void pairingUsesNamesAndTypesRatherThanAdjacency() {
    List<SketchSummary> pairs=pairs(); assertEquals(2,pairs.size()); assertEquals(7,pairs.get(0).profile.id);
    assertTrue(pairs.get(0).available()); assertTrue(pairs.get(1).available());
    assertFalse(SketchSummary.pair(Collections.singletonList(SketchManagementFixtures.plot(2,"onep",1)),Collections.emptySet()).get(0).available());
    assertFalse(SketchSummary.pair(Arrays.asList(SketchManagementFixtures.plot(2,"onep",1),SketchManagementFixtures.plot(7,"ones",2)),Collections.singleton(2L)).get(0).available());
    assertFalse(SketchSummary.pair(Arrays.asList(SketchManagementFixtures.plot(2,"onep",1),SketchManagementFixtures.plot(3,"onep",1),
      SketchManagementFixtures.plot(7,"ones",2)),Collections.emptySet()).get(0).available());
  }
  @Test public void editingDraftDoesNotMutateSavedIdentityOrProjection() {
    SketchSummary saved=pairs().get(1); saved.profile.azimuth=75; saved.profile.clino=10;
    SketchDraft draft=SketchDraft.editing(saved);
    assertFalse(draft.hasEdits(saved.name,saved.origin()));
    draft.name="renamed";
    assertTrue(draft.hasEdits(saved.name,saved.origin())); assertFalse(draft.changesOrigin(saved.origin()));
    draft.origin="B"; assertTrue(draft.changesOrigin(saved.origin()));
    assertEquals("twop",saved.plan.name); assertEquals("A",saved.origin());
    assertTrue(draft.projected); assertEquals(75,draft.azimuth); assertEquals(10,draft.oblique);
    // Cancel discards that value object; reopening derives a clean draft from saved data.
    assertFalse(SketchDraft.editing(saved).hasEdits(saved.name,saved.origin()));
  }
  @Test public void orderRetainsIdentityAndAppendsUnlistedSketches() {
    assertEquals(Arrays.asList(9L,2L),SketchSummary.order("1:9,999,9,",pairs()));
    assertEquals(Arrays.asList(2L,9L),SketchSummary.order("1:9,broken",pairs()));
    List<SketchSummary> renamed=pairs(); renamed.get(0).plan.name="renamedp";
    assertEquals(Arrays.asList(9L,2L),SketchSummary.order(SketchSummary.encodeOrder(Arrays.asList(9L,2L)),renamed));
  }
  @Test public void capturedSaveRejectsRenamedDeletedAndDifferentSurveyRows() {
    PlotInfo plot=SketchManagementFixtures.plot(2,"onep",1); SketchSaveIdentity saved=new SketchSaveIdentity(plot);
    assertTrue(saved.matches(plot,0)); assertFalse(saved.matches(plot,1));
    plot.name="twop"; assertFalse(saved.matches(plot,0)); plot.name="onep"; plot.surveyId++;
    assertFalse(saved.matches(plot,0)); assertFalse(saved.matches(null,0));
  }
  @Test public void rewritePreservesAllUnrelatedBytesAndRenamesOnlyReferences() throws Exception {
    byte[] before=SketchManagementFixtures.drawing("survey-oldp",1,"survey-xs-A-oldp",0,0,"A");
    Map<String,String> names=new HashMap<>(); names.put("survey-oldp","survey-newp");
    names.put("survey-xs-A-oldp","survey-xs-A-newp"); names.put("xs-A-oldp","xs-A-newp");
    byte[] after=SketchDrawingRewrite.rewrite(before,names,12,-8,"B");
    assertArrayEquals(SketchManagementFixtures.drawing("survey-newp",1,"survey-xs-A-newp",12,-8,"B"),after);
    assertArrayEquals(before,SketchDrawingRewrite.rewrite(before,Collections.emptyMap(),0,0));
  }
  @Test public void corruptOrUnknownDrawingsAreRejected() throws Exception {
    byte[] drawing=SketchManagementFixtures.drawing("survey-oldp",1,"survey-xs-A-oldp",0,0,"A");
    for (byte[] bad : Arrays.asList(Arrays.copyOf(drawing,drawing.length-1),new byte[]{'V',0,0,0,1,'E'},new byte[]{'V',0,9,55,(byte)124,'Q'})) {
      try { SketchDrawingRewrite.rewrite(bad,Collections.emptyMap(),0,0); fail("Accepted invalid drawing"); }
      catch(IOException expected) { }
    }
  }
  @Test public void interruptedReplacementRollsBackOnReopen() throws Exception {
    File root=temporary.newFolder(); File old=new File(root,"old.tdr"),target=new File(root,"new.tdr"),dir=new File(root,"journal");
    SketchFileJournal.write(old,new byte[]{1,2,3}); SketchFileJournal journal=new SketchFileJournal(dir);
    journal.stage(old,target,new byte[]{4,5}); journal.seal();
    try { journal.apply(index -> { throw new IOException("interrupted"); }); fail(); } catch(IOException expected) { }
    new SketchFileJournal(dir).recover(null);
    assertArrayEquals(new byte[]{1,2,3},SketchFileJournal.read(old)); assertFalse(target.exists()); assertFalse(dir.exists());
  }
  @Test public void committedReplacementSurvivesRecoveryCleanup() throws Exception {
    File root=temporary.newFolder(); File old=new File(root,"old.tdr"),target=new File(root,"new.tdr"),dir=new File(root,"journal");
    SketchFileJournal.write(old,new byte[]{1}); SketchFileJournal journal=new SketchFileJournal(dir);
    journal.stage(old,target,new byte[]{2}); journal.seal(); journal.apply(null);
    new SketchFileJournal(dir).recover(journal.token);
    assertArrayEquals(new byte[]{2},SketchFileJournal.read(target)); assertFalse(old.exists()); assertFalse(dir.exists());
  }
  @Test public void destinationCollisionNeverChangesOriginals() throws Exception {
    File root=temporary.newFolder(),old=new File(root,"old.tdr"),target=new File(root,"new.tdr"),dir=new File(root,"journal");
    SketchFileJournal.write(old,new byte[]{1}); SketchFileJournal.write(target,new byte[]{2});
    SketchFileJournal journal=new SketchFileJournal(dir);
    try { journal.stage(old,target,new byte[]{3}); fail(); } catch(IOException expected) { }
    journal.recover(null);
    assertArrayEquals(new byte[]{1},SketchFileJournal.read(old)); assertArrayEquals(new byte[]{2},SketchFileJournal.read(target));
  }
}
