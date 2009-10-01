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

import java.util.Arrays;
import java.util.List;

import org.slim3.tester.DatastoreTestCase;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;

/**
 * @author higa
 * 
 */
public class SpikeTest extends DatastoreTestCase {

    /**
     * @throws Exception
     */
    public void testSpike() throws Exception {
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        Entity entity = new Entity("Group");
        entity.setProperty("name", "Admin");
        Entity entity2 = new Entity("Group");
        entity2.setProperty("name", "User");
        List<Key> keys = ds.put(Arrays.asList(entity, entity2));
        assertEquals("Admin", ds.get(keys.get(0)).getProperty("name"));
        assertEquals("User", ds.get(keys.get(1)).getProperty("name"));
    }
}
