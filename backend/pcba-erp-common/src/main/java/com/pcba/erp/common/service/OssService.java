package com.pcba.erp.common.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.pcba.erp.common.config.OssConfig;
import com.pcba.erp.common.exception.ServiceException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/** OSS bucket 由基础设施预先创建；应用只访问对象，不修改 bucket 权限。 */
@Service
public class OssService {
    private final OSS client;
    private final OssConfig config;

    public OssService(@Lazy OSS client, OssConfig config) {
        this.client = client;
        this.config = config;
    }

    public String upload(MultipartFile file, String objectKey) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream stream = file.getInputStream()) {
            client.putObject(config.getBucket(), objectKey, stream, metadata);
            return objectKey;
        } catch (Exception ex) {
            throw new ServiceException("附件上传失败，请检查 OSS 配置和访问权限");
        }
    }

    public InputStream download(String objectKey) {
        try {
            OSSObject object = client.getObject(config.getBucket(), objectKey);
            return new FilterInputStream(object.getObjectContent()) {
                @Override
                public void close() throws IOException {
                    object.close();
                }
            };
        } catch (Exception ex) {
            throw new ServiceException("附件下载失败，请检查 OSS 配置和对象是否存在");
        }
    }

    public void delete(String objectKey) {
        try {
            client.deleteObject(config.getBucket(), objectKey);
        } catch (Exception ex) {
            throw new ServiceException("附件删除失败，请重试");
        }
    }

    public String getPresignedUrl(String objectKey) {
        return client.generatePresignedUrl(config.getBucket(), objectKey,
                new Date(System.currentTimeMillis() + 3_600_000L)).toString();
    }
}
