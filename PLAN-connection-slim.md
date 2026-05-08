# Plan: Slim Connection with TerminalFeatures Delegate

## Motivation

Connection has grown to ~60 methods. Even with sub-interface grouping,
anyone receiving a `Connection` sees all methods in autocomplete. This makes
the API hard to approach for new adopters who just need basic terminal I/O.

**Goal:** A slim Connection with core I/O methods, and a `terminal()` accessor
that returns a `TerminalFeatures` object for all advanced terminal-specific
functionality.

---

## Design

### Two Concepts, Cleanly Separated

**1. Connection** — Core terminal I/O (~20 methods)

What you need to read input, write output, and manage a terminal session.
Familiar to any Java developer.

**2. TerminalFeatures** — Advanced terminal features (~40 methods)

A plain class wrapping a Connection. Holds all terminal-specific quirks:
queries (OSC, DA1/DA2, DECRQM), capability detection, semantic writes
(shell integration, hyperlinks, clipboard), and mode toggles (synchronized
output, grapheme clusters, theme notifications).

Accessed via `conn.terminal()`.

---

## Connection Interface (~20 methods)

```java
public interface Connection extends Appendable, AutoCloseable {

    // ── Identity ──
    Device device();
    Size size();

    // ── Input ──
    Consumer<int[]> stdinHandler();
    void setStdinHandler(Consumer<int[]> handler);

    // ── Output ──
    Consumer<int[]> stdoutHandler();
    Connection write(String s);
    boolean put(Capability capability, Object... params);

    // ── Handlers ──
    Consumer<Signal> signalHandler();
    void setSignalHandler(Consumer<Signal> handler);
    Consumer<Size> sizeHandler();
    void setSizeHandler(Consumer<Size> handler);
    Consumer<Void> closeHandler();
    void setCloseHandler(Consumer<Void> closeHandler);
    Consumer<TerminalTheme> themeChangeHandler();
    void setThemeChangeHandler(Consumer<TerminalTheme> handler);

    // ── Lifecycle ──
    void openBlocking();
    void openNonBlocking();
    boolean reading();
    void close();

    // ── Terminal state ──
    Attributes attributes();
    void setAttributes(Attributes attr);
    Attributes enterRawMode();
    boolean supportsAnsi();
    Charset inputEncoding();
    Charset outputEncoding();

    // ── Advanced features ──
    default TerminalFeatures terminal() {
        return new TerminalFeatures(this);
    }

    // ── Standard I/O adapter ──
    default java.io.Writer asWriter() {
        return new ConnectionWriter(this);
    }

    default java.io.PrintWriter asPrintWriter() {
        return new java.io.PrintWriter(asWriter(), true);
    }
}
```

### Why `themeChangeHandler` stays on Connection

It follows the same handler pattern as `signalHandler`, `sizeHandler`, and
`closeHandler` — a getter/setter pair backed by EventDecoder in
AbstractConnection. The _enablement_ of theme notifications (sending the
mode query) moves to TerminalFeatures, but the handler itself stays on
Connection since EventDecoder dispatches to it.

### Why `supportsAnsi()` stays on Connection

It's used by core write methods and `enterRawMode()`. It's also a
fundamental property of the connection, not an advanced capability check.

---

## TerminalFeatures Class

A plain class wrapping a Connection reference. No Writer, no interface
inheritance — just a class that provides access to terminal-specific
features.

```java
public class TerminalFeatures {

    private final Connection connection;

    public TerminalFeatures(Connection connection) {
        this.connection = connection;
    }

    /** Access the underlying connection. */
    public Connection connection() {
        return connection;
    }

    // ══════════════════════════════════════════════
    // Queries (from current TerminalQueryable)
    // ══════════════════════════════════════════════

    /** Generic terminal query — send escape, wait for response. */
    public <T> T queryTerminal(String query, long timeoutMs,
            Function<int[], T> responseParser) { ... }

    /** Query DA1 device attributes. */
    public DeviceAttributes queryDeviceAttributes(long timeoutMs) { ... }

    /** Query DA2 secondary device attributes. */
    public SecondaryDeviceAttributes querySecondaryDeviceAttributes(long timeoutMs) { ... }

    /** Query terminal theme mode (light/dark). */
    public TerminalTheme queryThemeMode(long timeoutMs) { ... }

    /** Query foreground color (OSC 10). */
    public int[] queryForegroundColor(long timeoutMs) { ... }

    /** Query background color (OSC 11). */
    public int[] queryBackgroundColor(long timeoutMs) { ... }

    /** Query terminal name (XTVERSION). */
    public String queryTerminalName(long timeoutMs) { ... }

    /** Query a palette color (OSC 4). */
    public int[] queryPaletteColor(int index, long timeoutMs) { ... }

    /** Query DECRQM mode status. */
    public int queryModeStatus(int mode, long timeoutMs) { ... }

    /** Combined auto-detection of capabilities and theme. */
    public TerminalColorCapability queryColorCapability(long timeoutMs) { ... }

    // ══════════════════════════════════════════════
    // Capability Detection (from current TerminalCapabilities)
    // ══════════════════════════════════════════════

    /** Check if OSC queries are supported. */
    public boolean supportsOscQueries() { ... }

    /** Check if OSC queries are supported, with DA1 attributes. */
    public boolean supportsOscQueries(DeviceAttributes attrs) { ... }

    /** Get the color depth. */
    public ColorDepth colorDepth() { ... }

    /** Get color capabilities (depth + theme). */
    public TerminalColorCapability colorCapability() { ... }

    /** Check theme query support. */
    public boolean supportsThemeQuery() { ... }

    /** Check Mode 2026 (synchronized output) support. */
    public boolean supportsSynchronizedOutput() { ... }

    /** Check OSC 133 (shell integration) support. */
    public boolean supportsShellIntegration() { ... }

    /** Check Mode 2027 (grapheme cluster) support. */
    public boolean supportsGraphemeClusterMode() { ... }

    /** Check OSC 8 hyperlink support. */
    public boolean supportsHyperlinks() { ... }

    /** Detect terminal type from environment. */
    public Device.TerminalType getTerminalType() { ... }

    /** Check support for a specific OSC code. */
    public boolean supportsOscCode(Device.OscCode oscCode) { ... }

    /** Check OSC 4 palette query support. */
    public boolean supportsPaletteQuery() { ... }

    /** Check OSC 10/11 color query support. */
    public boolean supportsColorQuery() { ... }

    /** Check OSC 52 clipboard support. */
    public boolean supportsClipboard() { ... }

    // ══════════════════════════════════════════════
    // Semantic Writes (from current TerminalWriter)
    // ══════════════════════════════════════════════

    /** Write OSC 133 prompt start marker. */
    public Connection writePromptStart() { ... }

    /** Write OSC 133 prompt end marker. */
    public Connection writePromptEnd() { ... }

    /** Write OSC 133 command start marker. */
    public Connection writeCommandStart() { ... }

    /** Write OSC 133 command finished marker. */
    public Connection writeCommandFinished(int exitCode) { ... }

    /** Write OSC 8 hyperlink. */
    public Connection writeHyperlink(String url, String text) { ... }

    /** Write text to clipboard via OSC 52. */
    public Connection writeClipboard(String text) { ... }

    // ══════════════════════════════════════════════
    // Mode Toggles (from current TerminalWriter)
    // ══════════════════════════════════════════════

    /** Enable Mode 2026 (synchronized output). */
    public Connection enableSynchronizedOutput() { ... }

    /** Disable Mode 2026. */
    public Connection disableSynchronizedOutput() { ... }

    /** Enable Mode 2027 (grapheme cluster segmentation). */
    public Connection enableGraphemeClusterMode() { ... }

    /** Disable Mode 2027. */
    public Connection disableGraphemeClusterMode() { ... }

    /** Enable theme change notification via DSR. */
    public void enableThemeChangeNotification(Consumer<TerminalTheme> handler) { ... }

    /** Enable theme change notification with DA1 context. */
    public void enableThemeChangeNotification(
            Consumer<TerminalTheme> handler, DeviceAttributes attrs) { ... }
}
```

### What This Gets Right

- **Just a class.** No interface hierarchy, no `extends Writer`, no
  `implements` anything. Simple to understand, simple to instantiate.
- **One field.** Everything delegates back to the Connection for actual I/O.
  The class is a namespace, not a new layer.
- **Discoverable.** `conn.terminal().` and autocomplete shows you everything
  terminal-specific. Clear separation from Connection's core I/O.
- **Returns Connection.** Semantic write methods return Connection so you
  can chain back: `conn.terminal().writePromptStart().write("$ ")`.

---

## Writer Adapter (Separate Concern)

The `asWriter()` / `asPrintWriter()` adapter on Connection is independent
of TerminalFeatures. It bridges Connection to `java.io.Writer` for interop
with existing Java libraries.

```java
class ConnectionWriter extends java.io.Writer {
    private final Connection connection;

    ConnectionWriter(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        connection.write(new String(cbuf, off, len));
    }

    @Override
    public void write(String str) {
        connection.write(str);
    }

    @Override
    public void flush() { /* No-op: Connection output is unbuffered */ }

    @Override
    public void close() {
        connection.close();
    }
}
```

Usage:
```java
// Pass to any library expecting a Writer
someLibrary.render(conn.asWriter());

// Formatted output
conn.asPrintWriter().printf("User: %s, Age: %d%n", name, age);
```

This is additive and non-breaking — it can be added at any time.

---

## Usage Examples

### New user — basic terminal I/O
```java
TerminalConnection conn = new TerminalConnection();
conn.write("Hello, terminal!\n");
conn.setStdinHandler(input -> { /* handle keys */ });
conn.openBlocking();
```

Connection is clean and approachable. No query/capability noise.

### Advanced user — terminal features
```java
TerminalConnection conn = new TerminalConnection();
TerminalFeatures term = conn.terminal();

// Detect capabilities
DeviceAttributes da = term.queryDeviceAttributes(500);
if (term.supportsSynchronizedOutput()) {
    term.enableSynchronizedOutput();
}

// Shell integration
term.writePromptStart();
conn.write("$ ");
term.writePromptEnd();

// Theme detection
TerminalTheme theme = term.queryThemeMode(500);
```

### Interop with Java I/O
```java
// Works with any library expecting a Writer
Writer writer = conn.asWriter();
PrintWriter pw = conn.asPrintWriter();
pw.printf("Loading %d items...%n", count);
```

---

## Object Allocation

The default `terminal()` method on Connection creates a new TerminalFeatures
on each call. Cache it in AbstractConnection:

```java
public abstract class AbstractConnection implements Connection {
    private TerminalFeatures terminalFeatures;

    @Override
    public TerminalFeatures terminal() {
        if (terminalFeatures == null) {
            terminalFeatures = new TerminalFeatures(this);
        }
        return terminalFeatures;
    }
}
```

TelnetTtyConnection (which doesn't extend AbstractConnection) can do the
same in its own implementation.

---

## Migration Path

### Deprecation Period

Keep the current sub-interface methods on Connection but mark them
`@Deprecated`, delegating to `terminal()`:

```java
public interface Connection extends Appendable, AutoCloseable {
    // ... core methods ...

    default TerminalFeatures terminal() {
        return new TerminalFeatures(this);
    }

    /** @deprecated Use {@code terminal().queryThemeMode(timeoutMs)} */
    @Deprecated
    default TerminalTheme queryThemeMode(long timeoutMs) {
        return terminal().queryThemeMode(timeoutMs);
    }

    /** @deprecated Use {@code terminal().supportsClipboard()} */
    @Deprecated
    default boolean supportsClipboard() {
        return terminal().supportsClipboard();
    }

    // ... etc for all methods moving to TerminalFeatures ...
}
```

Existing code keeps working. IDE warnings guide migration. Remove deprecated
methods in the next major version.

### What Happens to the Sub-Interface Files?

**Convert them into the TerminalFeatures class.** The code is already
well-organized in TerminalQueryable, TerminalCapabilities, and
TerminalWriter. Converting from interface default methods to class methods
is mechanical:

- Change `Connection self = (Connection) this;` → use `this.connection`
- Change `default` methods → regular `public` methods
- Merge all three into TerminalFeatures (or keep as internal classes if
  the file gets too large)

The sub-interface files (TerminalQueryable.java, TerminalCapabilities.java,
TerminalWriter.java) would be deleted once the deprecated period ends, or
kept as package-private interfaces that TerminalFeatures implements for
internal structure.

### Steps

1. Create `TerminalFeatures` class with all methods from the three
   sub-interfaces, rewritten to use `this.connection` instead of
   `(Connection) this`
2. Add `terminal()` default method to Connection
3. Add `asWriter()` / `asPrintWriter()` default methods to Connection
4. Create `ConnectionWriter` adapter class
5. Cache `TerminalFeatures` in AbstractConnection
6. Mark all sub-interface methods `@Deprecated` on Connection, delegating
   to `terminal()`
7. Update internal callers (Readline, examples, tests) to use
   `conn.terminal().*` instead of `conn.*` for advanced features
8. Eventually: remove `extends TerminalQueryable, TerminalCapabilities,
   TerminalWriter` from Connection

---

## Comparison with Current State

| Aspect | Current (Sub-interfaces) | Proposed (TerminalFeatures) |
|--------|--------------------------|---------------------------|
| Connection surface | ~60 methods (all inherited) | ~20 methods + `terminal()` |
| Autocomplete noise | High | Low |
| Advanced features | Flat on Connection | Namespaced under `terminal()` |
| New user experience | Overwhelming | Clean, layered |
| Writer interop | None | `asWriter()` / `asPrintWriter()` |
| TerminalFeatures type | N/A | Plain class, no inheritance |
| Backward compat | Current state | Deprecation period |
| Implementation effort | Done | Moderate refactor |

---

## Summary

- **TerminalFeatures** is a plain class wrapping Connection. It provides
  access to all terminal-specific features: queries, capability detection,
  semantic writes, and mode toggles. No Writer, no interfaces — just a
  class.

- **Connection** stays slim (~20 methods): I/O, handlers, lifecycle,
  terminal state. Plus `terminal()` to access advanced features and
  `asWriter()` for standard Java I/O interop.

- **Migration** via deprecation: existing methods stay on Connection with
  `@Deprecated` annotations, delegating to `terminal()`. Callers migrate
  at their own pace.
