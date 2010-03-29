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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.slim3.util.ThrowableUtil;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.labs.taskqueue.Queue;
import com.google.appengine.api.labs.taskqueue.QueueFactory;
import com.google.appengine.api.labs.taskqueue.TaskOptions;
import com.google.apphosting.api.DeadlineExceededException;

/**
 * The global transaction coordinator. If an error occurs during transaction,
 * this transaction will be rolled back automatically.
 * 
 * @author higa
 * @since 3.0
 * 
 */
public class GlobalTransaction {

    /**
     * The kind of global transaction entity.
     */
    public static final String KIND = "slim3.GlobalTransaction";

    /**
     * The valid property name.
     */
    public static final String VALID_PROPERTY = "valid";

    /**
     * The queue name.
     */
    protected static final String QUEUE_NAME = "slim3-gtx-queue";

    /**
     * The maximum number of locks.
     */
    protected static final int MAX_LOCKS = 100;

    /**
     * The maximum number of journals.
     */
    protected static final int MAX_JOURNALS = 500;

    /**
     * The active global transactions.
     */
    protected static final ThreadLocal<Stack<GlobalTransaction>> activeTransactions =
        new ThreadLocal<Stack<GlobalTransaction>>() {

            @Override
            protected Stack<GlobalTransaction> initialValue() {
                return new Stack<GlobalTransaction>();
            }
        };

    private static final Logger logger =
        Logger.getLogger(GlobalTransaction.class.getName());

    /**
     * The local transaction.
     */
    protected Transaction localTransaction;

    /**
     * The root key of local transaction.
     */
    protected Key localTransactionRootKey;

    /**
     * The global transaction key.
     */
    protected Key globalTransactionKey;

    /**
     * The time-stamp that a process begun.
     */
    protected long timestamp;

    /**
     * The map of {@link Lock}.
     */
    protected Map<Key, Lock> lockMap;

    /**
     * The map of journals.
     */
    protected Map<Key, Entity> journalMap;

    /**
     * Whether this global transaction is valid.
     */
    protected boolean valid = true;

    /**
     * Returns the current transaction.
     * 
     * @return the current transaction
     */
    protected static GlobalTransaction getCurrentTransaction() {
        Stack<GlobalTransaction> stack = activeTransactions.get();
        if (stack.isEmpty()) {
            return null;
        }
        return stack.peek();
    }

    /**
     * Returns the active transactions.
     * 
     * @return the active transactions
     */
    protected static Collection<GlobalTransaction> getActiveTransactions() {
        Stack<GlobalTransaction> stack = activeTransactions.get();
        Stack<GlobalTransaction> copy = new Stack<GlobalTransaction>();
        copy.addAll(stack);
        return copy;
    }

    /**
     * Clears the active transactions.
     */
    protected static void clearActiveTransactions() {
        activeTransactions.get().clear();
    }

    /**
     * Returns a global transaction specified by the key. Returns null if no
     * entity is found.
     * 
     * @param tx
     *            the transaction
     * @param key
     *            the global transaction key
     * @return a global transaction
     */
    protected static GlobalTransaction getOrNull(Transaction tx, Key key) {
        Entity entity = Datastore.getOrNull(tx, key);
        if (entity == null) {
            return null;
        }
        return toGlobalTransaction(entity);
    }

    /**
     * Converts the entity to a global transaction.
     * 
     * @param entity
     *            the entity
     * @return a global transaction
     * @throws NullPointerException
     *             if the entity parameter is null or if the valid property is
     *             null
     */
    protected static GlobalTransaction toGlobalTransaction(Entity entity)
            throws NullPointerException {
        if (entity == null) {
            throw new NullPointerException(
                "The entity parameter must not be null.");
        }
        Boolean valid = (Boolean) entity.getProperty(VALID_PROPERTY);
        return new GlobalTransaction(entity.getKey(), valid);
    }

    /**
     * Puts the global transaction to datastore.
     * 
     * @param tx
     *            the transaction
     * @param globalTransaction
     *            the global transaction
     * @throws NullPointerException
     *             if the tx parameter is null or if the globalTransaction
     *             parameter is null
     */
    protected static void put(Transaction tx,
            GlobalTransaction globalTransaction) throws NullPointerException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter must not be null.");
        }
        if (globalTransaction == null) {
            throw new NullPointerException(
                "The globalTransaction parameter must not be null.");
        }
        Datastore.put(tx, globalTransaction.toEntity());
    }

    /**
     * Rolls forward the transaction.
     * 
     * @param globalTransactionKey
     *            the global transaction key
     * @throws NullPointerException
     *             if the globalTransactionKey parameter is null
     */
    protected static void rollForward(Key globalTransactionKey)
            throws NullPointerException {
        if (globalTransactionKey == null) {
            logger
                .warning("The globalTransactionKey parameter must not be null.");
            return;
        }
        if (Datastore.getAsMap(globalTransactionKey).size() == 0) {
            return;
        }
        Journal.apply(globalTransactionKey);
        Lock.deleteWithoutTx(globalTransactionKey);
        Datastore.deleteWithoutTx(globalTransactionKey);
    }

    /**
     * Rolls back the transaction.
     * 
     * @param globalTransactionKey
     *            the global transaction key
     * @throws NullPointerException
     *             if the globalTransactionKey parameter is null
     */
    protected static void rollback(Key globalTransactionKey)
            throws NullPointerException {
        if (globalTransactionKey == null) {
            logger
                .warning("The globalTransactionKey parameter must not be null.");
            return;
        }
        Journal.deleteInTx(globalTransactionKey);
        Lock.deleteInTx(globalTransactionKey);
    }

    /**
     * Submits a roll-forward job.
     * 
     * @param tx
     *            the transaction
     * @param globalTransactionKey
     *            the global transaction key
     * @throws NullPointerException
     *             if the tx parameter is null or if the globalTransactionKey
     *             parameter is null
     */
    protected static void submitRollForwardJob(Transaction tx,
            Key globalTransactionKey) throws NullPointerException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter must not be null.");
        }
        if (globalTransactionKey == null) {
            throw new NullPointerException(
                "The globalTransactionKey parameter must not be null.");
        }
        String encodedKey = Datastore.keyToString(globalTransactionKey);
        Queue queue = QueueFactory.getQueue(QUEUE_NAME);
        queue.add(tx, TaskOptions.Builder.url(
            GlobalTransactionServlet.SERVLET_PATH).param(
            GlobalTransactionServlet.COMMAND_NAME,
            GlobalTransactionServlet.ROLLFORWARD_COMMAND).param(
            GlobalTransactionServlet.KEY_NAME,
            encodedKey));
    }

    /**
     * Submits a rollback job.
     * 
     * @param globalTransactionKey
     *            the global transaction key
     * @throws NullPointerException
     *             if the globalTransactionKey parameter is null
     */
    protected static void submitRollbackJob(Key globalTransactionKey)
            throws NullPointerException {
        if (globalTransactionKey == null) {
            throw new NullPointerException(
                "The globalTransactionKey parameter must not be null.");
        }
        String encodedKey = Datastore.keyToString(globalTransactionKey);
        Queue queue = QueueFactory.getQueue(QUEUE_NAME);
        queue.add(TaskOptions.Builder
            .url(GlobalTransactionServlet.SERVLET_PATH)
            .param(
                GlobalTransactionServlet.COMMAND_NAME,
                GlobalTransactionServlet.ROLLBACK_COMMAND)
            .param(GlobalTransactionServlet.KEY_NAME, encodedKey));
    }

    /**
     * Constructor.
     */
    public GlobalTransaction() {
    }

    /**
     * Constructor.
     * 
     * @param globalTransactionKey
     *            the global transaction key
     * @param valid
     *            whether this transaction is valid
     * @throws NullPointerException
     *             if the globalTransactionKey parameter is null or if the valid
     *             parameter is null
     */
    protected GlobalTransaction(Key globalTransactionKey, Boolean valid)
            throws NullPointerException {
        if (globalTransactionKey == null) {
            throw new NullPointerException(
                "The globalTransactionKey parameter must not be null.");
        }
        if (valid == null) {
            throw new NullPointerException(
                "The valid parameter must not be null.");
        }
        this.globalTransactionKey = globalTransactionKey;
        this.valid = valid;
    }

    /**
     * Returns the local transaction.
     * 
     * @return the local transaction
     */
    public Transaction getLocalTransaction() {
        assertActive();
        return localTransaction;
    }

    /**
     * Determines if this transaction is active.
     * 
     * @return whether this transaction is active
     */
    public boolean isActive() {
        if (localTransaction != null) {
            return localTransaction.isActive();
        }
        return false;
    }

    /**
     * Asserts that this transaction is active.if this transaction is not active
     * 
     * @throws IllegalStateException
     *             if this transaction is not active
     */
    protected void assertActive() throws IllegalStateException {
        if (!isActive()) {
            throw new IllegalStateException("This transaction must be active.");
        }
    }

    /**
     * Returns the transaction identifier.
     * 
     * @return the transaction identifier
     */
    public String getId() {
        assertActive();
        return localTransaction.getId();
    }

    /**
     * Begins this global transaction.
     */
    protected void begin() {
        activeTransactions.get().add(this);
        localTransaction = Datastore.beginTransaction();
        timestamp = System.currentTimeMillis();
        lockMap = new HashMap<Key, Lock>();
        journalMap = new HashMap<Key, Entity>();
    }

    /**
     * Gets an entity specified by the key. If locking the entity failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param key
     *            the key
     * @return an entity
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public Entity get(Key key) throws NullPointerException,
            EntityNotFoundRuntimeException, ConcurrentModificationException,
            IllegalStateException {
        Entity entity = getOrNull(key);
        if (entity == null) {
            throw new EntityNotFoundRuntimeException(key);
        }
        return entity;
    }

    /**
     * Gets a model specified by the key. If locking the entity failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param key
     *            the key
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M get(Class<M> modelClass, Key key) throws NullPointerException,
            EntityNotFoundRuntimeException, ConcurrentModificationException,
            IllegalStateException {
        return get(DatastoreUtil.getModelMeta(modelClass), key);
    }

    /**
     * Gets a model specified by the key. If locking the entity failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param key
     *            the key
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M get(ModelMeta<M> modelMeta, Key key)
            throws NullPointerException, EntityNotFoundRuntimeException,
            ConcurrentModificationException, IllegalStateException {
        Entity entity = get(key);
        ModelMeta<M> mm = DatastoreUtil.getModelMeta(modelMeta, entity);
        mm.validateKey(key);
        return mm.entityToModel(entity);
    }

    /**
     * Gets a model specified by the key. If locking the entity failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param key
     *            the key
     * @param version
     *            the version
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M get(Class<M> modelClass, Key key, long version)
            throws NullPointerException, EntityNotFoundRuntimeException,
            ConcurrentModificationException, IllegalStateException {
        return get(DatastoreUtil.getModelMeta(modelClass), key, version);
    }

    /**
     * Gets a model specified by the key. If locking the entity failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param key
     *            the key
     * @param version
     *            the version
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M get(ModelMeta<M> modelMeta, Key key, long version)
            throws NullPointerException, EntityNotFoundRuntimeException,
            ConcurrentModificationException, IllegalStateException {
        Entity entity = get(key);
        ModelMeta<M> mm = DatastoreUtil.getModelMeta(modelMeta, entity);
        mm.validateKey(key);
        M model = mm.entityToModel(entity);
        if (version != modelMeta.getVersion(model)) {
            throw new ConcurrentModificationException(
                "Failed optimistic lock by key("
                    + key
                    + ") and version("
                    + version
                    + ").");
        }
        return model;
    }

    /**
     * Gets an entity specified by the key. Returns null if no entity is found.
     * If locking the entity failed, the other locks that this transaction has
     * are released automatically.
     * 
     * @param key
     *            the key
     * @return an entity
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public Entity getOrNull(Key key) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(key);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return verifyLockAndGetAsMap(rootKey, Arrays.asList(key)).get(key);
        }
        if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.getOrNull(localTransaction, key);
        }
        return lockAndGetAsMap(rootKey, Arrays.asList(key)).get(key);
    }

    /**
     * Gets a model specified by the key. Returns null if no entity is found. If
     * locking the entity failed, the other locks that this transaction has are
     * released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param key
     *            the key
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M getOrNull(Class<M> modelClass, Key key)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return getOrNull(DatastoreUtil.getModelMeta(modelClass), key);
    }

    /**
     * Gets a model specified by the key. Returns null if no entity is found. If
     * locking the entity failed, the other locks that this transaction has are
     * released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param key
     *            the key
     * @return a model
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> M getOrNull(ModelMeta<M> modelMeta, Key key)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        Entity entity = getOrNull(key);
        if (entity == null) {
            return null;
        }
        ModelMeta<M> mm = DatastoreUtil.getModelMeta(modelMeta, entity);
        mm.validateKey(key);
        return mm.entityToModel(entity);
    }

    /**
     * Gets entities specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param keys
     *            keys
     * @return entities
     * @throws NullPointerException
     *             if the keys parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public List<Entity> get(Iterable<Key> keys) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        return DatastoreUtil.entityMapToEntityList(keys, getAsMap(keys));
    }

    /**
     * Gets models specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param keys
     *            keys
     * @return models
     * @throws NullPointerException
     *             if the modelClass parameter is null or if the keys parameter
     *             is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> List<M> get(Class<M> modelClass, Iterable<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return get(DatastoreUtil.getModelMeta(modelClass), keys);
    }

    /**
     * Gets models specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            keys
     * @return models
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the keys parameter
     *             is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> List<M> get(ModelMeta<M> modelMeta, Iterable<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return DatastoreUtil.entityMapToModelList(
            modelMeta,
            keys,
            getAsMap(keys));
    }

    /**
     * Gets entities specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param keys
     *            keys
     * @return entities
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public List<Entity> get(Key... keys)
            throws ConcurrentModificationException, IllegalStateException {
        return get(Arrays.asList(keys));
    }

    /**
     * Gets models specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param keys
     *            keys
     * @return models
     * @throws NullPointerException
     *             if the modelClass parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> List<M> get(Class<M> modelClass, Key... keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return get(modelClass, Arrays.asList(keys));
    }

    /**
     * Gets models specified by the keys. If locking the entities failed, the
     * other locks that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            keys
     * @return models
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> List<M> get(ModelMeta<M> modelMeta, Key... keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return get(modelMeta, Arrays.asList(keys));
    }

    /**
     * Gets entities as map. If locking the entity groups failed, the other
     * locks that this transaction has are released automatically.
     * 
     * @param keys
     *            the keys
     * @return entities
     * @throws NullPointerException
     *             if the keys parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public Map<Key, Entity> getAsMap(Iterable<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        Map<Key, Entity> map = new HashMap<Key, Entity>();
        Key ltxRootKey = null;
        Set<Key> gtxRootKeys = new HashSet<Key>();
        List<Key> ltxKeys = new ArrayList<Key>();
        List<Key> gtxKeys = new ArrayList<Key>();
        for (Key key : keys) {
            Key rootKey = DatastoreUtil.getRoot(key);
            if (localTransactionRootKey == null) {
                if (ltxRootKey == null) {
                    ltxRootKey = rootKey;
                    ltxKeys.add(key);
                } else if (ltxRootKey.equals(rootKey)) {
                    ltxKeys.add(key);
                } else {
                    gtxRootKeys.add(rootKey);
                    gtxKeys.add(key);
                }
            } else if (localTransactionRootKey.equals(rootKey)) {
                ltxKeys.add(key);
            } else {
                gtxRootKeys.add(rootKey);
                gtxKeys.add(key);
            }
        }
        if (ltxRootKey != null) {
            setLocalTransactionRootKey(ltxRootKey);
            map.putAll(verifyLockAndGetAsMap(ltxRootKey, ltxKeys));
        } else {
            if (ltxKeys.size() > 0) {
                map.putAll(Datastore.getAsMap(localTransaction, ltxKeys));
            }
        }
        if (gtxKeys.size() > 0) {
            for (Key key : gtxRootKeys) {
                lock(key);
            }
            map.putAll(Datastore.getAsMapWithoutTx(gtxKeys));
        }
        return map;
    }

    /**
     * Gets models as map. If locking the entity groups failed, the other locks
     * that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param keys
     *            the keys
     * @return entities
     * @throws NullPointerException
     *             if the modelClass parameter is null or if the keys parameter
     *             is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> Map<Key, M> getAsMap(Class<M> modelClass, Iterable<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return getAsMap(DatastoreUtil.getModelMeta(modelClass), keys);
    }

    /**
     * Gets models as map. If locking the entity groups failed, the other locks
     * that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return entities
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the keys parameter
     *             is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> Map<Key, M> getAsMap(ModelMeta<M> modelMeta, Iterable<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return DatastoreUtil.entityMapToModelMap(modelMeta, getAsMap(keys));
    }

    /**
     * Gets entities as map. If locking the entity groups failed, the other
     * locks that this transaction has are released automatically.
     * 
     * @param keys
     *            the keys
     * @return entities
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public Map<Key, Entity> getAsMap(Key... keys)
            throws ConcurrentModificationException, IllegalStateException {
        return getAsMap(Arrays.asList(keys));
    }

    /**
     * Gets models as map. If locking the entity groups failed, the other locks
     * that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param keys
     *            the keys
     * @return entities
     * @throws NullPointerException
     *             if the modelClass parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> Map<Key, M> getAsMap(Class<M> modelClass, Key... keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return getAsMap(modelClass, Arrays.asList(keys));
    }

    /**
     * Gets models as map. If locking the entity groups failed, the other locks
     * that this transaction has are released automatically.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return entities
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> Map<Key, M> getAsMap(ModelMeta<M> modelMeta, Key... keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        return getAsMap(modelMeta, Arrays.asList(keys));
    }

    /**
     * Returns an {@link EntityQuery}.
     * 
     * @param kind
     *            the kind
     * @param ancestorKey
     *            the ancestor key
     * @return an {@link EntityQuery}
     * @throws NullPointerException
     *             if the kind parameter is null or if the ancestorKey parameter
     *             is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public EntityQuery query(String kind, Key ancestorKey)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(ancestorKey);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return Datastore.query(localTransaction, kind, ancestorKey);
        } else if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.query(localTransaction, kind, ancestorKey);
        }
        lock(rootKey);
        return Datastore.query(kind, ancestorKey);
    }

    /**
     * Returns a {@link ModelQuery}.
     * 
     * @param <M>
     *            the model type
     * @param modelClass
     *            the model class
     * @param ancestorKey
     *            the ancestor key
     * @return a {@link ModelQuery}
     * @throws NullPointerException
     *             if the modelClass parameter is null or if the ancestorKey
     *             parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> ModelQuery<M> query(Class<M> modelClass, Key ancestorKey)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(ancestorKey);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return Datastore.query(localTransaction, modelClass, ancestorKey);
        } else if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.query(localTransaction, modelClass, ancestorKey);
        }
        lock(rootKey);
        return Datastore.query(modelClass, ancestorKey);
    }

    /**
     * Returns a {@link ModelQuery}.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param ancestorKey
     *            the ancestor key
     * @return a {@link ModelQuery}
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the ancestorKey
     *             parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public <M> ModelQuery<M> query(ModelMeta<M> modelMeta, Key ancestorKey)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(ancestorKey);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return Datastore.query(localTransaction, modelMeta, ancestorKey);
        } else if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.query(localTransaction, modelMeta, ancestorKey);
        }
        lock(rootKey);
        return Datastore.query(modelMeta, ancestorKey);
    }

    /**
     * Returns a {@link KindlessQuery}.
     * 
     * @param ancestorKey
     *            the ancestor key
     * @return a {@link KindlessQuery}
     * @throws NullPointerException
     *             if the ancestorKey parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    public KindlessQuery query(Key ancestorKey) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(ancestorKey);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return Datastore.query(localTransaction, ancestorKey);
        } else if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.query(localTransaction, ancestorKey);
        }
        lock(rootKey);
        return Datastore.query(ancestorKey);
    }

    /**
     * Puts the entity. If locking the entity failed, the other locks that this
     * transaction has are released automatically.
     * 
     * @param entity
     *            the entity
     * @return a key
     * @throws NullPointerException
     *             if the entity parameter is null
     * @throws IllegalArgumentException
     *             if the size of the entity is more than 1,000,000 bytes
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public Key put(Entity entity) throws NullPointerException,
            IllegalArgumentException, ConcurrentModificationException,
            IllegalStateException {
        if (entity == null) {
            throw new NullPointerException(
                "The entity parameter must not be null.");
        }
        DatastoreUtil.assignKeyIfNecessary(entity);
        Key key = entity.getKey();
        Key rootKey = DatastoreUtil.getRoot(key);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            return Datastore.put(localTransaction, entity);
        } else if (rootKey.equals(localTransactionRootKey)) {
            return Datastore.put(localTransaction, entity);
        }
        lock(rootKey);
        verifyJournalSize();
        journalMap.put(key, entity);
        return key;
    }

    /**
     * Puts the model. If locking the entity failed, the other locks that this
     * transaction has are released automatically.
     * 
     * @param model
     *            the model
     * @return a key
     * @throws NullPointerException
     *             if the model parameter is null
     * @throws IllegalArgumentException
     *             if the size of the entity is more than 1,000,000 bytes
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public Key put(Object model) throws NullPointerException,
            IllegalArgumentException, ConcurrentModificationException,
            IllegalStateException {
        Entity entity = DatastoreUtil.modelToEntity(model);
        return put(entity);
    }

    /**
     * Puts the models. If locking the entities failed, the other locks that
     * this transaction has are released automatically.
     * 
     * @param models
     *            the models
     * @return a list of keys
     * @throws NullPointerException
     *             if the models parameter is null
     * @throws IllegalArgumentException
     *             if the size of the entity is more than 1,000,000 bytes
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public List<Key> put(Iterable<?> models) throws NullPointerException,
            IllegalArgumentException, ConcurrentModificationException,
            IllegalStateException {
        if (models == null) {
            throw new NullPointerException(
                "The models parameter must not be null.");
        }
        List<Key> keys = new ArrayList<Key>();
        for (Object model : models) {
            if (model instanceof Entity) {
                keys.add(put((Entity) model));
            } else {
                keys.add(put(model));
            }
        }
        return keys;
    }

    /**
     * Puts the models. If locking the entities failed, the other locks that
     * this transaction has are released automatically.
     * 
     * @param models
     *            the models
     * @return a list of keys
     * @throws IllegalArgumentException
     *             if the size of the entity is more than 1,000,000 bytes
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public List<Key> put(Object... models) throws IllegalArgumentException,
            ConcurrentModificationException, IllegalStateException {
        return put(Arrays.asList(models));
    }

    /**
     * Deletes the entity. If locking the entity failed, the other locks that
     * this transaction has are released automatically.
     * 
     * @param key
     *            the key
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public void delete(Key key) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(key);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            Datastore.delete(localTransaction, key);
        } else if (rootKey.equals(localTransactionRootKey)) {
            Datastore.delete(localTransaction, key);
        } else {
            lock(rootKey);
            verifyJournalSize();
            journalMap.put(key, null);
        }
    }

    /**
     * Deletes the models. If locking the entities failed, the other locks that
     * this transaction has are released automatically.
     * 
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the keys parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public void delete(Iterable<Key> keys) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        for (Key key : keys) {
            delete(key);
        }
    }

    /**
     * Deletes the models. If locking the entities failed, the other locks that
     * this transaction has are released automatically.
     * 
     * @param keys
     *            the keys
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public void delete(Key... keys) throws ConcurrentModificationException,
            IllegalStateException {
        delete(Arrays.asList(keys));
    }

    /**
     * Deletes all descendant entities. If locking the entity failed, the other
     * locks that this transaction has are released automatically.
     * 
     * @param ancestorKey
     *            the ancestor key
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking the entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     *             or if the number of journals in this global transaction
     *             exceeds 500
     */
    public void deleteAll(Key ancestorKey) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        Key rootKey = DatastoreUtil.getRoot(ancestorKey);
        if (localTransactionRootKey == null) {
            setLocalTransactionRootKey(rootKey);
            Datastore.deleteAll(localTransaction, ancestorKey);
        } else if (rootKey.equals(localTransactionRootKey)) {
            Datastore.deleteAll(localTransaction, ancestorKey);
        } else {
            lock(rootKey);
            for (Key key : Datastore.query(ancestorKey).asKeyList()) {
                if (key.getKind().equals(Lock.KIND)) {
                    continue;
                }
                verifyJournalSize();
                journalMap.put(key, null);
            }
        }
    }

    /**
     * Commits this transaction. If multiple entity groups join this
     * transaction, the roll-forward process is executed asynchronously.
     */
    public void commit() {
        assertActive();
        if (isLocalTransaction()) {
            commitLocalTransaction();
        } else {
            commitGlobalTransaction();
        }
    }

    /**
     * Rolls back this transaction.
     */
    public void rollback() {
        assertActive();
        try {
            if (isLocalTransaction()) {
                rollbackLocalTransaction();
            } else {
                rollbackGlobalTransaction();
            }
        } catch (DeadlineExceededException e) {
            if (lockMap.isEmpty()) {
                return;
            }
            logger
                .info("This rollback process will be executed asynchronously, because a DeadlineExceededException occurred.");
            submitRollbackJob(globalTransactionKey);
        }
    }

    /**
     * Rolls back this transaction asynchronously.
     */
    protected void rollbackAsync() {
        assertActive();
        if (isLocalTransaction()) {
            rollbackLocalTransaction();
        } else {
            rollbackAsyncGlobalTransaction();
        }
    }

    /**
     * Converts this transaction to an entity.
     * 
     * @return an entity
     */
    protected Entity toEntity() {
        Entity entity = new Entity(globalTransactionKey);
        entity.setUnindexedProperty(VALID_PROPERTY, valid);
        return entity;
    }

    /**
     * Sets the root key of local transaction.
     * 
     * @param rootKey
     *            the root key
     * @throws NullPointerException
     *             if the rootKey parameter is null
     */
    protected void setLocalTransactionRootKey(Key rootKey)
            throws NullPointerException {
        if (rootKey == null) {
            throw new NullPointerException(
                "The rootKey parameter must not be null.");
        }
        this.localTransactionRootKey = rootKey;
        globalTransactionKey =
            Datastore.createKey(rootKey, KIND, localTransaction.getId());
    }

    /**
     * Verifies lock specified by the root key and returns entities as map.
     * 
     * @param rootKey
     *            the root key
     * @param keys
     *            the keys
     * @return entities as map
     * @throws NullPointerException
     *             if the rootKey parameter is null or if the keys parameter is
     *             null
     */
    protected Map<Key, Entity> verifyLockAndGetAsMap(Key rootKey,
            Collection<Key> keys) throws NullPointerException {
        if (rootKey == null) {
            throw new NullPointerException(
                "The rootKey parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        assertActive();
        return Lock.verifyAndGetAsMap(localTransaction, rootKey, keys);
    }

    /**
     * Locks the entity group and returns entities as map.
     * 
     * @param rootKey
     *            the root key
     * @param keys
     *            the keys
     * @return entities as map
     * @throws NullPointerException
     *             if the rootKey parameter is null or if the keys parameter is
     *             null
     * @throws ConcurrentModificationException
     *             if locking an entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of entity groups that join global transaction
     *             exceeds 100
     */
    protected Map<Key, Entity> lockAndGetAsMap(Key rootKey, Collection<Key> keys)
            throws NullPointerException, ConcurrentModificationException,
            IllegalStateException {
        if (rootKey == null) {
            throw new NullPointerException(
                "The rootKey parameter must not be null.");
        }
        if (keys == null) {
            throw new NullPointerException(
                "The keys parameter must not be null.");
        }
        assertActive();
        if (!lockMap.containsKey(rootKey)) {
            verifyLockSize();
            Lock lock = new Lock(globalTransactionKey, rootKey, timestamp);
            try {
                Map<Key, Entity> map = lock.lockAndGetAsMap(keys);
                lockMap.put(rootKey, lock);
                return map;
            } catch (ConcurrentModificationException e) {
                unlock();
                throw e;
            }
        }
        return Datastore.getAsMapWithoutTx(keys);
    }

    /**
     * Lock the entity. If locking the entity failed, the other locks that this
     * transaction has are released automatically.
     * 
     * @param rootKey
     *            a root key
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws ConcurrentModificationException
     *             if locking an entity specified by the key failed
     * @throws IllegalStateException
     *             if the number of entity groups that join global transaction
     *             exceeds 100
     */
    protected void lock(Key rootKey) throws NullPointerException,
            ConcurrentModificationException, IllegalStateException {
        if (rootKey == null) {
            throw new NullPointerException(
                "The key parameter must not be null.");
        }
        assertActive();
        if (!lockMap.containsKey(rootKey)) {
            verifyLockSize();
            Lock lock = new Lock(globalTransactionKey, rootKey, timestamp);
            try {
                lock.lock();
            } catch (ConcurrentModificationException e) {
                unlock();
                throw e;
            }
            lockMap.put(rootKey, lock);
        }
    }

    /**
     * Unlocks entities.
     */
    protected void unlock() {
        activeTransactions.get().remove(this);
        if (localTransaction.isActive()) {
            localTransaction.rollback();
        }
        if (!lockMap.isEmpty()) {
            Lock.deleteInTx(globalTransactionKey, lockMap.values());
            lockMap.clear();
        }
    }

    /**
     * Verifies the size of locks.
     * 
     * @throws IllegalStateException
     *             if the number of locks in this global transaction exceeds 100
     */
    protected void verifyLockSize() throws IllegalStateException {
        if (lockMap.size() >= MAX_LOCKS) {
            unlock();
            throw new IllegalStateException(
                "You cannot add more than 100 locks into this global transaction. NOTE: The lock of first entity group is not counted.");
        }
    }

    /**
     * Verifies the size of journals.
     * 
     * @throws IllegalStateException
     *             if the number of journals in this global transaction exceeds
     *             500
     */
    protected void verifyJournalSize() throws IllegalStateException {
        if (journalMap.size() >= MAX_JOURNALS) {
            unlock();
            throw new IllegalStateException(
                "You cannot add more than 500 journals into this global transaction. NOTE: The journals of first entity group are not counted.");
        }
    }

    /**
     * Determines if this is a local transaction.
     * 
     * @return whether this is a local transaction
     */
    protected boolean isLocalTransaction() {
        return lockMap.isEmpty();
    }

    /**
     * Commits this transaction as local transaction.
     */
    protected void commitLocalTransaction() {
        activeTransactions.get().remove(this);
        try {
            localTransaction.commit();
        } finally {
            if (localTransaction.isActive()) {
                localTransaction.rollback();
            }
        }
    }

    /**
     * Commits this transaction as global transaction.
     */
    protected void commitGlobalTransaction() {
        putJournals();
        commitGlobalTransactionInternally();
    }

    /**
     * Puts the journals.
     */
    protected void putJournals() {
        try {
            Journal.put(globalTransactionKey, journalMap);
        } catch (Throwable cause) {
            try {
                Journal.deleteInTx(globalTransactionKey);
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            try {
                unlock();
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            throw ThrowableUtil.wrap(cause);
        }

    }

    /**
     * Commits this global transaction.
     */
    protected void commitGlobalTransactionInternally() {
        activeTransactions.get().remove(this);
        try {
            Datastore.put(localTransaction, toEntity());
            submitRollForwardJob(localTransaction, globalTransactionKey);
            localTransaction.commit();
        } catch (Throwable cause) {
            try {
                if (localTransaction.isActive()) {
                    localTransaction.rollback();
                }
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            try {
                GlobalTransaction gtx =
                    getOrNull((Transaction) null, globalTransactionKey);
                if (gtx != null && gtx.valid) {
                    return;
                }
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            try {
                Journal.deleteInTx(globalTransactionKey);
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            try {
                unlock();
            } catch (Throwable cause2) {
                logger.log(Level.WARNING, cause2.getMessage(), cause2);
            }
            throw ThrowableUtil.wrap(cause);
        }
    }

    /**
     * Rolls back this transaction as local transaction.
     */
    protected void rollbackLocalTransaction() {
        unlock();
    }

    /**
     * Rolls back this transaction as global transaction.
     */
    protected void rollbackGlobalTransaction() {
        Journal.deleteInTx(globalTransactionKey);
        unlock();
    }

    /**
     * Rolls back this transaction as global transaction asynchronously.
     */
    protected void rollbackAsyncGlobalTransaction() {
        submitRollbackJob(globalTransactionKey);
        activeTransactions.get().remove(this);
        if (localTransaction.isActive()) {
            localTransaction.rollback();
        }
    }
}