# Witch's Atelier

A magic mod for Minecraft (NeoForge 1.21.1), inspired by the system in Witch Hat Atelier: you do not pick a spell from a menu, you **draw** it. Nothing happens until the ring is closed.

## Running

```bash
./gradlew runClient
```

Recognizer tests and in-world game tests:

```bash
./gradlew test
```

```bash
./gradlew runGameTestServer
```

## How to cast (what already works)

1. Take an **Ink Pen** (the "Tools" tab, or craft it: conjuring ink + iron nugget + feather).
2. Hold **right click** while looking at any block face (floor, wall, ceiling) and move your view: a line of ink stays behind. A glyph may span several blocks of one plane.
3. Draw a **sigil in the centre**, **signs around it**, and finally **close the ring**. The spell fires the moment the ring closes.
4. **Shift + right click** erases ink. Rub out part of the ring or break a block under it, and the spell goes out.

Signs are drawn with their "top" pointing outwards, away from the centre; a reversed sign gives the opposite effect.

| What to draw | What happens |
|---|---|
| Fire + Column ×4 | a pillar of flame that sets everything in the beam alight |
| Water + Column | a water jet: pushes, puts out fire, turns lava into obsidian |
| Wind + Column | an updraft that throws things up |
| Light + Column | a beam of light that outlines mobs and lights the way |
| Earth + Column | a stone pillar made of the surface's own material (temporary) |
| Earth + Crush (on a wall) | Wall Breaker: the wall crumbles |
| Earth + reversed Crush | Integration: what crumbled is put back together |
| Fire / Wind + Bolt | a volley of fireballs / wind charges |
| any element + Dispersion | spills out around the glyph |
| + Levitation / Float | lifts whoever stands over the glyph (reversed: presses them down) |
| + Pull | pulls in (reversed: pushes away; turned along the ring: swirls) |
| + Rain | a rain of the element over the area |
| Repetition in the centre | mends tools in hand; items on the ground do not despawn |
| an empty closed ring | a burst of the ink's energy (without breaking blocks) |

**How well it is drawn shapes the result.** A bigger ring is a stronger and larger spell. A rounder ring, neater signs and a more symmetric layout make it stand up to wear for longer. If there are more signs on one side, the effect leans that way. A careless glyph may misfire or be read as a different sign.

## What's next
- drawing on items: paper, Palm Quire, **armour**, cloaks (a separate canvas in a GUI)
- pens for different surfaces: Woodcruor Wand (chalk on stone), Searneedle (burning into metal)
- Silverwood and true conjuring ink, additives (invisibility, waterproofing)
- nested rings, linked glyphs, two halves of a ring on two items (switches)
- artifacts (contraptions), learning signs, forbidden magic and the Knights Moralis
