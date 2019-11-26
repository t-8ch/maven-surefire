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

import org.apache.maven.surefire.shared.utils.cli.StreamConsumer;

import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 *
 */
public final class LineConsumerThread extends Thread
{
    private final Scanner scanner;
    private final StreamConsumer consumer;
    private final CountDownLatch endOfStreamsCountdown;
    private volatile boolean disabled;

    public LineConsumerThread( String threadName,
                               ReadableByteChannel channel, StreamConsumer consumer,
                               CountDownLatch endOfStreamsCountdown )
    {
        this( threadName, channel, consumer, Charset.defaultCharset(), endOfStreamsCountdown );
    }

    public LineConsumerThread( String threadName,
                               ReadableByteChannel channel, StreamConsumer consumer, Charset encoding,
                               CountDownLatch endOfStreamsCountdown )
    {
        setName( threadName );
        setDaemon( true );
        scanner = new Scanner( channel, encoding.name() );
        this.consumer = consumer;
        this.endOfStreamsCountdown = endOfStreamsCountdown;
    }

    @Override
    public void run()
    {
        try ( Scanner stream = scanner )
        {
            boolean isError = false;
            while ( stream.hasNextLine() )
            {
                try
                {
                    String line = stream.nextLine();
                    isError |= stream.ioException() != null;
                    if ( !isError && !disabled )
                    {
                        consumer.consumeLine( line );
                    }
                }
                catch ( IllegalStateException e )
                {
                    isError = true;
                }
            }
        }
        catch ( IllegalStateException e )
        {
            // not needed
        }
        finally
        {
            endOfStreamsCountdown.countDown();
        }
    }

    public void disable()
    {
        disabled = true;
    }
}
