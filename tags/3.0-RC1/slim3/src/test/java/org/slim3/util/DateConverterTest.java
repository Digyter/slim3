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
package org.slim3.util;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

/**
 * @author higa
 * 
 */
public class DateConverterTest {

    /**
     * @throws Exception
     */
    @Test
    public void getAsObjectAndGetAsString() throws Exception {
        DateConverter converter = new DateConverter("MM/dd/yyyy");
        Date result = converter.getAsObject("01/16/2008");
        assertThat(converter.getAsString(result), is("01/16/2008"));
    }

    /**
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void getAsStringForCastException() throws Exception {
        DateConverter converter = new DateConverter("yyyy/MM/dd");
        converter.getAsString("aaa");
    }

    /**
     * @throws Exception
     */
    @Test(expected = NullPointerException.class)
    public void constructorForNull() throws Exception {
        new DateConverter(null);
    }

    /**
     * @throws Exception
     */
    @Test
    public void isTarget() throws Exception {
        DateConverter converter = new DateConverter("MM/dd/yyyy");
        assertThat(converter.isTarget(java.util.Date.class), is(true));
        assertThat(converter.isTarget(java.sql.Date.class), is(true));
    }
}