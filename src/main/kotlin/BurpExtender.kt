package ch.pentagrid.burpexts.responseoverview

import burp.*
import burpwrappers.SerializableHttpRequestResponse
import java.io.PrintWriter


fun ByteArray.findFirst(sequence: ByteArray,startFrom: Int = 0): Int {
    if(sequence.isEmpty()) throw IllegalArgumentException("non-empty byte sequence is required")
    if(startFrom < 0 ) throw IllegalArgumentException("startFrom must be non-negative")
    var matchOffset = 0
    var start = startFrom
    var offset = startFrom
    while( offset < size ) {
        if( this[offset] == sequence[matchOffset]) {
            if( matchOffset++ == 0 ) start = offset
            if( matchOffset == sequence.size ) return start
        }
        else
            matchOffset = 0
        offset++
    }
    return -1
}

class BurpExtender : IBurpExtender, IExtensionStateListener, IHttpListener {

    companion object {
        // These act more or less like globals in the entire code
        lateinit var c: IBurpExtenderCallbacks
        lateinit var h: IExtensionHelpers
        lateinit var gr: Grouper
        lateinit var ui: OverviewUI
        lateinit var stdout: PrintWriter
        lateinit var stderr: PrintWriter

        // Constants
        val uninterestingMimeTypes = arrayOf("JPEG", "CSS", "script", "GIF", "PNG", "image")
        //val uninterestingStatusCodes = emptyArray<String>()
        val uninterestingUrlFileExtensions = arrayOf(
            // Mainly "static" things are excluded
            "js", ".map",
            "swf", "css", "zip", "war", "jar", "doc", "docx", "xls", "xlsx", "pdf", "exe", "dll",
            "png", "jpeg", "jpg", "bmp", "tif", "tiff", "gif", "webp", "svg",
            "m3u", "mp4", "m4a", "ogg", "aac", "flac", "mp3", "wav", "avi", "mov", "mpeg", "wmv", "webm",
            "woff", "woff2", "ttf", "otf"
            // Interesting are at least: json, xml, html, text, application/octet-stream
        )

        fun println(s: String){
            stdout.println(s)
        }

    }

    private val extensionName = "Response Overview"

    override fun registerExtenderCallbacks(callbacks: IBurpExtenderCallbacks) {
        c = callbacks
        h = c.helpers

        c.setExtensionName(extensionName)

        stdout = PrintWriter(c.stdout, true)
        stderr = PrintWriter(c.stderr, true)

        println("Loading $extensionName")

        gr = Grouper()
        gr.start()

        ui = OverviewUI()

        /*
        Find out if GrouperThread is already running.

        for(threadMap in Thread.getAllStackTraces()){
            val thread = threadMap.key
            println(thread.name)
            if(thread.name == cwThreadName){
                println("Found running $cwThreadName thread, reusing")
            }

        }
        */

        // Get notified when extension is unloaded
        c.registerExtensionStateListener(this)

        c.registerHttpListener(this)
        println("$extensionName loaded")
    }

    override fun extensionUnloaded(){
        println("Giving grouper thread the shut down signal...")
        gr.stop = true
        println("Saving the settings")
        ui.save()
        println("Ready for shutdown")
    }

    override fun processHttpMessage(toolFlag: Int, messageIsRequest: Boolean, messageInfo: IHttpRequestResponse) {
        if(!messageIsRequest){
            if(ui.log().size >= ui.settings.maxGroups){
                if(ui.settings.debug){
                    println("Too many groups (${ui.settings.maxGroups}) already")
                }
                return
            }
            val resp = messageInfo.response
            if(resp == null){
                println("Error in Burp API: messageInfo.response is null")
                return
            }
            if(resp.size >= ui.settings.responseMaxSize || resp.isEmpty()){
                if(ui.settings.debug){
                    println("Response too large or zero, ${resp.size} > ${ui.settings.responseMaxSize}")
                }
                return
            }
            val iRequestInfo = h.analyzeRequest(messageInfo)
            val url = iRequestInfo.url
            if(url == null){
                println("Error in Burp API: iRequestInfo.url is null")
                return
            }
            if(!c.isInScope(url)){
                if(ui.settings.debug){
                    println("Not in scope: $url")
                }
                return
            }

            val iResponseInfo = h.analyzeResponse(resp)
            val mimeType = iResponseInfo.statedMimeType
            if(mimeType in uninterestingMimeTypes){
                if(ui.settings.debug){
                    println("Mime type $mimeType is uninteresting, $url")
                }
                return
            }


            /*
            if(iResponseInfo.statusCode in uninterestingStatusCodes){
                if(debug){
                    println("Status code ${iResponseInfo.statusCode} is uninteresting, $url")
                }
                return
            }
            */

            val file = url.path
            if(file != null && '.' in file && file.split(".").last() in uninterestingUrlFileExtensions){
                if(ui.settings.debug){
                    println("Uninteresting file extension: $url")
                }
                return
            }

            val body = resp.drop(iResponseInfo.bodyOffset).toByteArray()
            val bodyString = h.bytesToString(body).trim()

            if(
                (bodyString.startsWith("<html><body>Please follow <a href=\"") && bodyString.endsWith("\">this link</a></body></html>")) ||
                (bodyString.startsWith("""<!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
<html><head>
<title>303 See Other</title>
</head><body>
<h1>See Other</h1>
<p>The answer to your request is located <a href="""") && bodyString.endsWith("""">here</a>.</p>
</body></html>""")) ||
                (bodyString.startsWith("""<!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
<html><head>
<title>301 Moved Permanently</title>
</head><body>
<h1>Moved Permanently</h1>
<p>The document has moved <a href="""") && bodyString.endsWith("""">here</a>.</p>
</body></html>"""))
                ){
                if(ui.settings.debug){
                    println("Standard Apache error/redirect/status code page found: $url")
                }
                return
            }

            /*if(ui.settings.debug){
                println("Candidate detected: $url")
            }*/
            //So this is indeed a response we want to group

            gr.addNewCandidate(LogEntry(
                SerializableHttpRequestResponse.fromHttpRequestResponse(messageInfo), toolFlag,
                iResponseInfo.statusCode, url, body, null, null, 1, false))
        }
    }
}