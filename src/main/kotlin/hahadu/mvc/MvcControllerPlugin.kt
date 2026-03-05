package hahadu.mvc

import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.util.reflect.TypeInfo
import kotlin.collections.orEmpty
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.full.callSuspend

class MvcControllerConfig {
    var controllers: List<Any> = emptyList()
    var middlewares: Map<String, suspend (ApplicationCall) -> Boolean> = emptyMap()
    var globalMiddlewares: List<String> = emptyList()
    var routeGroupMiddlewares: Map<String, List<String>> = emptyMap()
    var apiCallClass: KClass<*>? = null
    var apiCallFactory: ((ApplicationCall) -> Any)? = null
}

val MvcControllerPlugin = createApplicationPlugin(name = "MvcControllerPlugin", ::MvcControllerConfig) {
    val controllers = pluginConfig.controllers
    if (controllers.isEmpty()) return@createApplicationPlugin

    application.routing {
        for (controller in controllers) {
            val controllerClass = controller::class
            val controllerAnn = controllerClass.findAnnotation<Controller>()
                ?: continue
            val basePath = normalizePath(controllerAnn.path)
            val classAuth = controllerClass.findAnnotation<UseAuth>()?.names?.toList().orEmpty()
            val groupPath = normalizePath(controllerClass.findAnnotation<RouteGroup>()?.path ?: "")
            val baseRoute: Route.() -> Unit = {
                val classMiddlewares = controllerClass.findAnnotation<UseMiddleware>()?.names?.toList().orEmpty()
                val groupMiddlewares = pluginConfig.routeGroupMiddlewares[basePath].orEmpty()
                controllerClass.declaredMemberFunctions.forEach { fn ->
                    val methodAuth = fn.findAnnotation<UseAuth>()?.names?.toList().orEmpty()
                    val allAuth = (classAuth + methodAuth).distinct()
                val bind: Route.() -> Unit = {
                    when {
                        fn.hasAnnotation<Get>() -> bindRoute(
                            HttpMethod.Get,
                            fn,
                            controller,
                            classMiddlewares,
                            groupMiddlewares,
                            pluginConfig.globalMiddlewares,
                            pluginConfig.middlewares,
                            pluginConfig.apiCallClass,
                            pluginConfig.apiCallFactory
                        )
                        fn.hasAnnotation<Post>() -> bindRoute(
                            HttpMethod.Post,
                            fn,
                            controller,
                            classMiddlewares,
                            groupMiddlewares,
                            pluginConfig.globalMiddlewares,
                            pluginConfig.middlewares,
                            pluginConfig.apiCallClass,
                            pluginConfig.apiCallFactory
                        )
                        fn.hasAnnotation<Put>() -> bindRoute(
                            HttpMethod.Put,
                            fn,
                            controller,
                            classMiddlewares,
                            groupMiddlewares,
                            pluginConfig.globalMiddlewares,
                            pluginConfig.middlewares,
                            pluginConfig.apiCallClass,
                            pluginConfig.apiCallFactory
                        )
                        fn.hasAnnotation<Delete>() -> bindRoute(
                            HttpMethod.Delete,
                            fn,
                            controller,
                            classMiddlewares,
                            groupMiddlewares,
                            pluginConfig.globalMiddlewares,
                            pluginConfig.middlewares,
                            pluginConfig.apiCallClass,
                            pluginConfig.apiCallFactory
                        )
                        fn.hasAnnotation<Patch>() -> bindRoute(
                            HttpMethod.Patch,
                            fn,
                            controller,
                            classMiddlewares,
                            groupMiddlewares,
                            pluginConfig.globalMiddlewares,
                            pluginConfig.middlewares,
                            pluginConfig.apiCallClass,
                            pluginConfig.apiCallFactory
                        )
                    }
                }

                    if (allAuth.isNotEmpty()) {
                        authenticate(*allAuth.toTypedArray()) { bind() }
                    } else {
                        bind()
                    }
                }
            }

            if (groupPath.isNotBlank()) {
                route(groupPath) {
                    route(basePath) {
                        baseRoute()
                    }
                }
            } else {
                route(basePath) {
                    baseRoute()
                }
            }
        }
    }
}


private fun Route.bindRoute(
    method: HttpMethod,
    fn: KFunction<*>,
    controller: Any,
    classMiddlewares: List<String>,
    groupMiddlewares: List<String>,
    globalMiddlewares: List<String>,
    middlewareRegistry: Map<String, suspend (ApplicationCall) -> Boolean>,
    apiCallClass: KClass<*>?,
    apiCallFactory: ((ApplicationCall) -> Any)?,
) {
    val path = normalizePath(
        fn.findAnnotation<Get>()?.path ?: fn.findAnnotation<Post>()?.path ?: fn.findAnnotation<Put>()?.path ?: fn.findAnnotation<Delete>()?.path ?: fn.findAnnotation<Patch>()?.path ?: ""
    )
    val methodMiddlewares = fn.findAnnotation<UseMiddleware>()?.names?.toList().orEmpty()
    val allMiddlewares = (globalMiddlewares + groupMiddlewares + classMiddlewares + methodMiddlewares).distinct()

    when (method) {
        HttpMethod.Get -> get(path) { invokeHandler(fn, controller, call, allMiddlewares, middlewareRegistry, apiCallClass, apiCallFactory) }
        HttpMethod.Post -> post(path) { invokeHandler(fn, controller, call, allMiddlewares, middlewareRegistry, apiCallClass, apiCallFactory) }
        HttpMethod.Put -> put(path) { invokeHandler(fn, controller, call, allMiddlewares, middlewareRegistry, apiCallClass, apiCallFactory) }
        HttpMethod.Delete -> delete(path) { invokeHandler(fn, controller, call, allMiddlewares, middlewareRegistry, apiCallClass, apiCallFactory) }
        HttpMethod.Patch -> patch(path) { invokeHandler(fn, controller, call, allMiddlewares, middlewareRegistry, apiCallClass, apiCallFactory) }
        else -> {}
    }
}

private suspend fun invokeHandler(
    fn: KFunction<*>,
    controller: Any,
    call: ApplicationCall,
    middlewareNames: List<String>,
    middlewareRegistry: Map<String, suspend (ApplicationCall) -> Boolean>,
    apiCallClass: KClass<*>?,
    apiCallFactory: ((ApplicationCall) -> Any)?,
) {
    for (name in middlewareNames) {
        val middleware = middlewareRegistry[name]
            ?: throw IllegalStateException("Middleware not found: $name")
        if (!middleware(call)) {
            return
        }
    }

    fn.isAccessible = true
    var bodyConsumed = false
    val fnParamMaps = collectFunctionParamMaps(fn)
    val params = buildList {
        for (p in fn.parameters) {
            when (p.kind) {
                KParameter.Kind.INSTANCE -> add(controller)
                KParameter.Kind.VALUE -> {
                    when (p.type.classifier) {
                        ApplicationCall::class -> add(call)
                        else -> {
                            if (apiCallClass != null && p.type.classifier == apiCallClass) {
                                val apiCall = apiCallFactory?.invoke(call)
                                    ?: throw IllegalStateException("apiCallFactory is required for $apiCallClass")
                                add(apiCall)
                            } else {
                                val value = resolveAnnotatedParam(call, fnParamMaps, p, bodyConsumed)
                                if (value.first) {
                                    bodyConsumed = bodyConsumed || value.second
                                    add(value.third)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
    fn.callSuspend(*params.toTypedArray())
}

private data class FunctionParamMaps(
    val query: Map<String, QueryParam>,
    val url: Map<String, UrlParam>,
    val header: Map<String, HeaderParam>,
)

private fun collectFunctionParamMaps(fn: KFunction<*>): FunctionParamMaps {
    val query = (fn.annotations.filterIsInstance<QueryParam>() +
        fn.annotations.filterIsInstance<QueryParams>().flatMap { it.value.toList() })
        .associateBy { it.name }
    val url = (fn.annotations.filterIsInstance<UrlParam>() +
        fn.annotations.filterIsInstance<UrlParams>().flatMap { it.value.toList() })
        .associateBy { it.name }
    val header = (fn.annotations.filterIsInstance<HeaderParam>() +
        fn.annotations.filterIsInstance<HeaderParams>().flatMap { it.value.toList() })
        .associateBy { it.name }
    return FunctionParamMaps(query, url, header)
}

private suspend fun resolveAnnotatedParam(
    call: ApplicationCall,
    fnParamMaps: FunctionParamMaps,
    param: KParameter,
    bodyConsumed: Boolean,
): Triple<Boolean, Boolean, Any?> {
    param.findAnnotation<QueryParam>()?.let { ann ->
        return Triple(true, false, coerceParam(call.request.queryParameters[ann.name], ann.type, param.type))
    }
    param.findAnnotation<UrlParam>()?.let { ann ->
        return Triple(true, false, coerceParam(call.parameters[ann.name], ann.type, param.type))
    }
    param.findAnnotation<HeaderParam>()?.let { ann ->
        return Triple(true, false, coerceParam(call.request.headers[ann.name], ann.type, param.type))
    }
    param.findAnnotation<BodyParam>()?.let { ann ->
        if (bodyConsumed) {
            return Triple(true, false, null)
        }
        val type = ann.type
        val typeInfo = TypeInfo(type, param.type)
        val body = call.receive<Any>(typeInfo)
        return Triple(true, true, body)
    }
    val paramName = param.name
    if (!paramName.isNullOrBlank()) {
        fnParamMaps.query[paramName]?.let { ann ->
            return Triple(true, false, coerceParam(call.request.queryParameters[ann.name], ann.type, param.type))
        }
        fnParamMaps.url[paramName]?.let { ann ->
            return Triple(true, false, coerceParam(call.parameters[ann.name], ann.type, param.type))
        }
        fnParamMaps.header[paramName]?.let { ann ->
            return Triple(true, false, coerceParam(call.request.headers[ann.name], ann.type, param.type))
        }
    }
    return Triple(false, false, null)
}

private fun coerceParam(raw: String?, target: KClass<*>, kotlinType: KType): Any? {
    if (raw == null) return null
    return when (target) {
        String::class -> raw
        Int::class -> raw.toIntOrNull()
        Long::class -> raw.toLongOrNull()
        Short::class -> raw.toShortOrNull()
        Double::class -> raw.toDoubleOrNull()
        Float::class -> raw.toFloatOrNull()
        Boolean::class -> raw.equals("true", ignoreCase = true)
        else -> if (kotlinType.isMarkedNullable) null else raw
    }
}

private fun normalizePath(path: String): String {
    if (path.isBlank()) return ""
    val trimmed = path.trim()
    return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
}
