package com.topodroid.TDX;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.topodroid.prefs.TDSetting;
import com.topodroid.types.PlotType;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Paired sketch management; forms own drafts, the service owns persisted changes. */
public class SketchManagerActivity extends Activity {
  static final int REQUEST = 7421;
  static final String SURVEY = "sketch.survey", CURRENT = "sketch.current", EDIT = "sketch.edit", VIEW = "sketch.view";
  private SketchManagementService service;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private long sid;
  private String surveyName;
  private boolean deleted, busy, mutable;
  private int dock, surface, cell, ink, dim, accent, amber, line;
  private LinearLayout root, rows;
  private ScrollView scroll;
  private int dragScrollDirection;
  private final Runnable dragScroller=new Runnable() {
    @Override public void run() {
      if (dragScrollDirection==0 || scroll==null) return;
      scroll.scrollBy(0,dp(20)*dragScrollDirection);
      scroll.postDelayed(this,50);
    }
  };
  private Dialog sheet;
  private SketchDraft draft;
  private EditText nameInput, originInput, azimuthInput;
  private CheckBox missingInput;
  private Switch projectionInput;
  private List<SketchSummary> visible = new ArrayList<>();

  static Intent intent(Context context, long sid, long current, boolean edit) {
    return new Intent(context,SketchManagerActivity.class).putExtra(SURVEY,sid).putExtra(CURRENT,current).putExtra(EDIT,edit);
  }
  @Override protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().getDecorView().setSystemUiVisibility(TDSetting.mUiVisibility);
    sid = getIntent().getLongExtra(SURVEY,-1);
    if (TopoDroidApp.mData == null || sid != TDInstance.sid) { finish(); return; }
    service = new SketchManagementService(TopoDroidApp.mData,sid);
    SurveyInfo info = TopoDroidApp.mData.selectSurveyInfo(sid);
    if (info == null) { finish(); return; }
    surveyName = info.name; mutable = TopoDroidApp.mData.isSurveyMutable(sid);
    dock=color(R.color.toolbar_dock); surface=color(R.color.toolbar_surface); cell=color(R.color.toolbar_cell);
    ink=color(R.color.toolbar_ink); dim=color(R.color.toolbar_dim); accent=color(R.color.toolbar_accent);
    amber=color(R.color.toolbar_amber); line=color(R.color.toolbar_grid_line);
    if (saved != null) {
      deleted=saved.getBoolean("deleted");
      if (saved.getBoolean("draft")) {
        draft=new SketchDraft(); draft.id=saved.getLong("id"); draft.name=saved.getString("name","");
        draft.origin=saved.getString("origin",""); draft.projected=saved.getBoolean("projected");
        draft.allowMissing=saved.getBoolean("missing"); draft.azimuth=saved.getInt("azimuth"); draft.oblique=saved.getInt("oblique");
      }
    }
    render();
    run(() -> { service.recover(); return null; }, ignored -> {
      render();
      if (draft != null) showSheet();
      else if (saved == null && getIntent().getBooleanExtra(EDIT,false)) edit(getIntent().getLongExtra(CURRENT,-1));
    });
  }
  private int color(int resource) { return ToolbarVisualStyle.color(this,resource); }
  private int dp(int value) { return ToolbarVisualStyle.dp(this,value); }
  private LinearLayout column() { LinearLayout view=new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
  private LinearLayout row() { LinearLayout view=new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
  private TextView label(String text,int size,int color) { return ToolbarVisualStyle.label(this,text,size,color); }
  private TextView action(String text,Runnable task) {
    TextView view=ToolbarVisualStyle.action(this,text,accent); view.setMinHeight(dp(48));
    view.setOnClickListener(v -> { if (!busy) task.run(); }); return view;
  }
  private void render() {
    stopDragScroll();
    root=column(); root.setId(R.id.sketch_manager_list); root.setBackgroundColor(dock);
    LinearLayout bar=row(); bar.setPadding(dp(8),0,dp(8),0); bar.setBackgroundColor(surface);
    bar.addView(action("‹",this::onBackPressed),new LinearLayout.LayoutParams(dp(40),dp(56)));
    TextView title=label(deleted ? getString(R.string.sketch_deleted) : getString(R.string.sketch_manager_title),18,ink);
    title.setTypeface(null,Typeface.BOLD); bar.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));
    if (!deleted && mutable) {
      TextView add=action(getString(R.string.sketch_new),this::create); add.setId(R.id.plot_new); bar.addView(add);
    }
    TextView menu=action("⋮",() -> {}); menu.setContentDescription(getString(R.string.sketch_more));
    menu.setOnClickListener(v -> {
      if (busy) return;
      PopupMenu popup=new PopupMenu(this,menu);
      popup.getMenu().add(deleted ? R.string.sketch_manager_title : R.string.sketch_deleted).setOnMenuItemClickListener(item -> {
        deleted=!deleted; render(); return true;
      }); popup.show();
    }); bar.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(56))); root.addView(bar);
    TextView heading=label(surveyName+"  ·  "+getString(busy ? R.string.sketch_working : deleted ? R.string.sketch_restore_hint : R.string.sketch_open_hint),12,dim);
    heading.setPadding(dp(12),dp(12),dp(12),dp(12)); root.addView(heading);
    scroll=new ScrollView(this); rows=column(); rows.setPadding(dp(8),0,dp(8),dp(16)); scroll.addView(rows);
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    visible=new ArrayList<>();
    for (SketchSummary sketch : service.list()) if (sketch.deleted == deleted) visible.add(sketch);
    for (SketchSummary sketch : visible) addSketch(sketch);
    if (visible.isEmpty()) {
      TextView empty=label(getString(deleted ? R.string.sketch_no_deleted : R.string.sketch_empty),18,dim);
      empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),dp(56),dp(24),dp(24)); rows.addView(empty);
      if (!deleted && mutable) rows.addView(action(getString(R.string.sketch_new),this::create));
    }
    scroll.setOnDragListener((v,event) -> {
      if (!(event.getLocalState() instanceof Long)) return false;
      if (event.getAction()==DragEvent.ACTION_DRAG_LOCATION) {
        dragScroll(event.getY());
      }
      if (event.getAction()==DragEvent.ACTION_DRAG_ENDED) stopDragScroll();
      return true;
    });
    setContentView(root);
  }
  private void addSketch(SketchSummary sketch) {
    boolean narrow=getResources().getConfiguration().screenWidthDp<520;
    LinearLayout container=column(); container.setBackgroundColor(surface);
    LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2); card.bottomMargin=dp(1); rows.addView(container,card);
    LinearLayout top=row(); container.addView(top);
    if (!deleted) {
      TextView handle=action("≡",() -> {}); handle.setTextColor(dim); handle.setContentDescription(getString(R.string.sketch_reorder,sketch.name));
      handle.setOnLongClickListener(v -> {
        if (busy || !mutable) return false;
        ClipData clip=ClipData.newPlainText("sketch",Long.toString(sketch.id()));
        if (Build.VERSION.SDK_INT>=24) v.startDragAndDrop(clip,new View.DragShadowBuilder(container),sketch.id(),0);
        else v.startDrag(clip,new View.DragShadowBuilder(container),sketch.id(),0);
        return true;
      }); top.addView(handle,new LinearLayout.LayoutParams(dp(36),dp(72)));
    }
    LinearLayout metadata=column(); metadata.setPadding(dp(8),dp(12),dp(8),dp(12));
    TextView name=label(sketch.name,17,ink); name.setTypeface(null,Typeface.BOLD); metadata.addView(name);
    metadata.addView(label(getString(R.string.sketch_origin_value,sketch.origin()),12,dim));
    top.addView(metadata,new LinearLayout.LayoutParams(0,-2,1.3f));
    LinearLayout tiles=row();
    if (!sketch.available()) {
      TextView unavailable=label(sketch.problem,14,dim); unavailable.setPadding(dp(12),dp(12),dp(12),dp(12)); tiles.addView(unavailable);
    } else if (deleted) {
      tiles.addView(label("Plan · "+profileLabel(sketch),13,dim));
      if (mutable) tiles.addView(action(getString(R.string.sketch_restore),() -> run(() -> { service.setDeleted(sketch.id(),false); return null; },x -> render())));
    } else {
      tiles.addView(tile(sketch,PlotType.PLOT_PLAN,"∿  "+getString(R.string.sketch_plan)),new LinearLayout.LayoutParams(0,dp(76),1));
      tiles.addView(tile(sketch,sketch.profile.type,"⌁  "+profileLabel(sketch)),new LinearLayout.LayoutParams(0,dp(76),1));
    }
    if (!narrow) top.addView(tiles,new LinearLayout.LayoutParams(0,-2,2));
    if (!deleted && mutable && sketch.available()) {
      TextView edit=action("✎",() -> edit(sketch.id())); edit.setContentDescription(getString(R.string.sketch_edit_named,sketch.name));
      top.addView(edit,new LinearLayout.LayoutParams(dp(48),dp(64)));
    }
    if (narrow) container.addView(tiles,new LinearLayout.LayoutParams(-1,-2));
    container.setOnDragListener((v,event) -> {
      if (deleted || !mutable || !(event.getLocalState() instanceof Long)) return false;
      if (event.getAction()==DragEvent.ACTION_DRAG_LOCATION) dragScroll(event.getY()+container.getTop()-scroll.getScrollY());
      if (event.getAction()==DragEvent.ACTION_DRAG_ENTERED) container.setBackgroundColor(cell);
      if (event.getAction()==DragEvent.ACTION_DRAG_EXITED || event.getAction()==DragEvent.ACTION_DRAG_ENDED) container.setBackgroundColor(surface);
      if (event.getAction()==DragEvent.ACTION_DRAG_ENDED) stopDragScroll();
      if (event.getAction()==DragEvent.ACTION_DROP) {
        stopDragScroll();
        long moving=(Long)event.getLocalState(); List<Long> ids=new ArrayList<>();
        for (SketchSummary item : visible) ids.add(item.id());
        int target=ids.indexOf(sketch.id()); ids.remove(moving); ids.add(target,moving);
        run(() -> { service.reorder(ids); return null; },x -> render());
      }
      return true;
    });
  }
  private void dragScroll(float y) {
    int direction=y<dp(64) ? -1 : y>scroll.getHeight()-dp(64) ? 1 : 0;
    if (direction==dragScrollDirection) return;
    stopDragScroll(); dragScrollDirection=direction;
    if (direction!=0) scroll.post(dragScroller);
  }
  private void stopDragScroll() {
    dragScrollDirection=0; if (scroll!=null) scroll.removeCallbacks(dragScroller);
  }
  private String profileLabel(SketchSummary sketch) {
    return sketch.profile.type==PlotType.PLOT_PROJECTED ? getString(R.string.sketch_projected_value,Math.round(sketch.profile.azimuth)) : getString(R.string.sketch_extended);
  }
  private View tile(SketchSummary sketch,long type,String title) {
    long[] recent=service.recent(); boolean selected=recent[0]==sketch.id() && recent[1]==type;
    TextView view=action(title+(selected ? "\n"+getString(R.string.sketch_last_opened) : ""),() -> select(sketch,type));
    view.setContentDescription(getString(type==PlotType.PLOT_PLAN ? R.string.sketch_open_plan : R.string.sketch_open_profile,sketch.name));
    view.setTextColor(ink); view.setBackground(ToolbarVisualStyle.rounded(this,cell,selected ? amber : line,selected ? 2 : 1,0));
    return view;
  }
  private void select(SketchSummary sketch,long type) {
    // The drawing records recent selection after it successfully loads.
    setResult(RESULT_OK,new Intent().putExtra(CURRENT,sketch.id()).putExtra(VIEW,type)); finish();
  }
  private void create() {
    draft=new SketchDraft();
    try { draft.name=service.suggestedName(); }
    catch (IOException e) { draft=null; error(e); return; }
    TopoDroidApp app=(TopoDroidApp)getApplication();
    String origin;
    if (TDSetting.mFixedOrigin) { origin=app.getFirstPlotOrigin(); if (origin==null) origin=app.getFirstStation(); }
    else origin=TDInstance.isDivingMode() ? app.getFirstStation() : app.getCurrentOrLastStation();
    draft.origin=origin==null ? TDSetting.mInitStation : origin; showSheet();
  }
  private void edit(long id) {
    try {
      draft=SketchDraft.editing(service.find(id)); showSheet();
    } catch (IOException e) { error(e); }
  }
  private EditText field(LinearLayout form,int title,String value,int input) {
    form.addView(label(getString(title),13,dim)); EditText edit=new EditText(this); edit.setSingleLine(true); edit.setTextColor(ink);
    edit.setInputType(input); edit.setText(value); form.addView(edit,new LinearLayout.LayoutParams(-1,dp(52))); return edit;
  }
  private void showSheet() {
    sheet=new Dialog(this); sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel=column(); panel.setPadding(dp(16),dp(12),dp(16),dp(12)); panel.setBackgroundColor(surface);
    panel.addView(label(getString(draft.id<0 ? R.string.sketch_new : R.string.sketch_edit),20,ink));
    ScrollView body=new ScrollView(this); LinearLayout form=column(); body.addView(form);
    panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    nameInput=field(form,R.string.sketch_name,draft.name,InputType.TYPE_CLASS_TEXT);
    originInput=field(form,R.string.sketch_origin,draft.origin,InputType.TYPE_CLASS_TEXT);
    nameInput.setId(R.id.edit_plot_name); originInput.setId(R.id.edit_plot_start);
    missingInput=new CheckBox(this); missingInput.setText(R.string.sketch_allow_missing); missingInput.setTextColor(dim);
    missingInput.setChecked(draft.allowMissing); form.addView(missingInput);
    projectionInput=null; azimuthInput=null;
    if (draft.id<0) {
      projectionInput=new Switch(this); projectionInput.setText(R.string.sketch_projected); projectionInput.setTextColor(ink);
      projectionInput.setChecked(draft.projected); projectionInput.setPadding(0,dp(12),0,dp(12)); form.addView(projectionInput);
      LinearLayout projection=column(); form.addView(projection);
      azimuthInput=field(projection,R.string.sketch_azimuth,Integer.toString(draft.azimuth),InputType.TYPE_CLASS_NUMBER);
      projection.addView(action(getString(R.string.sketch_preview),() -> {
        if (!capture()) return;
        try { service.validate(draft); }
        catch (IOException e) { error(e); return; }
        if (TopoDroidApp.mData.selectAllShots(sid,com.topodroid.util.TDStatus.NORMAL).isEmpty()) {
          TDToast.makeBad(R.string.few_data); return;
        }
        new ProjectionDialog(this,sid,draft.origin,draft.azimuth,draft.oblique,(azimuth,oblique) -> {
          draft.azimuth=azimuth; draft.oblique=oblique; azimuthInput.setText(Integer.toString(azimuth));
        }).show();
      }));
      projection.setVisibility(draft.projected ? View.VISIBLE : View.GONE);
      projectionInput.setOnCheckedChangeListener((button,checked) -> projection.setVisibility(checked ? View.VISIBLE : View.GONE));
    } else {
      TextView hint=label(getString(R.string.sketch_origin_explanation),14,dim); hint.setPadding(0,dp(12),0,dp(12)); form.addView(hint);
      TextView delete=action(getString(R.string.sketch_delete),() -> new AlertDialog.Builder(this)
        .setTitle(R.string.sketch_delete).setMessage(R.string.sketch_delete_explanation)
        .setNegativeButton(R.string.button_cancel,null).setPositiveButton(R.string.sketch_delete,(dialog,which) -> {
          long id=draft.id; run(() -> { service.setDeleted(id,true); return null; },x -> closeSheet());
        }).show()); delete.setTextColor(color(R.color.toolbar_danger)); form.addView(delete);
    }
    LinearLayout actions=row(); actions.addView(action(getString(R.string.button_cancel),this::closeSheet),new LinearLayout.LayoutParams(0,dp(48),1));
    actions.addView(action(getString(draft.id<0 ? R.string.sketch_create : R.string.sketch_save),() -> save(false)),new LinearLayout.LayoutParams(0,dp(48),1));
    panel.addView(actions);
    if (draft.id<0) {
      TextView createOpen=action(getString(R.string.sketch_create_open),() -> save(true));
      createOpen.setId(R.id.btn_ok); panel.addView(createOpen);
    }
    sheet.setContentView(panel); sheet.setOnDismissListener(dialog -> { draft=null; sheet=null; }); sheet.show();
    Window window=sheet.getWindow(); window.setBackgroundDrawableResource(android.R.color.transparent);
    window.setGravity(Gravity.BOTTOM); window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(this::sizeSheet);
    sizeSheet();
  }
  private void sizeSheet() {
    if (sheet==null) return;
    Window window=sheet.getWindow();
    android.graphics.Rect visibleArea=new android.graphics.Rect();
    window.getDecorView().getWindowVisibleDisplayFrame(visibleArea);
    int height=(int)(getResources().getDisplayMetrics().heightPixels*0.82f);
    if (visibleArea.height()>0) height=Math.min(height,visibleArea.height());
    int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(680));
    if (window.getAttributes().width!=width || window.getAttributes().height!=height) window.setLayout(width,height);
  }
  private boolean capture() {
    if (draft==null) return false;
    draft.name=nameInput.getText().toString(); draft.origin=originInput.getText().toString(); draft.allowMissing=missingInput.isChecked();
    if (projectionInput!=null) draft.projected=projectionInput.isChecked();
    if (draft.id<0 && draft.projected) {
      try { draft.azimuth=Integer.parseInt(azimuthInput.getText().toString()); }
      catch (NumberFormatException e) { azimuthInput.setError(getString(R.string.sketch_azimuth_error)); return false; }
    }
    return true;
  }
  private void save(boolean open) {
    if (!capture()) return;
    try {
      service.validate(draft);
      if (draft.id>=0 && draft.changesOrigin(service.find(draft.id).origin())) {
        new AlertDialog.Builder(this).setTitle(R.string.sketch_change_origin).setMessage(R.string.sketch_origin_explanation)
          .setNegativeButton(R.string.button_cancel,null).setPositiveButton(R.string.sketch_save,(d,w) -> commit(open)).show();
      } else commit(open);
    } catch (IOException e) { error(e); }
  }
  private void commit(boolean open) {
    SketchDraft saved=draft;
    run(() -> { if (saved.id<0) return service.create(saved); service.edit(saved); return service.find(saved.id); },sketch -> {
      closeSheet(); if (open) select(sketch,PlotType.PLOT_PLAN);
    });
  }
  private void closeSheet() { if (busy) return; if (sheet!=null) sheet.dismiss(); draft=null; render(); }
  private interface Done<T> { void accept(T result); }
  private <T> void run(Callable<T> job,Done<T> done) {
    if (busy) return; busy=true; setEnabled(root,false); if (sheet!=null) { sheet.setCancelable(false); setEnabled(sheet.getWindow().getDecorView(),false); }
    worker.execute(() -> {
      T result=null; Exception failure=null;
      try { result=job.call(); } catch (Exception e) { failure=e; }
      final T value=result; final Exception error=failure;
      runOnUiThread(() -> {
        busy=false; if (isFinishing() || isDestroyed()) return;
        setEnabled(root,true); if (sheet!=null) { sheet.setCancelable(true); setEnabled(sheet.getWindow().getDecorView(),true); }
        if (error!=null) error(error); else done.accept(value);
      });
    });
  }
  private void setEnabled(View view,boolean enabled) {
    view.setEnabled(enabled); if (view instanceof ViewGroup) {
      ViewGroup group=(ViewGroup)view; for (int i=0;i<group.getChildCount();i++) setEnabled(group.getChildAt(i),enabled);
    }
  }
  private void error(Exception error) { new AlertDialog.Builder(this).setTitle(R.string.sketch_error).setMessage(error.getMessage()).setPositiveButton(R.string.button_ok,null).show(); }
  @Override public void onBackPressed() { if (busy) return; if (sheet!=null) closeSheet(); else if (deleted) { deleted=false; render(); } else super.onBackPressed(); }
  @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); render(); sizeSheet(); }
  @Override protected void onSaveInstanceState(Bundle state) {
    state.putBoolean("deleted",deleted);
    if (draft!=null) { capture(); state.putBoolean("draft",true); state.putLong("id",draft.id); state.putString("name",draft.name);
      state.putString("origin",draft.origin); state.putBoolean("projected",draft.projected); state.putBoolean("missing",draft.allowMissing);
      state.putInt("azimuth",draft.azimuth); state.putInt("oblique",draft.oblique); }
    super.onSaveInstanceState(state);
  }
  @Override protected void onDestroy() { stopDragScroll(); worker.shutdown(); super.onDestroy(); }
}
