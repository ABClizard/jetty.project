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

package org.eclipse.jetty.websocket.api.extensions;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Represents an Extension Configuration, as seen during the connection Handshake process.
 */
public interface ExtensionConfig
{
    interface Parser
    {
        ExtensionConfig parse(String parameterizedName);
    }

    private static ExtensionConfig.Parser getParser()
    {
        return ServiceLoader.load(ExtensionConfig.Parser.class).findFirst().get();
    }

    static ExtensionConfig parse(String parameterizedName)
    {
        return getParser().parse(parameterizedName);
    }

    String getName();

    int getParameter(String key, int defValue);

    String getParameter(String key, String defValue);

    String getParameterizedName();

    Set<String> getParameterKeys();

    /**
     * Return parameters found in request URI.
     *
     * @return the parameter map
     */
    Map<String, String> getParameters();

    void setParameter(String key);

    void setParameter(String key, int value);

    void setParameter(String key, String value);
}
