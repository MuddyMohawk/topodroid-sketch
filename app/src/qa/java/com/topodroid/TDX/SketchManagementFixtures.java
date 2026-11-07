package com.topodroid.TDX;

import java.io.*;

/** Explicit persisted records shared by JVM and Android checks. No production rewrite code. */
final class SketchManagementFixtures {
  static PlotInfo plot(long id,String name,int type) {
    PlotInfo plot=new PlotInfo(); plot.id=id; plot.surveyId=42; plot.name=name; plot.type=type;
    plot.start="A"; plot.hide=""; return plot;
  }
  static SketchDraft draft(String name) {
    SketchDraft draft=new SketchDraft(); draft.name=name; draft.origin="A"; draft.allowMissing=true; return draft;
  }
  static byte[] drawing(String header,int type,String section,float dx,float dy,String origin) throws IOException {
    ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
    out.write('V'); out.writeInt(604028); out.write('S'); out.writeUTF(header); out.writeInt(type);
    if (type==8) out.writeInt(45);
    out.writeUTF("user,section,label"); out.writeUTF("wall,section"); out.writeUTF("water");
    out.write('I'); out.writeFloat(-10000); out.writeFloat(-10000); out.writeFloat(10000); out.writeFloat(10000); out.writeInt(0);
    out.write('N'); out.writeInt(0);
    point(out,"user",10,20,"text with xs-A-oldp", "-custom preserved -sketchweight 3 -image path.png",0);
    point(out,"section",30+dx,40+dy,"", "-scrap \""+section+"\" -pip yes -pipw 90 -piph 70 -piprefs no -private keep",0);
    line(out,"wall",0,0,"-sketchweight 2 -custom wall",0);
    String id=section.substring(section.indexOf("-xs-" )>=0 ? section.indexOf("-xs-")+1 : 0);
    line(out,"section",dx,dy,"-id "+id+" -private guide1",0);
    out.write('N'); out.writeInt(2);
    point(out,"user",70,80,"", "-custom second-scrap",2);
    out.write('A'); out.writeUTF("water"); out.writeUTF(""); out.writeUTF("a"); out.writeInt(1); out.write(1);
    out.writeFloat(17); out.writeInt(1); out.writeInt(2); out.writeUTF("-sketchweight 5 -private area"); out.writeInt(3);
    vertex(out,1,1); vertex(out,5,1); vertex(out,1,5);
    out.write('T'); out.writeFloat(88); out.writeFloat(99); out.writeFloat(12); out.writeInt(1); out.writeInt(1); out.writeInt(2);
    out.writeUTF("Styled label\nsecond line"); out.writeUTF("-private text-style");
    out.write('Y'); out.writeFloat(22); out.writeFloat(44); out.writeFloat(0); out.writeInt(1); out.writeInt(1); out.writeInt(2);
    out.writeUTF("Reference image"); out.writeUTF("-private image-metadata"); out.writeInt(7); out.writeUTF("photo-code");
    out.writeInt(1); out.writeFloat(22); out.writeFloat(44); out.writeFloat(3);
    out.write('F'); out.write('D'); for(int i=0;i<5;i++) out.writeFloat(i);
    out.writeUTF(origin); out.writeUTF(""); out.writeUTF(""); out.writeUTF("comment");
    out.write('X'); out.writeFloat(1); out.writeFloat(2); out.writeUTF("A"); out.writeInt(1); out.writeInt(0); out.writeInt(-1);
    out.write('E'); return bytes.toByteArray();
  }
  private static void point(DataOutputStream out,String symbol,float x,float y,String text,String options,int scrap) throws IOException {
    out.write('P'); out.writeFloat(x); out.writeFloat(y); out.writeUTF(symbol); out.writeUTF(""); out.writeFloat(23);
    out.writeInt(1); out.writeInt(1); out.writeInt(scrap); out.writeUTF(text); out.writeUTF(options);
  }
  private static void line(DataOutputStream out,String symbol,float dx,float dy,String options,int scrap) throws IOException {
    out.write('L'); out.writeUTF(symbol); out.writeUTF(""); out.write(0); out.write(1);
    out.writeInt(0); out.writeInt(0); out.writeInt(1); out.writeInt(scrap); out.writeUTF(options); out.writeInt(2);
    vertex(out,3+dx,4+dy); out.writeFloat(5+dx); out.writeFloat(6+dy); out.write(1);
    out.writeFloat(3+dx); out.writeFloat(7+dy); out.writeFloat(4+dx); out.writeFloat(8+dy);
  }
  private static void vertex(DataOutputStream out,float x,float y) throws IOException { out.writeFloat(x); out.writeFloat(y); out.write(0); }
}
