/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal.ssh;

import java.util.Map;

import org.aesh.terminal.Attributes;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.server.Environment;

/**
 * Builder for creating terminal Attributes from an SSH environment.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class SSHAttributesBuilder {

    private static final Map<PtyMode, Attributes.ControlChar> CONTROL_CHARS = Map.ofEntries(
            Map.entry(PtyMode.VINTR, Attributes.ControlChar.VINTR),
            Map.entry(PtyMode.VQUIT, Attributes.ControlChar.VQUIT),
            Map.entry(PtyMode.VERASE, Attributes.ControlChar.VERASE),
            Map.entry(PtyMode.VKILL, Attributes.ControlChar.VKILL),
            Map.entry(PtyMode.VEOF, Attributes.ControlChar.VEOF),
            Map.entry(PtyMode.VEOL, Attributes.ControlChar.VEOL),
            Map.entry(PtyMode.VEOL2, Attributes.ControlChar.VEOL2),
            Map.entry(PtyMode.VSTART, Attributes.ControlChar.VSTART),
            Map.entry(PtyMode.VSTOP, Attributes.ControlChar.VSTOP),
            Map.entry(PtyMode.VSUSP, Attributes.ControlChar.VSUSP),
            Map.entry(PtyMode.VDSUSP, Attributes.ControlChar.VDSUSP),
            Map.entry(PtyMode.VREPRINT, Attributes.ControlChar.VREPRINT),
            Map.entry(PtyMode.VWERASE, Attributes.ControlChar.VWERASE),
            Map.entry(PtyMode.VLNEXT, Attributes.ControlChar.VLNEXT),
            Map.entry(PtyMode.VSTATUS, Attributes.ControlChar.VSTATUS),
            Map.entry(PtyMode.VDISCARD, Attributes.ControlChar.VDISCARD));

    private static final Map<PtyMode, Attributes.LocalFlag> LOCAL_FLAGS = Map.of(
            PtyMode.ECHO, Attributes.LocalFlag.ECHO,
            PtyMode.ICANON, Attributes.LocalFlag.ICANON,
            PtyMode.ISIG, Attributes.LocalFlag.ISIG);

    private static final Map<PtyMode, Attributes.InputFlag> INPUT_FLAGS = Map.of(
            PtyMode.ICRNL, Attributes.InputFlag.ICRNL,
            PtyMode.INLCR, Attributes.InputFlag.INLCR,
            PtyMode.IGNCR, Attributes.InputFlag.IGNCR);

    private static final Map<PtyMode, Attributes.OutputFlag> OUTPUT_FLAGS = Map.of(
            PtyMode.OCRNL, Attributes.OutputFlag.OCRNL,
            PtyMode.ONLCR, Attributes.OutputFlag.ONLCR,
            PtyMode.ONLRET, Attributes.OutputFlag.ONLRET,
            PtyMode.OPOST, Attributes.OutputFlag.OPOST);

    private Environment environment;

    private SSHAttributesBuilder() {
    }

    /**
     * Creates a new SSHAttributesBuilder instance.
     *
     * @return a new builder instance
     */
    public static SSHAttributesBuilder builder() {
        return new SSHAttributesBuilder();
    }

    /**
     * Sets the SSH environment to read PTY modes from.
     *
     * @param environment the SSH environment
     * @return this builder for method chaining
     */
    public SSHAttributesBuilder environment(Environment environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Builds the terminal Attributes from the configured SSH environment.
     * Maps SSH PTY modes to terminal control characters and flags.
     *
     * @return the configured Attributes instance
     */
    public Attributes build() {
        Attributes attr = new Attributes();
        for (Map.Entry<PtyMode, Integer> e : environment.getPtyModes().entrySet()) {
            Attributes.ControlChar cc = CONTROL_CHARS.get(e.getKey());
            if (cc != null) {
                attr.setControlChar(cc, e.getValue());
                continue;
            }
            Attributes.LocalFlag lf = LOCAL_FLAGS.get(e.getKey());
            if (lf != null) {
                attr.setLocalFlag(lf, e.getValue() != 0);
                continue;
            }
            Attributes.InputFlag inf = INPUT_FLAGS.get(e.getKey());
            if (inf != null) {
                attr.setInputFlag(inf, e.getValue() != 0);
                continue;
            }
            Attributes.OutputFlag of = OUTPUT_FLAGS.get(e.getKey());
            if (of != null) {
                attr.setOutputFlag(of, e.getValue() != 0);
            }
        }

        return attr;
    }

}
