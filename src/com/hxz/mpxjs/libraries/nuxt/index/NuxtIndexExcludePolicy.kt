// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.libraries.nuxt.index

import com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.hxz.mpxjs.libraries.nuxt.NUXT_CONFIG_NAMES
import com.hxz.mpxjs.libraries.nuxt.NUXT_OUTPUT_FOLDER
import com.hxz.mpxjs.libraries.nuxt.library.NuxtDotFolderSyntheticLibraryProvider

/**
 * @see NuxtDotFolderSyntheticLibraryProvider
 */
class NuxtIndexExcludePolicy(val project: Project) : DirectoryIndexExcludePolicy {

  override fun getExcludeUrlsForProject(): Array<String> {
    return PackageJsonFileManager.getInstance(project)
      .validPackageJsonFiles
      .asSequence()
      .mapNotNull { it.parent }
      .filter { parent -> NUXT_CONFIG_NAMES.any { parent.findChild(it) != null } }
      .map { "${it.url}/${NUXT_OUTPUT_FOLDER}" }
      .toList()
      .toTypedArray()
  }
}