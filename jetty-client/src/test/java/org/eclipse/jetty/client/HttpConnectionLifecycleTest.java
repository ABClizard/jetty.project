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

package org.eclipse.jetty.client;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpConnectionLifecycleTest extends AbstractHttpClientServerTest
{
    @Override
    public HttpClient newHttpClient(HttpClientTransport transport)
    {
        HttpClient client = super.newHttpClient(transport);
        client.setStrictEventOrdering(false);
        return client;
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_SuccessfulRequest_ReturnsConnection(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        CountDownLatch headersLatch = new CountDownLatch(1);
        CountDownLatch successLatch = new CountDownLatch(3);
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        request.onRequestSuccess(r -> successLatch.countDown())
            .onResponseHeaders(response ->
            {
                assertEquals(0, idleConnections.size());
                assertEquals(1, activeConnections.size());
                headersLatch.countDown();
            })
            .send(new Response.Listener.Adapter()
            {
                @Override
                public void onSuccess(Response response)
                {
                    successLatch.countDown();
                }

                @Override
                public void onComplete(Result result)
                {
                    assertFalse(result.isFailed());
                    successLatch.countDown();
                }
            });

        assertTrue(headersLatch.await(30, TimeUnit.SECONDS));
        assertTrue(successLatch.await(30, TimeUnit.SECONDS));

        assertEquals(1, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_FailedRequest_RemovesConnection(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        CountDownLatch beginLatch = new CountDownLatch(1);
        CountDownLatch failureLatch = new CountDownLatch(2);
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        request.listener(new Request.Listener.Adapter()
        {
            @Override
            public void onBegin(Request request)
            {
                activeConnections.iterator().next().close();
                beginLatch.countDown();
            }

            @Override
            public void onFailure(Request request, Throwable failure)
            {
                failureLatch.countDown();
            }
        })
            .send(new Response.Listener.Adapter()
            {
                @Override
                public void onComplete(Result result)
                {
                    assertTrue(result.isFailed());
                    assertEquals(0, idleConnections.size());
                    assertEquals(0, activeConnections.size());
                    failureLatch.countDown();
                }
            });

        assertTrue(beginLatch.await(30, TimeUnit.SECONDS));
        assertTrue(failureLatch.await(30, TimeUnit.SECONDS));

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_BadRequest_RemovesConnection(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        CountDownLatch successLatch = new CountDownLatch(3);
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Queue<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        request.listener(new Request.Listener.Adapter()
        {
            @Override
            public void onBegin(Request request)
            {
                // Remove the host header, this will make the request invalid
                request.header(HttpHeader.HOST, null);
            }

            @Override
            public void onSuccess(Request request)
            {
                successLatch.countDown();
            }
        })
            .send(new Response.Listener.Adapter()
            {
                @Override
                public void onSuccess(Response response)
                {
                    assertEquals(400, response.getStatus());
                    // 400 response also come with a Connection: close,
                    // so the connection is closed and removed
                    successLatch.countDown();
                }

                @Override
                public void onComplete(Result result)
                {
                    assertFalse(result.isFailed());
                    successLatch.countDown();
                }
            });

        assertTrue(successLatch.await(30, TimeUnit.SECONDS));

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    @Tag("Slow")
    @DisabledIfSystemProperty(named = "env", matches = "ci") // TODO: SLOW, needs review
    public void test_BadRequest_WithSlowRequest_RemovesConnection(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        CountDownLatch successLatch = new CountDownLatch(3);
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        long delay = 1000;
        request.listener(new Request.Listener.Adapter()
        {
            @Override
            public void onBegin(Request request)
            {
                // Remove the host header, this will make the request invalid
                request.header(HttpHeader.HOST, null);
            }

            @Override
            public void onHeaders(Request request)
            {
                try
                {
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSuccess(Request request)
            {
                successLatch.countDown();
            }
        })
            .send(new Response.Listener.Adapter()
            {
                @Override
                public void onSuccess(Response response)
                {
                    assertEquals(400, response.getStatus());
                    // 400 response also come with a Connection: close,
                    // so the connection is closed and removed
                    successLatch.countDown();
                }

                @Override
                public void onComplete(Result result)
                {
                    assertFalse(result.isFailed());
                    successLatch.countDown();
                }
            });

        assertTrue(successLatch.await(delay * 30, TimeUnit.MILLISECONDS));

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_ConnectionFailure_RemovesConnection(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        server.stop();

        CountDownLatch failureLatch = new CountDownLatch(2);
        request.onRequestFailure((r, x) -> failureLatch.countDown())
            .send(result ->
            {
                assertTrue(result.isFailed());
                failureLatch.countDown();
            });

        assertTrue(failureLatch.await(30, TimeUnit.SECONDS));

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_ResponseWithConnectionCloseHeader_RemovesConnection(Scenario scenario) throws Exception
    {
        start(scenario, new AbstractHandler()
        {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            {
                response.setHeader("Connection", "close");
                baseRequest.setHandled(true);
            }
        });

        String host = "localhost";
        int port = connector.getLocalPort();
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        CountDownLatch latch = new CountDownLatch(1);
        request.send(new Response.Listener.Adapter()
        {
            @Override
            public void onComplete(Result result)
            {
                assertFalse(result.isFailed());
                assertEquals(0, idleConnections.size());
                assertEquals(0, activeConnections.size());
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void test_BigRequestContent_ResponseWithConnectionCloseHeader_RemovesConnection(Scenario scenario) throws Exception
    {
        try (StacklessLogging ignore = new StacklessLogging(HttpConnection.class))
        {
            start(scenario, new AbstractHandler()
            {
                @Override
                public void handle(String target, org.eclipse.jetty.server.Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                {
                    response.setHeader("Connection", "close");
                    baseRequest.setHandled(true);
                    // Don't read request content; this causes the server parser to be closed
                }
            });

            String host = "localhost";
            int port = connector.getLocalPort();
            Request request = client.newRequest(host, port).scheme(scenario.getScheme());
            HttpDestination destination = (HttpDestination)client.resolveDestination(request);
            DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

            Collection<Connection> idleConnections = connectionPool.getIdleConnections();
            assertEquals(0, idleConnections.size());

            Collection<Connection> activeConnections = connectionPool.getActiveConnections();
            assertEquals(0, activeConnections.size());

            Log.getLogger(HttpConnection.class).info("Expecting java.lang.IllegalStateException: HttpParser{s=CLOSED,...");

            CountDownLatch latch = new CountDownLatch(1);
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024 * 1024);
            Arrays.fill(buffer.array(), (byte)'x');
            request.content(new ByteBufferContentProvider(buffer))
                .send(new Response.Listener.Adapter()
                {
                    @Override
                    public void onComplete(Result result)
                    {
                        assertEquals(1, latch.getCount());
                        assertEquals(0, idleConnections.size());
                        assertEquals(0, activeConnections.size());
                        latch.countDown();
                    }
                });

            assertTrue(latch.await(30, TimeUnit.SECONDS));

            assertEquals(0, idleConnections.size());
            assertEquals(0, activeConnections.size());

            server.stop();
        }
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    @Tag("Slow")
    @DisabledIfSystemProperty(named = "env", matches = "ci") // TODO: SLOW, needs review
    public void test_IdleConnection_IsClosed_OnRemoteClose(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        ContentResponse response = request.timeout(30, TimeUnit.SECONDS).send();

        assertEquals(200, response.getStatus());

        connector.stop();

        // Give the connection some time to process the remote close
        TimeUnit.SECONDS.sleep(1);

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }

    @ParameterizedTest
    @ArgumentsSource(ScenarioProvider.class)
    public void testConnectionForHTTP10ResponseIsRemoved(Scenario scenario) throws Exception
    {
        start(scenario, new EmptyServerHandler());

        String host = "localhost";
        int port = connector.getLocalPort();
        Request request = client.newRequest(host, port).scheme(scenario.getScheme());
        HttpDestination destination = (HttpDestination)client.resolveDestination(request);
        DuplexConnectionPool connectionPool = (DuplexConnectionPool)destination.getConnectionPool();

        Collection<Connection> idleConnections = connectionPool.getIdleConnections();
        assertEquals(0, idleConnections.size());

        Collection<Connection> activeConnections = connectionPool.getActiveConnections();
        assertEquals(0, activeConnections.size());

        client.setStrictEventOrdering(false);
        ContentResponse response = request
            .onResponseBegin(response1 ->
            {
                // Simulate an HTTP 1.0 response has been received.
                ((HttpResponse)response1).version(HttpVersion.HTTP_1_0);
            })
            .send();

        assertEquals(200, response.getStatus());

        assertEquals(0, idleConnections.size());
        assertEquals(0, activeConnections.size());
    }
}
