// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.lang.expr.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.javascript.types.JSParameterElementType
import com.intellij.psi.PsiElement
//import com.hxz.mpxjs.lang.expr.parser.VueJSStubElementTypes.EXTERNAL_ID_PREFIX
import com.hxz.mpxjs.lang.expr.psi.impl.VueJSScriptSetupParameterImpl

class VueJSScriptSetupParameterElementType : JSParameterElementType("MPX_SCRIPT_SETUP_PARAMETER") {
  override fun construct(node: ASTNode): PsiElement = VueJSScriptSetupParameterImpl(node)
  override fun shouldCreateStub(node: ASTNode): Boolean = false

//  override fun getExternalId(): String = EXTERNAL_ID_PREFIX + debugName
}