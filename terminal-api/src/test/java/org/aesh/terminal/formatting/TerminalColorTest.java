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
package org.aesh.terminal.formatting;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColorTest {

    @Test
    public void testTerminalColor() {
        TerminalColor color = new TerminalColor(Color.DEFAULT, Color.BLACK);

        assertEquals("3" + Color.DEFAULT.getValue() + ";4" + Color.BLACK.getValue(), color.toString());

        assertTrue(color.isFormatted());

        color = new TerminalColor();
        assertFalse(color.isFormatted());
    }

    @Test
    public void testHslToRgbPrimaryColors() {
        // Red: H=0, S=100, L=50
        int[] red = TerminalColor.hslToRgb(0, 100, 50);
        assertEquals(255, red[0]);
        assertEquals(0, red[1]);
        assertEquals(0, red[2]);

        // Green: H=120, S=100, L=50
        int[] green = TerminalColor.hslToRgb(120, 100, 50);
        assertEquals(0, green[0]);
        assertEquals(255, green[1]);
        assertEquals(0, green[2]);

        // Blue: H=240, S=100, L=50
        int[] blue = TerminalColor.hslToRgb(240, 100, 50);
        assertEquals(0, blue[0]);
        assertEquals(0, blue[1]);
        assertEquals(255, blue[2]);
    }

    @Test
    public void testHslToRgbSecondaryColors() {
        // Yellow: H=60, S=100, L=50
        int[] yellow = TerminalColor.hslToRgb(60, 100, 50);
        assertEquals(255, yellow[0]);
        assertEquals(255, yellow[1]);
        assertEquals(0, yellow[2]);

        // Cyan: H=180, S=100, L=50
        int[] cyan = TerminalColor.hslToRgb(180, 100, 50);
        assertEquals(0, cyan[0]);
        assertEquals(255, cyan[1]);
        assertEquals(255, cyan[2]);

        // Magenta: H=300, S=100, L=50
        int[] magenta = TerminalColor.hslToRgb(300, 100, 50);
        assertEquals(255, magenta[0]);
        assertEquals(0, magenta[1]);
        assertEquals(255, magenta[2]);
    }

    @Test
    public void testHslToRgbGrayscale() {
        // Black: L=0
        int[] black = TerminalColor.hslToRgb(0, 0, 0);
        assertEquals(0, black[0]);
        assertEquals(0, black[1]);
        assertEquals(0, black[2]);

        // White: L=100
        int[] white = TerminalColor.hslToRgb(0, 0, 100);
        assertEquals(255, white[0]);
        assertEquals(255, white[1]);
        assertEquals(255, white[2]);

        // Gray: L=50, S=0
        int[] gray = TerminalColor.hslToRgb(0, 0, 50);
        assertEquals(128, gray[0]);
        assertEquals(128, gray[1]);
        assertEquals(128, gray[2]);
    }

    @Test
    public void testHslToRgbLightness() {
        // Light red: H=0, S=100, L=75
        int[] lightRed = TerminalColor.hslToRgb(0, 100, 75);
        assertEquals(255, lightRed[0]);
        assertEquals(128, lightRed[1]);
        assertEquals(128, lightRed[2]);

        // Dark red: H=0, S=100, L=25
        int[] darkRed = TerminalColor.hslToRgb(0, 100, 25);
        assertEquals(128, darkRed[0]);
        assertEquals(0, darkRed[1]);
        assertEquals(0, darkRed[2]);
    }

    @Test
    public void testHslHueWrapping() {
        // 360 degrees should equal 0 degrees (red)
        int[] red360 = TerminalColor.hslToRgb(360, 100, 50);
        int[] red0 = TerminalColor.hslToRgb(0, 100, 50);
        assertArrayEquals(red0, red360);

        // Negative hue should wrap
        int[] negativeHue = TerminalColor.hslToRgb(-60, 100, 50);
        int[] positiveHue = TerminalColor.hslToRgb(300, 100, 50);
        assertArrayEquals(positiveHue, negativeHue);
    }

    @Test
    public void testRgbToHsl() {
        // Red
        float[] redHsl = TerminalColor.rgbToHsl(255, 0, 0);
        assertEquals(0, redHsl[0], 0.1);
        assertEquals(100, redHsl[1], 0.1);
        assertEquals(50, redHsl[2], 0.1);

        // Green
        float[] greenHsl = TerminalColor.rgbToHsl(0, 255, 0);
        assertEquals(120, greenHsl[0], 0.1);
        assertEquals(100, greenHsl[1], 0.1);
        assertEquals(50, greenHsl[2], 0.1);

        // Blue
        float[] blueHsl = TerminalColor.rgbToHsl(0, 0, 255);
        assertEquals(240, blueHsl[0], 0.1);
        assertEquals(100, blueHsl[1], 0.1);
        assertEquals(50, blueHsl[2], 0.1);
    }

    @Test
    public void testRgbToHslGray() {
        // Gray has 0 saturation
        float[] grayHsl = TerminalColor.rgbToHsl(128, 128, 128);
        assertEquals(0, grayHsl[1], 0.1); // Saturation should be 0
    }

    @Test
    public void testFromHSL() {
        // Create color from HSL
        TerminalColor color = TerminalColor.fromHSL(0, 100, 50);
        assertTrue(color.isTrueColor());

        int[] rgb = color.getTextRGB();
        assertNotNull(rgb);
        assertEquals(255, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testFromHSLWithBackground() {
        // Create color with foreground and background from HSL
        TerminalColor color = TerminalColor.fromHSL(0, 100, 50, 240, 100, 50);
        assertTrue(color.isTrueColor());

        int[] fgRgb = color.getTextRGB();
        assertNotNull(fgRgb);
        assertEquals(255, fgRgb[0]); // Red foreground

        int[] bgRgb = color.getBackgroundRGB();
        assertNotNull(bgRgb);
        assertEquals(255, bgRgb[2]); // Blue background
    }

    @Test
    public void testGetTextHSL() {
        TerminalColor color = TerminalColor.fromRGB(255, 0, 0);
        float[] hsl = color.getTextHSL();
        assertNotNull(hsl);
        assertEquals(0, hsl[0], 0.1);
        assertEquals(100, hsl[1], 0.1);
        assertEquals(50, hsl[2], 0.1);
    }

    @Test
    public void testHslRoundTrip() {
        // Convert HSL -> RGB -> HSL should give same values
        float h = 180, s = 80, l = 60;
        int[] rgb = TerminalColor.hslToRgb(h, s, l);
        float[] hsl = TerminalColor.rgbToHsl(rgb[0], rgb[1], rgb[2]);

        assertEquals(h, hsl[0], 1.0);
        assertEquals(s, hsl[1], 1.0);
        assertEquals(l, hsl[2], 1.0);
    }
}
