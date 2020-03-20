package cn.kherrisan.eyebrella.core.common

import org.apache.logging.log4j.LogManager
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
@Lazy(false)
class SpringContainer : ApplicationContextAware {

    private val logger = LogManager.getLogger()

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        logger.debug("setApplicationContext")
        context = applicationContext
    }

    companion object {
        private lateinit var context: ApplicationContext

        operator fun <T> get(cls: Class<T>): T = context.getBean(cls)

        operator fun <T : Any> get(cls: KClass<T>): T = context.getBean(cls.java)
    }
}

//@Component
//class SpringContainerFactory {
//
//    @Bean
//    @Lazy(false)
//    fun springContainer(): SpringContainer = SpringContainer
//}