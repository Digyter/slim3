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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.slim3.tester.AppEngineTestCase;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.QueryResultList;

/**
 * @author higa
 * 
 */
public class S3QueryResultListTest extends AppEngineTestCase {

    private AsyncDatastoreService ds = DatastoreServiceFactory
        .getAsyncDatastoreService();

    /**
     * @throws Exception
     */
    @Test
    public void constructor() throws Exception {
        DatastoreUtil.put(ds, null, new Entity("Hoge"));
        EntityQuery q = new EntityQuery(ds, "Hoge");
        QueryResultList<Entity> qrList =
            q
                .filter("myString", FilterOperator.EQUAL, "aaa")
                .asQueryResultList();
        Cursor cursor = qrList.getCursor();
        S3QueryResultList<Entity> list =
            new S3QueryResultList<Entity>(
                qrList,
                cursor.toWebSafeString(),
                q.getEncodedFilter(),
                q.getEncodedSorts(),
                true);
        assertThat(list.getEncodedCursor(), is(cursor.toWebSafeString()));
        assertThat(list.getEncodedFilter(), is(q.getEncodedFilter()));
        assertThat(list.getEncodedSorts(), is(q.getEncodedSorts()));
        assertThat(list.hasNext(), is(true));
    }
}