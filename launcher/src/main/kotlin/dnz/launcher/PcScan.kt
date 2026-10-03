package dnz.launcher

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.awt.GraphicsEnvironment
import java.lang.management.ManagementFactory
import java.util.Base64
import java.util.concurrent.TimeUnit

/** What the "Auto" button knows about the computer. */
data class PcSpecs(
    val ramGb: Double,
    val cpuName: String,
    val cpuCores: Int,
    val cpuThreads: Int,
    val gpus: List<Gpu>,
    val laptop: Boolean,
    val refreshRate: Int?,
    /** Game folders (--gameDir) of Minecraft windows that are open right now. */
    val runningGameDirs: List<String>,
    /** Mac: Low Power Mode is on (the Mac runs slower on purpose). */
    val lowPowerMode: Boolean = false,
    /** Running on battery instead of the charger. */
    val onBattery: Boolean = false,
) {
    data class Gpu(val name: String, val vramGb: Double?, val dedicated: Boolean) {
        /** 0 = weak (most built-in graphics) .. 3 = strong (dedicated with 8 GB+). */
        val score: Int
            get() {
                if (!dedicated) return if (STRONG_BUILT_IN.any { it in name.lowercase() }) 1 else 0
                val vram = vramGb ?: 0.0
                return when {
                    vram >= 7.5 -> 3
                    vram >= 3.5 -> 2
                    else -> 1
                }
            }
    }

    val bestGpu: Gpu? get() = gpus.maxByOrNull { it.score }

    /** Laptop-style "two graphics cards": Windows may start the game on the weak one. */
    val hybridGraphics: Boolean get() = gpus.any { it.dedicated } && gpus.any { !it.dedicated }

    companion object {
        private val STRONG_BUILT_IN = listOf("iris xe", "arc graphics", "radeon 680m", "radeon 780m", "radeon 880m", "radeon 890m")
    }
}

/** Reads the computer's hardware (Windows): RAM from Java, CPU/GPU/battery from Windows (read-only). */
object PcScan {
    private val IGNORED_GPUS = listOf("microsoft basic", "remote display", "virtual", "parsec", "citrix", "vmware", "hyper-v")
    /** Windows chassis types of portable computers (laptop, notebook, sub notebook, convertible...). */
    private val LAPTOP_CHASSIS = setOf(8, 9, 10, 11, 12, 14, 18, 21, 30, 31, 32)

    /** A JSON number or array of numbers. */
    private fun numbers(e: JsonElement?): List<Int> = when {
        e == null || e.isJsonNull -> emptyList()
        e.isJsonPrimitive -> listOfNotNull(runCatching { e.asInt }.getOrNull())
        e.isJsonArray -> e.asJsonArray.mapNotNull { runCatching { it.asInt }.getOrNull() }
        else -> emptyList()
    }

    private val SCRIPT = """
${'$'}ErrorActionPreference = 'SilentlyContinue'
${'$'}cpu = Get-CimInstance Win32_Processor | Select-Object -First 1 Name, NumberOfCores, NumberOfLogicalProcessors
${'$'}gpus = @(Get-CimInstance Win32_VideoController | Select-Object Name, AdapterRAM)
${'$'}vram = @(Get-ItemProperty 'HKLM:\SYSTEM\ControlSet001\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}\0*' | Where-Object { ${'$'}_.DriverDesc } | ForEach-Object { [pscustomobject]@{ Name = ${'$'}_.DriverDesc; Qw = ${'$'}_.'HardwareInformation.qwMemorySize'; Dw = ${'$'}_.'HardwareInformation.MemorySize' } })
${'$'}battery = @(Get-CimInstance Win32_Battery).Count
${'$'}chassis = @((Get-CimInstance Win32_SystemEnclosure).ChassisTypes)
${'$'}games = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" | ForEach-Object { if (${'$'}_.CommandLine -match '--gameDir\s+(?:"([^"]+)"|(\S+))') { if (${'$'}matches[1]) { ${'$'}matches[1] } else { ${'$'}matches[2] } } })
[pscustomobject]@{ cpu = ${'$'}cpu; gpus = ${'$'}gpus; vram = ${'$'}vram; battery = ${'$'}battery; chassis = ${'$'}chassis; games = ${'$'}games } | ConvertTo-Json -Compress -Depth 4
"""

    fun scan(): PcSpecs {
        val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        val ramGb = (os?.totalMemorySize ?: 0L) / (1024.0 * 1024 * 1024)
        val threads = Runtime.getRuntime().availableProcessors()
        val refresh = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate.takeIf { it > 0 }
        }.getOrNull()
        if (!Platform.isWindows) return unixScan(ramGb, threads, refresh)

        val info = runCatching { windowsInfo() }.getOrNull()
        val cpu = info?.get("cpu") as? JsonObject
        val vram = (info?.get("vram") as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val bytes = bytes(o.get("Qw")) ?: bytes(o.get("Dw")) ?: return@mapNotNull null
            o.str("Name")?.let { it to bytes }
        }?.toMap() ?: emptyMap()
        val gpus = (info?.get("gpus") as? JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("Name")?.trim() ?: return@mapNotNull null
            if (IGNORED_GPUS.any { it in name.lowercase() }) return@mapNotNull null
            // The registry value is exact; AdapterRAM stops at 4 GB.
            val bytes = vram[name] ?: bytes(o.get("AdapterRAM"))
            PcSpecs.Gpu(name, bytes?.let { it / (1024.0 * 1024 * 1024) }?.takeIf { it > 0.1 }, isDedicated(name))
        }?.distinctBy { it.name } ?: emptyList()

        return PcSpecs(
            ramGb = ramGb,
            cpuName = cpu?.str("Name")?.trim()?.replace(Regex("\\s+"), " ") ?: "",
            cpuCores = cpu?.get("NumberOfCores")?.takeIf { it.isJsonPrimitive }?.asInt ?: threads,
            cpuThreads = cpu?.get("NumberOfLogicalProcessors")?.takeIf { it.isJsonPrimitive }?.asInt ?: threads,
            gpus = gpus,
            laptop = (info?.get("battery")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0) > 0 ||
                numbers(info?.get("chassis")).any { it in LAPTOP_CHASSIS } || gpus.any { "laptop" in it.name.lowercase() },
            refreshRate = refresh,
            runningGameDirs = (info?.get("games") as? JsonArray)?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                ?: (info?.get("games")?.takeIf { it.isJsonPrimitive }?.asString?.let { listOf(it) } ?: emptyList()),
        )
    }

    /**
     * macOS (and Linux, partly): CPU from sysctl, graphics from system_profiler. Apple Silicon graphics share the
     * RAM; they count as strong graphics with about half the RAM as video memory.
     */
    private fun unixScan(ramGb: Double, threads: Int, refresh: Int?): PcSpecs {
        val gpus = mutableListOf<PcSpecs.Gpu>()
        var cpuName = ""
        var cores = threads
        var laptop = false
        if (Platform.isMac) {
            cpuName = Platform.run("sysctl", "-n", "machdep.cpu.brand_string").orEmpty()
            cores = Platform.run("sysctl", "-n", "hw.physicalcpu")?.toIntOrNull() ?: threads
            laptop = Platform.run("sysctl", "-n", "hw.model")?.contains("MacBook", ignoreCase = true) == true
            val displays = runCatching {
                JsonParser.parseString(Platform.run("system_profiler", "SPDisplaysDataType", "-json", timeoutSec = 20).orEmpty())
                    .asJsonObject.getAsJsonArray("SPDisplaysDataType")
            }.getOrNull()
            displays?.forEach { e ->
                val o = e.asJsonObject
                val name = o.str("sppci_model") ?: o.str("_name") ?: return@forEach
                val apple = name.startsWith("Apple", ignoreCase = true)
                val vram = (o.str("spdisplays_vram") ?: o.str("spdisplays_vram_shared"))?.let { text ->
                    val n = Regex("([0-9.]+)").find(text)?.value?.toDoubleOrNull()
                    n?.let { if (text.contains("MB", true)) it / 1024 else it }
                }
                val builtIn = o.str("sppci_bus")?.contains("builtin", ignoreCase = true) == true
                gpus += PcSpecs.Gpu(
                    name,
                    if (apple) ramGb / 2 else vram,
                    apple || (!builtIn && isDedicated(name)),
                )
            }
        }
        val games = Platform.run("ps", "-axww", "-o", "command=")?.lines()?.mapNotNull { line ->
            Regex("--gameDir\\s+(\\S+)").find(line)?.groupValues?.get(1)
        } ?: emptyList()
        val lowPower = Platform.isMac && Platform.run("pmset", "-g")?.lines()?.any { it.trim().startsWith("lowpowermode") && it.trim().endsWith("1") } == true
        val battery = Platform.isMac && Platform.run("pmset", "-g", "batt")?.contains("Battery Power") == true
        return PcSpecs(ramGb, cpuName, cores, threads, gpus, laptop, refresh, games, lowPower, battery)
    }

    fun isDedicated(name: String): Boolean {
        val n = name.lowercase()
        return when {
            listOf("nvidia", "geforce", "quadro", "rtx", "gtx").any { it in n } -> true
            "radeon rx" in n || "radeon pro" in n || Regex("radeon\\s+r[579]\\s").containsMatchIn(n) -> true
            "intel" in n && Regex("\\barc\\s+[ab]\\d").containsMatchIn(n) -> true // Intel Arc cards (not "Arc Graphics" in CPUs)
            else -> false // Intel UHD/Iris/HD, AMD "Radeon Graphics" (built into the CPU)
        }
    }

    private fun windowsInfo(): JsonObject? {
        // -EncodedCommand (UTF-16LE Base64) keeps the quotes in the script intact on the Windows command line.
        val encoded = Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        val output = process.inputStream.bufferedReader().readText()
        val json = output.trim().lines().lastOrNull { it.trim().startsWith("{") } ?: return null
        return JsonParser.parseString(json).asJsonObject
    }

    /** Memory sizes come as a number or as little-endian bytes. */
    private fun bytes(e: JsonElement?): Long? = when {
        e == null || e.isJsonNull -> null
        e.isJsonPrimitive -> runCatching { e.asLong }.getOrNull()?.takeIf { it > 0 }
        e.isJsonArray -> e.asJsonArray.foldIndexed(0L) { i, acc, b -> acc or ((b.asLong and 0xFF) shl (8 * i)) }.takeIf { it > 0 }
        else -> null
    }

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
}
