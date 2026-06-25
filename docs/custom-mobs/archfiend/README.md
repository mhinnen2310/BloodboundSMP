# The Bloodbound Archfiend

Blockbench source model and textures for a dark fantasy BloodboundSMP final boss.

## Files

- `bloodbound_archfiend.bbmodel` - Blockbench model source.
- `textures/archfiend_body.png` - obsidian body, armor cracks, crown, bone highlights, runes.
- `textures/archfiend_glow.png` - crimson heart core, eyes, gem, particles, burst effects.
- `textures/archfiend_wings.png` - damaged blood-red wing membrane with torn holes.
- `textures/archfiend_weapon.png` - ritual blood scythe and ash-gray metal.

## Model Notes

- Model name: `The Bloodbound Archfiend`
- Texture size: `128x128`
- Cube count: `56`
- Style: obsidian black body, dark robe/armor silhouette, crown, glowing core, torn wings, chains, claw hand, scythe.
- Designed as a readable large boss silhouette while keeping cube count modest for Paper server usage.

## Animations

- `idle`
- `summon`
- `attack_scythe`
- `attack_blood_burst`
- `wing_slam`
- `death`

## Resource Pack Usage

Import `bloodbound_archfiend.bbmodel` into Blockbench, then export to the format required by your server setup, such as a custom item model, ModelEngine/ItemsAdder/Oraxen workflow, or another Paper-compatible entity model pipeline. The `.bbmodel` embeds the texture data and also references the texture files in `textures/`.
