/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.tty;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Utility class to detect terminal color capabilities.
 * <p>
 * This detector can query the terminal to determine:
 * <ul>
 * <li>Color depth (8, 16, 256, or true color)</li>
 * <li>Background theme (light or dark)</li>
 * <li>Actual foreground and background RGB colors</li>
 * </ul>
 * <p>
 * Detection methods:
 * <ul>
 * <li>Environment variables (COLORTERM, TERM)</li>
 * <li>terminfo/infocmp database (max_colors capability)</li>
 * <li>OSC 10/11 queries for actual terminal colors</li>
 * </ul>
 * <p>
 * The detector supports caching to avoid repeated detection overhead.
 * Use {@link #detectCached(Connection)} for cached detection.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public final class TerminalColorDetector {

    /** Logger for this class. */
    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalColorDetector.class.getName());

    /**
     * Cached color capability (thread-safe via volatile).
     */
    private static volatile TerminalColorCapability cachedCapability;

    /**
     * Time when the cache was last updated (for optional expiration).
     */
    private static volatile long cacheTimestamp;

    /**
     * OSC escape sequence to query foreground color (OSC 10).
     * Response format: ESC ] 10 ; rgb:RRRR/GGGG/BBBB ST
     */
    private static final String OSC_QUERY_FOREGROUND = "\u001B]10;?\u0007";

    /**
     * OSC escape sequence to query background color (OSC 11).
     * Response format: ESC ] 11 ; rgb:RRRR/GGGG/BBBB ST
     */
    private static final String OSC_QUERY_BACKGROUND = "\u001B]11;?\u0007";

    /**
     * Default timeout for OSC queries in milliseconds.
     * Terminal responses can take 200-500ms depending on the terminal and system load.
     */
    private static final long DEFAULT_TIMEOUT_MS = 500;

    private TerminalColorDetector() {
        // Utility class
    }

    /**
     * Detect terminal color capabilities using cached result if available.
     * <p>
     * This method returns a cached result if one exists and hasn't expired.
     * Otherwise, it performs full detection and caches the result.
     *
     * @param connection the terminal connection
     * @return detected color capabilities (may be cached)
     */
    public static TerminalColorCapability detectCached(Connection connection) {
        TerminalColorCapability cached = cachedCapability;
        long timestamp = cacheTimestamp;

        // Check if cache is set
        if (cached != null) {
            return cached;
        }

        // Perform detection and cache result
        TerminalColorCapability result = detect(connection);
        cachedCapability = result;
        cacheTimestamp = System.currentTimeMillis();
        return result;
    }

    /**
     * Clear the cached color capability.
     * <p>
     * Call this when the terminal environment may have changed
     * (e.g., after a theme switch).
     */
    public static void clearCache() {
        cachedCapability = null;
        cacheTimestamp = 0;
    }

    /**
     * Get the cached color capability without performing detection.
     *
     * @return the cached capability, or null if not cached
     */
    public static TerminalColorCapability getCached() {
        return cachedCapability;
    }

    /**
     * Detect terminal color capabilities using all available methods.
     * <p>
     * This method combines environment variable detection, terminfo database,
     * and optional OSC terminal queries for the most complete detection.
     *
     * @param connection the terminal connection
     * @return detected color capabilities
     */
    public static TerminalColorCapability detect(Connection connection) {
        return detect(connection, true);
    }

    /**
     * Detect terminal color capabilities.
     *
     * @param connection the terminal connection
     * @param queryTerminal if true, send OSC queries to the terminal to detect
     *        actual colors (may cause brief flicker on some terminals)
     * @return detected color capabilities
     */
    public static TerminalColorCapability detect(Connection connection, boolean queryTerminal) {
        ColorDepth colorDepth = detectColorDepth(connection);
        TerminalTheme theme = TerminalTheme.UNKNOWN;
        int[] foregroundRGB = null;
        int[] backgroundRGB = null;
        int[] cursorRGB = null;
        java.util.Map<Integer, int[]> paletteColors = null;

        if (queryTerminal && connection != null && connection.supportsAnsi()) {
            try {
                // Use the Connection's synchronous query method
                // This uses direct terminal I/O and doesn't require an active reading thread
                TerminalColorCapability queryResult = connection.queryColorCapability(DEFAULT_TIMEOUT_MS);
                if (queryResult != null) {
                    foregroundRGB = queryResult.getForegroundRGB();
                    backgroundRGB = queryResult.getBackgroundRGB();
                    cursorRGB = queryResult.getCursorRGB();
                    paletteColors = queryResult.getPaletteColors();
                    theme = queryResult.getTheme();

                    LOGGER.log(Level.FINE, "Queried colors via Connection: FG=" + (foregroundRGB != null) +
                            ", BG=" + (backgroundRGB != null) + ", cursor=" + (cursorRGB != null) +
                            ", palette=" + (paletteColors != null ? paletteColors.size() : 0));
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to query terminal colors", e);
            }
        }

        // If we couldn't detect via OSC, try environment-based detection
        if (theme == TerminalTheme.UNKNOWN) {
            theme = detectThemeFromEnvironment();
        }

        // Use detected colorDepth if queryResult didn't provide one
        return new TerminalColorCapability(colorDepth, theme, foregroundRGB, backgroundRGB,
                cursorRGB, paletteColors);
    }

    // ==================== Synchronous Color Query ====================

    /** DCS prefix for tmux passthrough. */
    private static final String DCS_TMUX_PREFIX = "\u001BPtmux;\u001B";
    /** DCS suffix for tmux passthrough. */
    private static final String DCS_TMUX_SUFFIX = "\u001B\\";

    /**
     * Query terminal color capabilities using synchronous I/O.
     * <p>
     * This method uses direct terminal I/O and does NOT require an active
     * reading thread. It can be called before the connection is opened.
     * <p>
     * When running in tmux with passthrough enabled, this method will wrap
     * queries in DCS passthrough sequences to reach the outer terminal.
     *
     * @param connection the terminal connection (must be a TerminalConnection)
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return TerminalColorCapability with detected colors, or null if not supported
     */
    public static TerminalColorCapability queryColorCapability(TerminalConnection connection, long timeoutMs) {
        if (connection == null || !connection.supportsAnsi()) {
            return null;
        }

        Terminal terminal = connection.getTerminal();
        if (terminal == null) {
            return null;
        }

        // Check for terminals that don't support OSC queries
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null &&
                terminalEmulator.toLowerCase().contains("jetbrains")) {
            LOGGER.log(Level.FINE, "JetBrains/JediTerm detected - OSC queries not supported");
            return null;
        }

        boolean inTmux = isRunningInTmux();
        boolean canUseTmuxPassthrough = shouldUseTmuxPassthrough();

        // Skip OSC support check when in tmux (tmux may not accurately reflect outer terminal)
        if (!inTmux) {
            Device device = connection.device();
            if (device != null && !device.supportsOscQueries()) {
                LOGGER.log(Level.FINE, "OSC queries not supported by device");
                return null;
            }
        }

        // Check OSC support for cursor and palette colors
        // In tmux, assume support if passthrough is enabled
        boolean supportsCursor = inTmux || connection.supportsOscCode(Device.OscCode.CURSOR_COLOR);
        boolean supportsPalette = inTmux || connection.supportsPaletteQuery();

        // Strategy 1: Try plain OSC query (works for normal terminals and tmux 3.3+ native)
        LOGGER.log(Level.FINE, "Trying plain OSC color query");
        TerminalColorCapability result = doSynchronousColorQuery(
                connection, terminal, timeoutMs, supportsCursor, supportsPalette, false);

        // In tmux: Plain query may get FG/BG from tmux native (3.3+), but OSC 4 (palette)
        // typically needs DCS passthrough to reach the outer terminal
        boolean needPalettePassthrough = inTmux && canUseTmuxPassthrough && supportsPalette
                && (result == null || result.getPaletteColors() == null || result.getPaletteColors().isEmpty());

        if (needPalettePassthrough) {
            LOGGER.log(Level.FINE, "Trying tmux DCS passthrough for palette colors");
            // Query only palette colors via passthrough, merge with existing result
            TerminalColorCapability paletteResult = doSynchronousColorQuery(
                    connection, terminal, timeoutMs / 2, false, true, true);

            if (paletteResult != null && paletteResult.getPaletteColors() != null
                    && !paletteResult.getPaletteColors().isEmpty()) {
                LOGGER.log(Level.FINE, "DCS passthrough palette query succeeded");
                // Merge palette colors into result
                if (result != null) {
                    result = new TerminalColorCapability(
                            result.getColorDepth(),
                            result.getTheme(),
                            result.getForegroundRGB(),
                            result.getBackgroundRGB(),
                            result.getCursorRGB() != null ? result.getCursorRGB() : paletteResult.getCursorRGB(),
                            paletteResult.getPaletteColors());
                } else {
                    result = paletteResult;
                }
            }
        } else if (result == null || !hasColors(result)) {
            // No colors from plain query - try full DCS passthrough
            if (inTmux && canUseTmuxPassthrough) {
                LOGGER.log(Level.FINE, "Trying tmux DCS passthrough for all colors");
                result = doSynchronousColorQuery(
                        connection, terminal, timeoutMs, supportsCursor, supportsPalette, true);
            }
        }

        if (result != null && hasColors(result)) {
            LOGGER.log(Level.FINE, "Color query succeeded");
        }

        return result;
    }

    /**
     * Check if any colors were detected.
     */
    private static boolean hasColors(TerminalColorCapability cap) {
        return cap != null && (cap.getForegroundRGB() != null || cap.getBackgroundRGB() != null);
    }

    /**
     * Perform synchronous color query using direct terminal I/O.
     */
    private static TerminalColorCapability doSynchronousColorQuery(
            Connection connection, Terminal terminal, long timeoutMs,
            boolean supportsCursor, boolean supportsPalette, boolean useDcsPassthrough) {

        int[] foregroundRGB = null;
        int[] backgroundRGB = null;
        int[] cursorRGB = null;
        java.util.Map<Integer, int[]> paletteColors = null;

        // Save current attributes and enter raw mode
        Attributes savedAttributes = connection.getAttributes();
        Attributes rawAttributes = new Attributes(savedAttributes);
        rawAttributes.setLocalFlags(
                java.util.EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO),
                false);
        rawAttributes.setControlChar(Attributes.ControlChar.VMIN, 0);
        rawAttributes.setControlChar(Attributes.ControlChar.VTIME, 1);
        connection.setAttributes(rawAttributes);

        try {
            java.io.InputStream input = terminal.input();

            // Drain any pending input first
            while (input.available() > 0) {
                input.read();
            }

            // Build combined query string
            String combinedQuery = buildSyncColorQuery(supportsCursor, supportsPalette, useDcsPassthrough);
            connection.stdoutHandler().accept(combinedQuery.codePoints().toArray());

            // Calculate expected responses
            int expectedResponses = 2; // FG + BG
            if (supportsCursor)
                expectedResponses++;
            if (supportsPalette)
                expectedResponses += 16;

            // Read responses with timeout
            StringBuilder response = new StringBuilder();
            long endTime = System.currentTimeMillis() + timeoutMs;
            byte[] buffer = new byte[1024];

            while (System.currentTimeMillis() < endTime) {
                int read = input.read(buffer);
                if (read > 0) {
                    for (int i = 0; i < read; i++) {
                        response.append((char) (buffer[i] & 0xFF));
                    }
                    int responseCount = countResponses(response.toString());
                    if (responseCount >= expectedResponses) {
                        break;
                    }
                } else if (read < 0) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Drain any remaining input to prevent leakage
            drainInput(input, 100);

            // Parse the collected responses
            String responseStr = response.toString();
            if (!responseStr.isEmpty()) {
                LOGGER.log(Level.FINE, "OSC color response received, length: " + responseStr.length());

                java.util.Map<Integer, int[]> fgBgColors = org.aesh.terminal.utils.ANSI.parseMultipleOscColorResponses(
                        responseStr.codePoints().toArray(),
                        org.aesh.terminal.utils.ANSI.OSC_FOREGROUND,
                        org.aesh.terminal.utils.ANSI.OSC_BACKGROUND);
                foregroundRGB = fgBgColors.get(org.aesh.terminal.utils.ANSI.OSC_FOREGROUND);
                backgroundRGB = fgBgColors.get(org.aesh.terminal.utils.ANSI.OSC_BACKGROUND);

                if (supportsCursor) {
                    cursorRGB = org.aesh.terminal.utils.ANSI.parseOscColorResponse(
                            responseStr.codePoints().toArray(),
                            org.aesh.terminal.utils.ANSI.OSC_CURSOR_COLOR);
                }

                if (supportsPalette) {
                    paletteColors = org.aesh.terminal.utils.ANSI.parseMultiplePaletteResponses(
                            responseStr.codePoints().toArray(),
                            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
                }
            }

        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to query color capability", e);
            return null;
        } finally {
            connection.setAttributes(savedAttributes);
        }

        ColorDepth colorDepth = connection.getColorDepth();
        TerminalTheme theme = TerminalTheme.UNKNOWN;
        if (backgroundRGB != null) {
            theme = TerminalTheme.fromRGB(backgroundRGB[0], backgroundRGB[1], backgroundRGB[2]);
        }

        return new TerminalColorCapability(colorDepth, theme, foregroundRGB, backgroundRGB, cursorRGB, paletteColors);
    }

    /**
     * Build the OSC query string for synchronous queries.
     */
    private static String buildSyncColorQuery(boolean supportsCursor, boolean supportsPalette, boolean useDcsPassthrough) {
        StringBuilder queryBuilder = new StringBuilder();

        if (useDcsPassthrough) {
            queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]10;?\u0007").append(DCS_TMUX_SUFFIX);
            queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]11;?\u0007").append(DCS_TMUX_SUFFIX);
            if (supportsCursor) {
                queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]12;?\u0007").append(DCS_TMUX_SUFFIX);
            }
            if (supportsPalette) {
                for (int i = 0; i < 16; i++) {
                    queryBuilder.append(DCS_TMUX_PREFIX)
                            .append("\u001B]4;").append(i).append(";?\u0007")
                            .append(DCS_TMUX_SUFFIX);
                }
            }
        } else {
            queryBuilder.append("\u001B]10;?\u0007");
            queryBuilder.append("\u001B]11;?\u0007");
            if (supportsCursor) {
                queryBuilder.append("\u001B]12;?\u0007");
            }
            if (supportsPalette) {
                for (int i = 0; i < 16; i++) {
                    queryBuilder.append("\u001B]4;").append(i).append(";?\u0007");
                }
            }
        }

        return queryBuilder.toString();
    }

    /**
     * Count OSC responses in a string (each ends with BEL or ST).
     */
    private static int countResponses(String response) {
        int count = 0;
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '\u0007') {
                count++;
            } else if (c == '\\' && i > 0 && response.charAt(i - 1) == '\u001B') {
                count++;
            }
        }
        return count;
    }

    /**
     * Drain remaining input from the stream.
     */
    private static void drainInput(java.io.InputStream input, long maxWaitMs) throws java.io.IOException {
        byte[] buffer = new byte[256];
        long endTime = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < endTime) {
            if (input.available() > 0) {
                input.read(buffer);
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (input.available() == 0) {
                    break;
                }
            }
        }
    }

    // ==================== Color Depth Detection ====================

    /**
     * Detect only the color depth without querying the terminal for colors.
     * This is a fast, non-blocking operation.
     *
     * @param connection the terminal connection
     * @return the detected color depth
     */
    public static ColorDepth detectColorDepth(Connection connection) {
        // First check environment variables for true color support
        String colorterm = System.getenv("COLORTERM");
        if ("truecolor".equalsIgnoreCase(colorterm) || "24bit".equalsIgnoreCase(colorterm)) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check TERM environment variable
        String term = System.getenv("TERM");
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("truecolor") || termLower.contains("24bit")) {
                return ColorDepth.TRUE_COLOR;
            }
            // Modern terminals that support true color
            if (termLower.contains("ghostty") ||
                    termLower.contains("kitty") ||
                    termLower.contains("alacritty")) {
                return ColorDepth.TRUE_COLOR;
            }
            if (termLower.contains("256color") || termLower.contains("256-color")) {
                return ColorDepth.COLORS_256;
            }
        }

        // Check TERM_PROGRAM for true color terminals
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.contains("ghostty") ||
                    lower.contains("kitty") ||
                    lower.contains("alacritty") ||
                    lower.contains("iterm") ||
                    lower.contains("wezterm") ||
                    lower.contains("hyper")) {
                return ColorDepth.TRUE_COLOR;
            }
        }

        // Check for terminal-specific environment variables that indicate true color
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null ||
                System.getenv("KITTY_WINDOW_ID") != null ||
                System.getenv("ALACRITTY_SOCKET") != null ||
                System.getenv("WEZTERM_PANE") != null) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check TERMINAL_EMULATOR for JetBrains IDEs (IntelliJ, etc.)
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null &&
                terminalEmulator.toLowerCase().contains("jetbrains")) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check for Windows 10+ with Virtual Terminal support
        // Windows 10 build 14931+ supports ANSI/VT sequences including 256 colors and true color
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            ColorDepth windowsDepth = detectWindowsColorDepth();
            if (windowsDepth != null) {
                return windowsDepth;
            }
        }

        // Check terminfo max_colors capability
        if (connection != null && connection.device() != null) {
            Device device = connection.device();
            Integer maxColors = device.getNumericCapability(Capability.max_colors);
            if (maxColors != null) {
                return ColorDepth.fromColorCount(maxColors);
            }
        }

        // Default to 8 colors if ANSI is supported
        if (connection != null && connection.supportsAnsi()) {
            return ColorDepth.COLORS_8;
        }

        return ColorDepth.NO_COLOR;
    }

    /**
     * Detect terminal theme from environment variables.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    public static TerminalTheme detectThemeFromEnvironment() {
        // Check for IDE-specific terminals FIRST, before generic environment variables
        // IDE terminals may inherit COLORFGBG from the system shell, which doesn't
        // reflect the actual IDE theme. The IDE-specific detection reads config files.

        // Check for JetBrains IDE (IntelliJ, PyCharm, etc.)
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        boolean isJetBrainsTerminal = terminalEmulator != null &&
                terminalEmulator.toLowerCase().contains("jetbrains");
        if (isJetBrainsTerminal) {
            TerminalTheme theme = detectJetBrainsTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
            // JetBrains detection failed - skip COLORFGBG (unreliable in IDEs)
            // and continue to other detection methods below
            LOGGER.log(Level.FINE, "JetBrains theme detection failed, skipping COLORFGBG");
        }

        // Check for VSCode integrated terminal
        String termProgram = System.getenv("TERM_PROGRAM");
        boolean isVSCodeTerminal = "vscode".equalsIgnoreCase(termProgram);
        if (isVSCodeTerminal) {
            TerminalTheme theme = detectVSCodeTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
            // VSCode detection failed - skip COLORFGBG (unreliable in IDEs)
            LOGGER.log(Level.FINE, "VSCode theme detection failed, skipping COLORFGBG");
        }

        // Check COLORFGBG for terminals that reliably set this variable
        // Note: This is skipped for IDE terminals where the value may be
        // inherited from the parent shell and not reflect the actual theme
        if (!isJetBrainsTerminal && !isVSCodeTerminal) {
            String colorfgbg = System.getenv("COLORFGBG");
            if (colorfgbg != null) {
                // Format is typically "fg;bg" where values < 7 are dark, >= 7 are light
                String[] parts = colorfgbg.split(";");
                if (parts.length >= 2) {
                    try {
                        int bg = Integer.parseInt(parts[parts.length - 1]);
                        // Colors 0, 1, 2, 3, 4, 5, 6 are typically dark
                        // Colors 7 and above are typically light
                        return bg < 7 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                    } catch (NumberFormatException e) {
                        // Ignore, continue with other methods
                    }
                }
            }
        }

        // Check macOS dark mode via environment
        String appleInterfaceStyle = System.getenv("APPLE_INTERFACE_STYLE");
        if (appleInterfaceStyle != null) {
            return "Dark".equalsIgnoreCase(appleInterfaceStyle)
                    ? TerminalTheme.DARK
                    : TerminalTheme.LIGHT;
        }

        // Check for Alacritty terminal
        String term = System.getenv("TERM");
        if (term != null && term.toLowerCase().contains("alacritty")) {
            TerminalTheme theme = detectAlacrittyTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
        }

        // Check Windows-specific detection
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            // Try Windows Terminal settings first
            TerminalTheme theme = detectWindowsTerminalTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }

            // Try ConEmu/Cmder detection
            theme = detectConEmuTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }

            // Try legacy console color settings from registry
            // This applies to cmd.exe and PowerShell in legacy console
            theme = detectWindowsConsoleTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }

            // Try Windows Apps dark mode setting as fallback
            theme = detectWindowsAppsDarkMode();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }

            // Default: Windows console has historically been dark (black background)
            // If we're on Windows and couldn't detect otherwise, assume dark
            LOGGER.log(Level.FINE, "Windows detected but theme not determined, defaulting to dark");
            return TerminalTheme.DARK;
        }

        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect color depth on Windows systems.
     * <p>
     * Windows 10 build 14931+ (Anniversary Update) supports ANSI/VT sequences.
     * Windows 10 build 15063+ (Creators Update) has better VT support.
     * Windows Terminal and modern cmd.exe support 256 colors and true color.
     * <p>
     * Detection order:
     * <ol>
     * <li>Windows Terminal (WT_SESSION) - true color</li>
     * <li>ConEmu/Cmder - true color</li>
     * <li>Windows 10 build 14931+ - true color (VT enabled by default in newer builds)</li>
     * <li>Older Windows - 16 colors</li>
     * </ol>
     *
     * @return the detected color depth, or null if not determinable
     */
    private static ColorDepth detectWindowsColorDepth() {
        // Windows Terminal always supports true color
        if (System.getenv("WT_SESSION") != null) {
            LOGGER.log(Level.FINE, "Windows Terminal detected - true color supported");
            return ColorDepth.TRUE_COLOR;
        }

        // ConEmu/Cmder support true color
        if (System.getenv("ConEmuPID") != null || System.getenv("ConEmuANSI") != null) {
            LOGGER.log(Level.FINE, "ConEmu detected - true color supported");
            return ColorDepth.TRUE_COLOR;
        }

        // Check Windows version for VT support
        // Windows 10 build 14931+ supports VT sequences
        String osVersion = System.getProperty("os.version", "");
        try {
            // os.version on Windows 10 is "10.0" followed by build number in other properties
            // We need to check the build number for accurate detection
            if (osVersion.startsWith("10.") || osVersion.startsWith("11.")) {
                // Windows 10 or 11 - check build number
                int buildNumber = getWindowsBuildNumber();
                if (buildNumber >= 14931) {
                    // VT support available - modern Windows supports true color
                    // Build 14931+ has basic VT support
                    // Build 15063+ (Creators Update) has improved VT support
                    LOGGER.log(Level.FINE, "Windows 10+ build " + buildNumber + " detected - true color supported");
                    return ColorDepth.TRUE_COLOR;
                } else if (buildNumber > 0) {
                    // Older Windows 10 build - limited color support
                    LOGGER.log(Level.FINE, "Windows 10 build " + buildNumber + " - 16 colors");
                    return ColorDepth.COLORS_16;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to detect Windows version", e);
        }

        // Fallback: check if running in a modern console by testing TERM
        // Some Windows setups (MSYS2, Git Bash, Cygwin) set TERM
        String term = System.getenv("TERM");
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("256color")) {
                return ColorDepth.COLORS_256;
            }
            if (termLower.contains("xterm") || termLower.contains("cygwin") ||
                    termLower.contains("msys")) {
                return ColorDepth.COLORS_256;
            }
        }

        // Default for Windows without specific detection
        // Assume modern Windows (10+) with console VT support
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows 10") || osName.contains("windows 11") ||
                osName.contains("windows server 2016") || osName.contains("windows server 2019") ||
                osName.contains("windows server 2022")) {
            LOGGER.log(Level.FINE, "Modern Windows detected by name - assuming true color");
            return ColorDepth.TRUE_COLOR;
        }

        return null;
    }

    /**
     * Get the Windows build number from system properties or registry.
     *
     * @return the build number, or 0 if not determinable
     */
    private static int getWindowsBuildNumber() {
        // Try to get build number from system properties first
        // Java doesn't expose this directly, so we need to query the registry
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                    "/v", "CurrentBuildNumber");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            java.io.InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String result = output.toString();
                // Output looks like:
                // HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows NT\CurrentVersion
                //     CurrentBuildNumber    REG_SZ    19045
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("CurrentBuildNumber\\s+REG_SZ\\s+(\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    int buildNumber = Integer.parseInt(matcher.group(1));
                    LOGGER.log(Level.FINE, "Windows build number from registry: " + buildNumber);
                    return buildNumber;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to query Windows build number from registry", e);
        }

        // Fallback: try to parse from os.version (less reliable)
        // Some JVMs report "10.0.19045" as os.version
        String osVersion = System.getProperty("os.version", "");
        String[] parts = osVersion.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return 0;
    }

    /**
     * Detect theme from Windows legacy console registry settings.
     * <p>
     * The Windows console stores color settings in:
     * {@code HKEY_CURRENT_USER\Console}
     * <p>
     * The {@code ScreenColors} value is a DWORD where:
     * <ul>
     * <li>Bits 0-3 (low nibble): foreground color index (0-15)</li>
     * <li>Bits 4-7 (high nibble): background color index (0-15)</li>
     * </ul>
     * <p>
     * Default Windows console color indices:
     * <ul>
     * <li>0 = Black, 1 = Dark Blue, 2 = Dark Green, 3 = Dark Cyan</li>
     * <li>4 = Dark Red, 5 = Dark Magenta, 6 = Dark Yellow, 7 = Light Gray</li>
     * <li>8 = Dark Gray, 9 = Blue, 10 = Green, 11 = Cyan</li>
     * <li>12 = Red, 13 = Magenta, 14 = Yellow, 15 = White</li>
     * </ul>
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectWindowsConsoleTheme() {
        try {
            // Query the ScreenColors value from the Console registry key
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKCU\\Console",
                    "/v", "ScreenColors");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            java.io.InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String result = output.toString();
                // Output looks like:
                // HKEY_CURRENT_USER\Console
                //     ScreenColors    REG_DWORD    0x7
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                        "ScreenColors\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");
                java.util.regex.Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    int screenColors = Integer.parseInt(matcher.group(1), 16);
                    // Background color is in bits 4-7 (high nibble of low byte)
                    int bgColorIndex = (screenColors >> 4) & 0x0F;

                    // Determine if background is dark or light based on color index
                    // Dark colors: 0 (black), 1-6 (dark colors), 8 (dark gray)
                    // Light colors: 7 (light gray), 9-15 (bright colors)
                    boolean isDark = (bgColorIndex <= 6) || (bgColorIndex == 8);

                    LOGGER.log(Level.FINE, "Windows Console ScreenColors=0x" +
                            Integer.toHexString(screenColors) + " bgIndex=" + bgColorIndex +
                            " -> " + (isDark ? "DARK" : "LIGHT"));

                    return isDark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to query Windows Console registry", e);
        }

        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect theme from Windows Terminal settings.json.
     * <p>
     * Windows Terminal stores settings in:
     * {@code %LOCALAPPDATA%\Packages\Microsoft.WindowsTerminal_8wekyb3d8bbwe\LocalState\settings.json}
     * <p>
     * The settings contain color scheme information that can be used to determine
     * if the terminal is using a light or dark theme.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectWindowsTerminalTheme() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null) {
            return TerminalTheme.UNKNOWN;
        }

        // Windows Terminal settings location
        java.io.File settingsFile = new java.io.File(localAppData,
                "Packages/Microsoft.WindowsTerminal_8wekyb3d8bbwe/LocalState/settings.json");

        // Also check Windows Terminal Preview
        if (!settingsFile.isFile()) {
            settingsFile = new java.io.File(localAppData,
                    "Packages/Microsoft.WindowsTerminalPreview_8wekyb3d8bbwe/LocalState/settings.json");
        }

        // Also check unpackaged/dev version
        if (!settingsFile.isFile()) {
            settingsFile = new java.io.File(localAppData, "Microsoft/Windows Terminal/settings.json");
        }

        if (!settingsFile.isFile()) {
            LOGGER.log(Level.FINE, "Windows Terminal settings.json not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseWindowsTerminalSettings(settingsFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse Windows Terminal settings", e);
            return TerminalTheme.UNKNOWN;
        }
    }

    /**
     * Parse Windows Terminal settings.json to detect the theme.
     * <p>
     * Looks for the default profile's color scheme and determines if it's light or dark.
     *
     * @param settingsFile the settings.json file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseWindowsTerminalSettings(java.io.File settingsFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(settingsFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            String json = content.toString();

            // Look for colorScheme in defaults or profiles
            // Simple parsing - look for "colorScheme" : "SchemeName"
            String colorScheme = extractJsonValue(json, "colorScheme");
            if (colorScheme != null) {
                return isWindowsTerminalSchemeDark(colorScheme)
                        ? TerminalTheme.DARK
                        : TerminalTheme.LIGHT;
            }

            // Check if using a dark theme based on application theme setting
            String theme = extractJsonValue(json, "theme");
            if (theme != null) {
                if ("dark".equalsIgnoreCase(theme) || "system".equalsIgnoreCase(theme)) {
                    // If system, we'll need to check Windows dark mode
                    return theme.equalsIgnoreCase("dark") ? TerminalTheme.DARK : TerminalTheme.UNKNOWN;
                }
                if ("light".equalsIgnoreCase(theme)) {
                    return TerminalTheme.LIGHT;
                }
            }

        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read Windows Terminal settings", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Extract a simple string value from JSON.
     * This is a basic parser that works for simple key-value pairs.
     *
     * @param json the JSON string
     * @param key the key to find
     * @return the value, or null if not found
     */
    private static String extractJsonValue(String json, String key) {
        // Look for "key" : "value" or "key": "value"
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Determine if a Windows Terminal color scheme is dark.
     *
     * @param schemeName the color scheme name
     * @return true if the scheme is considered dark
     */
    private static boolean isWindowsTerminalSchemeDark(String schemeName) {
        if (schemeName == null) {
            return true; // Default to dark
        }
        String lower = schemeName.toLowerCase();

        // Known dark schemes
        if (lower.contains("dark") ||
                lower.contains("campbell") || // Campbell is dark by default
                lower.contains("one half dark") ||
                lower.contains("tango dark") ||
                lower.contains("vintage") ||
                lower.contains("solarized dark") ||
                lower.contains("dracula") ||
                lower.contains("monokai") ||
                lower.contains("nord") ||
                lower.contains("gruvbox dark") ||
                lower.contains("tomorrow night") ||
                lower.contains("material")) {
            return true;
        }

        // Known light schemes
        if (lower.contains("light") ||
                lower.contains("one half light") ||
                lower.contains("tango light") ||
                lower.contains("solarized light") ||
                lower.contains("gruvbox light")) {
            return false;
        }

        // Default to dark for unknown schemes
        return true;
    }

    /**
     * Detect theme from ConEmu/Cmder terminal configuration.
     * <p>
     * ConEmu is a popular Windows terminal emulator that Cmder is built on.
     * It can be detected by the ConEmuDir or ConEmuBaseDir environment variables.
     * <p>
     * Configuration is stored in:
     * <ul>
     * <li>{@code %ConEmuDir%\ConEmu.xml}</li>
     * <li>{@code %APPDATA%\ConEmu.xml}</li>
     * </ul>
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectConEmuTheme() {
        // Check if we're running in ConEmu
        String conEmuDir = System.getenv("ConEmuDir");
        String conEmuBaseDir = System.getenv("ConEmuBaseDir");
        String conEmuPid = System.getenv("ConEmuPID");

        if (conEmuDir == null && conEmuBaseDir == null && conEmuPid == null) {
            // Not running in ConEmu
            return TerminalTheme.UNKNOWN;
        }

        // Look for ConEmu.xml configuration
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();

        if (conEmuDir != null) {
            candidates.add(new java.io.File(conEmuDir, "ConEmu.xml"));
        }
        if (conEmuBaseDir != null) {
            candidates.add(new java.io.File(conEmuBaseDir, "ConEmu.xml"));
        }
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            candidates.add(new java.io.File(appData, "ConEmu.xml"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            candidates.add(new java.io.File(userHome, "ConEmu.xml"));
        }

        for (java.io.File configFile : candidates) {
            if (configFile.isFile()) {
                TerminalTheme theme = parseConEmuConfig(configFile);
                if (theme != TerminalTheme.UNKNOWN) {
                    return theme;
                }
            }
        }

        // ConEmu detected but couldn't determine theme - default to dark
        LOGGER.log(Level.FINE, "ConEmu detected but theme not determined, defaulting to dark");
        return TerminalTheme.DARK;
    }

    /**
     * Parse ConEmu XML configuration to detect the theme.
     * <p>
     * Looks for the color scheme name or background color value.
     *
     * @param configFile the ConEmu.xml file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseConEmuConfig(java.io.File configFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();

                // Look for color scheme name
                // <value name="ColorTable00" type="dword" data="00000000"/>
                // or palette name references
                if (trimmed.contains("name=\"palettename\"") ||
                        trimmed.contains("name=\"schemename\"")) {
                    if (trimmed.contains("dark") ||
                            trimmed.contains("monokai") ||
                            trimmed.contains("dracula") ||
                            trimmed.contains("solarized dark") ||
                            trimmed.contains("tomorrow night") ||
                            trimmed.contains("zenburn") ||
                            trimmed.contains("gruvbox")) {
                        LOGGER.log(Level.FINE, "ConEmu dark palette detected");
                        return TerminalTheme.DARK;
                    }
                    if (trimmed.contains("light") ||
                            trimmed.contains("solarized light")) {
                        LOGGER.log(Level.FINE, "ConEmu light palette detected");
                        return TerminalTheme.LIGHT;
                    }
                }

                // Look for background color (ColorTable00 is typically background)
                // Format: <value name="ColorTable00" type="dword" data="00362b00"/>
                if (trimmed.contains("colortable00") && trimmed.contains("data=\"")) {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("data=\"([0-9a-fA-F]+)\"");
                    java.util.regex.Matcher m = p.matcher(trimmed);
                    if (m.find()) {
                        String hex = m.group(1);
                        // ConEmu stores as BBGGRR (reversed)
                        if (hex.length() >= 6) {
                            // Take last 6 chars if padded
                            String bgr = hex.length() > 6 ? hex.substring(hex.length() - 6) : hex;
                            int b = Integer.parseInt(bgr.substring(0, 2), 16);
                            int g = Integer.parseInt(bgr.substring(2, 4), 16);
                            int r = Integer.parseInt(bgr.substring(4, 6), 16);
                            double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                            TerminalTheme theme = luminance < 0.5 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                            LOGGER.log(Level.FINE, "ConEmu background BGR=" + bgr +
                                    " luminance=" + luminance + " -> " + theme);
                            return theme;
                        }
                    }
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read ConEmu config", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect Windows Apps dark mode setting from the registry.
     * <p>
     * Windows stores the dark mode preference in:
     * {@code HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Themes\Personalize}
     * <p>
     * Keys checked (in order):
     * <ul>
     * <li>AppsUseLightTheme - per-app dark mode setting (0 = dark, 1 = light)</li>
     * <li>SystemUsesLightTheme - system-wide dark mode setting (0 = dark, 1 = light)</li>
     * </ul>
     * <p>
     * This method uses the 'reg' command to query the registry.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectWindowsAppsDarkMode() {
        // First try AppsUseLightTheme (app-specific setting)
        TerminalTheme theme = queryWindowsThemeRegistryKey("AppsUseLightTheme");
        if (theme != TerminalTheme.UNKNOWN) {
            return theme;
        }

        // Fall back to SystemUsesLightTheme (system-wide setting)
        theme = queryWindowsThemeRegistryKey("SystemUsesLightTheme");
        if (theme != TerminalTheme.UNKNOWN) {
            return theme;
        }

        return TerminalTheme.UNKNOWN;
    }

    /**
     * Query a specific Windows theme registry key.
     *
     * @param keyName the registry key name (e.g., "AppsUseLightTheme" or "SystemUsesLightTheme")
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme queryWindowsThemeRegistryKey(String keyName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", keyName);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            java.io.InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String result = output.toString();
                // Output looks like:
                // HKEY_CURRENT_USER\SOFTWARE\Microsoft\Windows\CurrentVersion\Themes\Personalize
                //     AppsUseLightTheme    REG_DWORD    0x0
                if (result.contains("0x0") || result.contains("0x00000000")) {
                    LOGGER.log(Level.FINE, "Windows " + keyName + " = 0 (dark mode)");
                    return TerminalTheme.DARK;
                } else if (result.contains("0x1") || result.contains("0x00000001")) {
                    LOGGER.log(Level.FINE, "Windows " + keyName + " = 1 (light mode)");
                    return TerminalTheme.LIGHT;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to query Windows registry key: " + keyName, e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect theme from Alacritty terminal configuration.
     * <p>
     * Alacritty stores configuration in:
     * <ul>
     * <li>Linux/macOS: {@code ~/.config/alacritty/alacritty.toml} (v0.13+) or
     * {@code ~/.config/alacritty/alacritty.yml} (older)</li>
     * <li>Windows: {@code %APPDATA%\alacritty\alacritty.toml}</li>
     * </ul>
     * <p>
     * The configuration may import a color scheme or define colors directly.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectAlacrittyTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        java.io.File configFile = getAlacrittyConfigFile(userHome);
        if (configFile == null || !configFile.isFile()) {
            LOGGER.log(Level.FINE, "Alacritty config not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseAlacrittyConfig(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse Alacritty config", e);
            return TerminalTheme.UNKNOWN;
        }
    }

    /**
     * Get the Alacritty configuration file path for the current OS.
     *
     * @param userHome the user's home directory
     * @return the config file, or null if not found
     */
    private static java.io.File getAlacrittyConfigFile(String userHome) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        java.util.List<java.io.File> candidates = new java.util.ArrayList<>();

        if (osName.contains("win")) {
            // Windows: %APPDATA%\alacritty\alacritty.toml
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                candidates.add(new java.io.File(appData, "alacritty/alacritty.toml"));
                candidates.add(new java.io.File(appData, "alacritty/alacritty.yml"));
            }
        } else {
            // Linux/macOS: ~/.config/alacritty/alacritty.toml
            candidates.add(new java.io.File(userHome, ".config/alacritty/alacritty.toml"));
            candidates.add(new java.io.File(userHome, ".config/alacritty/alacritty.yml"));
            // XDG config home
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                candidates.add(new java.io.File(xdgConfigHome, "alacritty/alacritty.toml"));
                candidates.add(new java.io.File(xdgConfigHome, "alacritty/alacritty.yml"));
            }
            // Also check ~/.alacritty.yml (legacy location)
            candidates.add(new java.io.File(userHome, ".alacritty.toml"));
            candidates.add(new java.io.File(userHome, ".alacritty.yml"));
        }

        for (java.io.File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Parse Alacritty configuration to detect the theme.
     * <p>
     * Looks for color scheme imports or direct color definitions.
     * The background color value is used to determine light/dark.
     *
     * @param configFile the alacritty.toml or alacritty.yml file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseAlacrittyConfig(java.io.File configFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(configFile))) {
            String line;
            boolean inColors = false;
            boolean inPrimary = false;
            String fileName = configFile.getName().toLowerCase();
            boolean isToml = fileName.endsWith(".toml");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();

                // Check for theme/scheme import that hints at theme type
                if (trimmed.contains("import") || trimmed.contains("theme")) {
                    if (trimmed.contains("dark") ||
                            trimmed.contains("dracula") ||
                            trimmed.contains("monokai") ||
                            trimmed.contains("nord") ||
                            trimmed.contains("gruvbox_dark") ||
                            trimmed.contains("solarized_dark") ||
                            trimmed.contains("one_dark") ||
                            trimmed.contains("tokyo-night") ||
                            trimmed.contains("catppuccin-mocha")) {
                        LOGGER.log(Level.FINE, "Alacritty dark theme detected from import/theme name");
                        return TerminalTheme.DARK;
                    }
                    if (trimmed.contains("light") ||
                            trimmed.contains("gruvbox_light") ||
                            trimmed.contains("solarized_light") ||
                            trimmed.contains("catppuccin-latte")) {
                        LOGGER.log(Level.FINE, "Alacritty light theme detected from import/theme name");
                        return TerminalTheme.LIGHT;
                    }
                }

                // Track section headers for TOML format
                if (isToml) {
                    if (trimmed.startsWith("[colors")) {
                        inColors = true;
                        inPrimary = trimmed.contains("primary");
                        continue;
                    } else if (trimmed.startsWith("[") && !trimmed.startsWith("[colors")) {
                        inColors = false;
                        inPrimary = false;
                    }
                } else {
                    // YAML format
                    if (trimmed.equals("colors:")) {
                        inColors = true;
                        continue;
                    } else if (trimmed.equals("primary:") && inColors) {
                        inPrimary = true;
                        continue;
                    } else if (!trimmed.isEmpty() && !line.startsWith(" ") && !line.startsWith("\t") && inColors) {
                        // Non-indented line means we left the colors section
                        inColors = false;
                        inPrimary = false;
                    }
                }

                // Look for background color
                if ((inColors && inPrimary) || trimmed.contains("background")) {
                    // Match patterns like:
                    // background = "#1e1e1e"  (TOML)
                    // background: '#1e1e1e'   (YAML)
                    // background: '0x1e1e1e'  (YAML hex)
                    java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("[\"']#?(?:0x)?([0-9a-fA-F]{6})[\"']");
                    java.util.regex.Matcher matcher = hexPattern.matcher(trimmed);
                    if (matcher.find() && trimmed.contains("background")) {
                        String hex = matcher.group(1);
                        int r = Integer.parseInt(hex.substring(0, 2), 16);
                        int g = Integer.parseInt(hex.substring(2, 4), 16);
                        int b = Integer.parseInt(hex.substring(4, 6), 16);
                        // Calculate luminance
                        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                        TerminalTheme theme = luminance < 0.5 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                        LOGGER.log(Level.FINE, "Alacritty background color #" + hex +
                                " luminance=" + luminance + " -> " + theme);
                        return theme;
                    }
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read Alacritty config", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect theme from Visual Studio Code settings.
     * <p>
     * VSCode stores settings in:
     * <ul>
     * <li>Linux: {@code ~/.config/Code/User/settings.json}</li>
     * <li>macOS: {@code ~/Library/Application Support/Code/User/settings.json}</li>
     * <li>Windows: {@code %APPDATA%\Code\User\settings.json}</li>
     * </ul>
     * <p>
     * The {@code workbench.colorTheme} setting contains the theme name.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectVSCodeTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        java.io.File settingsFile = getVSCodeSettingsFile(userHome);
        if (settingsFile == null || !settingsFile.isFile()) {
            LOGGER.log(Level.FINE, "VSCode settings.json not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseVSCodeSettings(settingsFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse VSCode settings", e);
            return TerminalTheme.UNKNOWN;
        }
    }

    /**
     * Get the VSCode settings.json file path for the current OS.
     *
     * @param userHome the user's home directory
     * @return the settings file, or null if not determinable
     */
    private static java.io.File getVSCodeSettingsFile(String userHome) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        java.io.File settingsFile = null;

        if (osName.contains("mac") || osName.contains("darwin")) {
            // macOS: ~/Library/Application Support/Code/User/settings.json
            settingsFile = new java.io.File(userHome,
                    "Library/Application Support/Code/User/settings.json");
            // Also check VSCode Insiders
            if (!settingsFile.isFile()) {
                settingsFile = new java.io.File(userHome,
                        "Library/Application Support/Code - Insiders/User/settings.json");
            }
        } else if (osName.contains("win")) {
            // Windows: %APPDATA%\Code\User\settings.json
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                settingsFile = new java.io.File(appData, "Code/User/settings.json");
                // Also check VSCode Insiders
                if (!settingsFile.isFile()) {
                    settingsFile = new java.io.File(appData, "Code - Insiders/User/settings.json");
                }
            }
        } else {
            // Linux: ~/.config/Code/User/settings.json
            settingsFile = new java.io.File(userHome, ".config/Code/User/settings.json");
            // Also check VSCode Insiders
            if (!settingsFile.isFile()) {
                settingsFile = new java.io.File(userHome, ".config/Code - Insiders/User/settings.json");
            }
            // Check XDG_CONFIG_HOME
            if (!settingsFile.isFile()) {
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
                if (xdgConfigHome != null) {
                    settingsFile = new java.io.File(xdgConfigHome, "Code/User/settings.json");
                }
            }
        }

        return settingsFile;
    }

    /**
     * Parse VSCode settings.json to detect the theme.
     *
     * @param settingsFile the settings.json file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseVSCodeSettings(java.io.File settingsFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(settingsFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            String json = content.toString();

            // Look for "workbench.colorTheme": "ThemeName"
            String colorTheme = extractJsonValue(json, "workbench.colorTheme");
            if (colorTheme != null) {
                TerminalTheme theme = isVSCodeThemeDark(colorTheme);
                LOGGER.log(Level.FINE, "VSCode theme detected: " + colorTheme + " -> " + theme);
                return theme;
            }

            // Check if there's a terminal-specific theme override
            String terminalTheme = extractJsonValue(json, "workbench.preferredDarkColorTheme");
            if (terminalTheme != null) {
                // If preferredDarkColorTheme is set, user prefers dark themes
                return TerminalTheme.DARK;
            }

        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read VSCode settings", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Determine if a VSCode color theme is dark or light.
     *
     * @param themeName the color theme name
     * @return the detected theme
     */
    private static TerminalTheme isVSCodeThemeDark(String themeName) {
        if (themeName == null) {
            return TerminalTheme.UNKNOWN;
        }
        String lower = themeName.toLowerCase();

        // Known dark themes
        if (lower.contains("dark") ||
                lower.contains("monokai") ||
                lower.contains("dracula") ||
                lower.contains("one dark") ||
                lower.contains("nord") ||
                lower.contains("night") ||
                lower.contains("tomorrow night") ||
                lower.contains("material") ||
                lower.contains("abyss") ||
                lower.contains("default dark") ||
                lower.contains("visual studio dark") ||
                lower.contains("solarized dark") ||
                lower.contains("gruvbox dark") ||
                lower.contains("cobalt") ||
                lower.contains("synthwave") ||
                lower.contains("atom one dark") ||
                lower.contains("palenight") ||
                lower.contains("winter is coming") && !lower.contains("light") ||
                lower.contains("github dark") ||
                lower.contains("catppuccin mocha") ||
                lower.contains("catppuccin macchiato") ||
                lower.contains("catppuccin frappe") ||
                lower.contains("tokyo night")) {
            return TerminalTheme.DARK;
        }

        // Known light themes
        if (lower.contains("light") ||
                lower.contains("solarized light") ||
                lower.contains("gruvbox light") ||
                lower.contains("github light") ||
                lower.contains("quiet light") ||
                lower.contains("default light") ||
                lower.contains("visual studio light") ||
                lower.contains("catppuccin latte") ||
                lower.contains("atom one light") ||
                lower.contains("tomorrow")) {
            // "Tomorrow" without "night" is light
            if (!lower.contains("night")) {
                return TerminalTheme.LIGHT;
            }
        }

        // Default to dark for unknown VSCode themes (most popular themes are dark)
        return TerminalTheme.DARK;
    }

    /**
     * Detect the theme from JetBrains IDE configuration files.
     * <p>
     * JetBrains IDEs store their color scheme in different locations by OS:
     * <ul>
     * <li>Linux: {@code ~/.config/JetBrains/<product><version>/options/colors.scheme.xml}</li>
     * <li>macOS: {@code ~/Library/Application Support/JetBrains/<product><version>/options/colors.scheme.xml}</li>
     * <li>Windows: {@code %APPDATA%\JetBrains\<product><version>\options\colors.scheme.xml}</li>
     * <li>Legacy (all OS): {@code ~/.<product><version>/options/colors.scheme.xml}</li>
     * </ul>
     * <p>
     * The file contains:
     * {@code <global_color_scheme name="Dark" />} or
     * {@code <global_color_scheme name="..." />}
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    private static TerminalTheme detectJetBrainsTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        // Get all possible JetBrains config directories for this OS
        java.util.List<java.io.File> configDirs = getJetBrainsConfigDirectories(userHome);

        for (java.io.File jetbrainsDir : configDirs) {
            if (!jetbrainsDir.isDirectory()) {
                continue;
            }

            // Find product directories
            java.io.File[] productDirs;

            // Special handling for legacy home directory location
            // where products are stored as ~/.IntelliJIdea2019.3/, ~/.PyCharm2020.1/, etc.
            if (jetbrainsDir.getAbsolutePath().equals(userHome)) {
                productDirs = jetbrainsDir.listFiles(file -> file.isDirectory() && isLegacyJetBrainsDir(file.getName()));
            } else {
                productDirs = jetbrainsDir.listFiles(java.io.File::isDirectory);
            }

            if (productDirs == null || productDirs.length == 0) {
                continue;
            }

            // Sort by modification time, most recent first
            java.util.Arrays.sort(productDirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            // Try each product directory
            for (java.io.File productDir : productDirs) {
                // First try laf.xml (Look and Feel) which contains the IDE theme
                // This is more reliable as it contains the actual theme name
                java.io.File lafFile = new java.io.File(productDir, "options/laf.xml");
                if (!lafFile.isFile()) {
                    lafFile = new java.io.File(productDir, "config/options/laf.xml");
                }
                if (lafFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsLafFile(lafFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE, "Detected JetBrains theme from " + lafFile + ": " + theme);
                        return theme;
                    }
                }

                // Fall back to colors.scheme.xml
                java.io.File colorsFile = new java.io.File(productDir, "options/colors.scheme.xml");
                if (!colorsFile.isFile()) {
                    colorsFile = new java.io.File(productDir, "config/options/colors.scheme.xml");
                }
                if (colorsFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsColorScheme(colorsFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE, "Detected JetBrains theme from " + colorsFile + ": " + theme);
                        return theme;
                    }
                }
            }
        }

        LOGGER.log(Level.FINE, "Could not detect JetBrains theme from config files");
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Parse a JetBrains laf.xml file to detect the theme.
     * <p>
     * The laf.xml file contains the Look and Feel settings including the theme name.
     * Format example:
     *
     * <pre>
     * &lt;application&gt;
     *   &lt;component name="LafManager"&gt;
     *     &lt;laf class-name="com.intellij.ide.ui.laf.darcula.DarculaLaf" themeId="Darcula"/&gt;
     *   &lt;/component&gt;
     * &lt;/application&gt;
     * </pre>
     *
     * Or for newer versions:
     *
     * <pre>
     * &lt;laf themeId="JetBrainsLightTheme"/&gt;
     * </pre>
     *
     * @param lafFile the laf.xml file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseJetBrainsLafFile(java.io.File lafFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(lafFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();

                // Look for themeId or class-name attributes
                if (lower.contains("themeid=") || lower.contains("class-name=")) {
                    // Dark themes
                    if (lower.contains("darcula") ||
                            lower.contains("dark") ||
                            lower.contains("high_contrast") ||
                            lower.contains("highcontrast") ||
                            lower.contains("one dark") ||
                            lower.contains("dracula") ||
                            lower.contains("nord") ||
                            lower.contains("monokai") ||
                            lower.contains("material")) {
                        return TerminalTheme.DARK;
                    }
                    // Light themes
                    if (lower.contains("light") ||
                            lower.contains("intellijlaf") ||
                            lower.contains("intellij laf") ||
                            lower.contains("jetbrainslight") ||
                            lower.contains("default") ||
                            lower.contains("classic") ||
                            lower.contains("windows") ||
                            lower.contains("gtk") ||
                            lower.contains("metal")) {
                        return TerminalTheme.LIGHT;
                    }
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read JetBrains laf.xml file", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Check if a directory name matches the legacy JetBrains product naming pattern.
     * <p>
     * Legacy format: .IntelliJIdea2019.3, .PyCharm2020.1, .WebStorm2021.2, etc.
     *
     * @param name the directory name
     * @return true if it matches a JetBrains product pattern
     */
    private static boolean isLegacyJetBrainsDir(String name) {
        if (!name.startsWith(".")) {
            return false;
        }
        String[] products = {
                ".IntelliJIdea", ".IdeaIC", // IntelliJ IDEA Ultimate/Community
                ".PyCharm", ".PyCharmCE", // PyCharm Professional/Community
                ".WebStorm", ".PhpStorm", // WebStorm, PhpStorm
                ".RubyMine", ".CLion", // RubyMine, CLion
                ".GoLand", ".Rider", // GoLand, Rider
                ".DataGrip", ".AppCode", // DataGrip, AppCode
                ".AndroidStudio", ".DataSpell", // Android Studio, DataSpell
                ".Fleet", ".RustRover", // Fleet, RustRover
                ".Aqua", ".Writerside" // Aqua, Writerside
        };
        for (String product : products) {
            if (name.startsWith(product)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all possible JetBrains configuration directories for the current OS.
     * <p>
     * Returns directories in order of preference (most likely first).
     *
     * @param userHome the user's home directory
     * @return list of possible JetBrains config directories
     */
    private static java.util.List<java.io.File> getJetBrainsConfigDirectories(String userHome) {
        java.util.List<java.io.File> dirs = new java.util.ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            // macOS: ~/Library/Application Support/JetBrains/
            dirs.add(new java.io.File(userHome, "Library/Application Support/JetBrains"));
            // Older versions used ~/Library/Preferences/
            dirs.add(new java.io.File(userHome, "Library/Preferences"));
        } else if (osName.contains("win")) {
            // Windows: %APPDATA%\JetBrains\
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                dirs.add(new java.io.File(appData, "JetBrains"));
            }
            // Fallback to user home
            dirs.add(new java.io.File(userHome, "AppData/Roaming/JetBrains"));
        } else {
            // Linux and others: ~/.config/JetBrains/
            dirs.add(new java.io.File(userHome, ".config/JetBrains"));
            // XDG config home
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                dirs.add(new java.io.File(xdgConfigHome, "JetBrains"));
            }
        }

        // Legacy location (older JetBrains versions, all platforms)
        // Format: ~/.<product><version>/
        // e.g., ~/.IntelliJIdea2019.3/
        dirs.add(new java.io.File(userHome));

        return dirs;
    }

    /**
     * Parse a JetBrains colors.scheme.xml file to detect the theme.
     *
     * @param colorsFile the colors.scheme.xml file
     * @return the detected theme, or UNKNOWN if parsing failed
     */
    private static TerminalTheme parseJetBrainsColorScheme(java.io.File colorsFile) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(colorsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Look for: <global_color_scheme name="Dark" />
                // or: <global_color_scheme name="Darcula" />
                // or: <global_color_scheme name="High contrast" />
                // or: <global_color_scheme name="IntelliJ Light" />
                if (line.contains("global_color_scheme")) {
                    String lower = line.toLowerCase();
                    if (lower.contains("\"dark\"") ||
                            lower.contains("\"darcula\"") ||
                            lower.contains("\"high contrast\"") ||
                            lower.contains("\"one dark\"") ||
                            lower.contains("\"monokai\"") ||
                            lower.contains("\"dracula\"") ||
                            lower.contains("\"nord\"") ||
                            lower.contains("\"gruvbox dark\"") ||
                            lower.contains("\"solarized dark\"") ||
                            lower.contains("\"tomorrow night\"") ||
                            lower.contains("\"atom one dark\"") ||
                            lower.contains("\"material\"")) {
                        return TerminalTheme.DARK;
                    }
                    if (lower.contains("\"light\"") ||
                            lower.contains("\"intellij\"") ||
                            lower.contains("\"default\"") ||
                            lower.contains("\"solarized light\"") ||
                            lower.contains("\"gruvbox light\"") ||
                            lower.contains("\"github\"")) {
                        return TerminalTheme.LIGHT;
                    }
                    // If we found the tag but couldn't determine the theme,
                    // assume dark as it's more common for developers
                    LOGGER.log(Level.FINE, "Unknown JetBrains color scheme: " + line.trim());
                    return TerminalTheme.DARK;
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.log(Level.FINE, "Failed to read JetBrains color scheme file", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Query the terminal for its foreground color using OSC 10.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds
     * @return RGB array [r, g, b] (0-255 each), or null if not supported
     */
    public static int[] queryForegroundColor(Connection connection, long timeoutMs) {
        if (connection == null) {
            return null;
        }
        return connection.queryForegroundColor(timeoutMs);
    }

    /**
     * Query the terminal for its background color using OSC 11.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds
     * @return RGB array [r, g, b] (0-255 each), or null if not supported
     */
    public static int[] queryBackgroundColor(Connection connection, long timeoutMs) {
        if (connection == null) {
            return null;
        }
        return connection.queryBackgroundColor(timeoutMs);
    }

    // ==================== Batch Color Queries ====================

    /**
     * Query foreground, background, and cursor colors in a single batch operation.
     * <p>
     * This method is much more efficient than calling {@link #queryForegroundColor}
     * and {@link #queryBackgroundColor} separately. By sending all queries at once,
     * latency is reduced from O(n * timeout) to O(timeout).
     * <p>
     * For example, querying 3 colors individually might take 300-400ms due to
     * serial round-trips, while batch querying takes only 50-100ms.
     * <p>
     * If OSC queries are not supported by the terminal, this method returns an
     * empty map. Use {@link #isOscColorQuerySupported(Connection)} to check support
     * beforehand, or use {@link #detect(Connection)} which has automatic fallbacks.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from OSC code to RGB array [r, g, b] (0-255 each);
     *         keys are {@link org.aesh.terminal.utils.ANSI#OSC_FOREGROUND} (10),
     *         {@link org.aesh.terminal.utils.ANSI#OSC_BACKGROUND} (11),
     *         {@link org.aesh.terminal.utils.ANSI#OSC_CURSOR_COLOR} (12)
     */
    public static java.util.Map<Integer, int[]> queryColors(Connection connection, long timeoutMs) {
        if (connection == null) {
            return java.util.Collections.emptyMap();
        }

        // Check OSC support before attempting queries
        if (!isOscColorQuerySupported(connection)) {
            LOGGER.log(Level.FINE, "OSC color queries not supported, returning empty result");
            return java.util.Collections.emptyMap();
        }

        return connection.queryColors(timeoutMs);
    }

    /**
     * Query multiple palette colors in a single batch operation.
     * <p>
     * This method sends all palette color queries at once, significantly
     * reducing latency compared to calling individual queries multiple times.
     * <p>
     * Palette colors are indexed 0-255, where:
     * <ul>
     * <li>0-7: Standard ANSI colors</li>
     * <li>8-15: Bright ANSI colors</li>
     * <li>16-231: 216-color cube</li>
     * <li>232-255: Grayscale ramp</li>
     * </ul>
     * <p>
     * Note: Not all terminals support OSC 4 palette queries. Use
     * {@link Connection#supportsPaletteQuery()} to check support beforehand.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param indices the palette color indices to query (0-255)
     * @return map from palette index to RGB array [r, g, b] (0-255 each)
     */
    public static java.util.Map<Integer, int[]> queryPaletteColors(Connection connection, long timeoutMs, int... indices) {
        if (connection == null) {
            return java.util.Collections.emptyMap();
        }

        // Check palette query support
        if (!connection.supportsPaletteQuery()) {
            LOGGER.log(Level.FINE, "OSC 4 palette queries not supported");
            return java.util.Collections.emptyMap();
        }

        return connection.queryPaletteColors(timeoutMs, indices);
    }

    /**
     * Query the ANSI 16-color palette (colors 0-15) in a single batch operation.
     * <p>
     * This queries the 8 standard colors (0-7) and 8 bright colors (8-15).
     * <p>
     * Note: Not all terminals support OSC 4 palette queries. Use
     * {@link Connection#supportsPaletteQuery()} to check support beforehand.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from palette index (0-15) to RGB array [r, g, b] (0-255 each)
     */
    public static java.util.Map<Integer, int[]> queryAnsi16Colors(Connection connection, long timeoutMs) {
        if (connection == null) {
            return java.util.Collections.emptyMap();
        }

        // Check palette query support
        if (!connection.supportsPaletteQuery()) {
            LOGGER.log(Level.FINE, "OSC 4 palette queries not supported");
            return java.util.Collections.emptyMap();
        }

        return connection.queryAnsi16Colors(timeoutMs);
    }

    /**
     * Check if an RGB color represents a dark theme (luminance &lt; 0.5).
     * <p>
     * This is useful for determining whether to use light or dark
     * foreground colors based on the terminal's background color.
     *
     * @param rgb the RGB color array [r, g, b] (0-255 each)
     * @return true if the color is dark, false if light
     */
    public static boolean isDarkColor(int[] rgb) {
        if (rgb == null || rgb.length < 3) {
            return true; // assume dark if unknown
        }
        // Calculate relative luminance using ITU-R BT.601 coefficients
        double luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0;
        return luminance < 0.5;
    }

    /**
     * Query colors with automatic fallback to environment-based detection.
     * <p>
     * This method first tries to query the terminal for actual colors.
     * If OSC queries are not supported or fail, it falls back to
     * environment-based theme detection and returns typical colors
     * for that theme.
     * <p>
     * This is the recommended method when you need colors and want
     * graceful degradation on terminals that don't support OSC queries.
     *
     * @param connection the terminal connection
     * @param timeoutMs timeout in milliseconds for OSC queries
     * @return map from OSC code to RGB array; always contains at least
     *         estimated foreground and background colors
     */
    public static java.util.Map<Integer, int[]> queryColorsWithFallback(Connection connection, long timeoutMs) {
        // First try actual OSC queries
        java.util.Map<Integer, int[]> results = queryColors(connection, timeoutMs);

        // If we got results, return them
        if (!results.isEmpty()) {
            return results;
        }

        // Fall back to environment-based detection
        LOGGER.log(Level.FINE, "OSC queries failed or not supported, using environment-based fallback");

        java.util.Map<Integer, int[]> fallback = new java.util.LinkedHashMap<>();
        TerminalTheme theme = detectThemeFromEnvironment();

        if (theme == TerminalTheme.LIGHT) {
            // Typical light theme colors
            fallback.put(org.aesh.terminal.utils.ANSI.OSC_FOREGROUND, new int[] { 0, 0, 0 }); // Black text
            fallback.put(org.aesh.terminal.utils.ANSI.OSC_BACKGROUND, new int[] { 255, 255, 255 }); // White background
        } else {
            // Typical dark theme colors (default assumption)
            fallback.put(org.aesh.terminal.utils.ANSI.OSC_FOREGROUND, new int[] { 204, 204, 204 }); // Light gray text
            fallback.put(org.aesh.terminal.utils.ANSI.OSC_BACKGROUND, new int[] { 30, 30, 30 }); // Dark gray background
        }

        return fallback;
    }

    /**
     * Check if the result contains any detected colors.
     */
    private static boolean hasColors(int[][] result) {
        return result != null && (result[0] != null || result[1] != null);
    }

    /**
     * Try to detect terminal colors by querying tmux options.
     * <p>
     * This is a fallback when OSC queries don't work. It checks:
     * <ul>
     * <li>window-style and window-active-style options</li>
     * <li>pane-border-style and pane-active-border-style options</li>
     * </ul>
     *
     * @return array of two RGB arrays: [foreground, background], either may be null
     */
    private static int[][] detectColorsFromTmux() {
        // This method runs tmux commands to query colors
        // Only call this if we're actually in tmux
        if (!isRunningInTmux()) {
            return null;
        }

        try {
            // Try to get the window-active-style which may contain bg= and fg=
            ProcessBuilder pb = new ProcessBuilder("tmux", "show-options", "-gv", "window-active-style");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            java.io.InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String style = output.toString().trim();
                LOGGER.log(Level.FINE, "tmux window-active-style: " + style);

                int[][] colors = parseTmuxStyle(style);
                if (hasColors(colors)) {
                    return colors;
                }
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.FINE, "Failed to query tmux options", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        return null;
    }

    /**
     * Parse a tmux style string like "bg=colour235,fg=colour252" or "bg=#282828".
     *
     * @param style the tmux style string
     * @return array of two RGB arrays: [foreground, background], either may be null
     */
    private static int[][] parseTmuxStyle(String style) {
        if (style == null || style.isEmpty() || "default".equals(style)) {
            return null;
        }

        int[] fg = null;
        int[] bg = null;

        // Parse key=value pairs separated by commas
        String[] parts = style.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("fg=")) {
                fg = parseTmuxColor(trimmed.substring(3));
            } else if (trimmed.startsWith("bg=")) {
                bg = parseTmuxColor(trimmed.substring(3));
            }
        }

        return new int[][] { fg, bg };
    }

    /**
     * Parse a tmux color value.
     *
     * @param color the color string (e.g., "#282828", "colour235", "black")
     * @return RGB array or null
     */
    private static int[] parseTmuxColor(String color) {
        if (color == null || color.isEmpty() || "default".equals(color)) {
            return null;
        }

        // Hex color: #RRGGBB or #RGB
        if (color.startsWith("#")) {
            try {
                String hex = color.substring(1);
                if (hex.length() == 6) {
                    int r = Integer.parseInt(hex.substring(0, 2), 16);
                    int g = Integer.parseInt(hex.substring(2, 4), 16);
                    int b = Integer.parseInt(hex.substring(4, 6), 16);
                    return new int[] { r, g, b };
                } else if (hex.length() == 3) {
                    int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                    return new int[] { r, g, b };
                }
            } catch (NumberFormatException e) {
                // Fall through
            }
        }

        // 256-color palette: colour0-colour255 or color0-color255
        if (color.startsWith("colour") || color.startsWith("color")) {
            try {
                int num = Integer.parseInt(color.replaceFirst("colou?r", ""));
                return palette256ToRGB(num);
            } catch (NumberFormatException e) {
                // Fall through
            }
        }

        // Named colors (basic 16)
        return namedColorToRGB(color);
    }

    /**
     * Convert a 256-color palette index to RGB.
     */
    private static int[] palette256ToRGB(int index) {
        if (index < 0 || index > 255) {
            return null;
        }

        // Colors 0-15: Standard colors (approximate)
        if (index < 16) {
            int[][] standard = {
                    { 0, 0, 0 }, // 0 black
                    { 128, 0, 0 }, // 1 red
                    { 0, 128, 0 }, // 2 green
                    { 128, 128, 0 }, // 3 yellow
                    { 0, 0, 128 }, // 4 blue
                    { 128, 0, 128 }, // 5 magenta
                    { 0, 128, 128 }, // 6 cyan
                    { 192, 192, 192 }, // 7 white
                    { 128, 128, 128 }, // 8 bright black
                    { 255, 0, 0 }, // 9 bright red
                    { 0, 255, 0 }, // 10 bright green
                    { 255, 255, 0 }, // 11 bright yellow
                    { 0, 0, 255 }, // 12 bright blue
                    { 255, 0, 255 }, // 13 bright magenta
                    { 0, 255, 255 }, // 14 bright cyan
                    { 255, 255, 255 } // 15 bright white
            };
            return standard[index];
        }

        // Colors 16-231: 6x6x6 color cube
        if (index < 232) {
            int n = index - 16;
            int r = (n / 36) % 6;
            int g = (n / 6) % 6;
            int b = n % 6;
            return new int[] {
                    r == 0 ? 0 : 55 + r * 40,
                    g == 0 ? 0 : 55 + g * 40,
                    b == 0 ? 0 : 55 + b * 40
            };
        }

        // Colors 232-255: Grayscale
        int gray = (index - 232) * 10 + 8;
        return new int[] { gray, gray, gray };
    }

    /**
     * Convert a named color to RGB.
     */
    private static int[] namedColorToRGB(String name) {
        if (name == null) {
            return null;
        }
        switch (name.toLowerCase()) {
            case "black":
                return new int[] { 0, 0, 0 };
            case "red":
                return new int[] { 128, 0, 0 };
            case "green":
                return new int[] { 0, 128, 0 };
            case "yellow":
                return new int[] { 128, 128, 0 };
            case "blue":
                return new int[] { 0, 0, 128 };
            case "magenta":
                return new int[] { 128, 0, 128 };
            case "cyan":
                return new int[] { 0, 128, 128 };
            case "white":
                return new int[] { 192, 192, 192 };
            default:
                return null;
        }
    }

    /**
     * Check if the terminal likely supports OSC color queries.
     * <p>
     * This is a heuristic based on the TERM environment variable and
     * known terminal emulators. For more accurate detection, use
     * {@link Device#supportsOscQueries()} or {@link Connection#supportsOscQueries()}.
     *
     * @return true if OSC queries are likely supported
     * @see Device#supportsOscQueries()
     * @see Connection#supportsOscQueries()
     * @see #isOscColorQuerySupported(Connection)
     * @deprecated Use {@link Connection#supportsColorQuery()} instead for more accurate detection.
     */
    @Deprecated
    public static boolean isOscColorQuerySupported() {
        // Check TERMINAL_EMULATOR for JetBrains IDEs (IntelliJ, etc.)
        // JediTerm does NOT support OSC 10/11 queries
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null &&
                terminalEmulator.toLowerCase().contains("jetbrains")) {
            return false;
        }

        // Check for terminal-specific environment variables that indicate
        // the outer terminal supports OSC queries (even if running in tmux)
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null ||
                System.getenv("KITTY_WINDOW_ID") != null ||
                System.getenv("ALACRITTY_SOCKET") != null ||
                System.getenv("WEZTERM_PANE") != null ||
                System.getenv("ITERM_SESSION_ID") != null) {
            return true;
        }

        String term = System.getenv("TERM");
        String termLower = term != null ? term.toLowerCase() : "";

        // Terminal multiplexers don't pass through OSC queries properly
        // unless allow-passthrough is enabled
        if (termLower.startsWith("screen") || termLower.startsWith("tmux")) {
            return isTmuxPassthroughLikely();
        }

        // Known terminals that support OSC 10/11 queries (check TERM)
        if (termLower.contains("xterm") ||
                termLower.contains("vte") ||
                termLower.contains("rxvt") ||
                termLower.contains("konsole") ||
                termLower.contains("iterm") ||
                termLower.contains("alacritty") ||
                termLower.contains("kitty") ||
                termLower.contains("ghostty") ||
                termLower.contains("wezterm") ||
                termLower.contains("foot") ||
                termLower.contains("contour") ||
                termLower.contains("rio") ||
                termLower.contains("warp") ||
                termLower.contains("hyper") ||
                termLower.contains("terminus") ||
                termLower.contains("tabby") ||
                termLower.contains("extraterm") ||
                termLower.contains("wave")) {
            return true;
        }

        // Check TERM_PROGRAM for macOS terminals and others
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String termProgramLower = termProgram.toLowerCase();
            // Terminal multiplexers
            if (termProgramLower.equals("tmux") || termProgramLower.equals("screen")) {
                return isTmuxPassthroughLikely();
            }
            if (termProgramLower.contains("iterm") ||
                    termProgramLower.contains("apple_terminal") ||
                    termProgramLower.contains("terminal.app") ||
                    termProgramLower.contains("alacritty") ||
                    termProgramLower.contains("kitty") ||
                    termProgramLower.contains("ghostty") ||
                    termProgramLower.contains("wezterm") ||
                    termProgramLower.contains("vscode") ||
                    termProgramLower.contains("hyper") ||
                    termProgramLower.contains("terminus") ||
                    termProgramLower.contains("tabby")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the terminal likely supports OSC color queries.
     * <p>
     * This method uses the Connection's device to detect terminal type and
     * check OSC support more accurately than the static method.
     *
     * @param connection the terminal connection to check
     * @return true if OSC color queries are likely supported
     * @see Connection#supportsColorQuery()
     */
    public static boolean isOscColorQuerySupported(Connection connection) {
        return connection != null && connection.supportsColorQuery();
    }

    /**
     * Check if tmux passthrough is likely enabled.
     * <p>
     * Tmux 3.3+ supports allow-passthrough option which enables OSC queries
     * to be passed through to the underlying terminal. This method checks
     * for hints that passthrough might be enabled.
     * <p>
     * Detection methods:
     * <ul>
     * <li>TMUX_PASSTHROUGH environment variable (custom, set by user)</li>
     * <li>Check if running in a modern tmux version (3.3+)</li>
     * <li>Check if a known OSC-capable outer terminal is detected</li>
     * </ul>
     * <p>
     * Note: This is a best-effort heuristic. The only reliable way to know
     * if passthrough is enabled is to attempt an OSC query.
     *
     * @return true if tmux passthrough is likely enabled
     */
    public static boolean isTmuxPassthroughLikely() {
        // Check for custom environment variable that user can set
        String passthrough = System.getenv("TMUX_PASSTHROUGH");
        if (passthrough != null) {
            return "1".equals(passthrough) ||
                    "true".equalsIgnoreCase(passthrough) ||
                    "on".equalsIgnoreCase(passthrough);
        }

        // Check TMUX environment variable for version info
        // Format: /tmp/tmux-1000/default,12345,0 or similar
        String tmux = System.getenv("TMUX");
        if (tmux == null || tmux.isEmpty()) {
            // Not running in tmux
            return false;
        }

        // Try to detect tmux version from TMUX_VERSION if available
        // (Not a standard variable, but some setups provide it)
        String tmuxVersion = System.getenv("TMUX_VERSION");
        if (tmuxVersion != null) {
            try {
                // Parse version like "3.3" or "3.3a"
                String numericPart = tmuxVersion.replaceAll("[^0-9.]", "");
                String[] parts = numericPart.split("\\.");
                if (parts.length >= 2) {
                    int major = Integer.parseInt(parts[0]);
                    int minor = Integer.parseInt(parts[1]);
                    // Passthrough support was added in tmux 3.3
                    if (major > 3 || (major == 3 && minor >= 3)) {
                        // Version supports passthrough, but it's off by default
                        // We can't know if it's enabled, so return false
                        // unless user explicitly sets TMUX_PASSTHROUGH=1
                        return false;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }

        // Default: assume passthrough is not enabled
        return false;
    }

    /**
     * Check if we should use tmux DCS passthrough wrapping for OSC queries.
     * <p>
     * This returns true when:
     * <ul>
     * <li>We are running inside tmux</li>
     * <li>AND we detect a known OSC-capable outer terminal (like Alacritty, Kitty, etc.)</li>
     * </ul>
     * <p>
     * When passthrough is used, OSC sequences are wrapped in DCS (Device Control String)
     * sequences that tmux will forward to the outer terminal.
     *
     * @return true if tmux passthrough wrapping should be used
     */
    public static boolean shouldUseTmuxPassthrough() {
        // Only relevant if running in tmux
        if (!isRunningInTmux()) {
            return false;
        }

        // Check if user explicitly enabled passthrough
        String passthrough = System.getenv("TMUX_PASSTHROUGH");
        if (passthrough != null) {
            return "1".equals(passthrough) ||
                    "true".equalsIgnoreCase(passthrough) ||
                    "on".equalsIgnoreCase(passthrough);
        }

        // Check if we detect a known OSC-capable outer terminal
        // If so, it's worth trying passthrough
        if (System.getenv("ALACRITTY_SOCKET") != null ||
                System.getenv("KITTY_WINDOW_ID") != null ||
                System.getenv("WEZTERM_PANE") != null ||
                System.getenv("GHOSTTY_RESOURCES_DIR") != null ||
                System.getenv("ITERM_SESSION_ID") != null) {
            return true;
        }

        // Note: JetBrains/JediTerm does NOT support OSC queries or DCS passthrough
        // so we explicitly don't include it here

        return false;
    }

    /**
     * Check if running inside tmux.
     *
     * @return true if running inside tmux
     */
    public static boolean isRunningInTmux() {
        String tmux = System.getenv("TMUX");
        return tmux != null && !tmux.isEmpty();
    }

    /**
     * Check if running inside GNU Screen.
     *
     * @return true if running inside screen
     */
    public static boolean isRunningInScreen() {
        String sty = System.getenv("STY");
        return sty != null && !sty.isEmpty();
    }

    /**
     * Check if running inside any terminal multiplexer (tmux or screen).
     *
     * @return true if running inside a multiplexer
     */
    public static boolean isRunningInMultiplexer() {
        return isRunningInTmux() || isRunningInScreen();
    }

    /**
     * Get a fast, non-blocking color capability based only on environment
     * detection (no terminal queries).
     *
     * @param connection the terminal connection (may be null)
     * @return detected color capabilities
     */
    public static TerminalColorCapability detectFast(Connection connection) {
        ColorDepth depth = detectColorDepth(connection);
        TerminalTheme theme = detectThemeFromEnvironment();
        return new TerminalColorCapability(depth, theme);
    }
}
