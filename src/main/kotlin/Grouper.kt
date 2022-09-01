package ch.pentagrid.burpexts.responseoverview

import java.util.regex.PatternSyntaxException
import kotlin.system.measureTimeMillis

class Grouper : Thread() {

    var stop = false
    private var paused = false
    private val newCandidates = mutableListOf<LogEntry>()

    companion object{
        fun println(s: String){
            BurpExtender.stdout.println(s)
        }
    }

    fun addNewCandidate(candidate: LogEntry) {
        synchronized(newCandidates){
            newCandidates.add(candidate)
        }
    }

    override fun run() {
        while(!stop){
            if(!paused){
                var candidate: LogEntry? = null
                synchronized(newCandidates){
                    try {
                        candidate = newCandidates.removeFirst()
                    }catch(_: NoSuchElementException){

                    }
                }
                candidate?.let { checkCandidate(it) }
                if(candidate == null){
                    sleep(500)
                }
            }
        }
    }

    private fun checkCandidate(candidate: LogEntry) {
        val oldLogSize = BurpExtender.ui.log().size
        var found = false

        try {
            if(BurpExtender.ui.settings.removeRegex.isNotEmpty()) {
                val re = Regex(BurpExtender.ui.settings.removeRegex)
                candidate.body = BurpExtender.h.stringToBytes(
                    re.replace(BurpExtender.h.bytesToString(candidate.body), "")
                )
            }
        }
        catch(e: PatternSyntaxException){
            BurpExtender.println("Invalid Regex ${BurpExtender.ui.settings.removeRegex}")
        }

        val log = BurpExtender.ui.log()

        //Performance optimization is more important than an accurate groupSize counter.
        //If there are too many log entries with the exact same response size already
        //we don't want to create a new log entry. But if we wouldn't anyway, the only point
        //in searching for duplicates is to have a more accurate groupSize counter.
        //But we rather take the performance optimization: Don't do the search (and the costly similarity comparison)
        //while maybe having a too low groupSize counter for an item.
        val logEntriesSameSizeSameStatus = log.filter{it.body.size == candidate.body.size &&
                it.statusCode == candidate.statusCode}.size
        if (logEntriesSameSizeSameStatus >= BurpExtender.ui.settings.maxGroupsWithSameResponseBodySize) {
            BurpExtender.println("We already have ${BurpExtender.ui.settings.maxGroupsWithSameResponseBodySize} " +
                    "log entries with status code ${candidate.statusCode} and response body size ${candidate.body.size}." +
                    "Therefore ignoring this response. Change the 'Maximum amount of log entries with same status code and response body size' " +
                    "setting if you would like to look at more.")
            return
        }

        val elapsed = measureTimeMillis {
            for (logEntry in log) {
                if (logEntry.statusCode == candidate.statusCode) {
                    //IMPORTANT: The candidate body has to be passed in first, the logEntry.body second!
                    val (isSimilar, charCount) = Similarity.isSimilar(
                        candidate.body, logEntry.body,
                        BurpExtender.ui.settings.similarity / 100, logEntry.charCountPrecalculated
                    )
                    if (charCount != null) {
                        logEntry.charCountPrecalculated = charCount
                    }
                    if (isSimilar) {
                        if(BurpExtender.ui.settings.debug){
                            println("${candidate.url} is similar to ${logEntry.url}")
                        }
                        logEntry.groupSize += 1
                        //We don't tell the UI to persist the new groupSize, because that would
                        //be irresponsible performance wise. This means you might lose the most current
                        //groupSize values when Burp crashes. It is persisted as soon as a new line is added
                        //to the logs or if you exit Burp cleanly.
                        found = true
                        break
                    }
                }
            }
            if (!found) {
                BurpExtender.ui.addNewLogEntry(candidate)
            }
        }
        if(elapsed > 500 || BurpExtender.ui.settings.debug){
            println(
                "Grouper.checkCandidate elapsed time: ${elapsed}ms, length of candidate: ${candidate.body.size}, " +
                        "number of responses to compare against: ${oldLogSize}. Resulted in new entry: ${!found}."
            )
        }
    }

}