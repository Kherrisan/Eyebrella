package cn.kherrisan.eyebrella

import cn.kherrisan.eyebrella.core.Exchanges
import cn.kherrisan.eyebrella.core.common.ExchangeName
import org.apache.logging.log4j.LogManager
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

object Eyebrella {

    private val logger = LogManager.getLogger()

    fun init() {
        runApplication<SpringStarter>()
        Exchanges
        logger.debug("Eyebrella 启动成功")
    }
}

@SpringBootApplication
class SpringStarter