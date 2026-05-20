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
package org.aesh.terminal.parser;

/**
 * Callback interface for VT escape sequence parser events.
 * <p>
 * All methods have default no-op implementations so that consumers
 * only need to override the events they care about.
 * <p>
 * The parameter arrays passed to callbacks are reused between calls —
 * consumers must copy the data if they need to retain it.
 *
 * @see VtParser
 */
public interface VtHandler {

    /**
     * A printable character was received in the ground state.
     *
     * @param codePoint the character (0x20-0x7F or 0xA0-0xFF)
     */
    default void print(int codePoint) {
    }

    /**
     * A C0 or C1 control character was received.
     *
     * @param controlChar the control character (0x00-0x1F, 0x80-0x9F)
     */
    default void execute(int controlChar) {
    }

    /**
     * An escape sequence was completed.
     * <p>
     * Examples: {@code ESC M} (Reverse Index), {@code ESC ( B} (Select charset).
     *
     * @param finalChar the final character of the sequence
     * @param intermediates the intermediate characters (0x20-0x2F)
     * @param intermediateCount the number of intermediate characters
     */
    default void escDispatch(int finalChar, int[] intermediates, int intermediateCount) {
    }

    /**
     * A CSI (Control Sequence Introducer) sequence was completed.
     * <p>
     * Examples: {@code CSI 2 J} (Erase Display), {@code CSI ? 1049 h} (Alt Screen).
     * <p>
     * The first intermediate may be a private marker (0x3C-0x3F: {@code < = > ?}).
     * Parameter value {@code -1} indicates a default (empty) parameter.
     *
     * @param finalChar the final character (0x40-0x7E)
     * @param params the parameter values (-1 = default)
     * @param paramCount the number of parameters
     * @param intermediates the intermediate/private-marker characters
     * @param intermediateCount the number of intermediates
     * @param hasSubParams true if colon-separated subparameters were present
     */
    default void csiDispatch(int finalChar, int[] params, int paramCount,
            int[] intermediates, int intermediateCount,
            boolean hasSubParams) {
    }

    /**
     * A DCS (Device Control String) was started.
     * <p>
     * The first part of the DCS has been parsed (parameters, intermediates,
     * final character). Subsequent data arrives via {@link #put(int)} until
     * {@link #unhook()} is called.
     *
     * @param finalChar the final character determining the control function
     * @param params the parameter values
     * @param paramCount the number of parameters
     * @param intermediates the intermediate characters
     * @param intermediateCount the number of intermediates
     */
    default void hook(int finalChar, int[] params, int paramCount,
            int[] intermediates, int intermediateCount) {
    }

    /**
     * A data byte within a DCS passthrough string.
     *
     * @param b the data byte
     */
    default void put(int b) {
    }

    /**
     * The DCS passthrough string has ended (ST, CAN, SUB, or ESC received).
     */
    default void unhook() {
    }

    /**
     * An OSC (Operating System Command) string was completed.
     * <p>
     * Examples: {@code OSC 0 ; title BEL} (Set Title),
     * {@code OSC 10 ; ? BEL} (Query Foreground Color).
     *
     * @param data the OSC string content (between the OSC introducer and terminator)
     */
    default void oscEnd(String data) {
    }
}
