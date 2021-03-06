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

package org.eclipse.jetty.websocket.core;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.core.server.Negotiation;

/**
 * Interface for local WebSocket Endpoint Frame handling.
 *
 * <p>
 * This is the receiver of Parsed Frames.  It is implemented by the Application (or Application API layer or Framework)
 * as the primary API to/from the Core websocket implementation.   The instance to be used for each websocket connection
 * is instantiated by the application, either:
 * </p>
 * <ul>
 * <li>On the server, the application layer must provide a {@link org.eclipse.jetty.websocket.core.server.WebSocketNegotiator} instance
 * to negotiate and accept websocket connections, which will return the FrameHandler instance to use from
 * {@link org.eclipse.jetty.websocket.core.server.WebSocketNegotiator#negotiate(Negotiation)}.</li>
 * <li>On the client, the application returns the FrameHandler instance to user from the {@link ClientUpgradeRequest}
 * instance that it passes to the {@link org.eclipse.jetty.websocket.core.client.WebSocketCoreClient#connect(ClientUpgradeRequest)} method/</li>
 * </ul>
 * <p>
 * Once instantiated the FrameHandler follows is used as follows:
 * </p>
 * <ul>
 * <li>The {@link #onOpen(CoreSession, Callback)} method is called when negotiation of the connection is completed. The passed {@link CoreSession} instance is used
 * to obtain information about the connection and to send frames</li>
 * <li>Every data and control frame received is passed to {@link #onFrame(Frame, Callback)}.</li>
 * <li>Received Control Frames that require a response (eg Ping, Close) are first passed to the {@link #onFrame(Frame, Callback)} to give the
 * Application an opportunity to send the response itself. If an appropriate response has not been sent when the callback passed is completed, then a
 * response will be generated.</li>
 * <li>If an error is detected or received, then {@link #onError(Throwable, Callback)} will be called to inform the application of the cause of the problem.
 * The connection will then be closed or aborted and the {@link #onClosed(CloseStatus, Callback)} method called.</li>
 * <li>The {@link #onClosed(CloseStatus, Callback)} method is always called once a websocket connection is terminated, either gracefully or not. The error code
 * will indicate the nature of the close.</li>
 * </ul>
 */
public interface FrameHandler extends IncomingFrames
{
    /**
     * Async notification that Connection is being opened.
     * <p>
     * FrameHandler can write during this call, but can not receive frames until the callback is succeeded.
     * </p>
     * <p>
     * If the FrameHandler succeeds the callback we transition to OPEN state and can now receive frames if
     * not demanding, or can now call {@link CoreSession#demand(long)} to receive frames if demanding.
     * If the FrameHandler fails the callback a close frame will be sent with {@link CloseStatus#SERVER_ERROR} and
     * the connection will be closed. <br>
     * </p>
     *
     * @param coreSession the session associated with this connection.
     * @param callback the callback to indicate success in processing (or failure)
     */
    void onOpen(CoreSession coreSession, Callback callback);

    /**
     * Receiver of all Frames.
     * This method will never be called in parallel for the same session and will be called
     * sequentially to satisfy all outstanding demand signaled by calls to
     * {@link CoreSession#demand(long)}.
     * Control and Data frames are passed to this method.
     * Close frames may be responded to by the handler, but if an appropriate close response is not
     * sent once the callback is succeeded, then a response close will be generated and sent.
     *
     * @param frame the raw frame
     * @param callback the callback to indicate success in processing frame (or failure)
     */
    void onFrame(Frame frame, Callback callback);

    /**
     * An error has occurred or been detected in websocket-core and being reported to FrameHandler.
     * A call to onError will be followed by a call to {@link #onClosed(CloseStatus, Callback)} giving the close status
     * derived from the error. This will not be called more than once, {@link #onClosed(CloseStatus, Callback)}
     * will be called on the callback completion.
     *
     * @param cause the reason for the error
     * @param callback the callback to indicate success in processing (or failure)
     */
    void onError(Throwable cause, Callback callback);

    /**
     * This is the Close Handshake Complete event.
     * <p>
     * The connection is now closed, no reading or writing is possible anymore.
     * Implementations of FrameHandler can cleanup their resources for this connection now.
     * This method will be called only once.
     * </p>
     *
     * @param closeStatus the close status received from remote, or in the case of abnormal closure from local.
     * @param callback the callback to indicate success in processing (or failure)
     */
    void onClosed(CloseStatus closeStatus, Callback callback);

    /**
     * Does the FrameHandler manage it's own demand?
     *
     * @return true iff the FrameHandler will manage its own flow control by calling {@link CoreSession#demand(long)} when it
     * is willing to receive new Frames.  Otherwise the demand will be managed by an automatic call to demand(1) after every
     * succeeded callback passed to {@link #onFrame(Frame, Callback)}.
     */
    default boolean isDemanding()
    {
        return false;
    }

    interface Configuration
    {

        /**
         * Get the Idle Timeout
         *
         * @return the idle timeout
         */
        Duration getIdleTimeout();

        /**
         * Get the Write Timeout
         *
         * @return the write timeout
         */
        Duration getWriteTimeout();

        /**
         * Set the Idle Timeout.
         *
         * @param timeout the timeout duration (timeout &lt;= 0 implies an infinite timeout)
         */
        void setIdleTimeout(Duration timeout);

        /**
         * Set the Write Timeout.
         *
         * @param timeout the timeout duration (timeout &lt;= 0 implies an infinite timeout)
         */
        void setWriteTimeout(Duration timeout);

        boolean isAutoFragment();

        void setAutoFragment(boolean autoFragment);

        long getMaxFrameSize();

        void setMaxFrameSize(long maxFrameSize);

        int getOutputBufferSize();

        void setOutputBufferSize(int outputBufferSize);

        int getInputBufferSize();

        void setInputBufferSize(int inputBufferSize);

        long getMaxBinaryMessageSize();

        void setMaxBinaryMessageSize(long maxSize);

        long getMaxTextMessageSize();

        void setMaxTextMessageSize(long maxSize);
    }

    /**
     * Represents the outgoing Frames.
     */
    interface CoreSession extends OutgoingFrames, Configuration
    {
        /**
         * The negotiated WebSocket Sub-Protocol for this session.
         *
         * @return the negotiated WebSocket Sub-Protocol for this session.
         */
        String getNegotiatedSubProtocol();

        /**
         * The negotiated WebSocket Extension Configurations for this session.
         *
         * @return the list of Negotiated Extension Configurations for this session.
         */
        List<ExtensionConfig> getNegotiatedExtensions();

        /**
         * The parameter map (from URI Query) for the active session.
         *
         * @return the immutable map of parameters
         */
        Map<String, List<String>> getParameterMap();

        /**
         * The active {@code Sec-WebSocket-Version} (protocol version) in use.
         *
         * @return the protocol version in use.
         */
        String getProtocolVersion();

        /**
         * The active connection's Request URI.
         * This is the URI of the upgrade request and is typically http: or https: rather than
         * the ws: or wss: scheme.
         *
         * @return the absolute URI (including Query string)
         */
        URI getRequestURI();

        /**
         * The active connection's Secure status indicator.
         *
         * @return true if connection is secure (similar in role to {@code HttpServletRequest.isSecure()})
         */
        boolean isSecure();

        /**
         * @return Client or Server behaviour
         */
        Behavior getBehavior();

        /**
         * @return The shared ByteBufferPool
         */
        ByteBufferPool getByteBufferPool();

        /**
         * The Local Socket Address for the connection
         * <p>
         * Do not assume that this will return a {@link InetSocketAddress} in all cases.
         * Use of various proxies, and even UnixSockets can result a SocketAddress being returned
         * without supporting {@link InetSocketAddress}
         * </p>
         *
         * @return the SocketAddress for the local connection, or null if not supported by Session
         */
        SocketAddress getLocalAddress();

        /**
         * The Remote Socket Address for the connection
         * <p>
         * Do not assume that this will return a {@link InetSocketAddress} in all cases.
         * Use of various proxies, and even UnixSockets can result a SocketAddress being returned
         * without supporting {@link InetSocketAddress}
         * </p>
         *
         * @return the SocketAddress for the remote connection, or null if not supported by Session
         */
        SocketAddress getRemoteAddress();

        /**
         * @return True if the websocket is open outbound
         */
        boolean isOutputOpen();

        /**
         * If using BatchMode.ON or BatchMode.AUTO, trigger a flush of enqueued / batched frames.
         *
         * @param callback the callback to track close frame sent (or failed)
         */
        void flush(Callback callback);

        /**
         * Initiate close handshake, no payload (no declared status code or reason phrase)
         *
         * @param callback the callback to track close frame sent (or failed)
         */
        void close(Callback callback);

        /**
         * Initiate close handshake with provide status code and optional reason phrase.
         *
         * @param statusCode the status code (should be a valid status code that can be sent)
         * @param reason optional reason phrase (will be truncated automatically by implementation to fit within limits of protocol)
         * @param callback the callback to track close frame sent (or failed)
         */
        void close(int statusCode, String reason, Callback callback);

        /**
         * Issue a harsh abort of the underlying connection.
         * <p>
         * This will terminate the connection, without sending a websocket close frame.
         * No WebSocket Protocol close handshake will be performed.
         * </p>
         * <p>
         * Once called, any read/write activity on the websocket from this point will be indeterminate.
         * This can result in the {@link #onError(Throwable, Callback)} event being called indicating any issue that arises.
         * </p>
         * <p>
         * Once the underlying connection has been determined to be closed, the {@link #onClosed(CloseStatus, Callback)} event will be called.
         * </p>
         */
        void abort();

        /**
         * Manage flow control by indicating demand for handling Frames.  A call to
         * {@link FrameHandler#onFrame(Frame, Callback)} will only be made if a
         * corresponding demand has been signaled.   It is an error to call this method
         * if {@link FrameHandler#isDemanding()} returns false.
         *
         * @param n The number of frames that can be handled (in sequential calls to
         * {@link FrameHandler#onFrame(Frame, Callback)}).  May not be negative.
         */
        void demand(long n);

        class Empty extends ConfigurationCustomizer implements CoreSession
        {
            @Override
            public String getNegotiatedSubProtocol()
            {
                return null;
            }

            @Override
            public List<ExtensionConfig> getNegotiatedExtensions()
            {
                return null;
            }

            @Override
            public Map<String, List<String>> getParameterMap()
            {
                return null;
            }

            @Override
            public String getProtocolVersion()
            {
                return null;
            }

            @Override
            public URI getRequestURI()
            {
                return null;
            }

            @Override
            public boolean isSecure()
            {
                return false;
            }

            @Override
            public void abort()
            {
            }

            @Override
            public Behavior getBehavior()
            {
                return null;
            }

            @Override
            public ByteBufferPool getByteBufferPool()
            {
                return null;
            }

            @Override
            public SocketAddress getLocalAddress()
            {
                return null;
            }

            @Override
            public SocketAddress getRemoteAddress()
            {
                return null;
            }

            @Override
            public boolean isOutputOpen()
            {
                return false;
            }

            @Override
            public void flush(Callback callback)
            {
            }

            @Override
            public void close(Callback callback)
            {
            }

            @Override
            public void close(int statusCode, String reason, Callback callback)
            {
            }

            @Override
            public void demand(long n)
            {
            }

            @Override
            public void sendFrame(Frame frame, Callback callback, boolean batch)
            {
            }
        }
    }

    interface Customizer
    {
        void customize(Configuration configurable);
    }

    class ConfigurationHolder implements Configuration
    {
        protected Duration idleTimeout;
        protected Duration writeTimeout;
        protected Boolean autoFragment;
        protected Long maxFrameSize;
        protected Integer outputBufferSize;
        protected Integer inputBufferSize;
        protected Long maxBinaryMessageSize;
        protected Long maxTextMessageSize;

        @Override
        public Duration getIdleTimeout()
        {
            return idleTimeout == null ? WebSocketConstants.DEFAULT_IDLE_TIMEOUT : idleTimeout;
        }

        @Override
        public Duration getWriteTimeout()
        {
            return writeTimeout == null ? WebSocketConstants.DEFAULT_WRITE_TIMEOUT : writeTimeout;
        }

        @Override
        public void setIdleTimeout(Duration timeout)
        {
            this.idleTimeout = timeout;
        }

        @Override
        public void setWriteTimeout(Duration timeout)
        {
            this.writeTimeout = timeout;
        }

        @Override
        public boolean isAutoFragment()
        {
            return autoFragment == null ? WebSocketConstants.DEFAULT_AUTO_FRAGMENT : autoFragment;
        }

        @Override
        public void setAutoFragment(boolean autoFragment)
        {
            this.autoFragment = autoFragment;
        }

        @Override
        public long getMaxFrameSize()
        {
            return maxFrameSize == null ? WebSocketConstants.DEFAULT_MAX_FRAME_SIZE : maxFrameSize;
        }

        @Override
        public void setMaxFrameSize(long maxFrameSize)
        {
            this.maxFrameSize = maxFrameSize;
        }

        @Override
        public int getOutputBufferSize()
        {
            return outputBufferSize == null ? WebSocketConstants.DEFAULT_OUTPUT_BUFFER_SIZE : outputBufferSize;
        }

        @Override
        public void setOutputBufferSize(int outputBufferSize)
        {
            this.outputBufferSize = outputBufferSize;
        }

        @Override
        public int getInputBufferSize()
        {
            return inputBufferSize == null ? WebSocketConstants.DEFAULT_INPUT_BUFFER_SIZE : inputBufferSize;
        }

        @Override
        public void setInputBufferSize(int inputBufferSize)
        {
            this.inputBufferSize = inputBufferSize;
        }

        @Override
        public long getMaxBinaryMessageSize()
        {
            return maxBinaryMessageSize == null ? WebSocketConstants.DEFAULT_MAX_BINARY_MESSAGE_SIZE : maxBinaryMessageSize;
        }

        @Override
        public void setMaxBinaryMessageSize(long maxBinaryMessageSize)
        {
            this.maxBinaryMessageSize = maxBinaryMessageSize;
        }

        @Override
        public long getMaxTextMessageSize()
        {
            return maxTextMessageSize == null ? WebSocketConstants.DEFAULT_MAX_TEXT_MESSAGE_SIZE : maxTextMessageSize;
        }

        @Override
        public void setMaxTextMessageSize(long maxTextMessageSize)
        {
            this.maxTextMessageSize = maxTextMessageSize;
        }
    }

    class ConfigurationCustomizer extends ConfigurationHolder implements Customizer
    {
        @Override
        public void customize(Configuration configurable)
        {
            if (idleTimeout != null)
                configurable.setIdleTimeout(idleTimeout);
            if (writeTimeout != null)
                configurable.setWriteTimeout(idleTimeout);
            if (autoFragment != null)
                configurable.setAutoFragment(autoFragment);
            if (maxFrameSize != null)
                configurable.setMaxFrameSize(maxFrameSize);
            if (inputBufferSize != null)
                configurable.setInputBufferSize(inputBufferSize);
            if (outputBufferSize != null)
                configurable.setOutputBufferSize(outputBufferSize);
            if (maxBinaryMessageSize != null)
                configurable.setMaxBinaryMessageSize(maxBinaryMessageSize);
            if (maxTextMessageSize != null)
                configurable.setMaxTextMessageSize(maxTextMessageSize);
        }

        public static ConfigurationCustomizer from(ConfigurationCustomizer parent, ConfigurationCustomizer child)
        {
            ConfigurationCustomizer customizer = new ConfigurationCustomizer();
            parent.customize(customizer);
            child.customize(customizer);
            return customizer;
        }
    }
}
