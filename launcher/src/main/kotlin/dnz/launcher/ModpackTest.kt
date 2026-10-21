package dnz.launcher

/** Searches Modrinth modpacks and installs the smallest result into a throwaway profile: gradlew modpackTest */
fun main() {
    val page = Modpacks.search("", listOf("26.3", "26.2"), Modrinth.Sort.Downloads, 0, 10)
    println("found ${page.total}: " + page.hits.joinToString { it.title })
    val hit = page.hits.firstOrNull() ?: return
    val result = Modpacks.install(hit.id, "DNZ Modpack Test", listOf("26.3", "26.2"), emptyList()) { print("\r$it   ") }
    val dir = GameLauncher.instanceDir(result.profile)
    println("\nprofile ${result.profile.name} mc ${result.profile.version}: ${result.files} files, ${result.skipped} skipped, mods=${java.io.File(dir, "mods").list()?.size}")
    dir.deleteRecursively()
    println("cleaned")
}
