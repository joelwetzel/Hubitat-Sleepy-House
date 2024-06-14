package joelwetzel.sleepy_house.tests.integrationTests

import me.biocomp.hubitat_ci.util.device_fixtures.SwitchFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.DimmerFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.MotionSensorFixtureFactory
import me.biocomp.hubitat_ci.util.integration.IntegrationAppSpecification
import me.biocomp.hubitat_ci.util.integration.TimeKeeper
import me.biocomp.hubitat_ci.util.Utility
import me.biocomp.hubitat_ci.validation.Flags

import spock.lang.Specification
import spock.lang.Unroll

class CanWakeTests extends IntegrationAppSpecification {
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

    def wakeUpMessage = "Waking room."

    @Override
    def setup() {
        super.initializeEnvironment(appScriptFilename: "Sleepy-Room.groovy",
                                    validationFlags: [Flags.AllowWritingToSettings],
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

        TimeKeeper.reset()
    }


    def "during day, motion activity is ignored"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T12:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        motionSensorFixture1.activate()

        then:
        0 * log.debug(wakeUpMessage)
        // Check that all switches are still off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are still off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "during day, switch activity is ignored"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T12:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        switchFixture1.on()

        then:
        0 * log.debug(wakeUpMessage)
        // Check that the other 2 switches are still off
        switchFixture2.currentValue('switch') == 'off'
        switchFixture3.currentValue('switch') == 'off'
        // Check that all dimmers are still off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "during day, dimmer activity is ignored"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T12:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        dimmerFixture1.setLevel(50)

        then:
        0 * log.debug(wakeUpMessage)
        // Check that all switches are still off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that the other 2 dimmers are still off
        dimmerFixture2.currentValue('switch') == 'off'
        dimmerFixture3.currentValue('switch') == 'off'
    }

    def "at night, motion activity wakes room"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        motionSensorFixture1.activate()

        then:
        1 * log.debug(wakeUpMessage)
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }
    }

    def "at night, motion activity is ignored if wakeUpForMotion == false"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)
        appScript.wakeUpForMotion = false

        when:
        motionSensorFixture1.activate()

        then:
        0 * log.debug(wakeUpMessage)
        // Check that all switches are still off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are still off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "at night, switch activity wakes room"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        switchFixture3.on()

        then:
        1 * log.debug(wakeUpMessage)
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }
    }

    def "at night, switch activity is ignored if wakeUpForSwitchActivity == false"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)
        appScript.wakeUpForSwitchActivity = false

        when:
        switchFixture3.on()

        then:
        0 * log.debug(wakeUpMessage)
        // Check that the other 2 switches are still off
        switchFixture1.currentValue('switch') == 'off'
        switchFixture2.currentValue('switch') == 'off'
        // Check that all dimmers are still off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "at night, dimmer activity wakes room"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)

        when:
        dimmerFixture2.setLevel(50)

        then:
        1 * log.debug(wakeUpMessage)
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level, except the one I set to 50
        dimmerFixture1.currentValue('level') == 5
        dimmerFixture2.currentValue('level') == 50
        dimmerFixture3.currentValue('level') == 5
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }
    }

    def "at night, dimmer activity is ignored if wakeUpForDimmerActivity == false"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00.000-0600")
        TimeKeeper.set(testTime)
        appScript.wakeUpForDimmerActivity = false
        appScript.wakeUpForSwitchActivity = false       // TODO - dimmers are also switches, but this is confusing.

        when:
        dimmerFixture2.setLevel(50)

        then:
        0 * log.debug(wakeUpMessage)
        // Check that the other 2 switches are still off
        switchFixture1.currentValue('switch') == 'off'
        switchFixture2.currentValue('switch') == 'off'
        // Check that all dimmers are at the off level, except the one I set to 50
        dimmerFixture1.currentValue('level') == 0
        dimmerFixture2.currentValue('level') == 50
        dimmerFixture3.currentValue('level') == 0
        // Check that all dimmers are still off, except the one I turned on
        dimmerFixture1.currentValue('switch') == 'off'
        dimmerFixture2.currentValue('switch') == 'on'
        dimmerFixture3.currentValue('switch') == 'off'
    }
}
