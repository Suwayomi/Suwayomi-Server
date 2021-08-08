rootProject.name = System.getenv("ProductName") ?: "Tachidesk-Server"

include("server")

include("AndroidCompat")
include("AndroidCompat:Config")