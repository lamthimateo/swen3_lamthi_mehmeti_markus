package com.swen3.swen3rest.storage;

import com.swen3.swen3rest.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MinIO is a Mockito mock; these tests check what the service does around the client calls. */
@ExtendWith(MockitoExtension.class)
class MinioFileStorageTest {

    private static final MinioProperties PROPERTIES =
            new MinioProperties("http://minio:9000", "user", "secret", "documents");

    @Mock
    private MinioClient client;

    private MinioFileStorage storage() {
        return new MinioFileStorage(client, PROPERTIES);
    }

    private static ByteArrayInputStream content() {
        return new ByteArrayInputStream("%PDF-1.7".getBytes());
    }

    @Test
    void store_createsTheBucketOnFirstUse_thenUploads() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storage().store("7/invoice.pdf", content(), 8, "application/pdf");

        verify(client).makeBucket(any(MakeBucketArgs.class));
        verify(client).putObject(any(PutObjectArgs.class));
    }

    @Test
    void store_doesNotRecreateAnExistingBucket_andChecksOnlyOnce() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MinioFileStorage storage = storage();

        storage.store("1/a.pdf", content(), 8, "application/pdf");
        storage.store("2/b.pdf", content(), 8, "application/pdf");

        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
        verify(client, times(1)).bucketExists(any(BucketExistsArgs.class));
        verify(client, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    void store_minioUnreachable_throwsStorageException() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> storage().store("7/invoice.pdf", content(), 8, "application/pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("7/invoice.pdf")
                .hasCauseInstanceOf(IOException.class);
        verify(client, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void deleteQuietly_removesTheObject() throws Exception {
        storage().deleteQuietly("7/invoice.pdf");

        verify(client).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteQuietly_swallowsFailures() throws Exception {
        doThrow(new IOException("connection refused"))
                .when(client).removeObject(any(RemoveObjectArgs.class));

        assertThatCode(() -> storage().deleteQuietly("7/invoice.pdf")).doesNotThrowAnyException();
    }
}
