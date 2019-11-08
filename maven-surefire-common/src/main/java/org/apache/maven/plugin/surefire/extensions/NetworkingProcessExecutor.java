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
import org.apache.maven.surefire.extensions.CommandReader;
import org.apache.maven.surefire.extensions.EventHandler;
import org.apache.maven.surefire.extensions.ExecutableCommandline;
import org.apache.maven.surefire.extensions.StdErrStreamLine;
import org.apache.maven.surefire.extensions.StdOutStreamLine;

import javax.annotation.Nonnull;
import java.net.ServerSocket;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author <a href="mailto:tibordigana@apache.org">Tibor Digana (tibor17)</a>
 * @since 3.0.0-M4
 */
final class NetworkingProcessExecutor implements ExecutableCommandline
{
    private final ServerSocket ss;

    NetworkingProcessExecutor( ServerSocket ss )
    {
        this.ss = ss;
    }

    @Nonnull
    @Override
    public <T> Callable<Integer> executeCommandLineAsCallable( @Nonnull T cli,
                                                               @Nonnull CommandReader commands,
                                                               @Nonnull EventHandler events,
                                                               StdOutStreamLine stdOut,
                                                               StdErrStreamLine stdErr,
                                                               @Nonnull Runnable runAfterProcessTermination )
            throws Exception
    {
        /*
        Call in Threads:

        Socket s = ss.accept();

        for ( Scanner scanner = new Scanner( s.getInputStream(), "ASCII" ); scanner.hasNextLine(); )
        {
            events.handleEvent( scanner.nextLine() );
        }

        Command cmd = commands.readNextCommand();
        if ( cmd != null )
        {
            MasterProcessCommand cmdType = cmd.getCommandType();
            s.getOutputStream()
                    .write( cmdType.hasDataType() ? cmdType.encode( cmd.getData() ) : cmdType.encode() );
        }*/

        return CommandLineUtils.executeCommandLineAsCallable( (Commandline) cli, null,
                new StdOutAdapter( stdOut ), new StdErrAdapter( stdErr ), 0, runAfterProcessTermination, US_ASCII );
    }
}
