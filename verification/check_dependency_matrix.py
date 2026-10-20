"""Release-jar linkage audit. Reads class files; never starts Minecraft or a server.

--download fetches checksum-locked publisher files into ignored build/dependency-matrix.
Checks actual external references (including inherited members), Mixin targets and native
bogey resources. This cannot establish runtime Mixin application or gameplay acceptance.
"""
import argparse
import hashlib
import io
import json
import re
import struct
import urllib.request
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
PREFIXES = {
    'create': ('com/simibubi/create/',),
    'sable': ('dev/ryanhcode/sable/',),
    'bnb': ('com/weido/create_bb/',),
    'cee': ('com/george_vi/electroenergetics/',),
    'railways': ('com/railwayteam/railways/',),
    'hose': ('com/mk2525/vsfluidlink/',),
    'logistics': ('net/sprocketgames/create_aeronautics_automated_logistics/',),
    'aero': ('dev/simulated_team/simulated/',),
}


def read_class(data):
    stream = io.BytesIO(data)
    def read(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    assert read('I') == 0xCAFEBABE
    stream.read(4)
    pool = [None] * read('H')
    i = 1
    while i < len(pool):
        tag = read('B')
        if tag == 1:
            pool[i] = (tag, stream.read(read('H')).decode('utf-8', errors='replace'))
        elif tag in (7, 8, 16, 19, 20):
            pool[i] = (tag, read('H'))
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[i] = (tag, read('H'), read('H'))
        elif tag in (3, 4):
            stream.read(4)
        elif tag in (5, 6):
            stream.read(8)
            i += 1
        elif tag == 15:
            stream.read(3)
        else:
            raise ValueError(tag)
        i += 1
    def utf(index):
        return pool[index][1]
    def cls(index):
        return utf(pool[index][1]) if index else None
    flags, this, parent = read('H'), read('H'), read('H')
    parents = [cls(parent)] if parent else []
    parents += [cls(read('H')) for _ in range(read('H'))]
    members = {}
    for _ in range(2):
        for _ in range(read('H')):
            access, name, desc = read('H'), utf(read('H')), utf(read('H'))
            members[name + desc] = access
            for _ in range(read('H')):
                stream.read(2)
                stream.read(read('I'))
    refs, classes = set(), set()
    for value in pool:
        if not value:
            continue
        if value[0] in (9, 10, 11):
            nt = pool[value[2]]
            refs.add((cls(value[1]), utf(nt[1]) + utf(nt[2])))
        if value[0] == 7:
            classes.add(utf(value[1]))
        if value[0] == 1:
            classes.update(re.findall(r'L([\w/$]+);', value[1]))
    return dict(name=cls(this), parents=parents, members=members, refs=refs, classes=classes)


class Archive:
    def __init__(self, path):
        self.files, self.cache = {}, {}
        self.archives = []
        def add(z):
            self.archives.append(z)
            for name in z.namelist():
                if name.endswith('.class'):
                    self.files.setdefault(name[:-6], (z, name))
                elif name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
                    add(ZipFile(io.BytesIO(z.read(name))))
        add(ZipFile(path))

    def get(self, name):
        if name not in self.cache:
            value = self.files.get(name)
            self.cache[name] = read_class(value[0].read(value[1])) if value else None
        return self.cache[name]

    def member(self, owner, signature, seen=None):
        seen = set() if seen is None else seen
        if owner in seen:
            return None
        seen.add(owner)
        c = self.get(owner)
        if c is None:
            return None
        if signature in c['members']:
            return c['members'][signature]
        if signature.startswith('<init>'):
            return None
        for parent in c['parents']:
            found = self.member(parent, signature, seen)
            if found is not None:
                return found
        return None


def mixin_contracts(mod, own):
    # Private Sable implementation hooks are just as important as public APIs.
    if mod == 'sable':
        p = 'dev/ryanhcode/sable/physics/impl/rapier/'
        return {
            p+'RapierPhysicsPipeline': [
                'levelLnet/minecraft/server/level/ServerLevel;',
                'activeContraptionsLit/unimi/dsi/fastutil/objects/Object2ObjectMap;',
                'prePhysicsTicks()V', 'processCollisionEffects()V'],
            p+'RapierPhysicsPipeline$TrackedKinematicContraption': ['idI'],
            p+'Rapier3D': ['clearCollisions(J)[D'],
            'dev/ryanhcode/sable/mixin/block_placement/BlockPlaceContextMixin': [
                'sable$intersectBlocks(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;Ldev/ryanhcode/sable/sublevel/SubLevel;Ldev/ryanhcode/sable/companion/math/BoundingBox3dc;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;Lorg/joml/Vector3d;Lorg/joml/Quaterniond;)Z'],
        }
    # Read required Mixin fields/methods from the pinned baseline declarations.
    source = ROOT / 'src/main/java/com/guidedrailways/mixin'
    result = {}
    plugin = (source / 'CargoCompatPlugin.java').read_text('utf-8')
    hooks = {name: re.findall(r'"([^"]+)"', body) for name, body in re.findall(
        r'"(\w+Mixin)"\s*,\s*Set\.of\(((?:\s|,|"(?:[^"\\]|\\.)*")*)\)', plugin)}
    for file in source.glob('*.java'):
        text = file.read_text('utf-8')
        match = re.search(r'@Mixin\(\s*targets\s*=\s*("[^"]+"|\{[^}]+\})', text)
        if not match:
            continue
        owners = [s.replace('.', '/') for s in re.findall(r'"([^"]+)"', match[1])]
        owners = [s for s in owners if s.startswith(PREFIXES[mod])]
        if not owners:
            continue
        # Method names and @Shadow descriptors are resolved from the latest release below.
        names = set(hooks.get(file.stem, []))
        names.update(re.findall(r'@(?:Inject|ModifyArg|ModifyExpressionValue)\(\s*method\s*=\s*"([^"]+)"', text))
        for annotation in re.findall(r'@Inject\((.*?)\s+(?:private|public|protected)', text, re.S):
            if re.search(r'require\s*=\s*0', annotation):
                method = re.search(r'method\s*=\s*"([^"]+)"', annotation)
                if method:
                    names.discard(method[1])
        compiled = next(c for c in own if c['name'].endswith('/'+file.stem))
        for shadow in re.findall(r'@Shadow\b([^;]+);', text):
            name = re.search(r'(\w+)\s*$', shadow)
            if name:
                names.update(m for m in compiled['members'] if m.startswith(name[1]) and '(' not in m)
        for owner in owners:
            result.setdefault(owner, set()).update(names)
        for called, signature in re.findall(r'target\s*=\s*"L([^;]+);([^"]+)"', text):
            if called.startswith(PREFIXES[mod]):
                result.setdefault(called, set()).add(signature)
    if mod == 'hose':
        p = 'com/mk2525/vsfluidlink/content/'
        for name in ['ItemHoseConnecotor/ItemHoseConnectorBlockEntity',
                     'ItemMagnetHoseConnector/ItemMagnetHoseConnectorBlockEntity']:
            result.setdefault(p+name, set()).add('getInventory()Lnet/neoforged/neoforge/items/ItemStackHandler;')
        result.setdefault(p+'HoseConnector/HoseConnectorBlockEntity', set()).add(
            'getTank()Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;')
    if mod == 'aero':
        p = 'dev/simulated_team/simulated/'
        result.setdefault(p+'content/blocks/docking_connector/DockingConnectorBlockEntity', set()).update({
            'inventoryLdev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorSoloInventory;',
            'tankLdev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorTank;',
            'getOtherConnector()Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;',
            'isLocked()Z'})
        result[p+'multiloader/tanks/SingleTank'] = {'amountJ', 'capacityJ',
            'typeLdev/simulated_team/simulated/multiloader/tanks/CFluidType;'}
        result[p+'multiloader/tanks/CFluidType'] = {'fluidLnet/minecraft/world/level/material/Fluid;'}
    if mod == 'logistics':
        p = 'net/sprocketgames/create_aeronautics_automated_logistics/'
        result[p+'cargo/LinkedCargoEntry'] = {
            '<init>(Lnet/minecraft/core/BlockPos;ZZ)V', 'pos()Lnet/minecraft/core/BlockPos;',
            'itemStorage()Z', 'fluidStorage()Z'}
        result.setdefault(p+'dock/DockTransferSnapshot$ConnectorContents', set()).update({
            '<init>(Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;IJ)V',
            'items()Ljava/util/List;', 'fluids()Ljava/util/List;', 'itemStacks()Ljava/util/List;',
            'fluidStacks()Ljava/util/List;', 'itemCount()I', 'fluidAmount()J'})
    return result


def geometry(archive, mod):
    namespace = {'bnb': 'create_bb', 'railways': 'railways'}.get(mod)
    if namespace is None:
        return {}
    z = archive.archives[0]
    result = {}
    for name in z.namelist():
        if not name.startswith('assets/'+namespace+'/models/') or 'bog' not in name:
            continue
        if name.endswith('.obj'):
            result[name] = z.read(name)
        elif name.endswith('.json'):
            data = json.loads(z.read(name))
            data.pop('textures', None)  # texture-only fixes do not change collision geometry
            result[name] = data
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, required=True)
    parser.add_argument('--download', action='store_true')
    args = parser.parse_args()
    rows = json.loads((ROOT / 'verification/dependency-releases.json').read_text('utf-8'))
    folder = ROOT / 'build/dependency-matrix'
    folder.mkdir(exist_ok=True)
    for row in rows:
        path = folder / row['file']
        if not path.exists() and args.download:
            path.write_bytes(urllib.request.urlopen(row['url'], timeout=60).read())
        assert hashlib.sha256(path.read_bytes()).hexdigest() == row['sha256'], path
    with ZipFile(args.artifact) as z:
        own = [read_class(z.read(n)) for n in z.namelist() if n.endswith('.class')]
    refs = set().union(*(c['refs'] for c in own))
    classes = set().union(*(c['classes'] for c in own))
    baseline = {m: Archive(folder / next(r['file'] for r in rows if r['mod'] == m)) for m in PREFIXES}
    report = []
    for row in rows:
        mod, prefixes = row['mod'], PREFIXES[row['mod']]
        current, latest = Archive(folder / row['file']), baseline[mod]
        missing = set()
        checked = 0
        for c in classes:
            if c.startswith(prefixes) and latest.get(c):
                checked += 1
                if current.get(c) is None:
                    missing.add('class '+c)
        for owner, member in refs:
            if not owner.startswith(prefixes):
                continue
            access = latest.member(owner, member)
            if access is None:  # inherited Minecraft/NeoForge methods checked by compilation
                continue
            checked += 1
            candidate = current.member(owner, member)
            if (candidate is None or (candidate & 8) != (access & 8)
                    or access & 1 and not candidate & 1
                    or access & 4 and not candidate & 5):
                missing.add(owner+'.'+member)
        for owner, members in mixin_contracts(mod, own).items():
            for member in members:
                if '(' in member or member.endswith(';') or re.search(r'[ZBCSIJFD]$', member):
                    expected = [member]
                else:
                    base = latest.get(owner)
                    expected = [m for m in base['members'] if m.startswith(member+'(')] if base else []
                for signature in expected:
                    checked += 1
                    c = current.get(owner)
                    if c is None or signature not in c['members']:
                        missing.add('mixin '+owner+'.'+signature)
        if mod == 'cee':
            config = current.get('com/george_vi/electroenergetics/config/CResistances')
            config_members = config['members'] if config else {}
            suffix = 'Lcom/george_vi/electroenergetics/config/CResistances$ConfigDouble;'
            power = ['electricTrain'+s+'PowerConsumption'+suffix for s in ['Acceleration', 'Cruise']]
            resistance = ['electricTrain'+s+'Resistance'+suffix for s in ['Acceleration', 'Cruise']]
            checked += 2
            if not (all(f in config_members for f in power) or all(f in config_members for f in resistance)):
                missing.add('CEE train load configuration pair')
        if mod == 'sable':
            hook = 'dev/ryanhcode/sable/mixin/block_placement/BlockPlaceContextMixin.class'
            checked += 1
            if current.archives[0].read(hook) != latest.archives[0].read(hook):
                missing.add('Sable placement helper bytecode changed; review injection and locals')
        base_models, models = geometry(latest, mod), geometry(current, mod)
        changed_models = [n for n, data in base_models.items() if models.get(n) != data]
        result = dict(mod=mod, version=row['version'], checks=checked,
                      linkage=not missing, geometry=not changed_models,
                      missing=sorted(missing), changedModels=changed_models)
        report.append(result)
        print(mod, row['version'], 'PASS' if not missing else 'FAIL', checked,
              '; '.join(sorted(missing)[:2]), 'changed models', len(changed_models))
    (ROOT / 'build/dependency-matrix-report.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    # Every selected latest release must pass. Older failures identify safe lower bounds.
    for mod in PREFIXES:
        assert next(r for r in report if r['mod'] == mod)['linkage'], mod
    policy = json.loads((ROOT / 'verification/dependency-policy.json').read_text('utf-8'))
    for mod, versions in policy['supported'].items():
        for version in versions:
            r = next(r for r in report if r['mod'] == mod and r['version'] == version)
            assert r['linkage'] and r['geometry'], (mod, version, r['missing'], r['changedModels'])
    print('Dependency audit:', len(rows), 'release jars,',
          sum(map(len, policy['supported'].values())), 'supported releases passed')


if __name__ == '__main__':
    main()
