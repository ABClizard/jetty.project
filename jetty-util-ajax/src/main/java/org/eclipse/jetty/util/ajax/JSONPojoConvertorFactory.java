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

package org.eclipse.jetty.util.ajax;

import java.util.Map;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.ajax.JSON.Convertor;
import org.eclipse.jetty.util.ajax.JSON.Output;

public class JSONPojoConvertorFactory implements JSON.Convertor
{
    private final JSON _json;
    private final boolean _fromJson;

    public JSONPojoConvertorFactory(JSON json)
    {
        if (json == null)
        {
            throw new IllegalArgumentException();
        }
        _json = json;
        _fromJson = true;
    }

    /**
     * @param json The JSON instance to use
     * @param fromJSON If true, the class name of the objects is included
     * in the generated JSON and is used to instantiate the object when
     * JSON is parsed (otherwise a Map is used).
     */
    public JSONPojoConvertorFactory(JSON json, boolean fromJSON)
    {
        if (json == null)
        {
            throw new IllegalArgumentException();
        }
        _json = json;
        _fromJson = fromJSON;
    }

    @Override
    public void toJSON(Object obj, Output out)
    {
        String clsName = obj.getClass().getName();
        Convertor convertor = _json.getConvertorFor(clsName);
        if (convertor == null)
        {
            try
            {
                Class cls = Loader.loadClass(clsName);
                convertor = new JSONPojoConvertor(cls, _fromJson);
                _json.addConvertorFor(clsName, convertor);
            }
            catch (ClassNotFoundException e)
            {
                JSON.LOG.warn(e);
            }
        }
        if (convertor != null)
        {
            convertor.toJSON(obj, out);
        }
    }

    @Override
    public Object fromJSON(Map object)
    {
        Map map = object;
        String clsName = (String)map.get("class");
        if (clsName != null)
        {
            Convertor convertor = _json.getConvertorFor(clsName);
            if (convertor == null)
            {
                try
                {
                    Class cls = Loader.loadClass(clsName);
                    convertor = new JSONPojoConvertor(cls, _fromJson);
                    _json.addConvertorFor(clsName, convertor);
                }
                catch (ClassNotFoundException e)
                {
                    JSON.LOG.warn(e);
                }
            }
            if (convertor != null)
            {
                return convertor.fromJSON(object);
            }
        }
        return map;
    }
}
