// DNZ Client for Minecraft 1.8.9 (Forge). Built with Essential's Loom fork, which still supports 1.8.9 Forge.
// Gradle runs on Java 17; the mod is compiled for Java 8, the Java Minecraft 1.8.9 runs on.
// Mixin (bundled inside the jar, started by its tweaker) lets DNZ change Minecraft's own drawing code (DNZ Engine).
plugins {
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.dnz"
base.archivesName.set("dnz-client-mc1.8.9")

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    runConfigs {
        "client" {
            property("mixin.debug", "true")
            programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.dnzclient.json")
    }
    mixin {
        defaultRefmapName.set("mixins.dnzclient.refmap.json")
    }
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("mcmod.info") {
        expand("version" to project.version)
    }
}

tasks.withType<Jar> {
    manifest.attributes(
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "MixinConfigs" to "mixins.dnzclient.json",
    )
}

tasks.shadowJar {
    archiveClassifier.set("all-dev")
    configurations = listOf(shadowImpl)
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.remapJar {
    input.set(tasks.shadowJar.get().archiveFile)
    archiveClassifier.set("")
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.assemble.get().dependsOn(tasks.remapJar)
