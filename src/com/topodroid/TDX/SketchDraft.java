package com.topodroid.TDX;

/** Unsaved form state; projection preview only changes this object. */
final class SketchDraft {
  long id = -1;
  String name = "", origin = "";
  boolean projected, allowMissing;
  int azimuth, oblique;
  static SketchDraft editing(SketchSummary saved) {
    SketchDraft draft=new SketchDraft(); draft.id=saved.id(); draft.name=saved.name; draft.origin=saved.origin();
    draft.projected=saved.profile.type==com.topodroid.types.PlotType.PLOT_PROJECTED;
    draft.azimuth=Math.round(saved.profile.azimuth); draft.oblique=Math.round(saved.profile.clino);
    return draft;
  }
  boolean changesOrigin(String savedOrigin) { return !origin.equals(savedOrigin); }
  boolean hasEdits(String savedName,String savedOrigin) { return !name.equals(savedName) || changesOrigin(savedOrigin); }
}
