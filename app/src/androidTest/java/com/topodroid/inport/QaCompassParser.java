package com.topodroid.inport;
import java.io.*;
import java.util.*;
/** Test-only access to the production parser; no replacement parser or fake importer. */
public final class QaCompassParser {
  public static List<ParserShot> parse(String payload) throws Exception {
    return new ParserCompass(new InputStreamReader(new ByteArrayInputStream(payload.getBytes("UTF-8")),"UTF-8"),"qa.dat",false,false,true).getShots();
  }
}
