package io.grimoire.reading

private val requirementPattern = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""")
private val hostPattern = Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-SNAPSHOT)?""")

fun declaredExtensionApi(value: String?): String? = value?.takeIf { parseRequirement(it) != null }

private fun parseRequirement(value: String): Pair<Int, Int>? {
    if (value.length > 21) return null
    val match = requirementPattern.matchEntire(value) ?: return null
    val major = match.groupValues[1].toIntOrNull() ?: return null
    val minor = match.groupValues[2].toIntOrNull() ?: return null
    return major to minor
}

fun supportsExtensionApi(required: String?, host: String): Boolean {
    // APKs released before this metadata existed retain their existing compatibility probe.
    if (required == null) return true
    val requirement = parseRequirement(required) ?: return false
    if (host.length > 64) return false
    val match = hostPattern.matchEntire(host) ?: return false
    val provided = parseRequirement("${match.groupValues[1]}.${match.groupValues[2]}") ?: return false
    return requirement.first == provided.first && requirement.second <= provided.second
}
