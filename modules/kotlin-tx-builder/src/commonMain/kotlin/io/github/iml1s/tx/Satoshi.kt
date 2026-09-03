package io.github.iml1s.tx

public data class Satoshi(val sat: Long) : Comparable<Satoshi> {
    // @formatter:off
    public operator fun plus(other: Satoshi): Satoshi = Satoshi(sat + other.sat)
    public operator fun minus(other: Satoshi): Satoshi = Satoshi(sat - other.sat)
    public operator fun times(m: Int): Satoshi = Satoshi(sat * m)
    public operator fun times(m: Long): Satoshi = Satoshi(sat * m)
    public operator fun times(m: Double): Satoshi = Satoshi((sat * m).toLong())
    public operator fun div(d: Int): Satoshi = Satoshi(sat / d)
    public operator fun div(d: Long): Satoshi = Satoshi(sat / d)
    public operator fun unaryMinus(): Satoshi = Satoshi(-sat)

    override fun compareTo(other: Satoshi): Int = sat.compareTo(other.sat)

    public fun max(other: Satoshi): Satoshi = if (this > other) this else other
    public fun min(other: Satoshi): Satoshi = if (this < other) this else other

    public fun toLong(): Long = sat

    public fun toULong(): ULong = sat.toULong() // requires Kotlin 1.3+
    override fun toString(): String = "$sat sat"
    // @formatter:on

    public companion object {
        public const val COIN: Long = 100_000_000L
        public val MAX_MONEY: Satoshi = Satoshi(21_000_000L * COIN)
    }
}

public fun Long.sat(): Satoshi = Satoshi(this)
public fun Long.toSatoshi(): Satoshi = Satoshi(this)
public fun Int.sat(): Satoshi = Satoshi(this.toLong())
public fun Int.toSatoshi(): Satoshi = Satoshi(this.toLong())
