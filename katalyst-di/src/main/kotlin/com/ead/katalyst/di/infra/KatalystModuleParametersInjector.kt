package com.ead.katalyst.di.infra

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationEnvironment
import io.ktor.server.application.ModuleParametersInjector
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubclassOf

/**
 * Fallback injector to satisfy module parameters when Ktor loads modules from configuration.
 *
 * Primarily handles `args: Array<String>` to avoid startup failures when `ktor.application.modules`
 * points at a main function that expects CLI args.
 */
class KatalystModuleParametersInjector(
    private val providedArgs: Array<String> = emptyArray()
) : ModuleParametersInjector {

    override suspend fun resolveParameter(application: Application, parameter: KParameter): Any? {
        val typeClassifier = parameter.type.classifier as? KClass<*> ?: return null

        return when {
            typeClassifier.isSubclassOf(ApplicationEnvironment::class) -> application.environment
            typeClassifier == Array<String>::class -> providedArgs
            else -> null
        }
    }
}
