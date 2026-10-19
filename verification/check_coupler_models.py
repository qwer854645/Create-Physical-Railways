"""Native model contract for the pinned Railway Beta; no game or graphics server is started."""
import argparse,json
from zipfile import ZipFile
p=argparse.ArgumentParser();p.add_argument('--railways',required=True);args=p.parse_args()
models=0
with ZipFile(args.railways) as jar:
    for style,group_counts in {
        'link_and_pin':{'body_and_pin':11},
        'link_and_pin_linkless':{'body_and_pin':11},
        'knuckle_coupler':{'body':12,'knuckle':2},
        'split_knuckle_coupler':{'body':12,'knuckle':5},
        'screwlink_coupler':{'base':10,'hooks':7,'flat_links':6,'screw':4,'shackle':5},
    }.items():
        for mount in ['single_deco','wooden_headstock','copycat_headstock']:
            for flip in ([False] if mount=='single_deco' else [False,True]):
                path=('single_deco/'+style if mount=='single_deco' else 'headstock/'+mount+'_'+style+('_upside_down' if flip else ''))
                data=json.loads(jar.read('assets/railways/models/block/buffer/'+path+'.json'))
                groups={g['name']:g['children'] for g in data['groups'] if isinstance(g,dict)}
                for group,count in group_counts.items():
                    assert group in groups and len(groups[group])==count,(path,group)
                    assert all(isinstance(i,int) and 0<=i<len(data['elements']) for i in groups[group]),path
                if style=='link_and_pin':assert len(groups.get('link',groups.get('pin',[])))==4,path
                if style.startswith('link_and_pin'):
                    pins=[data['elements'][i] for i in groups['body_and_pin'] if data['elements'][i]['from'][0]==7.5 and data['elements'][i]['to'][0]==8.5]
                    assert len(pins)==1 and pins[0]['to'][1]-pins[0]['from'][1]==4,path
                for texture in data['textures'].values():
                    if texture.startswith('railways:'):
                        assert 'assets/railways/textures/'+texture.split(':')[1]+'.png' in jar.namelist(),texture
                models+=1
print(f'Native coupler assets: {models} native variants and mechanical group/texture contracts passed')
