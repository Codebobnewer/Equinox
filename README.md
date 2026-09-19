# Equinox

A horse stable plugin for Paper/Folia servers. Players buy horses through a `/stable` GUI,
ride them exclusively, and sell them back for a partial refund at WorldGuard-defined stations.

## Features

- `/stable` opens a GUI with three horse tiers (Basic/Advanced/Elite), each with its own price,
  health, speed and jump strength - all configurable in `config.yml`.
- Buying spawns a saddled horse at the nearest admin-defined **buy station** (a WorldGuard region).
- Only the buyer can ride or leash their horse - other players can't mount it or lead it away,
  though anyone (or anything) can still damage it.
- An unridden horse follows its owner wolf-style: it walks over once they stray far enough away,
  and teleports to a safe nearby spot if they get too far to realistically catch up on foot.
- Punching your own horse - the one interaction it's otherwise immune to from its owner - toggles
  **stay**: it stops following and sits in place, still reactive to being pushed, hit, or knocked
  around, just not moving or acting on its own, until punched again.
- Purchased horses can't breed, so a tier horse's stats and looks never end up on an untracked
  wild foal.
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

- `database.file` - SQLite filename (owned-horse records only), stored in the plugin's data folder.
  Stations are hand-editable admin data and live in `stations.yml` instead, not this file.
- `economy.refund-percent` - fraction of the purchase price refunded on sell.
- `tiers.*` - display name, price, health, speed, jump strength, and horse color/style per tier.
- `messages.*` - every player-facing message, as [MiniMessage](https://docs.advntr.dev/minimessage/)
  strings.

> [!NOTE]
> The follow/stay/teleport distances and timing (how far before a horse starts walking to its
> owner, how close before it stops, when it teleports instead of walking) aren't config options -
> they're constants in `HorseService`, tuned to match vanilla's own wolf AI thresholds.

## Architecture notes

- **Repository / Service split.** Storage lives behind a `Repository` interface per domain
  (`HorseRepository` -> `SqliteHorseRepository`; `StationRepository` -> `YamlStationRepository`);
  business logic lives in `HorseService`/`StationService`. Commands (`StableCommand`) and the GUI
  (`StableMenu`) are thin callers into those services - no logic of their own.
- **Horses are a database; stations are a file.** Ownership is core gameplay state that changes
  constantly and nobody hand-edits, so it's SQLite. Stations are small, admin-authored location
  data - the kind of thing staff should be able to open and edit directly - so they're YAML
  (`stations.yml`), read/written exclusively through `YamlStationRepository`.
- **Economy is stubbed.** `EconomyProvider` is an interface; `StubEconomyProvider` always
  succeeds and just logs withdrawals/deposits. Swap in a Vault-backed implementation later without
  touching anything else.
- **`config.yml` self-heals new keys.** `ConfigManager` loads the bundled `config.yml` as defaults
  and copies in anything missing from the deployed file (then saves it), so upgrading Equinox picks
  up newly-added keys automatically instead of silently falling back to raw key names in chat.
- **Ownership is cached in memory.** `HorseService` keeps a `Map<UUID, OwnedHorse>` loaded fully
  asynchronously at startup (`loadOwnedHorsesIntoCache()`, logged once it actually finishes), so
  purchase/sell/menu-open never block the calling thread on I/O, and a slow disk can't stall
  server startup either.
- **Folia-safe.** All entity spawning, world mutation, and DB/file I/O goes through
  [UniversalScheduler](https://github.com/Anon8281/UniversalScheduler)'s `TaskScheduler` directly -
  a region/entity-scoped call for anything tied to a location or entity, `runTaskAsynchronously`
  for I/O - never `Bukkit.getScheduler()` or a raw `Thread`.
- **Static service locator.** `Equinox` exposes its wired services as static accessors
  (`Equinox.getHorseService()`, `getStationService()`, `getScheduler()`, `getMessages()`) for
  anything outside the constructor-injected call graph that needs them - it's an access point,
  not a place business logic lives.

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
