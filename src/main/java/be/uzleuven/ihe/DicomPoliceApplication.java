package be.uzleuven.ihe;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MHDConfiguration.class)
public class DicomPoliceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DicomPoliceApplication.class, args);
    }
}
