package com.vknxworks.contactctrl.data.qr

/**
 * A small QR Code encoder (ISO/IEC 18004), byte mode only, for showing a contact as a QR code without a
 * library. It follows the structure of Project Nayuki's public QR Code generator: pick the smallest version
 * that fits, add Reed-Solomon error correction per block, interleave, place the bits, and use the mask with
 * the lowest penalty. Plain Kotlin, no Android classes, so it is unit-tested on the JVM.
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {

    /** True for a dark module at column [x], row [y]. */
    fun dark(x: Int, y: Int): Boolean = x in 0 until size && y in 0 until size && modules[y][x]

    enum class Ecc(val formatBits: Int) { LOW(1), MEDIUM(0) }

    companion object {
        /** The most bytes [encode] takes (version 40 at low error correction). */
        const val MAX_BYTES = 2953

        /**
         * [data] as a QR code at error correction [ecc] or, when it doesn't fit, the lowest level that does.
         * Null when the data is too long for any QR code.
         */
        fun encode(data: ByteArray, ecc: Ecc = Ecc.MEDIUM): QrCode? {
            val levels = if (ecc == Ecc.MEDIUM) listOf(Ecc.MEDIUM, Ecc.LOW) else listOf(Ecc.LOW)
            for (level in levels) {
                for (ver in 1..40) {
                    val countBits = if (ver <= 9) 8 else 16
                    val needed = 4 + countBits + data.size * 8
                    if (needed <= dataCodewords(ver, level) * 8) return build(ver, level, data, countBits)
                }
            }
            return null
        }

        private fun build(ver: Int, ecc: Ecc, data: ByteArray, countBits: Int): QrCode {
            val capacityBits = dataCodewords(ver, ecc) * 8
            val bits = BitList()
            bits.append(0b0100, 4)
            bits.append(data.size, countBits)
            for (b in data) bits.append(b.toInt() and 0xFF, 8)
            bits.append(0, minOf(4, capacityBits - bits.size))
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capacityBits) {
                bits.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }
            val codewords = ByteArray(bits.size / 8)
            for (i in 0 until bits.size) if (bits[i]) codewords[i ushr 3] = (codewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()

            val q = Builder(ver, ecc)
            q.drawFunctionPatterns()
            q.drawCodewords(addEccAndInterleave(codewords, ver, ecc))
            var best = 0
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                q.applyMask(mask)
                q.drawFormatBits(mask)
                val p = q.penalty()
                if (p < bestPenalty) { best = mask; bestPenalty = p }
                q.applyMask(mask) // undo (XOR)
            }
            q.applyMask(best)
            q.drawFormatBits(best)
            return QrCode(q.size, q.modules)
        }

        private val ECC_PER_BLOCK = arrayOf(
            // LOW
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            // MEDIUM
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
        )
        private val NUM_BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
        )

        private fun row(ecc: Ecc) = if (ecc == Ecc.LOW) 0 else 1

        internal fun rawDataModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        internal fun dataCodewords(ver: Int, ecc: Ecc): Int =
            rawDataModules(ver) / 8 - ECC_PER_BLOCK[row(ecc)][ver] * NUM_BLOCKS[row(ecc)][ver]

        private fun addEccAndInterleave(data: ByteArray, ver: Int, ecc: Ecc): ByteArray {
            val numBlocks = NUM_BLOCKS[row(ecc)][ver]
            val eccLen = ECC_PER_BLOCK[row(ecc)][ver]
            val rawCodewords = rawDataModules(ver) / 8
            val numShort = numBlocks - rawCodewords % numBlocks
            val shortLen = rawCodewords / numBlocks
            val divisor = rsDivisor(eccLen)
            val blocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortLen - eccLen + if (i < numShort) 0 else 1
                val dat = data.copyOfRange(k, k + datLen)
                k += datLen
                val ecc2 = rsRemainder(dat, divisor)
                val block = ByteArray(shortLen + 1)
                dat.copyInto(block, 0)
                // Short blocks get a placeholder byte so every block has the same length; it is skipped below.
                ecc2.copyInto(block, shortLen + 1 - eccLen)
                blocks += block
            }
            val out = ByteArray(rawCodewords)
            var n = 0
            for (i in 0 until shortLen + 1) {
                for (j in 0 until numBlocks) {
                    if (i != shortLen - eccLen || j >= numShort) out[n++] = blocks[j][i]
                }
            }
            return out
        }

        private fun rsDivisor(degree: Int): ByteArray {
            val result = ByteArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    result[j] = gfMul(result[j].toInt() and 0xFF, root).toByte()
                    if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
                root = gfMul(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
            val result = ByteArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() xor result[0].toInt()) and 0xFF
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = (result[i].toInt() xor gfMul(divisor[i].toInt() and 0xFF, factor)).toByte()
            }
            return result
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }
    }

    private class BitList {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size
        operator fun get(i: Int) = bits[i]
        fun append(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }
    }

    private class Builder(val ver: Int, val ecc: Ecc) {
        val size = ver * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }

        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }

        fun drawFunctionPatterns() {
            for (i in 0 until size) {
                set(6, i, i % 2 == 0)
                set(i, 6, i % 2 == 0)
            }
            finder(3, 3)
            finder(size - 4, 3)
            finder(3, size - 4)
            val pos = alignmentPositions()
            val n = pos.size
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
            drawFormatBits(0)
            drawVersion()
        }

        private fun finder(x: Int, y: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) set(xx, yy, d != 2 && d != 4)
            }
        }

        private fun alignmentPositions(): IntArray {
            if (ver == 1) return IntArray(0)
            val n = ver / 7 + 2
            val step = (ver * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val result = IntArray(n)
            result[0] = 6
            var p = size - 7
            for (i in n - 1 downTo 1) {
                result[i] = p
                p -= step
            }
            return result
        }

        fun drawFormatBits(mask: Int) {
            val data = (ecc.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = ((bits ushr i) and 1) != 0
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6))
            set(8, 8, bit(7))
            set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun drawVersion() {
            if (ver < 7) return
            var rem = ver
            repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
            val bits = (ver shl 12) or rem
            for (i in 0 until 18) {
                val b = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val c = i / 3
                set(a, c, b)
                set(c, a, b)
            }
        }

        fun drawCodewords(data: ByteArray) {
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!function[y][x] && i < data.size * 8) {
                            modules[y][x] = ((data[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                            i++
                        }
                    }
                }
                right -= 2
            }
        }

        fun applyMask(mask: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !function[y][x]) modules[y][x] = !modules[y][x]
            }
        }

        /** The standard mask penalty (runs, 2x2 blocks, finder-like patterns, dark/light balance). */
        fun penalty(): Int {
            var result = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until size) {
                    if (get(i) == get(i - 1)) {
                        run++
                        if (run == 5) result += 3 else if (run > 5) result += 1
                    } else run = 1
                }
                // Finder-like 1:1:3:1:1 with four light modules on either side (outside the symbol counts as light).
                fun at(i: Int) = i in 0 until size && get(i)
                for (i in -4 until size) {
                    val core = at(i) && !at(i + 1) && at(i + 2) && at(i + 3) && at(i + 4) && !at(i + 5) && at(i + 6)
                    if (!core) continue
                    val before = (1..4).none { at(i - it) }
                    val after = (7..10).none { at(i + it) }
                    if (before) result += 40
                    if (after) result += 40
                }
            }
            for (y in 0 until size) line { modules[y][it] }
            for (x in 0 until size) line { modules[it][x] }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3
            }
            var dark = 0
            for (r in modules) for (m in r) if (m) dark++
            val total = size * size
            val k = (kotlin.math.abs(dark * 20 - total * 10) + total - 1) / total - 1
            result += maxOf(0, k) * 10
            return result
        }
    }
}
