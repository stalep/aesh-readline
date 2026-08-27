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
 * Context-aware Up arrow action for multi-line editing.
 * <p>
 * When the buffer contains multiple logical lines (multi-line mode):
 * <ul>
 * <li>If the cursor is on line N &gt; 0, moves to the same column
 * on line N-1 (clamped to the line length).</li>
 * <li>If the cursor is on line 0, delegates to {@link PrevHistory}.</li>
 * </ul>
 * <p>
 * In single-line mode, always delegates to {@link PrevHistory}.
 */
public class PrevLine implements Action {

    @Override
    public String name() {
        return "previous-line";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        Buffer buffer = inputProcessor.buffer().buffer();

        if (!buffer.isMultiLine() || buffer.getLogicalLineIndex(buffer.cursor()) == 0) {
            // Single-line or cursor on first line — navigate history
            new PrevHistory().accept(inputProcessor);
            return;
        }

        // Move to same column on previous line (clamped to line length)
        int cursor = buffer.cursor();
        int currentCol = buffer.getCursorColumnOnLine(cursor);
        int currentLineStart = buffer.getLogicalLineStart(cursor);

        // Previous line ends at currentLineStart - 1 (the \n)
        int prevLineEnd = currentLineStart - 1;
        int prevLineStart = buffer.getLogicalLineStart(prevLineEnd);
        int prevLineLength = prevLineEnd - prevLineStart;

        int targetCol = Math.min(currentCol, prevLineLength);
        int targetPos = prevLineStart + targetCol;
        int move = targetPos - cursor;

        inputProcessor.buffer().moveCursor(move);
    }
}
