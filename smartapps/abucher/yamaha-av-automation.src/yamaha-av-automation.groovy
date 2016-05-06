/**
 *  Yamaha AV Automation
 *
 *  Copyright 2016 Aaron Bucher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Yamaha AV Automation",
    namespace: "abucher",
    author: "Aaron Bucher",
    description: "Automate Yamaha AV controls.",
    category: "My Apps",
    parent: "abucher:Yamaha AV Controller",
    iconUrl: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw",
    iconX2Url: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw",
    iconX3Url: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw"
)


preferences {
    page(name: "addRoutine")
    page(name: "modifyRoutine")
    page(name: "setActions")
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	subscribe(location, "routineExecuted", routineChanged)
}

/**
 * Routine configuration.
 */
def addRoutine() {
	dynamicPage(name: "addRoutine", title: "Configure Actions", install: false, uninstall: true, nextPage: "setActions") {
    	section {
        	def actions = location.helloHome?.getPhrases()*.label
        	if (actions) {
            	actions.sort()
                input(name: "routine", type: "enum", title: "Select a routine", options: actions, required: true, multiple: false)
                input(name: "yamahaDevice", type: "device.yamahaAVReceiver", title: "Select a Yamaha device", required: true, multiple: false)
            } else {
            	paragraph "Add routines before configuring AV automations!"
            }
        }
    }
}

def setActions() {
	log.debug "[setActions] yamahaDevice: ${yamahaDevice}"
	yamahaDevice.getScenes()
    //yamahaDevice.supportedAttributes.each { log.trace "${it.name} and ${it.values}" }
    
    log.debug "[setActions] Attribute scenes: ${yamahaDevice.currentValue("scenes")}"
    
    dynamicPage(name: "setActions", title: "Set Device Actions") {
    	section {
        	input(name: "power", type: "enum", title: "Select Power State", options: ["On", "Off", "Standby"], required: true, multiple: false)
            input(name: "scene", type: "enum", title: "Select a Scene", options: yamahaDevice.currentValue("scenes").split(', '), required: false, multiple: false)
        }
    }
}

def modifyRoutine() {
}

/**
 * Routine events
 */
def routineChanged(evt) {
	log.debug "[routineChanged] Routine changed ${evt.displayName}"
    
    if (evt.displayName == routine) {
    	log.trace "[routineChanged] Setting yamaha device power: ${power}"
    	//yamahaDevice.setPower(power)
        log.trace "[routineChanged] Setting yamaha device scene: ${scene}"
        //yamahaDevice.setScene(scene)
    }
}