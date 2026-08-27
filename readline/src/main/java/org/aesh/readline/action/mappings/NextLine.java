/*
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
package org.aesh.readline.action.mappings;

import org.aesh.readline.Buffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Context-aware Down arrow action for multi-line editing.
 * <p>
 * When the buffer contains multiple logical lines (multi-line mode):
 * <ul>
 * <li>If the cursor is on a line that is not the last, moves to the
 * same column on the next line (clamped to the line length).</li>
 * <li>If the cursor is on the last line, delegates to {@link NextHistory}.</li>
 * </ul>
 * <p>
 * In single-line mode, always delegates to {@link NextHistory}.
 */
public class NextLine implements Action {

    @Override
    public String name() {
        return "next-line";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        Buffer buffer = inputProcessor.buffer().buffer();
        int cursor = buffer.cursor();
        int lastLineIndex = buffer.getLogicalLineCount() - 1;

        if (!buffer.isMultiLine()) {
            // Single-line — navigate history
            new NextHistory().accept(inputProcessor);
            return;
        }
        if (buffer.getLogicalLineIndex(cursor) >= lastLineIndex) {
            // Already on last line in multi-line mode — do nothing
            return;
        }

        // Move to same column on next line (clamped to line length)
        int currentCol = buffer.getCursorColumnOnLine(cursor);
        int currentLineEnd = buffer.getLogicalLineEnd(cursor);

        // Next line starts after the \n
        int nextLineStart = currentLineEnd + 1;
        int nextLineEnd = buffer.getLogicalLineEnd(nextLineStart);
        int nextLineLength = nextLineEnd - nextLineStart;

        int targetCol = Math.min(currentCol, nextLineLength);
        int targetPos = nextLineStart + targetCol;
        int move = targetPos - cursor;

        inputProcessor.buffer().moveCursor(move);
    }
}
