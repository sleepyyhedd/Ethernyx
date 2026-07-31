# ☾ EtherNyxAuth ☾

A dark-fantasy-themed authentication + player-info + ban-management plugin for
Spigot/Paper 1.16.5+.

## Building

This is a standard Maven project.

```bash
mvn clean package
```

The shaded jar will be produced at `target/EtherNyxAuth-1.0.0.jar`. Drop it into
your server's `plugins/` folder.

**Note:** I wasn't able to actually compile this in my sandbox (no JDK/Maven
available and no network access to pull dependencies), so it hasn't been
build-verified. Please run `mvn clean package` yourself and report back any
compile errors — I'm glad to fix them. I'd recommend testing on a local/dev
server before deploying to production, especially for anything touching
ban enforcement.

## Dependencies (pulled automatically by Maven)

- Spigot API 1.16.5-R0.1-SNAPSHOT (`provided` — build against your own Spigot
  BuildTools output, or point at a mirror repo of the Spigot API)
- `org.mindrot:jbcrypt:0.4` — password hashing (shaded/relocated into the jar)
- Floodgate API (soft dependency — only needed if you want Bedrock auto-auth)
- DiscordSRV (soft dependency — only needed if you want Discord ban announcements)

If you don't use Floodgate or DiscordSRV, the plugin still loads fine; those
features simply no-op or fall back to console logging.

## Project layout

```
src/main/java/com/ethernyx/auth/
  EtherNyxAuth.java          Main plugin class, wiring, scheduled tasks
  commands/                  /register, /login, /changepassword, /auth <sub>
  listeners/
    AuthListener.java        Join/quit, remember-me auto-login, IP/device change
    RestrictionListener.java Enforces every pre-login restriction (movement,
                              damage, interaction, chat, commands, etc.)
    StatsListener.java       Kills, deaths, mining, crafting, distance,
                              achievement checks
    BanListener.java         Ban enforcement on login + third-party ban detection
    FloodgateListener.java   Bedrock auto-auth via Floodgate
  managers/
    PlayerDataManager.java   players.yml persistence (thread-safe, backups)
    BanManager.java          bans.yml persistence, ban/unban/expiry logic
    AuthManager.java         Live session state: who's logged in, attempts,
                              cooldowns, kick timers
    AchievementManager.java  Achievement definitions and granting logic
    DiscordManager.java      DiscordSRV integration via reflection (soft dep)
                              + webhook fallback
    BookManager.java         Builds the /auth info written book
  models/
    PlayerData.java          Per-player persisted state
    BanEntry.java            Per-ban record
  util/
    PasswordUtil.java        BCrypt hashing + password rule validation
    DurationUtil.java        Ban duration parsing/formatting (1h, 1d, 1w, ...)
```

## Config

`src/main/resources/config.yml` ships with sane defaults matching the spec:
login timeout, brute-force cooldowns, password rules, all security toggles,
ban message templates, Discord channel IDs/message templates, and achievement
definitions. Edit `plugins/EtherNyxAuth/config.yml` after first run, then
`/auth reload`.

## Things worth double-checking before production use

- **Spigot API version pin**: this targets 1.16.5-R0.1-SNAPSHOT. If your
  server runs a different Minecraft version, update the dependency version
  (and re-test the event names — a few, like `PlayerAttemptPickupItemEvent`,
  are version-sensitive).
- **DiscordSRV reflection**: DiscordSRV's internal API has changed across
  versions historically. The reflection calls in `DiscordManager` target a
  commonly-used shape (`DiscordSRV.getPlugin().getJda().getTextChannelById(...)`),
  but if your DiscordSRV version differs, you may need to adjust the method
  names. The webhook fallback (`discord.webhook.*` in config) is a
  version-independent alternative if reflection gives you trouble.
- **Third-party ban detection**: implemented as a best-effort heuristic that
  watches `PlayerKickEvent` reasons for ban-like phrasing, since GriefPrevention/
  Anti-Spam-style plugins don't share a common "ban" event. For tighter
  integration with a specific plugin you use, hooking its actual API directly
  will be more reliable than the phrase-matching fallback.
- **Book page layout**: only `book.title` and `book.author` are read from
  config; the five page layouts themselves are built directly in
  `BookManager.java` (not driven by the `book.pages.*` templates you'll see
  referenced in the original spec) so content stays in sync with actual
  player data. Vanilla written books cap each page at 256 characters — the
  generated pages are close to that limit, so if you edit `BookManager.java`
  to add more fields, watch for overflow.
