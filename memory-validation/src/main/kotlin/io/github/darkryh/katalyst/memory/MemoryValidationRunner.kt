package io.github.darkryh.katalyst.memory

import java.io.Closeable
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.math.ceil
import kotlin.system.exitProcess

internal data class RunnerConfig(
    val mode: String,
    val sampleDir: Path,
    val outputRoot: Path,
    val heapMb: Int,
    val seed: Long,
) {
    val durationSeconds: Long = when (mode) {
        "smoke" -> 30
        "baseline" -> 15 * 60
        "soak" -> 30 * 60
        else -> error("Unsupported mode '$mode'; expected smoke, baseline, or soak")
    }

    val concurrency: Int = when (mode) {
        "smoke" -> 2
        "baseline" -> 8
        else -> 16
    }
}

private data class Sample(
    val elapsedMillis: Long,
    val phase: String,
    val rssKb: Long,
    val threadCount: Int,
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
    val poolActive: Int,
    val poolIdle: Int,
    val poolPending: Int,
    val poolTotal: Int,
    val transactionAdapters: Int,
    val completed: Long,
    val failed: Long,
)

private data class BackendTelemetry(
    val heapUsedBytes: Long = -1,
    val heapCommittedBytes: Long = -1,
    val threadCount: Int = -1,
    val poolActive: Int = -1,
    val poolIdle: Int = -1,
    val poolPending: Int = -1,
    val poolTotal: Int = -1,
    val transactionAdapters: Int = -1,
)

private class WorkloadMetrics {
    val completed = AtomicLong()
    val failed = AtomicLong()
    val sequence = AtomicLong()
    val latenciesMicros = ConcurrentLinkedQueue<Long>()

    fun record(startNanos: Long, statusCode: Int) {
        latenciesMicros.add((System.nanoTime() - startNanos) / 1_000)
        if (statusCode in 200..299) completed.incrementAndGet() else failed.incrementAndGet()
    }
}

private class BackendProcess(
    private val config: RunnerConfig,
    private val runDir: Path,
    val port: Int,
) : Closeable {
    private lateinit var process: Process
    val pid: Long get() = process.pid()

    fun start() {
        val libDir = config.sampleDir.resolve("lib")
        require(libDir.exists()) { "Sample distribution not found at ${config.sampleDir}" }

        val separator = System.getProperty("path.separator")
        val classpath = Files.list(libDir).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".jar") }
                .sorted()
                .map(Path::absolutePathString)
                .toList()
                .joinToString(separator)
        }
        val java = Path.of(System.getProperty("java.home"), "bin", "java").absolutePathString()
        val jfr = runDir.resolve("recording.jfr").absolutePathString()
        val gcLog = runDir.resolve("gc.log").absolutePathString()
        val command = listOf(
            java,
            "-Xms${config.heapMb}m",
            "-Xmx${config.heapMb}m",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:NativeMemoryTracking=summary",
            "-XX:StartFlightRecording=filename=$jfr,settings=profile,dumponexit=true",
            "-Xlog:gc*,safepoint:file=$gcLog:time,uptime,level,tags",
            "-cp",
            classpath,
            "io.github.darkryh.katalyst.example.ApplicationKt",
        )
        val serverLog = runDir.resolve("server.log").toFile()
        process = ProcessBuilder(command)
            .directory(config.sampleDir.toFile())
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(serverLog))
            .apply {
                environment().putAll(
                    mapOf(
                        "KATALYST_PROFILE" to "dev",
                        "KATALYST_MEMORY_VALIDATION" to "true",
                        "SERVER_HOST" to "127.0.0.1",
                        "SERVER_PORT" to port.toString(),
                        "DB_URL" to "jdbc:h2:mem:katalyst_memory_validation;DB_CLOSE_DELAY=-1",
                        "DB_DRIVER" to "org.h2.Driver",
                        "DB_USERNAME" to "sa",
                        "DB_PASSWORD" to "",
                        "DB_MAX_POOL" to "8",
                        "DB_MIN_IDLE" to "1",
                        "JWT_SECRET" to "memory-validation-secret-at-least-32-characters",
                        "TRANSACTION_LOGGING_ENABLED" to "false",
                    )
                )
            }
            .start()
    }

    fun isAlive(): Boolean = process.isAlive

    override fun close() {
        if (!::process.isInitialized || !process.isAlive) return
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
        }
    }
}

private class FullBackendWorkload(
    private val baseUri: URI,
    private val metrics: WorkloadMetrics,
) : Closeable {
    private val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
    private val executor = Executors.newFixedThreadPool(32)
    private val websocketFailures = AtomicInteger()

    fun awaitReady(timeoutSeconds: Long = 60) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        var lastFailure: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val response = get("/health/detailed")
                if (response.statusCode() == 200) return
            } catch (error: Throwable) {
                lastFailure = error
            }
            Thread.sleep(250)
        }
        throw IllegalStateException("Backend did not become ready", lastFailure)
    }

    fun runPhase(durationSeconds: Long, concurrency: Int) {
        if (durationSeconds <= 0) return
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds)
        val futures = (0 until concurrency).map {
            CompletableFuture.runAsync({ worker(deadline) }, executor)
        }
        futures.forEach { it.join() }
    }

    fun runWebSocketProbe() {
        runWebSocketProbeInternal()
    }

    fun telemetry(): BackendTelemetry {
        val response = get("/internal/memory-validation/telemetry")
        check(response.statusCode() == 200) { "Telemetry endpoint returned ${response.statusCode()}" }
        val body = response.body()
        fun long(name: String): Long = requireNotNull(
            Regex("\"$name\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toLongOrNull()
        ) { "Telemetry field '$name' is missing" }
        return BackendTelemetry(
            heapUsedBytes = long("heapUsedBytes"),
            heapCommittedBytes = long("heapCommittedBytes"),
            threadCount = long("threadCount").toInt(),
            poolActive = long("poolActive").toInt(),
            poolIdle = long("poolIdle").toInt(),
            poolPending = long("poolPending").toInt(),
            poolTotal = long("poolTotal").toInt(),
            transactionAdapters = long("transactionAdapters").toInt(),
        )
    }

    private fun worker(deadline: Long) {
        while (System.nanoTime() < deadline) {
            val sequence = metrics.sequence.incrementAndGet()
            when (sequence % 10) {
                0L -> register(sequence)
                else -> health()
            }
            Thread.sleep(10)
        }
    }

    private fun health() {
        val start = System.nanoTime()
        runCatching { get("/health") }
            .onSuccess { metrics.record(start, it.statusCode()) }
            .onFailure {
                metrics.failed.incrementAndGet()
                metrics.latenciesMicros.add((System.nanoTime() - start) / 1_000)
            }
    }

    private fun register(sequence: Long) {
        val start = System.nanoTime()
        val body = """{"email":"memory-$sequence@example.test","password":"ValidPassword123!","displayName":"Memory $sequence"}"""
        runCatching { post("/api/auth/register", body) }
            .onSuccess { metrics.record(start, it.statusCode()) }
            .onFailure {
                metrics.failed.incrementAndGet()
                metrics.latenciesMicros.add((System.nanoTime() - start) / 1_000)
            }
    }

    private fun runWebSocketProbeInternal() {
        val listener = object : WebSocket.Listener {
            override fun onOpen(webSocket: WebSocket) {
                webSocket.request(1)
                webSocket.sendText("ping", true)
            }

            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*>? {
                webSocket.request(1)
                return null
            }

            override fun onError(webSocket: WebSocket, error: Throwable) {
                websocketFailures.incrementAndGet()
            }
        }
        runCatching {
            client.newWebSocketBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://${baseUri.host}:${baseUri.port}/ws/users"), listener)
                .join()
                .sendClose(WebSocket.NORMAL_CLOSURE, "validation complete")
                .join()
        }.onFailure {
            websocketFailures.incrementAndGet()
        }
        if (websocketFailures.get() > 0) metrics.failed.addAndGet(websocketFailures.get().toLong())
    }

    private fun get(path: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(baseUri.resolve(path)).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun post(path: String, body: String): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(baseUri.resolve(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}

fun main(args: Array<String>) {
    val config = parseArgs(args)
    val runId = "${Instant.now().toString().replace(':', '-')}-${config.mode}"
    val runDir = config.outputRoot.resolve(runId).createDirectories()
    val port = ServerSocket(0).use { it.localPort }
    val metrics = WorkloadMetrics()
    val samples = ConcurrentLinkedQueue<Sample>()
    val phase = AtomicReference("startup")
    val startedAt = System.nanoTime()
    val backend = BackendProcess(config, runDir, port)
    val sampler = Executors.newSingleThreadScheduledExecutor()

    try {
        backend.start()
        val workload = FullBackendWorkload(URI.create("http://127.0.0.1:$port"), metrics)
        workload.use {
            it.awaitReady()
            phase.set("idle")
            Thread.sleep(if (config.mode == "smoke") 1_000 else 5_000)
            captureJcmd(backend.pid, "GC.class_histogram", runDir.resolve("histogram-ready.txt"))
            sampler.scheduleAtFixedRate({
                val telemetry = it.telemetry()
                samples.add(
                    Sample(
                        elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        phase = phase.get(),
                        rssKb = processRssKb(backend.pid),
                        threadCount = telemetry.threadCount.takeIf { count -> count >= 0 }
                            ?: processThreadCount(backend.pid),
                        heapUsedBytes = telemetry.heapUsedBytes,
                        heapCommittedBytes = telemetry.heapCommittedBytes,
                        poolActive = telemetry.poolActive,
                        poolIdle = telemetry.poolIdle,
                        poolPending = telemetry.poolPending,
                        poolTotal = telemetry.poolTotal,
                        transactionAdapters = telemetry.transactionAdapters,
                        completed = metrics.completed.get(),
                        failed = metrics.failed.get(),
                    )
                )
            }, 0, 1, TimeUnit.SECONDS)
            val warmupSeconds = (config.durationSeconds * 20 / 100).coerceAtLeast(1)
            val stressSeconds = (config.durationSeconds * 20 / 100).coerceAtLeast(1)
            val cooldownSeconds = (config.durationSeconds * 10 / 100).coerceAtLeast(1)
            val steadySeconds = config.durationSeconds - warmupSeconds - stressSeconds - cooldownSeconds
            phase.set("warmup")
            it.runPhase(warmupSeconds, config.concurrency)
            captureJcmd(backend.pid, "GC.class_histogram", runDir.resolve("histogram-post-warmup.txt"))
            phase.set("steady")
            it.runPhase(steadySeconds, config.concurrency)
            phase.set("stress")
            it.runPhase(stressSeconds, config.concurrency * 2)
            it.runWebSocketProbe()
            phase.set("cooldown")
            Thread.sleep(TimeUnit.SECONDS.toMillis(cooldownSeconds))
        }
        sampler.shutdown()
        sampler.awaitTermination(5, TimeUnit.SECONDS)
        captureJcmd(backend.pid, "GC.class_histogram", runDir.resolve("histogram-final.txt"))
        captureJcmd(backend.pid, "VM.native_memory summary", runDir.resolve("native-memory-final.txt"))
    } catch (error: Throwable) {
        runDir.resolve("runner-error.txt").writeText(error.stackTraceToString())
        System.err.println("Memory validation failed: ${error.message}")
        exitProcess(1)
    } finally {
        sampler.shutdownNow()
        backend.close()
        writeReports(config, runDir, samples.toList(), metrics)
    }

    check(metrics.failed.get() == 0L) { "Workload completed with ${metrics.failed.get()} failure(s)" }
    println("Memory validation passed. Report: ${runDir.absolutePathString()}")
}

internal fun parseArgs(args: Array<String>): RunnerConfig {
    val values = args.associate { argument ->
        require(argument.startsWith("--") && '=' in argument) { "Expected --key=value, got '$argument'" }
        argument.removePrefix("--").substringBefore('=') to argument.substringAfter('=')
    }
    val cwd = Path.of("").toAbsolutePath()
    return RunnerConfig(
        mode = values["mode"] ?: "baseline",
        sampleDir = Path.of(requireNotNull(values["sample-dir"]) { "--sample-dir is required" }),
        outputRoot = Path.of(values["output-dir"] ?: cwd.resolve("build/reports/memory-validation").toString()),
        heapMb = values["heap-mb"]?.toInt() ?: 256,
        seed = values["seed"]?.toLong() ?: 42L,
    )
}

private fun processRssKb(pid: Long): Long = runCommand(listOf("ps", "-o", "rss=", "-p", pid.toString()))
    .trim().toLongOrNull() ?: -1

private fun processThreadCount(pid: Long): Int = runCommand(listOf("ps", "-M", "-p", pid.toString()))
    .lineSequence().count { it.isNotBlank() }.minus(1).coerceAtLeast(0)

private fun captureJcmd(pid: Long, command: String, target: Path) {
    val jcmd = Path.of(System.getProperty("java.home"), "bin", "jcmd").absolutePathString()
    target.writeText(runCommand(listOf(jcmd, pid.toString()) + command.split(' ')))
}

private fun runCommand(command: List<String>): String = runCatching {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor(30, TimeUnit.SECONDS)
    output
}.getOrDefault("")

private fun writeReports(
    config: RunnerConfig,
    runDir: Path,
    samples: List<Sample>,
    metrics: WorkloadMetrics,
) {
    val sortedLatencies = metrics.latenciesMicros.sorted()
    fun percentile(fraction: Double): Long {
        if (sortedLatencies.isEmpty()) return 0
        return sortedLatencies[(ceil(sortedLatencies.size * fraction).toInt() - 1).coerceIn(sortedLatencies.indices)]
    }
    val peakRssKb = samples.maxOfOrNull(Sample::rssKb) ?: -1
    val peakHeapBytes = samples.maxOfOrNull(Sample::heapUsedBytes) ?: -1
    val maxPoolPending = samples.maxOfOrNull(Sample::poolPending) ?: -1
    val steadySamples = samples.filter { it.phase == "steady" && it.heapUsedBytes >= 0 }
    val liveSetSlopeBytesPerSecond = linearSlope(
        steadySamples.map { it.elapsedMillis.toDouble() / 1_000.0 to it.heapUsedBytes.toDouble() }
    ).toLong()
    val gitCommit = runCommand(listOf("git", "rev-parse", "HEAD")).trim()
    val gitDirty = runCommand(listOf("git", "status", "--porcelain")).isNotBlank()
    val manifest = """
        {
          "mode": "${config.mode}",
          "heapMb": ${config.heapMb},
          "durationSeconds": ${config.durationSeconds},
          "concurrency": ${config.concurrency},
          "seed": ${config.seed},
          "gitCommit": "${jsonEscape(gitCommit)}",
          "gitDirty": $gitDirty,
          "javaVersion": "${jsonEscape(System.getProperty("java.version"))}",
          "os": "${jsonEscape(System.getProperty("os.name"))}",
          "createdAt": "${Instant.now()}"
        }
    """.trimIndent()
    val summary = """
        {
          "passed": ${metrics.failed.get() == 0L},
          "completed": ${metrics.completed.get()},
          "failed": ${metrics.failed.get()},
          "peakRssKb": $peakRssKb,
          "peakHeapBytes": $peakHeapBytes,
          "steadyHeapSlopeBytesPerSecond": $liveSetSlopeBytesPerSecond,
          "maxPoolPending": $maxPoolPending,
          "latencyMicros": {"p50": ${percentile(0.50)}, "p95": ${percentile(0.95)}, "p99": ${percentile(0.99)}, "max": ${sortedLatencies.lastOrNull() ?: 0}}
        }
    """.trimIndent()
    runDir.resolve("manifest.json").writeText(manifest)
    runDir.resolve("summary.json").writeText(summary)
    Files.newBufferedWriter(
        runDir.resolve("timeseries.csv"),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    ).use { writer ->
        writer.appendLine(
            "elapsedMillis,phase,rssKb,threadCount,heapUsedBytes,heapCommittedBytes," +
                "poolActive,poolIdle,poolPending,poolTotal,transactionAdapters,completed,failed"
        )
        samples.forEach { sample ->
            writer.appendLine(
                "${sample.elapsedMillis},${sample.phase},${sample.rssKb},${sample.threadCount}," +
                    "${sample.heapUsedBytes},${sample.heapCommittedBytes},${sample.poolActive},${sample.poolIdle}," +
                    "${sample.poolPending},${sample.poolTotal},${sample.transactionAdapters}," +
                    "${sample.completed},${sample.failed}"
            )
        }
    }
}

private fun linearSlope(points: List<Pair<Double, Double>>): Double {
    if (points.size < 2) return 0.0
    val xMean = points.sumOf { it.first } / points.size
    val yMean = points.sumOf { it.second } / points.size
    val denominator = points.sumOf { (it.first - xMean) * (it.first - xMean) }
    if (denominator == 0.0) return 0.0
    return points.sumOf { (it.first - xMean) * (it.second - yMean) } / denominator
}

private fun jsonEscape(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}
