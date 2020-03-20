package cn.kherrisan.eyebrella.core

import java.math.MathContext

const val KB = 1024
const val MB = 1024 * 1024
const val GB = 1024 * 1024 * 1024

val mathContext = MathContext.DECIMAL32

fun readableSize(byte: Long): String {
    val d = byte.toBigDecimal(mathContext)
    return when {
        byte < KB -> "${d.setScale(2)}B"
        byte in (KB + 1) until MB -> "${d / KB.toBigDecimal(mathContext)}KB"
        byte in (MB + 1) until GB -> "${d / MB.toBigDecimal(mathContext)}MB"
        else -> "${d / GB.toBigDecimal(mathContext)}GB"
    }
}