package ru.genesiscorporation.workspace.beta.data

object UrnParser {
    fun parseUrl(urn: String?, baseUrl: String): String? {
        if (urn == null || !urn.startsWith("urn:")) return null
        val body = urn.removePrefix("urn:")
        val queryIndex = body.indexOf('?')
        val (path, query) = if (queryIndex >= 0) {
            body.substring(0, queryIndex) to body.substring(queryIndex) // includes '?'
        } else {
            body to ""
        }
        val parts = path.split(':', limit = 2)
        if (parts.size != 2) return null
        val (namespace, id) = parts
        if (namespace.isEmpty() || id.isEmpty()) return null
        when(namespace) {
            "image" -> return "$baseUrl/api/workspace/v1/messenger/files/${id}/actions/download"
            "gravatar" -> return "https://secure.gravatar.com/avatar/${id}?d=identicon"
            "url" -> return id
            else -> return null
        }
    }
}