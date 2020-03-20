package cn.kherrisan.eyebrella.core.common

import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

fun TopCoroutineScope(): CoroutineScope = CoroutineScope(Job())