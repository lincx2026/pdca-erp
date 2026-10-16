package com.pcba.erp;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.pcba.erp.common.config.OssConfig;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.service.OssService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OssServiceTest {
    @Test
    void blankStsTokensAreOmittedFromSignedUrls() {
        for (String token : new String[]{null, "", " \t "}) {
            OssConfig config = signingConfig(token);
            OSS client = config.ossClient();
            try {
                String query = client.generatePresignedUrl(config.getBucket(), "test.txt",
                        new Date(System.currentTimeMillis() + 60_000)).getQuery();
                assertFalse(query.contains("security-token"));
            } finally {
                client.shutdown();
            }
        }
    }

    @Test
    void configuredStsTokensArePreservedInSignedUrls() {
        OssConfig config = signingConfig("test-security-token");
        OSS client = config.ossClient();
        try {
            String query = client.generatePresignedUrl(config.getBucket(), "test.txt",
                    new Date(System.currentTimeMillis() + 60_000)).getQuery();
            assertTrue(query.contains("security-token=test-security-token"));
        } finally {
            client.shutdown();
        }
    }

    private static OssConfig signingConfig(String token) {
        OssConfig config = new OssConfig();
        config.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        config.setBucket("test-bucket");
        config.setAccessKeyId("test-access-key");
        config.setAccessKeySecret("test-access-secret");
        config.setSecurityToken(token);
        return config;
    }

    @Test
    void uploadsKeepObjectKeysAndCloseTheStream() throws Exception {
        OSS client = mock(OSS.class);
        OssConfig config = new OssConfig(); config.setBucket("test-bucket");
        OssService service = new OssService(client, config);
        assertEquals("pm/project/file.txt", service.upload(
                new MockMultipartFile("file", "file.txt", "text/plain", new byte[]{1,2,3}), "pm/project/file.txt"));
        verify(client).putObject(eq("test-bucket"), eq("pm/project/file.txt"), any(InputStream.class),
                argThat((ObjectMetadata m) -> m.getContentLength()==3 && "text/plain".equals(m.getContentType())));
    }

    @Test
    void closingDownloadClosesTheSdkObject() throws Exception {
        OSS client = mock(OSS.class);
        OssConfig config = new OssConfig(); config.setBucket("test-bucket");
        OSSObject object = spy(new OSSObject());
        object.setObjectContent(new ByteArrayInputStream(new byte[]{1}));
        when(client.getObject("test-bucket", "key")).thenReturn(object);
        try (InputStream stream = new OssService(client, config).download("key")) {
            assertEquals(1, stream.read());
        }
        verify(object).close();
    }

    @Test
    void failedDeletionIsReportedInsteadOfSilentlyDeletingMetadata() {
        OSS client = mock(OSS.class);
        OssConfig config = new OssConfig(); config.setBucket("test-bucket");
        doThrow(new RuntimeException("sensitive SDK diagnostics")).when(client).deleteObject("test-bucket", "key");
        ServiceException error = assertThrows(ServiceException.class, () -> new OssService(client, config).delete("key"));
        assertFalse(error.getMessage().contains("sensitive"));
    }
}
