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

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.Declaration;

/**
 * Logs messages.
 * 
 * @author taedium
 * @since 3.0
 * 
 */
public final class Logger {

    /**
     * Logs a debug message.
     * 
     * @param env
     *            the environment.
     * @param message
     *            the message.
     */
    public static void debug(AnnotationProcessorEnvironment env, String message) {
        Messager messager = env.getMessager();
        messager.printNotice(message);
    }

    /**
     * Logs a warning message.
     * 
     * @param env
     *            the environment.
     * @param element
     *            the element to use as a position hint
     * @param message
     *            the message.
     */
    public static void warning(AnnotationProcessorEnvironment env,
            Declaration element, String message) {
        Messager messager = env.getMessager();
        messager.printWarning(element.getPosition(), message);
    }

    /**
     * Logs an error message.
     * 
     * @param env
     *            the environment.
     * @param element
     *            the element to use as a position hint
     * @param message
     *            the message.
     */
    public static void error(AnnotationProcessorEnvironment env,
            Declaration element, String message) {
        Messager messager = env.getMessager();
        messager.printError(element.getPosition(), message);
    }

}