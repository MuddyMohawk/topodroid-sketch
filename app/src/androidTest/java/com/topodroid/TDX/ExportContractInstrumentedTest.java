package com.topodroid.TDX;

import com.topodroid.TDX.qa.TestTier;
import com.topodroid.inport.*;
import com.topodroid.prefs.TDSetting;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(com.topodroid.TDX.QaJUnit4.class)
@TestTier(TestTier.Tier.FAST)
@QaFixtureRule.Database
@com.topodroid.qa.TestFeatures({com.topodroid.qa.TestFeatures.Feature.EXCHANGE, com.topodroid.qa.TestFeatures.Feature.DATA})
public class ExportContractInstrumentedTest {
  @Rule public final QaFixtureRule fixture=new QaFixtureRule();
  private long survey;
  @Before public void seedProductionData() {
    DataHelper data=TopoDroidApp.mData;
    survey=data.setSurvey("qa_export_contract",0);
    data.updateSurveyInfo(survey,"2026-11-03","QA team",0,"contract","A1",0);
    shot("A1","A2",10,90);shot("A2","A3",6,0);
    TDSetting.mUnitLength=1;TDSetting.mUnitAngle=1;TDSetting.mCsvSeparator=',';TDSetting.mSurvexEol="\r\n";
  }
  private void shot(String from,String to,double length,double bearing) {
    DataHelper data=TopoDroidApp.mData;
    long id=data.insertManualShot(survey,-1,0,0,length,bearing,0,0,1,0,0,0,1);
    assertTrue(id>=0);data.updateShotName(id,survey,from,to);
    data.updateShotComment(id,survey,"lead, \"quoted\" note");
  }
  @Test public void compassProductionExport_reimportsStationsLengthsAndDirections() throws Exception {
    StringWriter payload=new StringWriter();
    assertEquals(1,TDExporter.exportSurveyAsDat(new BufferedWriter(payload),survey,TopoDroidApp.mData,TopoDroidApp.mData.selectSurveyInfo(survey),"qa_export_contract",""));
    List<ParserShot> parsed=QaCompassParser.parse(payload.toString());
    List<ParserShot> traverse=new ArrayList<>();for(ParserShot shot:parsed)if(!shot.from.isEmpty()&&!shot.to.isEmpty())traverse.add(shot);
    assertEquals("Traverse must survive production export/import",2,traverse.size());
    assertEquals("A1",traverse.get(0).from);assertEquals("A2",traverse.get(0).to);
    assertEquals(10,traverse.get(0).len,.01);assertEquals(90,traverse.get(0).ber,.1);
    assertEquals(6,traverse.get(1).len,.01);assertEquals(0,traverse.get(1).ber,.1);
  }
  @Test public void csvProductionExport_quotesCommentsAndRetainsTwoLegs() throws Exception {
    StringWriter payload=new StringWriter();
    assertEquals(1,TDExporter.exportSurveyAsCsv(new BufferedWriter(payload),survey,TopoDroidApp.mData,TopoDroidApp.mData.selectSurveyInfo(survey),"qa_export_contract"));
    String text=payload.toString();assertTrue(text.contains("\"lead, \"\"quoted\"\" note\""));
    List<List<String>> rows=new ArrayList<>();
    for(String line:text.split("\r?\n")) {
      if(line.isEmpty()||line.startsWith("#"))continue;
      List<String> fields=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
      for(int i=0;i<line.length();i++) {
        char c=line.charAt(i);
        if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append(c);i++;}else quoted=!quoted;}
        else if(c==','&&!quoted){fields.add(field.toString());field.setLength(0);}else field.append(c);
      }
      assertFalse("Unclosed CSV quote",quoted);fields.add(field.toString());rows.add(fields);
    }
    assertEquals(2,rows.size());assertEquals(8,rows.get(0).size());assertEquals("lead, \"quoted\" note",rows.get(0).get(7));
    assertTrue(rows.get(0).get(0).startsWith("A1@"));assertTrue(rows.get(1).get(1).startsWith("A3@"));
  }
}
