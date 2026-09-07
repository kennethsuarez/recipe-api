package com.kenenthsuarez.recipe_api.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerUIController {
    @GetMapping("/docs")
    String documentation() {
        return "redirect:/docs.html";
    }
}
