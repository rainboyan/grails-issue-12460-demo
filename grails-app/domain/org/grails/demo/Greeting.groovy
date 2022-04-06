package org.grails.demo

class Greeting {
    String content

    static constraints = {
        content(blank: false, nullable: false)
    }
}
