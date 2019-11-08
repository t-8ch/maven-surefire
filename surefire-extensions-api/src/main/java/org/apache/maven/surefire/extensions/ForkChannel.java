package org.apache.maven.surefire.extensions;

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

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;

/**
 * The constructor prepares I/O or throws {@link IOException}. Open channel can be closed and closes all streams.
 * <br>
 * The forked JVM uses the {@link #createExecutableCommandline() connection string}.
 * The executable CLI {@link #createExecutableCommandline()} is using the streams. This method and constructor should
 * not be blocked while establishing the connection.
 */
public interface ForkChannel extends Closeable
{
    String getForkNodeConnectionString();

    @Nonnull
    ExecutableCommandline createExecutableCommandline() throws IOException;

    @Override
    void close() throws IOException;
}
