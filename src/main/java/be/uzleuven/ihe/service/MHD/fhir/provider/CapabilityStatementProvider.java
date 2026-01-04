package be.uzleuven.ihe.service.MHD.fhir.provider;

import be.uzleuven.ihe.service.MHD.config.MHDConfiguration;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Provider for the server's CapabilityStatement.
 * Declares support for MHD Document Responder with MADO capabilities.
 */
@Component
public class CapabilityStatementProvider {

    private final MHDConfiguration config;

    private RestfulServer restfulServer;

    @Autowired
    public CapabilityStatementProvider(MHDConfiguration config) {
        this.config = config;
    }

    public void setRestfulServer(RestfulServer theRestfulServer) {
        this.restfulServer = theRestfulServer;
    }

    /**
     * Create the CapabilityStatement for this MHD Document Responder.
     */
    @Metadata
    public CapabilityStatement getConformance() {
        CapabilityStatement cs = new CapabilityStatement();

        // Basic metadata
        cs.setId("MHD-DocumentResponder-MADO");
        cs.setUrl(config.getFhirBaseUrl() + "/metadata");
        cs.setVersion(config.getSoftwareVersion());
        cs.setName("MHDDocumentResponderMADO");
        cs.setTitle("IHE MHD Document Responder with MADO Support");
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setExperimental(false);
        cs.setDate(new Date());
        cs.setPublisher(config.getInstitutionName());

        cs.setDescription("This server implements the IHE MHD Document Responder actor " +
                "with support for MADO (Manifest for Access to DICOM Objects). " +
                "It provides a FHIR R4 facade over a DICOM PACS, generating MADO manifests on-the-fly.");

        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);

        // Software
        CapabilityStatement.CapabilityStatementSoftwareComponent software =
            new CapabilityStatement.CapabilityStatementSoftwareComponent();
        software.setName(config.getManufacturerModelName());
        software.setVersion(config.getSoftwareVersion());
        cs.setSoftware(software);

        // Implementation
        CapabilityStatement.CapabilityStatementImplementationComponent implementation =
            new CapabilityStatement.CapabilityStatementImplementationComponent();
        implementation.setDescription("IHE MHD Document Responder MADO Facade");
        implementation.setUrl(config.getFhirBaseUrl());
        cs.setImplementation(implementation);

        // FHIR Version
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);

        // Formats
        cs.addFormat("application/fhir+json");
        cs.addFormat("application/fhir+xml");

        // Implementation Guides (IHE MHD)
        cs.addImplementationGuide("https://profiles.ihe.net/ITI/MHD/ImplementationGuide/ihe.iti.mhd");

        // REST configuration
        CapabilityStatement.CapabilityStatementRestComponent rest =
            new CapabilityStatement.CapabilityStatementRestComponent();
        rest.setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        rest.setDocumentation("MHD Document Responder supporting ITI-66, ITI-67, and ITI-68 transactions");

        // Security - placeholder for IUA support
        CapabilityStatement.CapabilityStatementRestSecurityComponent security =
            new CapabilityStatement.CapabilityStatementRestSecurityComponent();
        security.setCors(true);
        security.setDescription("TLS 1.2+ recommended. OAuth2/IUA integration available.");
        rest.setSecurity(security);

        // DocumentReference resource
        CapabilityStatement.CapabilityStatementRestResourceComponent docRefResource =
            new CapabilityStatement.CapabilityStatementRestResourceComponent();
        docRefResource.setType("DocumentReference");
        // supported profiles
        docRefResource.getSupportedProfile().add(new CanonicalType("https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.DocumentReference"));
        docRefResource.setDocumentation("ITI-67: Find Document References");

        // DocumentReference interactions
        docRefResource.addInteraction()
            .setCode(CapabilityStatement.TypeRestfulInteraction.READ)
            .setDocumentation("Read a DocumentReference by ID");
        docRefResource.addInteraction()
            .setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE)
            .setDocumentation("Search for DocumentReferences");

        // DocumentReference search parameters
        addSearchParam(docRefResource, "patient", Enumerations.SearchParamType.REFERENCE,
            "Patient reference");
        addSearchParam(docRefResource, "patient.identifier", Enumerations.SearchParamType.TOKEN,
            "Patient identifier");
        addSearchParam(docRefResource, "status", Enumerations.SearchParamType.TOKEN,
            "Document status");
        addSearchParam(docRefResource, "date", Enumerations.SearchParamType.DATE,
            "Document creation date");
        addSearchParam(docRefResource, "study-instance-uid", Enumerations.SearchParamType.STRING,
            "MADO: DICOM Study Instance UID");
        addSearchParam(docRefResource, "accession", Enumerations.SearchParamType.TOKEN,
            "MADO: Accession Number");
        addSearchParam(docRefResource, "modality", Enumerations.SearchParamType.TOKEN,
            "MADO: Modality filter");

        rest.addResource(docRefResource);

        // List (SubmissionSet) resource
        CapabilityStatement.CapabilityStatementRestResourceComponent listResource =
            new CapabilityStatement.CapabilityStatementRestResourceComponent();
        listResource.setType("List");
        listResource.getSupportedProfile().add(new CanonicalType("https://profiles.ihe.net/ITI/MHD/StructureDefinition/IHE.MHD.Minimal.SubmissionSet"));
        listResource.setDocumentation("ITI-66: Find Document Lists (SubmissionSets)");

        // List interactions
        listResource.addInteraction()
            .setCode(CapabilityStatement.TypeRestfulInteraction.READ)
            .setDocumentation("Read a SubmissionSet by ID");
        listResource.addInteraction()
            .setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE)
            .setDocumentation("Search for SubmissionSets");

        // List search parameters
        addSearchParam(listResource, "patient", Enumerations.SearchParamType.REFERENCE,
            "Patient reference");
        addSearchParam(listResource, "patient.identifier", Enumerations.SearchParamType.TOKEN,
            "Patient identifier");
        addSearchParam(listResource, "status", Enumerations.SearchParamType.TOKEN,
            "List status");
        addSearchParam(listResource, "code", Enumerations.SearchParamType.TOKEN,
            "List type code (submissionset)");
        addSearchParam(listResource, "date", Enumerations.SearchParamType.DATE,
            "Submission date");

        rest.addResource(listResource);

        // Binary resource
        CapabilityStatement.CapabilityStatementRestResourceComponent binaryResource =
            new CapabilityStatement.CapabilityStatementRestResourceComponent();
        binaryResource.setType("Binary");
        binaryResource.setDocumentation("ITI-68: Retrieve Document (MADO manifest)");

        // Binary interactions
        binaryResource.addInteraction()
            .setCode(CapabilityStatement.TypeRestfulInteraction.READ)
            .setDocumentation("Retrieve MADO manifest as DICOM file");

        rest.addResource(binaryResource);

        cs.addRest(rest);

        return cs;
    }

    private void addSearchParam(CapabilityStatement.CapabilityStatementRestResourceComponent resource,
                                 String name, Enumerations.SearchParamType type, String documentation) {
        CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent param =
            new CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent();
        param.setName(name);
        param.setType(type);
        param.setDocumentation(documentation);
        resource.addSearchParam(param);
    }
}
