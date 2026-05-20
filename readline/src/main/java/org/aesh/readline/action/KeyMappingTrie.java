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
package org.aesh.readline.action;

import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.KeyAction;

/**
 * A trie-based data structure for efficient key sequence matching.
 * Provides O(m) lookup complexity where m is the length of the input sequence,
 * compared to O(n*m) for linear search where n is the number of mappings.
 * <p>
 * The single-byte fast path uses pre-computed arrays for O(1) lookup.
 * HotSpot's escape analysis eliminates the MatchResult allocation on
 * the hot path, making the object-returning API zero-allocation in practice.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class KeyMappingTrie {

    /** Constructor. */
    public KeyMappingTrie() {
    }

    private final TrieNode root = new TrieNode();

    // Pre-computed single-byte lookups for fast path
    private final KeyAction[] singleByteLookup = new KeyAction[256];
    private final boolean[] singleByteHasPrefix = new boolean[256];

    /**
     * Result of a trie match operation.
     */
    public static class MatchResult {
        /** The matched action (longest match found), or null if no match. */
        public final KeyAction action;
        /** True if the buffer is a prefix of a longer sequence in the trie. */
        public final boolean hasPrefix;

        MatchResult(KeyAction action, boolean hasPrefix) {
            this.action = action;
            this.hasPrefix = hasPrefix;
        }
    }

    /**
     * Internal trie node structure.
     */
    private static class TrieNode {
        // Sparse array for children with code points 0-255
        private final TrieNode[] children = new TrieNode[256];
        // Map for extended code points > 255
        private Map<Integer, TrieNode> extendedChildren;
        // The KeyAction at this node (if this is a terminal node)
        private KeyAction action;
        // Tracked child count to avoid scanning the 256-element array
        private int childCount;
    }

    /**
     * Builds the trie from the given key mappings.
     * This clears any existing mappings and rebuilds the trie.
     *
     * @param mappings the array of KeyAction mappings to index
     */
    public void build(KeyAction[] mappings) {
        clear();
        for (KeyAction mapping : mappings) {
            if (mapping != null && mapping.length() > 0) {
                insert(mapping);
            }
        }
        computeSingleByteLookups();
    }

    /**
     * Clears all mappings from the trie.
     */
    public void clear() {
        for (int i = 0; i < 256; i++) {
            root.children[i] = null;
            singleByteLookup[i] = null;
            singleByteHasPrefix[i] = false;
        }
        root.extendedChildren = null;
        root.action = null;
        root.childCount = 0;
    }

    /**
     * Inserts a KeyAction into the trie.
     */
    private void insert(KeyAction action) {
        TrieNode current = root;
        for (int i = 0; i < action.length(); i++) {
            int codePoint = action.getCodePointAt(i);
            TrieNode child = getChild(current, codePoint);
            if (child == null) {
                child = new TrieNode();
                setChild(current, codePoint, child);
            }
            current = child;
        }
        // Prefer longer matches
        if (current.action == null || current.action.length() <= action.length()) {
            current.action = action;
        }
    }

    private TrieNode getChild(TrieNode node, int codePoint) {
        if (codePoint >= 0 && codePoint < 256) {
            return node.children[codePoint];
        } else if (node.extendedChildren != null) {
            return node.extendedChildren.get(codePoint);
        }
        return null;
    }

    private void setChild(TrieNode node, int codePoint, TrieNode child) {
        if (codePoint >= 0 && codePoint < 256) {
            if (node.children[codePoint] == null) {
                node.childCount++;
            }
            node.children[codePoint] = child;
        } else {
            if (node.extendedChildren == null) {
                node.extendedChildren = new HashMap<>();
            }
            if (!node.extendedChildren.containsKey(codePoint)) {
                node.childCount++;
            }
            node.extendedChildren.put(codePoint, child);
        }
    }

    private void computeSingleByteLookups() {
        for (int i = 0; i < 256; i++) {
            TrieNode child = root.children[i];
            if (child != null) {
                singleByteLookup[i] = child.action;
                singleByteHasPrefix[i] = child.childCount > 0;
            }
        }
    }

    /**
     * Fast path for single-byte matching.
     *
     * @param code the single byte code point
     * @return the match result
     */
    public MatchResult matchSingleByte(int code) {
        if (code >= 0 && code < 256) {
            return new MatchResult(singleByteLookup[code], singleByteHasPrefix[code]);
        }
        return match(new int[] { code });
    }

    /**
     * Matches the input buffer against the trie.
     * Returns the longest matching KeyAction and whether the buffer is a prefix.
     *
     * @param buffer the input code points to match
     * @return the match result
     */
    public MatchResult match(int[] buffer) {
        if (buffer == null || buffer.length == 0) {
            return new MatchResult(null, false);
        }
        return match(buffer, 0, buffer.length);
    }

    /**
     * Matches a range of the input buffer against the trie.
     *
     * @param buffer the input code points
     * @param offset the start offset in the buffer
     * @param length the number of code points to match
     * @return the match result
     */
    public MatchResult match(int[] buffer, int offset, int length) {
        if (buffer == null || length == 0) {
            return new MatchResult(null, false);
        }

        TrieNode current = root;
        KeyAction longestMatch = null;
        boolean hasPrefix = false;

        int end = offset + length;
        for (int i = offset; i < end; i++) {
            int codePoint = buffer[i];
            TrieNode child = getChild(current, codePoint);

            if (child == null) {
                break;
            }

            current = child;

            if (current.action != null) {
                longestMatch = current.action;
            }

            if (i == end - 1 && current.childCount > 0) {
                hasPrefix = true;
            }
        }

        return new MatchResult(longestMatch, hasPrefix);
    }
}
