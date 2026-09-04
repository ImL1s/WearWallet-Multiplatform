package io.github.iml1s.tx.bitcoin

import io.github.iml1s.address.AddressGenerator
import io.github.iml1s.address.Base58
import io.github.iml1s.address.Bech32
import io.github.iml1s.tx.utils.ByteVector32

/**
 * 交易建構器 DSL
 */
class TxBuilder(val network: AddressGenerator.Network = AddressGenerator.Network.MAINNET) {
    var version: Int = 2
    var lockTime: Long = 0
    private val inputs = mutableListOf<TxInput>()
    private val outputs = mutableListOf<TxOutput>()
    private val witnesses = mutableListOf<TxWitness>()

    /**
     * 新增交易輸入
     */
    fun input(txid: String, vout: Long, sequence: Long = TxInput.SEQUENCE_FINAL, scriptSig: ByteArray = ByteArray(0)) {
        val hash = ByteVector32(txid).toByteArray().reversedArray() // txid is reversed hash
        inputs.add(TxInput(hash, vout, scriptSig, sequence))
        witnesses.add(TxWitness.EMPTY)
    }

    /**
     * 新增交易輸出 (使用 scriptPubKey)
     */
    fun output(amount: Long, scriptPubKey: ByteArray) {
        outputs.add(TxOutput(amount, scriptPubKey))
    }

    /**
     * 新增交易輸出 (使用地址)
     */
    fun output(address: String, amount: Long) {
        val script = addressToScript(address)
        outputs.add(TxOutput(amount, script))
    }

    /**
     * 建構交易物件
     */
    fun build(): Transaction {
        return Transaction(
            version = version,
            inputs = inputs,
            outputs = outputs,
            witnesses = witnesses,
            lockTime = lockTime
        )
    }

    private fun addressToScript(address: String): ByteArray {
        // 1. Try SegWit (Bech32/Bech32m)
        val segwit = Bech32.decodeSegwitAddress(address)
        if (segwit != null) {
            val (version, program) = segwit
            return when (version) {
                0 -> if (program.size == 20) Script.pay2wpkh(program) else Script.pay2wsh(program)
                1 -> Script.pay2tr(program)
                else -> throw IllegalArgumentException("Unsupported witness version: $version")
            }
        }

        // 2. Try Legacy/Nested SegWit (Base58)
        val base58 = try { Base58.decodeCheck(address) } catch (e: Exception) { null }
        if (base58 != null) {
            val version = base58.first
            val hash = base58.second
            return when (version) {
                network.p2pkhVersion -> Script.pay2pkh(hash)
                network.p2shVersion -> Script.pay2sh(hash)
                else -> throw IllegalArgumentException("Address version $version does not match network ${network.name}")
            }
        }

        throw IllegalArgumentException("Invalid or unsupported bitcoin address: $address")
    }
}

/**
 * DSL 入口函數
 */
fun tx(network: AddressGenerator.Network = AddressGenerator.Network.MAINNET, block: TxBuilder.() -> Unit): Transaction {
    val builder = TxBuilder(network)
    builder.block()
    return builder.build()
}
