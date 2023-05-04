// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.lang.expr.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.impl.JSParameterImpl
import com.intellij.lang.javascript.psi.util.JSDestructuringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.hxz.mpxjs.index.findModule
import com.hxz.mpxjs.lang.expr.psi.VueJSScriptSetupParameter
import com.hxz.mpxjs.model.VueModelManager
import com.hxz.mpxjs.model.source.VueComponents
import com.hxz.mpxjs.types.VuePropsType

class VueJSScriptSetupParameterImpl(node: ASTNode) : JSParameterImpl(node), VueJSScriptSetupParameter {
  override fun hasBlockScope(): Boolean = true

  override fun getUseScope(): SearchScope =
    declarationScope?.let { LocalSearchScope(it) } ?: LocalSearchScope.EMPTY

  override fun getDeclarationScope(): PsiElement? =
    PsiTreeUtil.getContextOfType(this, XmlTag::class.java, PsiFile::class.java)

  override fun calculateType(): JSType? {
    if (this.parent.children.find { it is VueJSScriptSetupParameter } != this) return null
    return JSDestructuringUtil.getTypeFromInitializer(this) {
      VueModelManager.getComponent(VueComponents.getComponentDescriptor(
        findModule(this, false) ?: findModule(this, true)))
        ?.let { VuePropsType(it) }
    }
  }

}
