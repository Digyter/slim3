/*
 * Copyright 2004-2010 the Seasar Foundation and the Others.
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

import com.google.appengine.api.datastore.Query.SortDirection;

/**
 * An implementation class for "ascending" sort.
 * 
 * @author higa
 * @since 1.0.0
 * 
 */
public class AscCriterion extends InMemoryAscCriterion implements SortCriterion {

    /**
     * The {@link Sort}.
     */
    protected Sort sort;

    /**
     * Constructor.
     * 
     * @param attributeMeta
     *            the meta data of attribute
     * @throws NullPointerException
     *             if the attributeMeta parameter is null
     */
    public AscCriterion(AttributeMeta<?, ?> attributeMeta)
            throws NullPointerException {
        super(attributeMeta);
        sort = new Sort(attributeMeta.getName(), SortDirection.ASCENDING);
    }

    public Sort getSort() {
        return sort;
    }
}