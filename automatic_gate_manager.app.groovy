/*
 *  Automatic Gate Manager
 *
 *  Parent App
 *
 *  Change Log
 *  v1.0.0 - 2020-07-13 - Initial Release
 *
 */
definition(
	name: "Automatic Gate Manager",
    namespace: "jdh",
    author: "Joe Huntley",
    description: "Control your automatic gates, garage doors, or any other actuated door using independent contact sensors and relays",
	singleInstance: true,	
	category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
	installOnOpen: true,
	importUrl: ""
)


preferences {
	page(name:"mainPage")
}

def mainPage() {	
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: false) {
		section() {
		}
		section() {
			app(name: "automaticGates", title: "New Automatic Gate Opener", appName: "Automatic Gate", namespace: "jdh", multiple: true, uninstall: false)
		}
		
	}	
}




def installed() {
	log.trace "installed()"
}

def updated() {		
	log.trace "updated()"
}
