package joelwetzel.sleepy_house.tests

import me.biocomp.hubitat_ci.util.device_fixtures.SwitchFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.DimmerFixtureFactory
import me.biocomp.hubitat_ci.util.device_fixtures.MotionSensorFixtureFactory
import me.biocomp.hubitat_ci.util.integration.IntegrationAppSpecification
import me.biocomp.hubitat_ci.util.integration.TimeKeeper

import spock.lang.Specification
import spock.lang.Unroll

/**
* Tests of private methods for lockdown.groovy
*/
class IsNightTests extends IntegrationAppSpecification {
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
                                        activityWaitMinutes: 5,
                                        sleepMode: "Completely off",
                                        wakeUpForMotion: true,
                                        wakeUpDimmers: true,
                                        wakeUpSwitches: true,
                                        fromTime: "22:00",
                                        toTime: "06:00",
                                        enableLogging: true
                                    ])
    }

    @Unroll
    def "isNight calculates correctly with an overnight span"(String testTime, boolean expectedResult) {
        given:
        def fromTime = "2020-02-24T22:00:00.000-0600"
        def toTime = "2020-02-24T05:30:00.000-0600"
        def testTimeToday = appScript.timeToday(testTime)

        expect:
        appScript.isNight(fromTime, toTime, testTimeToday) == expectedResult

        where:
        testTime | expectedResult
        "2020-02-24T17:00:00.000-0600" | false          // Before fromTime
        "2020-02-24T23:00:00.000-0600" | true           // After fromTime, but before midnight
        "2020-02-25T03:00:00.000-0600" | true           // After midnight, but before toTime
        "2020-02-25T06:00:00.000-0600" | false          // After toTime
    }

    @Unroll
    def "isNight calculates correctly with a morning-only span"(String testTime, boolean expectedResult) {
        given:
        def fromTime = "2020-02-24T02:00:00.000-0600"
        def toTime = "2020-02-24T05:30:00.000-0600"
        def testTimeToday = appScript.timeToday(testTime)

        expect:
        appScript.isNight(fromTime, toTime, testTimeToday) == expectedResult

        where:
        testTime | expectedResult
        "2020-02-24T01:00:00.000-0600" | false          // Before fromTime
        "2020-02-24T03:00:00.000-0600" | true           // During
        "2020-02-25T06:00:00.000-0600" | false          // After toTime
    }
}
