package be.uzleuven.ihe.service.MHD.fhir.interceptor;

import be.uzleuven.ihe.service.MHD.fhir.provider.CapabilityStatementProvider;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Interceptor that handles requests to the FHIR base URL (e.g. /fhir or /fhir?_format=json).
 * Instead of returning a 400 error, it returns the server's CapabilityStatement as JSON.
 */
@Interceptor
public class BaseUrlInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(BaseUrlInterceptor.class);

    private final CapabilityStatementProvider capabilityStatementProvider;
    private final FhirContext fhirContext;

    public BaseUrlInterceptor(CapabilityStatementProvider capabilityStatementProvider, FhirContext fhirContext) {
        this.capabilityStatementProvider = capabilityStatementProvider;
        this.fhirContext = fhirContext;
    }

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_PROCESSED)
    public boolean handleBaseUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String pathInfo = request.getPathInfo();

        // Check if this is a base URL request (no resource type or operation)
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            LOG.info("Base URL request detected, returning CapabilityStatement as JSON");

            CapabilityStatement cs = capabilityStatementProvider.getConformance();
            String json = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(cs);

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/fhir+json;charset=UTF-8");
            response.getWriter().write(json);
            response.getWriter().flush();

            // Return false to stop further processing
            return false;
        }

        // Continue normal processing
        return true;
    }
}

