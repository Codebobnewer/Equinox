# Equinox

A horse stable plugin for Paper/Folia servers. Players buy horses through a `/stable` GUI,
ride them exclusively, and sell them back for a partial refund at WorldGuard-defined stations.

## Features

- `/stable` opens a GUI with three horse tiers (Basic/Advanced/Elite), each with its own price,
  health, speed and jump strength - all configurable in `config.yml`.
- Buying spawns a saddled horse at the nearest admin-defined **buy station** (a WorldGuard region).
- Only the buyer can ride their horse; other players can't mount it, and can't damage it either.
- An unridden horse stands still (no wandering AI) until it's attacked, at which point it can
  panic/flee for a configurable duration before settling back down.
- `/stable sell` refunds a configurable percentage of the purchase price, but only while the
  horse is standing inside a **sell station** region.
- One horse owned per player at a time.

## Requirements

- Java 21
- Maven
- A Paper or Folia server on **1.21.11** (pinned there deliberately - see [Version pinning](#version-pinning))
- [WorldGuard](https://enginehub.org/worldguard) installed on the server (buy/sell stations are
  WorldGuard regions)
- [CommandAPI](https://commandapi.dev) installed on the server as its own plugin (Equinox depends
  on it rather than bundling its own copy - see [Why CommandAPI isn't shaded](#why-commandapi-isnt-shaded))

## Building

```
mvn clean package
```

Produces `target/Equinox-<version>.jar`. Everything else Equinox needs (InvUI, UniversalScheduler,
HikariCP, sqlite-jdbc, Lombok at compile time) is shaded into that one jar - only WorldGuard,
CommandAPI and the server API itself are expected to already be present on the server.

## Commands & permissions

| Command | Permission | Description |
|---|---|---|
| `/stable` | `equinox.use` (default: true) | Opens the buy menu |
| `/stable sell` | `equinox.use` | Sells the horse you're currently on, if it's in a sell station |
| `/stable station setbuy <name> <region>` | `equinox.admin.station` (default: op) | Registers a WorldGuard region (in your current world) as a buy station |
| `/stable station setsell <name> <region>` | `equinox.admin.station` | Registers a region as a sell station |
| `/stable station remove <name>` | `equinox.admin.station` | Removes a station |
| `/stable station list` | `equinox.admin.station` | Lists all registered stations |

Buy/sell regions must already exist as WorldGuard regions before running `setbuy`/`setsell` -
Equinox only reads region bounds, it never creates or edits them.

## Configuration

See `src/main/resources/config.yml` for the full set of options:

- `database.file` - SQLite filename, stored in the plugin's data folder.
- `economy.refund-percent` - fraction of the purchase price refunded on sell.
- `horse.panic-duration-ticks` - how long a hit horse's AI stays on before it goes back to
  standing still, if it isn't hit again in the meantime.
- `tiers.*` - display name, price, health, speed, jump strength, and horse color/style per tier.
- `messages.*` - every player-facing message, as [MiniMessage](https://docs.advntr.dev/minimessage/)
  strings.

## Architecture notes

- **Economy is stubbed.** `EconomyProvider` is an interface; `StubEconomyProvider` always
  succeeds and just logs withdrawals/deposits. Swap in a Vault-backed implementation later without
  touching anything else.
- **Ownership is cached in memory.** `HorseManager` keeps a `Map<UUID, OwnedHorse>` populated once
  at startup, so purchase/sell/menu-open never block the calling thread on a database read. SQLite
  is only touched for durability (writes happen asynchronously off the game thread).
- **Folia-safe.** All entity spawning and world mutation goes through `SchedulerService`, a thin
  wrapper over [UniversalScheduler](https://github.com/Anon8281/UniversalScheduler), instead of
  calling `Bukkit.getScheduler()` or `World.spawn()` directly.

### Version pinning

This project targets Minecraft 1.21.11 / Java 21 deliberately. Minecraft 26.1+ (March 2026) raised
the minimum Java version to 25 and changed Paper's versioning scheme; bumping past 1.21.11 means
also raising the Java target and re-checking every dependency version in `pom.xml`.

### Why CommandAPI isn't shaded

CommandAPI ships as its own Paper plugin (`CommandAPI-*-Paper.jar`) with a single shared
`CommandAPIHandler` used by every plugin that depends on it. Equinox depends on
`commandapi-bukkit-core` at `provided` scope and declares `depend: [CommandAPI, WorldGuard]` in
`plugin.yml` instead of shading its own copy - two versions of CommandAPI patching the same
command dispatcher in one server is a real source of crashes.
