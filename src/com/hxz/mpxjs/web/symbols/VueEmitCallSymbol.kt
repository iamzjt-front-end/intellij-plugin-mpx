// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web.symbols

import com.intellij.find.usages.api.SearchTarget
import com.intellij.find.usages.api.UsageHandler
import com.intellij.lang.javascript.psi.JSType
import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolOrigin
import com.hxz.mpxjs.model.VueComponent
import com.hxz.mpxjs.model.VueContainer
import com.hxz.mpxjs.model.VueEmitCall

class VueEmitCallSymbol(emitCall: VueEmitCall,
                        owner: VueComponent,
                        origin: WebSymbolOrigin)
  : VueNamedWebSymbol<VueEmitCall>(emitCall, origin = origin, owner = owner), SearchTarget {

  override val namespace: SymbolNamespace
    get() = WebSymbol.NAMESPACE_JS

  override val kind: SymbolKind
    get() = WebSymbol.KIND_JS_EVENTS

  override val type: JSType?
    get() = item.eventJSType

  override val priority: WebSymbol.Priority
    get() = WebSymbol.Priority.HIGHEST

  override fun presentation(): TargetPresentation {
    return presentation
  }

  override val usageHandler: UsageHandler
    get() = UsageHandler.createEmptyUsageHandler(name)

  override fun createPointer(): Pointer<VueEmitCallSymbol> =
    object : NamedSymbolPointer<VueEmitCall, VueEmitCallSymbol>(this) {

      override fun locateSymbol(owner: VueComponent): VueEmitCall? =
        (owner as? VueContainer)?.emits?.find { it.name == name }

      override fun createWrapper(owner: VueComponent, symbol: VueEmitCall): VueEmitCallSymbol =
        VueEmitCallSymbol(symbol, owner, origin)

    }
}
