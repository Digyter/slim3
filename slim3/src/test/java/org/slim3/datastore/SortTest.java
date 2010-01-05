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
package org.slim3.datastore;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import org.junit.Test;

import com.google.appengine.api.datastore.Query.SortDirection;

/**
 * @author higa
 * 
 */
public class SortTest {

    /**
     * @throws Exception
     */
    @Test
    public void constructor() throws Exception {
        Sort sort = new Sort("aaa");
        assertThat(sort.getPropertyName(), is("aaa"));
        assertThat(sort.getDirection(), is(SortDirection.ASCENDING));
    }

    /**
     * @throws Exception
     */
    @Test
    public void constructor2() throws Exception {
        Sort sort = new Sort("aaa", SortDirection.DESCENDING);
        assertThat(sort.getPropertyName(), is("aaa"));
        assertThat(sort.getDirection(), is(SortDirection.DESCENDING));
    }
}