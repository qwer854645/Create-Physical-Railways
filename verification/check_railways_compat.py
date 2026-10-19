"""Verify pinned release contracts without launching a Minecraft server or client."""
import argparse
import hashlib
from pathlib import Path
from zipfile import ZipFile
from check_cargo_compat import signatures

parser = argparse.ArgumentParser()
parser.add_argument('--railways', type=Path, required=True)
parser.add_argument('--create', type=Path, required=True)
parser.add_argument('--artifact', type=Path, required=True)
args = parser.parse_args()
assert hashlib.sha256(args.railways.read_bytes()).hexdigest() == '64b74ddad661deae1cfe7e8334cfa3d39bb8ef7b761f83595ba0476c4a9bb1d5'
count = 0
with ZipFile(args.railways) as rail, ZipFile(args.create) as create, ZipFile(args.artifact) as artifact:
    def check(archive, name, expected):
        global count
        actual = signatures(archive.read(name.replace('.', '/') + '.class'))
        assert set(expected) <= actual, (name, set(expected) - actual)
        count += len(expected)
    prefix = 'com.railwayteam.railways.'
    check(rail, prefix+'mixin_interfaces.ISwitchDisabledEdge', ['isEnabled()Z', 'isAutomatic()Z', 'setAutomaticallySelected()V'])
    check(rail, prefix+'util.MixinVariables', ['temporarilySkipSwitchesZ'])
    check(rail, prefix+'mixin_interfaces.ILimitedGlobalStation', ['isStationEnabled()Z'])
    check(rail, prefix+'registry.CRBogeyStyles', ['MONOBOGEYLcom/simibubi/create/content/trains/bogey/BogeyStyle;'])
    check(rail, prefix+'content.coupling.coupler.TrackCouplerBlockEntity', [
        'getCoupler()Lcom/railwayteam/railways/content/coupling/coupler/TrackCoupler;',
        'getSecondaryCoupler()Lcom/railwayteam/railways/content/coupling/coupler/TrackCoupler;',
        'getAllowedOperationMode()Lcom/railwayteam/railways/content/coupling/coupler/TrackCouplerBlockEntity$AllowedOperationMode;'])
    check(rail, prefix+'content.coupling.coupler.TrackCouplerBlockEntity$AllowedOperationMode', ['canCoupleZ', 'canDecoupleZ'])
    check(create, 'com.simibubi.create.content.trains.bogey.BogeyStyle', [
        'render(Lcom/simibubi/create/content/trains/bogey/BogeySizes$BogeySize;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIFLnet/minecraft/nbt/CompoundTag;Z)V'])
    check(rail, prefix+'content.buffer.TrackBuffer', ['<init>()V'])
    metadata = rail.read('META-INF/neoforge.mods.toml').decode()
    assert '0.3.0-beta.2+neoforge-mc1.21.1' in metadata and '[21.1.233,)' in metadata
    for path in ('frame.obj', 'wheel.obj', 'frame.json', 'wheel.json'):
        assert 'assets/railways/models/block/bogey/monorail/'+path in rail.namelist()
    assert not any(n.startswith(('com/railwayteam/', 'dev/simulated_team/')) for n in artifact.namelist())
print(f'Railways Beta release contracts: {count} signatures, pinned metadata/assets and clean artifact passed')
