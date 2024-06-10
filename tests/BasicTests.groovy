package joelwetzel.sleepy_house.tests

import me.biocomp.hubitat_ci.util.device_fixtures.SwitchFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.DimmerFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.MotionSensorFixtureFactory
import me.biocomp.hubitat_ci.util.integration.IntegrationAppSpecification
import me.biocomp.hubitat_ci.util.integration.TimeKeeper

import spock.lang.Specification

/**
* Basic tests for lockdown.groovy
*/
class BasicTests extends IntegrationAppSpecification {
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
    }

    void "installed() logs the settings"() {
        when:
        appScript.installed()

        then:
        1 * log.info('Installed with settings: [roomName:Test Room, switches:[GeneratedDevice(input: s1, type: t), GeneratedDevice(input: s2, type: t), GeneratedDevice(input: s3, type: t)], dimmers:[GeneratedDevice(input: d1, type: t), GeneratedDevice(input: d2, type: t), GeneratedDevice(input: d3, type: t)], motionSensors:[GeneratedDevice(input: m1, type: t), GeneratedDevice(input: m2, type: t), GeneratedDevice(input: m3, type: t)], dimmedLevel:5, motionActivityKeepsAwake:true, switchActivityKeepsAwake:true, dimmerActivityKeepsAwake:true, activityWaitMinutes:5, sleepMode:Completely off, wakeUpForMotion:true, wakeUpForSwitchActivity:true, wakeUpForDimmerActivity:true, wakeUpDimmers:true, wakeUpSwitches:true, fromTime:22:00, toTime:06:00, enableLogging:true]')
    }

    void "initialize() subscribes to events"() {
        when:
        appScript.initialize()

        then:
        1 * appExecutor.subscribe(motionSensors, 'motion.active', 'motionActiveHandler')
        1 * appExecutor.subscribe(motionSensors, 'motion.inactive', 'motionInactiveHandler')
        1 * appExecutor.subscribe(switchFixtures, 'switch.on', 'switchActivityHandler')
        1 * appExecutor.subscribe(dimmerFixtures, 'switch.on', 'switchActivityHandler')
        1 * appExecutor.subscribe(dimmerFixtures, 'level', 'levelActivityHandler')

        1 * appExecutor.runEvery1Minute('tickTock')
    }

    void "initialize() sets state"() {
        when:
        appScript.initialize()

        then:
        appState.lastActivityTime instanceof String
    }
}
