# Changelog

## Unreleased

### Full rewrite for the network-wide plugin architecture rule set

Equinox previously followed its own conventions (adapted from Metabasis by observation). This
pass makes it explicitly compliant with the network's written rule set instead, changing several
things that were working fine but not in the mandated shape:

- **Data models are now Lombok `@Getter @With @AllArgsConstructor` plain classes, not records.**
  `TierDefinition`, `Station`, `OwnedHorse` all changed shape (`.name()` -> `.getName()`, etc.) -
  matching the rule set's required Lombok-on-plain-classes convention and Metabasis's own `Warp`
  class exactly.
- **Stations moved from SQLite to YAML (`stations.yml`).** The rule set treats hand-editable
  admin/definitional data as YAML-only, database as reserved for core runtime state a database
  read/write; stations (small, admin-authored location records) fit the YAML case the same way
  Metabasis's warps/groups/spawns do, while owned-horse records (constantly-changing player state,
  never hand-edited) stayed on SQLite.
- **Repository interfaces introduced for both domains.** `HorseRepository` (implemented by
  `SqliteHorseRepository`) and `StationRepository` (implemented by `YamlStationRepository`) sit
  between the services and their storage, replacing the old concrete `HorseDao`/`StationDao`
  classes. `DatabaseManager` was folded directly into `SqliteHorseRepository` (it was the only
  thing that needed a connection pool once stations left SQLite), matching how Metabasis's
  `SqliteWarpHistoryRepository` self-contains its own HikariCP setup rather than sharing a generic
  "database manager" abstraction.
- **Every repository method is now asynchronous end to end.** `save`/`delete`/`loadAll` all
  return `CompletableFuture`s and own their own `scheduler.runTaskAsynchronously(...)` internally,
  instead of the caller (`HorseService`) having to remember to wrap each call. Station
  create/remove commands now report success/failure via `.thenAccept(...)`/`.exceptionally(...)`
  once the write actually completes, rather than assuming it succeeded synchronously.
- **`Equinox` now exposes a static service locator** (`getHorseService()`, `getStationService()`,
  `getScheduler()`, `getMessages()`), populated once in `onEnable()` and cleared in `onDisable()`
  - an access point for anything outside the constructor-injected call graph, not a place logic
  lives (all business logic still sits in `HorseService`/`StationService` behind constructor DI).

Not changed, and why: no custom Bukkit events were added (the rule set makes that conditional on
another plugin actually needing to react to Equinox, and none currently does), and no explicit
state-machine was introduced for a "flow" (buying/selling are single-step actions, not a
multi-stage sequence the rule set's state-machine pattern is meant for).

## Earlier

### Renamed `Dao`/`Manager` classes to `Repository`/`Service`, stopped hardcoding admin messages

- `HorseDao` -> `HorseRepository`, `StationDao` -> `StationRepository`, `HorseManager` ->
  `HorseService`, `StationManager` -> `StationService`, matching "storage in Repositories,
  business logic in Services" and Metabasis's own naming. (`DatabaseManager` was left as-is at
  the time - it was infrastructure, not a repository - before later being folded away entirely
  in the rewrite above.)
- `StableCommand`'s admin station subcommands (`setbuy`/`setsell`/`remove`/`list`) sent raw
  hardcoded strings instead of going through `Messages`/`config.yml` like every other
  player-facing message in the plugin. Added six message keys and routed all six call sites
  through `Messages`.
