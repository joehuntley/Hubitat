/*
 *  Virtual Door Device
 *
 *  Virtual child device for Gate Opener
 *
 *  Change Log
 *  v1.0 - 2020-07-13 - Initial Release
 *
 */

metadata {
	definition (name: "JDH Virtual Door Device", namespace: "jdh", author: "Joe Huntley") {
		capability "Actuator"
		capability "Sensor"
		capability "Contact Sensor"
		capability "Door Control"
        capability "Garage Door Control"
		capability "Refresh"
		
        //capability "Switch"
        //capability "Relay Switch"
        capability "Momentary"
	}

	// preferences {}
}


def installed() {
	log.trace "installed()"
	initialize()
}

def updated() {
	log.trace "updated()"
	initialize()
}

def initialize() {
	// Default state?
}



def refresh() {
	log.trace "refresh()..."
	parent?.childRefresh(device.deviceNetworkId)
}



def open() {
	log.trace "open()"
	parent?.childOpen(device.deviceNetworkId)
}


def close() {
	log.trace "close()"
	parent?.childClose(device.deviceNetworkId)
}

def push() {
	log.trace "push()"
	parent?.childPush(device.deviceNetworkId)

}
