package ch.pentagrid.burpexts.responseoverview

import burp.HttpRequestResponse
import java.io.Serializable
import java.net.URL

data class LogEntry(
    var messageInfo: HttpRequestResponse, val toolFlag: Int, val statusCode: Short, val url: URL,
    val body: ByteArray, var charCountPrecalculated: Map<Byte, Int>?): Serializable{

    var groupSize = 1
    var hidden = false

    override fun toString(): String {
        return "ToolFlag: $toolFlag, StatusCode: $statusCode, URL: $url, GroupSize: $groupSize, body: $body, hidden: $hidden"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogEntry

        if (messageInfo != other.messageInfo) return false
        if (toolFlag != other.toolFlag) return false
        if (statusCode != other.statusCode) return false
        if (url != other.url) return false
        if (!body.contentEquals(other.body)) return false
        if (charCountPrecalculated != other.charCountPrecalculated) return false
        if (groupSize != other.groupSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = messageInfo.hashCode()
        result = 31 * result + toolFlag
        result = 31 * result + statusCode
        result = 31 * result + url.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + (charCountPrecalculated?.hashCode() ?: 0)
        result = 31 * result + groupSize
        return result
    }


}