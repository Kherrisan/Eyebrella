package cn.kherrisan.eyebrella.core.common

import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Symbol

/**
 * 小数精度调整
 *
 * 因为很多交易所返回的诸如价格、数量、金额的数字大都是浮点数，有时直接在 JSON 中使用 Float 类型，有时则会给出一个包含浮点数的字符串。为了使得这样的数字
 * 能够有准确的值，本程序统一使用 BigDecimal 类来表示数字信息。
 * 在从交易所 API 获得数字之后会进行精度的标准化，这样方便观察（打印出来不会有一长串的 0），同时交易所的业务本身也不会支持无线精度的数字，如货币个数
 * 这样的字段都会有一个最小步长。
 * 精度的标准化的规则需要由交易所相应接口提供，通常包含以下两个：
 * 1. 数量步长：个数步长规定了某个商品（货币）的交易个数的最小变化量，如橘子在交易时的数量步长通常为"个"，但香蕉有时可以让水果店老板切一半或者几根，所以步长可以为"半个"或者"根"。
 * 2. 价格步长：价格步长规定了最小的价格变化量，如 RMB 的价格步长是 1 分（通常而言）。
 * 本程序使用 Int 型变量表示步长，整数值表示小数点后的位数，也就是 BigDecima 的 scale 字段。如 0.0001 的步长为 4。
 */
interface DecimalAdaptor {

    /**
     * 币的数量步长
     *
     * 一般用于账户余额的数字。
     *
     * @param currency Currency?
     * @return Int
     */
    fun sizeIncrement(currency: Currency?): Int

    /**
     * 交易对基础币种的数量步长
     *
     * 在进行现货、杠杆、期货交易时，同一个币种的数量步长在不同的交易对中可能不一样，这可能受汇率的影响。
     *
     * @param currency Currency?
     * @return Int
     */
    fun sizeIncrement(symbol: Symbol?): Int

    /**
     * 交易对的价格步长
     *
     * 也称为报价精度。
     *
     * @param symbol Symbol?
     * @return Int
     */
    fun priceIncrement(symbol: Symbol?): Int

    /**
     * 交易对计价币种的数量步长
     *
     * 也称为交易金额的精度。有的交易所（Huobi）提供了这个标准，但有的交易所（Okex）没有，如果没有的话，默认采用 priceIncrement。
     *
     * @param symbol Symbol?
     * @return Int
     */
    fun volumeIncrement(symbol: Symbol?): Int
}