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
package org.slim3.controller.validator;

import javax.servlet.http.HttpServletRequest;

import org.slim3.util.ApplicationMessage;

/**
 * A validator for a float value.
 * 
 * @author higa
 * @since 3.0
 * 
 */
public class FloatTypeValidator extends AbstractValidator {

    /**
     * The instance.
     */
    public static FloatTypeValidator INSTANCE = new FloatTypeValidator();

    /**
     * Constructor.
     */
    public FloatTypeValidator() {
        super();
    }

    /**
     * Constructor.
     * 
     * @param message
     *            the error message
     */
    public FloatTypeValidator(String message) {
        super(message);
    }

    @Override
    public String validate(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        if (value == null || "".equals(value)) {
            return null;
        }
        try {
            String s = (String) value;
            Float.valueOf(s);
            return null;
        } catch (Throwable ignore) {
            if (message != null) {
                return message;
            }
            return ApplicationMessage
                .get("validator.floatType", getLabel(name));
        }
    }
}