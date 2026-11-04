package com.pcba.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.common.service.OssService;
import com.pcba.erp.pmm.entity.PmmWorkOrder;
import com.pcba.erp.pmm.mapper.PmmWorkOrderMapper;
import com.pcba.erp.pmm.service.WorkInstructionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkInstructionUploadTest {
    @Test
    void imageUploadRejectsFakeImageAndStoresVerifiedPng() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        OssService oss = mock(OssService.class);
        PmmWorkOrderMapper orders = mock(PmmWorkOrderMapper.class);
        when(orders.selectById(7L)).thenReturn(new PmmWorkOrder());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(), any(), any(), any(), any(), any(), any())).thenReturn(123L);
        WorkInstructionService service = new WorkInstructionService(jdbc, new ObjectMapper(), oss, orders);
        var fake = new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());
        assertThrows(ServiceException.class, () -> service.upload(7L, "IMAGE", fake));
        verify(oss, never()).upload(any(), anyString());
        byte[] bytes = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/a1sAAAAASUVORK5CYII=");
        var png = new MockMultipartFile("file", "part.png", "image/png", bytes);
        assertEquals(123L, service.upload(7L, "IMAGE", png).get("attachmentId"));
        verify(oss).upload(eq(png), startsWith("pm/work-instruction/7/"));
    }
}
