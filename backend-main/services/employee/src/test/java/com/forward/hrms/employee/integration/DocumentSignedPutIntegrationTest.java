package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.dto.request.RecordDocumentRequest;
import com.forward.hrms.employee.dto.request.UploadUrlRequest;
import com.forward.hrms.employee.dto.response.UploadUrlResponse;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeDocument;
import com.forward.hrms.employee.enums.DocumentType;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.repository.EmployeeDocumentRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.EmployeeDocumentService;

import io.minio.StatObjectResponse;
import okhttp3.Headers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Document upload contract: two-step signed-PUT flow + object-key prefix
 * guard. Validates:
 *
 * <ul>
 *   <li>{@code signUploadUrl} returns a key under {@code employees/{id}/}.</li>
 *   <li>{@code record} fails with 422 when the object isn't actually in
 *       MinIO (client never finished the PUT).</li>
 *   <li>{@code record} fails with 409 when the {@code objectKey} doesn't
 *       belong to the calling employee — including URL-encoded {@code ..}
 *       traversal.</li>
 *   <li>Happy path persists the metadata row pointing at the uploaded
 *       object's key + actual size.</li>
 * </ul>
 *
 * <p>{@link ObjectStorage} is mocked to keep this test focused on the
 * service contract — the SigV4-host-binding gotcha (in CLAUDE.md) makes
 * the real signed-URL round-trip flaky on developer laptops where the
 * docker-compose MinIO and the testcontainer compete for port 9000.
 */
@SpringBootTest
@WithTestcontainers
class DocumentSignedPutIntegrationTest {

    @Autowired EmployeeDocumentService docService;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeDocumentRepository documents;
    @Autowired DocumentProperties docProps;

    @MockitoBean ObjectStorage storage;

    private long employeeId;
    private long otherEmployeeId;

    @BeforeEach
    void seedAndStubStorage() {
        // Don't deleteAll on employees — Testcontainers DB reuse leaves
        // self-referential manager_id rows from prior runs that block delete.
        String suffix = String.valueOf(System.nanoTime());
        employeeId = persistEmployee("DOCS-A-" + suffix, "Docs One");
        otherEmployeeId = persistEmployee("DOCS-B-" + suffix, "Docs Two");

        // Default stubs — individual tests override stat() for their case.
        lenient().when(storage.signedPutUrl(anyString(), anyString(), any()))
                .thenAnswer(inv -> "https://minio.example/" + inv.getArgument(0)
                        + "/" + inv.getArgument(1) + "?X-Amz-Signature=stubbed");
        lenient().when(storage.signedGetUrl(anyString(), anyString(), any()))
                .thenAnswer(inv -> "https://minio.example/" + inv.getArgument(0)
                        + "/" + inv.getArgument(1) + "?X-Amz-Signature=get-stubbed");
    }

    @Test
    void signUploadUrl_keyLandsUnderEmployeePrefix() {
        UploadUrlResponse res = docService.signUploadUrl(employeeId,
                new UploadUrlRequest(DocumentType.CNIC, "id_card.pdf", "application/pdf", 123L));

        assertThat(res.objectKey()).startsWith("employees/" + employeeId + "/cnic/");
        assertThat(res.uploadUrl()).contains("X-Amz-Signature");
        assertThat(res.bucket()).isEqualTo(docProps.bucket());
        assertThat(res.expiresAt()).isNotNull();
    }

    @Test
    void record_succeedsAfterUpload_persistsMetadataWithActualSize() {
        UploadUrlResponse signed = docService.signUploadUrl(employeeId,
                new UploadUrlRequest(DocumentType.CNIC, "id.pdf", "application/pdf", 32L));
        long actualSize = "the-actual-document-bytes".getBytes().length;
        when(storage.stat(docProps.bucket(), signed.objectKey()))
                .thenReturn(Optional.of(stubStat(actualSize, "application/pdf")));

        var dto = docService.record(employeeId, new RecordDocumentRequest(
                DocumentType.CNIC, "ID Card", signed.objectKey(), "application/pdf"));

        assertThat(dto.objectKey()).isEqualTo(signed.objectKey());
        EmployeeDocument persisted = documents.findById(dto.id()).orElseThrow();
        assertThat(persisted.getEmployeeId()).isEqualTo(employeeId);
        assertThat(persisted.getDocType()).isEqualTo(DocumentType.CNIC);
        assertThat(persisted.getSizeBytes()).isEqualTo(actualSize);
    }

    @Test
    void record_withMissingObject_throws422() {
        UploadUrlResponse signed = docService.signUploadUrl(employeeId,
                new UploadUrlRequest(DocumentType.CNIC, "id.pdf", "application/pdf", 32L));
        when(storage.stat(docProps.bucket(), signed.objectKey())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> docService.record(employeeId, new RecordDocumentRequest(
                DocumentType.CNIC, "ID Card", signed.objectKey(), "application/pdf")))
                .isInstanceOf(UnprocessableError.class);
    }

    @Test
    void record_rejectsObjectKeyOutsideOwnEmployeePrefix() {
        UploadUrlResponse foreign = docService.signUploadUrl(otherEmployeeId,
                new UploadUrlRequest(DocumentType.CNIC, "leak.pdf", "application/pdf", 32L));
        assertThat(foreign.objectKey()).startsWith("employees/" + otherEmployeeId + "/");

        assertThatThrownBy(() -> docService.record(employeeId, new RecordDocumentRequest(
                DocumentType.CNIC, "Leak", foreign.objectKey(), "application/pdf")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void record_rejectsUrlEncodedTraversal() {
        // %2E%2E/%2E%2E decodes to ../.. — after normalize this escapes
        // employees/{self}/ and lands under employees/{other}/.
        String malicious = "employees/" + employeeId
                + "/cnic/%2E%2E/%2E%2E/" + otherEmployeeId + "/cnic/leak.pdf";

        assertThatThrownBy(() -> docService.record(employeeId, new RecordDocumentRequest(
                DocumentType.CNIC, "Leak", malicious, "application/pdf")))
                .isInstanceOf(ConflictError.class);
    }

    // ---------------------------------------------------------------- helpers

    private long persistEmployee(String code, String fullName) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFullName(fullName);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    /**
     * MinIO's {@link StatObjectResponse} doesn't have a public constructor
     * we can call with sane defaults — use the protected one via a tiny
     * subclass. Only {@code size} and {@code contentType} matter for the
     * service's checks.
     */
    private static StatObjectResponse stubStat(long size, String contentType) {
        Headers headers = new Headers.Builder()
                .add("Content-Length", String.valueOf(size))
                .add("Content-Type", contentType)
                .add("ETag", "\"stub-etag\"")
                .add("Last-Modified", "Sat, 09 May 2026 10:00:00 GMT")
                .build();
        return new StatObjectResponse(headers, "bucket-stub", null, "object-stub");
    }
}
