// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.html.webSymbols.elements.WebSymbolElementDescriptor
import com.intellij.lang.javascript.intentions.JavaScriptIntention
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.webSymbols.PsiSourcedWebSymbol
import com.intellij.webSymbols.WebSymbol
import com.hxz.mpxjs.VueBundle
import com.hxz.mpxjs.codeInsight.extractComponentSymbol
import com.hxz.mpxjs.codeInsight.toAsset
import com.hxz.mpxjs.inspections.quickfixes.VueImportComponentQuickFix
import com.hxz.mpxjs.model.VueModelVisitor
import com.hxz.mpxjs.web.VueWebSymbolsQueryConfigurator
import com.hxz.mpxjs.web.symbols.VueComponentSymbol

class VueImportComponentIntention : JavaScriptIntention(), HighPriorityAction {

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
    element.node.elementType == XmlElementType.XML_NAME
    && element.parent.asSafely<XmlTag>()
      ?.descriptor.asSafely<WebSymbolElementDescriptor>()
      ?.symbol
      ?.extractComponentSymbol()
      ?.takeIf { it.getElementToImport() != null }
      ?.properties?.let {
        it[VueWebSymbolsQueryConfigurator.PROP_VUE_COMPOSITION_COMPONENT] == true
        || it[VueWebSymbolsQueryConfigurator.PROP_VUE_PROXIMITY].let { proximity ->
          proximity == null || (proximity != VueModelVisitor.Proximity.LOCAL && proximity != VueModelVisitor.Proximity.OUT_OF_SCOPE)
        }
      } == true

  override fun getFamilyName(): String =
    VueBundle.message("vue.template.intention.import.component.family.name")

  override fun getText(): String =
    VueBundle.message("vue.template.intention.import.component.family.name")

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val tag = element.parent as? XmlTag ?: return
    val elementToImport = tag.descriptor.asSafely<WebSymbolElementDescriptor>()
                   ?.symbol
                   ?.extractComponentSymbol()
                   ?.getElementToImport()

                 ?: return
    VueImportComponentQuickFix(element, toAsset(tag.name), elementToImport).applyFix()
  }

  private fun WebSymbol.getElementToImport() =
    this.asSafely<PsiSourcedWebSymbol>()
      ?.let {
        if (it is VueComponentSymbol)
          it.rawSource
        else
          it.source
      }
      ?.takeIf { it !is JSLiteralExpression }
}