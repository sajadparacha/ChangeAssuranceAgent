package com.company.changeassurance;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Boots the application when deployed as a WAR to an external servlet container
 * (WebLogic 15.1.1+ with Jakarta EE and Java 17).
 */
public class ChangeAssuranceServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ChangeAssuranceApplication.class);
    }
}
