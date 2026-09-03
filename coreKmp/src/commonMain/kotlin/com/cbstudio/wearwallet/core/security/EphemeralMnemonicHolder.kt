package com.cbstudio.wearwallet.core.security

/**
 * 助記詞暫存持有器 (Ephemeral Mnemonic Holder)
 * 以 CharArray 形式儲存單詞，提供顯式記憶體抹除與生命週期保護。
 */
class EphemeralMnemonicHolder(
    wordsChars: List<CharArray>
) {
    private val _wordsChars: MutableList<CharArray> = wordsChars.map { it.copyOf() }.toMutableList()
    private var _isCleared: Boolean = false

    val wordCount: Int get() = if (_isCleared) 0 else _wordsChars.size
    val isCleared: Boolean get() = _isCleared

    fun getWordChars(index: Int): CharArray? {
        if (_isCleared) return null
        return _wordsChars.getOrNull(index)
    }

    fun getWords(): List<String> {
        if (_isCleared) return emptyList()
        return _wordsChars.map { String(it) }
    }

    fun clear() {
        if (_isCleared) return
        _isCleared = true
        for (chars in _wordsChars) {
            chars.fill('\u0000')
        }
        _wordsChars.clear()
    }

    companion object {
        fun fromMnemonicChars(mnemonic: CharArray): EphemeralMnemonicHolder {
            val list = mutableListOf<CharArray>()
            var start = 0
            var i = 0
            while (i <= mnemonic.size) {
                if (i == mnemonic.size || mnemonic[i] == ' ') {
                    if (i > start) {
                        val word = CharArray(i - start)
                        mnemonic.copyInto(word, destinationOffset = 0, startIndex = start, endIndex = i)
                        list.add(word)
                    }
                    start = i + 1
                }
                i++
            }
            return EphemeralMnemonicHolder(list)
        }

        fun fromWords(words: List<String>): EphemeralMnemonicHolder {
            val list = words.map { it.toCharArray() }
            val holder = EphemeralMnemonicHolder(list)
            for (chars in list) {
                chars.fill('\u0000')
            }
            return holder
        }
    }
}
