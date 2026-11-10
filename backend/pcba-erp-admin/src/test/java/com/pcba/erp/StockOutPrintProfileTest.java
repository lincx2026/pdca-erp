package com.pcba.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcba.erp.pmm.controller.CompanyProfileController;
import com.pcba.erp.pmm.entity.CompanyProfile;
import com.pcba.erp.pmm.service.IPmmCompanyProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockOutPrintProfileTest {
    @Test
    void stockOutPrintProfileExposesOnlyPrintFieldsAndRequiresStockOutAccess() throws Exception {
        IPmmCompanyProfileService service = mock(IPmmCompanyProfileService.class);
        CompanyProfile profile = new CompanyProfile();
        profile.setCompanyName("Example");
        profile.setAddress("Address");
        profile.setContactPhone("Phone");
        profile.setBankAccount("private");
        profile.setTaxNo("private");
        when(service.get()).thenReturn(profile);
        var result = new CompanyProfileController(service).stockOutPrint().getData();
        var json = new ObjectMapper().valueToTree(result);
        assertEquals("Example", json.get("companyName").asText());
        assertEquals("Address", json.get("address").asText());
        assertEquals("Phone", json.get("contactPhone").asText());
        assertEquals(3, json.size());
        assertFalse(json.has("bankAccount"));
        assertEquals("@ps.hasPermi('wms:stockout:list')", CompanyProfileController.class.getMethod("stockOutPrint").getAnnotation(PreAuthorize.class).value());
    }
}
