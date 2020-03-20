package cn.kherrisan.eyebrella.entity

import java.math.BigDecimal
import java.math.RoundingMode

data class CurrencyMetaInfo(val currency: Currency,
                            var sizeIncrement: Int) {

    fun validateSizeIncrement(size: BigDecimal): Boolean {
        return size.compareTo(BigDecimal.ZERO) != 0
                && size.setScale(sizeIncrement, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) != 0
    }

    fun smallerSizeIncrement(si: Int) {
        if (sizeIncrement < si) {
            sizeIncrement = si
        }
    }
}