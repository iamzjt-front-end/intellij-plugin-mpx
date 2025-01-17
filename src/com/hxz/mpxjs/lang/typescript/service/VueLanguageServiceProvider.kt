// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.lang.typescript.service

import com.intellij.lang.javascript.service.JSLanguageService
import com.intellij.lang.javascript.service.JSLanguageServiceProvider
import com.intellij.lang.typescript.compiler.TypeScriptLanguageServiceProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.hxz.mpxjs.lang.html.VueFileType

internal class VueLanguageServiceProvider(project: Project) : JSLanguageServiceProvider {
  private val languageService by lazy(LazyThreadSafetyMode.NONE) { project.service<ServiceWrapper>() }

  override fun getAllServices(): List<JSLanguageService> = listOf(languageService.service)

  override fun getService(file: VirtualFile): JSLanguageService? =
    languageService.service.takeIf { it.isAcceptable(file) }

  override fun isHighlightingCandidate(file: VirtualFile): Boolean {
    val type = file.fileType
    return TypeScriptLanguageServiceProvider.isJavaScriptOrTypeScriptFileType(type) || type == VueFileType.INSTANCE
  }
}

@Service
private class ServiceWrapper(project: Project) : Disposable {
  val service = VueTypeScriptService(project)

  override fun dispose() {
    Disposer.dispose(service)
  }
}