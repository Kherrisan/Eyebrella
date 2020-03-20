package cn.kherrisan.eyebrella.core

import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

@ObsoleteCoroutinesApi
val SINGLE_THREAD_CONTEXT = newSingleThreadContext("eyebrella-single-threa-context")