"""Fit the mounting plate to its yaw-swept footprint, preserving whole-block floor heights."""
import json, math, copy
from pathlib import Path

ROOT = Path('src/main/resources/assets/guidedrailways')
HIDDEN_STEM = 5.742566 / 16

def measure_bearing(frames):
    import numpy as np
    frames = np.asarray(frames)
    if not frames.size:
        return None
    fixed = (np.ptp(frames, axis=0).max(axis=1) < 1e-5).reshape(-1, 4).all(axis=1)
    quads = frames[0].reshape(-1, 4, 3)
    lo, hi = quads.min(axis=1), quads.max(axis=1)
    central = fixed & (lo[:, 0] < 3/16) & (hi[:, 0] > -3/16) & (lo[:, 2] < 3/16) & (hi[:, 2] > -3/16)
    assert central.any(), 'Visible native frame has no stationary central bearing'
    return round(float(hi[central, 1].max()) + 1.0078125, 7)

def fit_mount(row):
    if 'invisible' in row['id']:
        row['supportBottom'] = HIDDEN_STEM
        return
    boxes = row['boxes']
    def distance(lo, hi):
        return max(lo - .5, .5 - hi, 0)
    # A one-block square sweeps a radius sqrt(.5) around its central yaw bearing.
    footprint = [b for b in boxes if distance(b[0], b[3])**2 + distance(b[2], b[5])**2 < .5 - 1e-8]
    ceiling = max(b[4] for b in footprint)
    row['mountTop'] = max(2, math.ceil(row['ride'] - .5 + ceiling + .25 + .125 - 1e-6))
    # Attach to stationary frame geometry, never the peak of a moving handcar lever.
    bearing = row['bearingTop']
    offset = row['mountTop'] - .5 - row['ride']
    row['supportBottom'] = round(bearing - offset - 1/32, 7)
    assert -1 <= row['supportBottom'] < .75, (row['id'], row['supportBottom'])

def write_models(rows):
    template = json.loads((ROOT / 'models/block/bogie_mount.json').read_text())
    variants = {'style=0': {'model': 'guidedrailways:block/bogie_mount'}}
    for row in rows:
        model = copy.deepcopy(template)
        stem = model['elements'][0]
        stem['from'][1] = round(row['supportBottom'] * 16, 7)
        for face in ('north', 'south', 'east', 'west'):
            stem['faces'][face]['uv'] = [5, 0, 11, min(16, (12 - stem['from'][1]))]
        name = f'bogie_mount_{row["index"]}'
        (ROOT / f'models/block/{name}.json').write_text(json.dumps(model, separators=(',', ':'))+'\n', encoding='utf-8')
        variants[f'style={row["index"]}'] = {'model': f'guidedrailways:block/{name}'}
    (ROOT / 'blockstates/bogie_mount.json').write_text(json.dumps({'variants': variants}, indent=2)+'\n', encoding='utf-8')

if __name__ == '__main__':
    path = ROOT / 'geometry/all_bogeys.json'
    rows = json.loads(path.read_text())
    capture_path = Path('build/bogey-capture.json')
    if capture_path.exists():
        captured = {(r['id'], r['size']): r for r in json.loads(capture_path.read_text())}
        for row in rows:
            row['bearingTop'] = measure_bearing(captured[row['id'], row['size']]['frames'])
    for row in rows:
        fit_mount(row)
    path.write_text(json.dumps(rows, separators=(',', ':'))+'\n', encoding='utf-8')
    write_models(rows)
