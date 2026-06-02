package be.uzleuven.ihe.service.MHD.fhir;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import be.uzleuven.ihe.service.MHD.fhir.interceptor.BaseUrlInterceptor;
import be.uzleuven.ihe.service.MHD.fhir.interceptor.BinaryContentDispositionInterceptor;
import be.uzleuven.ihe.service.MHD.fhir.provider.BinaryProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.BundleProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.CapabilityStatementProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.DocumentReferenceProvider;
import be.uzleuven.ihe.service.MHD.fhir.provider.ListProvider;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.interceptor.CorsInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

/**
 * HAPI FHIR Restful Server for MHD Document Responder.
 * Configures the FHIR R4 server with MHD-specific resource providers.
 */
public class MHDFhirRestfulServer extends ca.uhn.fhir.rest.server.RestfulServer {

    private static final long serialVersionUID = 1L;

    private final DocumentReferenceProvider documentReferenceProvider;
    private final ListProvider listProvider;
    private final BinaryProvider binaryProvider;
    private final BundleProvider bundleProvider;
    private final CapabilityStatementProvider capabilityStatementProvider;
    private final MHDConfiguration config;

    @Autowired
    public MHDFhirRestfulServer(DocumentReferenceProvider documentReferenceProvider,
                                  ListProvider listProvider,
                                  BinaryProvider binaryProvider,
                                  BundleProvider bundleProvider,
                                  CapabilityStatementProvider capabilityStatementProvider,
                                  MHDConfiguration config) {
        this.documentReferenceProvider = documentReferenceProvider;
        this.listProvider = listProvider;
        this.binaryProvider = binaryProvider;
        this.bundleProvider = bundleProvider;
        this.capabilityStatementProvider = capabilityStatementProvider;
        this.config = config;
    }

    @Override
    protected void initialize() {
        // Set FHIR R4 context
        setFhirContext(FhirContext.forR4());

        // Register resource providers
        registerProvider(documentReferenceProvider);
        registerProvider(listProvider);
        registerProvider(binaryProvider);
        registerProvider(bundleProvider);

        // Register capability statement provider (for /metadata endpoint)
        registerProvider(capabilityStatementProvider);

        // Server settings
        setServerName("IHE MHD Document Responder - MADO");
        setServerVersion(config.getSoftwareVersion());
        setImplementationDescription("IHE MHD Document Responder facade for DICOM MADO manifests");

        // Default to JSON encoding so errors and responses are always JSON
        setDefaultResponseEncoding(EncodingEnum.JSON);
        setDefaultPrettyPrint(true);

        // Add response highlighter for browser testing (only when _format is not explicitly set)
        ResponseHighlighterInterceptor responseHighlighter = new ResponseHighlighterInterceptor();
        responseHighlighter.setShowResponseHeaders(false);
        registerInterceptor(responseHighlighter);

        // Add interceptor to handle base URL requests (return CapabilityStatement as JSON)
        registerInterceptor(new BaseUrlInterceptor(capabilityStatementProvider, getFhirContext()));

        // Add interceptor to set Content-Disposition for Binary resources (avoids servlet types)
        registerInterceptor(new BinaryContentDispositionInterceptor());

        // Configure CORS
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(java.util.Collections.singletonList("*"));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        corsConfig.setAllowedHeaders(java.util.Collections.singletonList("*"));
        corsConfig.setExposedHeaders(Arrays.asList("Location", "Content-Location"));
        corsConfig.setAllowCredentials(false);
        CorsInterceptor corsInterceptor = new CorsInterceptor(corsConfig);
        registerInterceptor(corsInterceptor);
    }
}
