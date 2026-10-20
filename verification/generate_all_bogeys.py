"""Build common-side collision measurements from a main-menu capture of native animated renderers."""
import json,math,collections,argparse,hashlib
from pathlib import Path
import numpy as np
p=argparse.ArgumentParser();p.add_argument('--input',default='build/bogey-capture.json');a=p.parse_args()
rows=json.loads(Path(a.input).read_text());out=[]
legacy=['railways:freight','railways:passenger','railways:modern','railways:blomberg','railways:y25']
rows.sort(key=lambda r:(0,legacy.index(r['id'])) if r['id'] in legacy else (1,r['id']+'|'+r['size']))
S=16;RENDER_Y=1.0078125
for index,row in enumerate(rows,1):
    frames=np.array(row.pop('frames'));vertices=frames[0] if frames.size else np.empty((0,3));circles=[]
    if frames.size:
        mean=frames.mean(axis=0);r=np.linalg.norm(frames[:,:,1:]-mean[None,:,1:],axis=2)
        valid=(np.ptp(frames[:,:,0],axis=0)<1e-4)&(r.mean(axis=0)>.06)&(np.ptp(r,axis=0)<.003)
        clusters=collections.defaultdict(list)
        for i in np.where(valid)[0]:clusters[tuple(np.round(mean[i,1:],4))].append(i)
        count=max(map(len,clusters.values()),default=0)
        circles=[(centre,ids) for centre,ids in clusters.items() if len(ids)>=max(100,count*.5)]
    if circles:
        centre,ids=max(circles,key=lambda c:len(c[1]));cy=float(centre[0]);maxr=float(r[:,ids].max());xs=collections.defaultdict(list)
        for i in ids:
            if r[:,i].mean()>.7*maxr:xs[round(abs(float(mean[i,0])),4)].append(i)
        ids=xs[max(xs)] if xs else ids
        points=vertices[ids][:,1:]-np.array(centre);angles={round(math.atan2(z,y)%(2*math.pi),4) for y,z in points if math.hypot(y,z)>.001}
        # Edge midpoints and polygon corners have different radii; use the convex hull's
        # minimum support distance, not the maximum vertex radius times a guessed segment count.
        pts=sorted(set((round(float(y),6),round(float(z),6)) for y,z in points))
        def cross(o,a,b):return (a[0]-o[0])*(b[1]-o[1])-(a[1]-o[1])*(b[0]-o[0])
        lower=[];upper=[]
        for q in pts:
            while len(lower)>=2 and cross(lower[-2],lower[-1],q)<=1e-9:lower.pop()
            lower.append(q)
        for q in reversed(pts):
            while len(upper)>=2 and cross(upper[-2],upper[-1],q)<=1e-9:upper.pop()
            upper.append(q)
        hull=lower[:-1]+upper[:-1]
        tread=min(abs(q[0]*w[1]-q[1]*w[0])/math.dist(q,w) for q,w in zip(hull,hull[1:]+hull[:1]))
        half=max(.5,row['spacing']/2,max(abs(float(c[0][1])) for c in circles))
    else:cy=-.7578125;tread=row['wheelRadius']*.9537;half=max(.5,row['spacing']/2)
    mono=row['gauge']=='railways:monorail'
    if mono:tread=.375
    ride=1 if mono else .346484375+tread+.5-RENDER_Y-cy
    ymax=float(frames[:,:,1].max()) if frames.size else -.0078125
    # The mounting plate's underside is mountTop-.25 above the rail graph.
    top=max(2,math.ceil(ride+RENDER_Y+ymax-.5+.375-1e-6))
    occupied=set()
    if frames.size:
        quads=frames.reshape(-1,4,3);lo=quads.min(axis=1);hi=quads.max(axis=1);lo+=np.array([.5,RENDER_Y,.5]);hi+=np.array([.5,RENDER_Y,.5])
        bounds=set()
        for low,high in zip(lo,hi):
            b=np.floor(low*S+1e-4).astype(int);e=np.ceil(high*S-1e-4).astype(int);e=np.maximum(e,b+1)
            bounds.add(tuple(b)+tuple(e))
        for x,y,z,X,Y,Z in bounds:
            occupied.update((i,j,k) for i in range(x,X) for j in range(y,Y) for k in range(z,Z))
    else:occupied.update((i,j,k) for i in range(6,10) for j in range(12,16) for k in range(6,10))
    boxes=[]
    while occupied:
        x,y,z=min(occupied);X=x+1
        while (X,y,z) in occupied and X//S==x//S:X+=1
        Y=y+1
        while Y//S==y//S and all((i,Y,z)in occupied for i in range(x,X)):Y+=1
        Z=z+1
        while Z//S==z//S and all((i,j,Z)in occupied for i in range(x,X)for j in range(y,Y)):Z+=1
        occupied.difference_update((i,j,k)for i in range(x,X)for j in range(y,Y)for k in range(z,Z))
        boxes.append([v/S for v in [x,y,z,X,Y,Z]])
    cells={(math.floor(b[0]),math.floor(b[1]),math.floor(b[2]))for b in boxes}
    # Always reserve a physical central cell for the frame BE and mounting bearing.
    if (0,0,0) not in cells:boxes.append([.375,.75,.375,.625,1,.625]);cells.add((0,0,0))
    assert len(cells)<127,(row['id'],len(cells))
    row.update(index=index,renderY=RENDER_Y,tread=round(max(.05,tread),7),ride=round(ride,7),half=round(half,7),mountTop=top,animationRatio=2 if row['id']=='railways:handcar' else 1,sourceJarSha256=hashlib.sha256(Path('libs/railways-0.3.0-beta.2+neoforge-mc1.21.1.jar').read_bytes()).hexdigest(),boxes=boxes)
    out.append(row);print(index,row['id'],row['size'],'ride',round(ride,3),'tread',round(tread,3),'half',half,'cells',len(cells),'boxes',len(boxes))
dest=Path('src/main/resources/assets/guidedrailways/geometry/all_bogeys.json');dest.write_text(json.dumps(out,separators=(',',':'))+'\n','utf-8')
print('Wrote',len(out),'native style/size profiles',dest)
mass_path=Path('src/main/resources/data/guidedrailways/physics_block_properties/bogie_collision.json')
mass=json.loads(mass_path.read_text());mass['overrides']={('style=0,'+k if not k.startswith('style=') else k):v for k,v in mass['overrides'].items() if not k.startswith('style=') or k.startswith('style=0,')}
for row in out:
    cells=sorted({tuple(math.floor(v)for v in b[:3])for b in row['boxes']},key=lambda c:(c[1],c[0],c[2]));part=0
    for cell in cells:
        if cell==(0,0,0):continue
        while part==13:part+=1
        mass['overrides'][f'style={row["index"]},part={part}']={'sable:mass':round(32/max(1,len(cells)-1),9)};part+=1
mass_path.write_text(json.dumps(mass,indent=2)+'\n')
