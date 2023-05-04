// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.hxz.mpxjs.lang.expr.parser

import com.intellij.lang.ecmascript6.parsing.ES6FunctionParser
import com.intellij.lang.ecmascript6.parsing.ES6Parser
import com.intellij.lang.javascript.JSElementTypes
import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.parsing.JavaScriptParserBase
import com.intellij.psi.tree.IElementType
import com.hxz.mpxjs.VueBundle
import com.hxz.mpxjs.codeInsight.attributes.VueAttributeNameParser

class VueJSExtraParser(parser: ES6Parser<*, *, *, *>,
                       private val parseFilterOptional: () -> Boolean) : JavaScriptParserBase<ES6Parser<*, *, *, *>>(parser) {
  private val statementParser get() = myJavaScriptParser.statementParser

  fun parseEmbeddedExpression(attributeInfo: VueAttributeNameParser.VueAttributeInfo?) {
    when (attributeInfo?.kind) {
      VueAttributeNameParser.VueAttributeKind.DIRECTIVE -> {
        when ((attributeInfo as VueAttributeNameParser.VueDirectiveInfo).directiveKind) {
//          VueAttributeNameParser.VueDirectiveKind.FOR -> parseVFor()
          VueAttributeNameParser.VueDirectiveKind.BIND -> parseVBind()
          VueAttributeNameParser.VueDirectiveKind.ON -> parseVOn()
          VueAttributeNameParser.VueDirectiveKind.SLOT -> parseSlotPropsExpression()
          else -> parseRegularExpression()
        }
      }
      VueAttributeNameParser.VueAttributeKind.SLOT_SCOPE -> parseSlotPropsExpression()
      VueAttributeNameParser.VueAttributeKind.SCOPE -> parseSlotPropsExpression()
      VueAttributeNameParser.VueAttributeKind.SCRIPT_SETUP -> parseScriptSetupExpression()
      else -> parseRegularExpression()
    }
  }

  private fun parseRegularExpression() {
    if (!parseFilterOptional() && !builder.eof()) {
      val mark = builder.mark()
      builder.advanceLexer()
      mark.error(JavaScriptBundle.message("javascript.parser.message.expected.expression"))
      parseRest(true)
    }
  }

  private fun parseVOn() {
    while (!builder.eof()) {
      if (builder.tokenType === JSTokenTypes.SEMICOLON) {
        builder.advanceLexer()
      }
      else if (!statementParser.parseExpressionStatement()) {
        builder.error(JavaScriptBundle.message("javascript.parser.message.expected.expression"))
        if (!builder.eof()) {
          builder.advanceLexer()
        }
      }
    }
  }

  private fun parseVBind() {
    if (!parseFilterOptional()) {
      val mark = builder.mark()
      if (!builder.eof()) {
        builder.advanceLexer()
      }
      mark.error(JavaScriptBundle.message("javascript.parser.message.expected.expression"))
      parseRest(true)
    }
  }

  private fun parseVFor() {
    val vForExpr = builder.mark()
    if (builder.tokenType == JSTokenTypes.LPAR) {
      parseVForVariables()
    }
    else if (!parseVariableStatement(VueJSStubElementTypes.V_FOR_VARIABLE)) {
      val marker = builder.mark()
      if (!builder.eof()
          && builder.tokenType !== JSTokenTypes.IN_KEYWORD
          && builder.tokenType !== JSTokenTypes.OF_KEYWORD) {
        builder.advanceLexer()
      }
      marker.error(JavaScriptBundle.message("javascript.parser.message.expected.identifier"))
    }
    if (builder.tokenType !== JSTokenTypes.IN_KEYWORD && builder.tokenType !== JSTokenTypes.OF_KEYWORD) {
      builder.error(VueBundle.message("vue.parser.message.expected.in.or.of"))
    }
    else {
      builder.advanceLexer()
    }
    myJavaScriptParser.expressionParser.parseExpression()
    vForExpr.done(VueJSElementTypes.V_FOR_EXPRESSION)
  }

  private fun parseSlotPropsExpression() {
    parseParametersExpression(VueJSElementTypes.SLOT_PROPS_EXPRESSION, VueJSStubElementTypes.SLOT_PROPS_PARAMETER)
  }

  private fun parseScriptSetupExpression() {
    parseParametersExpression(VueJSElementTypes.SCRIPT_SETUP_EXPRESSION, VueJSStubElementTypes.SCRIPT_SETUP_PARAMETER)
  }

  private fun parseParametersExpression(exprType: IElementType, paramType: IElementType) {
    val parametersList = builder.mark()
    val functionParser = object : ES6FunctionParser<ES6Parser<*, *, *, *>>(myJavaScriptParser) {
      override fun getParameterType(): IElementType = paramType
    }
    var first = true
    while (!builder.eof()) {
      if (first) {
        first = false
      }
      else {
        if (builder.tokenType === JSTokenTypes.COMMA) {
          builder.advanceLexer()
        }
        else {
          builder.error(VueBundle.message("vue.parser.message.expected.comma.or.end.of.expression"))
          break
        }
      }
      val parameter = builder.mark()
      if (builder.tokenType === JSTokenTypes.DOT_DOT_DOT) {
        builder.advanceLexer()
      }
      else if (builder.tokenType === JSTokenTypes.DOT) {
        // incomplete ...args
        builder.error(JavaScriptBundle.message("javascript.parser.message.expected.parameter.name"))
        while (builder.tokenType === JSTokenTypes.DOT) {
          builder.advanceLexer()
        }
      }
      functionParser.parseSingleParameter(parameter)
    }
    parametersList.done(JSStubElementTypes.PARAMETER_LIST)
    parametersList.precede().done(exprType)
  }

  internal fun parseRest(initialReported: Boolean = false) {
    var reported = initialReported
    while (!builder.eof()) {
      if (builder.tokenType === JSTokenTypes.SEMICOLON) {
        val mark = builder.mark()
        builder.advanceLexer()
        mark.error(VueBundle.message("vue.parser.message.statements.not.allowed"))
        reported = true
      }
      else {
        var justReported = false
        if (!reported) {
          builder.error(VueBundle.message("vue.parser.message.expected.end.of.expression"))
          reported = true
          justReported = true
        }
        if (!myJavaScriptParser.expressionParser.parseExpressionOptional()) {
          if (reported && !justReported) {
            val mark = builder.mark()
            builder.advanceLexer()
            mark.error(JavaScriptBundle.message("javascript.parser.message.expected.expression"))
          }
          else {
            builder.advanceLexer()
          }
        }
        else {
          reported = false
        }
      }
    }
  }

  private fun parseVariableStatement(elementType: IElementType): Boolean {
    val statement = builder.mark()
    if (parseVariable(elementType)) {
      statement.done(JSStubElementTypes.VAR_STATEMENT)
      return true
    }
    else {
      statement.drop()
      return false
    }
  }

  private fun parseVariable(elementType: IElementType): Boolean {
    if (isIdentifierToken(builder.tokenType)) {
      myJavaScriptParser.buildTokenElement(elementType)
      return true
    }
//    else if (myJavaScriptParser.functionParser.willParseDestructuringAssignment()) {
//      myJavaScriptParser.expressionParser.parseDestructuringElement(VueJSStubElementTypes.V_FOR_VARIABLE, false, false)
//      return true
//    }
    return false
  }

  private val EXTRA_VAR_COUNT = 2
  private fun parseVForVariables() {
    val parenthesis = builder.mark()
    builder.advanceLexer() //LPAR
    val varStatement = builder.mark()
    if (parseVariable(VueJSStubElementTypes.V_FOR_VARIABLE)) {
      var i = 0
      while (builder.tokenType == JSTokenTypes.COMMA && i < EXTRA_VAR_COUNT) {
        builder.advanceLexer()
        if (isIdentifierToken(builder.tokenType)) {
          myJavaScriptParser.buildTokenElement(VueJSStubElementTypes.V_FOR_VARIABLE)
        }
        i++
      }
    }
    if (builder.tokenType != JSTokenTypes.RPAR) {
      builder.error(JavaScriptBundle.message("javascript.parser.message.expected.rparen"))
      while (!builder.eof()
             && builder.tokenType != JSTokenTypes.RPAR
             && builder.tokenType != JSTokenTypes.IN_KEYWORD
             && builder.tokenType != JSTokenTypes.OF_KEYWORD) {
        builder.advanceLexer()
      }
      if (builder.tokenType != JSTokenTypes.RPAR) {
        varStatement.done(JSStubElementTypes.VAR_STATEMENT)
        parenthesis.done(JSElementTypes.PARENTHESIZED_EXPRESSION)
        return
      }
    }
    varStatement.done(JSStubElementTypes.VAR_STATEMENT)
    builder.advanceLexer()
    parenthesis.done(JSElementTypes.PARENTHESIZED_EXPRESSION)
  }
}
