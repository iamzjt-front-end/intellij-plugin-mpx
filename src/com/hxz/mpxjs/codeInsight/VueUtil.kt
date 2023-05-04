// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.hxz.mpxjs.codeInsight

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ecmascript6.psi.ES6ImportCall
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.ecmascript6.psi.JSExportAssignment
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.ecmascript6.resolve.JSFileReferencesUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.index.JSSymbolUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptAsExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptVariable
import com.intellij.lang.javascript.psi.impl.JSPsiImplUtils
import com.intellij.lang.javascript.psi.resolve.JSClassResolver
import com.intellij.lang.javascript.psi.resolve.QualifiedItemProcessor
import com.intellij.lang.javascript.psi.stubs.JSImplicitElement
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.evaluable.JSApplyNewType
import com.intellij.lang.javascript.psi.types.evaluable.JSReturnedExpressionType
import com.intellij.lang.javascript.psi.types.primitives.JSPrimitiveType
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil
import com.intellij.lang.javascript.psi.util.JSStubBasedPsiTreeUtil.isStubBased
import com.intellij.lang.typescript.psi.TypeScriptPsiUtil
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.*
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ObjectUtils.tryCast
import com.intellij.util.asSafely
import com.intellij.util.text.SemVer
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbol.Companion.NAMESPACE_HTML
import com.intellij.webSymbols.utils.unwrapMatchedSymbols
import com.hxz.mpxjs.index.findModule
import com.hxz.mpxjs.index.findScriptTag
import com.hxz.mpxjs.index.resolveLocally
import com.hxz.mpxjs.lang.expr.psi.VueJSEmbeddedExpressionContent
import com.hxz.mpxjs.lang.html.VueLanguage
import com.hxz.mpxjs.model.VueComponent
import com.hxz.mpxjs.model.VueEntitiesContainer
import com.hxz.mpxjs.model.VueModelProximityVisitor
import com.hxz.mpxjs.model.VueModelVisitor
import com.hxz.mpxjs.model.source.PROPS_REQUIRED_PROP
import com.hxz.mpxjs.model.source.PROPS_TYPE_PROP
import com.hxz.mpxjs.types.asCompleteType
import com.hxz.mpxjs.web.VueWebSymbolsQueryConfigurator
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import com.hxz.mpxjs.model.source.VueComponents.Companion.isDefineComponentOrVueExtendCall

const val LANG_ATTRIBUTE_NAME = "lang"
const val SETUP_ATTRIBUTE_NAME = "setup"
const val REF_ATTRIBUTE_NAME = "ref"
const val MODULE_ATTRIBUTE_NAME = "module"
const val ATTR_DIRECTIVE_PREFIX = "v-"
const val ATTR_EVENT_SHORTHAND = '@'
const val ATTR_SLOT_SHORTHAND = '#'
const val ATTR_ARGUMENT_PREFIX = ':'
const val ATTR_MODIFIER_PREFIX = '.'
const val ATTR_EVENT_PREFIX = "bind"

const val VITE_PKG = "vite"

val VUE_NOTIFICATIONS: NotificationGroup
  get() = NotificationGroupManager.getInstance().getNotificationGroup("Mpx")

fun fromAsset(name: String, hyphenBeforeDigit: Boolean = false): String {
  val result = StringBuilder()
  for (ch in name) {
    when {
      ch.isUpperCase() -> {
        if (result.isNotEmpty()
            && result.last() != '-') {
          result.append('-')
        }
        result.append(StringUtil.toLowerCase(ch))
      }
      ch in '0'..'9' -> {
        if (hyphenBeforeDigit
            && result.isNotEmpty()
            && result.last() != '-') {
          result.append('-')
        }
        result.append(ch)
      }
      else -> result.append(ch)
    }
  }
  return result.toString()
}

fun toAsset(name: String, capitalized: Boolean = false): String {
  val result = StringBuilder()
  var nextCapitalized = capitalized
  for (ch in name) {
    when {
      ch == '-' -> nextCapitalized = true
      nextCapitalized -> {
        result.append(StringUtil.toUpperCase(ch))
        nextCapitalized = false
      }
      else -> result.append(ch)
    }
  }
  return result.toString()
}

fun JSPsiNamedElementBase.resolveIfImportSpecifier(): JSPsiNamedElementBase =
  (this as? ES6ImportSpecifier)
    ?.multiResolve(false)
    ?.asSequence()
    ?.mapNotNull { it.takeIf { it.isValidResult }?.element as? JSPsiNamedElementBase }
    ?.firstOrNull()
  ?: this

private val QUOTES = setOf('\'', '"', '`')

fun es6Unquote(s: String): String {
  if (s.length < 2) return s
  if (QUOTES.contains(s[0]) && s.endsWith(s[0])) return s.substring(1, s.length - 1)
  return s
}

fun getStringLiteralsFromInitializerArray(holder: PsiElement): List<JSLiteralExpression> {
  return JSStubBasedPsiTreeUtil.findDescendants<JSLiteralExpression>(
    holder, TokenSet.create(JSStubElementTypes.LITERAL_EXPRESSION,
                            JSStubElementTypes.STRING_TEMPLATE_EXPRESSION))
    .filter {
      val context = it.context
      !it.significantValue.isNullOrBlank() &&
      QUOTES.contains(it.significantValue!![0]) &&
      ((context is JSArrayLiteralExpression) && (context.context == holder) || context == holder)
    }
}

@StubSafe
fun getTextIfLiteral(holder: PsiElement?): String? =
  (if (holder is JSReferenceExpression) {
    resolveLocally(holder).mapNotNull { (it as? JSVariable)?.initializerOrStub }.firstOrNull()
  }
  else holder)
    ?.asSafely<JSLiteralExpression>()
    ?.let { literalExpr ->
      when {
        (literalExpr as? StubBasedPsiElement<*>)?.stub != null -> literalExpr.significantValue?.let { es6Unquote(it) }
        literalExpr.isQuotedLiteral -> literalExpr.stringValue
        else -> null
      }
    }

fun detectLanguage(tag: XmlTag?): String? = tag?.getAttribute(LANG_ATTRIBUTE_NAME)?.value?.trim()

fun detectVueScriptLanguage(file: PsiFile): String? {
  val xmlFile = file as? XmlFile ?: return null
  val scriptTag = findScriptTag(xmlFile, false) ?: findScriptTag(xmlFile, true) ?: return null
  return detectLanguage(scriptTag)
}

fun objectLiteralFor(element: PsiElement?): JSObjectLiteralExpression? {
  return resolveElementTo(element, JSObjectLiteralExpression::class)
}

fun <T : PsiElement> resolveElementTo(element: PsiElement?, vararg classes: KClass<out T>): T? {
  val queue = ArrayDeque<PsiElement>()
  queue.add(element ?: return null)
  val visited = HashSet<PsiElement>()
  loop@ while (!queue.isEmpty()) {
    val cur = queue.removeFirst()
    if (visited.add(cur)) {
      if (cur !is JSEmbeddedContent && cur !is JSVariable && classes.any { it.isInstance(cur) }) {
        @Suppress("UNCHECKED_CAST")
        return cur as T
      }
      when (cur) {
        is JSFunction -> {
          JSStubBasedPsiTreeUtil.findReturnedExpressions(cur).asSequence()
            .filter { JSReturnedExpressionType.isCountableReturnedExpression(it) || it is ES6ImportCall }
            .toCollection(queue)
        }
        is JSInitializerOwner -> {
          ( // Try with stub
            when (cur) {
              is JSProperty -> cur.objectLiteralExpressionInitializer ?: cur.tryGetFunctionInitializer()
              is TypeScriptVariable -> cur.initializerOrStub ?: run {
                // Typed components from d.ts.
                if (cur.typeElement != null && classes.any { it.isInstance(cur) })
                  @Suppress("UNCHECKED_CAST")
                  return cur as T
                else null
              }
              is JSVariable -> cur.initializerOrStub
              else -> null
            }
            // Try extract reference name from type
            ?: JSPsiImplUtils.getInitializerReference(cur)?.let { JSStubBasedPsiTreeUtil.resolveLocally(it, cur) }
            // Most expensive solution through substitution, works with function calls
            ?: (cur as? JSTypeOwner)?.jsType?.substitute()?.sourceElement
          )?.let { queue.addLast(it) }
        }
        is PsiPolyVariantReference -> cur.multiResolve(false)
          .mapNotNullTo(queue) { if (it.isValidResult) it.element else null }
        is ES6ImportCall -> cur.resolveReferencedElements()
          .toCollection(queue)
        is JSEmbeddedContent -> {
          if (cur.context.let { tag ->
              tag is XmlTag && PsiTreeUtil.getStubChildrenOfTypeAsList(tag, XmlAttribute::class.java)
                .find { it.name == SETUP_ATTRIBUTE_NAME } != null
            }) {
            val regularScript = findModule(cur, false)
            if (regularScript != null) {
              queue.add(regularScript)
            }
            else if (classes.any { it == JSEmbeddedContent::class }) {
              @Suppress("UNCHECKED_CAST")
              return cur as T
            }
            else return null
          }
          else findDefaultExport(cur)?.let { queue.add(it) }
        }
//        else -> JSStubBasedPsiTreeUtil.calculateMeaningfulElements(cur)
//          .toCollection(queue)
      }
    }
  }
  return null
}

fun collectMembers(element: JSObjectLiteralExpression): List<Pair<String, JSElement>> {
  val result = mutableListOf<Pair<String, JSElement>>()
  val initialPropsList = element.propertiesIncludingSpreads
  val queue = ArrayDeque<JSElement>(initialPropsList.size)
  queue.addAll(initialPropsList)
  val visited = mutableSetOf<PsiElement>()
  while (queue.isNotEmpty()) {
    val property = queue.pollLast()
    if (!visited.add(property)) continue
    when (property) {
      is JSSpreadExpression -> {
        processJSTypeMembers(property.innerExpressionType).toCollection(result)
      }
      is JSProperty -> {
        if (property.name != null) {
          result.add(Pair(property.name!!, property))
        }
      }
      else -> processJSTypeMembers(JSTypeUtils.getTypeOfElement(element)).toCollection(result)
    }
  }
  return result
}

fun processJSTypeMembers(type: JSType?): List<Pair<String, JSElement>> =
  type?.asRecordType()
    ?.properties
    ?.filter { it.hasValidName() }
    ?.flatMap { prop ->
      QualifiedItemProcessor
        .getElementsForTypeMember(prop, null, false)
        .filterIsInstance<JSElement>()
        .map { Pair(prop.memberName, it) }
    }
  ?: emptyList()

val XmlTag.stubSafeAttributes: List<XmlAttribute>
  get() =
    if (isStubBased(this)) {
      PsiTreeUtil.getStubChildrenOfTypeAsList(this, XmlAttribute::class.java)
    }
    else {
      this.attributes.filter { isStubBased(it) }
    }

fun XmlTag.stubSafeGetAttribute(qname: String): XmlAttribute? =
  stubSafeAttributes.find { it.name == qname }

val JSCallExpression.stubSafeCallArguments: List<PsiElement>
  get() {
    if (isStubBased(this)) {
      (this as StubBasedPsiElementBase<*>).stub?.let { stub ->
        val methodExpr = stubSafeMethodExpression
        return stub.childrenStubs.map { it.psi }.filter { it !== methodExpr }.toList()
      }
      return arguments.filter { isStubBased(it) }.toList()
    }
    return emptyList()
  }

val JSArrayLiteralExpression.stubSafeElements: List<PsiElement>
  get() {
    if (isStubBased(this)) {
      (this as StubBasedPsiElementBase<*>).stub
        ?.childrenStubs
        ?.map { it.psi }
        ?.let { return it }
      return expressions.filter { isStubBased(it) }.toList()
    }
    return emptyList()
  }

fun getJSTypeFromPropOptions(expression: JSExpression?): JSType? =
  when (expression) {
    is JSArrayLiteralExpression -> JSCompositeTypeImpl.getCommonType(
      expression.expressions.map { getJSTypeFromConstructor(it) },
      JSTypeSource.EXPLICITLY_DECLARED, false
    )
    is JSObjectLiteralExpression -> expression.findProperty(PROPS_TYPE_PROP)
      ?.value
      ?.let {
        when (it) {
          is JSArrayLiteralExpression -> getJSTypeFromPropOptions(it)
          else -> getJSTypeFromConstructor(it)
        }
      }
    null -> null
    else -> getJSTypeFromConstructor(expression)
  }

fun JSType.fixPrimitiveTypes(): JSType =
  transformTypeHierarchy {
    if (it is JSPrimitiveType && !it.isPrimitive)
      JSNamedTypeFactory.createType(it.primitiveTypeText, it.source, it.typeContext)
    else it
  }

private fun getJSTypeFromConstructor(expression: JSExpression): JSType =
  (expression as? TypeScriptAsExpression)
    ?.type?.jsType?.asSafely<JSGenericTypeImpl>()
    ?.takeIf { (it.type as? JSTypeImpl)?.typeText == "PropType" }
    ?.arguments?.getOrNull(0)
    ?.asCompleteType()
  ?: JSApplyNewType(JSTypeofTypeImpl(expression, JSTypeSourceFactory.createTypeSource(expression, false)),
                    JSTypeSourceFactory.createTypeSource(expression.containingFile, false))

fun getRequiredFromPropOptions(expression: JSExpression?): Boolean =
  (expression as? JSObjectLiteralExpression)
    ?.findProperty(PROPS_REQUIRED_PROP)
    ?.jsType
    ?.let { type ->
      if (type is JSWidenType)
        type.originalType
      else type
    }
    ?.let { type ->
      (type as? JSBooleanLiteralTypeImpl)?.literal
    }
  ?: false

inline fun <reified T : JSExpression> XmlAttributeValue.findJSExpression(): T? {
  return findVueJSEmbeddedExpressionContent()?.firstChild as? T
}

fun XmlAttributeValue.findVueJSEmbeddedExpressionContent(): VueJSEmbeddedExpressionContent? {
  val root = when {
    language === VueLanguage.INSTANCE ->
      children.find { it is ASTWrapperPsiElement }
    textLength >= 2 ->
      InjectedLanguageManager.getInstance(project)
        .findInjectedElementAt(containingFile, textOffset + 1)
        ?.containingFile
    else -> null
  }
  return root?.firstChild?.asSafely<VueJSEmbeddedExpressionContent>()
}

fun getFirstInjectedFile(element: PsiElement?): PsiFile? {
  return element
    ?.let { InjectedLanguageManager.getInstance(element.project).getInjectedPsiFiles(element) }
    ?.asSequence()
    ?.mapNotNull { it.first as? PsiFile }
    ?.firstOrNull()
}

fun getHostFile(context: PsiElement): PsiFile? {
  val original = CompletionUtil.getOriginalOrSelf(context)
  val hostFile = FileContextUtil.getContextFile(if (original !== context) original else context.containingFile.originalFile)
  return hostFile?.originalFile
}

fun findDefaultExport(element: PsiElement?): PsiElement? =
  element?.let {
    (ES6PsiUtil.findDefaultExport(element) as? JSExportAssignment)?.stubSafeElement
    ?: findDefaultCommonJSExport(it)
  }

fun findCreatePage(element: PsiElement?): PsiElement? =
  element?.let {
    val res = PsiTreeUtil.findChildrenOfType(element, JSCallExpression::class.java)
    for (item in res) {
      if (isDefineComponentOrVueExtendCall(item)) {
        return@let item
      }
    }
    return@let null
  }

private fun findDefaultCommonJSExport(element: PsiElement): PsiElement? {
  return JSClassResolver.getInstance().findElementsByQNameIncludingImplicit(JSSymbolUtil.MODULE_EXPORTS, element.containingFile)
    .asSequence()
    .filterIsInstance<JSDefinitionExpression>()
    .mapNotNull { it.initializerOrStub }
    .firstOrNull()
}

private val resolveSymbolCache = ConcurrentHashMap<String, Key<CachedValue<*>>>()

private fun <T> computeKey(moduleName: String, symbolName: String, symbolClass: Class<T>): Key<CachedValue<T>> {
  @Suppress("UNCHECKED_CAST")
  val key: Key<CachedValue<T>> = resolveSymbolCache.computeIfAbsent("$moduleName/$symbolName/${symbolClass.simpleName}") {
    Key.create(it)
  } as Key<CachedValue<T>>
  return key
}

fun <T : PsiElement> resolveSymbolFromNodeModule(scope: PsiElement?, moduleName: String, symbolName: String, symbolClass: Class<T>): T? {
  val key = computeKey(moduleName, symbolName, symbolClass)
  val file = scope?.containingFile ?: return null
  return CachedValuesManager.getCachedValue(file, key) {
    val modules = JSFileReferencesUtil.resolveModuleReference(file, moduleName)
    val resolvedSymbols = modules
      .filterIsInstance<JSElement>()
      .let { ES6PsiUtil.resolveSymbolInModules(symbolName, file, it) }
    val suitableSymbol = resolvedSymbols
      .filter { it.element?.isValid == true }
      .mapNotNull { tryCast(it.element, symbolClass) }
      .minByOrNull { TypeScriptPsiUtil.isFromAugmentationModule(it) }

    CachedValueProvider.Result.create(suitableSymbol, PsiModificationTracker.MODIFICATION_COUNT)
  }
}

fun resolveMergedInterfaceJSTypeFromNodeModule(scope: PsiElement?, moduleName: String, symbolName: String): JSRecordType {
  val key = computeKey(moduleName, symbolName, JSRecordType::class.java)
  val file = scope?.containingFile ?: return JSTypeCastUtil.NO_RECORD_TYPE

  return CachedValuesManager.getCachedValue(file, key) {
    val modules = JSFileReferencesUtil.resolveModuleReference(file, moduleName)
    val resolvedSymbols = modules
      .filterIsInstance<JSElement>()
      .let { ES6PsiUtil.resolveSymbolInModules(symbolName, file, it) }
    val interfaces = resolvedSymbols
      .filter { it.element?.isValid == true }
      .mapNotNull { tryCast(it.element, TypeScriptInterface::class.java) }

    var typeSource: JSTypeSource? = null
    val typeMembers = mutableListOf<JSRecordType.TypeMember>()
    for (tsInterface in interfaces) {
      val singleRecord = tsInterface.jsType.asRecordType()
      typeSource = singleRecord.source
      typeMembers.addAll(singleRecord.typeMembers)
    }

    val jsRecordType = JSRecordTypeImpl(typeSource ?: JSTypeSourceFactory.createTypeSource(null, false), typeMembers)
    CachedValueProvider.Result.create(jsRecordType, PsiModificationTracker.MODIFICATION_COUNT)
  }
}

fun resolveLocalComponent(context: VueEntitiesContainer, tagName: String, containingFile: PsiFile): List<VueComponent> {
  val result = mutableListOf<VueComponent>()
  val normalizedTagName = fromAsset(tagName)
  context.acceptEntities(object : VueModelProximityVisitor() {
    override fun visitComponent(name: String, component: VueComponent, proximity: Proximity): Boolean {
      return acceptSameProximity(proximity, fromAsset(name) == normalizedTagName) {
        // Cannot self refer without export declaration with component name
        if ((component.source as? JSImplicitElement)?.context != containingFile) {
          result.add(component)
        }
      }
    }
  }, VueModelVisitor.Proximity.GLOBAL)
  return result
}

fun SemVer.withoutPreRelease() =
  if (this.preRelease != null)
    SemVer("${this.major}.${this.minor}.${this.patch}", this.major, this.minor, this.patch)
  else this

fun WebSymbol.extractComponentSymbol(): WebSymbol? =
  this.takeIf { it.namespace == NAMESPACE_HTML }
    ?.unwrapMatchedSymbols()
    ?.toList()
    ?.takeIf { it.size == 2 && it[0].pattern != null }
    ?.get(1)
    ?.takeIf { it.kind == VueWebSymbolsQueryConfigurator.KIND_VUE_COMPONENTS }
