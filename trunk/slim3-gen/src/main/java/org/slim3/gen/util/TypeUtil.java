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
package org.slim3.gen.util;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.DeclaredType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.VoidType;
import com.sun.mirror.type.PrimitiveType.Kind;
import com.sun.mirror.util.SimpleTypeVisitor;

/**
 * A utility class for operationg typemirrors.
 * 
 * @author taedium
 * @since 3.0
 * 
 */
public final class TypeUtil {

    /**
     * Returns {@code true} if a typeMirror represents void.
     * 
     * @param typeMirror
     *            the typemirror
     * @return {@code true} if a typeMirror represents void, otherwise {@code
     *         false}.
     */
    public static boolean isVoid(TypeMirror typeMirror) {
        class Visitor extends SimpleTypeVisitor {
            boolean result;

            @Override
            public void visitVoidType(VoidType voidtype) {
                result = true;
            }
        }
        Visitor visitor = new Visitor();
        typeMirror.accept(visitor);
        return visitor.result;
    }

    /**
     * Returns {@code true} if a typeMirror represents primitive.
     * 
     * @param typeMirror
     *            the typemirror
     * @param kind
     *            the kind of primitive
     * @return {@code true} if a typeMirror represents primitive, otherwise
     *         {@code false}.
     */
    public static boolean isPrimitive(TypeMirror typeMirror, final Kind kind) {
        class Visitor extends SimpleTypeVisitor {
            boolean result;

            @Override
            public void visitPrimitiveType(PrimitiveType primitivetype) {
                result = primitivetype.getKind() == kind;
            }
        }
        Visitor visitor = new Visitor();
        typeMirror.accept(visitor);
        return visitor.result;
    }

    /**
     * Returns {@code true} if a typeMirror represents {@link DeclaredType}.
     * 
     * @param typeMirror
     *            the typemirror
     * @return {@code true} if a typeMirror represents {@link DeclaredType},
     *         otherwise {@code null}.
     */
    public static DeclaredType toDeclaredType(TypeMirror typeMirror) {
        class Visitor extends SimpleTypeVisitor {
            DeclaredType result;

            @Override
            public void visitDeclaredType(DeclaredType declaredtype) {
                result = declaredtype;
            }
        }
        Visitor visitor = new Visitor();
        typeMirror.accept(visitor);
        return visitor.result;
    }

    /**
     * Returns {@code true} if a typeMirror represents {@link DeclaredType}.
     * 
     * @param env
     *            the environment
     * @param subtype
     *            the typemirror of subtype
     * @param superclass
     *            the superclass
     * @return {@code true} if a {@code subtype} is subtype of {@code
     *         superclass}, otherwise {@code false}.
     */
    public static boolean isSubtype(AnnotationProcessorEnvironment env,
            TypeMirror subtype, Class<?> superclass) {
        TypeDeclaration supertypeDeclaration =
            env.getTypeDeclaration(superclass.getName());
        if (supertypeDeclaration == null) {
            return false;
        }
        TypeMirror supertype =
            env.getTypeUtils().getDeclaredType(supertypeDeclaration);
        return env.getTypeUtils().isSubtype(subtype, supertype);
    }
}
