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
		standardTile("yamahaReceiver", "device.powerControl", width: 2, height: 2, canChangeIcon: true) {
    		state("off", label: '${name}', action: "powerOn", icon: "st.switches.switch.off", backgroundColor: "#ffffff")
    		state("on", label: '${name}', action: "powerStandby", icon: "st.switches.switch.on", backgroundColor: "#E60000")
        }
        valueTile("volume", "device.volume", width: 2, height: 2) {
        	state("volume", label: '${currentValue}', textColor: "#000000", backgroundColor: "#ffffff")
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
        	state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
    	}
        /*multiAttributeTile(name: "yamahaReceiver", type: "generic", width: 6, height: 4) {
  			tileAttribute("device.powerControl", key: "PRIMARY_CONTROL") {
            	attributeState "off", label: '${name}', icon: "st.switches.switch.off", backgroundColor: "#ffffff"
                attributeState "on", label: '${name}', icon: "st.switches.switch.on", backgroundColor: "#E60000"
    			//attributeState("on", label:'${device.volume}', unit:"")
                //attributeState("off", label:'off', unit:"")
  			}
        }*/
	
    	main("yamahaReceiver")
        details(["yamahaReceiver", "volume", "refresh"])
        //details("yamahaReceiver")
    }
}

// parse events into attributes
def parse(String description) {
	log.debug "Device Handler: Parsing message..."
	def results = []
    try {
    	def msg = parseLanMessage(description)
        
        if (msg.body) {
        	log.trace "Device Handler: Received XML message: ${msg.body}"
        	xml = parseXml(msg.body)
            
            if (xml.Main_Zone?.Basic_Status?.text()) {
            	def powerControl = resp.data.Main_Zone.Basic_Status.Power_Control.Power
    			def volume = resp.data.Main_Zone.Basic_Status.Volume.Lvl.Val.toInteger()*10**-(resp.data.Main_Zone.Basic_Status.Volume.Lvl.Exp.toInteger())
                def volumeUnit = resp.data.Main_Zone.Basic_Status.Volume.Lvl.Unit
                def mute = resp.data.Main_Zone.Basic_Status.Volume.Mute
   	 			def inputSelection = resp.data.Main_Zone.Basic_Status.Input.Current_Input_Sel_Item.Title
    
    			log.debug "Stored: powerControl: ${powerControl}"
    			log.debug "Stored: volume: ${volume}"
    			log.debug "Stored: volumeUnit: ${volumeUnit}"
                log.debug "Stored: mute: ${mute}"
    			log.debug "Stored: inputSelection: ${inputSelection}"
            }
        } else {
        	log.debug "Device Handler: Received non-XML message: ${msg.body}"
        }
    } catch (Throwable t) {
    	log.error "Error parsing event: ${t}"
    }
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

// commands
def powerOn() {
	log.debug "Executing 'powerOn'"
    sendXml("PUT", "<Power_Control><Power>On</Power></Power_Control>")      
}

def powerStandby() {
	log.debug "Executing 'powerStandby'"
   	sendXml("PUT", "<Power_Control><Power>Standby</Power></Power_Control>")
}

def poll() {
	log.debug "Executing 'poll'"
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
    //return convertHexToIP(ip) + ":80"
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}