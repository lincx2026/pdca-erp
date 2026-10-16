package com.pcba.erp.pmm.service;

import com.pcba.erp.pmm.entity.HrmEmployeeAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface IHrmEmployeeAttachmentService {

    HrmEmployeeAttachment upload(Long employeeId, String attachType, MultipartFile file, String remark);

    List<HrmEmployeeAttachment> listByEmployeeId(Long employeeId);

    HrmEmployeeAttachment getById(Long attachmentId);

    InputStream getDownloadStream(Long attachmentId);

    boolean remove(Long attachmentId);
}
