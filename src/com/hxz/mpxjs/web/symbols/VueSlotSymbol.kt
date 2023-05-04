// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web.symbols

import com.intellij.lang.javascript.psi.JSType
import com.intellij.model.Pointer
import com.intellij.webSymbols.SymbolKind
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolOrigin
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.WebSymbolsPatternFactory
import com.hxz.mpxjs.model.VueComponent
import com.hxz.mpxjs.model.VueContainer
import com.hxz.mpxjs.model.VueSlot

class VueSlotSymbol(slot: VueSlot,
                    owner: VueComponent,
                    origin: WebSymbolOrigin)
  : VueNamedWebSymbol<VueSlot>(slot, origin = origin, owner = owner) {

  override val pattern: WebSymbolsPattern?
    get() = item.pattern?.let { WebSymbolsPatternFactory.createRegExMatch(it, true) }

  override val kind: SymbolKind
    get() = WebSymbol.KIND_HTML_SLOTS

  override val type: JSType?
    get() = item.scope

  override fun createPointer(): Pointer<VueSlotSymbol> =
    object : NamedSymbolPointer<VueSlot, VueSlotSymbol>(this) {

      override fun locateSymbol(owner: VueComponent): VueSlot? =
        (owner as? VueContainer)?.slots?.find { it.name == name }

      override fun createWrapper(owner: VueComponent, symbol: VueSlot): VueSlotSymbol =
        VueSlotSymbol(symbol, owner, origin)

    }

}