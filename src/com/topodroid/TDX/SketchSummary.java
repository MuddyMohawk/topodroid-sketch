package com.topodroid.TDX;

import com.topodroid.types.PlotType;
import java.util.*;

/** The current paired storage contract. UI and callers carry identities, not adjacent IDs. */
final class SketchSummary
{
  final String name;
  final PlotInfo plan, profile;
  final boolean deleted;
  final String problem;

  private SketchSummary(String name, PlotInfo plan, PlotInfo profile, boolean deleted, String problem) {
    this.name = name; this.plan = plan; this.profile = profile;
    this.deleted = deleted; this.problem = problem;
  }
  long id() { return plan != null ? plan.id : profile.id; }
  String origin() { return plan != null ? plan.start : profile.start; }
  boolean available() { return problem == null; }

  static List<SketchSummary> pair(List<PlotInfo> plots, Set<Long> deleted) {
    Map<String,List<PlotInfo>> groups = new LinkedHashMap<>();
    for (PlotInfo p : plots) {
      if (!PlotType.isSketch2D(p.type)) continue;
      String base = p.name.length() > 0 ? p.name.substring(0, p.name.length()-1) : "";
      if (!groups.containsKey(base)) groups.put(base, new ArrayList<>());
      groups.get(base).add(p);
    }
    List<SketchSummary> result = new ArrayList<>();
    for (Map.Entry<String,List<PlotInfo>> group : groups.entrySet()) {
      PlotInfo plan = null, profile = null;
      boolean invalid = false;
      for (PlotInfo p : group.getValue()) {
        if (p.type == PlotType.PLOT_PLAN) {
          invalid |= plan != null || !p.name.equals(group.getKey()+"p"); plan = p;
        } else {
          invalid |= profile != null || !p.name.equals(group.getKey()+"s"); profile = p;
        }
      }
      String problem = invalid ? "Inconsistent sketch pair" : plan == null || profile == null ? "Missing Plan or Profile" :
        !Objects.equals(plan.start, profile.start) || deleted.contains(plan.id) != deleted.contains(profile.id) ? "Inconsistent sketch pair" : null;
      result.add(new SketchSummary(group.getKey(), plan, profile,
        deleted.contains(plan != null ? plan.id : profile.id), problem));
    }
    result.sort(Comparator.comparingLong(SketchSummary::id));
    return result;
  }

  static List<Long> order(String stored, List<SketchSummary> sketches) {
    Set<Long> remaining = new LinkedHashSet<>();
    for (SketchSummary sketch : sketches) remaining.add(sketch.id());
    List<Long> result = new ArrayList<>();
    if (stored != null && stored.startsWith("1:")) {
      try {
        for (String token : stored.substring(2).split(",")) {
          if (token.isEmpty()) continue;
          long id = Long.parseLong(token);
          if (remaining.remove(id)) result.add(id);
        }
      } catch (NumberFormatException e) {
        result.clear(); remaining.clear();
        for (SketchSummary sketch : sketches) remaining.add(sketch.id());
      }
    }
    result.addAll(remaining);
    return result;
  }
  static String encodeOrder(List<Long> ids) {
    StringBuilder value = new StringBuilder("1:");
    for (long id : ids) value.append(id).append(',');
    return value.toString();
  }
}
