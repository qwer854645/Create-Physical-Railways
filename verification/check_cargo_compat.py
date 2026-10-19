"""Check optional compatibility contracts against RELEASE jars without loading Minecraft.

Usage: python verification/check_cargo_compat.py --hose FILE --simulated FILE --logistics FILE
This validates bytecode signatures, not Mixin application or in-game transport.
"""
import argparse
import hashlib
import io
import re
import struct
from pathlib import Path
from zipfile import ZipFile


def signatures(data):
    stream = io.BytesIO(data)
    def read(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    assert read('I') == 0xCAFEBABE
    stream.read(4)
    pool = [None] * read('H')
    index = 1
    while index < len(pool):
        tag = read('B')
        if tag == 1:
            pool[index] = stream.read(read('H')).decode('utf-8', errors='replace')
        elif tag in (7, 8, 16, 19, 20):
            stream.read(2)
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            stream.read(4)
        elif tag in (5, 6):
            stream.read(8)
            index += 1
        elif tag == 15:
            stream.read(3)
        else:
            raise AssertionError(f'unknown constant pool tag {tag}')
        index += 1
    stream.read(6)
    stream.read(read('H') * 2)
    found = set()
    for _ in range(2):  # fields then methods
        for _ in range(read('H')):
            stream.read(2)
            name, desc = pool[read('H')], pool[read('H')]
            found.add(name + desc)
            for _ in range(read('H')):
                stream.read(2)
                stream.read(read('I'))
    return found


def main():
    parser = argparse.ArgumentParser()
    for mod in ('hose', 'simulated', 'logistics'):
        parser.add_argument('--' + mod, required=True, type=Path)
    parser.add_argument('--railways', type=Path)
    args = parser.parse_args()
    archives = {key: ZipFile(getattr(args, key)) for key in ('hose', 'simulated', 'logistics')}
    if args.railways:
        archives['railways'] = ZipFile(args.railways)
    root = Path(__file__).resolve().parents[1]
    source = root / 'src/main/java/com/guidedrailways/mixin'
    plugin = (source / 'CargoCompatPlugin.java').read_text('utf-8')
    count = 0
    def check(cls, expected):
        nonlocal count
        mod = 'railways' if cls.startswith('com.railwayteam.') else 'hose' if cls.startswith('com.mk2525.') else 'simulated' if cls.startswith('dev.simulated_team.') else 'logistics'
        if mod not in archives:
            print(f'Skipped {cls}: supply --{mod} to verify this optional adapter')
            return
        actual = signatures(archives[mod].read(cls.replace('.', '/') + '.class'))
        missing = set(expected) - actual
        assert not missing, f'{cls}: missing {sorted(missing)}'
        count += len(expected)
    for name, body in re.findall(r'"(\w+Mixin)",Set\.of\((.*?)\)(?:,|\);)', plugin):
        expected = re.findall(r'"([^"]+)"', body)
        mixin = (source / (name + '.java')).read_text('utf-8')
        target = re.search(r'@Mixin\(targets=("[^"]+"|\{[^}]+\})', mixin).group(1)
        for cls in re.findall(r'"([^"]+)"', target):
            check(cls, expected)
    check('dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity', [
        'inventoryLdev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorSoloInventory;',
        'tankLdev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorTank;',
        'getOtherConnector()Ldev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity;', 'isLocked()Z'])
    check('dev.simulated_team.simulated.multiloader.tanks.SingleTank', ['amountJ', 'capacityJ', 'typeLdev/simulated_team/simulated/multiloader/tanks/CFluidType;'])
    check('dev.simulated_team.simulated.multiloader.tanks.CFluidType', ['fluidLnet/minecraft/world/level/material/Fluid;'])
    for cls in ('ItemHoseConnecotor.ItemHoseConnectorBlockEntity', 'ItemMagnetHoseConnector.ItemMagnetHoseConnectorBlockEntity'):
        check('com.mk2525.vsfluidlink.content.' + cls, ['getInventory()Lnet/neoforged/neoforge/items/ItemStackHandler;'])
    check('com.mk2525.vsfluidlink.content.HoseConnector.HoseConnectorBlockEntity', ['getTank()Lnet/neoforged/neoforge/fluids/capability/templates/FluidTank;'])
    check('net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry', [
        '<init>(Lnet/minecraft/core/BlockPos;ZZ)V', 'pos()Lnet/minecraft/core/BlockPos;', 'itemStorage()Z', 'fluidStorage()Z'])
    check('net.sprocketgames.create_aeronautics_automated_logistics.dock.DockTransferSnapshot$ConnectorContents', [
        '<init>(Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;IJ)V', 'items()Ljava/util/List;',
        'fluids()Ljava/util/List;', 'itemStacks()Ljava/util/List;', 'fluidStacks()Ljava/util/List;', 'itemCount()I', 'fluidAmount()J'])
    assert count >= 30, 'all optional hook declarations must be checked'
    print(f'Cargo release contracts: {count} signatures passed')
    for key in archives:
        path = getattr(args, key)
        print(key, hashlib.sha256(path.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
