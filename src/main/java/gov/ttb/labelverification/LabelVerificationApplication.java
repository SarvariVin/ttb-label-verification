package gov.ttb.labelverification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LabelVerificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabelVerificationApplication.class, args);
    }
}
