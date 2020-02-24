/**
 *  Sleepy Room
 *
 *  Copyright 2019 Joel Wetzel
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

import groovy.time.*
	
definition(
    name: "Sleepy Room",
	parent: "joelwetzel:Sleepy House",
    namespace: "joelwetzel",
    author: "Joel Wetzel",
    description: "Child app of Sleepy House",
    category: "Lighting",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


preferences {
	page(name: "mainPage")
}


def mainPage() {
	dynamicPage (name: "mainPage", title: "", install: true, uninstall: true) {
        if (!app.label) {
			app.updateLabel("New Room")
		}
		section (getFormat("title", (app?.label ?: app?.name).toString())) {
			input(name:	"roomName", type: "string", title: "Room Name", multiple: false, required: true, submitOnChange: false)
            
			if (settings.roomName) {
				app.updateLabel(settings.roomName)
			}
		}
        section ("Room devices:", hidden: false, hideable: true) {
            input (name:	"switches",	type: "capability.switch", title: "Switches", description: "Select the switches in the room.", multiple: true, required: false, submitOnChange: false)
            input (name:	"dimmers",	type: "capability.switchLevel", title: "Dimmers", description: "Select the dimmers in the room.", multiple: true, required: false, submitOnChange: false)
            input (name:	"motionSensors",	type: "capability.motionSensor", title: "Motion Sensors", description: "Select the motion sensors in the room.", multiple: true, required: false, submitOnChange: false)
        }
        section ("Room going to sleep:", hidden: false, hideable: true) {
            input (name:	"dimmedLevel", type: "number", title: "Dimmed Level", defaultValue: 1, required: true)
            paragraph "<b>Note:</b> The 'Dimmed Level' is used for several purposes:<ul><li>When falling asleep, dimmers will fade to this dimmed level about 30 seconds before the room completely falls asleep.<li>This is the level that 'off' dimmers will be preset to, so that if you come into a dark room at night and push a physical switch, the light will not come on with the brightness of a thousand suns and blind you.<li>This is the level that the lights will come on to if you wake the room with motion.</li></li></li></ul>"
            input (name:    "motionActivityKeepsAwake", type: "bool", title: "Should motion activity keep the room from falling asleep?", required: true, defaultValue: true)
            input (name:    "switchActivityKeepsAwake", type: "bool", title: "Should switch/dimmer activity keep the room from falling asleep?", required: true, defaultValue: true)
            input (name:    "activityWaitMinutes", type: "number", title: "Minutes without activity before room starts to fall asleep:", required: true, defaultValue: 3)
            input (name:    "sleepMode", type: "enum", required: true, multiple: false, title: "When the room goes to sleep, dimmers should end up:", options: ["Completely off", "Just dimmed"], defaultValue: "Completely off")
        }
        section ("Room waking up:", hidden: false, hideable: true) {        
            input (name:    "wakeUpForMotion", type: "bool", title: "Should motion activity wake the room from sleep?", required: true, defaultValue: true, submitOnChange: true)
            if (settings.wakeUpForMotion || settings.wakeUpForMotion == null) {
                input (name:    "wakeUpDimmers", type: "bool", title: "Should the dimmers turn on when waking up the room? (They will come on at the Dimmed Level.)", required: true, defaultValue: true)
                input (name:    "wakeUpSwitches", type: "bool", title: "Should the switches turn on when waking up the room? (They will come on full brightness, because they are just switches.)", required: true, defaultValue: false)
            }
        }
        section ("Define 'Nighttime':", hidden: false, hideable: true) {          
            input (name:    "fromTime", type: "time", title: "Start of night", required: true)
            input (name:    "toTime", type: "time", title: "End of night", required: true)
            
            paragraph "Is currently night: ${isCurrentlyNight()}"
        }
        section ("Miscellaneous:", hidden: true, hideable: true) {
            input(name:	"enableLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: false,	required: true)
            // input(name: "btnRunTests", type: "button", title: "Run Tests", submitOnChange: true)
        }
    }
}


def appButtonHandler(btn) {
    switch (btn) {
        case "btnRunTests":
            log.debug "Running Tests..."
            runTests()
            break
    }
}


def installed() {
	log.info "Installed with settings: ${settings}"

	initialize()
}


def updated() {
	log.info "Updated with settings: ${settings}"

    if (settings.roomName) {
		app.updateLabel(settings.roomName)
    }
    
	initialize()
}


def initialize() {
	unschedule()
	unsubscribe()

    // NOTE: The logic would probably be clearer if I just put these settings checks into
    // the event handlers.  However, I want my apps to be light impact.  So I only subscribe
    // to events if necessary.  However, I also put the same checks in the event handlers
    // for better readability of the code.
    
    if (settings.wakeUpForMotion || settings.motionActivityKeepsAwake) {
	    subscribe(motionSensors, "motion.active", motionActiveHandler)
    }
    
    if (settings.switchActivityKeepsAwake) {
        subscribe(switches, "switch", switchActivityHandler)
        subscribe(dimmers, "switch", switchActivityHandler)
        subscribe(dimmers, "level", switchActivityHandler)
    }
    
    state.lastActivityTime = new Date()
	
    runEvery1Minute(tickTock)
}


def switchActivityHandler(evt) {
    log "Activity detected on '${evt.displayName}', type: '${evt.type}'"
    
    if (evt.type == "physical" && settings.switchActivityKeepsAwake) {
        state.lastActivityTime = new Date()
    }
}


def motionActiveHandler(evt) {
    log "Motion detected by '${evt.displayName}'"

    if (settings.motionActivityKeepsAwake) {
        state.lastActivityTime = new Date()
    }
    
    if (!isCurrentlyNight()) {
        return
    }

    if (settings.wakeUpForMotion) {
        wakeRoom()
    }
}


def tickTock() {
    //log.debug "tickTock"
    
    if (!isCurrentlyNight()) {
        return
    }

    log "Sleepy Time Room '${settings.roomName}' evaluating..."

    if (roomIsActive()) {
        log "The room has activity. Allowing the room to stay awake."
        return
    }
    
    def needToTurnOffIn30Seconds = false
    
    settings.dimmers.each { dimmer ->
        //log "Evaluating dimmer: ${dimmer.displayName}"
        if (dimmer.currentValue("switch") == "on") {
            if (dimmer.currentValue("level") > settings.dimmedLevel) {
                log "${dimmer.displayName} is on and level is ${dimmer.currentValue("level")}, which is above Dimmed Level (${settings.dimmedLevel}). Dimming..."
                dimmer.setLevel(settings.dimmedLevel, 10)
            }
            
            // Turn them off in 30 seconds if the motion sensors are still off.
            needToTurnOffIn30Seconds = true
        }
        else {
            if (dimmer.currentValue("level") > settings.dimmedLevel) {
                log "${dimmer.displayName} is off but level is ${dimmer.currentValue("level")}, which is above Dimmed Level (${settings.dimmedLevel}). Pre-dimming and keeping off..."
                dimmer.setLevel(settings.dimmedLevel)
                dimmer.off()
            }
        }
    }
    
    settings.switches.each { s ->
        //log "Evaluating switch: ${s.displayName}"
        if (s.currentValue("switch") == "on") {
            log "${s.displayName} is on."
            // Turn them off in 30 seconds if the motion sensors are still off.
            needToTurnOffIn30Seconds = true
        }
    }
    
    if (needToTurnOffIn30Seconds) {
        runIn(30, trySleepRoom)
    }
}


// Try to make the room go to sleep, as long as there's no ongoing activity.
def trySleepRoom() {
    log.debug "trySleepRoom()"
    
    if (!isCurrentlyNight()) {
        return
    }
    
    if (roomIsActive()) {
        log "The room has activity. Allowing the room to stay awake."
        return
    }

    log "Turning the room off..."
    
    if (settings.sleepMode == "Completely off") {
        settings.dimmers.each { dimmer ->
            dimmer.off()
        }
    }
    
    settings.switches.each { s ->
        s.off()
    }
}


// Wake the room up
def wakeRoom() {
    log.debug "wakeRoom()"
    
    if (settings.wakeUpDimmers) {
        settings.dimmers.each { dimmer ->
            if (dimmer.currentValue("switch") == "off") {
                log "Turning on dimmer '${dimmer.displayName}' to dimmed level."
                dimmer.setLevel(settings.dimmedLevel, 1)            
            }
        }
    }
    
    if (settings.wakeUpSwitches) {
        settings.switches.each { s ->
            if (s.currentValue("switch") == "off") {
                log "Turning on switch '${s.displayName}'."
                s.on()         
            }
        }
    }
}


def runTests() {
    logTest(isNight("2020-02-24T22:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 17:00:00 CST 2020")), false, "Nightspan - Before")
    logTest(isNight("2020-02-24T22:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 23:00:00 CST 2020")), true, "Nightspan - During Night")
    logTest(isNight("2020-02-24T22:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 01:00:00 CST 2020")), true, "Nightspan - During Morning")
    logTest(isNight("2020-02-24T22:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 07:00:00 CST 2020")), false, "Nightspan - After")
    
    logTest(isNight("2020-02-24T02:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 01:00:00 CST 2020")), false, "Morning Only - Before")
    logTest(isNight("2020-02-24T02:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 03:00:00 CST 2020")), true, "Morning Only - During Morning")
    logTest(isNight("2020-02-24T02:00:00.000-0600", "2020-02-24T05:30:00.000-0600", new Date("Mon Feb 24 07:00:00 CST 2020")), false, "Morning Only - After")
}


def logTest(result, desiredResult, msg) {
    if (result != desiredResult) {
        log.error "TEST FAILED: ${msg}"
    }
    else {
        log.info "Test passed: ${msg}"
    }
}


def isCurrentlyNight() {
    def now = new Date()
    return isNight(settings.fromTime, settings.toTime, now)
}

def isNight(fromTimeSetting, toTimeSetting, now) {
    use (groovy.time.TimeCategory) {
        def fromTime = timeToday(fromTimeSetting)
        def toTime = timeToday(toTimeSetting)
        
//        log.debug "from: ${fromTime}"
//        log.debug "to: ${toTime}"
//        log.debug "now: ${now}"

        if (fromTime < toTime) {
            // The timespan does NOT cross a midnight boundary.  (Usually meaning this is only active during early morning.)
            return now > fromTime && now < toTime
        }
        else {
            // The timespan crosses the midnight boundary
            def isNight = now > fromTime
            def isMorning = now < toTime

            return isNight || isMorning
        }
    }
}


def roomIsActive() {
    use (groovy.time.TimeCategory) {
        if (settings.motionActivityKeepsAwake) {
            // Return true if any motion sensor is active
            def aMotionSensorIsActive = false
            settings.motionSensors.each { motionSensor ->
                if (motionSensor.currentValue("motion") == "active") {
                    aMotionSensorIsActive = true
                }
            }
            if (aMotionSensorIsActive) {
                return true
            }
        }
    
        // Return true if there has been dimmer/switch/motion activity in the last {activityTime}
        def now = new Date()
        def minutesSinceLastActivity = (now - toDateTime(state.lastActivityTime)).minutes
    
        //log.debug "Minutes since last activity: ${minutesSinceLastActivity}"
        
        if (minutesSinceLastActivity < activityWaitMinutes) {
            return true
        }

        // ELSE room is not active anymore
        return false
    }
}


def getFormat(type, myText="") {
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def log(msg) {
	if (enableLogging) {
		log.debug msg
	}
}


