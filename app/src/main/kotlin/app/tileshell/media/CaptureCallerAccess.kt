package app.tileshell.media

/**
 * Condition (d) of [CaptureOutputGuard]: may the app that asked for a capture write the output URI ITSELF? Asked of
 * the platform through [UriAccessPort], never inferred from the intent's flags — an intent's grant flag is not checked
 * against the caller when the shell already holds the access (the guard's doc says why). Pure, so every branch is
 * unit-tested (`CaptureCallerAccessTest`). TRUST: under the adversarial review with the guard.
 */
object CaptureCallerAccess {
    /**
     * True only when [callingPackage] (`Activity.getCallingPackage()`, the platform's word for who receives the
     * result) resolves to a uid and that uid may write [output] by [UriAccessRules.mayWrite]:
     *  - the provider behind [output] is declared by an app of that same uid (its own FileProvider: the owner of a
     *    provider may always write it); or
     *  - that uid holds an explicit write grant for [output] (`Context.checkUriPermission` answers URI grants only: it
     *    never asks the provider); or
     *  - on API 35 and later, the provider itself, asked, says that uid may write it
     *    (`Context.checkContentUriPermissionFull`) — a MediaStore row the caller owns.
     * So on API 34, where the provider cannot be asked, a MediaStore row the caller inserted and passed with no grant
     * of its own is REFUSED: it fails closed. A URI of another user's provider, an unknown authority and an unknown
     * package are false.
     */
    fun callerMayWrite(access: UriAccessPort, output: ContentUriText, callingPackage: String?): Boolean {
        if (callingPackage == null) return false
        val callerUid = access.uidOf(callingPackage) ?: return false
        return UriAccessRules.mayWrite(access, output, callerUid)
    }
}
