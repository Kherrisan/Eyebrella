package cn.kherrisan.eyebrella.entity.data

import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Field(targetType = FieldType.DECIMAL128)
annotation class Decimal128

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class AutoIncrement