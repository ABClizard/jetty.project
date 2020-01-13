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

package org.eclipse.jetty.jmx;

import java.util.Arrays;
import java.util.List;

import com.openpojo.reflection.impl.PojoClassFactory;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;
import org.eclipse.jetty.util.log.jmx.LogMBean;
import org.junit.jupiter.api.Test;

/*
 * This class tests all the getters and setters for a given list of classes.
 */
public class PojoTest
{
    @Test
    public void testOpenPojo()
    {
        Validator validator = ValidatorBuilder.create().with(new SetterTester()).with(new GetterTester()).build();
        List<Class> classes = Arrays.asList(MBeanContainer.class, ObjectMBean.class, LogMBean.class);
        for (Class clazz : classes)
        {
            validator.validate(PojoClassFactory.getPojoClass(clazz));
        }
    }
}
