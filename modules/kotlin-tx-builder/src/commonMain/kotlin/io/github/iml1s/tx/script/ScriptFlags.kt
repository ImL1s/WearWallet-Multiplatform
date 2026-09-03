package io.github.iml1s.tx.script

/**
 * Script 驗證旗標常數
 *
 * 完整移植自 bitcoin-kmp ScriptFlags.kt，保持 Bitcoin Core 兼容性。
 */
public object ScriptFlags {
    public const val SCRIPT_VERIFY_NONE: Int = 0

    // Evaluate P2SH subscripts (softfork safe, BIP16).
    public const val SCRIPT_VERIFY_P2SH: Int = (1 shl 0)

    // Passing a non-strict-DER signature or one with undefined hashtype to a checksig operation causes script failure.
    public const val SCRIPT_VERIFY_STRICTENC: Int = (1 shl 1)

    // Passing a non-strict-DER signature to a checksig operation causes script failure (softfork safe, BIP62 rule 1)
    public const val SCRIPT_VERIFY_DERSIG: Int = (1 shl 2)

    // Passing a non-strict-DER signature or one with S > order/2 to a checksig operation causes script failure
    public const val SCRIPT_VERIFY_LOW_S: Int = (1 shl 3)

    // verify dummy stack item consumed by CHECKMULTISIG is of zero-length (softfork safe, BIP62 rule 7).
    public const val SCRIPT_VERIFY_NULLDUMMY: Int = (1 shl 4)

    // Using a non-push operator in the scriptSig causes script failure (softfork safe, BIP62 rule 2).
    public const val SCRIPT_VERIFY_SIGPUSHONLY: Int = (1 shl 5)

    // Require minimal encodings for all push operations
    public const val SCRIPT_VERIFY_MINIMALDATA: Int = (1 shl 6)

    // Discourage use of NOPs reserved for upgrades (NOP1-10)
    public const val SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_NOPS: Int = (1 shl 7)

    // Require that only a single stack element remains after evaluation.
    public const val SCRIPT_VERIFY_CLEANSTACK: Int = (1 shl 8)

    // Verify CHECKLOCKTIMEVERIFY (BIP65)
    public const val SCRIPT_VERIFY_CHECKLOCKTIMEVERIFY: Int = (1 shl 9)

    // See BIP112 for details
    public const val SCRIPT_VERIFY_CHECKSEQUENCEVERIFY: Int = (1 shl 10)

    // Support segregated witness
    public const val SCRIPT_VERIFY_WITNESS: Int = (1 shl 11)

    // Making v2-v16 witness program non-standard
    public const val SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_WITNESS_PROGRAM: Int = (1 shl 12)

    // Segwit script only: Require the argument of OP_IF/NOTIF to be exactly 0x01 or empty vector
    public const val SCRIPT_VERIFY_MINIMALIF: Int = (1 shl 13)

    // Signature(s) must be empty vector if an CHECK(MULTI)SIG operation failed
    public const val SCRIPT_VERIFY_NULLFAIL: Int = (1 shl 14)

    // Public keys in segregated witness scripts must be compressed
    public const val SCRIPT_VERIFY_WITNESS_PUBKEYTYPE: Int = (1 shl 15)

    // Making OP_CODESEPARATOR and FindAndDelete fail any non-segwit scripts
    public const val SCRIPT_VERIFY_CONST_SCRIPTCODE: Int = (1 shl 16)

    // Taproot/Tapscript validation (BIPs 341 & 342)
    public const val SCRIPT_VERIFY_TAPROOT: Int = (1 shl 17)

    // Making unknown Taproot leaf versions non-standard
    public const val SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_TAPROOT_VERSION: Int = (1 shl 18)

    // Making unknown OP_SUCCESS non-standard
    public const val SCRIPT_VERIFY_DISCOURAGE_OP_SUCCESS: Int = (1 shl 19)

    // Making unknown public key versions (in BIP 342 scripts) non-standard
    public const val SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_PUBKEYTYPE: Int = (1 shl 20)

    /**
     * Mandatory script verification flags that all new blocks must comply with.
     */
    public const val MANDATORY_SCRIPT_VERIFY_FLAGS: Int = SCRIPT_VERIFY_P2SH

    /**
     * Standard script verification flags that standard transactions will comply with.
     */
    public const val STANDARD_SCRIPT_VERIFY_FLAGS: Int = MANDATORY_SCRIPT_VERIFY_FLAGS or
            SCRIPT_VERIFY_DERSIG or
            SCRIPT_VERIFY_STRICTENC or
            SCRIPT_VERIFY_MINIMALDATA or
            SCRIPT_VERIFY_NULLDUMMY or
            SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_NOPS or
            SCRIPT_VERIFY_CLEANSTACK or
            SCRIPT_VERIFY_MINIMALIF or
            SCRIPT_VERIFY_NULLFAIL or
            SCRIPT_VERIFY_CHECKLOCKTIMEVERIFY or
            SCRIPT_VERIFY_CHECKSEQUENCEVERIFY or
            SCRIPT_VERIFY_LOW_S or
            SCRIPT_VERIFY_WITNESS or
            SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_WITNESS_PROGRAM or
            SCRIPT_VERIFY_WITNESS_PUBKEYTYPE or
            SCRIPT_VERIFY_CONST_SCRIPTCODE or
            SCRIPT_VERIFY_TAPROOT or
            SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_TAPROOT_VERSION or
            SCRIPT_VERIFY_DISCOURAGE_OP_SUCCESS or
            SCRIPT_VERIFY_DISCOURAGE_UPGRADABLE_PUBKEYTYPE

    /** For convenience, standard but not mandatory verify flags. */
    public const val STANDARD_NOT_MANDATORY_VERIFY_FLAGS: Int = STANDARD_SCRIPT_VERIFY_FLAGS and MANDATORY_SCRIPT_VERIFY_FLAGS.inv()
}
