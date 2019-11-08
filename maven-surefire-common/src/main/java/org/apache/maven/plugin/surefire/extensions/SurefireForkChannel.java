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
import java.net.ServerSocket;

/**
 *
 */
final class SurefireForkChannel implements ForkChannel
{
    private final ServerSocket ss;

    SurefireForkChannel() throws IOException
    {
        ss = new ServerSocket( 0 );
    }

    @Override
    public String getForkNodeConnectionString()
    {
        return "tcp://127.0.0.1:" + ss.getLocalPort();
    }

    @Nonnull
    @Override
    public ExecutableCommandline createExecutableCommandline() throws IOException
    {
        return new NetworkingProcessExecutor( ss );
    }

    @Override
    public void close() throws IOException
    {
        ss.close();
    }
}
