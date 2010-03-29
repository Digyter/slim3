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

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slim3.datastore.meta.HogeMeta;
import org.slim3.datastore.model.Hoge;
import org.slim3.tester.AppEngineTestCase;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.labs.taskqueue.TaskQueuePb.TaskQueueAddRequest;

/**
 * @author higa
 * 
 */
public class GlobalTransactionTest extends AppEngineTestCase {

    private GlobalTransaction gtx;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        gtx = new GlobalTransaction();
        gtx.begin();
    }

    /**
     * @throws Exception
     */
    @Test
    public void getOrNull() throws Exception {
        Key globalTransactionKey =
            Datastore.createKey(GlobalTransaction.KIND, 1);
        Boolean valid = false;
        Entity entity = new Entity(globalTransactionKey);
        entity.setUnindexedProperty(GlobalTransaction.VALID_PROPERTY, valid);
        Datastore.putWithoutTx(entity);
        GlobalTransaction gtx2 =
            GlobalTransaction.getOrNull(
                Datastore.beginTransaction(),
                globalTransactionKey);
        assertThat(gtx2.globalTransactionKey, is(globalTransactionKey));
        assertThat(gtx2.valid, is(valid));
    }

    /**
     * @throws Exception
     */
    @Test
    public void toGlobalTransaction() throws Exception {
        Key globalTransactionKey =
            Datastore.createKey(GlobalTransaction.KIND, 1);
        Boolean valid = false;
        Entity entity = new Entity(globalTransactionKey);
        entity.setUnindexedProperty(GlobalTransaction.VALID_PROPERTY, valid);
        GlobalTransaction gtx2 = GlobalTransaction.toGlobalTransaction(entity);
        assertThat(gtx2.globalTransactionKey, is(globalTransactionKey));
        assertThat(gtx2.valid, is(valid));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putGlobalTransaction() throws Exception {
        Transaction tx = Datastore.beginTransaction();
        Key globalTransactionKey =
            Datastore.createKey(GlobalTransaction.KIND, 1);
        Boolean valid = false;
        GlobalTransaction gtx2 =
            new GlobalTransaction(globalTransactionKey, valid);
        GlobalTransaction.put(tx, gtx2);
        tx.commit();
        Entity entity = Datastore.get(globalTransactionKey);
        assertThat(entity, is(notNullValue()));
        assertThat((Boolean) entity
            .getProperty(GlobalTransaction.VALID_PROPERTY), is(valid));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getActiveTransactions() throws Exception {
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(1));
        gtx.rollback();
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getCurrentTransaction() throws Exception {
        assertThat(GlobalTransaction.getCurrentTransaction(), is(gtx));
    }

    /**
     * @throws Exception
     */
    @Test
    public void clearActiveTransactions() throws Exception {
        GlobalTransaction.clearActiveTransactions();
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void constructor() throws Exception {
        Key globalTransactionKey = Datastore.allocateId(GlobalTransaction.KIND);
        boolean valid = false;
        GlobalTransaction gtx2 =
            new GlobalTransaction(globalTransactionKey, valid);
        assertThat(gtx2.globalTransactionKey, is(globalTransactionKey));
        assertThat(gtx2.valid, is(valid));
    }

    /**
     * @throws Exception
     */
    @Test
    public void begin() throws Exception {
        assertThat(gtx.localTransaction, is(notNullValue()));
        assertThat(gtx.globalTransactionKey, is(nullValue()));
        assertThat(gtx.isActive(), is(true));
        assertThat(gtx.timestamp, is(not(0L)));
        assertThat(gtx.lockMap, is(notNullValue()));
        assertThat(gtx.journalMap, is(notNullValue()));
        assertThat(GlobalTransaction.getCurrentTransaction(), is(gtx));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getId() throws Exception {
        assertThat(gtx.getId(), is(not(nullValue())));
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyLockAndGetAsMap() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.verifyLockAndGetAsMap(rootKey, Arrays.asList(key)).get(
            key), is(notNullValue()));
        assertThat(gtx.lockMap.get(rootKey), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyLockAndGetAsMapWhenNoEntityIsFound() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        assertThat(gtx.verifyLockAndGetAsMap(rootKey, Arrays.asList(key)).get(
            key), is(nullValue()));
        assertThat(gtx.lockMap.get(rootKey), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void lockAndGetAsMap() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        gtx.setLocalTransactionRootKey(rootKey);
        assertThat(
            gtx.lockAndGetAsMap(rootKey, Arrays.asList(key)).get(key),
            is(notNullValue()));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(Datastore.get(lock.key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void lockAndGetAsMapWhenNoEntityIsFound() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        gtx.setLocalTransactionRootKey(rootKey);
        assertThat(
            gtx.lockAndGetAsMap(rootKey, Arrays.asList(key)).get(key),
            is(nullValue()));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(Datastore.get(lock.key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void lockAndGetAsMapWhenConcurrentModificationExceptionOccurred()
            throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        GlobalTransaction otherGtx = new GlobalTransaction();
        otherGtx.begin();
        otherGtx.setLocalTransactionRootKey(key2);
        otherGtx.lock(key2);
        gtx.setLocalTransactionRootKey(key);
        try {
            gtx.lockAndGetAsMap(key, Arrays.asList(key));
            gtx.lockAndGetAsMap(key2, Arrays.asList(key2));
            fail();
        } catch (ConcurrentModificationException e) {
            assertThat(gtx.localTransaction.isActive(), is(false));
            assertThat(gtx.isActive(), is(false));
            assertThat(gtx.lockMap.size(), is(0));
            assertThat(Datastore.getAsMap(Lock.createKey(key)).size(), is(0));
            Lock lock = Lock.getOrNull(null, Lock.createKey(key2));
            assertThat(lock, is(notNullValue()));
            assertThat(
                lock.globalTransactionKey,
                is(otherGtx.globalTransactionKey));
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void lock() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(key);
        gtx.lock(key);
        Lock lock = gtx.lockMap.get(key);
        assertThat(lock, is(notNullValue()));
        assertThat(Datastore.getWithoutTx(lock.key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test(expected = IllegalArgumentException.class)
    public void lockForChildKey() throws Exception {
        Key parentKey = Datastore.createKey("Parent", 1);
        Key childKey = Datastore.createKey(parentKey, "Child", 1);
        gtx.setLocalTransactionRootKey(parentKey);
        gtx.lock(childKey);
    }

    /**
     * @throws Exception
     */
    @Test
    public void lockForSameKey() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.setLocalTransactionRootKey(key);
        gtx.lock(key);
        gtx.lock(key);
    }

    /**
     * @throws Exception
     */
    @Test
    public void lockWhenConcurrentModificationExceptionOccurred()
            throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        GlobalTransaction otherGtx = new GlobalTransaction();
        otherGtx.begin();
        otherGtx.setLocalTransactionRootKey(key2);
        otherGtx.lock(key2);
        try {
            gtx.setLocalTransactionRootKey(key);
            gtx.lock(key);
            gtx.lock(key2);
            fail();
        } catch (ConcurrentModificationException e) {
            assertThat(gtx.lockMap.get(key), is(nullValue()));
            assertThat(Datastore.getAsMap(Lock.createKey(key)).size(), is(0));
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void unlock() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(key);
        gtx.lock(key);
        gtx.unlock();
        assertThat(gtx.isActive(), is(false));
        assertThat(gtx.lockMap.get(key), is(nullValue()));
        assertThat(Datastore.getAsMap(Lock.createKey(key)).size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntity() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(key));
    }

    /**
     * @throws Exception
     */
    @Test(expected = EntityNotFoundRuntimeException.class)
    public void getEntityWhenNoEntityIsFound() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.get(key);
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityUsingModelClass() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(Hoge.class, key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityUsingModelMeta() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(HogeMeta.get(), key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityUsingModelClassAndVersion() throws Exception {
        Hoge hoge = new Hoge();
        Datastore.putWithoutTx(hoge);
        assertThat(gtx.get(Hoge.class, hoge.getKey(), 1), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test(expected = ConcurrentModificationException.class)
    public void getEntityUsingModelClassAndVersionWhenConcurrentModificationExceptinOccurred()
            throws Exception {
        Hoge hoge = new Hoge();
        Datastore.putWithoutTx(hoge);
        gtx.get(Hoge.class, hoge.getKey(), 2);
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityUsingModelMetaAndVersion() throws Exception {
        Hoge hoge = new Hoge();
        Datastore.putWithoutTx(hoge);
        assertThat(
            gtx.get(HogeMeta.get(), hoge.getKey(), 1),
            is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test(expected = ConcurrentModificationException.class)
    public void getEntityUsingModelMetaAndVersionWhenConcurrentModificationExceptinOccurred()
            throws Exception {
        Hoge hoge = new Hoge();
        Datastore.putWithoutTx(hoge);
        gtx.get(HogeMeta.get(), hoge.getKey(), 2);
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNull() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.getOrNull(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenNoEntityIsFound() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        assertThat(gtx.getOrNull(key), is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenEntityWithoutTxIsNotFound() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.getAsMap(gtx.localTransaction, key);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.getOrNull(key), is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenLocalTransactionRootKeyIsSame()
            throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Key key2 = Datastore.createKey(rootKey, "Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        gtx.getOrNull(key);
        assertThat(gtx.getOrNull(key2), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenLocalTransactionRootKeyIsSameAndEntityWithoutTxIsNotFound()
            throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        gtx.getOrNull(key);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.getOrNull(key), is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenLocalTransactionRootKeyIsDifferent()
            throws Exception {
        Key otherKey = Datastore.createKey("Hoge", 2);
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(otherKey));
        gtx.getOrNull(otherKey);
        assertThat(gtx.getOrNull(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(otherKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(Datastore.getWithoutTx(lock.key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullWhenLocalTransactionRootKeyIsDifferentAndNoEntityIsFound()
            throws Exception {
        Key otherKey = Datastore.createKey("Hoge", 2);
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        gtx.getOrNull(otherKey);
        assertThat(gtx.getOrNull(key), is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(otherKey));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(Datastore.getWithoutTx(lock.key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNulUsingModelClass() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.getOrNull(Hoge.class, key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNulUsingModelClassWhenNoEntityIsFound()
            throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        assertThat(gtx.getOrNull(Hoge.class, key), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullUsingModelMeta() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.getOrNull(HogeMeta.get(), key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntityOrNullUsingModelMetaWhenNoEntityIsFound()
            throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        assertThat(gtx.getOrNull(HogeMeta.get(), key), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntities() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(Arrays.asList(key)), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntitiesUsingModelClass() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(Hoge.class, Arrays.asList(key)), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntitiesUsingModelMeta() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(
            gtx.get(HogeMeta.get(), Arrays.asList(key)),
            is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntitiesVarargs() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(key, key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntitiesVarargsUsingModelClass() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(Hoge.class, key, key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getEntitiesVarargsUsingModelMeta() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        assertThat(gtx.get(HogeMeta.get(), key, key), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForOneLocalKey() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForMultipleLocalKeys() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Key key2 = Datastore.createKey(rootKey, "Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key, key2));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(2));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(map.get(key2), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForLocalKeyAndGlobalKey() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key, key2));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(2));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(map.get(key2), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(key));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(key2), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForLocalKeyAndGlobalKeyWhenLocalTransactionRootKeyIsAlreadySet()
            throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        gtx.getAsMap(Arrays.asList(rootKey));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key, key2));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(2));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(map.get(key2), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(key2), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForLocalKeyWhenLocalTransactionRootKeyIsAlreadySet()
            throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        gtx.getAsMap(Arrays.asList(rootKey));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForGlobalKeyWhenLocalTransactionRootKeyIsAlreadySet()
            throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        Datastore.putWithoutTx(new Entity(key2));
        gtx.getAsMap(Arrays.asList(rootKey));
        Map<Key, Entity> map = gtx.getAsMap(Arrays.asList(key2));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key2), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(key2), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapUsingModelClass() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Hoge> map = gtx.getAsMap(Hoge.class, Arrays.asList(key));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapUsingModelMeta() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Hoge> map = gtx.getAsMap(HogeMeta.get(), Arrays.asList(key));
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapForVarargs() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Entity> map = gtx.getAsMap(key);
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapUsingModelClassForVarargs() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Hoge> map = gtx.getAsMap(Hoge.class, key);
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void getAsMapUsingModelMetaForVarargs() throws Exception {
        Key rootKey = Datastore.createKey("Root", 1);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Map<Key, Hoge> map = gtx.getAsMap(HogeMeta.get(), key);
        assertThat(map, is(notNullValue()));
        assertThat(map.size(), is(1));
        assertThat(map.get(key), is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingKindAndAncestorKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        EntityQuery query = gtx.query("Hoge", ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingKindAndAncestorKeyWhenSameRootKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(ancestorKey);
        EntityQuery query = gtx.query("Hoge", ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingKindAndAncestorKeyWithGlobalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        gtx.getAsMap(key2);
        EntityQuery query = gtx.query("Hoge", ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(key2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(ancestorKey), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelClassAndAncestorKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        ModelQuery<Hoge> query = gtx.query(Hoge.class, ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelClassAndAncestorKeyWhenSameRootKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(ancestorKey);
        ModelQuery<Hoge> query = gtx.query(Hoge.class, ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelClassAndAncestorKeyWithGlobalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        gtx.getAsMap(key2);
        ModelQuery<Hoge> query = gtx.query(Hoge.class, ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(key2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(ancestorKey), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelMetaAndAncestorKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        ModelQuery<Hoge> query = gtx.query(HogeMeta.get(), ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelMetaAndAncestorKeyWhenSameRootKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(ancestorKey);
        ModelQuery<Hoge> query = gtx.query(HogeMeta.get(), ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingModelMetaAndAncestorKeyWithGlobalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        gtx.getAsMap(key2);
        ModelQuery<Hoge> query = gtx.query(HogeMeta.get(), ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(key2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(ancestorKey), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingAncestorKeyAsLocalTransaction() throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        KindlessQuery query = gtx.query(ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingAncestorKeyWhenSameRootKeyAsLocalTransaction()
            throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(ancestorKey);
        KindlessQuery query = gtx.query(ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(notNullValue()));
        assertThat(gtx.localTransactionRootKey, is(ancestorKey));
        assertThat(gtx.lockMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void queryUsingAncestorKeyWithGlobalTransaction() throws Exception {
        Key ancestorKey = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        gtx.getAsMap(key2);
        KindlessQuery query = gtx.query(ancestorKey);
        assertThat(query, is(notNullValue()));
        assertThat(query.tx, is(nullValue()));
        assertThat(gtx.localTransactionRootKey, is(key2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(ancestorKey), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putEntityAsLocalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Entity entity = new Entity(key);
        Transaction tx = Datastore.beginTransaction();
        assertThat(gtx.put(entity), is(entity.getKey()));
        tx.commit();
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(Datastore.query(key).count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putEntityForSameRootKeyAsLocalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        gtx.getAsMap(rootKey);
        Entity entity = new Entity(key);
        Transaction tx = Datastore.beginTransaction();
        assertThat(gtx.put(entity), is(entity.getKey()));
        tx.commit();
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(Datastore.query(key).count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putEntityAsGlobalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key rootKey2 = Datastore.createKey("Parent", 2);
        Key key = Datastore.createKey(rootKey, "Hoge", 1);
        Key key2 = Datastore.createKey(rootKey2, "Hoge", 2);
        gtx.getAsMap(key2);
        Entity entity = new Entity(key);
        Transaction tx = Datastore.beginTransaction();
        assertThat(gtx.put(entity), is(entity.getKey()));
        tx.commit();
        assertThat(gtx.localTransactionRootKey, is(rootKey2));
        assertThat(Datastore.query(key).count(), is(0));
        assertThat(gtx.lockMap.size(), is(1));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(lock.globalTransactionKey, is(gtx.globalTransactionKey));
        assertThat(lock.timestamp, is(not(0L)));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.get(key), is(entity));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putModel() throws Exception {
        Hoge hoge = new Hoge();
        assertThat(gtx.put(hoge), is(hoge.getKey()));
        assertThat(gtx.localTransactionRootKey, is(hoge.getKey()));
        assertThat(Datastore.query(hoge.getKey()).count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putModelAndEntity() throws Exception {
        Hoge hoge = new Hoge();
        Entity entity = new Entity("Hoge");
        List<Key> keys = gtx.put(Arrays.asList(hoge, entity));
        assertThat(keys, is(notNullValue()));
        assertThat(keys.size(), is(2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(entity.getKey()), is(notNullValue()));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.get(entity.getKey()), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putModelAndEntityVarargs() throws Exception {
        Hoge hoge = new Hoge();
        Entity entity = new Entity("Hoge");
        List<Key> keys = gtx.put(hoge, entity);
        assertThat(keys, is(notNullValue()));
        assertThat(keys.size(), is(2));
        assertThat(gtx.lockMap.size(), is(1));
        assertThat(gtx.lockMap.get(entity.getKey()), is(notNullValue()));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.get(entity.getKey()), is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteEntityAsLocalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Datastore.putWithoutTx(new Entity(key));
        gtx.delete(key);
        assertThat(Datastore.query("Child").count(), is(1));
        gtx.localTransaction.commit();
        assertThat(Datastore.query("Child").count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteEntityAsLocalTransactionForSameRootKey() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Datastore.putWithoutTx(new Entity(key));
        gtx.getAsMap(key);
        gtx.delete(key);
        assertThat(Datastore.query("Child").count(), is(1));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteEntityAsGlobalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Key key2 = Datastore.createKey("Hoge", 1);
        gtx.put(new Entity(key2));
        gtx.delete(key);
        assertThat(gtx.lockMap.size(), is(1));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(lock.globalTransactionKey, is(gtx.globalTransactionKey));
        assertThat(lock.timestamp, is(not(0L)));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.get(key), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteEntities() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        gtx.delete(Arrays.asList(key, key2));
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(gtx.lockMap.size(), is(1));
        Lock lock = gtx.lockMap.get(key2);
        assertThat(lock, is(notNullValue()));
        assertThat(lock.globalTransactionKey, is(gtx.globalTransactionKey));
        assertThat(lock.timestamp, is(not(0L)));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.containsKey(key2), is(true));
        assertThat(gtx.journalMap.get(key2), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteEntitiesVarargs() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        Key key2 = Datastore.createKey("Hoge", 2);
        Datastore.putWithoutTx(new Entity(key));
        gtx.delete(key, key2);
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(gtx.lockMap.size(), is(1));
        Lock lock = gtx.lockMap.get(key2);
        assertThat(lock, is(notNullValue()));
        assertThat(lock.globalTransactionKey, is(gtx.globalTransactionKey));
        assertThat(lock.timestamp, is(not(0L)));
        assertThat(gtx.journalMap.size(), is(1));
        assertThat(gtx.journalMap.containsKey(key2), is(true));
        assertThat(gtx.journalMap.get(key2), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteAllAsLocalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Key key2 = Datastore.createKey(key, "Grandchild", 1);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        gtx.deleteAll(key);
        assertThat(Datastore.query("Child").count(), is(1));
        assertThat(Datastore.query("Grandchild").count(), is(1));
        gtx.localTransaction.commit();
        assertThat(Datastore.query("Child").count(), is(0));
        assertThat(Datastore.query("Grandchild").count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteAllAsLocalTransactionForSameRootKey() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Key key2 = Datastore.createKey(key, "Grandchild", 1);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        gtx.getAsMap(rootKey);
        gtx.deleteAll(key);
        assertThat(Datastore.query("Child").count(), is(1));
        assertThat(Datastore.query("Grandchild").count(), is(1));
        gtx.localTransaction.commit();
        assertThat(Datastore.query("Child").count(), is(0));
        assertThat(Datastore.query("Grandchild").count(), is(0));
        assertThat(gtx.lockMap.size(), is(0));
        assertThat(gtx.journalMap.size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void deleteAllAsGlobalTransaction() throws Exception {
        Key rootKey = Datastore.createKey("Parent", 1);
        Key key = Datastore.createKey(rootKey, "Child", 1);
        Key key2 = Datastore.createKey(rootKey, "Child", 2);
        Key key3 = Datastore.createKey("Hoge", 1);
        Datastore.putWithoutTx(new Entity(key));
        Datastore.putWithoutTx(new Entity(key2));
        gtx.getAsMap(key3);
        gtx.deleteAll(rootKey);
        assertThat(gtx.lockMap.size(), is(1));
        Lock lock = gtx.lockMap.get(rootKey);
        assertThat(lock, is(notNullValue()));
        assertThat(lock.globalTransactionKey, is(gtx.globalTransactionKey));
        assertThat(lock.timestamp, is(not(0L)));
        assertThat(gtx.journalMap.size(), is(2));
        assertThat(gtx.journalMap.containsKey(key), is(true));
        assertThat(gtx.journalMap.get(key), is(nullValue()));
        assertThat(gtx.journalMap.containsKey(key2), is(true));
        assertThat(gtx.journalMap.get(key2), is(nullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void isLocalTransaction() throws Exception {
        assertThat(gtx.isLocalTransaction(), is(true));
        gtx.put(new Entity("Hoge"));
        assertThat(gtx.isLocalTransaction(), is(true));
        gtx.put(new Entity("Hoge"));
        assertThat(gtx.isLocalTransaction(), is(false));
    }

    /**
     * @throws Exception
     */
    @Test
    public void commitLocalTransaction() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.commitLocalTransaction();
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void commitGlobalTransactionInternally() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.commitGlobalTransactionInternally();
        Entity entity = Datastore.get(gtx.globalTransactionKey);
        assertThat(entity, is(notNullValue()));
        assertThat((Boolean) entity
            .getProperty(GlobalTransaction.VALID_PROPERTY), is(true));
        assertThat(gtx.isActive(), is(false));
        assertThat(gtx.localTransaction.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
        assertThat(Datastore.query("Hoge").count(), is(1));
        String encodedKey = Datastore.keyToString(gtx.globalTransactionKey);
        assertThat(tester.tasks.size(), is(1));
        TaskQueueAddRequest task = tester.tasks.get(0);
        assertThat(task.getQueueName(), is(GlobalTransaction.QUEUE_NAME));
        assertThat(task.getUrl(), is(GlobalTransactionServlet.SERVLET_PATH));
        assertThat(task.getBody(), is(GlobalTransactionServlet.COMMAND_NAME
            + "="
            + GlobalTransactionServlet.ROLLFORWARD_COMMAND
            + "&"
            + GlobalTransactionServlet.KEY_NAME
            + "="
            + encodedKey));
    }

    /**
     * @throws Exception
     */
    @Test
    public void toEntity() throws Exception {
        Entity entity = gtx.toEntity();
        assertThat(entity, is(notNullValue()));
        assertThat((Boolean) entity
            .getProperty(GlobalTransaction.VALID_PROPERTY), is(gtx.valid));
    }

    /**
     * @throws Exception
     */
    @Test
    public void setLocalTransactionRootKey() throws Exception {
        Key rootKey = Datastore.createKey("Hoge", 1);
        gtx.setLocalTransactionRootKey(rootKey);
        assertThat(gtx.localTransactionRootKey, is(rootKey));
        assertThat(gtx.globalTransactionKey, is(notNullValue()));
    }

    /**
     * @throws Exception
     */
    @Test
    public void commitGlobalTransaction() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.put(new Entity("Hoge2"));
        gtx.commitGlobalTransaction();
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(Datastore.query("Hoge2").count(), is(1));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void putJournals() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.put(new Entity("Hoge2"));
        gtx.putJournals();
        assertThat(Datastore.query(Journal.KIND).count(), is(1));
    }

    /**
     * @throws Exception
     */
    @Test
    public void commit() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.put(new Entity("Hoge2"));
        gtx.commit();
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(Datastore.query("Hoge2").count(), is(1));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollbackLocalTransaction() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.rollbackLocalTransaction();
        assertThat(Datastore.query("Hoge").count(), is(0));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollbackGlobalTransaction() throws Exception {
        gtx.put(new Entity("Hoge"));
        Journal.put(gtx.globalTransactionKey, gtx.journalMap);
        gtx.rollbackGlobalTransaction();
        assertThat(Datastore.query("Hoge").count(), is(0));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollback() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.put(new Entity("Hoge"));
        Journal.put(gtx.globalTransactionKey, gtx.journalMap);
        gtx.rollback();
        assertThat(Datastore.query("Hoge").count(), is(0));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollbackAsyncGlobalTransaction() throws Exception {
        gtx.setLocalTransactionRootKey(Datastore.createKey("Hoge", 1));
        String encodedKey = Datastore.keyToString(gtx.globalTransactionKey);
        gtx.rollbackAsyncGlobalTransaction();
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
        assertThat(tester.tasks.size(), is(1));
        TaskQueueAddRequest task = tester.tasks.get(0);
        assertThat(task.getQueueName(), is(GlobalTransaction.QUEUE_NAME));
        assertThat(task.getUrl(), is(GlobalTransactionServlet.SERVLET_PATH));
        assertThat(task.getBody(), is(GlobalTransactionServlet.COMMAND_NAME
            + "="
            + GlobalTransactionServlet.ROLLBACK_COMMAND
            + "&"
            + GlobalTransactionServlet.KEY_NAME
            + "="
            + encodedKey));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollbackAsync() throws Exception {
        gtx.put(new Entity("Hoge"));
        gtx.put(new Entity("Hoge"));
        String encodedKey = Datastore.keyToString(gtx.globalTransactionKey);
        gtx.rollbackAsyncGlobalTransaction();
        assertThat(gtx.isActive(), is(false));
        assertThat(GlobalTransaction.getActiveTransactions().size(), is(0));
        assertThat(tester.tasks.size(), is(1));
        TaskQueueAddRequest task = tester.tasks.get(0);
        assertThat(task.getQueueName(), is(GlobalTransaction.QUEUE_NAME));
        assertThat(task.getUrl(), is(GlobalTransactionServlet.SERVLET_PATH));
        assertThat(task.getBody(), is(GlobalTransactionServlet.COMMAND_NAME
            + "="
            + GlobalTransactionServlet.ROLLBACK_COMMAND
            + "&"
            + GlobalTransactionServlet.KEY_NAME
            + "="
            + encodedKey));
    }

    /**
     * @throws Exception
     */
    @Test
    public void submitRollForwardJob() throws Exception {
        gtx.getAsMap(Datastore.createKey("Hoge", 1));
        String encodedKey = Datastore.keyToString(gtx.globalTransactionKey);
        GlobalTransaction.submitRollForwardJob(
            gtx.localTransaction,
            gtx.globalTransactionKey,
            GlobalTransaction.ROLL_FORWARD_DELAY);
        assertThat(tester.tasks.size(), is(1));
        TaskQueueAddRequest task = tester.tasks.get(0);
        assertThat(task.getQueueName(), is(GlobalTransaction.QUEUE_NAME));
        assertThat(task.getUrl(), is(GlobalTransactionServlet.SERVLET_PATH));
        assertThat(task.getBody(), is(GlobalTransactionServlet.COMMAND_NAME
            + "="
            + GlobalTransactionServlet.ROLLFORWARD_COMMAND
            + "&"
            + GlobalTransactionServlet.KEY_NAME
            + "="
            + encodedKey));
    }

    /**
     * @throws Exception
     */
    @Test
    public void submitRollbackJob() throws Exception {
        gtx.getAsMap(Datastore.createKey("Hoge", 1));
        String encodedKey = Datastore.keyToString(gtx.globalTransactionKey);
        GlobalTransaction.submitRollbackJob(gtx.globalTransactionKey);
        assertThat(tester.tasks.size(), is(1));
        TaskQueueAddRequest task = tester.tasks.get(0);
        assertThat(task.getQueueName(), is(GlobalTransaction.QUEUE_NAME));
        assertThat(task.getUrl(), is(GlobalTransactionServlet.SERVLET_PATH));
        assertThat(task.getBody(), is(GlobalTransactionServlet.COMMAND_NAME
            + "="
            + GlobalTransactionServlet.ROLLBACK_COMMAND
            + "&"
            + GlobalTransactionServlet.KEY_NAME
            + "="
            + encodedKey));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollForward() throws Exception {
        gtx.put(new Entity("Hoge"));
        Journal.put(gtx.globalTransactionKey, gtx.journalMap);
        gtx.commitGlobalTransactionInternally();
        GlobalTransaction.rollForward(gtx.globalTransactionKey);
        assertThat(Datastore.query("Hoge").count(), is(1));
        assertThat(Datastore.query(GlobalTransaction.KIND).count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void rollbackByGlobalTransactionKey() throws Exception {
        gtx.put(new Entity("Hoge"));
        Journal.put(gtx.globalTransactionKey, gtx.journalMap);
        GlobalTransaction.rollback(gtx.globalTransactionKey);
        assertThat(Datastore.query("Hoge").count(), is(0));
        assertThat(Datastore.query(Lock.KIND).count(), is(0));
        assertThat(Datastore.query(Journal.KIND).count(), is(0));
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyLockSizeWithin100() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(key);
        for (int i = 1; i <= 100; i++) {
            gtx.put(new Entity("Hoge" + i));
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyLockSizeWithin100ForGetAsMap() throws Exception {
        Key key = Datastore.createKey("Hoge", 1);
        gtx.getAsMap(key);
        for (int i = 1; i <= 100; i++) {
            gtx.getAsMap(Datastore.createKey("Hoge" + i, i));
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyJournalSizeWithin500ForPut() throws Exception {
        Key parentKey = Datastore.createKey("Parent", 1);
        Key parentKey2 = Datastore.createKey("Parent", 2);
        gtx.getAsMap(parentKey2);
        for (int i = 1; i <= 500; i++) {
            gtx.put(new Entity(Datastore.createKey(parentKey, "Hoge", i)));
        }
    }

    /**
     * @throws Exception
     */
    @Test
    public void verifyJournalSizeWithin500ForDelete() throws Exception {
        Key parentKey = Datastore.createKey("Parent", 1);
        Key parentKey2 = Datastore.createKey("Parent", 2);
        gtx.getAsMap(parentKey2);
        for (int i = 1; i <= 500; i++) {
            gtx.delete(Datastore.createKey(parentKey, "Hoge", i));
        }
    }
}