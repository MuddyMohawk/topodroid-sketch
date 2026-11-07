package com.topodroid.TDX;

/** Capture values, not the mutable PlotInfo object held by the drawing window. */
final class SketchSaveIdentity {
  final long survey, id;
  final String name;
  SketchSaveIdentity(PlotInfo plot) { survey=plot.surveyId; id=plot.id; name=plot.name; }
  boolean matches(PlotInfo current, int status) {
    return current!=null && status==0 && survey==current.surveyId && id==current.id && name.equals(current.name);
  }
}
