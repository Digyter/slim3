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
package org.slim3.commons.exception;

/**
 * This exception is thrown when a class can not be assigned to an another
 * class.
 * 
 * @author higa
 * @since 3.0
 */
public class ClassCanNotAssignedRuntimeException extends SRuntimeException {

    static final long serialVersionUID = 1L;

    /**
     * The original class.
     */
    protected Class<?> originalClass;

    /**
     * The destination class.
     */
    protected Class<?> destinationClass;

    /**
     * Constructor.
     * 
     * @param originalClass
     *            the original class.
     * @param destinationClass
     *            the destination class.
     */
    public ClassCanNotAssignedRuntimeException(Class<?> originalClass,
            Class<?> destinationClass) {
        super("S3Commons-E0015", originalClass.getName(), destinationClass
                .getName());
        this.originalClass = originalClass;
        this.destinationClass = destinationClass;
    }

    /**
     * Returns the original class.
     * 
     * @return the original class
     */
    public Class<?> getOriginalClass() {
        return originalClass;
    }

    /**
     * Returns the destination class.
     * 
     * @return the destination class.
     */
    public Class<?> getDestinationClass() {
        return destinationClass;
    }
}