package org.apache.maven.plugin.surefire.extensions;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.StreamConsumer;
import org.apache.maven.surefire.booter.Command;
import org.apache.maven.surefire.booter.MasterProcessCommand;
import org.apache.maven.surefire.extensions.CommandReader;
import org.apache.maven.surefire.extensions.EventHandler;
import org.apache.maven.surefire.extensions.ExecutableCommandline;
import org.apache.maven.surefire.extensions.StdErrStreamLine;
import org.apache.maven.surefire.extensions.StdOutStreamLine;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Commands which are sent from plugin to the forked jvm.
 * <br>
 * Events are received from the forked jvm.
 * <br>
 * <br>
 * magic number : opcode [: opcode specific data]*
 * <br>
 * or data encoded with Base64
 * <br>
 * magic number : opcode [: Base64(opcode specific data)]*
 *
 * The command and event must be finished by the character ':' and New Line.
 *
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 3.0.0-M4
 */
final class PipeProcessExecutor
        implements ExecutableCommandline
{
    @Override
    @Nonnull
    public <T> Callable<Integer> executeCommandLineAsCallable( @Nonnull T cli,
                                                               @Nonnull CommandReader commands,
                                                               @Nonnull EventHandler events,
                                                               StdOutStreamLine stdOut,
                                                               StdErrStreamLine stdErr,
                                                               @Nonnull Runnable runAfterProcessTermination )
            throws Exception
    {
        return CommandLineUtils.executeCommandLineAsCallable( (Commandline) cli, new CommandReaderAdapter( commands ),
                new EventHandlerAdapter( events ), new StdErrAdapter( stdErr ),
                0, runAfterProcessTermination, US_ASCII );
    }

    private static class EventHandlerAdapter implements StreamConsumer
    {
        private final EventHandler events;

        private EventHandlerAdapter( EventHandler events )
        {
            this.events = events;
        }

        @Override
        public void consumeLine( String line )
        {
            events.handleEvent( line );
        }
    }

    private static class CommandReaderAdapter extends InputStream
    {
        private final CommandReader commands;
        private byte[] currentBuffer;
        private int currentPos;
        private volatile boolean closed;

        CommandReaderAdapter( CommandReader commands )
        {
            this.commands = commands;
        }

        @Override
        public int read() throws IOException
        {
            if ( commands.isClosed() )
            {
                close();
            }

            if ( closed )
            {
                return -1;
            }

            if ( currentBuffer == null )
            {
                Command cmd = commands.readNextCommand();
                if ( cmd == null )
                {
                    currentPos = 0;
                    return -1;
                }
                MasterProcessCommand cmdType = cmd.getCommandType();
                currentBuffer = cmdType.hasDataType() ? cmdType.encode( cmd.getData() ) : cmdType.encode();
            }

            @SuppressWarnings( "checkstyle:magicnumber" )
            int b =  currentBuffer[currentPos++] & 0xff;
            if ( currentPos == currentBuffer.length )
            {
                currentBuffer = null;
                currentPos = 0;
            }
            return b;
        }

        @Override
        public void close()
        {
            closed = true;
        }
    }
}
