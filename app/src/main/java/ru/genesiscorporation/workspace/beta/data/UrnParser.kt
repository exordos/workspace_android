package ru.genesiscorporation.workspace.beta.data

object UrnParser {
    fun parseUrl(urn: String?, baseUrl: String): String? {
        require(urn != null) { return null }
        require(urn.startsWith("urn:")) { return null }
        val parts = urn.removePrefix("urn:").split(':', limit = 2)
        require(parts.size == 2) { return null }
        val (namespace, id) = parts
        when(namespace) {
            "image" -> {
                val fileUuid = id.substringBefore('?').substringBefore('#')
                return "/api/workspace/v1/messenger/files/${fileUuid}/actions/download"
            }
            "gravatar" -> return "https://gravatar.com/avatar/${id}"
            "url" -> return id
            else -> return null
        }
    }
}
