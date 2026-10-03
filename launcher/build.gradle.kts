import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "dnz.launcher"
version = "0.1.0"

/** DNZ Launcher's released version (installer, Mac app, Windows "Installed apps"). */
val launcherVersion = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("com.google.code.gson:gson:2.13.1")
    // Dispatchers.Main for background work (installs, AUTO) on the desktop window; same version as coroutines-core.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "dnz.launcher.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "DNZ Launcher"
            packageVersion = launcherVersion
            // java.management: PC scan (RAM); jdk.crypto.ec etc.: HTTPS; java.net.http: downloads and sign-in.
            // jdk.httpserver: Microsoft sign-in page returns to DNZ; jdk.management: RAM size for the PC scan.
            // (list from "gradlew suggestRuntimeModules")
            modules("java.compiler", "java.instrument", "java.management", "jdk.management", "java.naming", "java.net.http", "java.sql",
                "jdk.httpserver", "jdk.unsupported")
            windows {
                menuGroup = "DNZ"
                shortcut = true
                iconFile.set(project.file("icons/dnz.ico"))
            }
            macOS {
                bundleID = "com.dnz.launcher"
                appCategory = "public.app-category.games"
                dockName = "DNZ Launcher"
                // Mac icon made from the DNZ logo by the GitHub build (see .github/workflows/macos.yml).
                val icns = rootDir.resolve("build/icon/dnz.icns")
                if (icns.exists()) iconFile.set(icns)
            }
        }
    }
}

// The app jar (installer, Mac app, preview) leaves out the developer tools: checks, screenshots, pack builder.
// The dnz tasks below run them from the compiled classes instead.
tasks.jar {
    // An old private key may still lie in the resources folder; it never goes into a release.
    exclude("curseforge.key")
    exclude("dnz/launcher/*TestKt*.class", "dnz/launcher/*ScreenshotsKt*.class", "dnz/launcher/Shot.class", "dnz/launcher/MrpackBuilder*.class")
}

// Copies the app and all libraries into build/preview/lib (used by the desktop shortcut).
tasks.register<Sync>("preview") {
    group = "dnz"
    from(tasks.jar)
    // Some libraries share a file name (e.g. androidx vs jetbrains builds), so prefix each with its cache folder.
    from(sourceSets["main"].runtimeClasspath.filter { it.name.endsWith(".jar") }) {
        eachFile { name = file.parentFile.name.take(8) + "-" + name }
    }
    into(layout.buildDirectory.dir("preview/lib"))
}

// ---------------------------------------------------------------- macOS app without a Mac (gradlew macZip)
// "DNZ Launcher.app" in a zip, built on Windows: the app's jars plus the Mac graphics libraries for both Intel and
// Apple Silicon, and a small start script that downloads Java 25 (Eclipse Temurin) on the first start.
val macRuntime: Configuration by configurations.creating
dependencies {
    macRuntime(compose.desktop.macos_x64)
    macRuntime(compose.desktop.macos_arm64)
}

val macApp = tasks.register("macApp") {
    group = "dnz"
    dependsOn(tasks.jar)
    val out = layout.buildDirectory.dir("mac/DNZ Launcher.app").get().asFile
    // Rebuilt whenever the launcher, its libraries or the bundled client jars change.
    inputs.files(tasks.jar, sourceSets["main"].runtimeClasspath, macRuntime)
    outputs.dir(out)
    doLast {
        out.deleteRecursively()
        val contents = out.resolve("Contents")
        val lib = contents.resolve("Java").apply { mkdirs() }
        // Everything the launcher needs, without the Windows graphics library, plus the Mac ones.
        val jars = sourceSets["main"].runtimeClasspath.filter { it.name.endsWith(".jar") && !it.name.contains("windows") } +
            macRuntime.filter { it.name.endsWith(".jar") } + files(tasks.jar.get().archiveFile)
        jars.files.forEach { f -> f.copyTo(lib.resolve(f.parentFile.name.take(8) + "-" + f.name), overwrite = true) }

        contents.resolve("Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleName</key><string>DNZ Launcher</string>
                <key>CFBundleDisplayName</key><string>DNZ Launcher</string>
                <key>CFBundleIdentifier</key><string>com.dnz.launcher</string>
                <key>CFBundleExecutable</key><string>dnz-launcher</string>
                <key>CFBundleIconFile</key><string>dnz</string>
                <key>CFBundlePackageType</key><string>APPL</string>
                <key>CFBundleShortVersionString</key><string>1.0.0</string>
                <key>CFBundleVersion</key><string>1</string>
                <key>LSMinimumSystemVersion</key><string>11.0</string>
                <key>LSApplicationCategoryType</key><string>public.app-category.games</string>
                <key>NSHighResolutionCapable</key><true/>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )

        // Mac icon: an .icns file holding the DNZ logo as PNG.
        val png = file("src/main/resources/logo.png").readBytes()
        val icns = ByteArrayOutputStream()
        val data = DataOutputStream(icns)
        data.writeBytes("icns"); data.writeInt(8 + 8 + png.size)
        data.writeBytes("ic08"); data.writeInt(8 + png.size); data.write(png)
        contents.resolve("Resources").apply { mkdirs() }.resolve("dnz.icns").writeBytes(icns.toByteArray())

        contents.resolve("MacOS").apply { mkdirs() }.resolve("dnz-launcher").writeText(
            """
            #!/bin/bash
            # DNZ Launcher for macOS: starts the launcher with its own Java 25 (downloaded once, Intel or Apple Silicon).
            APP="${'$'}(cd "${'$'}(dirname "${'$'}0")/../.." && pwd)"
            if [ "${'$'}(uname -m)" = "arm64" ]; then ARCH=aarch64; else ARCH=x64; fi
            DIR="${'$'}HOME/.dnzlauncher/launcher-java"
            JAVA="${'$'}DIR/${'$'}ARCH/Contents/Home/bin/java"
            if [ ! -x "${'$'}JAVA" ]; then
              osascript -e 'display notification "Java hazırlanıyor, ilk açılış bir dakika sürebilir..." with title "DNZ Launcher"'
              rm -rf "${'$'}DIR/${'$'}ARCH.tmp" && mkdir -p "${'$'}DIR/${'$'}ARCH.tmp"
              if curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/mac/${'$'}ARCH/jre/hotspot/normal/eclipse" \
                  | tar xz -C "${'$'}DIR/${'$'}ARCH.tmp" --strip-components 1; then
                rm -rf "${'$'}DIR/${'$'}ARCH" && mv "${'$'}DIR/${'$'}ARCH.tmp" "${'$'}DIR/${'$'}ARCH"
              else
                osascript -e 'display alert "DNZ Launcher" message "Java indirilemedi. İnternet bağlantını kontrol edip tekrar dene."'
                exit 1
              fi
            fi
            exec "${'$'}JAVA" -Xdock:name="DNZ Launcher" -Xdock:icon="${'$'}APP/Contents/Resources/dnz.icns" \
              -Djpackage.app-path="${'$'}APP/Contents/MacOS/dnz-launcher" \
              -cp "${'$'}APP/Contents/Java/*" dnz.launcher.MainKt "${'$'}@"
            """.trimIndent().replace("\r\n", "\n") + "\n",
        )
    }
}

tasks.register<Zip>("macZip") {
    group = "dnz"
    dependsOn(macApp)
    archiveFileName.set("DNZ-Launcher-macOS.zip")
    destinationDirectory.set(rootDir.resolve("../yayin-dosyalari/mac"))
    from(layout.buildDirectory.dir("mac")) {
        // The start script must stay runnable after unzipping on the Mac.
        eachFile { permissions { unix(if (path.endsWith("MacOS/dnz-launcher")) "rwxr-xr-x" else "rw-r--r--") } }
        dirPermissions { unix("rwxr-xr-x") }
    }
}

/** Version file for the C# programs: the version shown in setup, plus product name and version in the exe's properties. */
fun buildInfo(title: String): String =
    "using System.Reflection;\n" +
        "[assembly: AssemblyTitle(\"$title\")]\n" +
        "[assembly: AssemblyProduct(\"DNZ Launcher\")]\n" +
        "[assembly: AssemblyCompany(\"DNZ\")]\n" +
        "[assembly: AssemblyCopyright(\"Copyright (C) 2026 DNZ - GPL-3.0\")]\n" +
        "[assembly: AssemblyVersion(\"$launcherVersion.0\")]\n" +
        "[assembly: AssemblyFileVersion(\"$launcherVersion.0\")]\n" +
        "[assembly: AssemblyInformationalVersion(\"$launcherVersion\")]\n" +
        "namespace Dnz { static class BuildInfo { public const string Version = \"$launcherVersion\"; } }\n"

// ---------------------------------------------------------------- Windows installer (gradlew installer)
// DNZ-Launcher-Setup-<version>.exe: our own setup window (launcher/installer/*.cs, built with the C# compiler of
// Windows' .NET Framework), carrying the launcher jars, a Java 25 runtime and the uninstaller.
// Java: Mojang's java-runtime-epsilon (the one the launcher downloads for the game), or -PjavaRuntime=<folder>.
tasks.register("installer") {
    group = "dnz"
    dependsOn(tasks.jar)
    doLast {
        val csc = File(System.getenv("WINDIR") ?: "C:\\Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe")
        check(csc.exists()) { "C# compiler not found: $csc" }
        val runtime = File((project.findProperty("javaRuntime") as String?)
            ?: File(System.getProperty("user.home"), ".dnzlauncher/runtime/java-runtime-epsilon").path)
        check(File(runtime, "bin/javaw.exe").exists()) { "No Java runtime at $runtime (start the game once from DNZ Launcher, or pass -PjavaRuntime=)" }

        val work = layout.buildDirectory.dir("installer").get().asFile.apply { deleteRecursively(); mkdirs() }
        val src = file("installer")
        val icon = file("icons/dnz.ico")
        val logo = file("src/main/resources/logo.png")
        work.resolve("BuildInfo.cs").writeText(buildInfo("DNZ Launcher Setup"))

        fun compile(out: File, sources: List<String>, resources: List<File> = emptyList()) {
            val args = mutableListOf(
                csc.path, "/nologo", "/target:winexe", "/optimize+", "/codepage:65001", "/out:${out.path}",
                "/win32icon:${icon.path}", "/win32manifest:${src.resolve("app.manifest").path}",
                "/r:System.Windows.Forms.dll", "/r:System.Drawing.dll", "/r:System.Management.dll",
                "/r:System.IO.Compression.dll", "/r:System.IO.Compression.FileSystem.dll", "/r:Microsoft.VisualBasic.dll",
            )
            resources.forEach { args += "/resource:${it.path},${it.name}" }
            args += sources
            val process = ProcessBuilder(args).redirectErrorStream(true).start()
            val log = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "C# build failed for ${out.name}:\n$log" }
        }

        // What gets installed: uninstaller, icon, launcher jars, Java (the shortcuts start the launcher with that Java).
        val payload = work.resolve("payload").apply { mkdirs() }
        compile(payload.resolve("Uninstall DNZ Launcher.exe"),
            listOf(src.resolve("Common.cs").path, src.resolve("Uninstall.cs").path, work.resolve("BuildInfo.cs").path), listOf(logo))
        icon.copyTo(payload.resolve("dnz.ico"))
        val lib = payload.resolve("lib").apply { mkdirs() }
        (sourceSets["main"].runtimeClasspath.filter { it.name.endsWith(".jar") } + files(tasks.jar.get().archiveFile)).files.forEach { f ->
            f.copyTo(lib.resolve(f.parentFile.name.take(8) + "-" + f.name), overwrite = true)
        }
        runtime.copyRecursively(payload.resolve("runtime"))
        payload.resolve("runtime/.dnz-complete").delete()

        val zip = work.resolve("payload.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            payload.walkTopDown().filter { it.isFile }.forEach { f ->
                out.putNextEntry(ZipEntry(f.relativeTo(payload).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }

        val setup = rootDir.resolve("../yayin-dosyalari/DNZ-Launcher-Setup-$launcherVersion.exe")
        setup.parentFile.mkdirs()
        compile(setup, listOf(src.resolve("Common.cs").path, src.resolve("Setup.cs").path, work.resolve("BuildInfo.cs").path), listOf(logo, zip))
        println("Setup: ${setup.canonicalPath} (${setup.length() / (1024 * 1024)} MB)")
    }
}

// ---------------------------------------------------------------- Portable zip (gradlew portable)
// DNZ-Launcher-Portable-<version>.zip: no setup, unzip anywhere and start "DNZ Launcher.exe" (a tiny starter that runs
// the launcher with the Java runtime inside the folder).
tasks.register("portable") {
    group = "dnz"
    dependsOn(tasks.jar)
    doLast {
        val csc = File(System.getenv("WINDIR") ?: "C:\\Windows", "Microsoft.NET/Framework64/v4.0.30319/csc.exe")
        check(csc.exists()) { "C# compiler not found: $csc" }
        val runtime = File((project.findProperty("javaRuntime") as String?)
            ?: File(System.getProperty("user.home"), ".dnzlauncher/runtime/java-runtime-epsilon").path)
        check(File(runtime, "bin/javaw.exe").exists()) { "No Java runtime at $runtime (start the game once from DNZ Launcher, or pass -PjavaRuntime=)" }

        val work = layout.buildDirectory.dir("portable").get().asFile.apply { deleteRecursively(); mkdirs() }
        val folder = work.resolve("DNZ Launcher").apply { mkdirs() }
        val src = file("installer")
        val starter = folder.resolve("DNZ Launcher.exe")
        val process = ProcessBuilder(
            csc.path, "/nologo", "/target:winexe", "/optimize+", "/codepage:65001", "/out:${starter.path}",
            "/win32icon:${file("icons/dnz.ico").path}", "/win32manifest:${src.resolve("app.manifest").path}",
            "/r:System.Windows.Forms.dll", src.resolve("Portable.cs").path,
            work.resolve("BuildInfo.cs").apply { writeText(buildInfo("DNZ Launcher")) }.path,
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "C# build failed for the portable starter:\n$log" }

        val lib = folder.resolve("lib").apply { mkdirs() }
        (sourceSets["main"].runtimeClasspath.filter { it.name.endsWith(".jar") } + files(tasks.jar.get().archiveFile)).files.forEach { f ->
            f.copyTo(lib.resolve(f.parentFile.name.take(8) + "-" + f.name), overwrite = true)
        }
        runtime.copyRecursively(folder.resolve("runtime"))
        folder.resolve("runtime/.dnz-complete").delete()
        folder.resolve("README.txt").writeText(
            "DNZ Launcher $launcherVersion (portable)\r\n\r\n" +
                "Start \"DNZ Launcher.exe\". Nothing is installed; keep this folder together.\r\n" +
                "To remove it, delete this folder (your game files are in %USERPROFILE%\\.dnzlauncher).\r\n\r\n" +
                "\"DNZ Launcher.exe\" dosyasını aç. Kurulum yok; bu klasörü bir arada tut.\r\n" +
                "Silmek için klasörü sil (oyun dosyaların %USERPROFILE%\\.dnzlauncher içinde).\r\n")

        val zip = rootDir.resolve("../yayin-dosyalari/DNZ-Launcher-Portable-$launcherVersion.zip")
        zip.parentFile.mkdirs()
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            folder.walkTopDown().filter { it.isFile }.forEach { f ->
                out.putNextEntry(ZipEntry(f.relativeTo(work).invariantSeparatorsPath))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        println("Portable: ${zip.canonicalPath} (${zip.length() / (1024 * 1024)} MB)")
    }
}

// ---------------------------------------------------------------- Standalone jar (gradlew standaloneJar)
// DNZ-Launcher-<version>.jar: one file with everything (Windows and Mac graphics libraries included); double-click
// with Java 25 installed. Started by a small class built for old Java too, so older Java shows a clear message.
val boot: SourceSet = sourceSets.create("boot")
tasks.named<JavaCompile>("compileBootJava") {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.register("standaloneJar") {
    group = "dnz"
    dependsOn(tasks.jar, "bootClasses")
    doLast {
        val out = rootDir.resolve("../yayin-dosyalari/DNZ-Launcher-$launcherVersion.jar")
        out.parentFile.mkdirs()
        val jars = listOf(tasks.jar.get().archiveFile.get().asFile) +
            sourceSets["main"].runtimeClasspath.filter { it.name.endsWith(".jar") }.files +
            macRuntime.filter { it.name.endsWith(".jar") }.files
        val written = HashSet<String>()
        // Service lists (e.g. the Swing main thread for coroutines) are merged, not dropped as duplicates.
        val services = LinkedHashMap<String, StringBuilder>()
        val skip = Regex("""META-INF/(MANIFEST\.MF|INDEX\.LIST|[^/]+\.(SF|RSA|DSA|EC))|(META-INF/versions/\d+/)?module-info\.class""")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write(("Manifest-Version: 1.0\r\nMain-Class: dnz.launcher.Boot\r\nImplementation-Title: DNZ Launcher\r\n" +
                "Implementation-Version: $launcherVersion\r\n\r\n").toByteArray())
            zip.closeEntry()
            written += "META-INF/MANIFEST.MF"
            boot.output.classesDirs.forEach { dir ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val name = f.relativeTo(dir).invariantSeparatorsPath
                    if (written.add(name)) {
                        zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                }
            }
            jars.forEach { jar ->
                ZipFile(jar).use { source ->
                    source.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                        val name = e.name
                        when {
                            skip.matches(name) -> Unit
                            name.startsWith("META-INF/services/") ->
                                services.getOrPut(name) { StringBuilder() }.append(source.getInputStream(e).bufferedReader().readText().trim()).append('\n')
                            written.add(name) -> {
                                zip.putNextEntry(ZipEntry(name)); source.getInputStream(e).use { it.copyTo(zip) }; zip.closeEntry()
                            }
                        }
                    }
                }
            }
            services.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toString().toByteArray()); zip.closeEntry()
            }
        }
        println("Standalone jar: ${out.canonicalPath} (${out.length() / (1024 * 1024)} MB)")
    }
}

tasks.register<JavaExec>("installTest") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.InstallTestKt")
    args((project.findProperty("mc") as String?) ?: "26.3")
}

// Installs a mod (+ dependencies) into a throwaway profile, toggles and removes it: gradlew libraryTest -Pmc=26.3 -Pmod=iris
tasks.register<JavaExec>("libraryTest") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.LibraryTestKt")
    args((project.findProperty("mc") as String?) ?: "26.3", (project.findProperty("mod") as String?) ?: "iris")
}

// Account switching on a copy of the Minecraft Launcher's account file + skin face: gradlew accountsTest
tasks.register<JavaExec>("accountsTest") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.AccountsTestKt")
}

// Lists the other Minecraft installs on this computer and imports a throwaway one: gradlew importTest
tasks.register<JavaExec>("importTest") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.ImportTestKt")
}

// Builds the Modrinth App modpacks: gradlew mrpack -Pdnz=1.13.0 (client jars from ../client/build/libs)
tasks.register<JavaExec>("mrpack") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.MrpackBuilderKt")
    val dnz = (project.findProperty("dnz") as String?) ?: "1.13.0"
    args(dnz, rootDir.resolve("../client/build/libs").absolutePath, rootDir.resolve("../yayin-dosyalari/v$dnz").absolutePath)
}

// Renders every screen to PNG files (used to check the design without opening a window).
tasks.register<JavaExec>("screenshots") {
    group = "dnz"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dnz.launcher.ScreenshotsKt")
    // -Pshot=<name> renders only that one
    args(listOfNotNull(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath, project.findProperty("shot") as String?))
}
