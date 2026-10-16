package com.pcba.erp.pmm.controller;

import com.pcba.erp.common.annotation.OperLog;
import com.pcba.erp.common.core.R;
import com.pcba.erp.pmm.entity.HrmEmployeeAttachment;
import com.pcba.erp.pmm.service.IHrmEmployeeAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Tag(name = "员工附件")
@RestController
@RequestMapping("/hrm/employee/attachment")
@RequiredArgsConstructor
public class HrmEmployeeAttachmentController {

    private final IHrmEmployeeAttachmentService service;

    @Operation(summary = "上传员工附件")
    @PreAuthorize("@ps.hasPermi('hrm:employee:save')")
    @OperLog(title = "员工附件", businessType = 1)
    @PostMapping("/upload")
    public R<HrmEmployeeAttachment> upload(@RequestParam Long employeeId,
                                           @RequestParam(required = false) String attachType,
                                           @RequestParam MultipartFile file,
                                           @RequestParam(required = false) String remark) {
        return R.ok(service.upload(employeeId, attachType, file, remark));
    }

    @Operation(summary = "查询员工附件列表")
    @PreAuthorize("@ps.hasPermi('hrm:employee:list')")
    @GetMapping("/list/{employeeId}")
    public R<List<HrmEmployeeAttachment>> list(@PathVariable Long employeeId) {
        return R.ok(service.listByEmployeeId(employeeId));
    }

    @Operation(summary = "下载员工附件")
    @PreAuthorize("@ps.hasPermi('hrm:employee:list')")
    @GetMapping("/download/{attachmentId}")
    public void download(@PathVariable Long attachmentId, HttpServletResponse response) {
        HrmEmployeeAttachment att = service.getById(attachmentId);
        response.setContentType(att.getContentType() != null ? att.getContentType() : "application/octet-stream");
        String encodedName = URLEncoder.encode(att.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        if (att.getFileSize() != null) {
            response.setContentLengthLong(att.getFileSize());
        }
        try (InputStream is = service.getDownloadStream(attachmentId);
             OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
        } catch (Exception e) {
            log.error("[员工附件] 下载失败 attachmentId={}: {}", attachmentId, e.getMessage(), e);
        }
    }

    @Operation(summary = "删除员工附件")
    @PreAuthorize("@ps.hasPermi('hrm:employee:remove')")
    @OperLog(title = "员工附件", businessType = 3)
    @DeleteMapping("/{attachmentId}")
    public R<Integer> remove(@PathVariable Long attachmentId) {
        return service.remove(attachmentId) ? R.ok(1) : R.fail("删除失败");
    }
}
