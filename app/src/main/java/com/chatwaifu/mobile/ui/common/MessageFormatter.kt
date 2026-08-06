/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chatwaifu.mobile.ui.common

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp

// Regex containing the syntax tokens
val symbolPattern by lazy {
    Regex("""(https?://[^\s\t\n]+)|(`[^`]+`)|(@\w+)|(\*[\w]+\*)|(_[\w]+_)|(~[\w]+~)""")
}

// 原先是 Pair<AnnotatedString, StringAnnotation?>，配 ClickableText 的 getStringAnnotations 用。
// ClickableText 已废弃，改成直接携带 LinkAnnotation，由 Text 自己处理点击。
// 随之删掉的 SymbolAnnotationType 枚举（PERSON / LINK）也不再需要 —— 类型信息现在体现在
// LinkAnnotation 的子类上：Url 交给 UriHandler，Clickable 走自定义回调。
typealias SymbolAnnotation = Pair<AnnotatedString, LinkAnnotation?>

/**
 * Format a message following Markdown-lite syntax
 * | @username -> bold, primary color and clickable element
 * | http(s)://... -> clickable link, opening it into the browser
 * | *bold* -> bold
 * | _italic_ -> italic
 * | ~strikethrough~ -> strikethrough
 * | `MyClass.myMethod` -> inline code styling
 *
 * @param text contains message to be parsed
 * @param authorClicked 点到 @username 时回调，用户名不含 @
 * @return AnnotatedString，链接与 @提及 以 LinkAnnotation 内嵌，可直接交给 Text 渲染
 */
@Composable
fun messageFormatter(
    text: String,
    primary: Boolean,
    authorClicked: (String) -> Unit
): AnnotatedString {
    val tokens = symbolPattern.findAll(text)

    return buildAnnotatedString {

        var cursorPosition = 0

        val codeSnippetBackground =
            if (primary) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surface
            }

        for (token in tokens) {
            append(text.slice(cursorPosition until token.range.first))

            val (annotatedString, link) = getSymbolAnnotation(
                matchResult = token,
                colorScheme = MaterialTheme.colorScheme,
                primary = primary,
                codeSnippetBackground = codeSnippetBackground,
                authorClicked = authorClicked
            )
            // withLink 把 link 的范围锚在**实际 append 进去的内容**上。
            // 老写法是 addStringAnnotation(start = matchResult.range.first, ...)，
            // 用的是原始文本下标 —— 但 *bold* / `code` 这类 token 在构建时被 trim 掉了包裹符，
            // 前面只要出现过一个这种 token，后面链接的标注范围就会整体错位。
            // 换成 withLink 之后这个存量 bug 顺带没了。
            if (link != null) {
                withLink(link) { append(annotatedString) }
            } else {
                append(annotatedString)
            }

            cursorPosition = token.range.last + 1
        }

        if (!tokens.none()) {
            append(text.slice(cursorPosition..text.lastIndex))
        } else {
            append(text)
        }
    }
}

/**
 * Map regex matches found in a message with supported syntax symbols
 *
 * @param matchResult is a regex result matching our syntax symbols
 * @return pair of AnnotatedString with an optional LinkAnnotation to wrap it in
 */
private fun getSymbolAnnotation(
    matchResult: MatchResult,
    colorScheme: ColorScheme,
    primary: Boolean,
    codeSnippetBackground: Color,
    authorClicked: (String) -> Unit
): SymbolAnnotation {
    return when (matchResult.value.first()) {
        '@' -> {
            val author = matchResult.value.substring(1)
            SymbolAnnotation(
                AnnotatedString(
                    text = matchResult.value,
                    spanStyle = SpanStyle(
                        color = if (primary) colorScheme.inversePrimary else colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                ),
                // 非 URL 的可点击区域用 Clickable，tag 只是标识，实际动作在 listener 里
                LinkAnnotation.Clickable(
                    tag = author,
                    linkInteractionListener = { authorClicked(author) }
                )
            )
        }
        '*' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('*'),
                spanStyle = SpanStyle(fontWeight = FontWeight.Bold)
            ),
            null
        )
        '_' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('_'),
                spanStyle = SpanStyle(fontStyle = FontStyle.Italic)
            ),
            null
        )
        '~' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('~'),
                spanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
            ),
            null
        )
        '`' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value.trim('`'),
                spanStyle = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    background = codeSnippetBackground,
                    baselineShift = BaselineShift(0.2f)
                )
            ),
            null
        )
        'h' -> SymbolAnnotation(
            AnnotatedString(
                text = matchResult.value,
                spanStyle = SpanStyle(
                    color = if (primary) colorScheme.inversePrimary else colorScheme.primary
                )
            ),
            // Url 不需要自己接 UriHandler，Text 会用 LocalUriHandler 打开
            LinkAnnotation.Url(url = matchResult.value)
        )
        else -> SymbolAnnotation(AnnotatedString(matchResult.value), null)
    }
}
