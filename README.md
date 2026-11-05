# Impression Tracker SDK

An Android SDK that intercepts every ad impression reported by **AlphaAds**
and **BetaAds**, logs it in a fixed format and delivers it to a
backend — without a single line changed in the host application's Kotlin code.

```
Intercepted impression event:
- ID: 44ce9d81-5007-4921-8ed2-ea96605d8880
- Bidder Name: ACME
```

---

## Table of contents

- [Design constraints](#design-constraints)
- [Quick start](#quick-start)
- [How the SDK starts without touching the app](#how-the-sdk-starts-without-touching-the-app)
- [Intercepting the two networks](#intercepting-the-two-networks)
- [No runtime dependency on the ad networks](#no-runtime-dependency-on-the-ad-networks)
- [Architecture](#architecture)
- [Storage and delivery](#storage-and-delivery)
- [Configuration](#configuration)
- [Minified builds](#minified-builds)
- [Inspecting a closed-source SDK](#inspecting-a-closed-source-sdk)
- [Tests](#tests)
- [Known limitations](#known-limitations)
- [Packaging notes](#packaging-notes)

---

## Design constraints

The SDK was built to a deliberately strict brief, because those are the
constraints a real publisher-facing SDK lives under.

| Constraint | How it is met |
| --- | --- |
| Integrates without touching the host's code | one dependency line; the SDK boots from its own `ContentProvider` |
| No third-party libraries at all | only the Android framework and the JDK; nothing for the integrator to resolve |
| No compile-time or runtime dependency on the ad networks | reflection only — no type of theirs is referenced anywhere |
| The host keeps receiving its own impression callbacks | the single-slot listener is **decorated**, never replaced |
| Never crashes the host | every entry point degrades silently and logs |
| Survives minification | ships consumer ProGuard rules, verified in a real release build |
| Events survive process death | bounded SQLite queue with at-least-once delivery |
| Delivery can be batched once a day | `DispatchPolicy.DAILY_BATCH` plus a `JobScheduler` job |
| Measured, not guessed | the cost in the consumer APK is measured below |

Built with Gradle 8.9, AGP 8.7.3, Kotlin 2.0.0, JDK 21. `minSdk 21`, `compileSdk 35`.

### Building it

```bash
./gradlew :app:installDebug          # run the demo app
./gradlew :impression-tracker:test   # unit tests
```

An Android SDK installation is required; point Gradle at it with `ANDROID_HOME`
or a `local.properties` containing `sdk.dir`.

---

## Quick start

```bash
# 1. install the app
./gradlew :app:installDebug

# 2. optional: run a backend that accepts the impressions
python3 tools/fake-impression-server.py

# 3. watch what the SDK does
adb logcat -s ImpressionTracker
```

Then press **Load**, wait for the ad, press **Show**. Every impression appears
twice in the log: once from the app itself, once from this SDK.

The default endpoint is `http://10.0.2.2:8080/impressions`. Inside an Android
emulator `10.0.2.2` is the loopback address of the machine hosting it, so the
script above receives what the emulator sends. On a physical device, point the
SDK at the machine's LAN address instead — see [Configuration](#configuration).

Without a backend nothing breaks: delivery fails, a warning is logged, and the
impressions stay queued until the next attempt succeeds.

---

## How the SDK starts without touching the app

The host application never calls an `init` method, because its source may not be
changed. Android solves this for us: it instantiates **every `ContentProvider`
declared in the merged manifest** while the process starts. The SDK declares one,
the manifest merger copies it into the host's manifest, and the platform hands
the SDK control for free.

Two moments matter, and `TrackerInitProvider` covers both:

| Moment | What happens |
| --- | --- |
| `ContentProvider.onCreate()` | runs **before** `Application.onCreate()` — too early to hook, the host has not registered its listeners yet |
| a runnable posted to the main looper | runs **after** `Application.onCreate()` returns |

The ordering is guaranteed rather than lucky: both steps happen while the main
looper processes a single message, and the looper cannot start the next message
before finishing the current one. This is visible in logcat:

```
13:34:58.707  D/ImpressionTracker    Bootstrapping from ContentProvider
13:34:58.723  I/BetaAds  BetaAds initialised (lazy)   <- host's onCreate
13:34:58.726  D/ImpressionTracker    Host application ready
```

Integrating the SDK is therefore one line in the host's build script and nothing
else — no code, no manifest entry, no ProGuard rule:

```kotlin
implementation(project(":impression-tracker"))
```

---

## Intercepting the two networks

The two networks expose impressions very differently, and that difference drives
the whole design.

| | `BetaAds` | `AlphaAds` |
| --- | --- | --- |
| API | `addImpressionListener` / `removeImpressionListener` | `setAdImpressionDataListener` |
| Storage | `CopyOnWriteArrayList` | a single private field |
| Adding a listener | additive | **replaces** whatever was there |

`BetaAds` is straightforward: the SDK registers one more listener next to the
host's own, through the public API. Nothing the host did is affected.

`AlphaAds` has no `add` method — only a setter with a single slot. Registering the
SDK's listener would throw the host's away and silence its `New Impression` log,
which the host must never notice. So `AlphaAdNetworkHook` applies the **decorator pattern
through reflection**:

```
before:  [slot] -> host's listener
after:   [slot] -> SDK's Proxy -> host's listener
                              \-> SDK's own handling
```

It reads the listener the host installed, wraps it in a `java.lang.reflect.Proxy`
that forwards every call and additionally reports the impression, and writes the
wrapper back. The host is served **first**, so its behaviour is unchanged even if
the SDK's own handling fails. An exception thrown by the host's listener is
re-thrown unwrapped, so the ad network sees exactly what it would have seen
without the decorator.

Two details make this reliable:

- **The slot is found by type, not by name.** The network keeps its collaborators
  in an obfuscated service locator. Rather than depending on obfuscated field and
  method names, the hook walks the locator's static fields and picks the one whose
  type declares a field of the listener's type. Renaming members inside the
  network does not break it.
- **Attachment is idempotent and repeated.** The network stores the listener on a
  background executor, so right after the host's `onCreate` the slot may still be
  empty and the host's own write may still be pending. `ensureAttached()` compares
  the slot with the SDK's wrapper and re-wraps when they differ. It is called
  again on activity lifecycle events — always long before an ad can be shown.

---

## No runtime dependency on the ad networks

The SDK never references a type from either network. Classes are reached with
`Class.forName`, listeners are created with `Proxy`, and payloads are read
through their getters. A network that is missing from the host application is
simply reported and skipped.

This is checkable rather than a claim:

```bash
./gradlew :impression-tracker:dependencies --configuration releaseRuntimeClasspath
```

```
releaseRuntimeClasspath
\--- org.jetbrains.kotlin:kotlin-stdlib:2.0.0
     \--- org.jetbrains:annotations:13.0
```

`kotlin-stdlib` comes with the Kotlin plugin and the host already has it. There
is no AndroidX, no coroutines, no networking, DI or JSON library — everything
else is the Android framework and the JDK. JUnit and MockK are test-only and
never reach the published AAR.

---

## Architecture

```
dev.leliukh.impressiontracker/              public API: ImpressionTracker,
                                              ImpressionTrackerConfig, DispatchPolicy
└── internal/
    ├── domain/         the core: model, ports, use cases. Pure Kotlin.
    ├── interception/   incoming adapter - all reflection lives here
    ├── data/           outgoing adapters - SQLite, HTTP, logcat, clock
    └── platform/       Android entry points, scheduling, dependency graph
```

This is clean architecture; the dependency rule points inwards and
`domain/` contains no `import android.*` at all:

```bash
grep -r "import android" \
  impression-tracker/src/main/java/dev/leliukh/impressiontracker/internal/domain/
```

An SDK has no presentation layer, so instead of the usual
`data / domain / presentation` split the adapters are separated by **direction**,
which is the ports-and-adapters vocabulary:

| Package | Direction | Meaning |
| --- | --- | --- |
| `interception` | driving | the world calls us — an impression arrives |
| `data` | driven | we call the world — store, send, log |
| `platform` | neither | Android entry points and the object graph |

Everything below the public API is `internal`, both by modifier and by package.

---

## Storage and delivery

An impression travels like this:

```
ad network -> hook -> mapper -> TrackImpression -> logger
                                                \-> queue (SQLite) -> transport (HTTP)
```

- **Logging happens on the calling thread** — it costs microseconds and keeps the
  SDK's message next to the host's own in logcat.
- **Storing and sending do not.** They touch disk and network, and the caller is a
  thread owned by an ad network which the SDK has no business blocking. Both run
  on the SDK's single background thread.
- **The queue is durable.** Impressions survive process death and are delivered
  after the next launch. It is capped at 1000 entries; when full, the oldest are
  dropped, because recent impressions are the more useful ones.
- **Entries are deleted only after the backend accepts them.** A lost response
  therefore causes a resend rather than a lost impression — at-least-once
  delivery, with de-duplication left to the backend.
- **A failed batch ends the flush** instead of being retried on the spot.
  Retrying against an unreachable backend would keep the radio awake for nothing;
  the impressions stay queued for the next attempt.

The daily job is scheduled under **both** dispatch policies: for `DAILY_BATCH` it
is the only delivery path, for `IMMEDIATE` it is the safety net, since a failed
delivery is otherwise only retried on the next impression — which may never come.

---

## Configuration

Calling anything is optional. Settings are resolved in this order, each level
overriding the previous one:

1. defaults in `ImpressionTrackerConfig.DEFAULT`
2. `meta-data` in the host application's manifest
3. `ImpressionTracker.configure(...)` from code

```xml
<meta-data android:name="dev.leliukh.impressiontracker.ENDPOINT"
           android:value="https://example.com/impressions" />
<meta-data android:name="dev.leliukh.impressiontracker.DISPATCH_POLICY"
           android:value="DAILY_BATCH" />
```

The manifest is read inside `ContentProvider.onCreate`, before the host's
`Application.onCreate` runs, so a host that calls `configure` has the last word.
Settings are passed to collaborators as functions rather than values, because
they are created once and kept for the life of the process while `configure` may
be called at any moment.

The SDK contributes three things to the host's manifest, all through the merger:

| Entry | Why |
| --- | --- |
| `INTERNET` | delivering impressions |
| `ACCESS_NETWORK_STATE` | required by the daily job's connectivity constraint |
| a network security config | permits cleartext **only** to `localhost`, `127.0.0.1` and `10.0.2.2`, so a development backend is reachable without touching the host's policy for any other address |

---

## Minified builds

The app's release build runs R8, which renames everything the SDK looks up by
name. The SDK therefore ships `consumer-rules.pro`, applied to the host
automatically — integrating it still requires no ProGuard work from the host.

The two networks need very different rules, and the contrast is instructive:

| Network | Rules | Why |
| --- | --- | --- |
| `BetaAds` | three narrow rules | the SDK only uses its public API |
| `AlphaAds` | the whole package kept | the SDK has to replace a private field |

Narrow rules are not enough for `AlphaAds`. R8 merges the host's listener into a
synthetic class and then **narrows the field's declared type** to that class,
since nothing else is ever stored there. After that no `Proxy` can be written
into it at all:

```
java.lang.IllegalArgumentException: field a1.x.b has type T.d, got $Proxy0
```

Keeping that package whole is the price of reaching into internals that were
never meant to be reached. Measured on this project's release build, the broad
rule costs **648 bytes** of the SDK's total footprint; against a large
closed-source network it would cost whatever that library weighs, which is the
real argument for an additive listener API instead.

**Total cost in the consumer APK**, measured as the difference between release
builds of the demo app with and without the SDK dependency:

| | Size |
| --- | --- |
| APK without the SDK | 1 161 811 bytes |
| APK with the SDK | 1 172 207 bytes |
| **Delta** | **10 396 bytes — 10.2 KB, 0.89%** |

> **Note:** Android Studio highlights the ad network names in `consumer-rules.pro`
> as unresolved. That is expected and is in fact evidence that the requirement is
> met: those classes are not on the SDK module's classpath. The rules are applied
> when the **app** is built, where the classes do exist — `mapping.txt` confirms
> the kept names survive.

Verified by installing a real release build with R8 enabled: both networks are
hooked, impressions are logged and delivered.

---

## Inspecting a closed-source SDK

Integrating with a library that ships only an obfuscated binary comes up often
enough to be worth a recipe. This is the one used to work out that `AlphaAds`
keeps its listener in a single field behind a service locator, and it needs
nothing beyond `javap` from the JDK.

In this repository the two networks are open stand-ins, so their structure is
plain to read. The recipe is kept because the technique is the transferable
part, and because the hook is written as if the networks were still opaque: it
looks the slot up by **type**, never by a name an optimizer is free to change.

**What obfuscation cannot take away** is what you navigate by:

| Survives | Why |
| --- | --- |
| public type names | they are the API; renaming them would break every consumer |
| string literals | renaming them would break error messages and logic |
| method signatures | parameter and return types stay, only names change |
| calls into Android and the JDK | `Log.i`, `Executor.execute` and friends cannot be renamed |

**Step 1 — unpack.** An AAR is a zip, and the `classes.jar` inside it is a zip
too:

```bash
unzip -o network.aar && unzip -o classes.jar -d classes
```

**Step 2 — dump everything once**, then work in the text file rather than
re-running the tool:

```bash
javap -p -c -cp classes <every class> > dump.txt
```

**Step 3 — search by type, not by name.** This is the whole trick. A field named
`b` says nothing, but its type says everything:

```bash
grep -n "AlphaImpressionListener" dump.txt
```

```
private com.alphaads.sdk.AlphaImpressionListener b;   <- the slot
getfield  b:L...AlphaImpressionListener;                <- read here
invokeinterface AlphaImpressionListener.onImpression    <- dispatched here
```

One grep found where the listener is stored and where it is called from.

**Step 4 — read the public facade's bytecode** to learn the path to what you
found. JVM instructions carry full signatures in their comments, so they read
almost like source:

```
getstatic     o.a : Lo;          <- take the locator singleton
invokevirtual o.e() : Lm;        <- ask it for the holder
invokevirtual m.a(AlphaImpressionListener)V
```

That is where the service locator came from. Note that `getstatic` and `getfield`
are different instructions, so the bytecode also tells you whether a field is
static — which matters, because a static field is the only thing reflection can
reach without already holding an instance.

**Bonus — Kotlin leaks its parameter names.** Every public Kotlin function starts
with `Intrinsics.checkNotNullParameter(value, "name")`, and the name is a string
literal that obfuscators leave alone:

```bash
grep -o '// String [a-z][A-Za-z]*' dump.txt | sort -u
# adRepository  adStateNotifier  adsHolder  impressionNotifier  bidderName ...
```

Real names for classes called `b`, `m` and `o`, straight out of the binary.

**Finally, carry the idea into the code.** Names found this way are the fragile
part, so depend on as few of them as possible: `findListenerSlot` looks up one
class by name and finds everything else by type, which is why renaming members
inside the network does not break the SDK.

---

## Tests

```bash
./gradlew :impression-tracker:testDebugUnitTest          # 27 JVM tests
./gradlew :impression-tracker:connectedDebugAndroidTest  # 18 tests, needs a device
```

JVM tests cover the domain and the parts that can be exercised without the
framework: the exact wording of the log message, both payload mappers,
dispatch policy behaviour, flushing, and the start-up sequence.

Instrumented tests cover what only exists on a device: the SQLite queue
(ordering, the size cap, surviving a reopen, deleting only what was delivered),
the `JobScheduler` registration, and the HTTP transport — the latter against a
real `ServerSocket`, so the request body is inspected exactly as a backend would
see it.

---

## Known limitations

**The SDK runs in the main process only.** Android creates providers in every
process of an application, and a second instance would open the same queue
database and compete with the first for its locks. Real multi-process support
would mean routing writes through a single writer — a `ContentProvider` or a
bound `Service` living in the main process — or keeping a database per process
and merging them before delivery. Write-ahead logging is not an answer here:
Android's connection pool is per process.

**The ad network's internals are a moving target.** The `AlphaAds` hook depends
on one class name. A new version of that library may rename it, after which
the hook reports `AlphaAds internals changed, impressions will be missed`
and its impressions are lost — visibly, and without crashing. The proper fix is
not on this side: it is an `addImpressionListener` method in that network's
public API, exactly like the other network already has. That would replace the
entire hook with five lines.

**Database upgrades drop the queue.** `onUpgrade` recreates the table instead of
migrating it, losing at most a day of undelivered telemetry. Writing and
maintaining migrations for disposable data costs more than it saves.

**Force-stopping the app cancels the daily job.** A user who force-stops the app
from Settings every day would, under `DAILY_BATCH`, never see a delivery. This is
platform behaviour rather than an SDK defect — force-stop means "stop all
background work". It could be closed by flushing at start-up when the oldest
queued impression is older than a day, which keeps the once-a-day promise; it is
not implemented because the scheduled job already keeps that promise in every
case that does not involve a force-stop.

---

## Packaging notes

The app depends on the SDK as a Gradle module:

```kotlin
implementation(project(":impression-tracker"))
```

This keeps one source of truth and a single-command build. The module is a real
Android library and produces a real AAR:

```bash
./gradlew :impression-tracker:assembleRelease
# impression-tracker/build/outputs/aar/impression-tracker-release.aar
```

That AAR could be dropped into `app/libs/` and consumed with
`implementation(files(...))`, the way a closed-source SDK usually arrives. It is
not done here on purpose: committing a build output invites it to drift out of sync
with the sources it was built from, and the build stops being reproducible in one
step.

In production an SDK is not shipped as a file at all. It would be published with
the `maven-publish` plugin to an internal repository and consumed by coordinates
— giving versioning, reproducible builds and transitive metadata through the POM.
That is infrastructure a demonstration repository does not need, so it is
described rather than built.
