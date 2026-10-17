"""Offline render of the exact moving boxes exported by checkCouplerVisual.
Usage: python tools/render_coupler_preview.py PATH_TO_CREATE_SLIM_JAR
No Minecraft process or server is started.
"""
import io,json,sys,zipfile
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageEnhance
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/previews'
NAMES={'IRON':'industrial_iron_block','DARK':'dark_metal_block','BRASS':'brass_block'}

def element(lo,hi,material):
    x,y,z=lo;X,Y,Z=hi
    dims={'north':(X-x,Y-y),'south':(X-x,Y-y),'east':(Z-z,Y-y),'west':(Z-z,Y-y),'up':(X-x,Z-z),'down':(X-x,Z-z)}
    return {'from':lo,'to':hi,'faces':{k:{'texture':'#'+material,'uv':[0,0,*v]} for k,v in dims.items()}}

def write_models():
    textures={k.lower():'create:block/'+v for k,v in NAMES.items()}
    textures['particle']=textures['iron']
    base=[element([3,4,12],[13,12,16],'dark'),element([4,5,8],[12,11,14],'iron')]
    model={'textures':textures,'elements':base}
    path=ROOT/'src/main/resources/assets/guidedrailways/models'
    (path/'block/coupler.json').write_text(json.dumps(model,indent=2)+'\n')
    full=list(base)
    for box in json.loads((OUT/'coupler-rest.json').read_text()):
        a=box['min'];b=box['max']
        lo=[(.5-b[0])*16,(.5+a[1])*16,(.75-b[2])*16]
        hi=[(.5-a[0])*16,(.5+b[1])*16,(.75-a[2])*16]
        full.append(element(lo,hi,box['material'].lower()))
    item={'textures':textures,'elements':full,'display':{'gui':{'rotation':[25,135,0],'translation':[0,0,0],'scale':[.9,.9,.9]},'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[1,1,1]}}}
    (path/'item/coupler.json').write_text(json.dumps(item,indent=2)+'\n')

def rotation(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])

write_models()
with zipfile.ZipFile(sys.argv[1]) as archive:
    textures={k:Image.open(io.BytesIO(archive.read('assets/create/textures/block/'+v+'.png'))).convert('RGB') for k,v in NAMES.items()}
font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',22)
small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',17)
canvas=Image.new('RGB',(1800,960),'#171d24');draw=ImageDraw.Draw(canvas)
draw.text((30,20),'GUIDED RAILWAYS  /  Articulating coupler',font=font,fill='#ece9df')
draw.text((30,54),'Actual exported geometry + Create textures | Offline preview, not an in-game capture',font=small,fill='#a9b6c1')
view=np.array([3.,2.5,3.]);view/=np.linalg.norm(view)
right=np.cross([0,1,0],view);right/=np.linalg.norm(right);up=np.cross(view,right)
faces=[([0,2,6,4],[-1,0,0]),([5,7,3,1],[1,0,0]),([0,4,5,1],[0,-1,0]),([3,7,6,2],[0,1,0]),([1,3,2,0],[0,0,-1]),([4,6,7,5],[0,0,1])]
for index,scene in enumerate(json.loads((OUT/'coupler-scenes.json').read_text())):
    panel=Image.new('RGB',(570,390),'#242d36');pd=ImageDraw.Draw(panel)
    pd.text((20,16),scene['name'],font=font,fill='#e6e2d7')
    polygons=[];allpoints=[]
    for part in scene['parts']:
        lo=part['min'];hi=part['max'];r=rotation(part['rotation']);pivot=np.array(part['pivot'])
        local=np.array([[x,y,z] for z in [lo[2],hi[2]] for y in [lo[1],hi[1]] for x in [lo[0],hi[0]]])
        points=local@r.T+pivot;allpoints.extend(points)
        for ids,n in faces:
            normal=r@np.array(n)
            if normal@view<=0:continue
            p=points[ids];p2=np.array([[a@right,-a@up] for a in p])
            # Pixel density follows actual face size instead of stretching a whole texture.
            width=np.linalg.norm(p[3]-p[0]);height=np.linalg.norm(p[1]-p[0])
            shade=.48+.52*max(0,normal@np.array([.3,.85,.43]))
            polygons.append((np.mean(p@view),p2,part['material'],width,height,shade))
    allpoints=np.array(allpoints);project=np.array([[p@right,-p@up] for p in allpoints]);mid=(project.min(0)+project.max(0))/2
    scale=min(470/max(.01,np.ptp(project[:,0])),270/max(.01,np.ptp(project[:,1])))
    for _,poly,mat,w,h,shade in sorted(polygons,key=lambda p:p[0]):
        dst=(poly-mid)*scale+np.array([285,215]);left,top=np.floor(dst.min(0)).astype(int);rightx,bottom=np.ceil(dst.max(0)).astype(int)
        if rightx<=left or bottom<=top:continue
        uv=np.array([[0,h*16],[0,0],[w*16,0],[w*16,h*16]])
        A=np.column_stack([dst[:3],np.ones(3)]);coeff=np.linalg.solve(A,uv[:3])
        a,b,c=coeff[:,0];d,e,f=coeff[:,1]
        texture=ImageEnhance.Brightness(textures[mat]).enhance(shade)
        mapped=texture.transform((rightx-left,bottom-top),Image.Transform.AFFINE,(a,b,a*left+b*top+c,d,e,d*left+e*top+f),resample=Image.Resampling.NEAREST)
        mask=Image.new('L',mapped.size);ImageDraw.Draw(mask).polygon([tuple(p-[left,top]) for p in dst],fill=255)
        panel.paste(mapped,(left,top),mask)
    pd=ImageDraw.Draw(panel);pd.text((20,355),'Housing fixed | drawbar pivots | shared joint tip',font=small,fill='#a9b6c1')
    canvas.paste(panel,(20+(index%3)*600,100+(index//3)*420))
canvas.save(OUT/'coupler-model-preview.png')
print(OUT/'coupler-model-preview.png')
