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

import junit.framework.TestCase;

import org.slim3.datastore.meta.HogeMeta;

import com.google.appengine.api.datastore.Query.SortDirection;

/**
 * @author higa
 * 
 */
public class CoreAttributeMetaTest extends TestCase {

    private HogeMeta meta = new HogeMeta();

    /**
     * @throws Exception
     * 
     */
    public void testEqual() throws Exception {
        assertEquals(EqualCriterion.class, meta.myString.equal("a").getClass());
        assertNull(meta.myString.equal(null));
        EqualCriterion c =
            (EqualCriterion) meta.myEnum.equal(SortDirection.ASCENDING);
        assertEquals("ASCENDING", c.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testLessThan() throws Exception {
        assertEquals(LessThanCriterion.class, meta.myString
            .lessThan("a")
            .getClass());
        assertNull(meta.myString.lessThan(null));
        LessThanCriterion c =
            (LessThanCriterion) meta.myEnum.lessThan(SortDirection.ASCENDING);
        assertEquals("ASCENDING", c.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testLessThanOrEqual() throws Exception {
        assertEquals(LessThanOrEqualCriterion.class, meta.myString
            .lessThanOrEqual("a")
            .getClass());
        assertNull(meta.myString.lessThanOrEqual(null));
        LessThanOrEqualCriterion c =
            (LessThanOrEqualCriterion) meta.myEnum
                .lessThanOrEqual(SortDirection.ASCENDING);
        assertEquals("ASCENDING", c.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testGreaterThan() throws Exception {
        assertEquals(GreaterThanCriterion.class, meta.myString
            .greaterThan("a")
            .getClass());
        assertNull(meta.myString.greaterThan(null));
        GreaterThanCriterion c =
            (GreaterThanCriterion) meta.myEnum
                .greaterThan(SortDirection.ASCENDING);
        assertEquals("ASCENDING", c.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testGreaterThanOrEqual() throws Exception {
        assertEquals(GreaterThanOrEqualCriterion.class, meta.myString
            .greaterThanOrEqual("a")
            .getClass());
        assertNull(meta.myString.greaterThanOrEqual(null));
        GreaterThanOrEqualCriterion c =
            (GreaterThanOrEqualCriterion) meta.myEnum
                .greaterThanOrEqual(SortDirection.ASCENDING);
        assertEquals("ASCENDING", c.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testBetween() throws Exception {
        assertEquals(BetweenCriterion.class, meta.myString
            .between("a", "c")
            .getClass());
        assertEquals(GreaterThanOrEqualCriterion.class, meta.myString.between(
            "a",
            null).getClass());
        assertEquals(LessThanOrEqualCriterion.class, meta.myString.between(
            null,
            "c").getClass());
        assertNull(meta.myString.between(null, null));
        BetweenCriterion c =
            (BetweenCriterion) meta.myEnum.between(
                SortDirection.ASCENDING,
                SortDirection.DESCENDING);
        assertEquals("ASCENDING", c.start);
        assertEquals("DESCENDING", c.end);
        GreaterThanOrEqualCriterion c2 =
            (GreaterThanOrEqualCriterion) meta.myEnum.between(
                SortDirection.ASCENDING,
                null);
        assertEquals("ASCENDING", c2.value);
        LessThanOrEqualCriterion c3 =
            (LessThanOrEqualCriterion) meta.myEnum.between(
                null,
                SortDirection.DESCENDING);
        assertEquals("DESCENDING", c3.value);
    }

    /**
     * @throws Exception
     * 
     */
    public void testIsNotNull() throws Exception {
        assertEquals(IsNotNullCriterion.class, meta.myString
            .isNotNull()
            .getClass());
    }

    /**
     * @throws Exception
     * 
     */
    public void testIsNull() throws Exception {
        assertEquals(IsNullCriterion.class, meta.myString.isNull().getClass());
    }
}