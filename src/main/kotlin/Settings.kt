package ch.pentagrid.burpexts.responseoverview

import java.io.Serializable

data class Settings(
    var maxGroups: Int = 1000, var similarity: Double = 96.0, var responseMaxSize: Int = 1024*1024,
    var debug: Boolean = false): Serializable{

    override fun toString(): String {
        return "maxGroups: $maxGroups, similarity: $similarity, responseMaxSize: $responseMaxSize, debug: $debug"
    }
}