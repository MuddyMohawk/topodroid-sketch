"""Build a local, reviewable expected/candidate/diff sheet. Never changes goldens."""
import html,json,sys
from pathlib import Path
from PIL import Image,ImageChops,ImageFile
approved,stage=map(Path,sys.argv[1:])
manifest=json.loads((stage/'baseline-manifest.json').read_text(encoding='utf-8-sig'))
if isinstance(manifest,dict): manifest=[manifest]
rows=[]
for entry in manifest:
    name=entry['name']
    complete=(approved/name).read_bytes()[-12:]==bytes.fromhex('0000000049454e44ae426082')
    # Reporting may show a clearly labeled partial OLD asset. Validation always
    # rejects incomplete approved PNGs; this never repairs or applies that asset.
    Image.MAX_IMAGE_PIXELS=None
    ImageFile.LOAD_TRUNCATED_IMAGES=not complete
    note='' if complete else ' PRIOR ASSET IS INCOMPLETE (missing PNG IEND).'
    with Image.open(approved/name) as expected,Image.open(stage/name) as candidate:
        if expected.size!=candidate.size and expected.width>candidate.width*4 and expected.height>candidate.height*4:
            note+=f' Review preview sampled from {expected.size} to {candidate.size}; validation exports retain full dimensions.'
            expected=expected.resize(candidate.size,Image.Resampling.NEAREST)
        expected.save(stage/('expected-'+name))
        if expected.size==candidate.size:
            difference=ImageChops.difference(expected.convert('RGB'),candidate.convert('RGB'))
            difference.save(stage/('diff-'+name))
            changed=sum(any(c>8 for c in pixel) for pixel in difference.get_flattened_data())
            ratio=changed/(expected.width*expected.height)
        else: ratio=1
    ImageFile.LOAD_TRUNCATED_IMAGES=False
    rows.append(f'<tr><td>{html.escape(name)}<br>changed={ratio:.6%}<br>{html.escape(note)}</td>'+''.join(f'<td><a href="{html.escape(p+name)}"><img width="380" src="{html.escape(p+name)}"></a></td>' for p in ['expected-','','diff-'])+'</tr>')
(stage/'review.html').write_text('<!doctype html><meta charset="utf-8"><title>Baseline candidates</title><style>body{font:14px system-ui}td{vertical-align:top}img{max-width:30vw}</style><h1>Stable baseline candidates</h1><p>Expected / candidate / pixel difference. No approved files have been changed.</p><table>'+''.join(rows)+'</table>',encoding='utf-8')
