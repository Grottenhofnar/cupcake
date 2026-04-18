package config;

import io.javalin.rendering.template.JavalinThymeleaf;

public class ThymeleafConfig {
    public static JavalinThymeleaf create() {
        return new JavalinThymeleaf();
    }
}
