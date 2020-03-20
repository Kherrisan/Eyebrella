package cn.kherrisan.eyebrella.entity.data

enum class TransactionType {

    /**
     * 账户间划转
     */
    TRANSFER,

    /**
     * 现货、杠杆交易
     */
    TRADE,

    /**
     * 杠杆的借还
     */
    CREDIT,

    /**
     * 利息
     */
    INTEREST,

    /**
     * 强平仓
     */
    LIQUIDATION,

    /**
     * 充提币
     */
    DEPOSIT_WITHDRAW,

    /**
     * 各种手续费
     */
    FEE,

    /**
     * 其他
     */
    OTHERS
}
