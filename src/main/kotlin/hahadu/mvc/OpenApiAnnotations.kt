package hahadu.mvc

import kotlin.reflect.KClass
import kotlin.jvm.JvmRepeatable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiDocGroup(
    val name: String = "",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiDoc(
    val summary: String = "",
    val description: String = "",
    val tags: Array<String> = [],
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class WebSocket(val path: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ResponseDoc(
    val code: Int=200,
    val description: String = "",
    val type: KClass<*> = Any::class,
    val example: String = "",
)

@Target(AnnotationTarget.FUNCTION,AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(QueryParams::class)
annotation class QueryParam(
    val name: String,
    val type: KClass<*> = String::class,
    val required: Boolean = false,
    val description: String = "",
    val example: String = "",
)

@Target(AnnotationTarget.FUNCTION,AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(UrlParams::class)
annotation class UrlParam(
    val name: String,
    val type: KClass<*> = String::class,
    val description: String = "",
    val example: String = "",
)

@Target(AnnotationTarget.FUNCTION,AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(HeaderParams::class)
annotation class HeaderParam(
    val name: String,
    val type: KClass<*> = String::class,
    val required: Boolean = false,
    val description: String = "",
    val example: String = "",
)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(BodyParams::class)
annotation class BodyParam(
    val name: String = "",
    val type: KClass<*> = Any::class,
    val required: Boolean = true,
    val description: String = "",
    val example: String = "",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class BodyParams(val value: Array<BodyParam>)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class HeaderParams(val value: Array<HeaderParam>)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class QueryParams(val value: Array<QueryParam>)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UrlParams(val value: Array<UrlParam>)
