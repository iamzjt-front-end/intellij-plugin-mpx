// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web

import com.intellij.webSymbols.query.WebSymbolsQueryConfigurator
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.util.SmartList
import com.intellij.webSymbols.context.WebSymbolsContext
//import com.hxz.mpxjs.lang.html.VueFileType
import com.hxz.mpxjs.model.*
import com.hxz.mpxjs.model.source.VueCompositionApp
import com.hxz.mpxjs.model.source.VueSourceComponent
import com.hxz.mpxjs.web.containers.VueAvailableSlotsScope
import com.hxz.mpxjs.web.containers.VueCodeModelSymbolsScope
import com.hxz.mpxjs.web.containers.VueIncorrectlySelfReferredComponentFilteringScope
//import com.hxz.mpxjs.web.containers.VueTopLevelElementsScope

class VueWebSymbolsQueryConfigurator : WebSymbolsQueryConfigurator {

  companion object {
    const val KIND_VUE_TOP_LEVEL_ELEMENTS = "mpx-file-top-elements"
    const val KIND_VUE_COMPONENTS = "vue-components"
    const val KIND_VUE_COMPONENT_PROPS = "properties"
    const val KIND_VUE_DIRECTIVES = "vue-directives"
    const val KIND_VUE_AVAILABLE_SLOTS = "vue-available-slots"
    const val KIND_VUE_MODEL = "vue-model"
    const val KIND_VUE_DIRECTIVE_ARGUMENT = "argument"
    const val KIND_VUE_DIRECTIVE_MODIFIERS = "modifiers"

    const val PROP_VUE_MODEL_PROP = "prop"
    const val PROP_VUE_MODEL_EVENT = "event"

    const val PROP_VUE_PROXIMITY = "x-vue-proximity"
    const val PROP_VUE_COMPOSITION_COMPONENT = "x-vue-composition-component"

  }

  override fun getScope(project: Project,
                        element: PsiElement?,
                        context: WebSymbolsContext,
                        allowResolve: Boolean): List<WebSymbolsScope> {
    if (context.framework != VueFramework.ID || element == null) return emptyList()
    val result = SmartList<WebSymbolsScope>()
    val tag = (element as? XmlAttribute)?.parent ?: element as? XmlTag
    val fileContext = element.containingFile?.originalFile ?: return emptyList()

    if (allowResolve) {
      addEntityContainers(element, fileContext, result)
      tag?.let { result.add(VueAvailableSlotsScope(it)) }
    }

    // Top level tags
//    if (tag != null && tag.parentTag == null && fileContext.virtualFile?.fileType == VueFileType.INSTANCE) {
//      result.add(VueTopLevelElementsScope)
//    }

    return result
  }

  private fun addEntityContainers(element: PsiElement,
                                  fileContext: PsiFile,
                                  result: SmartList<WebSymbolsScope>) {
    VueModelManager.findEnclosingContainer(element).let { enclosingContainer ->
      val containerToProximity = mutableMapOf<VueEntitiesContainer, VueModelVisitor.Proximity>()

      containerToProximity[enclosingContainer] = VueModelVisitor.Proximity.LOCAL

      enclosingContainer.parents.forEach { parent ->
        when (parent) {
          is VueApp -> containerToProximity[parent] = VueModelVisitor.Proximity.APP
          is VuePlugin -> containerToProximity[parent] = VueModelVisitor.Proximity.PLUGIN
        }
      }

      enclosingContainer.global?.let { global ->
        val apps = containerToProximity.keys.filterIsInstance<VueApp>()
        global.plugins.forEach { plugin ->
          containerToProximity.computeIfAbsent(plugin) {
            apps.maxOfOrNull { it.getProximity(plugin) } ?: plugin.defaultProximity
          }
        }
        containerToProximity[global] = VueModelVisitor.Proximity.GLOBAL
      }

      if (enclosingContainer is VueSourceComponent && containerToProximity.keys.none { it is VueApp }) {
        enclosingContainer.global?.apps?.filterIsInstance<VueCompositionApp>()?.forEach {
          containerToProximity[it] = VueModelVisitor.Proximity.OUT_OF_SCOPE
        }
      }

      containerToProximity.forEach { (container, proximity) ->
        VueCodeModelSymbolsScope.create(container, proximity)
          ?.let {
            if (container == enclosingContainer || container is VueGlobal) {
              VueIncorrectlySelfReferredComponentFilteringScope(it, fileContext)
            }
            else it
          }
          ?.let {
            result.add(it)
          }
      }
    }
  }

}
