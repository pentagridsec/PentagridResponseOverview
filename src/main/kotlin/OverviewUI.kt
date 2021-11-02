package ch.pentagrid.burpexts.responseoverview

import burp.IHttpRequestResponse
import burp.IHttpService
import burp.IMessageEditorController
import burp.ITab
import java.awt.Component
import java.awt.EventQueue
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

class OverviewUI: ITab, IMessageEditorController {

    private val tabName = "Overview"
    private val resetText = "Reset settings"

    val log = ArrayList<LogEntry>()
    var currentlyDisplayedItem: IHttpRequestResponse? = null

    var settings = Settings()

    private val mainJtabedpane = JTabbedPane()
    val requestViewer = BurpExtender.c.createMessageEditor(this, false)
    val responseViewer = BurpExtender.c.createMessageEditor(this, false)

    private lateinit var maxGroupsFld: JTextField
    private lateinit var similarityFld: JTextField
    private lateinit var responseMaxSizeFld: JTextField
    private lateinit var debugBox: JCheckBox
    private lateinit var resetButton: JButton
    private val logTable = Table(this)

    private val about = """
        <html>
        Author: Tobias "floyd" Ospelt, @floyd_ch, http://www.floyd.ch<br>
        Pentagrid AG, https://www.pentagrid.ch
        <br>
        <h3>Find exotic responses by grouping response bodies (response overview)</h3>
        When the filter constraints are satisfied, the incoming response bodies (from all Burp tools!) is compared to all<br>
        the groups we already created. A group is defined by it's very first member, which we keep in memory.<br>
        If the response we are processing is ${settings.similarity}% similar to that first member, it belongs to that group<br>
        and only the "Group Size" counter is increased. That also means we won't store that response.<br>
        If the response is not ${settings.similarity}% similar to any group, it forms a new group and it is its first member.
        <h3>Trophy case</h3>
        So far:
        <ul>
        <li>I noticed a local file include vulnerability not picked up by the scanner.</li>
        <li>Found an SQL injection in a ClickHouse big data DBMS with a very exotic error message in the response.</li>
        <li>My attention was drawn to a lot of interesting functionality that I might have missed.</li>
        <li>Unfortunately nothing public so far, as I mainly do pentests with it. Did you find something? <br>
        Let me know: floyd at floyd dot ch</li>
        </ul>
        <h3>Howto use this extension</h3>
        Usage is very simple:
        <ul>
        <li>Add the website you test to the scope</li>
        <li>Test the web application (proxy, scanner, etc.) as you usually do.</li>
        <li>Check back on the $tabName tab and have a look at all the responses. Did you notice all that <br>
        functionality? Do you notice any strange error message? Any data in there that is new to you?</li>
        </ul>
        This extension analyses HTTP response bodies if:
        <ul>
        <li>They are in scope</li>
        <li>They are not uninteresting mime types (Burp mime types ${BurpExtender.uninterestingMimeTypes.joinToString(", ")})</li>
        <li>They do not have an uninteresting file extension 
        (${BurpExtender.uninterestingUrlFileExtensions.copyOfRange(0, 10).joinToString(", ")}, <br>
        ${BurpExtender.uninterestingUrlFileExtensions.copyOfRange(10, 29).joinToString(", ")}, <br>
        ${BurpExtender.uninterestingUrlFileExtensions.copyOfRange(29, 
            BurpExtender.uninterestingUrlFileExtensions.size).joinToString(", ")})</li>
        <li>They are smaller than ${settings.responseMaxSize} bytes</li>
        <li>We aren't already displaying ${settings.maxGroups} groups</li>
        </ul>
        <h3>History</h3>
        The first version of such a response overview that allows you to find anomalies I proposed in 2010<br>
        to the w3af project (see also a discussion here: https://github.com/andresriancho/w3af/issues/17345)<br>
        and was written in Python. It never made it into mainline w3af, I don't remember why. I learned a lot <br>
        about Python's difflib back then and optimized performance for the use case. I wrote a similar extension<br>
        in Python for Burp in 2019 and again learned a lot about Python's difflib when I was working for modzero AG<br>
        https://github.com/modzero/burp-ResponseClusterer. However, at one point I realized that the extension might <br>
        be eating some of Burp's performance, which I at first ignored. In 2021 the extension broke completely as it <br>
        wouldn't run with newer Jython versions anymore and while revisiting the code I realized that I coded a very <br>
        memory-inefficient extension. So here we are, this is a new extension in 2021 in Kotlin with different features, <br>
        again learning a lot from Python's difflib and different features. I did a performance improvement because I realized <br>
        certain calculations are not necessary if we always compare against the same strings (as already <br>
        implemented in the Python code).
        <h3>Performance discussion</h3>
        In theory, the default settings could result in not-that-great performance of Burp. However, this is highly <br>
        unlikely to occur. A test where all responses had maximum default response size (around 1MB) had to be <br>
        compared against each other, the comparison took up to 30ms with precalculated bByteCount optimization <br>
        (regular case) and up to 80ms without this optimization (happens only once for each entry). Multiplied <br>
        by the maximum amount of groups (1000) this means in the absolute worst case this could mean up to 80 <br>
        seconds of processing time. As there is a separate thread, this would be bearable, as the next comparison <br>
        would then only take a maximum time of 30 seconds. But which web applications has many different responses <br>
        of around 1MB? Let's hope not too many. Also don't forget that if the length of the responses differ more <br>
        than 2%, the similarity matching takes no time at all due to the veryQuickRatio optimization.
        <h3>Ideas for future improvements</h3>
        <ul>
        <li>We could hide the lines with the top 20% most seen groups (group size). Because that's probably just the <br>
        regular HTML code or JSON responses that we see normally and are very uninteresting. But then we would kind <br>
        of miss the "overview" property of the extension. So this is currently not done.</li>
        <li>Let me know if you think of any other improvements: tobias at pentagrid dot ch.</li>
        </ul>
        </html>
    """.trimIndent()

    companion object{
        fun println(s: String){
            BurpExtender.stdout.println(s)
        }
    }

    init{
        createUi()
    }

    private fun createUi(){
        loadSettings()

        logTable.rowSorter = TableRowSorter(logTable.model)

        maxGroupsFld = JTextField(settings.maxGroups.toString(), 10)
        similarityFld = JTextField(settings.similarity.toString(), 10)
        responseMaxSizeFld = JTextField(settings.responseMaxSize.toString(), 10)
        debugBox = JCheckBox("", settings.debug)
        resetButton = JButton(resetText)

        val aboutLbl = JTextPane()
        aboutLbl.contentType = "text/html"
        aboutLbl.isEditable = false
        aboutLbl.text = about
        //aboutLbl.layout = GridBagLayout()
        val aboutPanel = JScrollPane(aboutLbl)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)

        val tabs = JTabbedPane()
        tabs.addTab("Request", requestViewer.component)
        tabs.addTab("Response", responseViewer.component)

        val scrollPane = JScrollPane(logTable)

        splitPane.leftComponent = scrollPane
        splitPane.rightComponent = tabs

        val optionsJPanel = JPanel()
        val gridBagLayout = GridBagLayout()
        val gbc = GridBagConstraints()
        optionsJPanel.layout = gridBagLayout

        val maxGroupsLbl = JLabel("Maximum amount of groups: ")
        gbc.gridy=0
        gbc.gridx=0
        optionsJPanel.add(maxGroupsLbl, gbc)

        maxGroupsFld.document.addDocumentListener(
            DocumentHandler{
                try {
                    settings.maxGroups = maxGroupsFld.text.toInt()
                } catch (e: NumberFormatException) {
                    //println("NumberFormatException when reading maxGroupsFld")
                    //Happens when we press the reset button
                    settings.maxGroups = Settings().maxGroups
                }
                saveSettings()
            }
        )
        gbc.gridx=1
        optionsJPanel.add(maxGroupsFld, gbc)
        BurpExtender.c.customizeUiComponent(maxGroupsLbl)
        BurpExtender.c.customizeUiComponent(maxGroupsFld)

        val similarityLbl = JLabel("Similarity in % (between 0 and 100)")
        gbc.gridy=1
        gbc.gridx=0
        optionsJPanel.add(similarityLbl, gbc)
        similarityFld.document.addDocumentListener(
            DocumentHandler{
                try{
                    settings.similarity = similarityFld.text.toDouble()
                }catch(e: NumberFormatException){
                    //println("NumberFormatException when reading similarityFld")
                    //Happens when we press the reset button
                    settings.similarity = Settings().similarity
                }
                if(settings.similarity > 100.0 || settings.similarity < 0.0){
                    settings.similarity = Settings().similarity
                }
                saveSettings()
            }
        )
        gbc.gridx=1
        optionsJPanel.add(similarityFld, gbc)
        BurpExtender.c.customizeUiComponent(similarityLbl)
        BurpExtender.c.customizeUiComponent(similarityFld)

        val responseMaxSizeLbl = JLabel("Response max size (bytes)")
        gbc.gridy=3
        gbc.gridx=0
        optionsJPanel.add(responseMaxSizeLbl, gbc)
        responseMaxSizeFld.document.addDocumentListener(
            DocumentHandler {
                try {
                    settings.responseMaxSize = responseMaxSizeFld.text.toInt()
                } catch (e: NumberFormatException) {
                    //println("NumberFormatException when reading responseMaxSizeFld")
                    //Happens when we press the reset button
                    settings.responseMaxSize = Settings().responseMaxSize
                }
                if (settings.responseMaxSize < 0) {
                    settings.responseMaxSize = Settings().responseMaxSize
                }
                saveSettings()
            }
        )
        gbc.gridx=1
        optionsJPanel.add(responseMaxSizeFld, gbc)
        BurpExtender.c.customizeUiComponent(responseMaxSizeLbl)
        BurpExtender.c.customizeUiComponent(responseMaxSizeFld)

        val debugLbl = JLabel("Turn debug on (see extender output)")
        gbc.gridy=4
        gbc.gridx=0
        optionsJPanel.add(debugLbl, gbc)
        debugBox.addActionListener {
            settings.debug = debugBox.isSelected
            saveSettings()
        }
        gbc.gridx=1
        optionsJPanel.add(debugBox, gbc)
        BurpExtender.c.customizeUiComponent(debugLbl)
        BurpExtender.c.customizeUiComponent(debugBox)

        gbc.gridy=5
        resetButton.addActionListener {
            settings = Settings()
            saveSettings()
            EventQueue.invokeLater {
                maxGroupsFld.text = settings.maxGroups.toString()
                similarityFld.text = settings.similarity.toString()
                responseMaxSizeFld.text = settings.responseMaxSize.toString()
                debugBox.isSelected = settings.debug
            }
        }
        gbc.gridx=1
        optionsJPanel.add(resetButton, gbc)
        BurpExtender.c.customizeUiComponent(resetButton)

        BurpExtender.c.customizeUiComponent(splitPane)
        BurpExtender.c.customizeUiComponent(logTable)
        BurpExtender.c.customizeUiComponent(scrollPane)
        BurpExtender.c.customizeUiComponent(tabs)

        mainJtabedpane.addTab("Groups", null, splitPane, null)
        mainJtabedpane.addTab("Options", null, optionsJPanel, null)
        mainJtabedpane.addTab("About & README", null, aboutPanel, null)

        loadLogEntries()

        // Important: Do this at the very end (otherwise we could run into troubles locking up entire threads)
        // add the custom tab to Burp's UI
        BurpExtender.c.addSuiteTab(this)
    }

    fun addNewLogEntry(candidate: LogEntry, persist: Boolean = true) {
        //val persisted = BurpExtender.c.saveBuffersToTempFiles(candidate.messageInfo)
        //candidate.messageInfo = persisted
        val row = log.size
        log.add(candidate)
        val model = logTable.model as TableModel
        model.fire(row)
        if(persist){
            saveLogEntries()
        }
    }

    private fun loadSettings(){
        val s = PersistOverview.loadSettings()
        if(s != null){
            settings = s
            println("Old settings found from extension settings: $settings")
        }
    }

    private fun loadLogEntries() {
        //Load the already stored log entries from project settings
        val entries = PersistOverview.loadLogEntries()
        /*
        try {
            entries = PersistOverview.loadLogEntries()
        } catch (e: Exception) {
            println("Exception when trying to load the project settings log entries")
            println(e)
        }

         */
        for (entry in entries) {
            try {
                addNewLogEntry(entry, false)
            } catch (e: Exception) {
                println("Exception when deserializing a stored log entry $entry")
                println(e)
            }
        }
        saveLogEntries()
    }

    fun save(){
        saveSettings()
        saveLogEntries()
    }

    private fun saveSettings() {
        //println("Saving settings $settings")
        PersistOverview.saveSettings(settings)
    }

    private fun saveLogEntries() {
        if(log.isNotEmpty())
            PersistOverview.saveLogEntries(log)
    }

    override val tabCaption: String
        get() = tabName
    override val uiComponent: Component
        get() = mainJtabedpane
    override val httpService: IHttpService?
        get() = currentlyDisplayedItem?.httpService
    override val request: ByteArray?
        get() = currentlyDisplayedItem?.request
    override val response: ByteArray?
        get() = currentlyDisplayedItem?.response

    private inner class DocumentHandler(val function: (e: DocumentEvent?) -> Unit): DocumentListener{

        override fun insertUpdate(e: DocumentEvent?) {
            function(e)
        }

        override fun removeUpdate(e: DocumentEvent?) {
            function(e)
        }

        override fun changedUpdate(e: DocumentEvent?) {
            function(e)
        }

    }

}


class TableModel(private val userInterface: OverviewUI): AbstractTableModel() {

    private val toolColumn = "Tool"
    private val responseCodeColumn = "Response Code"
    private val groupSizeColumn = "Group size"
    private val urlColumn = "URL"
    private val idColumn = "ID"
    private val columns = arrayOf(idColumn, urlColumn, toolColumn, responseCodeColumn, groupSizeColumn)

    fun fire(row: Int){
        fireTableRowsInserted(row, row)
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val logEntry = userInterface.log[rowIndex]
        return when (columnIndex) {
            0 -> rowIndex
            1 -> logEntry.url.toString()
            2 -> BurpExtender.c.getToolName(logEntry.toolFlag)
            3 -> logEntry.statusCode.toString()
            4 -> logEntry.groupSize.toString()
            else -> ""
        }
    }

    override fun getRowCount(): Int {
        return try {
            userInterface.log.size
        } catch(e: Exception){
            0
        }
    }

    override fun getColumnCount(): Int {
        return columns.size
    }

    override fun getColumnName(columnIndex: Int): String {
        return columns[columnIndex]
    }

}

class Table(private val userInterface: OverviewUI): JTable() {
    init{
        model = TableModel(userInterface)
    }

    override fun changeSelection(row: Int, col: Int, toggle: Boolean, extend: Boolean){
        val logEntry = userInterface.log[convertRowIndexToModel(row)]
        userInterface.requestViewer.setMessage(logEntry.messageInfo.request, true)
        userInterface.responseViewer.setMessage(logEntry.messageInfo.response!!, false)
        userInterface.currentlyDisplayedItem = logEntry.messageInfo
        super.changeSelection(row, col, toggle, extend)
    }
}
