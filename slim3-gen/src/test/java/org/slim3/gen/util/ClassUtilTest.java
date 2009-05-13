/*
 * Copyright 2004-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.slim3.gen.util;

import junit.framework.TestCase;

/**
 * @author taedium
 * 
 */
public class ClassUtilTest extends TestCase {

    public void testGetPackageName() throws Exception {
        assertEquals("aaa.bbb", ClassUtil.getPackageName("aaa.bbb.Ccc"));
        assertEquals("", ClassUtil.getPackageName("Ccc"));
    }

    public void testGetSimpleName() throws Exception {
        assertEquals("Ccc", ClassUtil.getSimpleName("aaa.bbb.Ccc"));
        assertEquals("Ccc", ClassUtil.getSimpleName("Ccc"));
    }
}
