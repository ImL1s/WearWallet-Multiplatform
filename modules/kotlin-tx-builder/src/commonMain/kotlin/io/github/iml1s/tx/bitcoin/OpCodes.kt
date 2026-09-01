package io.github.iml1s.tx.bitcoin

/**
 * Bitcoin Script OP Codes
 * 
 * 簡化版實作，只包含常用的 OP Codes
 * 參考: https://en.bitcoin.it/wiki/Script
 */
object OpCodes {
    // Constants
    const val OP_0 = 0x00
    const val OP_FALSE = OP_0
    const val OP_PUSHDATA1 = 0x4c
    const val OP_PUSHDATA2 = 0x4d
    const val OP_PUSHDATA4 = 0x4e
    const val OP_1NEGATE = 0x4f
    const val OP_RESERVED = 0x50
    const val OP_1 = 0x51
    const val OP_TRUE = OP_1
    const val OP_2 = 0x52
    const val OP_3 = 0x53
    const val OP_4 = 0x54
    const val OP_5 = 0x55
    const val OP_6 = 0x56
    const val OP_7 = 0x57
    const val OP_8 = 0x58
    const val OP_9 = 0x59
    const val OP_10 = 0x5a
    const val OP_11 = 0x5b
    const val OP_12 = 0x5c
    const val OP_13 = 0x5d
    const val OP_14 = 0x5e
    const val OP_15 = 0x5f
    const val OP_16 = 0x60

    // Flow control
    const val OP_NOP = 0x61
    const val OP_IF = 0x63
    const val OP_NOTIF = 0x64
    const val OP_ELSE = 0x67
    const val OP_ENDIF = 0x68
    const val OP_VERIFY = 0x69
    const val OP_RETURN = 0x6a

    // Stack
    const val OP_DUP = 0x76
    const val OP_DROP = 0x75
    const val OP_SWAP = 0x7c

    // Bitwise logic
    const val OP_EQUAL = 0x87
    const val OP_EQUALVERIFY = 0x88

    // Crypto
    const val OP_RIPEMD160 = 0xa6
    const val OP_SHA256 = 0xa8
    const val OP_HASH160 = 0xa9
    const val OP_HASH256 = 0xaa
    const val OP_CHECKSIG = 0xac
    const val OP_CHECKSIGVERIFY = 0xad
    const val OP_CHECKMULTISIG = 0xae
    const val OP_CHECKMULTISIGVERIFY = 0xaf

    // Locktime
    const val OP_CHECKLOCKTIMEVERIFY = 0xb1
    const val OP_CHECKSEQUENCEVERIFY = 0xb2

    // Tapscript (BIP-342)
    const val OP_CHECKSIGADD = 0xba
}
