package cn.kherrisan.eyebrella.core.enumeration

enum class AccountTypeEnum {
    /**
     * 法币账户
     */
    MAIN,

    /**
     * 现货账户
     */
    SPOT,

    /**
     * （逐仓）杠杆账户
     */
    MARGIN,

    /**
     * 全仓杠杆账户
     */
    SUPERMARGIN,

    /**
     * 期货账户
     */
    FUTURE
}