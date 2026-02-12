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
package org.aesh.terminal.tty.utils;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.TerminalEnvironment;

/**
 * Utility methods for color conversion and tmux color detection.
 *
 * @author Ståle W. Pedersen
 */
public final class ColorUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(ColorUtils.class.getName());

    private ColorUtils() {
    }

    /**
     * Determine if an RGB color is dark based on perceived luminance.
     * <p>
     * Uses ITU-R BT.601 coefficients: 0.299*R + 0.587*G + 0.114*B
     *
     * @param rgb the RGB color array [r, g, b] (0-255 each)
     * @return true if the color is dark, false if light
     */
    public static boolean isDarkColor(int[] rgb) {
        if (rgb == null || rgb.length < 3) {
            return true; // assume dark if unknown
        }
        double luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255.0;
        return luminance < 0.5;
    }

    /**
     * Convert a 256-color palette index to RGB.
     *
     * @param index the palette index (0-255)
     * @return RGB array [r, g, b], or null if index is out of range
     */
    public static int[] palette256ToRGB(int index) {
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
     *
     * @param name the color name (e.g., "black", "red", "green")
     * @return RGB array [r, g, b], or null if name is not recognized
     */
    public static int[] namedColorToRGB(String name) {
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
     * Parse a tmux color value.
     *
     * @param color the color string (e.g., "#282828", "colour235", "black")
     * @return RGB array or null
     */
    public static int[] parseTmuxColor(String color) {
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
     * Parse a tmux style string like "bg=colour235,fg=colour252" or "bg=#282828".
     *
     * @param style the tmux style string
     * @return array of two RGB arrays: [foreground, background], either may be null
     */
    public static int[][] parseTmuxStyle(String style) {
        if (style == null || style.isEmpty() || "default".equals(style)) {
            return null;
        }

        int[] fg = null;
        int[] bg = null;

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
     * Try to detect terminal colors by querying tmux options.
     * <p>
     * Only works when running inside tmux. Queries the window-active-style option.
     *
     * @return array of two RGB arrays: [foreground, background], either may be null
     */
    public static int[][] detectColorsFromTmux() {
        if (!TerminalEnvironment.getInstance().isInTmux()) {
            return null;
        }

        try {
            ProcessHelper.ProcessResult result = ProcessHelper.execute("tmux", "show-options", "-gv", "window-active-style");
            if (result.success()) {
                String style = result.output();
                LOGGER.log(Level.FINE, "tmux window-active-style: " + style);

                int[][] colors = parseTmuxStyle(style);
                if (colors != null && (colors[0] != null || colors[1] != null)) {
                    return colors;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to query tmux options", e);
        }

        return null;
    }
}
