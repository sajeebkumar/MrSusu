# MrSusu NPC Plugin

> **Standalone fake-player NPC for Paper 1.21.1 – 1.21.4+**  
> No Citizens. No FancyNpcs. Pure NMS packet magic.

---

## Features

| Feature | Details |
|---|---|
| Fake player entity | Spawned via `ClientboundAddEntityPacket` + `ClientboundPlayerInfoUpdatePacket` |
| Custom skin | Fetched async from Mojang API by username; cached; fallback to Steve |
| Wander AI | 70% walk / 30% idle; smooth interpolation; configurable radius & interval |
| Water/lava avoidance | Path & destination checked block-by-block before committing |
| Head tracking | Idle mode rotates head toward nearest player within 8 blocks |
| Click interception | Netty `ChannelDuplexHandler` cancels `ServerboundInteractPacket` |
| Click cooldown | Per-player cooldown in seconds (configurable) |
| NPC messages | Chat messages sent to player on interact (color codes + PAPI ready) |
| Auto-spawn | Saved coordinates restored on every server startup |
| `/mrsusu` command | `spawn` · `despawn` · `reload` |

---

## Requirements

| Requirement | Version |
|---|---|
| Java | **21** |
| Server software | **Paper** (or Purpur) |
| Minecraft | **1.21.1 – 1.21.4+** |
| Mappings | **Mojang-mapped** (Paper default) |

---

## Building

```bash
# Clone / place project in a folder
cd MrSusu
mvn clean package
# Output: target/MrSusu-1.0.0.jar
```

Copy `target/MrSusu-1.0.0.jar` into your server's `plugins/` folder.

> **Paper version mismatch?**  
> Edit `<paper.version>` in `pom.xml` to match your exact Paper build, e.g.  
> `1.21.1-R0.1-SNAPSHOT` or `1.21.3-R0.1-SNAPSHOT`.

---

## Installation

1. Drop the JAR in `plugins/`.
2. Start the server – `plugins/MrSusu/config.yml` is generated.
3. Edit `config.yml` to set `skin-name`, `click-command`, movement options, etc.
4. `/mrsusu spawn` – stand where you want the NPC and run this command.
5. The location is saved; Mr Susu will reappear on every restart automatically.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/mrsusu spawn` | `mrsusu.admin` | Spawns NPC at your feet; saves location |
| `/mrsusu despawn` | `mrsusu.admin` | Removes NPC; clears saved location |
| `/mrsusu reload` | `mrsusu.admin` | Reloads `config.yml`; clears skin cache |

---

## config.yml Reference

```yaml
skin-name: "Herobrine"          # Mojang username to fetch skin from
click-command: "mrsusu"         # Command executed when player clicks NPC
click-cooldown-seconds: 3       # 0 = no cooldown

npc-messages:
  - "&eMr Susu &7says: &f\"You shouldn't be here...\""
show-messages-on-interact: true

movement:
  radius: 15                    # Max wander distance (blocks) from spawn
  interval-seconds: 10          # AI decision interval
  avoid-water: true             # Reject water paths/destinations
  avoid-lava: true              # Reject lava paths/destinations

skin-cache-minutes: 60
debug: false
```

---

## Architecture

```
dev.mrsusu
├── MrSusuPlugin.java           Main entry point
├── commands/
│   └── MrSusuCommand.java      /mrsusu executor + tab completer
├── data/
│   └── DataManager.java        npc_data.yml persistence
├── listeners/
│   ├── PacketListener.java     Netty channel injector + interact cancel
│   └── PlayerJoinListener.java Re-sends NPC packets to joining players
├── npc/
│   ├── NpcManager.java         Lifecycle, movement loop, interpolation
│   ├── NpcState.java           Immutable state record
│   └── PacketHelper.java       All clientbound packet construction
├── pathfinding/
│   ├── WanderPathfinder.java   Safe block finder (water/lava avoidance)
│   └── PathResult.java         Record returned by pathfinder
└── skin/
    ├── SkinFetcher.java        Async Mojang API → SkinData
    └── SkinData.java           Record: value + signature
```

---

## NMS Packet Reference (1.21.x Mojang-mapped)

| Packet | Purpose |
|---|---|
| `ClientboundPlayerInfoUpdatePacket` | Adds fake player to tab-list (required for skin) |
| `ClientboundAddEntityPacket` | Spawns the entity with `EntityType.PLAYER` |
| `ClientboundSetEntityDataPacket` | Sets entity metadata flags |
| `ClientboundTeleportEntityPacket` | Moves NPC to new position |
| `ClientboundRotateHeadPacket` | Rotates NPC head |
| `ClientboundPlayerInfoRemovePacket` | Removes from tab-list after spawn |
| `ClientboundRemoveEntitiesPacket` | Destroys entity on client |
| `ServerboundInteractPacket` | Inbound – intercepted via Netty handler |

---

## DeluxeMenus Integration Example

Set in `config.yml`:
```yaml
click-command: "dm open main_menu"
```
When a player clicks Mr Susu, `/dm open main_menu` is dispatched as that player.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| NPC invisible / no skin | Check `debug: true` and look for skin fetch errors in console |
| NPC visible but no skin | Mojang rate-limited; wait 60 s or set a different `skin-name` |
| Build fails – NMS class not found | Ensure `paper.version` in `pom.xml` matches your exact server |
| Click not detected | Confirm the player has `mrsusu.interact` permission (default: true) |
| NPC spawns at 0,0,0 | Always run `/mrsusu despawn` + `/mrsusu spawn` after moving |

---

## License

MIT – free to modify and redistribute.
