package com.swen3.ocr_worker.storage;

import com.swen3.ocr_worker.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Failure paths only: they decide whether a message is retried or dropped. */
@ExtendWith(MockitoExtension.class)
class MinioDocumentStorageTest {

    private static final MinioProperties PROPERTIES =
            new MinioProperties("http://minio:9000", "user", "secret", "documents");

    @Mock
    private MinioClient client;

    private ErrorResponseException minioError(String code) {
        ErrorResponse response = mock(ErrorResponse.class);
        when(response.code()).thenReturn(code);
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.errorResponse()).thenReturn(response);
        return exception;
    }

    @Test
    void download_noSuchKey_throwsDocumentNotFound() throws Exception {
        ErrorResponseException error = minioError("NoSuchKey");   // built first: stubbing must not be nested
        when(client.getObject(any(GetObjectArgs.class))).thenThrow(error);

        assertThatThrownBy(() -> new MinioDocumentStorage(client, PROPERTIES).download("5/scan.pdf"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("5/scan.pdf");
    }

    @Test
    void download_otherMinioError_throwsStorageException() throws Exception {
        ErrorResponseException error = minioError("AccessDenied");
        when(client.getObject(any(GetObjectArgs.class))).thenThrow(error);

        assertThatThrownBy(() -> new MinioDocumentStorage(client, PROPERTIES).download("5/scan.pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("AccessDenied");
    }

    @Test
    void download_connectionProblem_throwsStorageException() throws Exception {
        when(client.getObject(any(GetObjectArgs.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> new MinioDocumentStorage(client, PROPERTIES).download("5/scan.pdf"))
                .isInstanceOf(StorageException.class)
                .hasCauseInstanceOf(IOException.class);
    }
}
