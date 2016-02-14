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
	page(name:"yamahaDiscovery", title:"Yamaha Device Setup", content:"deviceDiscovery", refreshTimeout:5)
}

def yamahaUpnp = "urn:schemas-upnp-org:device:MediaRenderer:1"

/**
 * Device discovery.
 */
def deviceDiscovery () {
	if(canInstallLabs()) {
		int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
		state.refreshCount += 1
		def refreshInterval = 3

		def options = yamahasDiscovered() ?: []

		def numFound = options.size() ?: 0

		if(!state.subscribe) {
			log.trace "Yamaha: Subscribe to location..."
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

private discoverYamahas() {
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery $yamahaUpnp", physicalgraph.device.Protocol.LAN))
}

/**
 * Verify Yamaha devices.
 */
private verifyYamahaDevice() {
	def devices = getYamahaDevice().findAll { it?.value?.verified != true }

	if(devices) {
		log.warn "UNVERIFIED PLAYERS!: $devices"
	}

	devices.each {
		verifyYamahas((it?.value?.ip + ":" + it?.value?.port))
	}
}

private verifyYamahas(String deviceNetworkId) {

	log.trace "dni: $deviceNetworkId"
	String ip = getHostAddress(deviceNetworkId)

	log.trace "ip:" + ip

	//sendHubCommand(new physicalgraph.device.HubAction("""GET /xml/device_description.xml HTTP/1.1\r\nHOST: $ip\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

/**
 * Device utilities.
 */
Map yamahasDiscovered() {
	def verifiedYamahas = getVerifiedYamahas()
	def map = [:]
	verifiedYamahas.each {
		def value = "${it.value.name}"
		def key = it.value.ip + ":" + it.value.port
		map["${key}"] = value
	}
	map
}

def getYamahaDevice()
{
	state.yamahaDevices = state.yamahaDevices ?: [:]
}

def getVerifiedYamahaDevice()
{
	getYamahaDevice().findAll{ it?.value?.verified == true }
}


def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	def devices = getChildDevices()
	log.trace "deleting ${devices.size()} Yamaha"
	devices.each {
		deleteChildDevice(it.deviceNetworkId)
	}
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}


def initialize() {
	unsubscribe()
	state.subscribe = false

	unschedule()
	scheduleActions()

	if (selectedYamaha) {
		addYamaha()
	}

	scheduledActionsHandler()
}

/**
 * Event handlers.
 */
def scheduledActionsHandler() {
	log.trace "scheduledActionsHandler()"
	syncDevices()
	refreshAll()
}

private scheduleActions() {
	def sec = Math.round(Math.floor(Math.random() * 60))
	def min = Math.round(Math.floor(Math.random() * 20))
	def cron = "$sec $min/20 * * * ?"
	log.trace "schedule('$cron', scheduledActionsHandler)"
	schedule(cron, scheduledActionsHandler)
}

private syncDevices() {
	log.trace "Doing Yamaha device sync!"

	if(!state.subscribe) {
		subscribe(location, null, locationHandler, [filterEvents:false])
		state.subscribe = true
	}

	discoverYamahas()
}

private refreshAll(){
	log.trace "Refreshing Yamaha devices..."
	childDevices*.refresh()
	log.trace "Refresh complete."
}

def addYamaha() {
	def devices = getVerifiedYamahaDevice()
	def runSubscribe = false
	selectedYamaha.each { dni ->
		def d = getChildDevice(dni)
		if(!d) {
			def newDevice = devices.find { (it.value.ip + ":" + it.value.port) == dni }
			log.trace "New Yamaha device: $newDevice; ID: $dni"
			d = addChildDevice("smartthings", "Yamaha Receiver", dni, newDevice?.value.hub, [label:"${newDevice?.value.name} Yamaha"])
			log.trace "Created ${d.displayName} Yamaha device with ID: $dni"

			d.setModel(newDevice?.value.model)
			log.trace "Set Yamaha device ${d.displayName} model to ${newPlayer?.value.model}"

			runSubscribe = true
		} else {
			log.trace "Found Yamaha device ${d.displayName} with ID $dni already exists."
		}
	}
}

def locationHandler(evt) {
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseEventMessage(description)
	parsedEvent << ["hub":hub]

	// SSDP discovery
	if (parsedEvent?.ssdpTerm?.contains($yamahaUpnp)) {
		log.trace "SSDP: Yamaha device found."
        
        def yamahas = getYamahaDevice()

		if (!(yamahas."${parsedEvent.ssdpUSN.toString()}")) {
            log.trace "SSDP: NEW Yamaha found."
            
			yamahas << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		}
        else {
			log.trace "SSDP: Existing Yamaha found."

			def d = yamahas."${parsedEvent.ssdpUSN.toString()}"

			if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
				log.trace "SSDP: Updated device's IP and port."
                
                d.ip = parsedEvent.ip
				d.port = parsedEvent.port
			
				def children = getChildDevices()
				children.each {
					if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
						log.trace "SSDP: Updated device's DNI for device ${it} with mac ${parsedEvent.mac}."
						it.setDeviceNetworkId((parsedEvent.ip + ":" + parsedEvent.port))
					}
				}
			}
		}
	}
	else if (parsedEvent.headers && parsedEvent.body) {
    	log.trace "SSDP: Received Yamaha device response."
        
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())
		def type = (headerString =~ /Content-Type:.*/) ? (headerString =~ /Content-Type:.*/)[0] : null
		def body
		log.trace "SSDP: Yamaha response type: $type"
		
        if (type?.contains("xml")) {
			body = new XmlSlurper().parseText(bodyString)

			if (body?.system?.config?.modelName?.text()) {
				def yamahas = getYamahaDevice()
				def device = yamahas.find {it?.key?.contains(body?.system?.config?.systemId?.text())}
				if (device) {
                	log.trace "SSDP: Updating Yamaha device: Name: ${body?.system?.config?.modelName?.text()}; System ID: ${body?.system?.config?.systemId?.text()}"
					device.value << [name:body?.system?.config?.modelName?.text(),systemId:body?.system?.config?.systemId?.text(), verified: true]
				}
				else {
					log.error "SSDP: XML response returned a device that does not exist."
				}
			}
		}
		else if(type?.contains("json"))
		{ //(application/json)
			body = new groovy.json.JsonSlurper().parseText(bodyString)
			log.trace "GOT JSON $body"
		}

	}
	else {
		log.trace "cp desc: " + description
		//log.trace description
	}
}