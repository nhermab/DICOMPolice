package be.uzleuven.ihe.service.MHD.fhir.interceptor;

import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal no-op interceptor placeholder. The project prefers avoiding servlet types.
 * If you want to add response header manipulation via HAPI interceptors, implement it here
 * using APIs available in your HAPI version.
 */
public class BinaryContentDispositionInterceptor extends InterceptorAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(BinaryContentDispositionInterceptor.class);

    // No-op: kept to allow server registration without servlet dependencies
}
