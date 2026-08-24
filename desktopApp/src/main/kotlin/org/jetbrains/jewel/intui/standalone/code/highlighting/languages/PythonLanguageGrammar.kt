@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

// Patterns adapted from plugins/textmate/lib/bundles/python/syntaxes/MagicPython.tmLanguage.json.
package org.jetbrains.jewel.intui.standalone.code.highlighting.languages

import org.jetbrains.jewel.intui.standalone.code.highlighting.LanguageGrammar
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule

/** Python fenced-code grammar supplied to Jewel's standalone highlighter. */
internal val PYTHON =
    LanguageGrammar(
        name = "python",
        aliases = listOf("py", "py3", "python3"),
        rules = listOf(
            TokenRule.comment("#[^\\n]*"),
            TokenRule.string("(?:[rRuUbBfF]{0,2})?\"\"\"[\\s\\S]*?\"\"\""),
            TokenRule.string("(?:[rRuUbBfF]{0,2})?'''[\\s\\S]*?'''"),
            TokenRule.string("(?:[rRuUbBfF]{0,2})?\"(?:[^\"\\\\]|\\\\.)*\""),
            TokenRule.string("(?:[rRuUbBfF]{0,2})?'(?:[^'\\\\]|\\\\.)*'"),
            TokenRule.functionDeclaration("\\b((?:async\\s+)?def)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
            TokenRule.typeDeclaration("\\b(class)\\s+([A-Za-z_][A-Za-z0-9_]*)"),
            TokenRule.keyword(
                "\\b(async|await|as|assert|break|continue|def|del|elif|else|except|finally|for|from|" +
                    "global|if|import|in|is|lambda|match|case|nonlocal|pass|raise|return|try|while|with|yield)\\b",
            ),
            TokenRule.constant("\\b(True|False|None|NotImplemented|Ellipsis)\\b"),
            TokenRule.type(
                "\\b(bool|bytes|bytearray|complex|dict|float|frozenset|int|list|memoryview|object|range|set|str|tuple|type)\\b",
            ),
            TokenRule.builtin("\\b(abs|all|any|enumerate|filter|isinstance|len|map|max|min|open|print|range|reversed|sum|zip)\\b"),
            TokenRule.functionCall("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?=\\()"),
            TokenRule.number("\\b0[xX][0-9a-fA-F_]+\\b"),
            TokenRule.number("\\b0[bB][01_]+\\b"),
            TokenRule.number("\\b0[oO][0-7_]+\\b"),
            TokenRule.number("\\b[0-9][0-9_]*(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?[jJ]?\\b"),
        ),
    )
