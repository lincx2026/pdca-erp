package com.pcba.erp.pmm.service.impl;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.service.OssService;
import com.pcba.erp.pmm.entity.PmProject;
import com.pcba.erp.pmm.entity.PmProjectAttachment;
import com.pcba.erp.pmm.mapper.PmProjectAttachmentMapper;
import com.pcba.erp.pmm.mapper.PmProjectMapper;
import com.pcba.erp.pmm.service.IPmProjectAttachmentService;
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
public class PmProjectAttachmentServiceImpl implements IPmProjectAttachmentService {

    private final PmProjectAttachmentMapper attachmentMapper;
    private final PmProjectMapper projectMapper;
    private final OssService ossService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PmProjectAttachment upload(Long projectId, MultipartFile file, String remark) {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        if (file == null || file.isEmpty()) throw new ServiceException("上传文件不能为空");

        PmProject project = projectMapper.selectById(projectId);
        if (project == null) throw new ServiceException("项目不存在");

        // 生成对象键: pm/project/{projectId}/{yyyyMM}/{uuid}_{原始文件名}
        String ym = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "file";
        String objectKey = String.format("pm/project/%d/%s/%s_%s", projectId, ym, uuid, originalName);

        // 上传到 OSS
        ossService.upload(file, objectKey);

        // 写元数据
        PmProjectAttachment att = new PmProjectAttachment();
        att.setProjectId(projectId);
        att.setFileName(originalName);
        att.setObjectKey(objectKey);
        att.setFileSize(file.getSize());
        att.setContentType(file.getContentType());
        att.setRemark(remark);
        attachmentMapper.insert(att);

        log.info("[项目附件] 上传成功 projectId={}, attachmentId={}, file={}", projectId, att.getAttachmentId(), originalName);
        return att;
    }

    @Override
    public List<PmProjectAttachment> listByProjectId(Long projectId) {
        if (projectId == null) throw new ServiceException("项目ID不能为空");
        return attachmentMapper.selectByProjectId(projectId);
    }

    @Override
    public PmProjectAttachment getById(Long attachmentId) {
        if (attachmentId == null) throw new ServiceException("附件ID不能为空");
        PmProjectAttachment att = attachmentMapper.selectById(attachmentId);
        if (att == null) throw new ServiceException("附件不存在");
        return att;
    }

    @Override
    public InputStream getDownloadStream(Long attachmentId) {
        PmProjectAttachment att = getById(attachmentId);
        return ossService.download(att.getObjectKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long attachmentId) {
        PmProjectAttachment att = getById(attachmentId);
        // Only remove metadata after OSS confirms deletion.
        ossService.delete(att.getObjectKey());
        // 再删 DB
        return attachmentMapper.deleteById(attachmentId) > 0;
    }
}
