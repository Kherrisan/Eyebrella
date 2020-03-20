package cn.kherrisan.eyebrella.core.common

/**
 * ExchangeService 工厂类
 *
 * 这个类不需要 Spring 进行管理
 */
object ExchangeFactory {

    /**
     * ExchangeService 工厂方法
     *
     * @param name ExchangeName
     * @return ExchangeService
     */
    fun build(name: ExchangeName = ExchangeName.HUOBI): ExchangeService {
        val service = SpringContainer[name.exchangeServiceClass]
        service.name = name
        return service
    }
}