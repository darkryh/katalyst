package com.ead.katalyst.example

import com.ead.katalyst.components.Component

class TestComponent(
    private val testHiComponent: TestHiComponent
) : Component {
    fun hi() {
        testHiComponent.hi()
        println("hi from test component")
    }
}