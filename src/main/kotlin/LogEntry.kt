package ch.pentagrid.burpexts.responseoverview

import burpwrappers.SerializableHttpRequestResponse
import java.io.*
import java.net.URL


//TODO FIXME: THIS IS THE STRANGEST THING I'VE EXPERIENCED IN QUITE A WHILE
//I don't know why, but the "hidden" field is simply not correctly serialized.

data class LogEntry(
    var messageInfo: SerializableHttpRequestResponse, var toolFlag: Int, var statusCode: Short, var url: URL,
    var body: ByteArray, var removeParameterBody: ByteArray?, var charCountPrecalculated: Map<Byte, Int>?, var groupSize: Int,
    var hidden: Boolean
): Serializable{

    override fun toString(): String {
        return "ToolFlag: $toolFlag, StatusCode: $statusCode, URL: $url, GroupSize: $groupSize, hidden: $hidden, body: $body"
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
        if (hidden != other.hidden) return false

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
        result = 31 * result + hidden.hashCode()
        return result
    }

    /*
    @Throws(IOException::class)
    @Override
    private fun writeObject(out: ObjectOutputStream) {
        out.writeObject(messageInfo)
        out.writeObject(toolFlag)
        out.writeObject(statusCode)
        out.writeObject(url)
        out.writeObject(body)
        out.writeObject(charCountPrecalculated)
        out.writeObject(groupSize)
        out.writeObject(foobar)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    @Override
    private fun readObject(input: ObjectInputStream) {
        //messageInfo = (HttpRequestResponse) input
        messageInfo = input.readObject() as HttpRequestResponse
        toolFlag = input.readObject() as Int
        statusCode = input.readObject() as Short
        url = input.readObject() as URL
        body = input.readObject() as ByteArray
        charCountPrecalculated = input.readObject() as Map<Byte, Int>?
        groupSize = input.readObject() as Int
        foobar = input.readObject() as Int
    }
*/
}