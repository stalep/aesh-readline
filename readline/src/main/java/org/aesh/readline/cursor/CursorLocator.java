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
package org.aesh.readline.cursor;

import org.aesh.readline.Buffer;

/**
 * Map a command character index onto a cursor COL/ROW.
 * <p>
 * With the unified buffer model, line boundaries are computed on-demand
 * from {@code \n} characters in the buffer rather than being registered
 * externally via {@link #addLine(int, int)}.
 *
 * @author jdenise@redhat.com
 */
public class CursorLocator {

    private boolean invalidatedLines;

    private final Buffer buffer;

    /**
     * Creates a new cursor locator for the specified buffer.
     *
     * @param buffer the buffer to track cursor positions for
     */
    public CursorLocator(Buffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Adds a line with the specified size and prompt size to the locator.
     * <p>
     * With the unified buffer model, this is a no-op — line boundaries
     * are computed from the buffer content on demand.
     *
     * @param size the size of the line content
     * @param promptSize the size of the prompt on this line
     */
    public void addLine(int size, int promptSize) {
        // No-op: unified buffer computes line boundaries from \n positions
    }

    /**
     * Checks if the cursor location tracking has been invalidated.
     *
     * @return true if the location is invalidated, false otherwise
     */
    public boolean isLocationInvalidated() {
        return invalidatedLines;
    }

    /**
     * Marks the cursor location as invalidated. This typically happens
     * when the terminal state changes in a way that makes the stored
     * line information unreliable.
     */
    public void invalidateCursorLocation() {
        invalidatedLines = true;
    }

    /**
     * The core logic of the locator. Map a command index onto an absolute
     * COL/ROW cursor location by scanning the buffer for {@code \n} characters.
     *
     * @param index the character index in the buffer
     * @param width the terminal width
     * @return the cursor location corresponding to the index, or null if
     *         the location is invalidated or out of bounds
     */
    public CursorLocation locate(int index, int width) {
        if (isLocationInvalidated()) {
            return null;
        }
        if (width <= 0) {
            return new CursorLocation(0, index);
        }

        int row = 0;
        int logicalLine = 0;
        int lineStart = 0;
        int bufLen = buffer.length();

        // Iterate through logical lines (separated by \n)
        for (int i = 0; i <= bufLen; i++) {
            boolean isNewline = (i < bufLen && buffer.get(i) == '\n');
            boolean isEnd = (i == bufLen);

            if (isNewline || isEnd) {
                int lineLen = i - lineStart;
                int promptLen = buffer.getPromptLengthForLine(logicalLine);

                if (index >= lineStart && index <= i) {
                    // The target index is on this logical line
                    int posOnLine = index - lineStart;
                    int col = (posOnLine + promptLen) % width;
                    int wrappedRows = (posOnLine + promptLen) / width;
                    return new CursorLocation(row + wrappedRows, col);
                }

                // Account for this complete line's display rows
                row += Math.max(1, (lineLen + promptLen + width - 1) / width);
                logicalLine++;
                lineStart = i + 1;
            }
        }

        // Shouldn't reach here, but handle gracefully
        return null;
    }

    /**
     * Clears any cached state. With the unified buffer model this
     * only resets the invalidation flag.
     */
    public void clear() {
        invalidatedLines = false;
    }
}
