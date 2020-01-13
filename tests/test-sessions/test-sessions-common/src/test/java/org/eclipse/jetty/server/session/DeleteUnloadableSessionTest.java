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

package org.eclipse.jetty.server.session;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * DeleteUnloadableSessionTest
 */
public class DeleteUnloadableSessionTest
{

    /**
     * DelSessionDataStore
     */
    public static class DelSessionDataStore extends AbstractSessionDataStore
    {
        int count = 0;

        Object o = new Object();

        String unloadableId = null;

        @Override
        public boolean isPassivating()
        {
            return true;
        }

        @Override
        public boolean exists(String id) throws Exception
        {
            return o != null;
        }

        @Override
        public SessionData doLoad(String id) throws Exception
        {
            unloadableId = id;
            throw new UnreadableSessionDataException(id, null, new Exception("fake"));
        }

        @Override
        public boolean delete(String id) throws Exception
        {
            if (id.equals(unloadableId))
            {
                o = null;
                return true;
            }
            return false;
        }

        @Override
        public void doStore(String id, SessionData data, long lastSaveTime) throws Exception
        {
            //pretend it was saved
        }

        @Override
        public Set<String> doGetExpired(Set<String> candidates)
        {
            return null;
        }
    }

    public static class DelSessionDataStoreFactory extends AbstractSessionDataStoreFactory
    {

        /**
         * @see org.eclipse.jetty.server.session.SessionDataStoreFactory#getSessionDataStore(org.eclipse.jetty.server.session.SessionHandler)
         */
        @Override
        public SessionDataStore getSessionDataStore(SessionHandler handler) throws Exception
        {
            return new DelSessionDataStore();
        }
    }

    public static class TestServlet extends HttpServlet
    {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException
        {
            String action = request.getParameter("action");

            if ("test".equals(action))
            {
                HttpSession session = request.getSession(false);
                assertNull(session);
            }
        }
    }

    /**
     * Test that session data that can't be loaded results in a null Session object
     */
    @Test
    public void testDeleteUnloadableSession() throws Exception
    {
        String contextPath = "";
        String servletMapping = "/server";
        int inactivePeriod = -1;
        int scavengePeriod = 100;

        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        cacheFactory.setRemoveUnloadableSessions(true);
        SessionDataStoreFactory storeFactory = new DelSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(scavengePeriod);

        TestServer server = new TestServer(0, inactivePeriod, scavengePeriod, cacheFactory, storeFactory);
        ServletContextHandler context = server.addContext(contextPath);

        TestHttpChannelCompleteListener scopeListener = new TestHttpChannelCompleteListener();
        server.getServerConnector().addBean(scopeListener);

        TestServlet servlet = new TestServlet();
        ServletHolder holder = new ServletHolder(servlet);
        context.addServlet(holder, servletMapping);

        try (StacklessLogging stackless = new StacklessLogging(Log.getLogger("org.eclipse.jetty.server.session")))
        {
            server.start();
            int port = server.getPort();
            HttpClient client = new HttpClient();
            client.start();
            try
            {
                CountDownLatch latch = new CountDownLatch(1);
                scopeListener.setExitSynchronizer(latch);
                String sessionCookie = "JSESSIONID=w0rm3zxpa6h1zg1mevtv76b3te00.w0;$Path=/";
                Request request = client.newRequest("http://localhost:" + port + contextPath + servletMapping + "?action=test");
                request.header("Cookie", sessionCookie);
                ContentResponse response = request.send();
                assertEquals(HttpServletResponse.SC_OK, response.getStatus());

                //ensure request fully finished handlers
                latch.await(5, TimeUnit.SECONDS);

                assertFalse(context.getSessionHandler().getSessionCache().getSessionDataStore().exists(TestServer.extractSessionId(sessionCookie)));
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }
}
