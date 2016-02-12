/**
 *  RX-A1000
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
preferences {
	input("ipAddress", "text", title: "IP Address", description: "Your Yamaha receiver IP address", required: true)
    input("port", "number", title: "Port", description: "Your Yamaha receiver port", defaultValue: 80, required: false)
	}
 
metadata {
	definition (name: "Yamaha AV Controller", namespace: "abucher", author: "Aaron C. Bucher") {
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
	log.debug "Parsing '${description}'"
	// TODO: handle 'switch' attribute

}

// utilities
def sendXML(String cmd, String xml, String zone = "Main_Zone") {
	def params  = [
    	uri: "http://${settings.ipAddress}:${settings.port}",
        path: "/YamahaRemoteControl/ctrl",
        body: "<YAMAHA_AV cmd=\"${cmd}\"><${zone}>${xml}</${zone}></YAMAHA_AV>"
        ]
        
    log.debug "Sending \"${params.body}\" to ${params.uri}${params.path}"

    try {
    	httpPost(params) { resp ->
        	log.debug "Response status: ${resp.status}"

            if (cmd == "GET") {
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
    	}
	} catch (e) {
    	log.error "sendXML error: ${e}"
	}   
}

/* def updateSettings(HttpResponseDecorator config) {
    //def rawXML = '<YAMAHA_AV rsp="GET" RC="0"><Main_Zone><Basic_Status><Power_Control><Power>Standby</Power><Sleep>Off</Sleep></Power_Control><Volume><Lvl><Val>-200</Val><Exp>1</Exp><Unit>dB</Unit></Lvl><Mute>Off</Mute></Volume><Input><Input_Sel>AV1</Input_Sel><Current_Input_Sel_Item><Param>AV1</Param><RW>RW</RW><Title>AndroidTV</Title><Icon><On>/YamahaRemoteControl/Icons/icon007.png</On><Off>/YamahaRemoteControl/Icons/icon006.png</Off></Icon><Src_Name></Src_Name><Src_Number>1</Src_Number></Current_Input_Sel_Item></Input><Surround><Program_Sel><Current><Straight>Off</Straight><Enhancer>On</Enhancer><Sound_Program>7ch Stereo</Sound_Program></Current></Program_Sel></Surround></Basic_Status></Main_Zone></YAMAHA_AV>'
    //log.debug "Poll: Raw XML: ${rawXML}"
    
    //def config = parseXml(rawXML)
    
}*/

// commands
def powerOn() {
	log.debug "Executing 'powerOn'"
    sendXML("PUT", "<Power_Control><Power>On</Power></Power_Control>")      
}

def powerStandby() {
	log.debug "Executing 'powerStandby'"
   	sendXML("PUT", "<Power_Control><Power>Standby</Power></Power_Control>")
}

def poll() {
	log.debug "Executing 'poll'"
    sendXML("GET", "<Basic_Status>GetParam</Basic_Status>")
}

def refresh() {
	poll()
}