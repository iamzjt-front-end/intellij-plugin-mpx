// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.model

import com.intellij.model.Pointer
import com.hxz.mpxjs.codeInsight.documentation.VueDocumentedItem

interface VueComponent : VueNamedEntity, VueInstanceOwner, VueDocumentedItem {
  fun createPointer(): Pointer<out VueComponent>
}