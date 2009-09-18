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
package org.slim3.gen.processor;

import org.slim3.gen.message.MessageCode;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.Declaration;

/**
 * Thrown when validation in annotation processing is failed.
 * 
 * @author taedium
 * @since 3.0
 * 
 */
public class ValidationException extends AptException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new {@link ValidationException}.
     * 
     * @param messageCode
     *            the message code
     * @param env
     *            the environment
     * @param declaration
     *            the send target
     * @param args
     *            arguments
     */
    public ValidationException(MessageCode messageCode,
            AnnotationProcessorEnvironment env, Declaration declaration,
            Object... args) {
        this(messageCode, env, declaration, null, args);
    }

    /**
     * Creates a new {@link ValidationException}.
     * 
     * @param messageCode
     *            the message code
     * @param env
     *            the environment
     * @param declaration
     *            the send target
     * @param cause
     *            the cause
     * @param args
     *            arguments
     */
    public ValidationException(MessageCode messageCode,
            AnnotationProcessorEnvironment env, Declaration declaration,
            Throwable cause, Object... args) {
        super(messageCode, env, declaration, cause, args);
    }
}
