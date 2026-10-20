"""Pinned public contracts and clean optional compatibility artifact, without launching Minecraft."""
import argparse, hashlib, json
from pathlib import Path
from zipfile import ZipFile
from prepare_addons import FILES
from check_cargo_compat import signatures

p = argparse.ArgumentParser()
p.add_argument('--artifact', required=True, type=Path)
a = p.parse_args()
contracts = {
    'create_bb-1.0.8-1.21.1.jar': {
        'com/weido/create_bb/data/menu/BogieStyleSelectionScreen': [
            'selectedBogeyLcom/weido/create_bb/data/menu/Entry/StyleEntry;',
            'sizeScrollLcom/simibubi/create/foundation/gui/widget/ScrollInput;',
            'onMenuClose()V', '<init>(Lnet/minecraft/core/BlockPos;)V'],
        'com/weido/create_bb/data/menu/Input/SizeScrollInput': [
            'getCurrentSize()Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;'],
        'com/weido/create_bb/data/menu/Entry/StyleMenuHandler': [
            'getStyle(Ljava/util/UUID;)Lnet/createmod/catnip/data/Pair;'],
    },
    'electroenergetics-1.21.1-1.1.4.jar': {
        'com/george_vi/electroenergetics/events/AddToElectricGraphEvent': [
            'builderLcom/george_vi/electroenergetics/simulation/CircuitBuilder;',
            'levelLnet/minecraft/server/level/ServerLevel;'],
        'com/george_vi/electroenergetics/events/FinishElectricSimulationEvent': [
            'resultsLcom/george_vi/electroenergetics/simulation/SimulationResults;',
            'levelLnet/minecraft/server/level/ServerLevel;'],
        'com/george_vi/electroenergetics/content/railway_electrification/pantograph/PantographBlockEntity': ['extendedZ'],
        'com/george_vi/electroenergetics/simulation/CircuitBuilder': [
            'ground(Lcom/george_vi/electroenergetics/foundation/nodes/Node;D)V',
            'connect(Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/simulation/electrical_properties/ElectricalProperties;)V'],
        'com/george_vi/electroenergetics/simulation/SimulationResults': [
            'getVoltageAt(Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;)D',
            'getCurrentThrough(Lcom/george_vi/electroenergetics/foundation/nodes/Node;Lcom/george_vi/electroenergetics/foundation/nodes/Node;)D'],
    },
}
count = 0
for name, url, sha in FILES:
    jar = Path('libs') / name
    assert hashlib.sha256(jar.read_bytes()).hexdigest() == sha, name
    with ZipFile(jar) as z:
        for owner, members in contracts[name].items():
            found = signatures(z.read(owner + '.class'))
            assert set(members) <= found, (owner, set(members) - found)
            count += len(members)
with ZipFile(a.artifact) as z:
    assert not any(n.startswith(('com/weido/', 'com/george_vi/')) for n in z.namelist())
    rows = json.loads(z.read('assets/guidedrailways/geometry/all_bogeys.json'))
    assert len(rows) == 135 and sum(r['id'].startswith('create_bb:') for r in rows) == 96
    assert [r['index'] for r in rows] == list(range(1, 136))
    toml = z.read('META-INF/neoforge.mods.toml').decode()
    assert 'modId = "create_bb"' in toml and 'modId = "electroenergetics"' in toml
print(f'Addon contracts: {count} release signatures, pinned hashes, 96 BnB profiles and clean artifact passed')
