package dev.kpp.derive

internal fun camelToSnake(name: String): String {
    if (name.isEmpty()) return name
    val out = StringBuilder(name.length + 4)
    for ((i, c) in name.withIndex()) {
        if (c.isUpperCase()) {
            if (i > 0) out.append('_')
            out.append(c.lowercaseChar())
        } else {
            out.append(c)
        }
    }
    return out.toString()
}

internal fun escapeJsonString(s: String): String {
    val out = StringBuilder(s.length + 2)
    out.append('"')
    for (c in s) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            '\b' -> out.append("\\b")
            '' -> out.append("\\f")
            else -> out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}

// Token types for the lexer. We keep numbers split so the parser can pick
// Long vs Double without re-scanning the lexeme.
internal sealed interface JsonToken {
    data object LBrace : JsonToken
    data object RBrace : JsonToken
    data object LBracket : JsonToken
    data object RBracket : JsonToken
    data object Colon : JsonToken
    data object Comma : JsonToken
    data object NullTok : JsonToken
    data class BoolTok(val value: Boolean) : JsonToken
    data class StringTok(val value: String) : JsonToken
    data class LongTok(val value: Long) : JsonToken
    data class DoubleTok(val value: Double) : JsonToken
    data object Eof : JsonToken
}

internal class JsonLexException(message: String) : RuntimeException(message)
internal class JsonParseException(message: String) : RuntimeException(message)

internal class JsonLexer(private val src: String) {
    private var pos: Int = 0

    fun next(): JsonToken {
        skipWs()
        if (pos >= src.length) return JsonToken.Eof
        val c = src[pos]
        return when (c) {
            '{' -> { pos++; JsonToken.LBrace }
            '}' -> { pos++; JsonToken.RBrace }
            '[' -> { pos++; JsonToken.LBracket }
            ']' -> { pos++; JsonToken.RBracket }
            ':' -> { pos++; JsonToken.Colon }
            ',' -> { pos++; JsonToken.Comma }
            '"' -> readString()
            't', 'f' -> readBool()
            'n' -> readNull()
            else -> if (c == '-' || c.isDigit()) readNumber() else
                throw JsonLexException("unexpected char '$c' at $pos")
        }
    }

    private fun skipWs() {
        while (pos < src.length) {
            val c = src[pos]
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++ else break
        }
    }

    private fun readString(): JsonToken.StringTok {
        // assumes src[pos] == '"'
        pos++
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos]
            when {
                c == '"' -> {
                    pos++
                    return JsonToken.StringTok(sb.toString())
                }
                c == '\\' -> {
                    if (pos + 1 >= src.length) throw JsonLexException("dangling escape")
                    val esc = src[pos + 1]
                    when (esc) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('')
                        'u' -> {
                            if (pos + 5 >= src.length) throw JsonLexException("bad \\u escape")
                            val hex = src.substring(pos + 2, pos + 6)
                            val code = hex.toInt(16)
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> throw JsonLexException("bad escape \\$esc")
                    }
                    pos += 2
                }
                else -> {
                    sb.append(c)
                    pos++
                }
            }
        }
        throw JsonLexException("unterminated string")
    }

    private fun readBool(): JsonToken.BoolTok {
        if (src.startsWith("true", pos)) {
            pos += 4
            return JsonToken.BoolTok(true)
        }
        if (src.startsWith("false", pos)) {
            pos += 5
            return JsonToken.BoolTok(false)
        }
        throw JsonLexException("bad literal at $pos")
    }

    private fun readNull(): JsonToken.NullTok {
        if (src.startsWith("null", pos)) {
            pos += 4
            return JsonToken.NullTok
        }
        throw JsonLexException("bad literal at $pos")
    }

    private fun readNumber(): JsonToken {
        val start = pos
        var isFloat = false
        if (src[pos] == '-') pos++
        while (pos < src.length && src[pos].isDigit()) pos++
        if (pos < src.length && src[pos] == '.') {
            isFloat = true
            pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        if (pos < src.length && (src[pos] == 'e' || src[pos] == 'E')) {
            isFloat = true
            pos++
            if (pos < src.length && (src[pos] == '+' || src[pos] == '-')) pos++
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        val lex = src.substring(start, pos)
        return if (isFloat) JsonToken.DoubleTok(lex.toDouble()) else JsonToken.LongTok(lex.toLong())
    }
}

internal class JsonParser(text: String) {
    private val lexer = JsonLexer(text)
    private var current: JsonToken = lexer.next()

    fun parseValue(): Any? {
        val tok = current
        return when (tok) {
            JsonToken.LBrace -> parseObject()
            JsonToken.LBracket -> parseArray()
            JsonToken.NullTok -> { advance(); null }
            is JsonToken.BoolTok -> { advance(); tok.value }
            is JsonToken.StringTok -> { advance(); tok.value }
            is JsonToken.LongTok -> { advance(); tok.value }
            is JsonToken.DoubleTok -> { advance(); tok.value }
            else -> throw JsonParseException("unexpected token $tok")
        }
    }

    fun parseRoot(): Any? {
        val v = parseValue()
        if (current != JsonToken.Eof) throw JsonParseException("trailing data")
        return v
    }

    private fun advance() {
        current = lexer.next()
    }

    private fun parseObject(): MutableMap<String, Any?> {
        expect(JsonToken.LBrace)
        val map = LinkedHashMap<String, Any?>()
        if (current == JsonToken.RBrace) {
            advance()
            return map
        }
        while (true) {
            val keyTok = current as? JsonToken.StringTok
                ?: throw JsonParseException("expected string key, got $current")
            advance()
            expect(JsonToken.Colon)
            val v = parseValue()
            map[keyTok.value] = v
            when (current) {
                JsonToken.Comma -> advance()
                JsonToken.RBrace -> { advance(); return map }
                else -> throw JsonParseException("expected , or } got $current")
            }
        }
    }

    private fun parseArray(): MutableList<Any?> {
        expect(JsonToken.LBracket)
        val list = ArrayList<Any?>()
        if (current == JsonToken.RBracket) {
            advance()
            return list
        }
        while (true) {
            list.add(parseValue())
            when (current) {
                JsonToken.Comma -> advance()
                JsonToken.RBracket -> { advance(); return list }
                else -> throw JsonParseException("expected , or ] got $current")
            }
        }
    }

    private fun expect(t: JsonToken) {
        if (current != t) throw JsonParseException("expected $t got $current")
        advance()
    }
}
