package ch.pentagrid.burpexts.responseoverview

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
                    }catch(e: NoSuchElementException){

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
        val oldLogSize = BurpExtender.ui.log.size
        var found = false

        /*
        TODO

        const val FOUR_OH_THREE = 403.toShort()
        val ACCESS_DENIED = "AccessDenied".toByteArray()
        val CODE_TAG = "<Code>".toByteArray()
        val REQUEST_ID = "<RequestId>".toByteArray()
        val HOST_ID = "<HostId>".toByteArray()

        //Now a couple of special cases
        if(iResponseInfo.statusCode == FOUR_OH_THREE &&
            body.findFirst(ACCESS_DENIED) != -1 &&
            body.findFirst(CODE_TAG) != -1 &&
            body.findFirst(REQUEST_ID) != -1 &&
            body.findFirst(HOST_ID) != -1
        ){
            // Just an AmazonS3 AccessDenied
            if(BurpExtender.ui.settings.debug){
                BurpExtender.println("Ignoring AmazonS3 AccessDenied: $url")
            }
            return
        }
        */

        val elapsed = measureTimeMillis {
            for (logEntry in BurpExtender.ui.log) {
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