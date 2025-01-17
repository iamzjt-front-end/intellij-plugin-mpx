// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.model

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project

interface VueGlobal : VueEntitiesContainer {
  val apps: List<VueApp>
  val plugins: List<VuePlugin>
  val unregistered: VueEntitiesContainer
  val project: Project
  val packageJsonUrl: String?

  fun getParents(scopeElement: VueScopeElement): List<VueEntitiesContainer>

  override fun createPointer(): Pointer<out VueGlobal>
}
