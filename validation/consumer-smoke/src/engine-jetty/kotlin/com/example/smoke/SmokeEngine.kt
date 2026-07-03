package com.example.smoke

import io.github.darkryh.katalyst.ktor.engine.jetty.JettyServer

// Selected when the build is run with -PkatalystEngine=jetty. Provided by
// katalyst-starter-engine-jetty.
val smokeEngine = JettyServer
