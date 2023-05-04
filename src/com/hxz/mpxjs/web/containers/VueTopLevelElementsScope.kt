// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web.containers

//import com.intellij.model.Pointer
//import com.intellij.util.containers.Stack
//import com.intellij.webSymbols.*
//import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
//import com.intellij.webSymbols.query.WebSymbolMatch
//import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
//import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
//import com.hxz.mpxjs.web.VueWebSymbolsQueryConfigurator
//
//object VueTopLevelElementsScope : WebSymbolsScope {
//
//  override fun getSymbols(namespace: SymbolNamespace?,
//                          kind: SymbolKind,
//                          name: String?,
//                          params: WebSymbolsNameMatchQueryParams,
//                          scope: Stack<WebSymbolsScope>): List<WebSymbolsScope> =
//    if (namespace == WebSymbol.NAMESPACE_HTML && kind == WebSymbol.KIND_HTML_ELEMENTS)
//      params.queryExecutor.runNameMatchQuery(
//        listOfNotNull(WebSymbol.NAMESPACE_HTML, VueWebSymbolsQueryConfigurator.KIND_VUE_TOP_LEVEL_ELEMENTS, name),
//        scope = scope,
//        virtualSymbols = params.virtualSymbols,
//        strictScope = params.strictScope,
//        abstractSymbols = params.abstractSymbols,
//      )
//        .map {
//          WebSymbolMatch.create(it.name, it.nameSegments, WebSymbol.NAMESPACE_HTML, WebSymbol.KIND_HTML_ELEMENTS, it.origin)
//        }
//    else emptyList()
//
//  override fun getCodeCompletions(namespace: SymbolNamespace?,
//                                  kind: SymbolKind,
//                                  name: String?,
//                                  params: WebSymbolsCodeCompletionQueryParams,
//                                  scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
//    if (namespace == WebSymbol.NAMESPACE_HTML && kind == WebSymbol.KIND_HTML_ELEMENTS)
//      params.queryExecutor.runCodeCompletionQuery(
//        listOfNotNull(WebSymbol.NAMESPACE_HTML, VueWebSymbolsQueryConfigurator.KIND_VUE_TOP_LEVEL_ELEMENTS, name),
//        scope = scope,
//        position = params.position,
//        virtualSymbols = params.virtualSymbols,
//      )
//    else emptyList()
//
//  override fun createPointer(): Pointer<out WebSymbolsScope> = Pointer.hardPointer(this)
//
//  override fun getModificationCount(): Long = 0
//
//}
