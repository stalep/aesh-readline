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
package org.aesh.terminal;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Terminal attributes representing the terminal state including control characters,
 * input flags, output flags, control flags, and local flags.
 */
public class Attributes {

    /**
     * Control characters for terminal I/O processing.
     */
    public enum ControlChar {
        /** End-of-file character. */
        VEOF,
        /** End-of-line character. */
        VEOL,
        /** Second end-of-line character. */
        VEOL2,
        /** Erase character. */
        VERASE,
        /** Word erase character. */
        VWERASE,
        /** Kill (line erase) character. */
        VKILL,
        /** Reprint line character. */
        VREPRINT,
        /** Interrupt character. */
        VINTR,
        /** Quit character. */
        VQUIT,
        /** Suspend character. */
        VSUSP,
        /** Delayed suspend character. */
        VDSUSP,
        /** Start output character. */
        VSTART,
        /** Stop output character. */
        VSTOP,
        /** Literal next character. */
        VLNEXT,
        /** Discard output character. */
        VDISCARD,
        /** Minimum number of characters for non-canonical read. */
        VMIN,
        /** Timeout for non-canonical read. */
        VTIME,
        /** Status request character. */
        VSTATUS
    }

    /**
     * Input flags for software input processing.
     */
    public enum InputFlag {
        /** Ignore BREAK condition. */
        IGNBRK,
        /** Map BREAK to SIGINTR. */
        BRKINT,
        /** Ignore (discard) parity errors. */
        IGNPAR,
        /** Mark parity and framing errors. */
        PARMRK,
        /** Enable checking of parity errors. */
        INPCK,
        /** Strip 8th bit off characters. */
        ISTRIP,
        /** Map NL into CR. */
        INLCR,
        /** Ignore CR. */
        IGNCR,
        /** Map CR to NL. */
        ICRNL,
        /** Enable output flow control. */
        IXON,
        /** Enable input flow control. */
        IXOFF,
        /** Any character will restart after stop. */
        IXANY,
        /** Ring bell on input queue full. */
        IMAXBEL,
        /** Maintain state for UTF-8 VERASE. */
        IUTF8
    }

    /**
     * Output flags for software output processing.
     */
    public enum OutputFlag {
        /** Enable output processing. */
        OPOST,
        /** Map NL to CR-NL. */
        ONLCR,
        /** Expand tabs to spaces. */
        OXTABS,
        /** Discard EOT's (^D) on output. */
        ONOEOT,
        /** Map CR to NL on output. */
        OCRNL,
        /** No CR output at column 0. */
        ONOCR,
        /** NL performs CR function. */
        ONLRET,
        /** Newline delay. */
        NLDLY,
        /** Carriage return delay. */
        CRDLY,
        /** Form feed delay. */
        FFDLY,
        /** Backspace delay. */
        BSDLY,
        /** Vertical tab delay. */
        VTDLY,
        /** Horizontal tab delay. */
        TABDLY,
        /** Use fill characters for delay. */
        OFILL,
        /** Fill is DEL, else NUL. */
        OFDEL
    }

    /**
     * Control flags for hardware control of terminal.
     */
    public enum ControlFlag {
        /** Ignore control flags. */
        CIGNORE,
        /** 5 bits per character. */
        CS5,
        /** 6 bits per character. */
        CS6,
        /** 7 bits per character. */
        CS7,
        /** 8 bits per character. */
        CS8,
        /** Send 2 stop bits. */
        CSTOPB,
        /** Enable receiver. */
        CREAD,
        /** Parity enable. */
        PARENB,
        /** Odd parity, else even. */
        PARODD,
        /** Hang up on last close. */
        HUPCL,
        /** Ignore modem status lines. */
        CLOCAL,
        /** CTS flow control of output. */
        CCTS_OFLOW,
        /** RTS flow control of input. */
        CRTS_IFLOW,
        /** DTR flow control of input. */
        CDTR_IFLOW,
        /** DSR flow control of output. */
        CDSR_OFLOW,
        /** DCD flow control of output. */
        CCAR_OFLOW
    }

    /**
     * Local flags for terminal behavior.
     */
    public enum LocalFlag {
        /** Visual erase for line kill. */
        ECHOKE,
        /** Visually erase characters. */
        ECHOE,
        /** Echo NL after line kill. */
        ECHOK,
        /** Enable echoing. */
        ECHO,
        /** Echo NL even if ECHO is off. */
        ECHONL,
        /** Visual erase mode for hardcopy. */
        ECHOPRT,
        /** Echo control chars as ^(Char). */
        ECHOCTL,
        /** Enable signals INTR, QUIT, [D]SUSP. */
        ISIG,
        /** Canonicalize input lines. */
        ICANON,
        /** Use alternate WERASE algorithm. */
        ALTWERASE,
        /** Enable DISCARD and LNEXT. */
        IEXTEN,
        /** External processing. */
        EXTPROC,
        /** Stop background jobs from output. */
        TOSTOP,
        /** Output being flushed (state). */
        FLUSHO,
        /** No kernel output from VSTATUS. */
        NOKERNINFO,
        /** Retype pending input (state). */
        PENDIN,
        /** Don't flush after interrupt. */
        NOFLSH
    }

    private final EnumSet<InputFlag> inputFlags = EnumSet.noneOf(InputFlag.class);
    private final EnumSet<OutputFlag> outputFlags = EnumSet.noneOf(OutputFlag.class);
    private final EnumSet<ControlFlag> controlFlags = EnumSet.noneOf(ControlFlag.class);
    private final EnumSet<LocalFlag> localFlags = EnumSet.noneOf(LocalFlag.class);
    private final EnumMap<ControlChar, Integer> controlChars = new EnumMap<>(ControlChar.class);

    /**
     * Creates a new Attributes instance with default values.
     */
    public Attributes() {
    }

    /**
     * Creates a new Attributes instance by copying from another.
     *
     * @param attr the attributes to copy from
     */
    public Attributes(Attributes attr) {
        copy(attr);
    }

    //
    // Input flags
    //

    /**
     * Returns the set of input flags.
     *
     * @return the input flags
     */
    public EnumSet<InputFlag> getInputFlags() {
        return inputFlags;
    }

    /**
     * Sets the input flags, replacing any existing flags.
     *
     * @param flags the input flags to set
     */
    public void setInputFlags(EnumSet<InputFlag> flags) {
        inputFlags.clear();
        inputFlags.addAll(flags);
    }

    /**
     * Checks if a specific input flag is set.
     *
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public boolean getInputFlag(InputFlag flag) {
        return inputFlags.contains(flag);
    }

    /**
     * Adds or removes multiple input flags.
     *
     * @param flags the flags to add or remove
     * @param value true to add, false to remove
     */
    public void setInputFlags(EnumSet<InputFlag> flags, boolean value) {
        if (value) {
            inputFlags.addAll(flags);
        } else {
            inputFlags.removeAll(flags);
        }
    }

    /**
     * Adds or removes a single input flag.
     *
     * @param flag the flag to add or remove
     * @param value true to add, false to remove
     */
    public void setInputFlag(InputFlag flag, boolean value) {
        if (value) {
            inputFlags.add(flag);
        } else {
            inputFlags.remove(flag);
        }
    }

    //
    // Output flags
    //

    /**
     * Returns the set of output flags.
     *
     * @return the output flags
     */
    public EnumSet<OutputFlag> getOutputFlags() {
        return outputFlags;
    }

    /**
     * Sets the output flags, replacing any existing flags.
     *
     * @param flags the output flags to set
     */
    public void setOutputFlags(EnumSet<OutputFlag> flags) {
        outputFlags.clear();
        outputFlags.addAll(flags);
    }

    /**
     * Checks if a specific output flag is set.
     *
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public boolean getOutputFlag(OutputFlag flag) {
        return outputFlags.contains(flag);
    }

    /**
     * Adds or removes multiple output flags.
     *
     * @param flags the flags to add or remove
     * @param value true to add, false to remove
     */
    public void setOutputFlags(EnumSet<OutputFlag> flags, boolean value) {
        if (value) {
            outputFlags.addAll(flags);
        } else {
            outputFlags.removeAll(flags);
        }
    }

    /**
     * Adds or removes a single output flag.
     *
     * @param flag the flag to add or remove
     * @param value true to add, false to remove
     */
    public void setOutputFlag(OutputFlag flag, boolean value) {
        if (value) {
            outputFlags.add(flag);
        } else {
            outputFlags.remove(flag);
        }
    }

    //
    // Control flags
    //

    /**
     * Returns the set of control flags.
     *
     * @return the control flags
     */
    public EnumSet<ControlFlag> getControlFlags() {
        return controlFlags;
    }

    /**
     * Sets the control flags, replacing any existing flags.
     *
     * @param flags the control flags to set
     */
    public void setControlFlags(EnumSet<ControlFlag> flags) {
        controlFlags.clear();
        controlFlags.addAll(flags);
    }

    /**
     * Checks if a specific control flag is set.
     *
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public boolean getControlFlag(ControlFlag flag) {
        return controlFlags.contains(flag);
    }

    /**
     * Adds or removes multiple control flags.
     *
     * @param flags the flags to add or remove
     * @param value true to add, false to remove
     */
    public void setControlFlags(EnumSet<ControlFlag> flags, boolean value) {
        if (value) {
            controlFlags.addAll(flags);
        } else {
            controlFlags.removeAll(flags);
        }
    }

    /**
     * Adds or removes a single control flag.
     *
     * @param flag the flag to add or remove
     * @param value true to add, false to remove
     */
    public void setControlFlag(ControlFlag flag, boolean value) {
        if (value) {
            controlFlags.add(flag);
        } else {
            controlFlags.remove(flag);
        }
    }

    //
    // Local flags
    //

    /**
     * Returns the set of local flags.
     *
     * @return the local flags
     */
    public EnumSet<LocalFlag> getLocalFlags() {
        return localFlags;
    }

    /**
     * Sets the local flags, replacing any existing flags.
     *
     * @param flags the local flags to set
     */
    public void setLocalFlags(EnumSet<LocalFlag> flags) {
        localFlags.clear();
        localFlags.addAll(flags);
    }

    /**
     * Checks if a specific local flag is set.
     *
     * @param flag the flag to check
     * @return true if the flag is set
     */
    public boolean getLocalFlag(LocalFlag flag) {
        return localFlags.contains(flag);
    }

    /**
     * Adds or removes multiple local flags.
     *
     * @param flags the flags to add or remove
     * @param value true to add, false to remove
     */
    public void setLocalFlags(EnumSet<LocalFlag> flags, boolean value) {
        if (value) {
            localFlags.addAll(flags);
        } else {
            localFlags.removeAll(flags);
        }
    }

    /**
     * Adds or removes a single local flag.
     *
     * @param flag the flag to add or remove
     * @param value true to add, false to remove
     */
    public void setLocalFlag(LocalFlag flag, boolean value) {
        if (value) {
            localFlags.add(flag);
        } else {
            localFlags.remove(flag);
        }
    }

    //
    // Control chars
    //

    /**
     * Returns the map of control characters.
     *
     * @return the control characters map
     */
    public EnumMap<ControlChar, Integer> getControlChars() {
        return controlChars;
    }

    /**
     * Sets the control characters, replacing any existing mappings.
     *
     * @param chars the control characters map to set
     */
    public void setControlChars(EnumMap<ControlChar, Integer> chars) {
        controlChars.clear();
        controlChars.putAll(chars);
    }

    /**
     * Returns the value of a specific control character.
     *
     * @param c the control character
     * @return the character value, or -1 if not set
     */
    public int getControlChar(ControlChar c) {
        return controlChars.getOrDefault(c, -1);
    }

    /**
     * Sets the value of a control character.
     *
     * @param c the control character
     * @param value the character value
     */
    public void setControlChar(ControlChar c, int value) {
        controlChars.put(c, value);
    }

    //
    // Miscellaneous methods
    //

    /**
     * Copies all attributes from another Attributes instance.
     *
     * @param attributes the attributes to copy from
     */
    public void copy(Attributes attributes) {
        setControlFlags(attributes.getControlFlags());
        setInputFlags(attributes.getInputFlags());
        setLocalFlags(attributes.getLocalFlags());
        setOutputFlags(attributes.getOutputFlags());
        setControlChars(attributes.getControlChars());
    }
}
