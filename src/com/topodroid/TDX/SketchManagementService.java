package com.topodroid.TDX;

import com.topodroid.types.PlotType;
import com.topodroid.util.*;
import com.topodroid.num.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Shared management boundary for UI, legacy recovery and archive metadata. */
final class SketchManagementService {
  static final String ARCHIVE_ENTRY = "sketch_order.json";
  final DataHelper data;
  final long sid;
  SketchFileJournal.Hook fileHook; // deterministic failure injection at the file/DB boundary
  SketchManagementService(DataHelper data, long sid) { this.data = data; this.sid = sid; }
  static String orderKey(long sid) { return "sketch_order:"+sid; }
  static String recentKey(long sid) { return "sketch_recent:"+sid; }
  static String commitKey(long sid) { return "sketch_commit:"+sid; }
  static String[] configKeys(long sid) { return new String[]{orderKey(sid),recentKey(sid),commitKey(sid)}; }
  private SurveyInfo survey() throws IOException {
    SurveyInfo info = data.selectSurveyInfo(sid);
    if (info == null) throw new IOException("Survey is no longer available");
    return info;
  }
  static File drawingFile(String survey, String plot) {
    return new File(new File(TDPath.getSurveyDir(survey),"tdr"),survey+"-"+plot+".tdr");
  }
  void orderPicker(List<PlotInfo> plots) {
    Map<Long,Integer> positions=new HashMap<>(); int index=0;
    for (SketchSummary sketch : list()) {
      if (sketch.plan!=null) positions.put(sketch.plan.id,index);
      if (sketch.profile!=null) positions.put(sketch.profile.id,index);
      index++;
    }
    plots.sort(Comparator.comparingInt(plot -> positions.containsKey(plot.id) ? positions.get(plot.id) : Integer.MAX_VALUE));
  }
  private File journalDirectory() throws IOException {
    return new File(TDPath.getSurveyDir(survey().name),".sketch-edit-"+sid);
  }
  void recover() throws IOException {
    synchronized (TDPath.mTdrLock) {
      File directory = journalDirectory();
      if (directory.exists()) new SketchFileJournal(directory).recover(data.getSketchConfig(commitKey(sid)));
    }
  }
  List<SketchSummary> list() {
    Set<Long> deleted = new HashSet<>();
    for (PlotInfo plot : data.selectAllPlots(sid,TDStatus.DELETED)) deleted.add(plot.id);
    List<SketchSummary> all = SketchSummary.pair(data.selectAllPlots(sid),deleted);
    List<Long> order = SketchSummary.order(data.getValue(orderKey(sid)),all);
    Map<Long,Integer> positions = new HashMap<>();
    for (int i=0;i<order.size();i++) positions.put(order.get(i),i);
    all.sort(Comparator.comparingInt(s -> positions.get(s.id())));
    return all;
  }
  SketchSummary find(long id) throws IOException {
    for (SketchSummary sketch : list()) if (sketch.id() == id || sketch.profile != null && sketch.profile.id == id) {
      if (!sketch.available()) throw new IOException(sketch.problem);
      return sketch;
    }
    throw new IOException("Sketch is no longer available");
  }
  static String normalizedName(String name) throws IOException {
    String value = name == null ? "" : name.trim().replaceAll("\\s+","_");
    if (value.isEmpty() || value.equals(".") || value.equals("..") || value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*"))
      throw new IOException("Enter a valid sketch name");
    return value;
  }
  String suggestedName() throws IOException {
    long candidate=Math.max(1L,(long)data.maxPlotIndex(sid)+1);
    Set<String> reserved=new HashSet<>();
    for (PlotInfo plot : data.selectAllPlots(sid)) reserved.add(plot.name.toLowerCase(Locale.ROOT));
    String surveyName=survey().name;
    while (true) {
      String name=Long.toString(candidate++);
      if (reserved.contains(name+"p") || reserved.contains(name+"s")) continue;
      try {
        rejectExistingFiles(drawingFile(surveyName,name+"p"),null);
        rejectExistingFiles(drawingFile(surveyName,name+"s"),null);
        return name;
      } catch (IOException collision) { /* Retained files reserve their names too. */ }
    }
  }
  void validate(SketchDraft draft) throws IOException {
    draft.name = normalizedName(draft.name);
    draft.origin = draft.origin == null ? "" : draft.origin.replaceAll("\\s+","");
    if (draft.origin.isEmpty() || !TDUtil.isStationName(draft.origin)) throw new IOException("Enter a valid origin station");
    SketchSummary current = draft.id < 0 ? null : find(draft.id);
    if ((current == null || !draft.origin.equals(current.origin())) && !draft.allowMissing && !data.hasSurveyStation(sid,draft.origin))
      throw new IOException("Origin station is not in the survey");
    for (PlotInfo plot : data.selectAllPlots(sid)) {
      if (current != null && (plot.id == current.plan.id || plot.id == current.profile.id)) continue;
      if (plot.name.equalsIgnoreCase(draft.name+"p") || plot.name.equalsIgnoreCase(draft.name+"s"))
        throw new IOException("That sketch name is already used, including deleted sketches");
    }
    if (current==null || !current.name.equals(draft.name)) {
      String surveyName=survey().name;
      rejectExistingFiles(drawingFile(surveyName,draft.name+"p"),current==null ? null : drawingFile(surveyName,current.plan.name));
      rejectExistingFiles(drawingFile(surveyName,draft.name+"s"),current==null ? null : drawingFile(surveyName,current.profile.name));
    }
    if (draft.projected && (draft.azimuth < 0 || draft.azimuth > 359)) throw new IOException("Azimuth must be 0–359 degrees");
  }
  private void rejectExistingFiles(File target, File original) throws IOException {
    File[] matches=target.getParentFile().listFiles((dir,name) ->
      (original==null || !(name.equals(original.getName()) || name.matches(java.util.regex.Pattern.quote(original.getName())+"\\.bck[0-9]*"))) &&
      (name.equalsIgnoreCase(target.getName()) || name.toLowerCase(Locale.ROOT).matches(java.util.regex.Pattern.quote(target.getName().toLowerCase(Locale.ROOT))+"\\.bck[0-9]*")));
    if (matches!=null && matches.length>0) throw new IOException("Drawing files already use that name");
  }
  SketchSummary create(SketchDraft draft) throws IOException {
    synchronized (TDPath.mTdrLock) {
      recover(); validate(draft);
      long id = data.writeSketch(sid, () -> {
        long p = data.insertPlot(sid,-1,draft.name+"p",PlotType.PLOT_PLAN,0,draft.origin,"",0,0,TopoDroidApp.mScaleFactor,0,0,"","",0);
        long s = data.insertPlot(sid,-1,draft.name+"s",draft.projected ? PlotType.PLOT_PROJECTED : PlotType.PLOT_EXTENDED,
          0,draft.origin,"",0,0,TopoDroidApp.mScaleFactor,draft.projected ? draft.azimuth : 0,draft.projected ? draft.oblique : 0,"","",0);
        if (p < 0 || s < 0) throw new IOException("Unable to create both sketch views");
        List<Long> order = new ArrayList<>(); for (SketchSummary item : list()) order.add(item.id());
        data.putSketchConfig(orderKey(sid),SketchSummary.encodeOrder(order));
        return p;
      });
      return find(id);
    }
  }
  void setDeleted(long id, boolean deleted) throws IOException {
    synchronized (TDPath.mTdrLock) {
      recover(); SketchSummary sketch = find(id);
      data.writeSketch(sid, () -> {
        data.updateSketchRow(sid,sketch.plan,deleted ? TDStatus.DELETED : TDStatus.NORMAL);
        data.updateSketchRow(sid,sketch.profile,deleted ? TDStatus.DELETED : TDStatus.NORMAL);
        if (deleted && recent()[0] == sketch.id()) data.putSketchConfig(recentKey(sid),null);
        return null;
      });
      if (deleted && sid == TDInstance.sid && sketch.name.equals(TDInstance.recentPlot)) TDInstance.setRecentPlot(null,0);
    }
  }
  void reorder(List<Long> active) throws IOException {
    synchronized (TDPath.mTdrLock) {
      List<SketchSummary> all = list(); Set<Long> expected = new HashSet<>();
      for (SketchSummary sketch : all) if (!sketch.deleted) expected.add(sketch.id());
      if (active.size() != expected.size() || !new HashSet<>(active).equals(expected)) throw new IOException("Sketch list changed; try again");
      Iterator<Long> moving = active.iterator(); List<Long> order = new ArrayList<>();
      for (SketchSummary sketch : all) order.add(sketch.deleted ? sketch.id() : moving.next());
      data.writeSketch(sid, () -> { data.putSketchConfig(orderKey(sid),SketchSummary.encodeOrder(order)); return null; });
    }
  }
  long[] recent() {
    String value = data.getValue(recentKey(sid));
    try { String[] parts = value.split(":"); if (parts.length == 3 && parts[0].equals("1"))
      return new long[]{Long.parseLong(parts[1]),Long.parseLong(parts[2])}; }
    catch (RuntimeException ignored) { }
    return new long[]{-1,PlotType.PLOT_PLAN};
  }
  void opened(long id, long type) throws IOException {
    SketchSummary sketch = find(id);
    if (sketch.deleted) throw new IOException("Sketch is deleted");
    if (type != PlotType.PLOT_PLAN && type != sketch.profile.type) throw new IOException("View is unavailable");
    // Local navigation state is allowed for immutable surveys.
    data.setValue(recentKey(sid),"1:"+sketch.id()+":"+type);
    if (sid == TDInstance.sid) TDInstance.setRecentPlot(sketch.name,type);
  }
  void edit(SketchDraft draft) throws IOException {
    synchronized (TDPath.mTdrLock) {
      recover(); validate(draft);
      if (!data.isSurveyMutable(sid)) throw new IOException("Survey is read-only");
      SketchSummary sketch = find(draft.id);
      if (sketch.deleted) throw new IOException("Restore the sketch before editing");
      if (!draft.hasEdits(sketch.name,sketch.origin())) return;
      boolean rename = !sketch.name.equals(draft.name), origin = draft.changesOrigin(sketch.origin());
      SurveyInfo info = survey(); List<PlotInfo> plots = data.selectAllPlots(sid);
      Map<String,String> renamePlots = new LinkedHashMap<>(), references = new LinkedHashMap<>();
      if (rename) {
        renamePlots.put(sketch.plan.name,draft.name+"p"); renamePlots.put(sketch.profile.name,draft.name+"s");
        for (PlotInfo plot : plots) if (plot.isSectionPrivate() && renamePlots.containsKey(plot.hide) &&
            (plot.type == PlotType.PLOT_X_SECTION || plot.type == PlotType.PLOT_XH_SECTION)) {
          String suffix = "-"+plot.hide;
          if (!plot.name.endsWith(suffix)) throw new IOException("Private section name does not match its parent");
          renamePlots.put(plot.name,plot.name.substring(0,plot.name.length()-suffix.length())+"-"+renamePlots.get(plot.hide));
        }
        for (Map.Entry<String,String> entry : renamePlots.entrySet()) {
          for (PlotInfo plot : plots) if (!plot.name.equals(entry.getKey()) && plot.name.equalsIgnoreCase(entry.getValue()))
            throw new IOException("A section already uses the new name");
          references.put(entry.getKey(),entry.getValue());
          references.put(info.name+"-"+entry.getKey(),info.name+"-"+entry.getValue());
          rejectExistingFiles(drawingFile(info.name,entry.getValue()),drawingFile(info.name,entry.getKey()));
        }
      }
      float planX=0, planY=0, profileX=0, profileY=0;
      if (origin) {
        TDNum num = new TDNum(data.selectAllShots(sid,TDStatus.NORMAL),draft.origin,
          0, null);
        NumStation old = num.getStation(sketch.origin());
        if (old != null) { planX=(float)(20*old.e); planY=(float)(20*old.s); profileX=(float)(20*old.h); profileY=(float)(20*old.v); }
      }
      SketchFileJournal journal = new SketchFileJournal(journalDirectory());
      try {
        for (PlotInfo plot : plots) {
          File source = drawingFile(info.name,plot.name);
          File[] files = source.getParentFile().listFiles((dir,name) -> name.equals(source.getName()) ||
            name.matches(java.util.regex.Pattern.quote(source.getName())+"\\.bck[0-9]*"));
          if (files == null) {
            if (source.getParentFile().exists()) throw new IOException("Cannot read sketch files");
            files = new File[0]; // a never-opened pair need not have drawing files yet
          }
          float dx = plot.id == sketch.plan.id ? planX : plot.id == sketch.profile.id ? profileX : 0;
          float dy = plot.id == sketch.plan.id ? planY : plot.id == sketch.profile.id ? profileY : 0;
          String newOrigin = origin && (plot.id == sketch.plan.id || plot.id == sketch.profile.id) ? draft.origin : null;
          for (File file : files) {
            if (!rename && newOrigin == null) continue;
            byte[] before = SketchFileJournal.read(file);
            byte[] after = SketchDrawingRewrite.rewrite(before,references,dx,dy,newOrigin);
            String newName = renamePlots.get(plot.name);
            File target = newName == null ? file : new File(source.getParentFile(),info.name+"-"+newName+".tdr"+file.getName().substring(source.getName().length()));
            if (!target.equals(file) || !Arrays.equals(before,after)) journal.stage(file,target,after);
          }
        }
        // Legacy section photos use the section name rather than a media ID.
        for (Map.Entry<String,String> entry : renamePlots.entrySet()) {
          if (entry.getKey().equals(sketch.plan.name) || entry.getKey().equals(sketch.profile.name)) continue;
          File photos=new File(TDPath.getSurveyDir(info.name),"photo");
          for (String extension : new String[]{".jpg",".png"}) {
            File oldPhoto=new File(photos,entry.getKey()+extension);
            if (oldPhoto.exists()) journal.stage(oldPhoto,new File(photos,entry.getValue()+extension),SketchFileJournal.read(oldPhoto));
          }
        }
        journal.seal();
        data.writeSketch(sid, () -> {
          journal.apply(fileHook);
          for (PlotInfo plot : plots) {
            boolean changed = false;
            if (renamePlots.containsKey(plot.name)) { plot.name = renamePlots.get(plot.name); changed = true; }
            if (plot.isSectionPrivate() && renamePlots.containsKey(plot.hide)) { plot.hide = renamePlots.get(plot.hide); changed = true; }
            if (plot.id == sketch.plan.id || plot.id == sketch.profile.id) { plot.start = draft.origin; changed = true; }
            if (changed) data.updateSketchRow(sid,plot,-1);
          }
          data.putSketchConfig(commitKey(sid),journal.token);
          return null;
        });
      } finally { journal.recover(data.getSketchConfig(commitKey(sid))); }
      DrawingSurface.clearManagersCache();
      if (sid == TDInstance.sid && sketch.name.equals(TDInstance.recentPlot)) TDInstance.setRecentPlot(draft.name,TDInstance.recentPlotType);
    }
  }
  static String exportArchive(DataHelper data, long sid) throws JSONException {
    JSONArray names = new JSONArray();
    for (SketchSummary sketch : new SketchManagementService(data,sid).list()) if (sketch.plan != null) names.put(sketch.plan.name);
    return new JSONObject().put("version",1).put("plans",names).toString();
  }
  static boolean importArchive(DataHelper data, long sid, String value) {
    if (value == null) return true;
    try {
      JSONObject json = new JSONObject(value);
      if (json.getInt("version") != 1) return false;
      JSONArray names = json.getJSONArray("plans"); Set<String> seen = new HashSet<>();
      Map<String,Long> ids = new HashMap<>(); List<SketchSummary> all = new SketchManagementService(data,sid).list();
      for (SketchSummary sketch : all) if (sketch.plan != null) ids.put(sketch.plan.name,sketch.id());
      List<Long> order = new ArrayList<>();
      for (int i=0;i<names.length();i++) {
        String name = names.getString(i);
        if (!seen.add(name) || !ids.containsKey(name)) return false;
        order.add(ids.get(name));
      }
      for (SketchSummary sketch : all) if (!order.contains(sketch.id())) order.add(sketch.id());
      // Imports can deliberately produce immutable surveys; optional local metadata still applies.
      data.setValue(orderKey(sid),SketchSummary.encodeOrder(order));
      return SketchSummary.encodeOrder(order).equals(data.getValue(orderKey(sid)));
    } catch (JSONException | RuntimeException e) { return false; }
  }
}
