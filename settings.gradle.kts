// The ProductName env var renames the root project (and thereby the built
// jar/distribution name); used by downstream forks producing differently
// named artifacts. Defaults to the upstream name.
rootProject.name = System.getenv("ProductName") ?: "Suwayomi-Server"

include("server")
include("server:i18n")
include("server:server-config")
include("server:server-config-generate")

include("AndroidCompat")
include("AndroidCompat:Config")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
