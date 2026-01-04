package be.uzleuven.ihe.service.MHD.config;

import be.uzleuven.ihe.service.MHD.fhir.MHDFhirRestfulServer;
import be.uzleuven.ihe.service.MHD.fhir.provider.BinaryProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.CapabilityStatementProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.DocumentReferenceProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.ListProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Configuration for the MHD FHIR Server.
 * Registers the HAPI FHIR RestfulServer as a servlet.
 */
@Configuration
public class FhirServerConfig {

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ServletRegistrationBean fhirServletRegistration(
            DocumentReferenceProvider documentReferenceProvider,
            ListProvider listProvider,
            BinaryProvider binaryProvider,
            CapabilityStatementProvider capabilityStatementProvider,
            MHDConfiguration config) {

        MHDFhirRestfulServer fhirServlet = new MHDFhirRestfulServer(
            documentReferenceProvider,
            listProvider,
            binaryProvider,
            capabilityStatementProvider,
            config
        );

        ServletRegistrationBean registration =
            new ServletRegistrationBean<>(fhirServlet, "/fhir/*");

        registration.setName("MHD FHIR Server");
        registration.setLoadOnStartup(1);

        return registration;
    }
}
