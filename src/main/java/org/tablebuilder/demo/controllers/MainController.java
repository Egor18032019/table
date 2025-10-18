package org.tablebuilder.demo.controllers;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@Controller
@RequiredArgsConstructor
public class MainController {
    static final Logger log = LoggerFactory.getLogger(MainController.class);

    @RequestMapping({"/"})
    public String loadUI() {
        System.out.println("Forward !!!!");
        log.info("Forward on React index.html...");

        return "forward:/index.html";
    }
}