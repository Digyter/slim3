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
package org.slim3.util;

import java.util.TimeZone;

/**
 * A class to access the current {@link TimeZone}.
 * 
 * @author higa
 * @since 1.0.0
 * 
 */
public final class TimeZoneLocator {

    private static ThreadLocal<TimeZone> timeZones =
        new ThreadLocal<TimeZone>();

    /**
     * Returns the {@link TimeZone} attached to the current thread.
     * 
     * @return the {@link TimeZone} attached to the current thread
     */
    public static TimeZone get() {
        TimeZone timeZone = timeZones.get();
        if (timeZone != null) {
            return timeZone;
        }
        return TimeZone.getDefault();
    }

    /**
     * Sets the {@link TimeZone} to the current thread.
     * 
     * @param timeZone
     *            the {@link TimeZone}
     */
    public static void set(TimeZone timeZone) {
        timeZones.set(timeZone);
    }

    private TimeZoneLocator() {
    }
}