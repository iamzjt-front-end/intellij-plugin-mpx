// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.web.symbols

import com.intellij.model.Pointer
import com.intellij.webSymbols.WebSymbolOrigin
import com.hxz.mpxjs.model.VueComponent
import com.hxz.mpxjs.model.VueNamedSymbol

abstract class VueNamedWebSymbol<T : VueNamedSymbol>(item: T,
                                                     protected val owner: VueComponent,
                                                     override val origin: WebSymbolOrigin)
  : VueDocumentedItemSymbol<T>(item.name, item) {

  override val name: String
    get() = item.name

  abstract override fun createPointer(): Pointer<out VueNamedWebSymbol<T>>

  abstract class NamedSymbolPointer<T : VueNamedSymbol, S : VueNamedWebSymbol<T>>(wrapper: S) : Pointer<S> {
    val name = wrapper.item.name
    val origin = wrapper.origin
    private val owner = wrapper.owner.createPointer()

    override fun dereference(): S? =
      owner.dereference()?.let { component ->
        locateSymbol(component)
          ?.let { createWrapper(component, it) }
      }

    abstract fun locateSymbol(owner: VueComponent): T?

    abstract fun createWrapper(owner: VueComponent, symbol: T): S

  }

}