package org.grails.demo

import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class GreetingSpec extends Specification implements DomainUnitTest<Greeting> {

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect:"fix me"
            true == false
    }
}
