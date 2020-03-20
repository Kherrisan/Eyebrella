package cn.kherrisan.eyebrella.core.service

import cn.kherrisan.eyebrella.entity.Contract

/**
 * 期货市场行情接口
 */
interface FutureMarketService {

    suspend fun getContracts(): List<Contract>

}