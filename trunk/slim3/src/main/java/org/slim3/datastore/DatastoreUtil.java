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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.slim3.util.AppEngineUtil;
import org.slim3.util.ClassUtil;
import org.slim3.util.Cleanable;
import org.slim3.util.Cleaner;

import com.google.appengine.api.datastore.AsyncDatastoreService;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.EntityTranslator;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.KeyRange;
import com.google.appengine.api.datastore.KeyUtil;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.utils.FutureWrapper;
import com.google.apphosting.api.ApiProxy;
import com.google.apphosting.api.DatastorePb.GetSchemaRequest;
import com.google.apphosting.api.DatastorePb.Schema;
import com.google.storage.onestore.v3.OnestoreEntity.EntityProto;
import com.google.storage.onestore.v3.OnestoreEntity.Reference;
import com.google.storage.onestore.v3.OnestoreEntity.Path.Element;

/**
 * A utility for {@link DatastoreService}.
 * 
 * @author higa
 * @since 1.0.0
 * 
 */
public final class DatastoreUtil {

    /**
     * The maximum size(bytes) of entity.
     */
    public static final int MAX_ENTITY_SIZE = 1000000;

    /**
     * The maximum number of entities.
     */
    public static final int MAX_NUMBER_OF_ENTITIES = 500;

    /**
     * The extra size.
     */
    public static final int EXTRA_SIZE = 200;

    private static final int KEY_CACHE_SIZE = 50;

    private static final String DATASTORE_SERVICE = "datastore_v3";

    private static final String GET_SCHEMA_METHOD = "GetSchema";

    /**
     * The cache for {@link ModelMeta}.
     */
    protected static ConcurrentHashMap<String, ModelMeta<?>> modelMetaCache =
        new ConcurrentHashMap<String, ModelMeta<?>>(87);

    /**
     * The cache for the result of allocateIds().
     */
    protected static ConcurrentHashMap<String, Iterator<Key>> keysCache =
        new ConcurrentHashMap<String, Iterator<Key>>(87);

    private static volatile boolean initialized = false;

    static {
        initialize();
    }

    private static void initialize() {
        Cleaner.add(new Cleanable() {
            public void clean() {
                modelMetaCache.clear();
                initialized = false;
            }
        });
        initialized = true;
    }

    /**
     * Clears the active transactions.
     */
    public static void clearActiveGlobalTransactions() {
        GlobalTransaction.clearActiveTransactions();
    }

    /**
     * Allocates a key within a namespace defined by the kind.
     * 
     * @param ds
     *            the datastore service
     * @param kind
     *            the kind
     * @return a key within a namespace defined by the kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the kind parameter is null
     */
    public static Key allocateId(DatastoreService ds, String kind)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        Iterator<Key> keys = keysCache.get(kind);
        if (keys != null && keys.hasNext()) {
            return keys.next();
        }
        keys = allocateIds(ds, kind, KEY_CACHE_SIZE).iterator();
        keysCache.put(kind, keys);
        return keys.next();
    }

    /**
     * Allocates a key within a namespace defined by the kind asynchronously.
     * 
     * @param ds
     *            the asynchronous datastore service
     * @param kind
     *            the kind
     * @return a future key within a namespace defined by the kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the kind parameter is null
     */
    public static Future<Key> allocateIdAsync(AsyncDatastoreService ds,
            final String kind) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        return new FutureWrapper<KeyRange, Key>(allocateIdsAsync(ds, kind, 1)) {

            @Override
            protected Throwable convertException(Throwable throwable) {
                return throwable;
            }

            @Override
            protected Key wrap(KeyRange keyRange) throws Exception {
                return keyRange.iterator().next();
            }
        };
    }

    /**
     * Allocates a key within a namespace defined by the parent key and the
     * kind.
     * 
     * @param ds
     *            the datastore service
     * @param parentKey
     *            the parent key
     * @param kind
     *            the kind
     * 
     * @return a key within a namespace defined by the kind and the parent key
     * @throws NullPointerException
     *             if the ds parameter is null or if the parentKey parameter is
     *             null or if the kind parameter is null
     */
    public static Key allocateId(DatastoreService ds, Key parentKey, String kind)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (parentKey == null) {
            throw new NullPointerException(
                "The parentKey parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        Iterator<Key> keys = allocateIds(ds, parentKey, kind, 1).iterator();
        return keys.next();
    }

    /**
     * Allocates a key within a namespace defined by the parent key and the kind
     * asynchronously.
     * 
     * @param ds
     *            the asynchronous datastore service
     * @param parentKey
     *            the parent key
     * @param kind
     *            the kind
     * 
     * @return a key within a namespace defined by the kind and the parent key
     * @throws NullPointerException
     *             if the ds parameter is null or if the parentKey parameter is
     *             null or if the kind parameter is null
     */
    public static Future<Key> allocateIdAsync(AsyncDatastoreService ds,
            Key parentKey, String kind) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (parentKey == null) {
            throw new NullPointerException(
                "The parentKey parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        return new FutureWrapper<KeyRange, Key>(allocateIdsAsync(
            ds,
            parentKey,
            kind,
            1)) {

            @Override
            protected Throwable convertException(Throwable throwable) {
                return throwable;
            }

            @Override
            protected Key wrap(KeyRange keyRange) throws Exception {
                return keyRange.iterator().next();
            }
        };
    }

    /**
     * Allocates keys within a namespace defined by the kind.
     * 
     * @param ds
     *            the datastore service
     * @param kind
     *            the kind
     * @param num
     *            the number of allocated keys
     * @return keys within a namespace defined by the kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the kind parameter is null
     */
    public static KeyRange allocateIds(DatastoreService ds, String kind,
            long num) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        return ds.allocateIds(kind, num);
    }

    /**
     * Allocates keys within a namespace defined by the kind asynchronously.
     * 
     * @param ds
     *            the asynchronous datastore service
     * @param kind
     *            the kind
     * @param num
     *            the number of allocated keys
     * @return future keys within a namespace defined by the kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the kind parameter is null
     */
    public static Future<KeyRange> allocateIdsAsync(AsyncDatastoreService ds,
            String kind, long num) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException(
                "The kind parameter must not be null.");
        }
        return ds.allocateIds(kind, num);
    }

    /**
     * Allocates keys within a namespace defined by the parent key and the kind.
     * 
     * @param ds
     *            the datastore service
     * @param parentKey
     *            the parent key
     * @param kind
     *            the kind
     * @param num
     * @return keys within a namespace defined by the parent key and the kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the parentKey parameter is
     *             null or if the kind parameter is null
     */
    public static KeyRange allocateIds(DatastoreService ds, Key parentKey,
            String kind, long num) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (parentKey == null) {
            throw new NullPointerException(
                "The parentKey parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException("The kind parameter is null.");
        }
        return ds.allocateIds(parentKey, kind, num);
    }

    /**
     * Allocates keys within a namespace defined by the parent key and the kind
     * asynchronously.
     * 
     * @param ds
     *            the asynchronous datastore service
     * @param parentKey
     *            the parent key
     * @param kind
     *            the kind
     * @param num
     * @return future keys within a namespace defined by the parent key and the
     *         kind
     * @throws NullPointerException
     *             if the ds parameter is null or if the parentKey parameter is
     *             null or if the kind parameter is null
     */
    public static Future<KeyRange> allocateIdsAsync(AsyncDatastoreService ds,
            Key parentKey, String kind, long num) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (parentKey == null) {
            throw new NullPointerException(
                "The parentKey parameter must not be null.");
        }
        if (kind == null) {
            throw new NullPointerException("The kind parameter is null.");
        }
        return ds.allocateIds(parentKey, kind, num);
    }

    /**
     * Assigns a new key to the entity if necessary.
     * 
     * @param ds
     *            the datastore service
     * @param entity
     *            the entity
     * @throws NullPointerException
     *             if the ds parameter is null or if the entity parameter is
     *             null
     */
    public static void assignKeyIfNecessary(DatastoreService ds, Entity entity)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (entity == null) {
            throw new NullPointerException(
                "The entity parameter must not be null.");
        }
        if (!entity.getKey().isComplete()) {
            long id =
                entity.getParent() == null ? allocateId(ds, entity.getKind())
                    .getId() : allocateId(
                    ds,
                    entity.getParent(),
                    entity.getKind()).getId();
            KeyUtil.setId(entity.getKey(), id);
        }
    }

    /**
     * Assigns a new key to the entity if necessary.
     * 
     * @param ds
     *            the datastore service
     * @param entities
     *            the entities
     * @throws NullPointerException
     *             if the ds parameter is null or if the entities parameter is
     *             null
     */
    public static void assignKeyIfNecessary(DatastoreService ds,
            Iterable<Entity> entities) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (entities == null) {
            throw new NullPointerException(
                "The entities parameter must not be null.");
        }
        for (Entity e : entities) {
            assignKeyIfNecessary(ds, e);
        }
    }

    /**
     * Returns an entity specified by the key. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param ds
     *            the datastore service
     * @param key
     *            the key
     * @return an entity specified by the key
     * @throws NullPointerException
     *             if the ds parameter is null or if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static Entity get(DatastoreService ds, Key key)
            throws NullPointerException, EntityNotFoundRuntimeException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (key == null) {
            throw new NullPointerException(
                "The key parameter must not be null.");
        }
        try {
            return ds.get(key);
        } catch (EntityNotFoundException cause) {
            throw new EntityNotFoundRuntimeException(key, cause);
        }
    }

    /**
     * Returns an entity specified by the key asynchronously. If there is a
     * current transaction, this operation will execute within that transaction.
     * 
     * @param ds
     *            the datastore service
     * @param key
     *            the key
     * @return a future entity specified by the key
     * @throws NullPointerException
     *             if the ds parameter is null or if the key parameter is null
     * 
     */
    public static Future<Entity> getAsync(AsyncDatastoreService ds,
            final Key key) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return getAsync(ds, ds.getCurrentTransaction(null), key);
    }

    /**
     * Returns an entity specified by the key within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param key
     *            the key
     * @return an entity specified by the key
     * @throws NullPointerException
     *             if the ds parameter is null or if the key parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static Entity get(DatastoreService ds, Transaction tx, Key key)
            throws NullPointerException, IllegalStateException,
            EntityNotFoundRuntimeException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (key == null) {
            throw new NullPointerException(
                "The key parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        try {
            return ds.get(tx, key);
        } catch (EntityNotFoundException cause) {
            throw new EntityNotFoundRuntimeException(key, cause);
        }
    }

    /**
     * Returns an entity specified by the key within the provided transaction
     * asynchronously.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param key
     *            the key
     * @return a future entity specified by the key
     * @throws NullPointerException
     *             if the ds parameter is null or if the key parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Future<Entity> getAsync(AsyncDatastoreService ds,
            Transaction tx, final Key key) throws NullPointerException,
            IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (key == null) {
            throw new NullPointerException(
                "The key parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        return new FutureWrapper<Entity, Entity>(ds.get(tx, key)) {

            @Override
            protected Throwable convertException(Throwable throwable) {
                if (throwable instanceof EntityNotFoundException) {
                    return new EntityNotFoundRuntimeException(key, throwable);
                }
                return throwable;
            }

            @Override
            protected Entity wrap(Entity entity) throws Exception {
                return entity;
            }
        };
    }

    /**
     * Returns entities specified by the keys as map. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param ds
     *            the datastore service
     * @param keys
     *            the keys
     * @return entities specified by the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     */
    public static Map<Key, Entity> getAsMap(DatastoreService ds,
            Iterable<Key> keys) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        return ds.get(keys);
    }

    /**
     * Returns entities specified by the keys as map asynchronously. If there is
     * a current transaction, this operation will execute within that
     * transaction.
     * 
     * @param ds
     *            the datastore service
     * @param keys
     *            the keys
     * @return future entities specified by the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     */
    public static Future<Map<Key, Entity>> getAsMapAsync(
            AsyncDatastoreService ds, Iterable<Key> keys)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return getAsMapAsync(ds, ds.getCurrentTransaction(null), keys);
    }

    /**
     * Returns entities specified by the keys as map within the provided
     * transaction.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return entities specified by the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Map<Key, Entity> getAsMap(DatastoreService ds,
            Transaction tx, Iterable<Key> keys) throws NullPointerException,
            IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        return ds.get(tx, keys);
    }

    /**
     * Returns entities specified by the keys as map within the provided
     * transaction asynchronously.
     * 
     * @param ds
     *            the asynchronous datastore service
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return future entities specified by the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Future<Map<Key, Entity>> getAsMapAsync(
            AsyncDatastoreService ds, Transaction tx, Iterable<Key> keys)
            throws NullPointerException, IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        return new FutureWrapper<Map<Key, Entity>, Map<Key, Entity>>(ds.get(
            tx,
            keys)) {

            @Override
            protected Throwable convertException(Throwable throwable) {
                return throwable;
            }

            @Override
            protected Map<Key, Entity> wrap(Map<Key, Entity> map)
                    throws Exception {
                return map;
            }
        };
    }

    /**
     * Puts the entity to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param ds
     *            the datastore service
     * @param entity
     *            the entity
     * 
     * @return a key
     * @throws NullPointerException
     *             if the ds parameter is null or if the entity parameter is
     *             null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Key put(DatastoreService ds, Entity entity)
            throws NullPointerException, IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return put(ds, ds.getCurrentTransaction(null), entity);
    }

    /**
     * Puts the entity to datastore asynchronously. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param ads
     *            the asynchronous datastore service
     * @param ds
     *            the datastore service
     * @param entity
     *            the entity
     * 
     * @return a future key
     * @throws NullPointerException
     *             if the ads parameter is null or if the ds parameter is null
     *             or if the entity parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Future<Key> putAsync(AsyncDatastoreService ads,
            DatastoreService ds, Entity entity) throws NullPointerException,
            IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return putAsync(ads, ds, ds.getCurrentTransaction(null), entity);
    }

    /**
     * Puts the entity to datastore within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param entity
     *            the entity
     * 
     * @return a key
     * @throws NullPointerException
     *             if the ds parameter is null or if the entity parameter is
     *             null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Key put(DatastoreService ds, Transaction tx, Entity entity)
            throws NullPointerException, IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        assignKeyIfNecessary(ds, entity);
        return ds.put(tx, entity);
    }

    /**
     * Puts the entity to datastore within the provided transaction
     * asynchronously.
     * 
     * @param ads
     *            the asynchronous datastore service
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param entity
     *            the entity
     * 
     * @return a future key
     * @throws NullPointerException
     *             if the ads parameter is null or if the ds parameter is null
     *             or if the entity parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Future<Key> putAsync(AsyncDatastoreService ads,
            DatastoreService ds, Transaction tx, Entity entity)
            throws NullPointerException, IllegalStateException {
        if (ads == null) {
            throw new NullPointerException(
                "The ads parameter must not be null.");
        }
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        assignKeyIfNecessary(ds, entity);
        return ads.put(tx, entity);
    }

    /**
     * Puts the entities to datastore within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param entities
     *            the entities
     * @return a list of keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the entities parameter is
     *             null
     */
    public static List<Key> put(DatastoreService ds, Iterable<Entity> entities)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return put(ds, ds.getCurrentTransaction(null), entities);
    }

    /**
     * Puts the entities to datastore within the provided transaction
     * asynchronously.
     * 
     * @param ads
     *            the asynchronous datastore service
     * @param ds
     *            the datastore service
     * @param entities
     *            the entities
     * @return a future list of keys
     * @throws NullPointerException
     *             if the ads parameter is null or if the ds parameter is null
     *             or if the entities parameter is null
     */
    public static Future<List<Key>> putAsync(AsyncDatastoreService ads,
            DatastoreService ds, Iterable<Entity> entities)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        return putAsync(ads, ds, ds.getCurrentTransaction(null), entities);
    }

    /**
     * Puts the entities to datastore within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param entities
     *            the entities
     * 
     * @return a list of keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the entities parameter is
     *             null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static List<Key> put(DatastoreService ds, Transaction tx,
            Iterable<Entity> entities) throws NullPointerException,
            IllegalStateException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (entities == null) {
            throw new NullPointerException(
                "The entities parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        assignKeyIfNecessary(ds, entities);
        return ds.put(tx, entities);
    }

    /**
     * Puts the entities to datastore within the provided transaction
     * asynchronously.
     * 
     * @param ads
     *            the asynchronous datastore service
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param entities
     *            the entities
     * 
     * @return a future list of keys
     * @throws NullPointerException
     *             if the ads parameter is null or if the ds parameter is null
     *             or if the entities parameter is null
     * @throws IllegalStateException
     *             if the transaction is not null and the transaction is not
     *             active
     */
    public static Future<List<Key>> putAsync(AsyncDatastoreService ads,
            DatastoreService ds, Transaction tx, Iterable<Entity> entities)
            throws NullPointerException, IllegalStateException {
        if (ads == null) {
            throw new NullPointerException(
                "The ads parameter must not be null.");
        }
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (entities == null) {
            throw new NullPointerException(
                "The entities parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        assignKeyIfNecessary(ds, entities);
        return ads.put(tx, entities);
    }

    /**
     * Deletes entities specified by the keys within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     */
    public static void delete(DatastoreService ds, Iterable<Key> keys)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        delete(ds, ds.getCurrentTransaction(null), keys);
    }

    /**
     * Deletes entities specified by the keys within the provided transaction.
     * 
     * @param ds
     *            the datastore service
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the ds parameter is null or if the keys parameter is null
     */
    public static void delete(DatastoreService ds, Transaction tx,
            Iterable<Key> keys) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        if (tx != null && !tx.isActive()) {
            throw new IllegalStateException("The transaction must be active.");
        }
        ds.delete(tx, keys);
    }

    /**
     * Filters the list in memory.
     * 
     * @param <M>
     *            the model type
     * @param list
     *            the model list
     * @param criteria
     *            the filter criteria
     * @return the filtered list.
     * @throws NullPointerException
     *             if the list parameter is null or if the criteria parameter is
     *             null or if the model of the list is null
     */
    public static <M> List<M> filterInMemory(List<M> list,
            List<? extends InMemoryFilterCriterion> criteria)
            throws NullPointerException {
        if (list == null) {
            throw new NullPointerException(
                "The list parameter must not be null.");
        }
        if (criteria == null) {
            throw new NullPointerException(
                "The criteria parameter must not be null.");
        }
        if (criteria.size() == 0) {
            return list;
        }
        List<M> newList = new ArrayList<M>(list.size());
        for (M model : list) {
            if (model == null) {
                throw new NullPointerException(
                    "The element of list must not be null.");
            }
            if (accept(model, criteria)) {
                newList.add(model);
            }
        }
        return newList;
    }

    private static boolean accept(Object model,
            List<? extends InMemoryFilterCriterion> criteria) {
        for (InMemoryFilterCriterion c : criteria) {
            if (c == null) {
                continue;
            }
            if (!c.accept(model)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sorts the list in memory.
     * 
     * @param <M>
     *            the model type
     * @param list
     *            the model list
     * @param criteria
     *            criteria to sort
     * @return the sorted list
     * @throws NullPointerException
     *             if the list parameter is null of if the criteria parameter is
     *             null
     */
    public static <M> List<M> sortInMemory(List<M> list,
            List<InMemorySortCriterion> criteria) throws NullPointerException {
        if (list == null) {
            throw new NullPointerException(
                "The list parameter must not be null.");
        }
        if (criteria == null) {
            throw new NullPointerException(
                "The criteria parameter must not be null.");
        }
        if (criteria.size() == 0) {
            return list;
        }
        Collections.sort(list, new AttributeComparator(criteria));
        return list;
    }

    /**
     * Returns a meta data of the model
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @return a meta data of the model
     * @throws NullPointerException
     *             if the modelClass parameter is null
     */
    @SuppressWarnings("unchecked")
    public static <M> ModelMeta<M> getModelMeta(Class<M> modelClass)
            throws NullPointerException {
        if (modelClass == null) {
            throw new NullPointerException(
                "The modelClass parameter must not be null.");
        }
        if (!initialized) {
            initialize();
        }
        ModelMeta<M> modelMeta =
            (ModelMeta<M>) modelMetaCache.get(modelClass.getName());
        if (modelMeta != null) {
            return modelMeta;
        }
        modelMeta = createModelMeta(modelClass);
        ModelMeta<?> old =
            modelMetaCache.putIfAbsent(modelClass.getName(), modelMeta);
        return old != null ? (ModelMeta<M>) old : modelMeta;
    }

    /**
     * Returns a meta data of the model
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param entity
     *            the entity
     * @return a meta data of the model
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the entity parameter
     *             is null
     * @throws IllegalArgumentException
     *             if the model class is not assignable from entity class
     */
    @SuppressWarnings("unchecked")
    public static <M> ModelMeta<M> getModelMeta(ModelMeta<M> modelMeta,
            Entity entity) throws NullPointerException,
            IllegalArgumentException {
        if (modelMeta == null) {
            throw new NullPointerException(
                "The modelMeta parameter must not be null.");
        }
        if (entity == null) {
            throw new NullPointerException(
                "The entity parameter must not be null.");
        }
        List<String> classHierarchyList =
            (List<String>) entity.getProperty(modelMeta
                .getClassHierarchyListName());
        if (classHierarchyList == null) {
            return modelMeta;
        }
        Class<M> subModelClass =
            ClassUtil.forName(classHierarchyList
                .get(classHierarchyList.size() - 1));
        if (!modelMeta.getModelClass().isAssignableFrom(subModelClass)) {
            throw new IllegalArgumentException("The model class("
                + modelMeta.getModelClass().getName()
                + ") is not assignable from entity class("
                + subModelClass.getName()
                + ").");
        }
        return getModelMeta(subModelClass);
    }

    /**
     * Creates a meta data of the model
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @return a meta data of the model
     */
    public static <M> ModelMeta<M> createModelMeta(Class<M> modelClass) {
        try {
            String className = modelClass.getName();
            className = replacePackageName(className, "model", "meta");
            className =
                replacePackageName(className, "shared", "server") + "Meta";
            return ClassUtil.newInstance(className, Thread
                .currentThread()
                .getContextClassLoader());
        } catch (Throwable cause) {
            throw new IllegalArgumentException("The meta data of the model("
                + modelClass.getName()
                + ") is not found.");
        }
    }

    /**
     * Replaces a package name with another one.
     * 
     * @param className
     *            the class name
     * @param fromPackageName
     *            the "from" package name
     * @param toPackageName
     *            the "to" package name
     * @return the converted class name
     * @throws NullPointerException
     *             if the className parameter is null or if the fromPackageName
     *             is null or if the toPackageName parameter is null
     */
    protected static String replacePackageName(String className,
            String fromPackageName, String toPackageName)
            throws NullPointerException {
        if (className == null) {
            throw new NullPointerException(
                "The className parameter must not be null.");
        }
        if (fromPackageName == null) {
            throw new NullPointerException(
                "The fromPackageName parameter must not be null.");
        }
        if (toPackageName == null) {
            throw new NullPointerException(
                "The toPackageName parameter must not be null.");
        }
        int index = className.lastIndexOf("." + fromPackageName + ".");
        if (index < 0) {
            return className;
        }
        return className.substring(0, index)
            + "."
            + toPackageName
            + "."
            + className.substring(index + fromPackageName.length() + 2);
    }

    /**
     * Converts the entity to an array of bytes.
     * 
     * @param entity
     *            the entity
     * @return an array of bytes
     * @throws NullPointerException
     *             if the entity parameter is null
     */
    public static byte[] entityToBytes(Entity entity)
            throws NullPointerException {
        if (entity == null) {
            throw new NullPointerException(
                "The entity parameter must not be null.");
        }
        EntityProto pb = EntityTranslator.convertToPb(entity);
        byte[] buf = new byte[pb.encodingSize()];
        pb.outputTo(buf, 0);
        return buf;
    }

    /**
     * Converts the array of bytes to an entity.
     * 
     * @param bytes
     *            the array of bytes
     * @return an entity
     * @throws NullPointerException
     *             if the bytes parameter is null
     */
    public static Entity bytesToEntity(byte[] bytes)
            throws NullPointerException {
        if (bytes == null) {
            throw new NullPointerException(
                "The bytes parameter must not be null.");
        }
        EntityProto pb = new EntityProto();
        pb.mergeFrom(bytes);
        return EntityTranslator.createFromPb(pb);
    }

    /**
     * Converts the reference to a key.
     * 
     * @param reference
     *            the reference object
     * @return a key
     * @throws NullPointerException
     *             if the reference parameter is null
     */
    public static Key referenceToKey(Reference reference)
            throws NullPointerException {
        if (reference == null) {
            throw new NullPointerException(
                "The reference parameter must not be null.");
        }
        Key key = null;
        for (Element e : reference.getPath().elements()) {
            String kind = e.getType();
            long id = e.getId();
            String name = e.getName();
            if (key == null) {
                if (id != 0) {
                    key = KeyFactory.createKey(kind, id);
                } else {
                    key = KeyFactory.createKey(kind, name);
                }
            } else {
                if (id != 0) {
                    key = KeyFactory.createKey(key, kind, id);
                } else {
                    key = KeyFactory.createKey(key, kind, name);
                }
            }
        }
        if (key == null) {
            throw new IllegalArgumentException("The reference("
                + reference
                + ") cannot be converted to Key.");
        }
        return key;
    }

    /**
     * Converts the model to an entity.
     * 
     * @param ds
     *            the datastore service
     * @param model
     *            the model
     * @return an entity
     * @throws NullPointerException
     *             if the ds parameter is null or if the model parameter is null
     */
    public static Entity modelToEntity(DatastoreService ds, Object model)
            throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (model == null) {
            throw new NullPointerException(
                "The model parameter must not be null.");
        }
        ModelMeta<?> modelMeta = getModelMeta(model.getClass());
        Key key = modelMeta.getKey(model);
        if (key == null) {
            key = allocateId(ds, modelMeta.getKind());
            modelMeta.setKey(model, key);
        }
        modelMeta.assignKeyToModelRefIfNecessary(ds, model);
        modelMeta.incrementVersion(model);
        modelMeta.prePut(model);
        return modelMeta.modelToEntity(model);
    }

    /**
     * Converts the models to entities.
     * 
     * @param ds
     *            the datastore service
     * @param models
     *            the models
     * @return entities
     * @throws NullPointerException
     *             if the ds parameter is null or if the models parameter is
     *             null
     */
    public static List<Entity> modelsToEntities(DatastoreService ds,
            Iterable<?> models) throws NullPointerException {
        if (ds == null) {
            throw new NullPointerException("The ds parameter must not be null.");
        }
        if (models == null) {
            throw new NullPointerException(
                "The models parameter must not be null.");
        }
        List<Entity> entities = new ArrayList<Entity>();
        for (Object model : models) {
            if (model instanceof Entity) {
                Entity entity = (Entity) model;
                assignKeyIfNecessary(ds, entity);
                entities.add(entity);
            } else {
                entities.add(modelToEntity(ds, model));
            }
        }
        return entities;
    }

    /**
     * Converts the map of entities to a list of entities.
     * 
     * @param keys
     *            the keys
     * @param map
     *            the map of entities
     * @return a list of entities
     * @throws NullPointerException
     *             if the keys parameter is null or if the map parameter is null
     *             or if the element of keys is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity bound to a key is found
     */
    public static List<Entity> entityMapToEntityList(Iterable<Key> keys,
            Map<Key, Entity> map) throws NullPointerException,
            EntityNotFoundRuntimeException {
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        if (map == null) {
            throw new NullPointerException(
                "The map parameter must not be null.");
        }
        List<Entity> list = new ArrayList<Entity>(map.size());
        for (Key key : keys) {
            if (key == null) {
                throw new NullPointerException(
                    "The element of keys must not be null.");
            }
            Entity entity = map.get(key);
            if (entity == null) {
                throw new EntityNotFoundRuntimeException(key);
            }
            list.add(entity);
        }
        return list;
    }

    /**
     * Converts the map of entities to a list of models.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @param map
     *            the map of entities
     * @return a list of models
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the keys parameter
     *             is null or if the map parameter is null or if the element of
     *             keys is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity bound to a key is found
     */
    public static <M> List<M> entityMapToModelList(ModelMeta<M> modelMeta,
            Iterable<Key> keys, Map<Key, Entity> map)
            throws NullPointerException, EntityNotFoundRuntimeException {
        if (modelMeta == null) {
            throw new NullPointerException(
                "The modelMeta parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        if (map == null) {
            throw new NullPointerException(
                "The map parameter must not be null.");
        }
        List<M> list = new ArrayList<M>(map.size());
        for (Key key : keys) {
            if (key == null) {
                throw new NullPointerException(
                    "The element of keys must not be null.");
            }
            Entity entity = map.get(key);
            if (entity == null) {
                throw new EntityNotFoundRuntimeException(key);
            }
            ModelMeta<M> mm = getModelMeta(modelMeta, entity);
            mm.validateKey(key);
            list.add(mm.entityToModel(entity));
        }
        return list;
    }

    /**
     * Converts the map of entities to a map of models.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param map
     *            the map of entities
     * @return a map of models
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the map parameter is
     *             null
     */
    public static <M> Map<Key, M> entityMapToModelMap(ModelMeta<M> modelMeta,
            Map<Key, Entity> map) throws NullPointerException {
        if (modelMeta == null) {
            throw new NullPointerException(
                "The modelMeta parameter must not be null.");
        }
        if (map == null) {
            throw new NullPointerException(
                "The map parameter must not be null.");
        }
        Map<Key, M> modelMap = new HashMap<Key, M>(map.size());
        for (Key key : map.keySet()) {
            Entity entity = map.get(key);
            ModelMeta<M> mm = getModelMeta(modelMeta, entity);
            mm.validateKey(key);
            modelMap.put(key, mm.entityToModel(entity));
        }
        return modelMap;
    }

    /**
     * Returns a root key.
     * 
     * @param key
     *            the key
     * @return a root key
     */
    public static Key getRoot(Key key) {
        if (key == null) {
            throw new NullPointerException(
                "The key parameter must not be null.");
        }
        while (key != null) {
            Key parent = key.getParent();
            if (parent == null) {
                break;
            }
            key = parent;
        }
        return key;
    }

    /**
     * Returns a schema.
     * 
     * @return a schema
     * @throws IllegalStateException
     *             if this method is called on production server
     */
    public static Schema getSchema() throws IllegalStateException {
        if (AppEngineUtil.isProduction()) {
            throw new IllegalStateException(
                "This method does not work on production server.");
        }
        GetSchemaRequest req = new GetSchemaRequest();
        req.setApp(ApiProxy.getCurrentEnvironment().getAppId());
        byte[] resBuf =
            ApiProxy.makeSyncCall(DATASTORE_SERVICE, GET_SCHEMA_METHOD, req
                .toByteArray());
        Schema schema = new Schema();
        schema.mergeFrom(resBuf);
        return schema;
    }

    /**
     * Returns a list of kinds.
     * 
     * @return a list of kinds
     * @throws IllegalStateException
     *             if this method is called on production server
     */
    public static List<String> getKinds() throws IllegalStateException {
        if (AppEngineUtil.isProduction()) {
            throw new IllegalStateException(
                "This method does not work on production server.");
        }
        Schema schema = getSchema();
        List<EntityProto> entityProtoList = schema.kinds();
        List<String> kindList = new ArrayList<String>(entityProtoList.size());
        for (EntityProto entityProto : entityProtoList) {
            kindList.add(getKind(entityProto.getKey()));
        }
        return kindList;
    }

    /**
     * Returns a leaf kind.
     * 
     * @param key
     *            the key
     * @return a list of kinds
     */
    public static String getKind(Reference key) {
        List<Element> elements = key.getPath().elements();
        return elements.get(elements.size() - 1).getType();
    }

    private DatastoreUtil() {
    }
}