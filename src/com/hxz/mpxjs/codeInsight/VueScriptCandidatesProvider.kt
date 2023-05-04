// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.codeInsight

import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportCandidatesBase
import com.intellij.lang.javascript.modules.imports.providers.JSCandidatesProcessor
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.hxz.mpxjs.lang.html.VueFileType
import com.hxz.mpxjs.model.source.DEFINE_EMITS_FUN
import com.hxz.mpxjs.model.source.DEFINE_EXPOSE_FUN
import com.hxz.mpxjs.model.source.DEFINE_PROPS_FUN
import com.hxz.mpxjs.model.source.WITH_DEFAULTS_FUN
import java.util.function.Predicate

internal val SCRIPT_SETUP_API = setOf(
  DEFINE_PROPS_FUN,
  DEFINE_EMITS_FUN,
  DEFINE_EXPOSE_FUN,
  WITH_DEFAULTS_FUN,
)

class VueScriptCandidatesProvider(placeInfo: JSImportPlaceInfo) : JSImportCandidatesBase(placeInfo) {
  override fun getNames(keyFilter: Predicate<in String>): Set<String> {
    return SCRIPT_SETUP_API.filter { keyFilter.test(it) }.toSet()
  }

  override fun processCandidates(ref: String, processor: JSCandidatesProcessor) {
    if (SCRIPT_SETUP_API.contains(ref)) {
      processor.remove(ref)
    }
  }

  companion object : JSImportCandidatesProvider.CandidatesFactory {
    override fun createProvider(placeInfo: JSImportPlaceInfo): JSImportCandidatesProvider? {
      return if (placeInfo.file.fileType == VueFileType.INSTANCE) VueScriptCandidatesProvider(placeInfo) else null
    }
  }
}