// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.codeInsight

import com.intellij.lang.javascript.psi.JSEmbeddedContent
import com.intellij.lang.javascript.psi.JSPsiNamedElementBase
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.util.HtmlUtil
import com.hxz.mpxjs.index.findModule
import com.hxz.mpxjs.lang.expr.psi.VueJSScriptSetupExpression

object VueScriptAdditionalScopeProvider {

  fun getAdditionalScopeSymbols(element: JSReferenceExpression): List<JSPsiNamedElementBase> {
    val result = mutableListOf<JSPsiNamedElementBase>()
    val setupAttribute = PsiTreeUtil.getParentOfType(element, JSEmbeddedContent::class.java)
      ?.context?.asSafely<XmlTag>()
      ?.takeIf { HtmlUtil.isScriptTag(it) }
      ?.getAttribute(SETUP_ATTRIBUTE_NAME)
    if (setupAttribute != null) {
      setupAttribute.valueElement
        ?.findJSExpression<VueJSScriptSetupExpression>()
        ?.getParameterList()
        ?.parameterVariables
        ?.forEach {
          if (it is JSPsiNamedElementBase)
            result.add(it)
        }
      findModule(element, false)?.let {
        JSStubBasedPsiTreeUtil.processDeclarationsInScope(it, { element, _ ->
          if (element is JSPsiNamedElementBase)
            result.add(element)
          true
        }, false)
      }
    }
    return result
  }

}