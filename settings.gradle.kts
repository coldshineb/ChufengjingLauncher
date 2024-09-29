rootProject.name = "ChufengjingLauncher"
include(
    "HMCL",
    "HMCLCore",
    "HMCLTransformerDiscoveryService"
)

val minecraftLibraries = listOf("HMCLTransformerDiscoveryService")

for (library in minecraftLibraries) {
    project(":$library").projectDir = file("minecraft/libraries/$library")
}
