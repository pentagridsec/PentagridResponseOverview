package ch.pentagrid.burpexts.responseoverview

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

class Encoder {

    companion object {
        val allCoders: List<Coder> = listOf(
            //NoneCoder(),
            TrimCoder(),
            RemoveSpaceCoder(),
            RemoveNewlineCoder(),
            RemoveWhitespaceCoder(),
            UpperCoder(),
            LowerCoder(),
            EscaperCoder(),
            NewlineCoder(),
            HexCoder(),
            ReprCoderDoubleQuote(),
            ReprCoderSingleQuote(),
            UrlUtf8Coder(),
            UrlIso88591Coder(),
            UrlAsciiCoder(),
            PlusSpaceCoder(),
            Base64WithPaddingCoder(),
            Base64WithoutPaddingCoder(),
            Base64UrlWithPaddingCoder(),
            Base64UrlWithoutPaddingCoder(),
            Base64MimeWithPaddingCoder(),
            Base64MimeWithoutPaddingCoder()
        )

        fun getCodersWithEffect(search: String, coders: List<Coder>?=null): List<Coder>{
            if(search.isEmpty()){
                return emptyList()
            }
            val listOfCoders: MutableList<Coder> = mutableListOf()
            for (coder in coders ?: allCoders) {
                if(coder.encodingAlwaysEffectsOutput()){
                    listOfCoders.add(coder)
                    continue
                }
                if(search != coder.encode(search)){
                    listOfCoders.add(coder)
                    continue
                }
            }
            return listOfCoders.toList()
        }

        //Always finds the most simple encoder
        //TODO
        // this is not what we always want. For example screenshot.png might be a NoneCoder, but when encoding
        // something different, such as a weird filename, we would need a URL-safe encoder....
        // However, this is not for us to decide here, it's context specific. It might be wise to sometimes
        // choose something else than the one returned by this function
        fun getEncoder(search: String, haystack: String): List<Coder>? {
            if(haystack.contains(search)){
                return listOf(NoneCoder())
            }
            var numberOfEncodingsTried = 0

            // Try an encoder...
            // O(codersWithEffect.size)
            val codersWithEffect = getCodersWithEffect(search)
            //First try single encoding and check if any of them work
            for (coder in codersWithEffect) {
                if(haystack.contains(coder.encode(search))){
                    return listOf(coder)
                }
            }
            numberOfEncodingsTried += codersWithEffect.size

            // Try two encoders... 19 * 19 = 361 encoders maximum
            // if all encoders are found to have an effect *and* there is none found that works *and* all could apply multiple times
            // O(codersWithEffect.size * avg(codersWithEffectOnInner.size)) - 7
            for (inner in codersWithEffect) {
                val innerEncoded = inner.encode(search)
                val codersWithEffectOnInner = getCodersWithEffect(innerEncoded)
                for (outer in codersWithEffectOnInner) {
                    if(inner::class == outer::class && !inner.canApplyMultipleTimes()){
                        continue
                    }
                    numberOfEncodingsTried += 1
                    val outerEncoded = outer.encode(innerEncoded)
                    if(haystack.contains(outerEncoded)){
                        println("[Encoder] Tried $numberOfEncodingsTried before finding a double nested encoding that works")
                        return listOf(inner, outer)
                    }
                }
            }

            //Try three encoders... 19 * 19 * 19 = 6859 encoders maximum
            //if all encoders are found to have an effect *and* there is none found that works *and* all could apply multiple times
            //O(((codersWithEffect.size * avg(codersWithEffectOnFirst.size)) - 7) * avg(codersWithEffectOnSecond.size)) - 7
            //That's still O(n^3)
            //In practice we usually see something like 1877... and that's done blazingly fast
            //As long as this Encoder routine is not more heavily used this should be fine performance-wise
            for (first in codersWithEffect) {
                val firstEncoded = first.encode(search)
                val codersWithEffectOnFirst = getCodersWithEffect(firstEncoded)
                for (second in codersWithEffectOnFirst) {
                    if(first::class == second::class && !first.canApplyMultipleTimes()){
                        continue
                    }
                    val secondEncoded = second.encode(search)
                    val codersWithEffectOnSecond = getCodersWithEffect(secondEncoded)
                    for (third in codersWithEffectOnSecond) {
                        if(second::class == third::class && !second.canApplyMultipleTimes()){
                            continue
                        }
                        val thirdEncoded = third.encode(search)
                        numberOfEncodingsTried += 1
                        if(haystack.contains(thirdEncoded)) {
                            println("[Encoder] Tried $numberOfEncodingsTried before finding a triple nested encoding that works")
                            return listOf(first, second, third)
                        }
                    }
                }
            }
            return null
            //throw Exception("[Encoder] Tried $numberOfEncodingsTried encoding but was not able to find an encoding that fits (even when applying three encodings nested).")
        }

        fun applyEncoders(x: String, coders: List<Coder>): String{
            var new = x
            for(coder in coders){
                new = coder.encode(new)
            }
            return new
        }

        fun md5(input: String): ByteArray {
            //TODO
            return ByteArray(0)
        }

        fun sha1(input: String): ByteArray {
            //TODO
            return ByteArray(0)
        }

        fun sha256(input: String): ByteArray {
            //TODO
            return ByteArray(0)
        }

        fun sha512(input: String): ByteArray {
            //TODO
            return ByteArray(0)
        }

    }

    abstract class Coder {
        open fun encode(x: String): String = throw NotImplementedError("Encode routine not implemented")
        open fun hasEncode(): Boolean {
            return try {
                encode("");
                true
            } catch (e: NotImplementedError) {
                false
            }
        }
        open fun encodingAlwaysEffectsOutput(): Boolean {
            //For example base64 always turns a non-empty input into somethings (->true)
            //But replacing %20 with + does not always, when there is no %20 in the input (->false)
            return false
        }
        open fun canApplyMultipleTimes(): Boolean {
            //For example replace("%20", "+") can apply multiple times, as %2%200 is then ++ (->true)
            //But trimming " test " after it was already trimmed does not make any sense (->false)
            return true
        }

        open fun decode(x: String): String = throw NotImplementedError("Decode routine not implemented")
        open fun hasDecode(): Boolean {
            try {
                decode("");
                return true
            } catch (e: NotImplementedError) {
                return false
            }
        }
    }


    class NoneCoder : Coder() {
        override fun encode(x: String): String = x
        override fun decode(x: String): String = x
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class TrimCoder : Coder() {
        override fun encode(x: String): String = x.trim()
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class RemoveSpaceCoder : Coder() {
        override fun encode(x: String): String = x.replace(" ", "")
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class RemoveNewlineCoder : Coder() {
        override fun encode(x: String): String = x.replace("\n", "").replace("\r", "")
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class RemoveWhitespaceCoder : Coder() {
        override fun encode(x: String): String =
            x.replace(" ", "").replace("\t", "").replace("\n", "").replace("\r", "")
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class UpperCoder : Coder() {
        override fun encode(x: String): String = x.uppercase()
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class LowerCoder : Coder() {
        override fun encode(x: String): String = x.lowercase()
        override fun canApplyMultipleTimes(): Boolean {
            return false
        }
    }

    class EscaperCoder : Coder() {
        override fun encode(x: String): String =
            x.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("\"", "\\\"")

        override fun decode(x: String): String =
            x.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"")
    }

    class NewlineCoder : Coder() {
        override fun encode(x: String): String = x.replace("\n", "\r\n")
        override fun decode(x: String): String = x.replace("\r\n", "\n")
    }

    class HexCoder : Coder() {
        override fun encode(x: String): String = BurpExtender.h.stringToBytes(x).joinToString("") { "%02X".format(it) }
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }
        override fun decode(x: String): String =
            BurpExtender.h.bytesToString(x.chunked(2).map { it.uppercase().toInt(16).toByte() }.toByteArray())
    }

    private class ReprCoder(val delimiter: Char='"') : Coder() {
        override fun encode(x: String): String{
            val sb = StringBuilder()
            val limit = x.length
            var hexbuf: CharArray = CharArray(2)
            var pointer = 0
            sb.append(delimiter)
            while (pointer < limit)
            {
                var ch = x.get(pointer++)
                when (ch) {
                    '\u0000' -> sb.append("\\0")
                    '\t' -> sb.append("\\t")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    else -> if (' '.toInt() <= ch.toInt() && ch.toInt() <= '\u007e'.toInt())
                        sb.append(ch.toChar())
                    else
                    {
                        sb.append("\\x")
                        var offs = 2
                        while (offs > 0) {
                            hexbuf[--offs] = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')[ch.toInt() and 0xf]
                            ch = (ch.toInt() ushr 4).toChar()
                        }
                        sb.append(hexbuf, 0, 2)
                    }
                }
            }
            return sb.append(delimiter).toString()
        }

        override fun decode(x: String): String{
            val sb = StringBuilder()
            val inputString = x.removePrefix(delimiter.toString()).removeSuffix(delimiter.toString())
            val limit = inputString.length
            var pointer = 0
            val hexCoder = HexCoder()
            while (pointer < limit)
            {
                val ch = inputString[pointer++]
                when (ch) {
                    '\\' -> {
                        val secondCh = inputString[pointer++]
                        when (secondCh) {
                            '0' -> sb.append('\u0000')
                            't' -> sb.append('\t')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            delimiter -> sb.append(delimiter)
                            '\\' -> sb.append('\\')
                            'x' -> {
                                val thirdCh = inputString[pointer++]
                                val fourthCh = inputString[pointer++]
                                sb.append(hexCoder.decode(thirdCh.toString() + fourthCh.toString()))
                            }
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
    }

    class ReprCoderSingleQuote : Coder() {
        private val coder = ReprCoder('\'')
        override fun encode(x: String): String{
            return coder.encode(x)
        }

        override fun decode(x: String): String{
            return coder.decode(x)
        }
    }

    class ReprCoderDoubleQuote : Coder() {
        private val coder = ReprCoder('"')
        override fun encode(x: String): String{
            return coder.encode(x)
        }

        override fun decode(x: String): String{
            return coder.decode(x)
        }
    }

    class UrlUtf8Coder : Coder() {
        override fun encode(x: String): String = URLEncoder.encode(x, "UTF-8")
        override fun decode(x: String): String = URLDecoder.decode(x, "UTF-8")
    }

    class UrlIso88591Coder : Coder() {
        override fun encode(x: String): String = URLEncoder.encode(x, "ISO-8859-1")
        override fun decode(x: String): String = URLDecoder.decode(x, "ISO-8859-1")
    }

    class UrlAsciiCoder : Coder() {
        override fun encode(x: String): String = URLEncoder.encode(x, "US-ASCII")
        override fun decode(x: String): String = URLDecoder.decode(x, "US-ASCII")
    }

    class PlusSpaceCoder : Coder() {
        override fun encode(x: String): String = x.replace("+", "%20")
        override fun decode(x: String): String = x.replace("%20", "+")
    }

    class Base64WithPaddingCoder : Coder() {
        override fun encode(x: String): String = Base64.getEncoder().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }
        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getDecoder().decode(x))
    }

    class Base64WithoutPaddingCoder : Coder() {
        override fun encode(x: String): String =
            Base64.getEncoder().withoutPadding().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }

        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getDecoder().decode(x))
    }

    class Base64UrlWithPaddingCoder : Coder() {
        override fun encode(x: String): String = Base64.getUrlEncoder().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }
        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getUrlDecoder().decode(x))
    }

    class Base64UrlWithoutPaddingCoder : Coder() {
        override fun encode(x: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }

        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getUrlDecoder().decode(x))
    }

    //Mime friendly format, each line of the output is no longer than 76 characters and ends with a carriage return followed by a linefeed (\r\n)
    class Base64MimeWithPaddingCoder : Coder() {
        override fun encode(x: String): String = Base64.getMimeEncoder().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }
        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getMimeDecoder().decode(x))
    }

    class Base64MimeWithoutPaddingCoder : Coder() {
        override fun encode(x: String): String =
            Base64.getMimeEncoder().withoutPadding().encodeToString(BurpExtender.h.stringToBytes(x))
        override fun encodingAlwaysEffectsOutput(): Boolean {
            return true
        }

        override fun decode(x: String): String = BurpExtender.h.bytesToString(Base64.getMimeDecoder().decode(x))
    }

}