package com.example.smoke

import io.github.darkryh.katalyst.ktor.engine.netty.NettyServer

// Selected when the build is run with -PkatalystEngine=netty (the default). Provided by
// katalyst-starter-engine-netty.
val smokeEngine = NettyServer
