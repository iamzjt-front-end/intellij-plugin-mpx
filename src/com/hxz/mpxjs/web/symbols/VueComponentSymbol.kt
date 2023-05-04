// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web.symbols

import com.intellij.model.Pointer
import com.intellij.navigation.NavigationTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.utils.match
import com.hxz.mpxjs.model.*
import com.hxz.mpxjs.model.source.VueCompositionApp
import com.hxz.mpxjs.model.source.VueSourceContainer
import com.hxz.mpxjs.model.source.VueSourceEntityDescriptor
import com.hxz.mpxjs.model.source.VueUnresolvedComponent
//import com.hxz.mpxjs.web.VueComponentSourceNavigationTarget
import com.hxz.mpxjs.web.VueWebSymbolsQueryConfigurator
import com.hxz.mpxjs.web.asWebSymbolPriority

class VueComponentSymbol(matchedName: String, component: VueComponent, private val vueProximity: VueModelVisitor.Proximity) :
  VueScopeElementSymbol<VueComponent>(matchedName, component) {

  private val isCompositionComponent: Boolean = VueCompositionApp.isCompositionAppComponent(component)

  val sourceDescriptor: VueSourceEntityDescriptor?
    get() = (item as? VueSourceContainer)?.descriptor

  override val kind: SymbolKind
    get() = VueWebSymbolsQueryConfigurator.KIND_VUE_COMPONENTS

  override val name: String
    get() = name

  // The source field is used for refactoring purposes by Web Symbols framework
  override val source: PsiElement?
    get() = (item as? VueRegularComponent)?.nameElement ?: item.source

  override val priority: WebSymbol.Priority
    get() = vueProximity.asWebSymbolPriority()

  override fun equals(other: Any?): Boolean =
    super.equals(other)
    && (other as VueComponentSymbol).vueProximity == vueProximity

  override fun hashCode(): Int =
    31 * super.hashCode() + vueProximity.hashCode()

  // Use actual item source field for navigation
  override fun getNavigationTargets(project: Project): Collection<NavigationTarget> = emptyList()
//    item.source?.let { listOf(VueComponentSourceNavigationTarget(it)) } ?: emptyList()

  override val properties: Map<String, Any>
    get() = mapOf(Pair(VueWebSymbolsQueryConfigurator.PROP_VUE_PROXIMITY, vueProximity), Pair(
      VueWebSymbolsQueryConfigurator.PROP_VUE_COMPOSITION_COMPONENT, isCompositionComponent))

  override fun getSymbols(namespace: SymbolNamespace,
                          kind: String,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
    if (namespace == null || namespace == WebSymbol.NAMESPACE_HTML)
      when (kind) {
        VueWebSymbolsQueryConfigurator.KIND_VUE_COMPONENT_PROPS -> {
          val props = mutableListOf<VueInputProperty>()
          // TODO ambiguous resolution in case of duplicated names
          item.acceptPropertiesAndMethods(object : VueModelVisitor() {
            override fun visitInputProperty(prop: VueInputProperty, proximity: Proximity): Boolean {
              props.add(prop)
              return true
            }
          })
          props.mapWithNameFilter(name, params, scope) { VueInputPropSymbol(it, item, this.origin) }
        }
        WebSymbol.KIND_HTML_SLOTS -> {
          (item as? VueContainer)
            ?.slots
            ?.mapWithNameFilter(name, params, scope) { VueSlotSymbol(it, item, this.origin) }
          ?: if (!name.isNullOrEmpty()
                 && ((item is VueContainer && item.template == null)
                     || item is VueUnresolvedComponent)) {
            listOf(WebSymbolMatch.create(name, listOf(WebSymbolNameSegment(0, name.length)), WebSymbol.NAMESPACE_HTML,
                                         WebSymbol.KIND_HTML_SLOTS, this.origin))
          }
          else emptyList()
        }
        VueWebSymbolsQueryConfigurator.KIND_VUE_MODEL -> {
//          (item as? VueContainer)
//            ?.collectModelDirectiveProperties()
//            ?.takeIf { it.prop != null || it.event != null }
//            ?.let { listOf(VueModelSymbol(this.origin, it)) }
//          ?: emptyList()
          emptyList()
        }
        else -> emptyList()
      }
    else if (namespace == WebSymbol.NAMESPACE_JS && kind == WebSymbol.KIND_JS_EVENTS) {
      (item as? VueContainer)
        ?.emits
        ?.mapWithNameFilter(name, params, scope) { VueEmitCallSymbol(it, item, this.origin) }
      ?: emptyList()
    }
    else emptyList()

  override fun createPointer(): Pointer<VueComponentSymbol> {
    val component = item.createPointer()
    val matchedName = this.name
    val vueProximity = this.vueProximity
    return Pointer {
      component.dereference()?.let { VueComponentSymbol(matchedName, it, vueProximity) }
    }
  }


  private fun <T> List<T>.mapWithNameFilter(name: String?,
                                            params: WebSymbolsNameMatchQueryParams,
                                            context: Stack<WebSymbolsScope>,
                                            mapper: (T) -> WebSymbol): List<WebSymbol> =
    if (name != null) {
      asSequence()
        .map(mapper)
        .flatMap { it.match(name, context, params) }
        .toList()
    }
    else this.map(mapper)
}
