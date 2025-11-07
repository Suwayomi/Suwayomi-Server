rootProject.name = System.getenv("ProductName") ?: "Suwayomi-Server"

include("server")
include("server:i18n")
include("server:server-config")
include("server:server-config-generate")

include("AndroidCompat")
include("AndroidCompat:Config")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
