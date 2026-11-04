package com.pcba.erp.pmm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcba.erp.common.core.utils.SecurityUtils;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.service.OssService;
import com.pcba.erp.pmm.entity.PmmWorkInstruction;
import com.pcba.erp.pmm.mapper.PmmWorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkInstructionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OssService oss;
    private final PmmWorkOrderMapper orders;

    private void requireOrder(Long id) {
        if (id == null || orders.selectById(id) == null) throw new ServiceException("工单不存在");
    }

    public PmmWorkInstruction get(Long id) {
        requireOrder(id);
        var rows = jdbc.queryForList("SELECT content::text AS content, version FROM pmm_work_instruction WHERE work_order_id=?", id);
        if (rows.isEmpty()) return new PmmWorkInstruction();
        try {
            PmmWorkInstruction result = json.readValue((String) rows.get(0).get("content"), PmmWorkInstruction.class);
            result.setVersion(((Number) rows.get(0).get("version")).intValue());
            return result;
        } catch (Exception e) { throw new ServiceException("生产指令读取失败"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Long id, PmmWorkInstruction instruction) {
        requireOrder(id);
        if (instruction == null || instruction.getVersion() == null || instruction.getVersion() < 0) throw new ServiceException("指令版本无效");
        checkAttachment(id, instruction.getProductImageId(), "IMAGE");
        checkAttachment(id, instruction.getFirmwareAttachmentId(), "FIRMWARE");
        if (instruction.getItems() != null) for (var item : instruction.getItems()) {
            if (item == null) throw new ServiceException("物料明细不能为空");
            if (item.getQuantity() != null && item.getQuantity().signum() < 0) throw new ServiceException("物料数量不能为负数");
            checkAttachment(id, item.getImageId(), "IMAGE");
        }
        String data;
        try { data = json.writeValueAsString(instruction); } catch (Exception e) { throw new ServiceException("指令数据无效"); }
        if (data.length() > 300000) throw new ServiceException("指令内容过长");
        int n;
        if (instruction.getVersion() == 0) {
            n = jdbc.update("INSERT INTO pmm_work_instruction(work_order_id,content,version,updated_by) VALUES (?,CAST(? AS jsonb),1,?) ON CONFLICT DO NOTHING", id, data, SecurityUtils.getUsernameSafe());
        } else {
            n = jdbc.update("UPDATE pmm_work_instruction SET content=CAST(? AS jsonb),version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE work_order_id=? AND version=?", data, SecurityUtils.getUsernameSafe(), id, instruction.getVersion());
        }
        if (n != 1) throw new ServiceException("指令已被其他人修改，请重新加载后再保存");
    }

    private void checkAttachment(Long orderId, Long attachmentId, String kind) {
        if (attachmentId == null) return;
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM pmm_work_instruction_attachment WHERE work_order_id=? AND attachment_id=? AND kind=?", Integer.class, orderId, attachmentId, kind);
        if (n == null || n != 1) throw new ServiceException("指令附件不存在或类型不匹配");
    }

    public List<Map<String, Object>> attachments(Long id) {
        requireOrder(id);
        return jdbc.queryForList("SELECT attachment_id AS \"attachmentId\", file_name AS \"fileName\", content_type AS \"contentType\", file_size AS \"fileSize\", kind FROM pmm_work_instruction_attachment WHERE work_order_id=? ORDER BY attachment_id DESC", id);
    }

    public Map<String, Object> upload(Long id, String kind, MultipartFile file) {
        requireOrder(id);
        if (!"IMAGE".equals(kind) && !"FIRMWARE".equals(kind)) throw new ServiceException("附件类型无效");
        if (file == null || file.isEmpty()) throw new ServiceException("请选择文件");
        long max = "IMAGE".equals(kind) ? 5L * 1024 * 1024 : 50L * 1024 * 1024;
        if (file.getSize() > max) throw new ServiceException("图片不能超过5MB，固件不能超过50MB");
        String contentType = "application/octet-stream";
        if ("IMAGE".equals(kind)) {
            try (InputStream in = file.getInputStream()) {
                byte[] h = in.readNBytes(8);
                boolean png = h.length == 8 && java.util.Arrays.equals(h, new byte[]{(byte)137,80,78,71,13,10,26,10});
                boolean jpeg = h.length >= 3 && (h[0] & 255) == 255 && (h[1] & 255) == 216 && (h[2] & 255) == 255;
                if (!png && !jpeg) throw new ServiceException("仅支持有效的 PNG 或 JPEG 图片");
                try (InputStream imageBytes = file.getInputStream(); var imageStream = ImageIO.createImageInputStream(imageBytes)) {
                    if (imageStream == null) throw new ServiceException("图片读取失败");
                    var readers = ImageIO.getImageReaders(imageStream);
                    if (!readers.hasNext()) throw new ServiceException("图片格式无效");
                    var reader = readers.next();
                    try {
                        reader.setInput(imageStream, true, true);
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 40000000L) throw new ServiceException("图片尺寸过大");
                    } finally { reader.dispose(); }
                }
                contentType = png ? "image/png" : "image/jpeg";
            } catch (ServiceException e) { throw e; } catch (Exception e) { throw new ServiceException("图片读取失败"); }
        }
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename().replaceAll("[\\\\/\\r\\n]", "_");
        if (name.length() > 255) name = name.substring(name.length() - 255);
        String key = "pm/work-instruction/" + id + "/" + UUID.randomUUID();
        oss.upload(file, key);
        try {
            Long attachmentId = jdbc.queryForObject("INSERT INTO pmm_work_instruction_attachment(work_order_id,file_name,object_key,content_type,file_size,kind,created_by) VALUES (?,?,?,?,?,?,?) RETURNING attachment_id", Long.class, id, name, key, contentType, file.getSize(), kind, SecurityUtils.getUsernameSafe());
            return Map.of("attachmentId", attachmentId, "fileName", name, "kind", kind);
        } catch (RuntimeException e) {
            try { oss.delete(key); } catch (Exception ignored) { }
            throw e;
        }
    }

    public Map<String, Object> attachment(Long orderId, Long attachmentId) {
        requireOrder(orderId);
        var rows = jdbc.queryForList("SELECT file_name, object_key, content_type, kind FROM pmm_work_instruction_attachment WHERE work_order_id=? AND attachment_id=?", orderId, attachmentId);
        if (rows.isEmpty()) throw new ServiceException("附件不存在");
        return rows.get(0);
    }

    public InputStream open(Map<String, Object> attachment) { return oss.download((String) attachment.get("object_key")); }
}
