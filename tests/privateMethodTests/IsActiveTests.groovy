package joelwetzel.sleepy_house.tests.privateMethodTests

import me.biocomp.hubitat_ci.util.device_fixtures.SwitchFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.DimmerFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.MotionSensorFixtureFactory
import me.biocomp.hubitat_ci.util.integration.IntegrationAppSpecification
import me.biocomp.hubitat_ci.util.integration.TimeKeeper

import spock.lang.Specification
import spock.lang.Unroll

class IsActiveTests extends IntegrationAppSpecification {
    def switchFixture1 = SwitchFixtureFactory.create('s1')
    def switchFixture2 = SwitchFixtureFactory.create('s2')
    def switchFixture3 = SwitchFixtureFactory.create('s3')
    def switchFixtures = [switchFixture1, switchFixture2, switchFixture3]

    def dimmerFixture1 = DimmerFixtureFactory.create('d1')
    def dimmerFixture2 = DimmerFixtureFactory.create('d2')
    def dimmerFixture3 = DimmerFixtureFactory.create('d3')
    def dimmerFixtures = [dimmerFixture1, dimmerFixture2, dimmerFixture3]


    def motionSensorFixture1 = MotionSensorFixtureFactory.create('m1')
    def motionSensorFixture2 = MotionSensorFixtureFactory.create('m2')
    def motionSensorFixture3 = MotionSensorFixtureFactory.create('m3')
    def motionSensors = [motionSensorFixture1, motionSensorFixture2, motionSensorFixture3]

    @Override
    def setup() {
        super.initializeEnvironment(appScriptFilename: "Sleepy-Room.groovy",
                                    userSettingValues: [
                                        roomName: "Test Room",
                                        switches: switchFixtures,
                                        dimmers: dimmerFixtures,
                                        motionSensors: motionSensors,
                                        dimmedLevel: 5,
                                        motionActivityKeepsAwake: true,
                                        switchActivityKeepsAwake: true,
                                        dimmerActivityKeepsAwake: true,
                                        activityWaitMinutes: 5,
                                        sleepMode: "Completely off",
                                        wakeUpForMotion: true,
                                        wakeUpForSwitchActivity: true,
                                        wakeUpForDimmerActivity: true,
                                        wakeUpDimmers: true,
                                        wakeUpSwitches: true,
                                        fromTime: "22:00",
                                        toTime: "06:00",
                                        enableLogging: true
                                    ])

        // Initialize each switch
        switchFixtures.each { it.initialize(appExecutor, [switch: "off"]) }

        // Initialize each dimmer
        dimmerFixtures.each { it.initialize(appExecutor, [switch: "off", level: 0]) }

        // Initialize each motion sensor
        motionSensors.each { it.initialize(appExecutor, [motion: "inactive"]) }

        appScript.installed()

        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")
        TimeKeeper.set(testTime)
    }


    def "with default sensor values, the room should evaluate as not active"() {
        expect:
        appScript.roomIsActive() == false
    }

    def "advancing in time, the room should stay inactive"() {
        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false
    }

    def "turning on a switch, the room should be active"() {
        when:
        switchFixture2.on()

        then:
        1 * log.debug('Switch activity detected on \'s2\'')
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true
    }

    def "when switch is turned back off, room goes inactive 5 minutes after it was turned on"() {
        when:
        switchFixture2.on()

        then:
        1 * log.debug('Switch activity detected on \'s2\'')
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true

        when:
        switchFixture2.off()

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(4)

        then:
        appScript.roomIsActive() == false
    }

    def "adjusting a dimmer, the room should be active"() {
        when:
        dimmerFixture2.setLevel(50)

        then:
        1 * log.debug('Level activity detected on \'d2\'')    // One for switch on, one for level
        1 * log.debug('Switch activity detected on \'d2\'')
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true
    }

    def "adjusting a dimmer, the room should be inactive after 5 minutes"() {
        when:
        dimmerFixture2.setLevel(50)

        then:
        1 * log.debug('Level activity detected on \'d2\'')    // One for switch on, one for level
        1 * log.debug('Switch activity detected on \'d2\'')
        appScript.roomIsActive() == true

        when:
        for (def i = 0; i < 5; i++) {
            TimeKeeper.advanceMinutes(1)
        }
        def isActive = appScript.roomIsActive()

        then:
        isActive == false
    }

    def "motion detected, the room should be active"() {
        when:
        motionSensorFixture2.activate()

        then:
        1 * log.debug('Motion detected on \'m2\'')
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true
    }

    def "when motion ends, the room stays active for 5 minutes only"() {
        when:
        motionSensorFixture2.activate()

        then:
        1 * log.debug('Motion detected on \'m2\'')
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        and:
        motionSensorFixture2.inactivate()

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == true

        when:
        TimeKeeper.advanceMinutes(1)

        then:
        appScript.roomIsActive() == false
    }
}
