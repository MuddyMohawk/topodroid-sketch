package com.topodroid.TDX;

import static org.junit.Assert.*;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.topodroid.TDX.qa.TestTier;
import com.topodroid.inport.ParserShot;
import com.topodroid.inport.QaCompassParser;
import com.topodroid.num.NumStation;
import com.topodroid.num.TDNum;
import com.topodroid.prefs.TDSetting;
import com.topodroid.qa.TestFeatures;
import com.topodroid.util.TDStatus;
import com.topodroid.util.TDUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(QaJUnit4.class)
@TestTier(TestTier.Tier.FAST)
@QaFixtureRule.Database
@TestFeatures({TestFeatures.Feature.EXCHANGE, TestFeatures.Feature.DATA})
public class SurveyReadingAccuracyInstrumentedTest
{
  @Rule public final QaFixtureRule fixture = new QaFixtureRule();
  private DataHelper data;
  private long sid;
  private SurveyInfo info;
  private static final String NAME = "field_accuracy";

  @Before public void createSurvey() {
    data = TopoDroidApp.mData;
    sid = data.setSurvey(NAME, SurveyInfo.DATAMODE_NORMAL);
    info = data.selectSurveyInfo(sid);
    TDSetting.mUnitLength = TDUtil.M2FT;
    TDSetting.mUnitAngle = 1;
    TDSetting.mCsvSeparator = ',';
    TDSetting.mLRUDcount = false;
    // The field DAT projects every eligible splay onto both the vertical and horizontal axes.
    TDSetting.mLRUDhorizontal = 90;
    TDSetting.mLRUDvertical = 0;
    TDSetting.mOrthogonalLRUD = TDSetting.mSwapLR = false;
  }

  private List<String[]> fixtureRows(String name) throws Exception {
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(
        InstrumentationRegistry.getInstrumentation().getContext().getAssets()
            .open("fixtures/survey_readings/" + name + ".tsv"), "UTF-8"))) {
      r.readLine();
      String line;
      while ((line = r.readLine()) != null) rows.add(line.split("\t", -1));
    }
    return rows;
  }

  // Use stored rows, including historically deleted heads with active repeats.
  private void seed(List<String[]> rows) {
    try (SQLiteDatabase db = SQLiteDatabase.openDatabase(TDPath.getDatabase(), null, SQLiteDatabase.OPEN_READWRITE)) {
      db.beginTransaction();
      try {
        for (String[] r : rows) {
          db.execSQL("INSERT INTO shots (surveyId,id,fStation,tStation,distance,bearing,clino,leg,status,extend,flag,address,comment) "
              + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", new Object[]{sid,r[0],r[1],r[2],r[3],r[4],r[5],r[6],r[7],r[8],r[9],device(r),""});
        }
        db.setTransactionSuccessful();
      } finally { db.endTransaction(); }
    }
  }

  private static String device(String[] row) { return "02:00:00:00:00:0" + row[10]; }

  private String dat(boolean splays) {
    TDSetting.mCompassSplays = splays;
    StringWriter out = new StringWriter();
    assertEquals(1, TDExporter.exportSurveyAsDat(new BufferedWriter(out), sid, data, info, NAME, ""));
    return out.toString();
  }

  private List<String[]> csv(boolean raw) {
    StringWriter out = new StringWriter();
    BufferedWriter writer = new BufferedWriter(out);
    assertEquals(1, raw ? TDExporter.exportSurveyAsRawCsv(writer, sid, data, info, NAME)
                        : TDExporter.exportSurveyAsCsv(writer, sid, data, info, NAME));
    List<String[]> rows = new ArrayList<>();
    for (String line : out.toString().split("\r?\n")) {
      if (!line.isEmpty() && !line.startsWith("#")) rows.add(line.split(",", -1));
    }
    return rows;
  }

  private static List<String[]> datTraverse(String dat) {
    List<String[]> rows = new ArrayList<>();
    String body = dat.substring(dat.indexOf("FROM TO "));
    for (String line : body.split("\r?\n")) {
      if (line.trim().isEmpty() || line.startsWith("FROM TO ") || line.contains("#|S#")) continue;
      rows.add(line.trim().split("\\s+"));
    }
    return rows;
  }

  @Test public void processedCsvMatchesEveryTraverseLeg() throws Exception {
    seed(fixtureRows("shots"));
    List<String[]> expected = fixtureRows("traverse");
    int legs = 0, splays = 0;
    for (String[] row : csv(false)) {
      if (row[0].equals("-") || row[1].equals("-")) { ++splays; continue; }
      String[] ref = expected.get(legs++);
      assertEquals(ref[0] + "@" + NAME, row[0]);
      assertEquals(ref[1] + "@" + NAME, row[1]);
      assertArrayEquals(ref[0] + " to " + ref[1], Arrays.copyOfRange(ref, 2, 5), Arrays.copyOfRange(row, 2, 5));
    }
    assertEquals(27, legs);
    assertEquals(144, splays);
  }

  @Test public void compassDatMatchesEveryTraverseLegAndPassageDimension() throws Exception {
    seed(fixtureRows("shots"));
    List<String[]> expected = fixtureRows("traverse");
    for (boolean splays : new boolean[]{false, true}) {
      List<String[]> rows = datTraverse(dat(splays));
      assertEquals(27, rows.size());
      for (int i = 0; i < rows.size(); ++i) {
        assertArrayEquals("Traverse row " + i, expected.get(i), Arrays.copyOf(rows.get(i), 9));
      }
    }
  }

  @Test public void compassRoundTripPreservesEachFieldSplay() throws Exception {
    List<String[]> shots = fixtureRows("shots");
    seed(shots);
    List<String[]> splays = new ArrayList<>();
    for (String[] r : shots) if (!r[1].isEmpty() && r[2].isEmpty() && r[7].equals("0")) splays.add(r);
    assertEquals(27, QaCompassParser.parse(dat(false)).size());
    int count = 0, legs = 0;
    for (ParserShot shot : QaCompassParser.parse(dat(true))) {
      if (!shot.to.isEmpty()) { ++legs; continue; }
      String[] ref = splays.get(count++);
      assertEquals(ref[1], shot.from);
      assertEquals(Double.parseDouble(ref[3]), shot.len, .0051 * .3048);
      assertEquals(Double.parseDouble(ref[4]), shot.ber, .0501);
      assertEquals(Double.parseDouble(ref[5]), shot.cln, .0501);
    }
    assertEquals(27, legs);
    assertEquals(144, count);
  }

  private void reopen() {
    data.closeDatabase();
    data = new DataHelper(InstrumentationRegistry.getInstrumentation().getTargetContext());
    TopoDroidApp.mData = data;
  }

  @Test public void rawCsvAfterProcessedExportAndReopenPreservesAll324StoredRows() throws Exception {
    List<String[]> shots = fixtureRows("shots");
    seed(shots);
    csv(false);
    dat(true);
    reopen();
    List<String[]> raw = csv(true);
    assertEquals(324, raw.size());
    for (int i = 0; i < raw.size(); ++i) {
      String[] r = shots.get(i), out = raw.get(i);
      assertArrayEquals(Arrays.copyOf(r, 3), Arrays.copyOf(out, 3));
      for (int j = 3; j <= 5; ++j) assertEquals(Double.parseDouble(r[j]), Double.parseDouble(out[j]), .00001);
      assertArrayEquals(new String[]{device(r), r[8], r[9], r[6], r[7]}, Arrays.copyOfRange(out, 12, 17));
    }
  }

  @Test public void reopenedSketchUsesOnlyTheOwningHeadsActiveRepeats() throws Exception {
    seed(fixtureRows("shots"));
    reopen();
    TDSetting.mLoopClosure = TDSetting.LOOP_NONE;
    List<DBlock> blocks = data.selectSurveyShots(sid);
    TDNum num = new TDNum(blocks, "S01", 0, null);
    // Independent mean vectors of the retained forward blocks, in meters (east, south, down).
    assertStationVector(num, "S15", "S17", -.79247, 1.84542, .64366);
    assertStationVector(num, "S04", "S20", .51420, -1.50873, .08716);
    assertStationVector(num, "S06", "S07", 1.17666, -2.08937, -.57687);
    assertEquals(254, blocks.size());
    assertEquals(282, data.selectAllShots(sid, TDStatus.NORMAL).size());
  }

  private static void assertStationVector(TDNum num, String from, String to, double east, double south, double down) {
    NumStation a = num.getStation(from), b = num.getStation(to);
    assertNotNull(from, a);
    assertNotNull(to, b);
    assertArrayEquals(from + " to " + to, new double[]{east,south,down},
        new double[]{b.e - a.e,b.s - a.s,b.v - a.v}, .0001);
  }

  @Test public void unequalBlocksWeightEveryReadingAndKeepSeparateObservations() {
    // 3 forward and 4 reverse readings give 10.06 m. A second named back observation starts a new record.
    seed(Arrays.asList(
        row(1,"A","B",9.96,90,15,0), row(2,"","",10,90,15,1), row(3,"","",10.04,90,15,1),
        row(4,"B","A",10,270,-15,3), row(5,"","",10.06,270,-15,1), row(6,"","",10.12,270,-15,1),
        row(7,"","",10.24,270,-15,1), row(8,"B","A",12,270,-15,3),
        row(9,"A","B",7,90,15,0), row(10,"B","A",8,270,-15,0),
        row(11,"B","",2,15,0,0), row(12,"A","B",9,90,15,3)));
    TDSetting.mUnitLength = 1;
    List<String[]> metric = csv(false);
    assertEquals(6, metric.size());
    assertEquals("10.06", metric.get(0)[2]);
    List<String[]> rows = datTraverse(dat(false));
    assertEquals(5, rows.size());
    assertArrayEquals(new String[]{"A","B","33.01","90.0","15.0"}, Arrays.copyOf(rows.get(0),5));
    assertArrayEquals(new String[]{"39.37","22.97","26.25","29.53"},
        new String[]{rows.get(1)[2],rows.get(2)[2],rows.get(3)[2],rows.get(4)[2]});
  }

  private static String[] row(int id, String from, String to, double length, double bearing, double clino, int leg) {
    return new String[]{String.valueOf(id),from,to,String.valueOf(length),String.valueOf(bearing),
        String.valueOf(clino),String.valueOf(leg),"0","1","0","1"};
  }

  @Test public void deletedLastBlockCannotDonateItsRepeatsToThePreviousLeg() {
    String[] rejected = row(3,"C","B",30,180,0,3);
    rejected[7] = "1";
    seed(Arrays.asList(row(1,"A","B",10,359,0,0), row(2,"","",10,1,0,1),
        rejected, row(4,"","",30,180,0,1)));
    List<String[]> rows = datTraverse(dat(false));
    assertEquals(1, rows.size());
    assertEquals("32.80", rows.get(0)[2]);
    assertEquals(0, Double.parseDouble(rows.get(0)[3]) % 360, .01);
    assertEquals(0, Double.parseDouble(rows.get(0)[4]), .01);
    TDSetting.mUnitLength = 1;
    assertEquals("10.00", csv(false).get(0)[2]);
  }

  @Test public void orthogonalFilterUsesThreeDimensionalSeparationInDat() {
    seed(Arrays.asList(row(1,"A","B",10,0,0,0), row(2,"A","",3,90,0,0), row(3,"A","",9,20,0,0),
        row(4,"A","",10,160,0,0), row(5,"A","",2,270,0,0), row(6,"A","",4,0,90,0),
        row(7,"A","",5,0,-90,0), row(8,"C","D",10,0,60,0),
        row(9,"C","",6,0,-30,0), row(10,"C","",11,0,55,0)));
    TDSetting.mOrthogonalLRUD = true;
    TDSetting.mOrthogonalLRUDCosine = (float)Math.cos(Math.toRadians(45));
    List<String[]> rows = datTraverse(dat(false));
    assertArrayEquals(new String[]{"6.56","13.12","16.40","9.84"}, Arrays.copyOfRange(rows.get(0),5,9));
    assertEquals("0.00", rows.get(1)[6]); // The near-parallel uphill splay must be rejected.
    assertEquals("9.84", rows.get(1)[7]); // Its perpendicular downhill neighbor is accepted.
  }

  @Test public void deleteBacksightPromotesFirstSurvivingRepeatWithItsInstrumentFlags() {
    for (boolean multi : new boolean[]{false, true}) {
      int base = multi ? 100 : 0;
      String[] head = row(base+1,"B","A",10,270,-15,3);
      head[9] = "32";
      String[] repeat = row(base+7,"","",12,271,-16,1);
      repeat[9] = "131072";
      String[] old = row(base+2,"","",98,1,2,1);
      old[7] = "1";
      List<String[]> rows = new ArrayList<>(Arrays.asList(head,old,repeat,row(base+9,"","",12,272,-17,1)));
      if (multi) rows.add(row(base+5,"","",11,270,-15,1));
      seed(rows);
      data.updateShotComment(base+1, sid, "surveyor's note");
      data.updateShotExtend(base+1, sid, -1, .25f);
      DBlock owner = data.selectShot(base+1,sid);
      List<DBlock> selected = multi ? Arrays.asList(owner, data.selectShot(base+5,sid)) : Collections.singletonList(owner);
      assertEquals(selected.size(), data.deleteShotSelection(sid, selected, TDStatus.DELETED, false).size());
      reopen();
      DBlock promoted = data.selectShot(base+7,sid);
      assertTrue(promoted.isBackLeg());
      assertEquals("B", promoted.mFrom);
      assertEquals("A", promoted.mTo);
      assertEquals("surveyor's note", promoted.mComment);
      assertEquals(131072 | 32, promoted.getFlagFully());
      assertEquals(-1, promoted.getIntExtend());
      assertEquals(.25f, promoted.getStretch(), 0);
      assertEquals(12, promoted.mLength, 0);
      assertEquals(271, promoted.mBearing, 0);
      assertEquals(-16, promoted.mClino, 0);
      assertTrue(data.selectShot(base+9,sid).isSecLeg());
    }
    assertEquals(4, data.selectAllShots(sid, TDStatus.NORMAL).size());
    assertEquals(2, datTraverse(dat(false)).size());
  }

  @Test public void wholeBacksightDeletionStopsAtNextHeadAndRetainsPriorStatuses() {
    String[] old = row(4,"","",11,270,-15,1);
    old[7] = Long.toString(TDStatus.CHECK);
    seed(Arrays.asList(row(1,"B","A",10,270,-15,3),row(2,"","",10,270,-15,1),
        old,row(6,"","",10,270,-15,1),row(9,"B","C",4,0,0,0),row(10,"","",4,0,0,1),
        row(11,"C","D",5,60,0,0),row(12,"","",5,60,0,1)));
    List<DBlock> removed = data.deleteShotSelection(sid, Arrays.asList(data.selectShot(1,sid),
        data.selectShot(2,sid),data.selectShot(11,sid)), TDStatus.DELETED, true);
    List<Long> cutIds = new ArrayList<>();
    for (DBlock shot : removed) cutIds.add(shot.mId);
    assertEquals(Arrays.asList(1L,2L,6L,11L,12L), cutIds);
    reopen();
    assertEquals(5, data.selectAllShots(sid, TDStatus.DELETED).size());
    assertEquals(4, data.selectAllShots(sid, TDStatus.CHECK).get(0).mId);
    List<String[]> rows = datTraverse(dat(false));
    assertEquals(1, rows.size());
    assertArrayEquals(new String[]{"B","C","13.12","0.0","0.0"}, Arrays.copyOf(rows.get(0),5));
  }
}
