package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.core.enumeration.LoanStatusEnum
import cn.kherrisan.eyebrella.entity.*
import cn.kherrisan.eyebrella.entity.Currency
import java.math.BigDecimal
import java.util.*

/**
 * 杠杆交易接口
 *
 * 杠杆交易和现货交易共享相同的市场和行情，区别在于杠杆交易的资金不仅可以使用用户自己余额资金，还可以在有保证金的基础上借贷额外的资金，进行现货交易，最后偿还借得的
 * 本金和利息。
 * 有的交易所提供逐仓杠杆和全仓杠杆这两种方式，而有的交易所只提供一种。这里为了方便，将二者的接口合二为一了。在交易系统中，所谓的仓位以symbol为依据进行划分。
 * 故提供了一个单独的symbol对象：CROSS，来标识全仓操作。
 */
interface MarginTradingService : SpotMarginTradingService {

    /**
     * 仓内借币
     *
     * @param currency Currency
     * @param amount BigDecimal
     * @return TransactionResult
     */
    suspend fun loan(currency: Currency, amount: BigDecimal, symbol: Symbol = CROSS): TransactionResult

    /**
     * 仓内还币
     *
     * @param oid String
     * @param amount BigDecimal
     * @return TransactionResult
     */
    suspend fun repay(oid: String, currency: Currency, symbol: Symbol = CROSS, amount: BigDecimal): TransactionResult

    /**
     * 查询借币订单
     *
     * @param size Int?
     * @param start Date?
     * @param end Date?
     * @param status LoanStatusEnum?
     * @param currency Currency?
     * @return List<LoanOrder>
     */
    suspend fun getLoanOrders(symbol: Symbol = CROSS, size: Int? = null, start: Date? = null, end: Date? = null, status: LoanStatusEnum = LoanStatusEnum.ACCRUAL, currency: Currency? = null): List<LoanOrder>

    /**
     * 查询所有交易对余额
     *
     * @param symbol Symbol?
     * @return Map<Symbol, MarginBalance>
     */
    suspend fun getBalance(): Map<Symbol, MarginBalance>

    /**
     * 查询所有交易对借贷基本信息
     *
     * @return Map<Symbol, MarginInfo>
     */
    suspend fun getMarginInfo(): Map<Symbol, MarginInfo>
}