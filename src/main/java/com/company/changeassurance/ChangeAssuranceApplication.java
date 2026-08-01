package com.company.changeassurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Change Assurance Agent — standalone Spring Boot entry point.
 *
 * <p>Runnable as an executable WAR ({@code java -jar}) or deployed to an external
 * servlet container such as WebLogic 15.x via {@link ChangeAssuranceServletInitializer}.
 */
@SpringBootApplication
public class ChangeAssuranceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChangeAssuranceApplication.class, args);
    }
}
