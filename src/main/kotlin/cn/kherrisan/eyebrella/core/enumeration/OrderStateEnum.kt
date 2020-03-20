package cn.kherrisan.eyebrella.core.enumeration

/**
 * 订单状态枚举类
 */
enum class OrderStateEnum {

    /**
     * 订单已创建，但还未提交到交易系统
     */
    CREATED,

    /**
     * 订单已提交到了交易系统，等待撮合
     */
    SUBMITTED,

    /**
     * 订单已部分成交
     */
    PARTIAL_FILLED,

    /**
     * 订单已全部成交，此订单终结
     */
    FILLED,

    /**
     * 订单已撤销，是部分撤销还是全部撤销，看partialfilled的数量
     */
    CANCELED,

    /**
     * 失败
     */
    FAILED
}