package ch.pentagrid.burpexts.responseoverview

import burp.IHttpRequestResponse
import burp.IHttpService
import burp.IMessageEditorController
import burp.ITab
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.border.Border
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException


class OverviewUI: ITab, IMessageEditorController {

    private val tabName = "Overview"
    private val resetText = "Reset settings"
    private val unhideText = "Unhide all log entries"

    var currentlyDisplayedItem: IHttpRequestResponse? = null

    var settings = Settings()

    private val mainJtabedpane = JTabbedPane()
    val requestViewer = BurpExtender.c.createMessageEditor(this, false)
    val responseViewer = BurpExtender.c.createMessageEditor(this, false)

    private lateinit var maxGroupsFld: JTextField
    private lateinit var similarityFld: JTextField
    private lateinit var responseMaxSizeFld: JTextField
    private lateinit var removeRegexFld: JTextField
    private lateinit var maxGroupsWithSameResponseBodySizeFld: JTextField
    private lateinit var removeParameterBox: JCheckBox
    private lateinit var debugBox: JCheckBox
    private lateinit var resetButton: JButton
    private lateinit var unhideButton: JButton
    private lateinit var searchField: JTextField
    private lateinit var regexBox: JCheckBox
    private lateinit var caseInsensitiveBox: JCheckBox
    private lateinit var clearSearchButton: JButton
    private lateinit var searchTimer: Timer
    private lateinit var logSorter: TableRowSorter<TableModel>
    private var lastValidSearchFilter: RowFilter<TableModel, Int>? = null
    private var searchFieldDefaultBorder: Border? = null
    private val logTable = Table(this)

    fun log(): ArrayList<LogEntry> {
        return (logTable.model as TableModel).log
    }

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
        <li>They are not standard error pages of common web servers such as Apache</li>
        <li>They are smaller than ${settings.responseMaxSize} bytes</li>
        <li>We aren't already displaying ${settings.maxGroups} groups</li>
        <li>We aren't already displaying ${settings.maxGroupsWithSameResponseBodySize} groups with the same status code and response body size</li>
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

        logSorter = TableRowSorter(logTable.model as TableModel)
        // TableRowSorter drives sorting + filtering (hidden rows + search).
        logTable.rowSorter = logSorter
        updateRowFilter()

        val popupMenu = JPopupMenu()
        val hideItem = JMenuItem("Hide item(s)")
        hideItem.addActionListener {
            for(i in logTable.selectedRows) {
                // RowSorter active -> convert view row to model row.
                (logTable.model as TableModel).log[logTable.convertRowIndexToModel(i)].hidden = true
            }
            saveLogEntries()
            EventQueue.invokeLater {
                // it is important that we do this out of loop, because otherwise the row index changes and
                // convertRowIndexToModel will return the changed index, which breaks the entire logic
                (logTable.rowSorter as TableRowSorter<*>).sort()
            }
        }
        popupMenu.add(hideItem)
        logTable.componentPopupMenu = popupMenu

        maxGroupsFld = JTextField(settings.maxGroups.toString(), 10)
        similarityFld = JTextField(settings.similarity.toString(), 10)
        responseMaxSizeFld = JTextField(settings.responseMaxSize.toString(), 10)
        maxGroupsWithSameResponseBodySizeFld = JTextField(settings.maxGroupsWithSameResponseBodySize.toString(), 10)
        removeRegexFld = JTextField(settings.removeRegex, 10)
        debugBox = JCheckBox("", settings.debug)
        removeParameterBox = JCheckBox("", settings.removeParameter)
        resetButton = JButton(resetText)
        unhideButton = JButton(unhideText)

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
        val searchPanel = createSearchPanel()
        val groupsPanel = JPanel(BorderLayout())
        groupsPanel.add(searchPanel, BorderLayout.NORTH)
        groupsPanel.add(scrollPane, BorderLayout.CENTER)

        splitPane.leftComponent = groupsPanel
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

        val removeRegexLbl = JLabel("Remove regex")
        gbc.gridy=4
        gbc.gridx=0
        optionsJPanel.add(removeRegexLbl, gbc)
        removeRegexFld.document.addDocumentListener(
            DocumentHandler {
                settings.removeRegex = removeRegexFld.text
                saveSettings()
            }
        )
        gbc.gridx=1
        optionsJPanel.add(removeRegexFld, gbc)
        BurpExtender.c.customizeUiComponent(removeRegexLbl)
        BurpExtender.c.customizeUiComponent(removeRegexFld)

        val maxGroupsWithSameResponseBodySizeLbl = JLabel("Maximum amount of log entries with same status code and response body size")
        gbc.gridy=5
        gbc.gridx=0
        optionsJPanel.add(maxGroupsWithSameResponseBodySizeLbl, gbc)
        maxGroupsWithSameResponseBodySizeFld.document.addDocumentListener(
            DocumentHandler {
                try {
                    settings.maxGroupsWithSameResponseBodySize = maxGroupsWithSameResponseBodySizeFld.text.toInt()
                } catch (e: NumberFormatException) {
                    //println("NumberFormatException when reading maxGroupsWithSameResponseBodySize")
                    //Happens when we press the reset button
                    settings.maxGroupsWithSameResponseBodySize = Settings().maxGroupsWithSameResponseBodySize
                }
                if (settings.maxGroupsWithSameResponseBodySize < 0) {
                    settings.maxGroupsWithSameResponseBodySize = Settings().maxGroupsWithSameResponseBodySize
                }
                saveSettings()
            }
        )
        gbc.gridx=1
        optionsJPanel.add(maxGroupsWithSameResponseBodySizeFld, gbc)
        BurpExtender.c.customizeUiComponent(maxGroupsWithSameResponseBodySizeLbl)
        BurpExtender.c.customizeUiComponent(maxGroupsWithSameResponseBodySizeFld)

        val removeParameterLbl = JLabel("Remove Parameters (stores response body twice!)")
        gbc.gridy=6
        gbc.gridx=0
        optionsJPanel.add(removeParameterLbl, gbc)
        removeParameterBox.addActionListener {
            settings.removeParameter = removeParameterBox.isSelected
            saveSettings()
        }
        gbc.gridx=1
        optionsJPanel.add(removeParameterBox, gbc)
        BurpExtender.c.customizeUiComponent(removeParameterLbl)
        BurpExtender.c.customizeUiComponent(removeParameterBox)

        val debugLbl = JLabel("Turn debug on (see extender output)")
        gbc.gridy=7
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

        gbc.gridy=8
        resetButton.addActionListener {
            settings = Settings()
            saveSettings()
            EventQueue.invokeLater {
                maxGroupsFld.text = settings.maxGroups.toString()
                similarityFld.text = settings.similarity.toString()
                responseMaxSizeFld.text = settings.responseMaxSize.toString()
                removeParameterBox.isSelected = settings.removeParameter
                debugBox.isSelected = settings.debug
            }
        }
        gbc.gridx=1
        optionsJPanel.add(resetButton, gbc)
        BurpExtender.c.customizeUiComponent(resetButton)

        gbc.gridy=9
        unhideButton.addActionListener {
            EventQueue.invokeLater {
                (logTable.model as TableModel).resetNoShow()
                (logTable.rowSorter as TableRowSorter<*>).sort()
            }
        }
        gbc.gridx=1
        optionsJPanel.add(unhideButton, gbc)
        BurpExtender.c.customizeUiComponent(unhideButton)

        BurpExtender.c.customizeUiComponent(splitPane)
        BurpExtender.c.customizeUiComponent(logTable)
        BurpExtender.c.customizeUiComponent(scrollPane)
        BurpExtender.c.customizeUiComponent(searchPanel)
        BurpExtender.c.customizeUiComponent(groupsPanel)
        BurpExtender.c.customizeUiComponent(tabs)

        mainJtabedpane.addTab("Groups", null, splitPane, null)
        mainJtabedpane.addTab("Options", null, optionsJPanel, null)
        mainJtabedpane.addTab("About & README", null, aboutPanel, null)

        loadLogEntries()

        // Important: Do this at the very end (otherwise we could run into troubles locking up entire threads)
        // add the custom tab to Burp's UI
        BurpExtender.c.addSuiteTab(this)
    }

    private fun createSearchPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        val searchLbl = JLabel("Search:")
        searchField = JTextField(20)
        regexBox = JCheckBox("Regex")
        caseInsensitiveBox = JCheckBox("Case-insensitive", true)
        clearSearchButton = JButton("Clear")
        searchFieldDefaultBorder = searchField.border

        searchTimer = Timer(200) {
            applyGroupFilter()
        }
        searchTimer.isRepeats = false

        searchField.document.addDocumentListener(
            DocumentHandler {
                restartSearchTimer()
            }
        )
        regexBox.addActionListener {
            restartSearchTimer()
        }
        caseInsensitiveBox.addActionListener {
            restartSearchTimer()
        }
        clearSearchButton.addActionListener {
            searchField.text = ""
            regexBox.isSelected = false
            caseInsensitiveBox.isSelected = true
            restartSearchTimer()
        }

        panel.add(searchLbl)
        panel.add(searchField)
        panel.add(regexBox)
        panel.add(caseInsensitiveBox)
        panel.add(clearSearchButton)

        BurpExtender.c.customizeUiComponent(searchLbl)
        BurpExtender.c.customizeUiComponent(searchField)
        BurpExtender.c.customizeUiComponent(regexBox)
        BurpExtender.c.customizeUiComponent(caseInsensitiveBox)
        BurpExtender.c.customizeUiComponent(clearSearchButton)

        return panel
    }

    private fun restartSearchTimer() {
        searchTimer.restart()
    }

    private fun applyGroupFilter() {
        val query = searchField.text.trim()
        if (query.isEmpty()) {
            lastValidSearchFilter = null
            setSearchFieldError(false)
            updateRowFilter()
            return
        }

        val isRegex = regexBox.isSelected
        val isCaseInsensitive = caseInsensitiveBox.isSelected
        val pattern = try {
            val flags = if (isCaseInsensitive) Pattern.CASE_INSENSITIVE else 0
            val expression = if (isRegex) query else Pattern.quote(query)
            Pattern.compile(expression, flags)
        } catch (e: PatternSyntaxException) {
            // Invalid regex: keep the previous filter and mark the field for troubleshooting.
            setSearchFieldError(true)
            return
        }

        setSearchFieldError(false)
        lastValidSearchFilter = object : RowFilter<TableModel, Int>() {
            override fun include(entry: Entry<out TableModel, out Int>): Boolean {
                // Search matches against any column value + full raw request/response (headers + body).
                for (i in 0 until entry.valueCount) {
                    val value = entry.getValue(i) ?: continue
                    if (pattern.matcher(value.toString()).find()) {
                        return true
                    }
                }
                val logEntry = entry.model.log[entry.identifier]
                val responseBytes = logEntry.messageInfo.response
                if (responseBytes != null &&
                    pattern.matcher(BurpExtender.h.bytesToString(responseBytes)).find()
                ) {
                    return true
                }
                // Fallback when response bytes are unavailable.
                if (responseBytes == null &&
                    pattern.matcher(BurpExtender.h.bytesToString(logEntry.body)).find()
                ) {
                    return true
                }
                val requestBytes = logEntry.messageInfo.request
                if (pattern.matcher(BurpExtender.h.bytesToString(requestBytes)).find()) {
                    return true
                }
                return false
            }
        }
        updateRowFilter()
    }

    private fun setSearchFieldError(isError: Boolean) {
        if (isError) {
            searchField.border = BorderFactory.createLineBorder(Color.RED)
            searchField.toolTipText = "Invalid regex"
        } else {
            searchField.border = searchFieldDefaultBorder
            searchField.toolTipText = null
        }
    }

    private fun updateRowFilter() {
        // Apply hidden-row filter + current search filter to the sorter.
        val filters = ArrayList<RowFilter<TableModel, Int>>(2)
        filters.add(IdRowFilter())
        lastValidSearchFilter?.let { filters.add(it) }
        logSorter.rowFilter = if (filters.size == 1) filters[0] else RowFilter.andFilter(filters)
    }

    fun addNewLogEntry(candidate: LogEntry, persist: Boolean = true) {
        //val persisted = BurpExtender.c.saveBuffersToTempFiles(candidate.messageInfo)
        //candidate.messageInfo = persisted
        (logTable.model as TableModel).add(candidate)
        //println("candidate.hidden " + candidate.hidden)
        (logTable.rowSorter as TableRowSorter<*>).sort()
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
        if(entries.isNotEmpty()){
            println("Old table entries found from project settings: ${entries.size} entries")
        }
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
        if((logTable.model as TableModel).log.isNotEmpty())
            PersistOverview.saveLogEntries((logTable.model as TableModel).log)
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

    private inner class DocumentHandler(val function: (e: DocumentEvent?) -> Unit): DocumentListener {

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

class TableModel : AbstractTableModel() {

    private val toolColumn = "Tool"
    private val responseCodeColumn = "Response Code"
    private val responseBodyLengthColumn = "Response body length"
    private val groupSizeColumn = "Group size"
    private val urlColumn = "URL"
    private val idColumn = "ID"
    private val columns = arrayOf(idColumn, urlColumn, toolColumn, responseCodeColumn, responseBodyLengthColumn, groupSizeColumn)
    val log = ArrayList<LogEntry>()

    fun resetNoShow(){
        for(i in log){
            i.hidden = false
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val logEntry = log[rowIndex]
        return when (columnIndex) {
            0 -> rowIndex
            1 -> logEntry.url.toString()
            2 -> BurpExtender.c.getToolName(logEntry.toolFlag)
            3 -> logEntry.statusCode
            4 -> logEntry.body.size
            5 -> logEntry.groupSize
            else -> throw RuntimeException()
        }
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
        return when (columnIndex) {
            0 -> Integer::class.java
            1 -> String::class.java
            2 -> String::class.java
            3 -> Short::class.java
            4 -> Integer::class.java
            5 -> Integer::class.java
            else -> throw RuntimeException()
        }
    }

    override fun getRowCount(): Int {
        return try {
            log.size
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

    fun add(candidate: LogEntry) {
        val row = log.size
        log.add(candidate)
        fireTableRowsInserted(row, row)
    }

}

class Table(private val userInterface: OverviewUI): JTable() {
    init{
        model = TableModel()
    }

    override fun changeSelection(row: Int, col: Int, toggle: Boolean, extend: Boolean){
        // RowSorter active -> convert view row to model row.
        val logEntry = (model as TableModel).log[convertRowIndexToModel(row)]
        userInterface.requestViewer.setMessage(logEntry.messageInfo.request, true)
        userInterface.responseViewer.setMessage(logEntry.messageInfo.response!!, false)
        userInterface.currentlyDisplayedItem = logEntry.messageInfo
        super.changeSelection(row, col, toggle, extend)
    }
}

class IdRowFilter: RowFilter<TableModel, Int>(){

    override fun include(entry: Entry<out TableModel, out Int>): Boolean {
        return !entry.model.log[entry.identifier].hidden
    }

}
