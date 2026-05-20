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

/**
 * Controls mouse tracking on the terminal.
 * <p>
 * Mouse tracking is enabled by sending DEC private mode sequences to the
 * terminal. The protocol controls which events are reported, and the
 * encoding controls how coordinates are transmitted.
 * <p>
 * Example usage:
 *
 * <pre>
 * // Enable normal tracking with SGR encoding
 * MouseTracking.enable(connection, MouseTracking.Protocol.NORMAL);
 * MouseTracking.enableEncoding(connection, MouseTracking.Encoding.SGR);
 *
 * // ... handle mouse events ...
 *
 * // Disable on cleanup
 * MouseTracking.disable(connection, MouseTracking.Protocol.NORMAL);
 * MouseTracking.disableEncoding(connection, MouseTracking.Encoding.SGR);
 * </pre>
 *
 * @see MouseEvent
 */
public final class MouseTracking {

    /**
     * Mouse tracking protocol — controls which events are reported.
     */
    public enum Protocol {
        /** X10 compatibility mode: button press only. */
        X10(9),
        /** Normal tracking: press + release. */
        NORMAL(1000),
        /** Button-event tracking: press + release + drag. */
        BUTTON_MOTION(1002),
        /** Any-event tracking: press + release + drag + hover. */
        ANY_MOTION(1003);

        final int mode;

        Protocol(int mode) {
            this.mode = mode;
        }
    }

    /**
     * Mouse coordinate encoding — controls how coordinates are transmitted.
     */
    public enum Encoding {
        /** SGR encoding: {@code CSI < Pb;Px;Py M/m}. Recommended. No coordinate limits. */
        SGR(1006),
        /** UTF-8 encoding. Deprecated. */
        UTF8(1005),
        /** URXVT encoding: {@code CSI Pb;Px;Py M}. No release button ID. */
        URXVT(1015),
        /** SGR-Pixels encoding: like SGR but with pixel coordinates. */
        SGR_PIXELS(1016);

        final int mode;

        Encoding(int mode) {
            this.mode = mode;
        }
    }

    /**
     * Enables a mouse tracking protocol.
     *
     * @param out the output to write the escape sequence to
     * @param protocol the protocol to enable
     * @throws IOException if writing fails
     */
    public static void enable(Appendable out, Protocol protocol) throws IOException {
        setMode(out, protocol.mode, true);
    }

    /**
     * Disables a mouse tracking protocol.
     *
     * @param out the output to write the escape sequence to
     * @param protocol the protocol to disable
     * @throws IOException if writing fails
     */
    public static void disable(Appendable out, Protocol protocol) throws IOException {
        setMode(out, protocol.mode, false);
    }

    /**
     * Enables a mouse coordinate encoding.
     *
     * @param out the output to write the escape sequence to
     * @param encoding the encoding to enable
     * @throws IOException if writing fails
     */
    public static void enableEncoding(Appendable out, Encoding encoding) throws IOException {
        setMode(out, encoding.mode, true);
    }

    /**
     * Disables a mouse coordinate encoding.
     *
     * @param out the output to write the escape sequence to
     * @param encoding the encoding to disable
     * @throws IOException if writing fails
     */
    public static void disableEncoding(Appendable out, Encoding encoding) throws IOException {
        setMode(out, encoding.mode, false);
    }

    private static void setMode(Appendable out, int mode, boolean enable) throws IOException {
        // CSI ? {mode} h (enable) or CSI ? {mode} l (disable)
        out.append('\033');
        out.append('[');
        out.append('?');
        out.append(Integer.toString(mode));
        out.append(enable ? 'h' : 'l');
    }

    private MouseTracking() {
    }
}
