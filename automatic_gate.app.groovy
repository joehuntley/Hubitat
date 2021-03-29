/*
 *  JDH Automatic Gate
 *
 * Parent 
 *
 *  Change Log
 *  v1.0.0 - 2020-07-13 - Initial Release
 *
 */


definition(
	name: "Automatic Gate",
    namespace: "jdh",
    parent: "jdh:Automatic Gate Manager",
    author: "Joe Huntley",
    description: "Control your swing arm or sliding gate door operator with contact sensors and relays",
	category: "Convenience",
    iconUrl: "",
    iconX2Url: ""

)


preferences {
	page(name:"mainPage")
}

def mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: false) {
		
		section("<strong>Automation/Gate Name</strong>") {
            label title: "Enter name of automation:", required: true
		}
        
        if ( !state.is_installed ) {
		    section("<strong>Create Virtual Gate Device Name</strong>") {
			    paragraph "The app will create virtual gate device. Enter the name of the device. If blank, the automation name will be used."
			    input "deviceName", "text", title:"Device Name", description: "Virtual Gate Device Name", required: false
		    }
        }
        
        
		section("<strong>Gate Output Device</strong>") {
			paragraph "The output relay controls the gate operator. It can either be a relay or a momentary switch"
            
            input "outRelay", "capability.switch", title:"Output Relay", description: "Relay that controls your garage door", required: true, submitOnChange: true
            input "relayAutoShutOffTime", "enum", title: "Auto shut off off relay after: ", required: false, defaultValue: 1, options: [0:"Disabled", 0.5: "1/2 Second", 1: "1 Second", 2:"2 Seconds", 3:"3 Seconds", 4:"4 Seconds", 5:"5 Seconds"]
        }
	
		section("<strong>Open Limit Contact Sensor</strong>") {
			paragraph "The Open Limit Sensor will be used to determine when the gate is fully opened"
			input "openSensor", "capability.contactSensor", title: "Select Open Sensor:", required: true
		}
        
		section("<strong>Close Limit Contact Sensor</strong>") {
			paragraph "The Close Limit Sensor will be used to determine when the gate is fully closed."
			input "closeSensor", "capability.contactSensor", title: "Select Close Sensor:", required: true
		}
		
		section("<strong>Gate Movement Duration</strong>") {
			paragraph "The Movement Duration should be set to a value greater than or equal to the amount of time it takes for the physical gate to open/close."
			paragraph "The gate device will stay in the opening or closing states during that duration and set to the contact sensor's state afterwards."
									
			input "movementDuration", "number", title: "Enter Gate Movement Duration (seconds):", required: true
		}
		
		section("<strong>Warning Strobe</strong>") {
            paragraph "Allows the use of a warning strobe light to indicate the gate is opening. The actual gate opening/closing will be delayed by 5 seconds while the warning strobe is activated"
                                         
            input "warningStrobeRelay", "capability.switch", title:"Strobe (Relay)", description: "Relay which controls warning strobe", required: false
            input "warningStrobeRelayCycle", "bool", title:"Cycle strobe ", description: "Toggle warning strobe relay", required: false
        }
        
        
		section("<strong>Options</strong>") {
            input "changeStateWaitForFeedback", "bool", title:"Wait for Feedback before changing state", description: "If the gate is opened or closed through hubitat, then always for a sensor to indicate movement before changing state to opening or closing. Set to false",  required: false, default: false
            paragraph "<small>If the gate is opened or closed through hubitat, then always for a sensor to indicate movement before changing state to opening or closing.</small>"
            
            input "showDebugLog", "bool", title:"Show debug messages in log", description: "",  required: false, default: false
        }
        
	}
}





def installed() {
	logTrace "installed()"
    
	initialize()
    
    state.is_installed = true
}

def updated() {		
	logTrace "updated()"

	unsubscribe()
	initialize()
}

void initialize() {
    
    if ( !childGateDevice ) {
	    try {
            def label = settings?.deviceName ?  settings?.deviceName : app.label
            
		    def dev = addChildDevice("JDH", "JDH Virtual Door Device", "${app.id}-gate-door", null, [name: label, label: label, completedSetup: true])
		    checkGateStatus()
	    }
	    catch (e) {
		    log.error "Error when adding child device: $e"
        }
    }

	
	
	subscribe(settings?.outRelay, "switch.on", outputRelayOnEventHandler)
	subscribe(settings?.openSensor, "contact", openContactEventHandler)		
	subscribe(settings?.closeSensor, "contact", closeContactEventHandler)			
}



def uninstalled() {
	try {
        childDevices?.each { deleteChildDevice(it.deviceNetworkId) }
    }
 	catch (e) {}
}


def getChildGateDevice() {
	return childDevices?.find { it.deviceNetworkId?.endsWith("-gate-door") }	
}


void childRefresh(dni) {
	logTrace "childRefresh"	
	checkGateStatus()
}


def childClose(dni) {
    def gateStatus = childGateDevice?.currentValue("door")
    logTrace "childClose(): gateStatus: $gateStatus"
    
    
    if ( gateStatus == "open" ) {
        if ( !settings?.changeStateWaitForFeedback ) {
	        updateGateStatus("closing")
        }
        actuateGate()
    }
}


def childOpen(dni) {
    def gateStatus = childGateDevice?.currentValue("door")
    logTrace "childOpen(): gateStatus: $gateStatus"
    
    if ( gateStatus == "closed" ) { 
        if ( !settings?.changeStateWaitForFeedback ) {
	        updateGateStatus("opening")
        }
        actuateGate()
    }
    
}



def checkGateStatus(data) {
	logTrace "checkGateStatus(): $data"
  
	
	def openContactStatus = settings?.openSensor?.currentValue("contact")
	def closeContactStatus = settings?.closeSensor?.currentValue("contact")
	def gateStatus = childGateDevice?.currentValue("door")
    
    
    def newGateStatus = "unknown"
    
    if ( closeContactStatus == "closed" && openContactStatus == "open"  ) {
        newGateStatus = "closed"
    }
    else if ( closeContactStatus == "open" && openContactStatus == "closed" ) {
        newGateStatus = "open"
    }
    else if ( closeContactStatus == "open" && openContactStatus == "open" ) { 
        if ( gateStatus == "open" || gateStatus == "closed" ) {
             // Door was manually actuated (it wasn't activated through the relay), trigger was momentary, or otherwise missed
             newGateStatus = (gateStatus == "open" ? "closing" : "opening")
            
             runIn(settings?.movementDuration?.toInteger(), verifyGateOperationComplete)
        }
        else if ( gateStatus == "opening" || gateStatus == "closing" ) {
            // Do nothing - expected state
            newGateStatus = gateStatus
        }
        else { newGateStatus = "unknown" }
    }
    else if ( closeContactStatus == "closed" && openContactStatus == "closed" ) {
        log.warn "Both open & closed limit contacts active simultaenously"
        // This shouldn't happen in normal operation
        newGateStatus = "unknown"
    }
    
    
	if (gateStatus != newGateStatus) {
        // Allow a re-try before setting gate to unknown to allow contacts to stabilize	
        if ( newGateStatus == "unknown" ) {
            def tryNo = data?.tryNo ? data?.tryNo : 0
            
            if ( tryNo < 1 ) {
                runIn(1, checkGateStatus, [data: [tryNo: tryNo+1]])
            }
            
            return gateStatus
        }
        
		updateGateStatus(newGateStatus)		
    }

    
    return newGateStatus
}


def updateGateStatus(status) {
    logTrace "updateGateStatus(): $status"

    def gateDevice = childGateDevice
    
    gateDevice?.sendEvent([name: "door", value: status, displayed: true])
    
    if ( status == "closed" || status == "open" ) {
        logDebug "updateGateStatus: setting contact to $status"
        gateDevice?.sendEvent([name: "contact", value: status, displayed: false])
    }
    
}



def verifyGateOperationComplete(data) {
    def gateStatus = checkGateStatus()
    
    logTrace "verifyGateOperationComplete($data): gateStatus=$gateStatus"
    
    // the contacts are showing still opening or closing
    if ( gateStatus == "opening" || gateStatus == "closing" ) {
        // TODO: Add exception if acceleration is still detected. Limit exceptions to 2X the normal delay
        
        def tryNo = data?.tryNo ? data?.tryNo : 0
        
        logDebug "verifyGateOperationComplete: tryNo=$tryNo"
        
        if ( tryNo <= 1 ) {
            // wait another 3 seconds and try to re-read the contacts again
            runIn(3, verifyGateOperationComplete, [data: [tryNo: tryNo+1]])
        } else {
            updateGateStatus("unknown")
        }
        
    }
}

       
        
def actuateGate() {
    logTrace "actuateGate()"
    
    
    if ( settings?.warningStrobeRelay ) {
        activateWarningStrobe()
    } else {
        //runIn(5, turnOutputRelayOn)
        turnOutputRelayOn()
    }
}

def activateWarningStrobe(data) {
    logTrace "activateWarningStrobe($data)"
    
    def counter = data?.counter ? data?.counter : 0
    logDebug "counter: $counter"
    
    
    if ( counter % 2 == 0 ) {
        warningStrobeRelay?.on()
    } else {
        warningStrobeRelay?.off()
    }
    
    
    if ( settings?.warningStrobeRelayCycle && counter < 5 ) {
        runIn(1, activateWarningStrobe, [data: [counter: counter + 1]]); // start cycling every second on/off
        return
    } else if ( !settings?.warningStrobeRelayCycle && counter == 0 ) {
        runIn(5, activateWarningStrobe, [data: [counter: counter + 1]]); // turn on and stay on for full duration
        return
    }
    

    turnOutputRelayOn()
    
}


def turnOutputRelayOn() {
    logTrace "turnOutputRelayOn()"
	settings?.outRelay?.on()
}

def turnOutputRelayOff() {
    logTrace "turnOutputRelayOff()"
	settings?.outRelay?.off()
}


def outputRelayOnEventHandler(evt) {
	logDebug "outputRelayOnEventHandler: ${evt.value}"
    
    
    def autoShutOff = 0
    if (settings.relayAutoShutOffTime) { autoShutOff = (settings.relayAutoShutOffTime).toFloat() }
    
    logDebug "Turn relay off in: $autoShutOff"
    
    if (autoShutOff) {
        // if relayAutoShutOffTime is less than one, then assume the value is in milliseconds
        if (autoShutOff < 1) {
            autoShutOff = (autoShutOff * 1000).toInteger()
            runInMillis(autoShutOff, turnOutputRelayOff)
        } else {
            runIn(autoShutOff.toInteger(), turnOutputRelayOff)
        }
    }
    
    def gateStatus = childGateDevice?.currentValue("door")
    
    if ( gateStatus == "open" ) {
        updateGateStatus("closing")
    } else if ( gateStatus == "closed" ) {
        updateGateStatus("opening")
    } 
    
    
	runIn(settings?.movementDuration?.toInteger(), verifyGateOperationComplete)
    
}

def openContactEventHandler(evt) {
	 logTrace "openContactEventHandler(): $evt.value"
     checkGateStatus()
}


def closeContactEventHandler(evt) {
	 logTrace "closeContactEventHandler(): $evt.value"
     checkGateStatus()
}


def logTrace(msg) {
    log.trace msg
}

def logDebug(msg) {
    if ( settings?.showDebugLog ) {
        log.debug msg
    }
}


