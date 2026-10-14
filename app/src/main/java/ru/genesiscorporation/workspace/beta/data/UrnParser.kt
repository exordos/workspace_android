package ru.genesiscorporation.workspace.beta.data

object UrnParser {
    /** Parse the namespace before interpreting the identifier, which may itself contain colons. */
    fun namespace(urn: String?): String? {
        if (urn == null || !urn.startsWith("urn:")) return null
        val separator = urn.indexOf(':', startIndex = 4)
        if (separator <= 4 || separator == urn.lastIndex) return null
        return urn.substring(4, separator)
    }

    fun parseUrl(urn: String?, baseUrl: String): String? {
        val namespace = namespace(urn) ?: return null
        requireNotNull(urn)
        val body = urn.removePrefix("urn:")
        val queryIndex = body.indexOf('?')
        val (path, query) = if (queryIndex >= 0) {
            body.substring(0, queryIndex) to body.substring(queryIndex) // includes '?'
        } else {
            body to ""
        }
        val parts = path.split(':', limit = 2)
        if (parts.size != 2) return null
        val (_, id) = parts
        if (namespace.isEmpty() || id.isEmpty()) return null
        when(namespace) {
            "image" -> return "$baseUrl/api/workspace/v1/messenger/files/${id}/actions/download"
            "gravatar" -> return "https://secure.gravatar.com/avatar/${id}?d=identicon"
            "url" -> return id
            else -> return null
        }
    }
}
