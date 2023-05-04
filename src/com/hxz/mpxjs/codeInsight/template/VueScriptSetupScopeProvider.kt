// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.codeInsight.template

import com.intellij.lang.javascript.psi.JSExecutionScope
import com.intellij.lang.javascript.psi.JSPsiNamedElementBase
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.ResolveResult
import com.hxz.mpxjs.codeInsight.resolveIfImportSpecifier
import com.hxz.mpxjs.index.findModule
import com.hxz.mpxjs.model.source.VueCompositionInfoHelper
import java.util.function.Consumer

class VueScriptSetupScopeProvider : VueTemplateScopesProvider() {

  override fun getScopes(element: PsiElement, hostElement: PsiElement?): List<VueTemplateScope> {
    return findModule(element, true)?.let {
      listOf(VueScriptSetupScope(it))
    } ?: emptyList()
  }

  private class VueScriptSetupScope constructor(private val module: JSExecutionScope) : VueTemplateScope(null) {

    override fun resolve(consumer: Consumer<in ResolveResult>) {
      JSStubBasedPsiTreeUtil.processDeclarationsInScope(module, { element, _ ->
        val resolved = (element as? JSPsiNamedElementBase)?.resolveIfImportSpecifier()
        val elementToConsume = VueCompositionInfoHelper.getUnwrappedRefElement(resolved, module)
                               ?: resolved ?: element
        consumer.accept(PsiElementResolveResult(elementToConsume, true)).let { true }
      }, false)
    }
  }
}