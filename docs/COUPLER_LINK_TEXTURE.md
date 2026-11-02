# 车钩连接工具贴图（2026-11-03）

资源：`src/main/resources/assets/physicalrailways/textures/item/coupler_link.png`。

使用内置 imagegen 生成造型参考，最终按明确的 16×16 逻辑网格逐格重建，不再把高分辨率伪像素图直接缩小。原始生成图为 1254×1254，内部并非严格的 16×16 格，因此直接等距采样会造成轮廓和铰轴错位。无文字、光晕或外投影，保持原有 handheld 物品模型及使用逻辑；没有修改铁路对接连接器或第三方资源。

配色采用铁灰钳口、黄铜铰轴、深棕握柄，最终使用 7 色，透明度仅为 0 或 255。实际游戏内持握和快捷栏效果仍待验收。

## 原始生成提示词

Create a production Minecraft inventory ITEM TEXTURE of a single mechanical train-coupler linking tool / industrial coupling pliers. TRANSPARENT background. The reference target is a 16 by 16 pixel Minecraft sprite, enlarged with nearest neighbor to your canvas: precisely SIXTEEN logical square pixels across and SIXTEEN down; every visible shape must be made of large hard square pixel blocks, no fine detail or antialiasing. One tool only, diagonal from bottom-left handle to top-right steel jaw. Strong compact recognizable silhouette filling about 13 by 13 logical pixels, 1 pixel clear margin. Dark brown single short chunky grip at bottom-left, gunmetal shaft, warm brass 2x2 pixel hinge, substantial forked iron-grey coupling jaws at top-right, like a compact coupling pliers/spanner designed for hooking two train couplers. Palette 12 colors total, cool charcoal shadows, medium iron-grey metal, restrained warm brass detail, desaturated brown grip. Match the industrial handcrafted pixel aesthetic of Minecraft Create. No text, no letters, no logos, no background, no ground, no shadow outside the tool, no border, no perspective scene, no UI frame, no glow, no gradients. Crisp flat orthographic 2D inventory icon. This is a BRAND NEW item sprite, not a rendered 3D block and not a concept illustration. Output PNG with true alpha transparency.

修正钳口内部开口：恢复此前误删的两格外缘，并清除铰轴右上方、开口内的深色突起（零起算坐标 8,7）。最终预览采用严格的整数倍像素复制。
