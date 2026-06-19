# Minecraft Raid — Player Guide

Rust-style base raiding in Minecraft. You claim land, build inside it, and blocks you place gain **raid durability (HP)**. Raiders must chew through that HP to breach storage and loot your base. There is no Tool Cupboard — protection comes from your **land claim** and **raid blocks**.

> Server owners can change numbers below (grace timer, HP values, tools, etc.) in `config.yml`. Defaults are shown where relevant.

---

## Quick start

1. Stand where you want your base center.
2. Run `/raid claim <radius>` (default allowed radius: **5–128** blocks).
3. Build inside the claim — placed blocks automatically become raid blocks.
4. Get a **repair tool** (default: **Golden Hoe**) to repair and reinforce.
5. Run `/raid list` to see your claim IDs and coordinates.

---

## Land claims

| Command | What it does |
|---------|----------------|
| `/raid claim <radius>` | Create a circular claim centered on you |
| `/raid unclaim` or `/raid unclaim here` | Remove the claim you're standing in |
| `/raid unclaim <id>` | Remove a claim by ID (from `/raid list`) |
| `/raid list` | List your claims |

**Rules**

- Claims cannot overlap other claims (including special zones).
- Player claims cannot overlap **WorldGuard** protected regions (spawn, shops, etc.) when WorldGuard is installed on the server.
- You can own up to **5** claims by default.
- A **claim border** may appear when you're near the edge (a client-side ring around the circle).
- **Outsiders** cannot place, break, or use buckets inside your claim.
- **PvP is allowed** inside player claims — raiders can fight you while raiding.
- Animals and other passive entities inside your claim are protected from outsiders.

---

## Trusted members

| Command | What it does |
|---------|----------------|
| `/raid trust <player>` | Let a friend build in your claim (stand inside your claim) |
| `/raid untrust <player>` | Remove their access |

**Important:** Trusted players can place and break raid blocks, but those blocks always belong to **you** (the claim owner). If you untrust someone, they cannot take your base with them.

---

## Raid blocks

Any **block you place inside your claim** (or a trusted member places) becomes a **raid block** with HP.

- **Stronger materials = more HP.** Examples at default settings:
  - Most blocks: **100 HP**
  - Ancient debris: **400 HP**
  - Obsidian / crying obsidian: **500 HP**
  - Netherite block: **800 HP**
- Some blocks cannot be raid blocks (bedrock, barriers, command blocks, etc.).
- **Natural terrain** (stone, dirt already in the world) is **not** a raid block — outsiders cannot grief random terrain inside your claim, only blocks you built.
- Raid blocks resist **fire, spread, and ignition**.
- **Pistons cannot move** raid blocks.

**Who can break what**

- **You / trusted members** — remove raid blocks instantly (normal building).
- **Outsiders** — must mine down HP; they cannot break non-raid blocks in your claim.

---

## Checking durability

Look directly at a raid block (within about **2 blocks**). If it's **damaged** or **reinforced**, your **action bar** shows HP (and reinforcement tier if applicable).

Example: `Reinf II | 350/500`

Full-HP, unreinforced blocks show nothing.

---

## Repairing

1. Hold the **repair tool** (default: **Golden Hoe**).
2. Have **one block of the same type** in your inventory (e.g. obsidian to repair obsidian).
3. **Right-click** the damaged raid block.

This fully restores HP and clears a **breached** container back to locked.

---

## Reinforcing (upgrading HP)

Reinforcement adds extra max HP in **three tiers** (I → II → III).

1. Hold the repair tool.
2. **Shift + left-click** one of your raid blocks (corner 1).
3. **Shift + left-click** another (corner 2) to define a box.
4. Read the chat preview: materials needed, XP cost, and clickable **[ACCEPT]** / **[DENY]**.
5. Click **ACCEPT** (or run `/raid reinforce confirm <session>`) if you have the materials and XP.

**Default tier costs (per block upgraded)**

| Tier | Material | HP added (default) |
|------|----------|-------------------|
| I | 1× Stone | +200 |
| II | 1× Iron Block | +500 |
| III | 1× Obsidian | +800 |

Each tier also costs **XP** (scales with how many blocks you selected and which tier you're applying).

- Maximum **3 tiers** per block.
- Breached blocks cannot be reinforced.
- Selections are limited in size (default: up to **512** blocks per box).
- Proposals expire after a while — start over if you wait too long.

---

## Raiding someone else's base

**As a raider**

1. Find their claim (the border ring may help).
2. Attack **placed blocks** (raid blocks) — mine them or use **TNT / explosions**.
3. **Better pickaxes deal more damage.** Default mining damage per hit:
   - Wooden: 8 · Stone: 10 · Iron: 15 · Golden: 12 · Diamond: 20 · Netherite: 25
4. **Creepers do not damage raid blocks** — they won't help you breach walls.
5. **TNT and other explosions** do damage raid HP (creepers excluded).
6. **Containers** (chests, barrels, hoppers, furnaces, shulkers, etc.) **cannot be opened** until breached.
7. When a container's HP hits **0**, it becomes **breached** — you can open and loot it (the block stays until broken).
8. Non-container raid blocks **break naturally** at 0 HP.

**When can you damage raid blocks?**

By default, outsiders can only reduce raid HP while:

- The **claim owner is online**, or
- For **10 minutes** after the owner disconnects (anti combat-log grace period).

After that grace window ends, raid damage is blocked until the owner comes back online. Logging off mid-raid does **not** instantly make the base safe.

---

## Storage & loot rules

| Block type | At 0 HP |
|------------|---------|
| Chest, barrel, hopper, furnace, shulker, etc. | **Breached** — outsiders can open; block may still be there |
| Walls, floors, other blocks | **Destroyed** |

**Ender chests** are personal storage and are **not** raid containers.

---

## Special zones (Safe & War)

Some servers mark areas as **Safe Zones** or **War Zones**. You'll see **titles** when entering or leaving.

| Zone | Combat | Building |
|------|--------|----------|
| **Safe Zone** | No damage taken; can't hit players outside the zone from inside | Protected |
| **War Zone** | PvP enabled | No placing/breaking |

These are created by server staff — you play inside them; you don't create them with normal `/raid claim`.

Staff **admin zones** (`/raid admin safezone|warzone claim`) are not blocked by WorldGuard overlap checks.

---

## Tips

**Defenders**

- Build critical walls and storage with high-HP materials (obsidian, netherite).
- Reinforce storage rooms — Tier III on obsidian is painful to raid.
- Keep repair materials and the repair tool handy after a raid.
- Don't log off mid-raid expecting instant safety — the grace window still applies.

**Raiders**

- Bring good pickaxes and TNT; creepers won't break raid blocks.
- Breach containers first, then loot.
- Raid while the owner is online, or within the post-logout grace window.
- PvP inside claims is fair game — bring gear.

---

## Commands cheat sheet

```
/raid claim <radius>
/raid unclaim [here|<id>]
/raid list
/raid trust <player>
/raid untrust <player>
/raid reinforce confirm <session>
/raid reinforce deny <session>
```
