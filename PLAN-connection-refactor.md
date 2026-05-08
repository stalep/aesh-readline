# Connection Interface Refactoring Plan

The `Connection` interface has grown to ~60+ methods across ~1300 lines. This
document outlines the options and trade-offs for refactoring it.

**File:** `terminal-api/src/main/java/org/aesh/terminal/Connection.java`

---

## Current State

The interface currently covers six distinct responsibilities:

### A. Core I/O (~20 abstract methods)
The foundational contract -- handlers, encoding, open/close:
```
device(), size(), getSizeHandler(), setSizeHandler(),
getSignalHandler(), setSignalHandler(), getStdinHandler(),
setStdinHandler(), stdoutHandler(), setCloseHandler(),
getCloseHandler(), close(), openBlocking(), openNonBlocking(),
put(), getAttributes(), setAttributes(), inputEncoding(),
outputEncoding(), supportsAnsi()
```

### B. Writing Convenience (default methods)
```
write(String), enterRawMode(), getCursorPosition()
```

### C. Terminal Queries (default methods, ~20 methods)
OSC, DA1, DA2, DECRQM queries with timeout parameters:
```
queryTerminal(), queryOsc(), queryForegroundColor(),
queryBackgroundColor(), queryCursorColor(), queryThemeMode(),
queryPaletteColor(), queryBatchOsc(), queryColors(),
queryPaletteColors(), queryAnsi16Colors(),
queryPrimaryDeviceAttributes(), querySecondaryDeviceAttributes(),
queryDeviceAttributes(), queryImageProtocol(),
querySynchronizedOutput(), queryGraphemeClusterMode(),
queryColorCapability(), queryWithResponseBuffering()
```

### D. Capability Detection (default methods, ~15 methods)
`supports*()` checks that inspect `Device` or `DeviceAttributes`:
```
supportsOscQueries(), supportsOscQueries(DeviceAttributes),
querySupportsOscQueries(), supportsThemeQuery(),
supportsSynchronizedOutput(), supportsShellIntegration(),
supportsGraphemeClusterMode(), supportsHyperlinks(),
supportsOscCode(), supportsPaletteQuery(),
supportsColorQuery(), supportsClipboard(), supportsImages()
```

### E. Feature Toggles (default methods)
Enable/disable terminal modes:
```
enableSynchronizedOutput(), disableSynchronizedOutput(),
enableGraphemeClusterMode(), disableGraphemeClusterMode(),
enableThemeChangeNotification(), disableThemeChangeNotification()
```

### F. Semantic Writers (default methods)
Higher-level output operations:
```
writePromptStart(), writePromptEnd(), writeCommandStart(),
writeCommandFinished(), writeCommandFinished(int),
writeHyperlink(url, text), writeHyperlink(url, text, id),
writeClipboard(text)
```

---

## Known Issues Within Connection

### Issue 1: `stdoutHandler()` breaks the handler naming pattern

All other handlers:
```java
Consumer<Size>   getSizeHandler()    / setSizeHandler(Consumer<Size>)
Consumer<Signal> getSignalHandler()  / setSignalHandler(Consumer<Signal>)
Consumer<int[]>  getStdinHandler()   / setStdinHandler(Consumer<int[]>)
Consumer<Void>   getCloseHandler()   / setCloseHandler(Consumer<Void>)
Consumer<TerminalTheme> getThemeChangeHandler() / setThemeChangeHandler(...)
```

But stdout:
```java
Consumer<int[]> stdoutHandler()   // no "get" prefix, no setter
```

**Options:**
- a) Rename to `getStdoutHandler()` -- consistent but no setter exists.
- b) Leave as-is since stdout is write-only (set at construction, never changed).
- c) Rename to `output()` to align with the bare-noun style of `device()`, `size()`.

### Issue 2: Mixed getter conventions

The abstract methods mix bare-noun and `get`-prefix styles:
- Bare-noun: `device()`, `size()`, `inputEncoding()`, `outputEncoding()`
- `get`-prefix: `getAttributes()`, `getSizeHandler()`, `getCloseHandler()`

**Options:**
- a) Standardize on bare-noun style (modern Java) -- deprecate `get*` variants.
- b) Standardize on `get`-prefix (JavaBeans) -- deprecate bare-noun variants.
- c) Leave as-is to avoid massive API churn.

### Issue 3: Theme change handler pattern inconsistency

Theme change handler has both a getter/setter pattern AND convenience methods:
```java
void setThemeChangeHandler(Consumer<TerminalTheme>)
Consumer<TerminalTheme> getThemeChangeHandler()
void enableThemeChangeNotification()
void enableThemeChangeNotification(Consumer<TerminalTheme> handler)
void disableThemeChangeNotification()
```

The `enableThemeChangeNotification(handler)` overload combines setting the handler
and enabling the feature in one call, which overlaps with `setThemeChangeHandler()`.

---

## Refactoring Options

### Option A: Extract Sub-Interfaces (Interface Segregation)

Split `Connection` into focused interfaces and have `Connection` extend them all:

```java
public interface TerminalQueryable {
    <T> T queryTerminal(String query, long timeoutMs, Function<int[], T> parser);
    int[] queryForegroundColor(long timeoutMs);
    int[] queryBackgroundColor(long timeoutMs);
    TerminalTheme queryThemeMode(long timeoutMs);
    DeviceAttributes queryDeviceAttributes(long timeoutMs);
    ImageProtocol queryImageProtocol(long timeoutMs);
    // ... all other query* methods
}

public interface TerminalCapabilities {
    boolean supportsAnsi();
    boolean supportsOscQueries();
    boolean supportsThemeQuery();
    boolean supportsSynchronizedOutput();
    boolean supportsShellIntegration();
    boolean supportsHyperlinks();
    boolean supportsGraphemeClusterMode();
    boolean supportsClipboard();
    // ... all other supports* methods
}

public interface TerminalFeatures {
    Connection enableSynchronizedOutput();
    Connection disableSynchronizedOutput();
    Connection enableGraphemeClusterMode();
    Connection disableGraphemeClusterMode();
    Connection writePromptStart();
    Connection writePromptEnd();
    Connection writeCommandStart();
    Connection writeCommandFinished();
    Connection writeCommandFinished(int exitCode);
    Connection writeHyperlink(String url, String text);
    Connection writeClipboard(String text);
    // ... all other feature/write methods
}

public interface Connection
    extends TerminalQueryable, TerminalCapabilities, TerminalFeatures, AutoCloseable {
    // Only core I/O methods remain here
    Device device();
    Size size();
    // ...
}
```

**Pros:**
- Clean separation of concerns
- Users can depend on only the sub-interface they need
- Fully backward compatible (`Connection` still has all methods)
- Easier to document and navigate

**Cons:**
- More files to maintain
- Implementation classes unchanged (still implement Connection)
- Sub-interfaces may have dependencies on each other (queries need `stdoutHandler`)

---

### Option B: Delegate Object Pattern

Provide accessor methods that return focused helper objects:

```java
public interface Connection extends AutoCloseable {
    // Core I/O (unchanged)
    Device device();
    Size size();
    // ...

    // Accessor for query capabilities
    default TerminalQueries queries() {
        return new TerminalQueries(this);
    }

    // Accessor for feature toggles
    default TerminalFeatures features() {
        return new TerminalFeatures(this);
    }
}
```

Usage would change from:
```java
conn.queryThemeMode(500);
conn.enableSynchronizedOutput();
```
To:
```java
conn.queries().themeMode(500);
conn.features().enableSynchronizedOutput();
```

**Pros:**
- Connection interface stays small
- Clear namespacing for related methods
- Method names on sub-objects can be shorter (e.g., `queries().theme()`)

**Cons:**
- Breaking API change for all call sites using query/feature methods
- Object allocation on each call (unless cached)
- Harder to override in custom Connection implementations

---

### Option C: Keep Current Structure, Improve Organization

Don't split the interface, but improve discoverability:

- Add section Javadoc comments grouping methods by responsibility
- Add `@Category` or custom annotations for IDE navigation
- Move default method implementations to a companion `Connections` utility class
- Standardize naming conventions within the existing interface

**Pros:**
- Zero breaking changes
- Quick to implement

**Cons:**
- Interface remains large
- Doesn't solve the Interface Segregation issue

---

## Recommendation

**Option A** is the recommended approach. It preserves full backward compatibility
(since `Connection extends` all the sub-interfaces) while giving users and
implementers clearer structure. The sub-interfaces also serve as documentation --
they make it obvious which methods are core vs. optional capabilities.

The implementation order would be:

1. Create the new sub-interfaces with the default method implementations
   moved from `Connection`.
2. Make `Connection` extend the new sub-interfaces.
3. Remove the default method bodies from `Connection` (they now live in
   the sub-interfaces).
4. Update Javadoc.
5. No call sites need to change -- `Connection` still has every method.

---

## Handler Naming Decision

Regardless of which option is chosen, the handler naming should be standardized.

**Recommended approach:** Since the project already uses bare-noun style in many
places and it is the more modern convention, standardize on:

| Current | Proposed | Notes |
|---------|----------|-------|
| `getSizeHandler()` | `sizeHandler()` | Deprecate `get` form |
| `getSignalHandler()` | `signalHandler()` | Deprecate `get` form |
| `getStdinHandler()` | `stdinHandler()` | Deprecate `get` form |
| `getCloseHandler()` | `closeHandler()` | Deprecate `get` form |
| `getThemeChangeHandler()` | `themeChangeHandler()` | Deprecate `get` form |
| `stdoutHandler()` | *(no change)* | Already bare-noun |
| `getAttributes()` | `attributes()` | Deprecate `get` form |
| `getColorDepth()` | `colorDepth()` | Deprecate `get` form |
| `getColorCapability()` | `colorCapability()` | Deprecate `get` form |
| `getTerminalType()` | `terminalType()` | Deprecate `get` form |

The `set*` methods remain unchanged -- `setSizeHandler()`, `setSignalHandler()`, etc.
The setter having a prefix while the getter does not is an accepted pattern in
modern Java (cf. `java.time` API, records).

---

## Checklist

- [ ] Decide on refactoring option (A, B, or C)
- [ ] Decide on handler naming convention
- [ ] If Option A: design the sub-interface boundaries
- [ ] If Option A: decide which module the sub-interfaces belong to (terminal-api)
- [ ] Implement the chosen approach
- [ ] Standardize getter naming within Connection
- [ ] Fix `stdoutHandler()` naming
- [ ] Update all Javadoc
- [ ] Run full test suite
