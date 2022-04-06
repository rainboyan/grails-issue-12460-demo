package org.grails.demo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class HelloController {

    @GetMapping("/greeting2")
    public String greetingForm(Model model) {
        model.addAttribute("greeting", new Greeting());
        return "hello";
    }

    @PostMapping("/greeting2")
    public String greetingSubmit(@ModelAttribute Greeting greeting, Model model) {
        return "hello";
    }

}
