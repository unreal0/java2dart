/*
 * Copyright (c) 2014, the Dart project authors.
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

package com.google.dart.engine.internal.element.angular;

import com.google.common.annotations.VisibleForTesting;
import com.google.dart.engine.html.ast.XmlTagNode;

/**
 * Combination of {@link AngularTagSelectorElementImpl} and {@link HasAttributeSelectorElementImpl}.
 */
public class IsTagHasAttributeSelectorElementImpl extends AngularSelectorElementImpl {
  private final String tagName;
  private final String attributeName;

  public IsTagHasAttributeSelectorElementImpl(String tagName, String attributeName) {
    super(tagName + "[" + attributeName + "]", -1);
    this.tagName = tagName;
    this.attributeName = attributeName;
  }

  @Override
  public boolean apply(XmlTagNode node) {
    return node.getTag().equals(tagName) && node.getAttribute(attributeName) != null;
  }

  @VisibleForTesting
  public String getAttributeName() {
    return attributeName;
  }

  @VisibleForTesting
  public String getTagName() {
    return tagName;
  }
}
