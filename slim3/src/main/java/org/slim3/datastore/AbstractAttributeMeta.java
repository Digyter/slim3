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

import org.slim3.util.PropertyDesc;

/**
 * An abstract meta data of attribute.
 * 
 * @author higa
 * @param <M>
 *            the model type
 * @param <A>
 *            the attribute type
 * @since 3.0
 * 
 */
public abstract class AbstractAttributeMeta<M, A> {

    /**
     * The "ascending" sort criterion
     */
    public SortCriterion asc = new AscCriterion(this);

    /**
     * The "descending" sort criterion
     */
    public SortCriterion desc = new DescCriterion(this);

    /**
     * The meta data of model.
     */
    protected ModelMeta<M> modelMeta;

    /**
     * The name.
     */
    protected String name;

    /**
     * The attribute class.
     */
    protected Class<? super A> attributeClass;

    /**
     * The property descriptor.
     */
    protected PropertyDesc propertyDesc;

    /**
     * Constructor.
     * 
     * @param modelMeta
     *            the meta data of model
     * @param name
     *            the name
     * @param attributeClass
     *            the attribute class
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the name parameter
     *             is null or if the attributeClass parameter is null
     */
    public AbstractAttributeMeta(ModelMeta<M> modelMeta, String name,
            Class<? super A> attributeClass) {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        if (name == null) {
            throw new NullPointerException("The name parameter is null.");
        }
        if (attributeClass == null) {
            throw new NullPointerException(
                "The attributeClass parameter is null.");
        }
        this.modelMeta = modelMeta;
        this.name = name;
        this.attributeClass = attributeClass;
    }

    /**
     * Returns the name.
     * 
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the attribute class
     * 
     * @return the attribute class
     */
    public Class<? super A> getAttributeClass() {
        return attributeClass;
    }

    /**
     * Returns the property value.
     * 
     * @param model
     *            the model
     * @return the property value
     * @throws IllegalArgumentException
     *             if the property is not found
     */
    protected Object getValue(Object model) throws IllegalArgumentException {
        if (propertyDesc == null) {
            propertyDesc = modelMeta.getBeanDesc().getPropertyDesc(name);
        }
        if (propertyDesc == null) {
            throw new IllegalArgumentException("The property("
                + name
                + ") of model("
                + modelMeta.getModelClass().getName()
                + ") is not found.");
        }
        return propertyDesc.getValue(model);
    }

    /**
     * Converts the value for datastore.
     * 
     * @param value
     *            the value
     * @return a converted value for datastore
     */
    protected Object convertValueForDatastore(Object value) {
        if (value instanceof Enum<?>) {
            return Enum.class.cast(value).name();
        }
        return value;
    }
}