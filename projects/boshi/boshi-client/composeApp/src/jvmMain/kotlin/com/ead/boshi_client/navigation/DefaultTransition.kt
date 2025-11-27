package com.ead.boshi_client.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene



fun <T : Any> defaultTransition():
        AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    fadeIn() togetherWith fadeOut()
}

fun <T : Any> defaultPopTransition():
        AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    fadeIn() togetherWith fadeOut()
}

fun <T : Any> defaultPredictivePopTransition():
        AnimatedContentTransitionScope<Scene<T>>.(Int) -> ContentTransform = {
    fadeIn() togetherWith fadeOut()
}