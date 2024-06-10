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

/**
* Tests of TODO
*/
class CanSleepTests extends IntegrationAppSpecification {
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


    def "after waking, room gets sleepy after 5 minutes"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)            // After 5 minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "activityWaitMinutes can be adjusted"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")
        appScript.activityWaitMinutes = 10

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)            // After 5 minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        0 * log.debug("Scheduling trySleepRoom for 30 seconds from now")        // Should not go to sleep after 5 minutes

        when:
        TimeKeeper.advanceMinutes(1)            // But after another 5 minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "motion activity will delay sleep"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        motionSensorFixture1.activate()         // But then there is more motion after 4 minutes
        TimeKeeper.advanceMinutes(1)

        then:
        0 * log.debug("Scheduling trySleepRoom for 30 seconds from now")    // Shouldn't try to sleep
        appScript.roomIsActive() == true                                    // Room should still be active

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)            // After 5 minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "switch activity will delay sleep"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        switchFixture1.on()         // But then there is switch activity after 4 minutes
        TimeKeeper.advanceMinutes(1)

        then:
        0 * log.debug("Scheduling trySleepRoom for 30 seconds from now")    // Shouldn't try to sleep
        appScript.roomIsActive() == true                                    // Room should still be active

        when:
        TimeKeeper.advanceMinutes(1)            // After 4 more minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }

    def "dimmer activity can delay sleep"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        dimmerFixture1.setLevel(99)         // But then there is dimmer activity after 4 minutes
        TimeKeeper.advanceMinutes(1)

        then:
        0 * log.debug("Scheduling trySleepRoom for 30 seconds from now")    // Shouldn't try to sleep
        appScript.roomIsActive() == true                                    // Room should still be active

        when:
        TimeKeeper.advanceMinutes(1)            // After 4 more minutes, we starting shutting the lights off, starting with the dimmers
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }


    def "dimmer activity does not delay sleep if it's below the minimum level"() {
        given:
        def testTime = appScript.timeToday("2020-02-24T02:00:00-0600")

        when:
        TimeKeeper.set(testTime)

        and:
        motionSensorFixture1.activate()

        then:
        // Check that all switches are on
        switchFixtures.each { assert it.currentValue('switch') == 'on' }
        // Check that all dimmers are at the dimmed level
        dimmerFixtures.each { assert it.currentValue('level') == 5 }
        dimmerFixtures.each { assert it.currentValue('switch') == 'on' }

        when:
        motionSensorFixture1.inactivate()
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        TimeKeeper.advanceMinutes(1)
        dimmerFixture1.setLevel(1)         // But then there is dimmer activity after 4 minutes, but it's to down below the minimum level
        TimeKeeper.advanceMinutes(1)

        then:
        1 * log.debug("Scheduling trySleepRoom for 30 seconds from now")

        when:
        TimeKeeper.advanceSeconds(30)           // And it takes 30 seconds to shut them off, with switches last

        then:
        1 * log.debug("trySleepRoom()")
        // Check that all switches are off
        switchFixtures.each { assert it.currentValue('switch') == 'off' }
        // Check that all dimmers are off
        dimmerFixtures.each { assert it.currentValue('switch') == 'off' }
    }
}
