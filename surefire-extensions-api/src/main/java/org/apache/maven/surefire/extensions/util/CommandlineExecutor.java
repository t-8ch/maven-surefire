package org.apache.maven.surefire.extensions.util;

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

import org.apache.maven.surefire.shared.utils.cli.CommandLineException;
import org.apache.maven.surefire.shared.utils.cli.Commandline;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.apache.maven.surefire.shared.utils.cli.ShutdownHookUtils.addShutDownHook;
import static org.apache.maven.surefire.shared.utils.cli.ShutdownHookUtils.removeShutdownHook;

/**
 * Programming model with this class:
 * <pre> {@code
 * try ( CommandlineExecutor exec = new CommandlineExecutor( cli, runAfterProcessTermination, endOfStreamsCountdown );
 *       CommandlineStreams streams = exec.execute() )
 * {
 *     // register exec in the shutdown hook to destroy pending process
 *
 *     // register streams in the shutdown hook to close all three streams
 *
 *     ReadableByteChannel stdOut = streams.getStdOutChannel();
 *     ReadableByteChannel stdErr = streams.getStdErrChannel();
 *     WritableByteChannel stdIn = streams.getStdInChannel();
 *     // lineConsumerThread = new LineConsumerThread( ..., stdErr, ..., endOfStreamsCountdown );
 *     // lineConsumerThread.start();
 *
 *     // stdIn.write( ... );
 *
 *     int exitCode = exec.awaitExit();
 *     // process exitCode
 * }
 * catch ( InterruptedException | IOException | CommandLineException e )
 * {
 *     // lineConsumerThread.disable();
 *     // handle the exceptions
 * }
 * } </pre>
 */
public class CommandlineExecutor implements Closeable
{
    private final Commandline cli;
    private final CountDownLatch endOfStreamsCountdown;
    private final Closeable closeAfterProcessTermination;
    private Process process;
    private volatile Thread shutdownHook;

    public CommandlineExecutor( Commandline cli,
                                Closeable closeAfterProcessTermination, CountDownLatch endOfStreamsCountdown )
    {
        this.cli = cli;
        this.closeAfterProcessTermination = closeAfterProcessTermination;
        this.endOfStreamsCountdown = endOfStreamsCountdown;
    }

    public CommandlineStreams execute() throws CommandLineException
    {
        process = cli.execute();
        shutdownHook = new ProcessHook( process );
        addShutDownHook( shutdownHook );
        return new CommandlineStreams( process );
    }

    public int awaitExit() throws InterruptedException, IOException
    {
        int exitCode = process.waitFor();
        closeAfterProcessTermination.close();
        endOfStreamsCountdown.await();
        return exitCode;
    }

    @Override
    public void close()
    {
        if ( shutdownHook != null )
        {
            shutdownHook.run();
            removeShutdownHook( shutdownHook );
            shutdownHook = null;
        }
    }

    private static class ProcessHook extends Thread
    {
        private final Process process;

        private ProcessHook( Process process )
        {
            super( "cli-shutdown-hook" );
            this.process = process;
            setContextClassLoader( null );
            setDaemon( true );
        }

        /** {@inheritDoc} */
        public void run()
        {
            process.destroy();
        }
    }

}
