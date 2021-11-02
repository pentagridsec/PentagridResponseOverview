package burp

import ch.pentagrid.burpexts.responseoverview.BurpExtender as BurpExtenderRealPackage

class BurpExtender : IBurpExtender {

    override fun registerExtenderCallbacks(callbacks: IBurpExtenderCallbacks) {
        BurpExtenderRealPackage().registerExtenderCallbacks(callbacks)
    }

}