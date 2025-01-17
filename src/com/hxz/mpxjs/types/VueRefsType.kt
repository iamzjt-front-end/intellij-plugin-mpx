// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.types

import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.types.*
import com.intellij.util.ProcessingContext
import com.hxz.mpxjs.codeInsight.REF_ATTRIBUTE_NAME
import com.hxz.mpxjs.index.findAttribute
import com.hxz.mpxjs.lang.html.psi.VueRefAttribute
import com.hxz.mpxjs.model.VueInstanceOwner
import com.hxz.mpxjs.model.VueNamedEntity
import com.hxz.mpxjs.model.VueRegularComponent
import com.hxz.mpxjs.model.getDefaultVueComponentInstanceType
import com.hxz.mpxjs.model.source.INSTANCE_REFS_PROP

class VueRefsType(source: JSTypeSource,
                  private val instanceOwner: VueInstanceOwner) : JSSimpleTypeBaseImpl(source), JSCodeBasedType, VueCompleteType {

  override fun copyWithNewSource(source: JSTypeSource): JSType = VueRefsType(source, instanceOwner)

  override fun hashCodeImpl(): Int = instanceOwner.hashCode()

  override fun isEquivalentToWithSameClass(type: JSType, context: ProcessingContext?, allowResolve: Boolean): Boolean =
    type is VueRefsType
    && type.instanceOwner == instanceOwner

  override fun buildTypeTextImpl(format: JSType.TypeTextFormat, builder: JSTypeTextBuilder) {
    if (format == JSType.TypeTextFormat.SIMPLE) {
      builder.append("#VueRefsType: ")
        .append(instanceOwner.javaClass.simpleName)
      if (instanceOwner is VueNamedEntity) {
        builder.append("(").append(instanceOwner.defaultName).append(")")
      }
      return
    }
    substitute().buildTypeText(format, builder)
  }

  override fun substituteImpl(context: JSTypeSubstitutionContext): JSType {
    val members: MutableMap<String, JSRecordType.TypeMember> = mutableMapOf()
    (instanceOwner as? VueRegularComponent)?.template?.safeVisitTags { tag ->
      (findAttribute(tag, REF_ATTRIBUTE_NAME) as? VueRefAttribute)
        ?.implicitElement
        ?.let {
          if (it is JSTypeOwner) {
            // For multiple elements with the same ref name, the last one is taken by Vue engine
            members[it.name] = JSRecordTypeImpl.PropertySignatureImpl(it.name, it.jsType, false, true, it)
          }
        }
    }
    getDefaultVueComponentInstanceType(instanceOwner.source)
      ?.asRecordType()
      ?.findPropertySignature(INSTANCE_REFS_PROP)
      ?.jsType
      ?.asRecordType()
      ?.findIndexer(JSRecordType.IndexSignatureKind.STRING)
      ?.let { members[""] = it }
    return JSSimpleRecordTypeImpl(source, members.values.toList())
  }

}