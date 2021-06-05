rootProject.name = System.getenv("ProductName") ?: "Tachidesk"

include("server")

include("webUI")

include("AndroidCompat")
include("AndroidCompat:Config")