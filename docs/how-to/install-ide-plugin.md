# Install the IDE plugin

Katalyst is annotation-free: it discovers routes, middleware, WebSockets, exception handlers,
services, components, repositories, tables, event handlers, scheduled jobs, migrations,
initializers, and config loaders by convention at startup. Because nothing in *your* code calls
those entrypoints directly, the IDE's static analysis can't see that they're used and reports
them as **unused**.

The **Katalyst Support** plugin teaches IntelliJ IDEA and Android Studio those conventions, so
the editor treats framework entrypoints as live code. Install it and you no longer annotate
entrypoints with `@Suppress("unused")`.

## Install from the IDE

1. Open **Settings/Preferences → Plugins**.
2. Select the **Marketplace** tab.
3. Search for **Katalyst Support**.
4. Click **Install**, then restart the IDE when prompted.

You can also install it from the
[JetBrains Marketplace page](https://plugins.jetbrains.com/plugin/32380-katalyst-support).

## What you get

- **No more `@Suppress("unused")`.** Every Katalyst entrypoint is recognized as used, so the
  "never used" warning disappears without annotations.
- **Gutter icons** next to each discovered entrypoint, with a tooltip naming its kind (route,
  service, event handler, and so on).
- **Entrypoint signature inspection** that warns when a function *looks* like an entrypoint but
  won't be discovered — for example, it's `private`, or it has a Ktor receiver and a route-like
  name but is missing the `katalyst*` DSL call.
- **Scheduler validation**: invalid cron expressions are flagged in the editor with the exact
  field error (instead of only failing at startup), and duplicate job names within a `jobs { }`
  block are reported.
- **Cron expressions rendered in plain English** inline — `0 0 2 * * ?` shows as
  *Every day at 02:00*.
- **Event handler type check** that warns when an `EventHandler<T>`'s `eventType` doesn't match
  its type argument.

## Compatibility

The plugin supports IntelliJ IDEA and Android Studio on build **2024.2 (242)** and newer, and
works in both the K1 and K2 Kotlin analysis modes.

## Without the plugin

The plugin is optional — your application compiles and runs exactly the same without it. If you
don't install it, the IDE will flag discovered entrypoints as unused; that's a cosmetic editor
warning, not a compilation error. The plugin simply removes the noise so you can keep your code
free of `@Suppress` annotations.
