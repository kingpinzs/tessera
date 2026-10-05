package app.tileshell.photos

/** How a MediaStore row's URI is compared with the row Photos' own query found (pure; `MediaRowUriTest`). */
object MediaRowUri {
    /** The spellings of the volume Photos' own query reads: MediaStore's `external`, and its primary shared volume. */
    private val SHARED = setOf("external", "external_primary")

    /**
     * `content://media/external/<kind>/media/<id>` for either spelling of the shared volume, so a row handed in as
     * `external_primary` compares equal to the one the library's query found. THE VOLUME STAYS IN THE COMPARISON
     * (A-L5): `internal` is another database whose ids are its own, so an `internal` URI — or one of any other volume
     * name — is returned as it is and never matches the external row with the same id.
     */
    fun canonical(text: String, pathSegments: List<String>): String =
        if (pathSegments.size == 4 && pathSegments[0] in SHARED) "content://media/external/${pathSegments[1]}/${pathSegments[2]}/${pathSegments[3]}" else text
}
