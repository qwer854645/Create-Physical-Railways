"""Build our small, deterministic vanilla structure templates (no game required)."""

import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src/main/resources/assets/guidedrailways/ponder"


def tag_type(value):
    if isinstance(value, str):
        return 8
    if isinstance(value, list):
        return 9
    if isinstance(value, dict):
        return 10
    return 5 if isinstance(value, float) else 3


def string(value):
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def payload(value):
    kind = tag_type(value)
    if kind == 8:
        return string(value)
    if kind == 9:
        element = tag_type(value[0]) if value else 10
        assert all(tag_type(v) == element for v in value)
        return bytes([element]) + struct.pack(">i", len(value)) + b"".join(map(payload, value))
    if kind == 10:
        return b"".join(bytes([tag_type(v)]) + string(k) + payload(v) for k, v in value.items()) + b"\0"
    return struct.pack(">f" if kind == 5 else ">i", value)


def build(name):
    cells = {}

    def block(pos, name, props=None, nbt=None):
        state = {"Name": name}
        if props:
            state["Properties"] = props
        cells[pos] = (state, nbt)

    for x in range(9):
        for z in range(9):
            block((x, 0, z), "minecraft:smooth_stone")
    for z in range(9):
        block((4, 1, z), "create:track", {"shape": "zo", "turn": "false", "waterlogged": "false"})
    block((4, 5, 4), "guidedrailways:bogie_frame", {"style": "0"},
          {"id": "guidedrailways:bogie_frame", "WheelDistance": 0.0, "LinearSpeed": 0.0})
    block((4, 2, 4), "guidedrailways:bogie_mount", {"style": "0", "compact": "false"})
    for x in range(3, 6):
        for z in range(3, 6):
            block((x, 3, z), "minecraft:oak_planks")
    if name in ("physical_bogey", "physical_controls"):
        block((4, 3, 4), "create:shaft", {"axis": "y"})
        block((4, 4, 4), "create:creative_motor", {"facing": "down"},
              {"id": "create:creative_motor", "Speed": 64.0})
    if name != "physical_bogey":
        block((4, 4, 3), "guidedrailways:driver_console", {"facing": "south"},
              {"id": "guidedrailways:driver_console", "VisualNotch": -4, "Direction": 1})
    if name == "electric_controls":
        for z, facing in [(4, "north"), (5, "south")]:
            block((4, 4, z), "electroenergetics:pantograph", {"facing": facing, "double": "true"},
                  {"id": "electroenergetics:pantograph", "Extended": 0})
        for z in [1, 7]:
            for y in range(1, 7):
                block((2, y, z), "minecraft:iron_bars")
            block((3, 6, z), "minecraft:iron_bars")
            block((4, 6, z), "electroenergetics:catenary_holder", {"style": "standard"})
        block((1, 1, 1), "electroenergetics:creative_battery", {"facing": "south", "ac": "false"})
        block((0, 1, 1), "electroenergetics:ground_rod")
    if name == "rail_transfer":
        for y in range(1, 4):
            block((1, y, 4), "create:andesite_casing")
        block((1, 4, 4), "guidedrailways:rail_docking_connector",
              {"facing": "east", "extended": "false", "powered": "false"})
        block((3, 4, 4), "guidedrailways:rail_docking_connector",
              {"facing": "west", "extended": "false", "powered": "false"})
        block((1, 1, 6), "create:track_station")
        block((1, 1, 7), "create:white_postbox", {"facing": "south", "open": "false", "waterlogged": "false"})
        block((5, 4, 4), "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"})
        block((4, 4, 4), "create:andesite_funnel",
              {"facing": "west", "extracting": "true", "powered": "false", "waterlogged": "false"})
        block((0, 4, 4), "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"})

    palette, blocks = [], []
    for pos, (state, nbt) in sorted(cells.items()):
        if state not in palette:
            palette.append(state)
        entry = {"pos": list(pos), "state": palette.index(state)}
        if nbt:
            entry["nbt"] = dict(nbt, x=pos[0], y=pos[1], z=pos[2])
        blocks.append(entry)
    root = {"DataVersion": 3955, "size": [9, 7, 9], "palette": palette, "blocks": blocks, "entities": []}
    DEST.mkdir(parents=True, exist_ok=True)
    path = DEST / (name + ".nbt")
    path.write_bytes(gzip.compress(b"\x0a\0\0" + payload(root), mtime=0))
    print(name, len(blocks), "blocks,", len(palette), "states")


if __name__ == "__main__":
    for scene in ("physical_bogey", "physical_controls", "electric_controls", "rail_transfer"):
        build(scene)
