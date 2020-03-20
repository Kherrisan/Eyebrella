package cn.kherrisan.eyebrella

import cn.kherrisan.eyebrella.core.common.MyDate
import cn.kherrisan.eyebrella.entity.Currency
import cn.kherrisan.eyebrella.entity.Symbol
import cn.kherrisan.eyebrella.entity.data.AutoIncrement
import cn.kherrisan.eyebrella.entity.data.Sequence
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.util.ReflectionUtils
import java.util.*

@Configuration
class MongoConfig {
    @Bean
    fun customConversions(): MongoCustomConversions = MongoCustomConversions(
        listOf(
            MyDateReadingConverter(),
            CurrencyReadingConverter(),
            CurrencyWritingConverter(),
            SymbolReadingConverter(),
            SymbolWritingConverter()
        )
    )
}

class SymbolWritingConverter : Converter<Symbol, String> {
    override fun convert(source: Symbol): String? = source.name()
}

class SymbolReadingConverter : Converter<String, Symbol> {
    override fun convert(source: String): Symbol? {
        if (!source.contains("/"))
            return null
        val mid = source.indexOf("/")
        return Symbol(source.substring(0, mid), source.substring(mid + 1))
    }
}

class CurrencyWritingConverter : Converter<Currency, String> {
    override fun convert(source: Currency): String? = source.name
}

class CurrencyReadingConverter : Converter<String, Currency> {
    override fun convert(source: String): Currency? = Currency(source)
}

class MyDateReadingConverter : Converter<Date, MyDate> {
    override fun convert(source: Date): MyDate? = MyDate(source.time)
}

@Component
class SaveEventListener : AbstractMongoEventListener<Any>() {

    @Autowired
    lateinit var mongoTemplate: MongoTemplate

    override fun onBeforeConvert(event: BeforeConvertEvent<Any>) {
        val source = event.source
        ReflectionUtils.doWithFields(source.javaClass) {
            ReflectionUtils.makeAccessible(it)
            if (it.isAnnotationPresent(AutoIncrement::class.java)
                && it.get(source) is Number && it.getLong(source) == 0L
            ) {
                it.set(source, nextId(source.javaClass))
            }
        }
    }

    private fun nextId(cls: Class<Any>): Long {
        val query = Query(where("name").`is`(cls.simpleName))
        val u = Update()
        u.inc("seq", 1)
        val options = FindAndModifyOptions()
        options.upsert(true)
        options.returnNew(true)
        val seq = mongoTemplate.findAndModify(query, u, options, Sequence::class.java)
        return seq.seq
    }
}
