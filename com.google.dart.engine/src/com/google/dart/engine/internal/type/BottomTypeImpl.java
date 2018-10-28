/*
 * Copyright (c) 2012, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.engine.internal.type;

import com.google.dart.engine.internal.element.ElementPair;
import com.google.dart.engine.type.Type;

import java.util.Set;

/**
 * The unique instance of the class {@code BottomTypeImpl} implements the type {@code bottom}.
 * 
 * @coverage dart.engine.type
 */
public class BottomTypeImpl extends TypeImpl {
  /**
   * The unique instance of this class.
   */
  private static final BottomTypeImpl INSTANCE = new BottomTypeImpl(); //$NON-NLS-1$

  /**
   * Return the unique instance of this class.
   * 
   * @return the unique instance of this class
   */
  public static BottomTypeImpl getInstance() {
    return INSTANCE;
  }

  /**
   * Prevent the creation of instances of this class.
   */
  private BottomTypeImpl() {
    super(null, "<bottom>"); //$NON-NLS-1$
  }

  @Override
  public boolean equals(Object object) {
    return object == this;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  public boolean isSupertypeOf(Type type) {
    // bottom is a subtype of all types
    return false;
  }

  @Override
  public BottomTypeImpl substitute(Type[] argumentTypes, Type[] parameterTypes) {
    return this;
  }

  @Override
  protected boolean internalEquals(Object object, Set<ElementPair> visitedElementPairs) {
    return object == this;
  }

  @Override
  protected boolean internalIsMoreSpecificThan(Type type, boolean withDynamic,
      Set<TypePair> visitedTypePairs) {
    return true;
  }

  @Override
  protected boolean internalIsSubtypeOf(Type type, Set<TypePair> visitedTypePairs) {
    // bottom is a subtype of all types
    return true;
  }
}
