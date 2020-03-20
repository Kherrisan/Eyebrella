package cn.kherrisan.eyebrella.entity

import cn.kherrisan.eyebrella.core.common.Open

@Open
data class OrderResult(
        val oid: String,
        val result: Boolean = true,
        val errMsg: String = ""
)