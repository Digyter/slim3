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
package org.slim3.gen.datastore;

/**
 * @author taedium
 * 
 */
public abstract class AbstractDataType implements DataType {

    protected final String className;

    protected final String typeName;

    /** the unindex */
    protected boolean unindexed;

    /** the serializable */
    protected boolean serialized;

    /**
     * Creates a new {@link DataType}.
     * 
     * @param env
     *            the environment
     * @param declaration
     *            the declaration
     * @param typeMirror
     *            the typemirror
     */
    public AbstractDataType(String className, String typeName) {
        this.className = className;
        this.typeName = typeName;
    }

    /**
     * @return the name
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the unindex
     */
    public boolean isUnindexed() {
        return unindexed;
    }

    /**
     * @param unindexed
     *            the unindex to set
     */
    public void setUnindexed(boolean unindexed) {
        this.unindexed = unindexed;
    }

    /**
     * @return the serializable
     */
    public boolean isSerialized() {
        return serialized;
    }

    /**
     * @param serializable
     *            the serializable to set
     */
    public void setSerialized(boolean serializable) {
        this.serialized = serializable;
    }

    /**
     * @return the className
     */
    public String getTypeName() {
        return typeName;
    }

}
