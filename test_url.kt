fun extractDomain(url: String): String {
    return try {
        val uri = java.net.URI(url)
        val domain = uri.host ?: return url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        domain.removePrefix("www.").lowercase()
    } catch (e: Exception) {
        url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
    }
}

fun main() {
    println(extractDomain("https://WWW.eXample.COM/path"))
}
