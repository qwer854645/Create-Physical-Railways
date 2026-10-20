"""Verify the complete pinned native catalog and optionally its captured animation surface coverage."""
import argparse,json,hashlib,math,itertools
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--capture');a=p.parse_args()
rows=json.loads(Path('src/main/resources/assets/guidedrailways/geometry/all_bogeys.json').read_text())
sha=hashlib.sha256(Path('libs/railways-0.3.0-beta.2+neoforge-mc1.21.1.jar').read_bytes()).hexdigest()
assert len(rows)==39
assert [r['id'] for r in rows[:5]]==['railways:freight','railways:passenger','railways:modern','railways:blomberg','railways:y25']
keys=set()
for index,r in enumerate(rows,1):
    assert r['index']==index and r['sourceJarSha256']==sha
    key=(r['id'],r['size']);assert key not in keys;keys.add(key)
    assert r['half']>=.5 and r['tread']>0 and all(math.isfinite(r[k])for k in ['ride','renderY','tread','half'])
    for b in r['boxes']:assert all(math.isfinite(v)for v in b)and all(b[i+3]>b[i]for i in range(3))
    assert len({tuple(math.floor(v)for v in b[:3])for b in r['boxes']})<127
    assert r['renderY']==1.0078125,'native BogeyStyle render origin compensation missing'
    assert isinstance(r['mountTop'],int) and r['mountTop']<=r['legacyMountTop']
    assert abs(r['ride']+(r['mountTop']-.5-r['ride'])+.5-r['mountTop'])<1e-8
assert next(r for r in rows if r['id']=='railways:handcar')['animationRatio']==2
if a.capture:
    capture={(r['id'],r['size']):r for r in json.loads(Path(a.capture).read_text())};assert set(capture)==keys
    samples=0
    for r in rows:
        from fit_bogey_mounts import measure_bearing
        assert r['bearingTop']==measure_bearing(capture[(r['id'],r['size'])]['frames'])
        occupied=set()
        for b in r['boxes']:
            lo=[round(v*16)for v in b[:3]];hi=[round(v*16)for v in b[3:]]
            occupied.update(itertools.product(*(range(lo[i],hi[i])for i in range(3))))
        for frame in capture[(r['id'],r['size'])]['frames']:
            for point in frame[::8]:
                grid=[(point[0]+.5)*16,(point[1]+r['renderY'])*16,(point[2]+.5)*16]
                choices=[[math.floor(v)]if abs(v-round(v))>1e-3 else [round(v)-1,round(v)]for v in grid]
                assert any(q in occupied for q in itertools.product(*choices)),(r['id'],r['size'],point)
                samples+=1
    print(f'Animation surface coverage: {samples} samples across 12 phases per native profile passed')
print('Complete native bogey catalog: 39 style/size profiles, save indices, source hash and collider capacity passed')
