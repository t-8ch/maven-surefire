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
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.nio.ByteBuffer.wrap;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 3.0.0-M4
 */
final class NetworkingProcessExecutor implements ExecutableCommandline
{
    private final AsynchronousServerSocketChannel server;
    private final ExecutorService executorService;

    NetworkingProcessExecutor( AsynchronousServerSocketChannel server, ExecutorService executorService )
    {
        this.server = server;
        this.executorService = executorService;
    }

    @Nonnull
    @Override
    public <T> Callable<Integer> executeCommandLineAsCallable( @Nonnull T cli,
                                                               @Nonnull final CommandReader commands,
                                                               @Nonnull final EventHandler events,
                                                               StdOutStreamLine stdOut,
                                                               StdErrStreamLine stdErr,
                                                               @Nonnull Runnable runAfterProcessTermination )
            throws Exception
    {
        server.accept( null, new CompletionHandler<AsynchronousSocketChannel, Object>()
        {
            @Override
            public void completed( final AsynchronousSocketChannel client, Object attachment )
            {
                executorService.submit( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        InputStream is = toInputStream( client );
                        try
                        {
                            for ( Scanner scanner = new Scanner( is, "ASCII" ); scanner.hasNextLine(); )
                            {
                                if ( scanner.ioException() != null )
                                {
                                    break;
                                }
                                events.handleEvent( scanner.nextLine() );
                            }
                        }
                        catch ( IllegalStateException e )
                        {
                            // scanner and InputStream is closed
                            try
                            {
                                client.close();
                            }
                            catch ( IOException ex )
                            {
                                // couldn't close the client channel
                            }
                        }
                    }
                } );

                executorService.submit( new Runnable()
                {
                    @Override
                    @SuppressWarnings( "checkstyle:innerassignment" )
                    public void run()
                    {
                        try
                        {
                            for ( Command cmd; ( cmd = commands.readNextCommand() ) != null;  )
                            {
                                MasterProcessCommand cmdType = cmd.getCommandType();
                                byte[] b = cmdType.hasDataType() ? cmdType.encode( cmd.getData() ) : cmdType.encode();
                                ByteBuffer bb = wrap( b );
                                int writtenBytesTotal = 0;
                                do
                                {
                                    Future<Integer> writtenBytes = client.write( bb );
                                    int writtenCount = writtenBytes.get();
                                    writtenBytesTotal += writtenCount;
                                }
                                while ( writtenBytesTotal < bb.limit() );
                            }
                        }
                        catch ( Exception e )
                        {
                            // finished stream or error
                            try
                            {
                                client.close();
                            }
                            catch ( IOException ex )
                            {
                                // couldn't close the client channel
                            }
                        }
                    }
                } );
            }

            @Override
            public void failed( Throwable exc, Object attachment )
            {
                // write to dump file
                // close the server
            }
        } );

        return CommandLineUtils.executeCommandLineAsCallable( (Commandline) cli, null,
                new StdOutAdapter( stdOut ), new StdErrAdapter( stdErr ), 0, runAfterProcessTermination, US_ASCII );
    }

    private static InputStream toInputStream( final AsynchronousSocketChannel client )
    {
        return new InputStream()
        {
            private final ByteBuffer bb = ByteBuffer.allocate( 64 * 1024 );
            private boolean closed;

            @Override
            public int read() throws IOException
            {
                if ( closed )
                {
                    return -1;
                }

                try
                {
                    if ( !bb.hasRemaining() )
                    {
                        bb.clear();
                        if ( client.read( bb ).get() == 0 )
                        {
                            closed = true;
                            return -1;
                        }
                        bb.flip();
                    }
                    return bb.get();
                }
                catch ( InterruptedException e )
                {
                    closed = true;
                    return -1;
                }
                catch ( ExecutionException e )
                {
                    closed = true;
                    Throwable cause = e.getCause();
                    if ( cause instanceof IOException )
                    {
                        throw (IOException) cause;
                    }
                    else
                    {
                        return -1;
                    }
                }
            }

            @Override
            public void close() throws IOException
            {
                closed = true;
                super.close();
            }
        };
    }
}
