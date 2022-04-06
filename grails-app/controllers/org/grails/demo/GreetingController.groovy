package org.grails.demo

class GreetingController {

    static allowedMethods = [save: "POST"]

    def index() {
    }

    def save(Greeting greeting) {
        render 'Hello World! Exploit...!'
    }
}
