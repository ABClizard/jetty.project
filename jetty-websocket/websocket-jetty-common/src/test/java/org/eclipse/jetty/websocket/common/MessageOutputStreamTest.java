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

package org.eclipse.jetty.websocket.common;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.common.message.MessageOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

public class MessageOutputStreamTest
{
    private static final Logger LOG = Log.getLogger(MessageOutputStreamTest.class);
    private static final int OUTPUT_BUFFER_SIZE = 4096;

    public TestableLeakTrackingBufferPool bufferPool = new TestableLeakTrackingBufferPool("Test");

    @AfterEach
    public void afterEach()
    {
        bufferPool.assertNoLeaks();
    }

    private OutgoingMessageCapture sessionCapture;

    @BeforeEach
    public void setupTest() throws Exception
    {
        sessionCapture = new OutgoingMessageCapture();
    }

    @Test
    public void testMultipleWrites() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(sessionCapture, OUTPUT_BUFFER_SIZE, bufferPool))
        {
            stream.write("Hello".getBytes("UTF-8"));
            stream.write(" ".getBytes("UTF-8"));
            stream.write("World".getBytes("UTF-8"));
        }

        assertThat("Socket.binaryMessages.size", sessionCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = sessionCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, is("Hello World"));
    }

    @Test
    public void testSingleWrite() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(sessionCapture, OUTPUT_BUFFER_SIZE, bufferPool))
        {
            stream.write("Hello World".getBytes("UTF-8"));
        }

        assertThat("Socket.binaryMessages.size", sessionCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = sessionCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, is("Hello World"));
    }

    @Test
    public void testWriteLarge_RequiringMultipleBuffers() throws Exception
    {
        int bufsize = (int)(OUTPUT_BUFFER_SIZE * 2.5);
        byte[] buf = new byte[bufsize];
        LOG.debug("Buffer sizes: max:{}, test:{}", OUTPUT_BUFFER_SIZE, bufsize);
        Arrays.fill(buf, (byte)'x');
        buf[bufsize - 1] = (byte)'o'; // mark last entry for debugging

        try (MessageOutputStream stream = new MessageOutputStream(sessionCapture, OUTPUT_BUFFER_SIZE, bufferPool))
        {
            stream.write(buf);
        }

        assertThat("Socket.binaryMessages.size", sessionCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = sessionCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, endsWith("xxxxxo"));
    }
}
