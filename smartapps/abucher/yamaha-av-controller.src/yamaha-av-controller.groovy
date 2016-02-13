/**
 *  Yamaha AV Controller
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
    name: "Yamaha AV Controller",
    namespace: "abucher",
    author: "Aaron Bucher",
    description: "Interface with Yahama receivers and blu-ray players.",
    category: "My Apps",
    iconUrl: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw",
    iconX2Url: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw",
    iconX3Url: "https://lh5.ggpht.com/EmAboPYuJpxYeXt7dPUPjqZoNvkhr4r-RW2PKVCePZz-_Qqu6lCSPuocKgNaIgmZuMw=w300-rw")


preferences {
	page(name:"yamahaDiscovery", title:"Yamaha Device Setup", content:"yamahaDiscovery", refreshTimeout:5)
}

/**
 * Device discovery
 */
def yamahaDiscovery () {
	if(canInstallLabs()) {
		int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
		state.refreshCount += 1
		def refreshInterval = 3

		def options = yamahasDiscovered() ?: []

		def numFound = options.size() ?: 0

		if(!state.subscribe) {
			log.trace "subscribe to location"
			subscribe(location, null, locationHandler, [filterEvents:false])
			state.subscribe = true
		}

		// Yamaha discovery request every 5 //25 seconds
		if((refreshCount % 8) == 0) {
			discoverYamahas()
		}

		// GetBasicStatus request every 3 seconds except on discoveries
		if(((refreshCount % 1) == 0) && ((refreshCount % 8) != 0)) {
			verifyYamahaDevice()
		}

		return dynamicPage(name:"yamahaDiscovery", title:"Yamaha Discovery Started!", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
			section("Please wait while we discover your Yamaha device... discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
				input "selectedYamaha", "enum", required:false, title:"Select Yamaha (${numFound} found)", multiple:true, options:options
			}
		}
	}
	else {
		return dynamicPage(name:"yamahaDiscovery", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section("Upgrade") {
				paragraph """To use SmartThings Labs, your Hub should be completely up to date.

To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""
			}
		}
	}
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
	// TODO: subscribe to attributes, devices, locations, etc.
}

// TODO: implement event handlers