package org.grails.demo

class UrlMappings {

    static mappings = {
        "/greeting"(controller: "greeting", action: "index", method: "GET")
        "/greeting"(controller: "greeting", action: "save", method: "POST")
        
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
