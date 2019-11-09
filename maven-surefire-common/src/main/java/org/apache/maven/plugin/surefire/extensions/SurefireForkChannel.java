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

import org.apache.maven.surefire.extensions.ExecutableCommandline;
import org.apache.maven.surefire.extensions.ForkChannel;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.channels.AsynchronousChannelGroup.withThreadPool;
import static java.nio.channels.AsynchronousServerSocketChannel.open;
import static org.apache.maven.surefire.util.internal.DaemonThreadFactory.newDaemonThreadFactory;

/**
 *
 */
final class SurefireForkChannel implements ForkChannel
{
    private final ExecutorService executorService;
    private final AsynchronousServerSocketChannel server;
    private final int serverPort;

    SurefireForkChannel() throws IOException
    {
        executorService = Executors.newCachedThreadPool( newDaemonThreadFactory() );
        server = open( withThreadPool( executorService ) )
                .bind( new InetSocketAddress( 0 ) );
        serverPort = ( (InetSocketAddress) server.getLocalAddress() ).getPort();
    }

    @Override
    public String getForkNodeConnectionString()
    {
        return "tcp://127.0.0.1:" + serverPort;
    }

    @Nonnull
    @Override
    public ExecutableCommandline createExecutableCommandline()
    {
        return new NetworkingProcessExecutor( server, executorService );
    }

    @Override
    public void close() throws IOException
    {
        try
        {
            server.close();
        }
        finally
        {
            executorService.shutdownNow();
        }
    }
}
