/**
 *  Sleepy Room
 *
 *  Copyright 2024 Joel Wetzel
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
	dynamicPage (name: "mainPage", title: "Sleepy Room", install: true, uninstall: true) {
        if (!app.label) {
			app.updateLabel("New Room")
		}
		section (getFormat("title", (app?.label ?: app?.name).toString())) {
			input(name:	"roomName", type: "text", title: "Room Name", multiple: false, required: true, submitOnChange: false)

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
            input (name:	"dimmedLevel", type: "number", title: "Dimmed Level", defaultValue: 5, required: true)
            paragraph "<b>Note:</b> The 'Dimmed Level' is used for several purposes:<ul><li>When falling asleep, dimmers will fade to this dimmed level about 30 seconds before the room completely falls asleep.<li>This is the level that 'off' dimmers will be preset to, so that if you come into a dark room at night and push a physical switch, the light will not come on with the brightness of a thousand suns and blind you.<li>This is also the level that the lights will come on to if you wake the room with motion.</li></li></li></ul>"

            input (name:    "motionActivityKeepsAwake", type: "bool", title: "Should motion activity delay the room from falling asleep?", required: true, defaultValue: true)
            input (name:    "switchActivityKeepsAwake", type: "bool", title: "Should switch on/off activity delay the room from falling asleep?", required: true, defaultValue: true)
            input (name:    "dimmerActivityKeepsAwake", type: "bool", title: "Should dimmer brightness activity delay the room from falling asleep?", required: true, defaultValue: true)

            input (name:    "activityWaitMinutes", type: "number", title: "Minutes without activity before room starts to fall asleep:", required: true, defaultValue: 30)
            input (name:    "sleepMode", type: "enum", required: true, multiple: false, title: "When the room goes to sleep, dimmers should end up:", options: ["Completely off", "Just dimmed"], defaultValue: "Completely off")       // TODO - write tests for this.
        }
        section ("Room waking up:", hidden: false, hideable: true) {
            input (name:    "wakeUpForMotion", type: "bool", title: "Should motion activity wake the room from sleep?", required: true, defaultValue: true, submitOnChange: true)
            input (name:    "wakeUpForSwitchActivity", type: "bool", title: "Should switch on/off activity wake the room from sleep?", required: true, defaultValue: true, submitOnChange: true)
            input (name:    "wakeUpForDimmerActivity", type: "bool", title: "Should dimmer brightness activity wake the room from sleep?", required: true, defaultValue: true, submitOnChange: true)
            if (settings.wakeUpForMotion || settings.wakeUpForSwitchActivity || settings.wakeUpForDimmerActivity) {
                input (name:    "wakeUpDimmers", type: "bool", title: "Should the dimmers turn on when waking up the room? (They will come on at the Dimmed Level.)", required: true, defaultValue: true)
                // TODO - make the wakeup level be an option?
                input (name:    "wakeUpSwitches", type: "bool", title: "Should the switches turn on when waking up the room? (They will come on full brightness, because they are just switches.)", required: true, defaultValue: false)
            }
        }
        section ("Define 'Nighttime':", hidden: false, hideable: true) {
            input (name:    "fromTime", type: "time", title: "Start of night", required: true, submitOnChange: true)
            input (name:    "toTime", type: "time", title: "End of night", required: true, submitOnChange: true)

            paragraph "Is currently night: ${isCurrentlyNight()}"
        }
        section ("Miscellaneous:", hidden: true, hideable: true) {
            input(name:	"enableLogging", type: "bool", title: "Enable Debug Logging?", defaultValue: false,	required: true)
        }
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
    log.info "initialize()"

	unschedule()
	unsubscribe()

    // NOTE: The event handlers also check these settings, but I double-check them
    // here to keep the app lightweight, and not register for a handler that will
    // ALWAYS exit early.

    if (settings.wakeUpForMotion || settings.motionActivityKeepsAwake) {
        if (motionSensors) {
	        subscribe(motionSensors, "motion.active", motionActiveHandler)
            subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
        }
    }

    if (settings.wakeUpForSwitchActivity || settings.switchActivityKeepsAwake) {
        if (switches) {
            subscribe(switches, "switch.on", switchActivityHandler)
        }
        if (dimmers) {
            subscribe(dimmers, "switch.on", switchActivityHandler)
        }
    }

    if (settings.wakeUpForDimmerActivity || settings.dimmerActivityKeepsAwake) {
        if (dimmers) {
            subscribe(dimmers, "level", levelActivityHandler)
        }
    }

    if (!state.lastActivityTime) {
        use (groovy.time.TimeCategory) {
            state.lastActivityTime = formatDate(dateNow()-24.hours)
        }
    }

    runEvery1Minute(tickTock)
}


def switchActivityHandler(evt) {
    log "Switch activity detected on '${evt.displayName}'"

    if (settings.switchActivityKeepsAwake) {
        updateLastActivityTime()
    }

    if (settings.wakeUpForSwitchActivity && isCurrentlyNight()) {
        if (!roomIsAwake(evt.device.getIdAsLong())) {
            wakeRoom()
        }
    }
}


def levelActivityHandler(evt) {
    if ((evt.value as Integer) <= settings.dimmedLevel) {     // Dimming down to the dimmed level or below doesn't count as activity
        return
    }

    log "Level activity detected on '${evt.displayName}'"

    if (settings.dimmerActivityKeepsAwake) {
        updateLastActivityTime()
    }

    if (settings.wakeUpForDimmerActivity && isCurrentlyNight()) {
        if (!roomIsAwake(evt.device.getIdAsLong())) {
            wakeRoom()
        }
    }
}


def motionActiveHandler(evt) {
    log "Motion detected on '${evt.displayName}'"

    if (settings.motionActivityKeepsAwake) {
        updateLastActivityTime()
    }

    if (settings.wakeUpForMotion && isCurrentlyNight()) {
        if (!roomIsAwake()) {
            wakeRoom()
        }
    }
}


def motionInactiveHandler(evt) {
    log "Motion ended on '${evt.displayName}'"

    if (settings.motionActivityKeepsAwake) {
        updateLastActivityTime()
    }
}

def updateLastActivityTime() {
    log "Noted activity in the room.  Updated lastActivityTime"
    state.lastActivityTime = formatDate(new Date())
}


def tickTock(evt) {
    if (!isCurrentlyNight()) {
        return
    }

    if (roomIsActive()) {
        return
    }

    def needToTurnOffIn30Seconds = false

    settings.dimmers?.each { dimmer ->
        if (dimmer.currentValue("switch") == "on") {
            if (dimmer.currentValue("level") > settings.dimmedLevel) {
                log "${dimmer.displayName} is on and level is ${dimmer.currentValue("level")}, which is above Dimmed Level (${settings.dimmedLevel}). Dimming..."
                dimmer.setLevel(settings.dimmedLevel, 10)
            }

            // Turn them off in 30 seconds if no activity happens before then.
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

    settings.switches?.each { s ->
        if (s.currentValue("switch") == "on") {
            // Turn them off in 30 seconds if no activity happens before then.
            needToTurnOffIn30Seconds = true
        }
    }

    if (needToTurnOffIn30Seconds) {
        log "Room is falling asleep.  Scheduling full sleep for 30 seconds from now."
        runIn(30, trySleepRoom)
    }
}


// Try to make the room go to sleep, as long as there's no ongoing activity.
def trySleepRoom(evt) {
    if (!isCurrentlyNight()) {
        return
    }

    if (roomIsActive()) {
        log "The room has activity. Allowing the room to stay awake."
        return
    }

    log "Putting the room to sleep."

    if (settings.sleepMode == "Completely off") {
        settings.dimmers?.each { dimmer ->
            dimmer.off()
        }
    }

    settings.switches?.each { s ->
        s.off()
    }
}


// Wake the room up
def wakeRoom() {
    if (atomicState["wakeRoom"] == true) {
        return
    }

    log "Waking room."

    atomicState["wakeRoom"] = true

    if (settings.wakeUpDimmers) {
        settings.dimmers?.each { dimmer ->
            if (dimmer.currentValue("switch") == "off") {
                log "Turning on dimmer '${dimmer.displayName}' to dimmed level."
                dimmer.setLevel(settings.dimmedLevel, 1)
            }
        }
    }

    if (settings.wakeUpSwitches) {
        settings.switches?.each { s ->
            if (s.currentValue("switch") == "off") {
                log "Turning on switch '${s.displayName}'."
                s.on()
            }
        }
    }

    atomicState["wakeRoom"] = false
}

def isCurrentlyNight() {
    def now = new Date()
    return isNight(settings.fromTime, settings.toTime, now)
}

def isNight(fromTimeSetting, toTimeSetting, now) {
    use (groovy.time.TimeCategory) {
        def fromTime = timeToday(fromTimeSetting)
        def toTime = timeToday(toTimeSetting)

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

/**
 * A room is "awake" if any of its light switches or dimmers are turned on.
 */
def roomIsAwake(Long deviceIdToIgnore = null) {
    def result = false

    settings.switches?.each { s ->
        if (s.currentValue("switch") == "on" && s.getIdAsLong() != deviceIdToIgnore) {
            result = true
        }
    }

    settings.dimmers?.each { d ->
        if (d.currentValue("switch") == "on" && d.currentValue("level") >= settings.dimmedLevel && d.getIdAsLong() != deviceIdToIgnore) {
            result = true
        }
    }

    return result
}

/**
 * A room is "active" if it has active motion sensors, or if any motion, switch, or dimmer activity has occurred recently.
 * Being "active" is a reason to keep a room "awake".
 */
def roomIsActive() {
    def result = false

    use (groovy.time.TimeCategory) {
        if (settings.motionActivityKeepsAwake) {
            // Return true if any motion sensor is active
            settings.motionSensors?.each { motionSensor ->
                if (motionSensor.currentValue("motion") == "active") {
                    result = true
                }
            }
        }

        // Return true if there has been dimmer/switch/motion activity in the last {activityTime}
        def now = new Date()
        def minutesSinceLastActivity = calculateMinutesSinceLastActivity()

        // log "Minutes since last activity: ${minutesSinceLastActivity}"
        // log "activityWaitMinutes: ${activityWaitMinutes}"

        if (minutesSinceLastActivity < activityWaitMinutes) {
            result = true
        }
    }

    return result
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

def calculateMinutesSinceLastActivity() {
    def now = new Date()
    def lastActivityTime = toDateTime(state.lastActivityTime)

    return calculateMinutesBetweenDates(lastActivityTime, now)
}

def calculateMinutesBetweenDates(Date date1, Date date2) {
    if (date1 >= date2) {
        return 0
    }

    use (groovy.time.TimeCategory) {
        def diff = date2 - date1

        return diff.seconds/60 + diff.minutes + diff.hours*60 + diff.days*60*24
    }
}

def dateNow() {
    return new Date()
}

def formatDate(date) {
    return date.format("yyyy-MM-dd'T'HH:mm:ssZ")
}
