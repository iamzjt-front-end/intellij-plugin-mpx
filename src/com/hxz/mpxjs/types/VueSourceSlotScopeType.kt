// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.types

import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.JSTypeSubstitutionContext
import com.intellij.lang.javascript.psi.JSTypeTextBuilder
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl.PropertySignatureImpl
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import com.hxz.mpxjs.codeInsight.attributes.VueAttributeNameParser
import com.hxz.mpxjs.codeInsight.stubSafeAttributes
import com.hxz.mpxjs.model.SLOT_NAME_ATTRIBUTE
import com.hxz.mpxjs.model.SLOT_TAG_NAME
import com.hxz.mpxjs.model.VueImplicitElement

class VueSourceSlotScopeType private constructor(typeSource: JSTypeSource, private val tag: XmlTag, private val slotName: String)
  : JSSimpleTypeBaseImpl(typeSource), JSCodeBasedType {

  constructor(tag: XmlTag, slotName: String) : this(JSTypeSourceFactory.createTypeSource(tag, true), tag, slotName)

  override fun copyWithNewSource(source: JSTypeSource): JSType =
    VueSourceSlotScopeType(source, tag, slotName)

  override fun isEquivalentToWithSameClass(type: JSType, context: ProcessingContext?, allowResolve: Boolean): Boolean =
    (type is VueSourceSlotScopeType && type.tag == tag)

  override fun hashCodeImpl(): Int = slotName.hashCode()

  override fun buildTypeTextImpl(format: JSType.TypeTextFormat, builder: JSTypeTextBuilder) {
    if (format == JSType.TypeTextFormat.SIMPLE) {
      builder.append("#VueSourceSlotScopeType: ").append(slotName)
      return
    }
    substitute().buildTypeText(format, builder)
  }

  override fun substituteImpl(context: JSTypeSubstitutionContext): JSType =
    tag
      .stubSafeAttributes
      .mapNotNull { attr ->
        val info = VueAttributeNameParser.parse(attr.name, SLOT_TAG_NAME)
        val bindingName = (info as? VueAttributeNameParser.VueDirectiveInfo)
          ?.takeIf { it.directiveKind == VueAttributeNameParser.VueDirectiveKind.BIND }
          ?.arguments
        if (!bindingName.isNullOrBlank() && bindingName != SLOT_NAME_ATTRIBUTE && !attr.value.isNullOrBlank()) {
          val type = VueSourceSlotBindingType(attr, bindingName)
          PropertySignatureImpl(bindingName, type, false, true,
                                VueImplicitElement(bindingName, type, attr, JSImplicitElement.Type.Property))
        }
        else null
      }
      .let { JSRecordTypeImpl(source, it) }
      .asCompleteType()

}