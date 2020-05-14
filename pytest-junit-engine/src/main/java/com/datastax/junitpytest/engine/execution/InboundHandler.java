/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.junitpytest.engine.execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

final class InboundHandler
{
    final byte[] iobuf = new byte[8192];

    enum State
    {
        EXPECT_START(true),
        EXPECT_BLOCK_START(true),
        READING_BLOCK(false),
        POST_BLOCK_EOL(true),
        EXPECT_END(true);

        final boolean line;

        State(boolean line) {this.line = line;}
    }

    private State state = State.EXPECT_START;

    private final ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

    private int remainingBlocks;
    private String currentBlockName;
    private int currentBlockRemaining;

    private Message current;

    Message readMessage(InputStream input) throws IOException
    {
        if (state.line)
        {
            // EXPECT_START
            // EXPECT_END
            // EXPECT_HEADER
            // EXPECT_BLOCK_LENGTH
            while (input.available() > 0)
            {
                int c = input.read();
                if (c == -1)
                    return null;

                if (c == 10)
                {
                    String ln = new String(byteBuffer.toByteArray(), StandardCharsets.UTF_8);
                    byteBuffer.reset();
                    Message message = handleLine(ln);
                    if (message != null)
                        return message;
                    if (!state.line)
                        break;
                }
                else
                {
                    byteBuffer.write(c);
                }
            }
        }
        if (!state.line)
        {
            // READING_BLOCK
            int av = input.available();
            if (av > 0)
            {
                int toread = Math.min(currentBlockRemaining, Math.min(av, iobuf.length));
                int rd = input.read(iobuf, 0, toread);
                byteBuffer.write(iobuf, 0, rd);
                currentBlockRemaining -= rd;
            }
            if (currentBlockRemaining == 0)
            {
                current.blockMap.put(currentBlockName, new String(byteBuffer.toByteArray(), StandardCharsets.UTF_8));
                byteBuffer.reset();
                remainingBlocks--;
                state = State.POST_BLOCK_EOL;
            }
        }
        return null;
    }

    private Message handleLine(String line)
    {
        switch (state)
        {
            case EXPECT_START:
                if (!line.startsWith("*** START/"))
                    throw new IllegalStateException("Expected '*** START/...', but got '" + line + "'");
                StringTokenizer lineTokens = new StringTokenizer(line, "/");
                lineTokens.nextToken();
                current = Message.create(lineTokens.nextToken());
                remainingBlocks = Integer.parseInt(lineTokens.nextToken());
                state = remainingBlocks > 0 ? State.EXPECT_BLOCK_START : State.EXPECT_END;
                break;
            case EXPECT_BLOCK_START:
                int i = line.indexOf(": ");
                currentBlockName = line.substring(0, i);
                currentBlockRemaining = Integer.parseInt(line.substring(i + 2));
                state = State.READING_BLOCK;
                break;
            case POST_BLOCK_EOL:
                if (!line.isEmpty())
                    throw new IllegalStateException("Expected empty line, but got '" + line + "'");
                state = remainingBlocks > 0 ? State.EXPECT_BLOCK_START : State.EXPECT_END;
                break;
            case EXPECT_END:
                if (!"*** END".equals(line))
                    throw new IllegalStateException("Expected '*** END', but got '" + line + "'");
                state = State.EXPECT_START;
                Message r = current;
                current = null;
                return r;
        }
        return null;
    }
}
