"""Derive collision measurements, not render assets, from the pinned Railway OBJ frames."""
import argparse,json,hashlib
from pathlib import Path
from zipfile import ZipFile
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
target=Path('src/main/resources/assets/guidedrailways/geometry/railway_bogeys.json')
profiles={}
with ZipFile('libs/railways-0.3.0-beta.2+neoforge-mc1.21.1.jar') as jar:
    for name in ['freight','passenger','modern','blomberg','y25']:
        source=jar.read('assets/railways/models/block/bogey/'+name+'/frame.obj');groups=[];vertices=[]
        for line in source.decode().splitlines():
            if line.startswith('o '):
                if vertices:groups.append(vertices)
                vertices=[]
            elif line.startswith('v '):vertices.append(list(map(float,line.split()[1:4])))
        if vertices:groups.append(vertices)
        boxes=[]
        for group in groups:
            lo=[min(v[i] for v in group) for i in range(3)];hi=[max(v[i] for v in group) for i in range(3)]
            if all(hi[i]-lo[i]>1e-6 for i in range(3)):boxes.append([round(x,7) for x in lo+hi])
        assert boxes and max(b[4] for b in boxes)==1.5,name
        profiles[name]={'sourceSha256':hashlib.sha256(source).hexdigest(),'frameBoxes':boxes}
text=json.dumps(profiles,indent=2)+'\n'
if a.check:
    assert target.read_text('utf-8')==text,'Bogey collision measurements do not match installed assets'
else:
    target.parent.mkdir(parents=True,exist_ok=True);target.write_text(text,'utf-8')
print('Railway bogey profiles: 5 frame measurements match pinned OBJ assets')
