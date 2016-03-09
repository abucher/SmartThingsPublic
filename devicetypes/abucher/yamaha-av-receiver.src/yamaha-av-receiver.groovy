/**
 *  Yamaha AV Receiver
 *
 *  Copyright 2015 Aaron C. Bucher
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
metadata {
	definition (name: "Yamaha AV Receiver", namespace: "abucher", author: "Aaron C. Bucher") {
    	capability "polling"
        capability "refresh"
        
        attribute "powerControl", "string"
        attribute "volume", "number"
        attribute "volumeUnit", "string"
        attribute "inputSelection", "string"
        attribute "mute", "string"
        
        command "powerOn"
        command "powerStandby"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
        /*multiAttributeTile(name: "yamahaReceiver", type: "thermostat", width: 6, height: 4) {
  			tileAttribute("device.powerControl", key: "PRIMARY_CONTROL") {
            	attributeState("Standby", label: '${name}', action: "powerOn", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
                attributeState("On", label: '${name}', action: "powerStandby", icon: "st.switches.switch.on", backgroundColor: "#E60000")
  			}
        	tileAttribute("device.inputSelection", key: "SECONDARY_CONTROL") {
            	attributeState("default", label: '${currentValue}', textColor: "#000000")
            }
            tileAttribute("device.volume", key: "VALUE_CONTROL") {
            	attributeState("default", action: "setVolume")
            }
        }*/
        standardTile("yamahaReceiver", "device.powerControl", width: 2, height: 2, canChangeIcon: true) {
    		state("Standby", label: '${name}', action: "powerOn", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
    		state("On", label: '${name}', action: "powerStandby", icon: "st.switches.switch.on", backgroundColor: "#E60000")
        }
        valueTile("inputSelection", "device.inputSelection", width: 4, height: 2) {
        	state("default", label: '${currentValue}')
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
        	state("default", action:"refresh.refresh", icon:"st.secondary.refresh")
    	}
        valueTile("volume", "device.volume", width: 2, height: 2) {
        	state("default", label: '${currentValue} dB', textColor: "#000000", backgroundColor: "#ffffff")
        }
        standardTile("volumeUp", "device.volume", canChangeIcon: false, inactiveLabel: false) {
            state("default", label:'  ', action:"volumeUp", icon:"st.thermostat.thermostat-up")
        }
        standardTile("volumeDown", "device.volume", canChangeIcon: false, inactiveLabel: false) {
            state("default", label:'  ', action:"volumeDown", icon:"st.thermostat.thermostat-down")
        }
	
    	main("yamahaReceiver")
        details(["yamahaReceiver", "inputSelection", "volumeUp", "volumeDown", "volume", "refresh"])
    }
}

// parse events into attributes
def parse(String description) {
	def results = []
    try {
    	def msg = parseLanMessage(description)
        
        if (msg.xml) {
        	log.trace "Received XML message: ${msg.body}"
            
            if (msg.xml.Main_Zone?.Basic_Status?.text()) {
            	def powerControl = msg.xml.Main_Zone.Basic_Status.Power_Control.Power
    			def volume = msg.xml.Main_Zone.Basic_Status.Volume.Lvl.Val.toInteger()*10**-(msg.xml.Main_Zone.Basic_Status.Volume.Lvl.Exp.toInteger())
                def volumeUnit = msg.xml.Main_Zone.Basic_Status.Volume.Lvl.Unit
                def mute = msg.xml.Main_Zone.Basic_Status.Volume.Mute
   	 			def inputSelection = msg.xml.Main_Zone.Basic_Status.Input.Current_Input_Sel_Item.Title
                
                sendEvent(name: 'powerControl', value: powerControl, displayed: false)
                sendEvent(name: 'volume', value: volume, displayed: false)
                sendEvent(name: 'volumeUnit', value: volumeUnit, displayed: false)
                sendEvent(name: 'mute', value: mute, displayed: false)
                sendEvent(name: 'inputSelection', value: inputSelection, displayed: false)
            } else {
            	log.trace "Received unknown XML message."
            }
        } else {
        	log.debug "Device Handler: Received non-XML message: ${msg.body}"
        }
    } catch (Throwable t) {
    	log.error "Error parsing event: ${t}"
    }
    results
}

// utilities
def sendXml(String cmd, String xml, String zone = "Main_Zone") {
	def host = getHostAddress()
	def body = "<YAMAHA_AV cmd=\"${cmd}\"><${zone}>${xml}</${zone}></YAMAHA_AV>"
    
    log.debug "Sending \"${body}\" to ${host}"

    def result = new physicalgraph.device.HubAction(
     	"""POST /YamahaRemoteControl/ctrl HTTP/1.1\r\nHOST: ${host}\r\n\r\n${body}\r\n\r\n""",
        physicalgraph.device.Protocol.LAN,
        device.deviceNetworkId
    )
    result
}

/**
  * Power commands.
  */
private setPower(String setting) {
	sendEvent(name: 'powerControl', value: setting, displayed: true)
    sendXml("PUT", "<Power_Control><Power>${setting}</Power></Power_Control>")
}

def powerOn() {
	setPower("On")
}

def powerStandby() {
	setPower("Standby")
}

def poll() {
    sendXml("GET", "<Basic_Status>GetParam</Basic_Status>")
}

def refresh() {
	poll()
}

private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")
    
    if (!ip || !port) {
    	log.trace "device.deviceNetworkId: ${device.deviceNetworkId}"
        log.trace "device.label: ${device.label}"
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}