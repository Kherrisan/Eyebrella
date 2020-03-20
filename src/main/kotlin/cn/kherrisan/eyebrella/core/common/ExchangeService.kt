package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.core.service.MarginTradingService
import cn.kherrisan.eyebrella.core.service.SpotMarketService

abstract class ExchangeService {

    lateinit var name: ExchangeName

    /**
     * 用户运行时配置
     */
    open val runtimeConfig: ExchangeRuntimeConfig = ExchangeRuntimeConfig()

    /**
     * 交易过程中涉及到的元数据
     */
    open val metaInfo: ExchangeMetaInfo = ExchangeMetaInfo()

    /**
     * 现货行情服务接口
     *
     * 子类重写时必须 Autowried 和 Lazy
     */
    abstract val spotMarketService: SpotMarketService

    /**
     * 现货交易服务接口
     *
     * 子类重写时必须 Autowried 和 Lazy
     */
    abstract val spotTradingService: SpotTradingService

    /**
     * 杠杆交易服务接口
     *
     * 子类重写时必须 Autowried 和 Lazy
     */
    abstract val marginTradingService: MarginTradingService
}