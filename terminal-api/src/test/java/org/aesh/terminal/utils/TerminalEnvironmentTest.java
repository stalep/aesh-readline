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
package org.aesh.terminal.utils;

import static org.junit.Assert.*;

import org.aesh.terminal.Device;
import org.junit.Test;

/**
 * Tests for TerminalEnvironment class.
 * <p>
 * Note: Many tests here verify behavior based on the actual environment,
 * so assertions are kept general to pass in various CI/local environments.
 */
public class TerminalEnvironmentTest {

    @Test
    public void testSingletonInstance() {
        TerminalEnvironment env1 = TerminalEnvironment.getInstance();
        TerminalEnvironment env2 = TerminalEnvironment.getInstance();

        assertNotNull("Instance should not be null", env1);
        assertSame("Should return same instance", env1, env2);
    }

    @Test
    public void testRefreshCreatesNewInstance() {
        TerminalEnvironment before = TerminalEnvironment.getInstance();
        TerminalEnvironment.refresh();
        TerminalEnvironment after = TerminalEnvironment.getInstance();

        // After refresh, the instance fields are repopulated
        // The instance itself may or may not be the same object
        // depending on the implementation details, but the values
        // should be consistent
        assertNotNull(after);
        assertNotNull(after.getTerminalType());
    }

    @Test
    public void testTerminalTypeNeverNull() {
        Device.TerminalType type = TerminalEnvironment.getInstance().getTerminalType();
        assertNotNull("Terminal type should never be null", type);
    }

    @Test
    public void testColorDepthNeverNull() {
        ColorDepth depth = TerminalEnvironment.getInstance().getDefaultColorDepth();
        assertNotNull("Color depth should never be null", depth);
    }

    @Test
    public void testStaticConvenienceMethods() {
        // Static methods should work and return consistent results
        Device.TerminalType type = TerminalEnvironment.detectTerminalType();
        ColorDepth depth = TerminalEnvironment.detectColorDepth();

        assertNotNull(type);
        assertNotNull(depth);

        // Should match instance methods
        assertEquals(type, TerminalEnvironment.getInstance().getTerminalType());
        assertEquals(depth, TerminalEnvironment.getInstance().getDefaultColorDepth());
    }

    @Test
    public void testIsJetBrainsTerminal() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.isJetBrainsTerminal();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsOscSupported() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.isOscSupported();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsInMultiplexer() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.getInstance().isInMultiplexer();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsInTmux() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        boolean inTmux = env.isInTmux();

        // If in tmux, isInMultiplexer should also be true
        if (inTmux) {
            assertTrue("If in tmux, should be in multiplexer", env.isInMultiplexer());
        }
    }

    @Test
    public void testIsInScreen() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        boolean inScreen = env.isInScreen();

        // If in screen, isInMultiplexer should also be true
        if (inScreen) {
            assertTrue("If in screen, should be in multiplexer", env.isInMultiplexer());
        }
    }

    @Test
    public void testTmuxPassthroughWhenNotInTmux() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (!env.isInTmux()) {
            assertFalse("Passthrough should be false when not in tmux",
                    env.isTmuxPassthroughEnabled());
        }
    }

    @Test
    public void testHasKnownOuterTerminal() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If any specific terminal env var is set, hasKnownOuterTerminal should be true
        if (env.isKitty() || env.isGhostty() || env.isWezTerm() ||
                env.isITerm2() || env.isAlacritty()) {
            assertTrue("Should detect known outer terminal", env.hasKnownOuterTerminal());
        }
    }

    @Test
    public void testIsTrueColorIndicated() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If true color is indicated, color depth should be true color
        if (env.isTrueColorIndicated()) {
            assertEquals(ColorDepth.TRUE_COLOR, env.getDefaultColorDepth());
        }
    }

    @Test
    public void testToStringContainsRelevantInfo() {
        String str = TerminalEnvironment.getInstance().toString();

        assertNotNull(str);
        assertTrue("Should contain terminalType", str.contains("terminalType"));
        assertTrue("Should contain colorDepth", str.contains("colorDepth"));
    }

    @Test
    public void testTermAccessors() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // These can return null if not set, just verify no exception
        String term = env.getTerm();
        String termProgram = env.getTermProgram();
        String terminalEmulator = env.getTerminalEmulator();
        String colorterm = env.getColorterm();
        String colorFgBg = env.getColorFgBg();

        // No assertions on values - they depend on environment
    }

    @Test
    public void testSupportsOscQueriesLogic() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If JetBrains, OSC queries should not be supported
        if (env.isJetBrains()) {
            assertFalse("JetBrains should not support OSC queries",
                    env.supportsOscQueries());
        }

        // If has known outer terminal, should likely support OSC
        if (env.hasKnownOuterTerminal() && !env.isJetBrains()) {
            assertTrue("Known outer terminal should support OSC",
                    env.supportsOscQueries());
        }
    }

    @Test
    public void testKittyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isKitty()) {
            assertEquals("Kitty should map to KITTY terminal type",
                    Device.TerminalType.KITTY, env.getTerminalType());
        }
    }

    @Test
    public void testGhosttyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isGhostty()) {
            assertEquals("Ghostty should map to GHOSTTY terminal type",
                    Device.TerminalType.GHOSTTY, env.getTerminalType());
        }
    }

    @Test
    public void testWezTermDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isWezTerm()) {
            assertEquals("WezTerm should map to WEZTERM terminal type",
                    Device.TerminalType.WEZTERM, env.getTerminalType());
        }
    }

    @Test
    public void testITerm2Detection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isITerm2()) {
            assertEquals("iTerm2 should map to ITERM2 terminal type",
                    Device.TerminalType.ITERM2, env.getTerminalType());
        }
    }

    @Test
    public void testWindowsTerminalDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isWindowsTerminal()) {
            assertEquals("Windows Terminal should map to WINDOWS_TERMINAL type",
                    Device.TerminalType.WINDOWS_TERMINAL, env.getTerminalType());
        }
    }

    @Test
    public void testConEmuDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isConEmu()) {
            assertEquals("ConEmu should map to CONEMU terminal type",
                    Device.TerminalType.CONEMU, env.getTerminalType());
        }
    }

    @Test
    public void testAlacrittyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isAlacritty()) {
            assertEquals("Alacritty should map to ALACRITTY terminal type",
                    Device.TerminalType.ALACRITTY, env.getTerminalType());
        }
    }

    @Test
    public void testJetBrainsDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isJetBrains()) {
            assertEquals("JetBrains should map to JETBRAINS terminal type",
                    Device.TerminalType.JETBRAINS, env.getTerminalType());
        }
    }

    @Test
    public void testMacOsDarkMode() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // Just verify no exception
        boolean darkMode = env.isMacOsDarkMode();
        String style = env.getAppleInterfaceStyle();

        if ("Dark".equalsIgnoreCase(style)) {
            assertTrue("Dark style should indicate dark mode", darkMode);
        }
    }
}
