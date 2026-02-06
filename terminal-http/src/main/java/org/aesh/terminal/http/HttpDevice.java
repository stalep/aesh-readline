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
package org.aesh.terminal.http;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.InfoCmp;

/**
 * Device implementation for HTTP-based terminal connections.
 * <p>
 * Supports dynamic terminal type updates based on client capability reporting.
 * When a web client connects, it can report its terminal type and capabilities,
 * which this device will use to load the appropriate terminfo data.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class HttpDevice extends BaseDevice {

    /** Default terminal type for web-based terminals. */
    public static final String DEFAULT_TYPE = "xterm-256color";

    private String type;
    private final Set<Capability> bools;
    private final Map<Capability, Integer> ints;
    private final Map<Capability, String> strings;

    // Client-reported capabilities
    private String colorDepth;
    private List<String> features;
    private String userAgent;

    /**
     * Creates a new HTTP device with the default terminal type (xterm-256color).
     */
    public HttpDevice() {
        this(DEFAULT_TYPE);
    }

    /**
     * Creates a new HTTP device with the specified terminal type.
     *
     * @param type the terminal type (e.g., "vt100", "xterm-256color")
     */
    public HttpDevice(String type) {
        this.type = type;
        bools = new HashSet<>();
        ints = new HashMap<>();
        strings = new HashMap<>();
        loadTerminfo(type);
    }

    /**
     * Loads terminfo data for the specified terminal type.
     *
     * @param termType the terminal type to load
     */
    private void loadTerminfo(String termType) {
        bools.clear();
        ints.clear();
        strings.clear();
        String data = InfoCmp.getDefaultInfoCmp(termType);
        if (data != null) {
            InfoCmp.parseInfoCmp(data, bools, ints, strings);
        }
    }

    /**
     * Updates the terminal type and reloads terminfo data.
     * <p>
     * This is typically called when the client reports its capabilities.
     *
     * @param type the new terminal type
     */
    public void setType(String type) {
        if (type != null && !type.equals(this.type)) {
            this.type = type;
            loadTerminfo(type);
        }
    }

    /**
     * Sets the client-reported color depth.
     *
     * @param colorDepth the color depth (e.g., "TRUE_COLOR", "256", "16")
     */
    public void setReportedColorDepth(String colorDepth) {
        this.colorDepth = colorDepth;
    }

    /**
     * Returns the client-reported color depth as a string.
     *
     * @return the color depth, or null if not reported
     */
    public String getReportedColorDepth() {
        return colorDepth;
    }

    /**
     * Sets the client-reported features.
     *
     * @param features list of feature names (e.g., "UNICODE", "CLIPBOARD")
     */
    public void setFeatures(List<String> features) {
        this.features = features;
    }

    /**
     * Returns the client-reported features.
     *
     * @return list of feature names, or null if not reported
     */
    public List<String> getFeatures() {
        return features;
    }

    /**
     * Sets the client's user agent string.
     *
     * @param userAgent the user agent string
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Returns the client's user agent string.
     *
     * @return the user agent, or null if not reported
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Checks if the client reported a specific feature.
     *
     * @param feature the feature to check (e.g., "UNICODE", "CLIPBOARD")
     * @return true if the feature was reported
     */
    public boolean hasFeature(String feature) {
        return features != null && features.contains(feature);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }
}
