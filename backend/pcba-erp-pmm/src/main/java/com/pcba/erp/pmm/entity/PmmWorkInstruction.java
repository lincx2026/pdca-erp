package com.pcba.erp.pmm.entity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PmmWorkInstruction {
    private Integer version = 0;
    @Size(max=100) private String brandName;
    @Size(max=100) private String brandModel;
    @Size(max=100) private String deviceModel;
    @Size(max=100) private String deviceType;
    @Size(max=200) private String bluetoothName;
    @Size(max=2000) private String productionNote;
    @Size(max=1000) private String firmwareDescription;
    private Long productImageId;
    private Long firmwareAttachmentId;
    @Valid @Size(max=200) private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private Long materialId;
        @Size(max=100) private String code;
        @Size(max=200) private String name;
        @Size(max=1000) private String description;
        private BigDecimal quantity;
        @Size(max=20) private String unit;
        private Long imageId;
    }
}
