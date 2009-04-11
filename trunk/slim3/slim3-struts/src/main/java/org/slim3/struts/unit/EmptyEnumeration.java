/*
 * Copyright 2004-2009 the Seasar Foundation and the Others.
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
package org.slim3.struts.unit;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * An empty {@link Enumeration}.
 * 
 * @author higa
 * @param <T>
 *            the type
 * @since 3.0
 * 
 */
public class EmptyEnumeration<T> implements Enumeration<T> {

    /**
     * Constructor.
     * 
     */
    public EmptyEnumeration() {
    }

    public boolean hasMoreElements() {
        return false;
    }

    public T nextElement() {
        throw new NoSuchElementException("No element.");
    }
}