//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.websocket.common.message;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.Executor;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.common.AbstractMessageSink;
import org.eclipse.jetty.websocket.core.Frame;

public class PartialBinaryMessageSink extends AbstractMessageSink
{
    public PartialBinaryMessageSink(Executor executor, MethodHandle methodHandle)
    {
        super(executor, methodHandle);
    }

    @Override
    public void accept(Frame frame, Callback callback)
    {
        try
        {
            methodHandle.invoke(frame.getPayload(), frame.isFin());
            callback.succeeded();
        }
        catch (Throwable t)
        {
            callback.failed(t);
        }
    }
}
