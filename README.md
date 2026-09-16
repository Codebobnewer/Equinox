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
- [WorldGuard](https://enginehub.org/worldguard) (and WorldEdit, which it depends on) installed on
  the server - buy/sell stations are WorldGuard regions, defined with WorldEdit's selection tools
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

> [!IMPORTANT]
> Equinox never creates or edits WorldGuard regions itself - it only reads the bounds of a region
> that already exists. You have to define the region with WorldGuard/WorldEdit first, then point
> Equinox at it by name. See [Setting up buy and sell stations](#setting-up-buy-and-sell-stations)
> below for the full walkthrough.

## Setting up buy and sell stations

A **buy station** is where a purchased horse spawns; a **sell station** is where a horse has to be
standing for `/stable sell` to refund it. Both are just WorldGuard regions that Equinox looks up by
name, so you set them up in two steps: define the region with WorldGuard, then register it with
Equinox.

### 1. Define the region with WorldGuard

Stand where you want the station and mark out its area, then define the region:

```
//wand
```
```
/rg define buy_spawn
```

- `//wand` (WorldEdit) gives you the selection tool - left-click one corner, right-click the other.
- `/rg define <name>` (WorldGuard) turns that selection into a named region. Region names are
  case-insensitive and must be unique per world.

> [!TIP]
> Give the region some height (a few blocks above and below where players will stand). The station
> lookup checks whether the horse's exact feet position is inside the region's bounding box, so a
> region that's only one block tall can miss a horse standing on slightly uneven terrain.

Repeat this for as many buy and sell stations as you want - stations don't have to be in the same
world or anywhere near each other. Equinox always spawns a purchased horse at the **nearest** buy
station to the buyer, so having one per world (or one per hub) is a common setup.

### 2. Register the region with Equinox

While standing in the **same world** as the region you just defined, run:

```
/stable station setbuy <station-name> <region-id>
```

or, for a sell station:

```
/stable station setsell <station-name> <region-id>
```

`<station-name>` is whatever you want to call it in Equinox (used later with `remove`/`list`);
`<region-id>` is the exact WorldGuard region name from step 1. For example:

```
/stable station setbuy spawn-buy buy_spawn
/stable station setsell spawn-sell sell_spawn
```

Both commands need `equinox.admin.station` (default: `op`). If the region name doesn't exist in
your current world, Equinox tells you and registers nothing.

### 3. Verify it

```
/stable station list
```

prints every registered station, its type, world, and backing region ID. From here, `/stable` will
spawn purchases at the nearest `BUY` station, and `/stable sell` will only succeed while the horse
is standing inside a `SELL` station's region.

> [!NOTE]
> You need at least one buy station in a world before anyone can purchase a horse there, and at
> least one sell station before anyone can sell one back - `/stable` and `/stable sell` fail with a
> chat message (not an error) if neither exists yet.

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
