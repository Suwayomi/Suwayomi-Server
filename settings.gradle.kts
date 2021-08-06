rootProject.name = System.getenv("ProductName") ?: "Tachidesk"

include("server")

include("AndroidCompat")
include("AndroidCompat:Config")