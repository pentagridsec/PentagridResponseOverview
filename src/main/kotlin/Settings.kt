package ch.pentagrid.burpexts.responseoverview

import java.io.Serializable

data class Settings(
    var maxGroups: Int = 2000,
    var similarity: Double = 98.0,
    var responseMaxSize: Int = 1024*1024,
    var removeRegex: String = "",
    var maxGroupsWithSameResponseBodySize: Int = 30,
    var removeParameter: Boolean = true,
    var debug: Boolean = false): Serializable{

    override fun toString(): String {
        return "maxGroups: $maxGroups, similarity: $similarity, responseMaxSize: $responseMaxSize, " +
                "removeRegex: $removeRegex, maxGroupsWithSameResponseBodySize: $maxGroupsWithSameResponseBodySize, " +
                "removeParameter: $removeParameter, debug: $debug"
    }
}