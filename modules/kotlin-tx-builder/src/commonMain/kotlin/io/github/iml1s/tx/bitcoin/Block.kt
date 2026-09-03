/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.iml1s.tx.bitcoin

import io.github.iml1s.tx.crypto.Crypto
import io.github.iml1s.tx.io.ByteArrayInput
import io.github.iml1s.tx.io.Input
import io.github.iml1s.tx.io.Output
import io.github.iml1s.tx.serialization.BtcSerializable
import io.github.iml1s.tx.serialization.BtcSerializer
import io.github.iml1s.tx.utils.ByteVector32
import io.github.iml1s.tx.utils.byteVector32
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** This is the double hash of a serialized block header. */
public data class BlockHash(@JvmField val value: ByteVector32) {
    public constructor(hash: ByteArray) : this(hash.byteVector32())
    public constructor(hash: String) : this(ByteVector32(hash))
    public constructor(blockId: BlockId) : this(blockId.value.reversed())

    override fun toString(): String = value.toString()
}

/** This contains the same data as [BlockHash], but encoded with the opposite endianness. */
public data class BlockId(@JvmField val value: ByteVector32) {
    public constructor(blockId: ByteArray) : this(blockId.byteVector32())
    public constructor(blockId: String) : this(ByteVector32(blockId))
    public constructor(hash: BlockHash) : this(hash.value.reversed())

    override fun toString(): String = value.toString()
}

/**
 * @param version           Block version information, based upon the software version creating this block
 * @param hashPreviousBlock The hash value of the previous block this particular block references.
 * @param hashMerkleRoot    The reference to a Merkle tree collection which is a hash of all transactions related to this block
 * @param time              A timestamp recording when this block was created (Will overflow in 2106[2])
 * @param bits              The calculated difficulty target being used for this block
 * @param nonce             The nonce used to generate this block… to allow variations of the header and compute different hashes
 */
public data class BlockHeader(
    @JvmField val version: Long,
    @JvmField val hashPreviousBlock: BlockHash,
    @JvmField val hashMerkleRoot: ByteVector32,
    @JvmField val time: Long,
    @JvmField val bits: Long,
    @JvmField val nonce: Long
) : BtcSerializable<BlockHeader> {
    @JvmField
    public val hash: BlockHash = BlockHash(Crypto.hash256(Companion.write(this)))

    @JvmField
    public val blockId: BlockId = BlockId(hash)

    public fun difficulty(): UInt256 {
        val (diff, neg, _) = UInt256.decodeCompact(bits)
        return if (neg) -diff else diff
    }

    /**
     *
     * @return the amount of work represented by this block's difficulty target, as displayed by bitcoin core
     */
    public fun blockProof(): UInt256 = blockProof(bits)

    /**
     * Proof of work: hash(header) <= target difficulty
     *
     * @return true if this block header validates its expected proof of work
     */
    public fun checkProofOfWork(): Boolean {
        val (target, _, _) = UInt256.decodeCompact(bits)
        val hash = UInt256(blockId.value.toByteArray())
        return hash <= target
    }

    public companion object : BtcSerializer<BlockHeader>() {
        override fun read(input: Input, protocolVersion: Long): BlockHeader {
            val version = uint32(input)
            val hashPreviousBlock = BlockHash(hash(input))
            val hashMerkleRoot = hash(input)
            val time = uint32(input)
            val bits = uint32(input)
            val nonce = uint32(input)
            return BlockHeader(
                version.toLong(),
                hashPreviousBlock,
                hashMerkleRoot.byteVector32(),
                time.toLong(),
                bits.toLong(),
                nonce.toLong()
            )
        }

        override fun write(message: BlockHeader, output: Output, protocolVersion: Long) {
            writeUInt32(message.version.toUInt(), output)
            writeBytes(message.hashPreviousBlock.value.toByteArray(), output)
            writeBytes(message.hashMerkleRoot.toByteArray(), output)
            writeUInt32(message.time.toUInt(), output)
            writeUInt32(message.bits.toUInt(), output)
            writeUInt32(message.nonce.toUInt(), output)
        }

        @JvmStatic
        public fun blockProof(bits: Long): UInt256 {
            val (target, negative, overflow) = UInt256.decodeCompact(bits)
            return if (target == UInt256.Zero || negative || overflow) UInt256.Zero else {
                val work = target.inv()
                work /= target.inc()
                work.inc()
            }
        }

        @JvmStatic
        public fun checkProofOfWork(header: BlockHeader): Boolean = header.checkProofOfWork()
    }

    override fun serializer(): BtcSerializer<BlockHeader> = Companion
}

/**
 * see https://en.bitcoin.it/wiki/Protocol_specification#Merkle_Trees
 */
public object MerkleTree {
    @JvmStatic
    public tailrec fun computeRoot(tree: List<ByteVector32>): ByteVector32 {
        return when {
            tree.isEmpty() -> ByteVector32.Zeroes
            tree.size == 1 -> tree[0]
            (tree.size % 2) != 0 -> computeRoot(tree + listOf(tree.last())) // append last element again
            else -> {
                val tree1 = mutableListOf<ByteVector32>()
                for (i in 0 until (tree.size / 2)) {
                    val hash = Crypto.hash256(tree[2 * i].toByteArray() + tree[2 * i + 1].toByteArray())
                    tree1.add(hash.byteVector32())
                }
                computeRoot(tree1.toList())
            }
        }
    }
}

public data class Block(@JvmField val header: BlockHeader, @JvmField val tx: List<Transaction>) {
    @JvmField
    val hash: BlockHash = header.hash

    @JvmField
    val blockId: BlockId = header.blockId

    /**
     * Proof of work: hash(block) <= target difficulty
     *
     * @return true if the input block validates its expected proof of work
     */
    public fun checkProofOfWork(): Boolean = BlockHeader.checkProofOfWork(header)

    public companion object : BtcSerializer<Block>() {
        override fun write(message: Block, out: Output, protocolVersion: Long) {
            BlockHeader.write(message.header, out)
            writeVarint(message.tx.size.toULong(), out)
            message.tx.forEach { transaction ->
                writeBytes(transaction.serialize(), out)
            }
        }

        override fun read(input: Input, protocolVersion: Long): Block {
            val headerBytes = bytes(input, 80)
            val header = BlockHeader.read(headerBytes)
            val txCount = varint(input).toInt()
            val txs = mutableListOf<Transaction>()
            
            // Read all remaining bytes for transactions
            val remainingBytes = bytes(input, input.availableBytes)
            val reader = ByteArrayReader(remainingBytes)
            
            for (i in 0 until txCount) {
                val tx = Transaction.read(reader)
                txs.add(tx)
            }
            return Block(header, txs)
        }

        @JvmField
        public val LivenetGenesisBlock: Block = run {
            val coinbaseScript = byteArrayOf(0x04, 0xff.toByte(), 0xff.toByte(), 0x00, 0x1d, 0x01, 0x04, 0x45) + 
                                 "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks".encodeToByteArray()
            
            val scriptPubKey = byteArrayOf(0x41.toByte()) + 
                               io.github.iml1s.crypto.Hex.decode("04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f") +
                               byteArrayOf(OpCodes.OP_CHECKSIG.toByte())
            
            Block(
                BlockHeader(
                    version = 1,
                    hashPreviousBlock = BlockHash(ByteVector32.Zeroes),
                    hashMerkleRoot = ByteVector32("3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a"),
                    time = 1231006505,
                    bits = 0x1d00ffff,
                    nonce = 2083236893
                ),
                listOf(
                    Transaction(
                        version = 1,
                        inputs = listOf(TxInput(previousTxHash = ByteArray(32), previousOutputIndex = 0xFFFFFFFFL, scriptSig = coinbaseScript)),
                        outputs = listOf(TxOutput(value = 5000000000L, scriptPubKey = scriptPubKey)),
                        lockTime = 0
                    )
                )
            )
        }

        @JvmField
        public val Testnet3GenesisBlock: Block = LivenetGenesisBlock.copy(
            header = LivenetGenesisBlock.header.copy(time = 1296688602, nonce = 414098458)
        )

        @JvmField
        public val RegtestGenesisBlock: Block = LivenetGenesisBlock.copy(
            header = LivenetGenesisBlock.header.copy(
                bits = 0x207fffffL,
                nonce = 2,
                time = 1296688602
            )
        )

        @JvmField
        public val SignetGenesisBlock: Block = LivenetGenesisBlock.copy(
            header = LivenetGenesisBlock.header.copy(
                bits = 503543726,
                time = 1598918400,
                nonce = 52613770
            )
        )
    }
}
