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
package org.slim3.gen.desc;

import static org.slim3.gen.AnnotationConstants.Blob;
import static org.slim3.gen.AnnotationConstants.Impermanent;
import static org.slim3.gen.AnnotationConstants.PrimaryKey;
import static org.slim3.gen.AnnotationConstants.Text;
import static org.slim3.gen.AnnotationConstants.Version;

import java.util.LinkedList;
import java.util.List;

import org.slim3.gen.datastore.DatastoreType;
import org.slim3.gen.util.StringUtil;
import org.slim3.gen.util.TypeUtil;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.ArrayType;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.ReferenceType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.WildcardType;
import com.sun.mirror.type.PrimitiveType.Kind;
import com.sun.mirror.util.SimpleTypeVisitor;

/**
 * Represents an attribute meta description factory.
 * 
 * @author taedium
 * @since 3.0
 * 
 */
public class AttributeMetaDescFactory {

    /** the environment */
    protected final AnnotationProcessorEnvironment env;

    /**
     * Creates a new {@link AttributeMetaDescFactory}.
     * 
     * @param env
     *            the processing environment
     */
    public AttributeMetaDescFactory(AnnotationProcessorEnvironment env) {
        if (env == null) {
            throw new NullPointerException("The env parameter is null.");
        }
        this.env = env;
    }

    /**
     * Creates a new {@link AttributeMetaDesc}
     * 
     * @param attributeDeclaration
     *            the attribute declaration
     * @return an attribute meta description
     */
    public AttributeMetaDesc createAttributeMetaDesc(
            FieldDeclaration fieldDeclaration,
            List<MethodDeclaration> methodDeclarations) {
        if (fieldDeclaration == null) {
            throw new NullPointerException(
                "The fieldDeclaration parameter is null.");
        }
        if (methodDeclarations == null) {
            throw new NullPointerException(
                "The methodDeclarations parameter is null.");
        }

        AttributeMetaDesc attributeMetaDesc =
            new AttributeMetaDesc(
                fieldDeclaration.getSimpleName(),
                fieldDeclaration.getDeclaringType().getQualifiedName());
        handleField(attributeMetaDesc, fieldDeclaration);
        handleMethod(attributeMetaDesc, methodDeclarations);
        return attributeMetaDesc;
    }

    protected void handleField(AttributeMetaDesc attributeMetaDesc,
            FieldDeclaration fieldDeclaration) {
        DatastoreType type = null;

        if (type.isAnnotated(Impermanent)) {
            attributeMetaDesc.setImpermanent(true);
            if (type.isAnnotated(PrimaryKey)) {
                // throw
            }
            if (type.isAnnotated(Version)) {
                // throw
            }
            if (type.isAnnotated(Text)) {
                // throw
            }
            if (type.isAnnotated(Blob)) {
                // throw
            }
            return;
        }
        attributeMetaDesc.setUnindexed(type.isUnindex());

        if (type.isCollection()) {

        } else if (type.isArray()) {

        }

        if (type.isAnnotated(PrimaryKey)) {
            // TODO type check
            attributeMetaDesc.setPrimaryKey(true);
        }
        if (type.isAnnotated(Version)) {
            // TODO type check
            attributeMetaDesc.setVersion(true);
        }
        if (type.isAnnotated(Text)) {
            // TODO type check
            attributeMetaDesc.setText(true);
            attributeMetaDesc.setUnindexed(true);
        }
        if (type.isAnnotated(Blob)) {
            // TODO type check
            attributeMetaDesc.setBlob(true);
            attributeMetaDesc.setUnindexed(true);
            if (type.isSerialized()) {
                attributeMetaDesc.setSerialized(true);
            }
        } else if (type.isSerialized()) {
            attributeMetaDesc.setShortBlob(true);
            attributeMetaDesc.setSerialized(true);
        }
    }

    protected void handleMethod(AttributeMetaDesc attributeMetaDesc,
            List<MethodDeclaration> methodDeclarations) {
        for (MethodDeclaration m : methodDeclarations) {
            if (isReadMethod(m, attributeMetaDesc)) {
                attributeMetaDesc.setReadMethodName(m.getSimpleName());
                if (attributeMetaDesc.getWriteMethodName() != null) {
                    break;
                }
            } else if (isWriteMethod(m, attributeMetaDesc)) {
                attributeMetaDesc.setWriteMethodName(m.getSimpleName());
                if (attributeMetaDesc.getReadMethodName() != null) {
                    break;
                }
            }
        }
        // throw if (readMethod == null || writeMethod == null)
    }

    protected boolean isReadMethod(MethodDeclaration m,
            AttributeMetaDesc attributeMetaDesc) {
        String propertyName = null;
        if (m.getSimpleName().startsWith("get")) {
            propertyName =
                StringUtil.decapitalize(m.getSimpleName().substring(3));
        } else if (m.getSimpleName().startsWith("is")) {
            if (!TypeUtil.isPrimitive(m.getReturnType(), Kind.BOOLEAN)) {
                return false;
            }
            propertyName =
                StringUtil.decapitalize(m.getSimpleName().substring(2));
        } else {
            return false;
        }
        if (!propertyName.equals(attributeMetaDesc.getName())
            || TypeUtil.isVoid(m.getReturnType())
            || m.getParameters().size() != 0) {
            return false;
        }
        TypeDeclaration propertyClass =
            TypeUtil.toTypeDeclaration(m.getReturnType());
        if (propertyClass == null
            || !propertyClass.getQualifiedName().equals(
                attributeMetaDesc.getAttributeClassName())) {
            return false;
        }
        return true;
    }

    protected boolean isWriteMethod(MethodDeclaration m,
            AttributeMetaDesc attributeMetaDesc) {
        if (!m.getSimpleName().startsWith("set")) {
            return false;
        }
        String propertyName =
            StringUtil.decapitalize(m.getSimpleName().substring(3));
        if (!propertyName.equals(attributeMetaDesc.getName())
            || m.getParameters().size() != 1
            || TypeUtil.isVoid(m.getReturnType())) {
            return false;
        }
        TypeDeclaration propertyClass =
            TypeUtil.toTypeDeclaration(m
                .getParameters()
                .iterator()
                .next()
                .getType());
        if (propertyClass == null
            || !propertyClass.getQualifiedName().equals(
                attributeMetaDesc.getAttributeClassName())) {
            return false;
        }
        return true;
    }

    protected static class ClassNameCollector extends SimpleTypeVisitor {

        LinkedList<String> names = new LinkedList<String>();

        /** the target typeMirror */
        protected final TypeMirror typeMirror;

        /**
         * Creates a new {@link ClassNameCollector}
         * 
         * @param typeMirror
         *            the target typeMirror
         */
        public ClassNameCollector(TypeMirror typeMirror) {
            this.typeMirror = typeMirror;
        }

        /**
         * Collects the collection of class name.
         * 
         * @return the collection of class name
         */
        public LinkedList<String> collect() {
            typeMirror.accept(this);
            return names;
        }

        @Override
        public void visitArrayType(ArrayType type) {
            ClassNameCollector collector2 = new ClassNameCollector(type);
            LinkedList<String> names = collector2.collect();
            type.getComponentType().accept(collector2);
            this.names.add(names.getFirst() + "[]");
            this.names.add(names.getFirst());
        }

        @Override
        public void visitDeclaredType(DeclaredType type) {
            names.add(type.getDeclaration().getQualifiedName());
            for (TypeMirror arg : type.getActualTypeArguments()) {
                ClassNameCollector collector2 = new ClassNameCollector(arg);
                LinkedList<String> names = collector2.collect();
                this.names.add(names.getFirst());
            }
        }

        @Override
        public void visitPrimitiveType(PrimitiveType type) {
            switch (type.getKind()) {
            case BOOLEAN: {
                names.add(boolean.class.getName());
                break;
            }
            case BYTE: {
                names.add(byte.class.getName());
                break;
            }
            case CHAR: {
                names.add(char.class.getName());
                break;
            }
            case DOUBLE: {
                names.add(double.class.getName());
                break;
            }
            case FLOAT: {
                names.add(float.class.getName());
                break;
            }
            case INT: {
                names.add(int.class.getName());
                break;
            }
            case LONG: {
                names.add(long.class.getName());
                break;
            }
            case SHORT: {
                names.add(short.class.getName());
                break;
            }
            default: {
                throw new IllegalArgumentException(type.getKind().name());
            }
            }
        }

        @Override
        public void visitWildcardType(WildcardType type) {
            for (ReferenceType referenceType : type.getUpperBounds()) {
                ClassNameCollector collector2 =
                    new ClassNameCollector(referenceType);
                LinkedList<String> names = collector2.collect();
                this.names.add(names.getFirst());
            }
            for (ReferenceType referenceType : type.getLowerBounds()) {
                ClassNameCollector collector2 =
                    new ClassNameCollector(referenceType);
                LinkedList<String> names = collector2.collect();
                this.names.add(names.getFirst());
            }
        }
    }
}
