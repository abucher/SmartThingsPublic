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

/**
 * Device discovery.
 */
def deviceDiscovery () {
	if(canInstallLabs()) {
		int refreshCount = !state.refreshCount ? 0 : state.refreshCount as int
		state.refreshCount = refreshCount + 1
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
	sendHubCommand(new physicalgraph.device.HubAction(
    	"lan discovery urn:schemas-upnp-org:device:MediaRenderer:1", physicalgraph.device.Protocol.LAN))
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
	String ip = getHostAddress(deviceNetworkId)

	log.trace "Verifying Yamaha: DNI: ${deviceNetworkId}, IP: ${ip}"

    sendHubCommand(new physicalgraph.device.HubAction(
    	"""GET /MediaRenderer/desc.xml HTTP/1.1\r\nHOST: $ip\r\n\r\n""",
        physicalgraph.device.Protocol.LAN,
        "${deviceNetworkId}"
    ))
}

/**
 * Device utilities.
 */
Map yamahasDiscovered() {
	def verifiedYamahas = getVerifiedYamahaDevice()
	def map = [:]
	verifiedYamahas.each {
        map["${it.value.ip}:${it.value.port}"] = "${it.value.name}"
	}
	map
}

def getYamahaDevice() {
	state.yamahaDevices = state.yamahaDevices ?: [:]
}

def getVerifiedYamahaDevice() {
	getYamahaDevice().findAll{ it?.value?.verified == true }
}


def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def uninstalled() {
	def devices = getChildDevices()
	
    log.debug "Uninstalling ${devices.size()} Yamaha(s)"
	
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
	log.trace "Scheduling cron..."
	schedule("${Math.round(Math.floor(Math.random() * 60))} ${Math.round(Math.floor(Math.random() * 20))}/20 * * * ?", scheduledActionsHandler)
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
	log.debug "Selected Yamahas: ${selectedYamaha}"
    //def map = [selectedYamaha]
	selectedYamaha.each { dni ->
    	log.trace "Adding device: ${dni}"
		def d = getChildDevice(dni)
		if(!d) {
			def newDevice = devices.find { (it.value.ip + ":" + it.value.port) == dni }
			log.trace "New Yamaha device: $newDevice; ID: $dni"
			d = addChildDevice("abucher", "Yamaha ${newDevice?.value.description}", "${newDevice?.value.ip}:0050", newDevice?.value.hub,
            	[label:"${newDevice?.value.name}", description:"${newDevice?.value.description}",
                 model:"${newDevice?.value.model}", systemId:"${newDevice?.value.systemId}", address:"${newDevice?.value.ip}:${newDevice?.value.port}"])
			log.debug "Created ${d.displayName} Yamaha device with ID: $dni"

			d.setModel(newDevice?.value.model)
			log.trace "Set Yamaha device ${d.displayName} model to ${newDevice?.value.model}"

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
	if (parsedEvent?.ssdpPath?.contains("/MediaRenderer/desc.xml")) {
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
        log.trace "SSDP: Yamaha response headers: ${headerString}"
        log.trace "SSDP: Yamaha response body: ${bodyString}"
		log.trace "SSDP: Yamaha response type: ${type}"
		
        if (type?.contains("xml")) {
			body = new XmlSlurper().parseText(bodyString)
            
			if (body?.device?.manufacturer?.text().contains("YAMAHA CORPORATION")) {
				def yamahas = getYamahaDevice()
				def device = yamahas.find {it?.key?.contains(body?.device?.UDN?.text())}
				if (device) {
                	log.trace "SSDP: Updating Yamaha device: Name: ${body?.device?.friendlyName?.text()}, Description: ${body?.device?.modelDescription?.text()}, Model: ${body?.device?.modelName?.text()}; System ID: ${body?.device?.serialNumber?.text()}"
					device.value << [name:body?.device?.friendlyName?.text(),
                    				 description:body?.device?.modelDescription?.text(),
                                     model:body?.device?.modelName?.text(),
                                     systemId:body?.device?.serialNumber?.text(),
                                     verified: true]
				}
				else {
					log.error "SSDP: XML response returned a device that does not exist."
				}
            } else {
            	log.trace "XML: Unknown: ${body}"
            }
        } else {
        		log.trace "SSDP: Unknown respose type: ${type}"
        }
	} else {
		log.trace "SSDP: Unknown event description: " + description
	}
}

private def parseEventMessage(Map event) {
	return event
}

private def parseEventMessage(String description) {
	def event = [:]
	def parts = description.split(',')
    log.trace "SmartApp: Parsing event message..."
    
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			event.devicetype = valueString
            //log.trace "Event message: devicetype: ${event.devicetype}"
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.mac = valueString
			}
            //log.trace "Event message: mac: ${event.mac}"
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ip = valueString
			}
            //log.trace "Event message: ip: ${event.ip}"
		}
		else if (part.startsWith('deviceAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.port = valueString
			}
            //log.trace "Event message: port: ${event.port}"
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ssdpPath = valueString
			}
            //log.trace "Event message: ssdpPath: ${event.ssdpPath}"
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpUSN = valueString
			}
            //log.trace "Event message: ssdpUSN: ${event.ssdpUSN}"
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpTerm = valueString
			}
            //log.trace "Event message: ssdpTerm: ${event.ssdpTerm}"
		}
		else if (part.startsWith('headers')) {
			part -= "headers:"
			def valueString = part.trim()
			if (valueString) {
				event.headers = valueString
			}
            //log.trace "Event message: headers: ${event.headers}"
		}
		else if (part.startsWith('body')) {
			part -= "body:"
			def valueString = part.trim()
			if (valueString) {
				event.body = valueString
			}
            //log.trace "Event message: body: ${event.body}"
		}
	}

	event
}

/**
 * Child device methods.
 */
def parse(childDevice, description) {
	log.trace "Parsing child event..."
	def parsedEvent = parseEventMessage(description)

	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())
		log.trace "Parse: ${bodyString}"

		def body = new groovy.json.JsonSlurper().parseText(bodyString)
	} else {
		log.error "Parse: Error parsing headers and body."
		return []
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress(d) {
	def parts = d.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}
 