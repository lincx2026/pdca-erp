package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.PmProjectAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface IPmProjectAttachmentService {

    /** 上传附件: 存 OSS + 写元数据 */
    PmProjectAttachment upload(Long projectId, MultipartFile file, String remark);

    /** 按项目查询附件列表 */
    List<PmProjectAttachment> listByProjectId(Long projectId);

    /** 获取附件元数据 */
    PmProjectAttachment getById(Long attachmentId);

    /** 获取下载流 (调用方负责关闭) */
    InputStream getDownloadStream(Long attachmentId);

    /** 删除附件: 删 OSS + 删 DB */
    boolean remove(Long attachmentId);
}
