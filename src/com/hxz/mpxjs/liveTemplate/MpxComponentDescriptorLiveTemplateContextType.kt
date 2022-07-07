// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.liveTemplate

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.lang.ecmascript6.psi.JSExportAssignment
import com.intellij.lang.javascript.JavaScriptCodeContextType
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.hxz.mpxjs.VueBundle

private const val CONTEXT_TYPE = "MPX_COMPONENT_DESCRIPTOR"

class VueComponentDescriptorLiveTemplateContextType : TemplateContextType(CONTEXT_TYPE,
                                                                          VueBundle.message("vue.live.template.context.component"),
                                                                          VueBaseLiveTemplateContextType::class.java) {
  override fun isInContext(file: PsiFile, offset: Int): Boolean {
    return VueBaseLiveTemplateContextType.evaluateContext(
      file, offset,
      scriptContextEvaluator = { it is JSExportAssignment || PsiTreeUtil.getParentOfType(it, JSExportAssignment::class.java) != null },
      notVueFileType = {
        JavaScriptCodeContextType.areJavaScriptTemplatesApplicable(it) &&
        PsiTreeUtil.getParentOfType(it, JSObjectLiteralExpression::class.java) != null
      })
  }
}
