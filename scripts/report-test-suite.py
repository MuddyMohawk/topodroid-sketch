"""Render the runner's complete method accounting; no inferred passes."""
import csv,html,json,sys
from pathlib import Path
root=Path(sys.argv[1])
def read(name,default):
    p=root/name
    return json.loads(p.read_text(encoding='utf-8-sig')) if p.exists() else default
rows=read('results.json',[]) or []
if isinstance(rows,dict):rows=[rows]
timings=read('timings.json',{})
request=read('request.json',{})
def esc(value):return html.escape(str(value))
def duration(row):
    value=row.get('seconds')
    return 'unavailable' if value is None or (not value and row['status'] in ('BLOCKED','NOT_REQUESTED')) else f'{value:.3f}'
table=[]
for row in rows:
    ident=row['id'];status=row['status']
    table.append('<tr data-status="'+esc(status)+'"><td>'+esc(ident)+f"<br>attempt {row.get('iteration',1)}"+'</td><td>'+esc(row.get('device','emulator/JVM'))+'</td><td>'+esc(', '.join(row.get('features',[])))+'</td><td>'+esc(row['tier'])+'</td><td>'+esc(status)+'</td><td>'+duration(row)+'</td><td><pre>'+esc(row.get('note',''))+'</pre></td></tr>')
counts={s:sum(r['status']==s for r in rows) for s in sorted({r['status'] for r in rows})}
text='\n'.join(f"{r['status']:14} {duration(r):>12} {r['tier']:12} {r['id']}\n{r.get('note','')}" for r in rows)
(root/'test-results.txt').write_text(text,encoding='utf-8')
issues=(root/'issues.txt').read_text(encoding='utf-8-sig') if (root/'issues.txt').exists() else ''
page='''<!doctype html><meta charset="utf-8"><title>Test suite results</title>
<style>body{font:14px system-ui;margin:24px}table{border-collapse:collapse;width:100%}td,th{text-align:left;padding:8px;vertical-align:top;border-bottom:1px solid #ccc}pre{white-space:pre-wrap;max-width:65vw;font-size:12px}tr[data-status=FAILED],tr[data-status=BLOCKED]{background:#ffeded}input{padding:8px;width:65vw}</style>
<h1>Complete test accounting</h1>'''
page+='<p>'+esc(counts)+'</p><p>Timings (seconds): '+esc(timings)+'</p><pre>'+esc(issues)+'</pre>'
page+='<p>Suite: '+esc(request.get('suite',''))+'; features: '+esc(', '.join(request.get('features',[])) or 'all features')+'. Feature selections include matching JVM and Android methods; other discovered methods remain NOT_REQUESTED. <a href="command.json">Actual whole-command start, end and elapsed time</a>.</p>'
if request.get('resumeFrom'):
    page+='<p>Resumed unfinished checks from '+esc(request['resumeFrom'])+'. Completed passes, failures, and measurements were retained with their original durations; only unfinished checks ran here. APK hashes must match. See the source directory for original raw logs and timings.</p>'
page+='<p><a href="results.json">JSON</a> · <a href="results.csv">CSV</a> · <a href="test-results.txt">Text</a> · <a href="apks.txt">Exact APKs</a></p>'
page+='<input id="search" placeholder="Filter by feature, test, tier, status, or note"><table><thead><tr><th>Method</th><th>Device</th><th>Features</th><th>Tier</th><th>Status</th><th>Test seconds</th><th>Notes / failure</th></tr></thead><tbody>'+''.join(table)+'</tbody></table>'
page+='''<script>document.getElementById('search').oninput=function(){let q=this.value.toLowerCase();document.querySelectorAll('tbody tr').forEach(r=>r.hidden=!r.textContent.toLowerCase().includes(q));}</script>'''
(root/'report.html').write_text(page,encoding='utf-8')
