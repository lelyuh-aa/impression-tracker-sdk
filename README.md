# Impression Tracker SDK

An Android SDK that intercepts every ad impression reported by third-party ad
network SDKs it has **no compile-time dependency on**, logs it, stores it in a
durable queue and delivers it to a backend as a daily batch.

It installs itself into a host application through a single Gradle dependency.
No changes to the host application's source code are required.

```
Intercepted impression event:
- ID: 44ce9d81-5007-4921-8ed2-ea96605d8880
- Bidder Name: KADEN
```

## What this project demonstrates

- **Zero-touch integration** — the SDK boots itself through a `ContentProvider`
  contributed by the manifest merger, so adding one line to `build.gradle.kts`
  is the entire integration.
- **Intercepting a closed-source SDK** — one of the networks exposes a single
  listener slot that the host application already occupies. The SDK locates that
  slot reflectively, wraps the host's listener in a `java.lang.reflect.Proxy`
  decorator and puts it back, so the host keeps receiving its own callbacks.
- **No third-party libraries** — Android SDK only, no dependency the integrator
  has to resolve and no diamond-dependency risk.
- **Durable delivery** — a bounded SQLite queue with at-least-once semantics and
  a daily batch dispatch via `JobScheduler`.
- **Survives minification** — ships its own consumer ProGuard rules, verified in
  a release build, with the cost in the consumer APK measured rather than guessed.
- **Never crashes the host** — every entry point degrades silently.

Two stub ad network modules stand in for closed-source third-party SDKs, so the
project builds, runs and demonstrates the interception from a clean clone.

## Status

The implementation is being migrated into this repository.

## License

MIT — see [LICENSE](LICENSE).
