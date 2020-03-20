package cn.kherrisan.eyebrella.core.enumeration

/**
 * 借贷订单状态
 */
enum class LoanStatusEnum {
    /**
     * 未放款
     */
    CREATED,

    /**
     * 已放款
     */
    ACCRUAL,

    /**
     * 已还清
     */
    REPAYED,

    /**
     * 异常
     */
    INVALID
}