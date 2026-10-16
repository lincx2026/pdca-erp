package com.pcba.erp.pmm.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.service.OssService;
import com.pcba.erp.pmm.entity.HrmEmployee;
import com.pcba.erp.pmm.entity.HrmEmployeeAttachment;
import com.pcba.erp.pmm.mapper.HrmEmployeeAttachmentMapper;
import com.pcba.erp.pmm.mapper.HrmEmployeeMapper;
import com.pcba.erp.pmm.service.IHrmEmployeeAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HrmEmployeeAttachmentServiceImpl implements IHrmEmployeeAttachmentService {

    private final HrmEmployeeAttachmentMapper attachmentMapper;
    private final HrmEmployeeMapper employeeMapper;
    private final OssService ossService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HrmEmployeeAttachment upload(Long employeeId, String attachType, MultipartFile file, String remark) {
        if (employeeId == null) throw new ServiceException("员工ID不能为空");
        if (file == null || file.isEmpty()) throw new ServiceException("上传文件不能为空");

        HrmEmployee emp = employeeMapper.selectById(employeeId);
        if (emp == null) throw new ServiceException("员工不存在");

        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "file";
        String objectKey = String.format("hrm/employee/%d/%s/%s_%s", employeeId, ym, uuid, originalName);

        ossService.upload(file, objectKey);

        HrmEmployeeAttachment att = new HrmEmployeeAttachment();
        att.setEmployeeId(employeeId);
        att.setAttachType(attachType);
        att.setFileName(originalName);
        att.setObjectKey(objectKey);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        att.setRemark(remark);
        attachmentMapper.insert(att);

        log.info("[员工附件] 上传成功 employeeId={}, attachmentId={}, file={}", employeeId, att.getAttachmentId(), originalName);
        return att;
    }

    @Override
    public List<HrmEmployeeAttachment> listByEmployeeId(Long employeeId) {
        if (employeeId == null) throw new ServiceException("员工ID不能为空");
        return attachmentMapper.selectByEmployeeId(employeeId);
    }

    @Override
    public HrmEmployeeAttachment getById(Long attachmentId) {
        if (attachmentId == null) throw new ServiceException("附件ID不能为空");
        HrmEmployeeAttachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) throw new ServiceException("附件不存在");
        return att;
    }

    @Override
    public InputStream getDownloadStream(Long attachmentId) {
        HrmEmployeeAttachment att = getById(attachmentId);
        return ossService.download(att.getObjectKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long attachmentId) {
        HrmEmployeeAttachment att = getById(attachmentId);
        ossService.delete(att.getObjectKey());
        return attachmentMapper.deleteById(attachmentId) > 0;
    }
}
