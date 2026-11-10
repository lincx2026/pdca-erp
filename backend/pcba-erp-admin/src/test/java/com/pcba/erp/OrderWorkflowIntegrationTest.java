package com.pcba.erp;

import com.pcba.erp.common.exception.ServiceException;
import com.pcba.erp.pdm.mapper.PdmMaterialMapper;
import com.pcba.erp.pmm.entity.PmmWorkOrder;
import com.pcba.erp.pmm.entity.PmmWorkInstruction;
import com.pcba.erp.pmm.service.IPmmWorkOrderService;
import com.pcba.erp.pmm.service.WorkInstructionService;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import com.pcba.erp.scm.entity.ScmPurchaseRequisitionLine;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionService;
import com.pcba.erp.scm.service.IScmPurchaseRequisitionLineService;
import com.pcba.erp.sal.entity.SalSalesOrder;
import com.pcba.erp.sal.service.ISalSalesOrderService;
import com.pcba.erp.wms.service.IWmsStockOutService;
import com.pcba.erp.wms.service.IWmsInventoryService;
import com.pcba.erp.wms.entity.WmsStockOut;
import com.pcba.erp.wms.entity.WmsStockOutLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "PCBA_INTEGRATION_TESTS", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:55432/pcba_erp?stringtype=unspecified",
        "spring.datasource.username=postgres", "spring.datasource.password=postgres-test-only",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=56379", "spring.data.redis.password=redis-test-only",
        "pcba.jwt.secret=postgres-integration-test-secret-at-least-32-bytes",
        "pcba.bootstrap.admin-password=integration-admin-password-only"
})
class OrderWorkflowIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sql;
    @Autowired PdmMaterialMapper materials;
    @Autowired com.pcba.erp.pdm.service.IPdmBomService boms;
    @Autowired IPmmWorkOrderService workOrders;
    @Autowired WorkInstructionService instructions;
    @Autowired com.pcba.erp.pmm.service.IPmmWorkOrderBomService workBom;
    @Autowired com.pcba.erp.sal.service.ISalKittingService kitting;
    @Autowired IScmPurchaseRequisitionService requisitions;
    @Autowired IScmPurchaseRequisitionLineService reqLines;
    @Autowired ISalSalesOrderService sales;
    @Autowired com.pcba.erp.scm.service.IScmSupplierPriceService supplierPrices;
    @Autowired com.pcba.erp.sal.service.ISalSalesOrderLineService salesLines;
    @Autowired IWmsStockOutService stockOut;
    @Autowired IWmsInventoryService inventory;

    @Test @Transactional
    void salesOrderListSummarizesOnlyCurrentPageAndGroupsQuantitiesByUnit() {
        Long older = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,currency,total_amount) VALUES ('TEST-LIST-OLDER',1,CURRENT_DATE,'CNY',7) RETURNING so_id", Long.class);
        Long mixed = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,currency,total_amount,remark) VALUES ('TEST-LIST-MIXED',1,CURRENT_DATE,'USD',123.45,'model') RETURNING so_id", Long.class);
        Long empty = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,currency) VALUES ('TEST-LIST-EMPTY',1,CURRENT_DATE,'CNY') RETURNING so_id", Long.class);
        jdbc.update("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,material_name,specification,unit,order_qty,unit_price) VALUES (?,30,1,'last','L','PCS',2,1),(?,10,2,'first','SPEC','PCS',3.5,1),(?,20,3,'weight','W','KG',4,1),(?,40,4,'unknown','',NULL,1,1),(?,10,5,'other','','PCS',9,1)", mixed,mixed,mixed,mixed,older);
        var page = sales.selectPage(1,2,"TEST-LIST-",null,null,null,null,null,null);
        assertEquals(3,page.getTotal()); assertEquals(2,page.getRecords().size());
        assertEquals(empty,page.getRecords().get(0).getSoId()); assertEquals(0,page.getRecords().get(0).getLineCount()); assertTrue(page.getRecords().get(0).getQuantityByUnit().isEmpty());
        var order = page.getRecords().get(1); assertEquals(mixed,order.getSoId()); assertEquals(4,order.getLineCount()); assertEquals("first",order.getFirstMaterialName()); assertEquals("SPEC",order.getFirstSpecification());
        assertEquals("USD",order.getCurrency()); assertEquals(0,new BigDecimal("123.45").compareTo(order.getTotalAmount()));
        assertTrue(order.getAmountMismatch()); assertTrue(order.getLineAmountsComplete()); assertEquals(0,BigDecimal.ZERO.compareTo(order.getLineAmountTotal()));
        var totals = order.getQuantityByUnit().stream().collect(java.util.stream.Collectors.toMap(SalSalesOrder.UnitQuantity::getUnit, SalSalesOrder.UnitQuantity::getQuantity));
        assertEquals(3,totals.size()); assertEquals(0,new BigDecimal("5.5").compareTo(totals.get("PCS"))); assertEquals(0,new BigDecimal("4").compareTo(totals.get("KG"))); assertEquals(0,BigDecimal.ONE.compareTo(totals.get("未填写单位")));
    }

    @Test @Transactional
    void salesOrderPrintReturnsEveryLineInOrder() {
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,currency) VALUES ('TEST-PRINT-ORDER',1,CURRENT_DATE,'USD') RETURNING so_id", Long.class);
        jdbc.update("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,material_name,unit,order_qty,unit_price) SELECT ?,n,n,'item-' || n,'PCS',1,2 FROM generate_series(1,501) n",so);
        var data = sales.printData(so);
        assertEquals(so,((SalSalesOrder)data.get("order")).getSoId());
        assertTrue(((SalSalesOrder)data.get("order")).getLineAmountsComplete());
        var lines = (List<com.pcba.erp.sal.entity.SalSalesOrderLine>)data.get("lines");
        assertEquals(501,lines.size()); assertEquals(1,lines.get(0).getLineNo()); assertEquals(501,lines.get(500).getLineNo());
        assertThrows(ServiceException.class, () -> sales.printData(-1L));
    }

    @Test @Transactional
    void missingLineAmountIsNotReportedAsAValidAmountMatch() {
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,total_amount) VALUES ('TEST-LIST-NO-AMOUNT',1,CURRENT_DATE,0) RETURNING so_id",Long.class);
        jdbc.update("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price,line_amount) VALUES (?,1,1,1,1,NULL)",so);
        SalSalesOrder listed = sales.selectPage(1,10,"TEST-LIST-NO-AMOUNT",null,null,null,null,null,null).getRecords().get(0);
        assertFalse(listed.getLineAmountsComplete()); assertNull(listed.getAmountMismatch());
        SalSalesOrder printable = (SalSalesOrder)sales.printData(so).get("order");
        assertFalse(printable.getLineAmountsComplete()); assertNull(printable.getAmountMismatch());
    }

    @Test @Transactional
    void savesSalesBomWithAComponent() {
        Long product = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-BOM-PARENT','parent',1) RETURNING material_id", Long.class);
        Long component = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-BOM-CHILD','child',1) RETURNING material_id", Long.class);
        var bom = new com.pcba.erp.pdm.entity.PdmBom();
        bom.setBomCode("TEST-SALES-BOM-SAVE"); bom.setBomName("sales kit"); bom.setVersion("V2.0"); bom.setProductMaterialId(product); bom.setBomType(3); bom.setKitModel("E27M");
        var line = new com.pcba.erp.pdm.entity.PdmBomLine(); line.setComponentMaterialId(component); line.setQuantity(BigDecimal.ONE); bom.setLines(List.of(line));
        Long id = boms.saveWithLines(bom);
        assertEquals(1, boms.getDetail(id).getLines().size());
        assertNull(boms.getDetail(id).getProductMaterialId());
        assertNull(boms.getDetail(id).getLines().get(0).getParentMaterialId());
        assertEquals(0, BigDecimal.ONE.compareTo(boms.explodeFlatSum(id,10,true).get(component).getPathQty()));
        var duplicate = new com.pcba.erp.pdm.entity.PdmBom(); duplicate.setBomCode("TEST-SALES-BOM-DUP"); duplicate.setBomName("duplicate"); duplicate.setVersion("v2.0"); duplicate.setBomType(3); duplicate.setKitModel("e27m");
        assertThrows(ServiceException.class, () -> boms.saveWithLines(duplicate));
    }

    @Test @Transactional
    void manufacturingBomRetainsProductAndParentAndExplodes() {
        Long product = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-MFG-PARENT','parent',1) RETURNING material_id", Long.class);
        Long component = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-MFG-CHILD','child',1) RETURNING material_id", Long.class);
        var bom = new com.pcba.erp.pdm.entity.PdmBom(); bom.setBomCode("TEST-MFG-BOM"); bom.setBomName("manufacturing"); bom.setVersion("V1"); bom.setProductMaterialId(product); bom.setBomType(1);
        var line = new com.pcba.erp.pdm.entity.PdmBomLine(); line.setComponentMaterialId(component); line.setQuantity(new BigDecimal("2")); bom.setLines(List.of(line));
        Long id = boms.saveWithLines(bom);
        assertEquals(product, boms.getDetail(id).getLines().get(0).getParentMaterialId());
        assertEquals(0, new BigDecimal("2").compareTo(boms.explodeFlatSum(id,10,true).get(component).getPathQty()));
    }

    @Test @Transactional
    void flatSalesBomKittingLinksEachChildToItsOwnOrderLine() {
        Long a = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-FLAT-A','A',1) RETURNING material_id", Long.class);
        Long b = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-FLAT-B','B',1) RETURNING material_id", Long.class);
        var bom = new com.pcba.erp.pdm.entity.PdmBom(); bom.setBomCode("TEST-FLAT-BOM"); bom.setBomName("flat"); bom.setVersion("V1"); bom.setBomType(3); bom.setCustomerId(1L); bom.setKitModel("E27M"); bom.setQuantity(new BigDecimal("2"));
        var la = new com.pcba.erp.pdm.entity.PdmBomLine(); la.setComponentMaterialId(a); la.setQuantity(new BigDecimal("4"));
        var lb = new com.pcba.erp.pdm.entity.PdmBomLine(); lb.setComponentMaterialId(b); lb.setQuantity(new BigDecimal("2")); bom.setLines(List.of(la,lb));
        Long bomId = boms.saveWithLines(bom); boms.submitAudit(bomId); boms.auditPass(bomId,"tester");
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,customer_name,so_date,status,warehouse_id) VALUES ('TEST-FLAT-SO',1,'customer',CURRENT_DATE,2,90001) RETURNING so_id", Long.class);
        Long lineA = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,10,?,10,1) RETURNING line_id", Long.class,so,a);
        Long lineB = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,20,?,5,1) RETURNING line_id", Long.class,so,b);
        inventory.increaseInventory(90001L,a,"",new BigDecimal("30"),"PCS"); inventory.increaseInventory(90001L,b,"",new BigDecimal("30"),"PCS");
        assertEquals(1,kitting.bomOptions(so,null,"E27M").size());
        var k = new com.pcba.erp.sal.entity.SalKitting(); k.setSoId(so); k.setBomId(bomId); k.setKitQty(new BigDecimal("5")); k.setWarehouseId(90001L);
        Long kit = kitting.save(k); assertTrue(kitting.runKittingCheck(kit));
        Long out = kitting.generateStockOut(kit);
        var outLines = stockOut.getDetail(out).getLines();
        assertEquals(2,outLines.size());
        assertTrue(outLines.stream().anyMatch(l -> lineA.equals(l.getSoLineId()) && new BigDecimal("10").compareTo(l.getQuantity())==0));
        assertTrue(outLines.stream().anyMatch(l -> lineB.equals(l.getSoLineId()) && new BigDecimal("5").compareTo(l.getQuantity())==0));
        assertEquals(0,new BigDecimal("10").compareTo(jdbc.queryForObject("SELECT shipped_qty FROM sal_sales_order_line WHERE line_id=?",BigDecimal.class,lineA)));
        assertThrows(ServiceException.class, () -> kitting.generateStockOut(kit));
    }

    @Test @Transactional
    void flatSalesBomRejectsDuplicateComponentsAndEmptyApproval() {
        Long component = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-FLAT-DUP','child',1) RETURNING material_id", Long.class);
        var bom = new com.pcba.erp.pdm.entity.PdmBom(); bom.setBomCode("TEST-FLAT-EMPTY"); bom.setBomName("empty"); bom.setVersion("V1"); bom.setBomType(3); bom.setKitModel("EMPTY");
        Long id = boms.saveWithLines(bom);
        assertThrows(ServiceException.class, () -> boms.submitAudit(id));
        var first = new com.pcba.erp.pdm.entity.PdmBomLine(); first.setComponentMaterialId(component); first.setQuantity(BigDecimal.ONE);
        var second = new com.pcba.erp.pdm.entity.PdmBomLine(); second.setComponentMaterialId(component); second.setQuantity(BigDecimal.ONE);
        assertThrows(ServiceException.class, () -> boms.replaceLines(id,List.of(first,second)));
    }

    @Test @Transactional
    void salesKitDoesNotCreateShipmentForMissingOrAmbiguousOrderLine() {
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,so_date,status) VALUES ('TEST-KIT-MAP-SO',1,CURRENT_DATE,2) RETURNING so_id", Long.class);
        Long kit = jdbc.queryForObject("INSERT INTO sal_kitting(kitting_no,so_id,bom_id,status,warehouse_id) VALUES ('TEST-KIT-MAP',?,999999,2,90001) RETURNING kitting_id", Long.class,so);
        jdbc.update("INSERT INTO sal_kitting_line(kitting_id,material_id,material_code,req_qty,is_shortage) VALUES (?,991234,'TEST-MAP',1,0)",kit);
        assertThrows(ServiceException.class, () -> kitting.generateStockOut(kit));
        jdbc.update("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,10,991234,2,1),(?,20,991234,2,1)",so,so);
        assertThrows(ServiceException.class, () -> kitting.generateStockOut(kit));
        assertNull(jdbc.queryForObject("SELECT stock_out_id FROM sal_kitting WHERE kitting_id=?",Long.class,kit));
    }

    @Test @Transactional
    void workBomAllocatesLinesAndProtectsIssuedQuantity() {
        PmmWorkOrder order = new PmmWorkOrder(); order.setMaterialId(123L); order.setOrderQty(BigDecimal.TEN);
        Long id = workOrders.save(order);
        var first = new com.pcba.erp.pmm.entity.PmmWorkOrderBom(); first.setWorkOrderId(id); first.setMaterialId(123L); first.setQtyPerUnit(new BigDecimal("2"));
        Long firstId = workBom.save(first);
        var second = new com.pcba.erp.pmm.entity.PmmWorkOrderBom(); second.setWorkOrderId(id); second.setMaterialId(123L); second.setQtyPerUnit(BigDecimal.ONE);
        workBom.save(second);
        assertEquals(1, first.getLineNo()); assertEquals(2, second.getLineNo());
        assertEquals(0, new BigDecimal("20").compareTo(workBom.getDetail(firstId).getRequiredQty()));
        var duplicate = new com.pcba.erp.pmm.entity.PmmWorkOrderBom(); duplicate.setWorkOrderId(id); duplicate.setMaterialId(123L); duplicate.setQtyPerUnit(BigDecimal.ONE); duplicate.setLineNo(1);
        assertThrows(ServiceException.class, () -> workBom.save(duplicate));
        var update = new com.pcba.erp.pmm.entity.PmmWorkOrderBom(); update.setBomLineId(firstId); update.setIssuedQty(BigDecimal.TEN);
        workBom.update(update); assertEquals(0, BigDecimal.ZERO.compareTo(workBom.getDetail(firstId).getIssuedQty()));
    }

    @Test @Transactional
    void instructionTemplateCreatesIndependentSnapshotAndAttachmentReferences() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-TEMPLATE-MODEL','model',1) RETURNING material_id", Long.class);
        PmmWorkOrder a = new PmmWorkOrder(); a.setMaterialId(material); a.setOrderQty(BigDecimal.ONE); Long first = workOrders.save(a);
        Long attachment = jdbc.queryForObject("INSERT INTO pmm_work_instruction_attachment(work_order_id,file_name,object_key,content_type,file_size,kind) VALUES (?,'test.png','test-template-key','image/png',10,'IMAGE') RETURNING attachment_id", Long.class, first);
        PmmWorkInstruction instruction = new PmmWorkInstruction(); instruction.setBluetoothName("original"); instruction.setProductImageId(attachment); instructions.save(first, instruction);
        Long template = instructions.publishTemplate(first, "model template");
        instruction.setVersion(1); instruction.setBluetoothName("changed"); instructions.save(first, instruction);
        PmmWorkOrder b = new PmmWorkOrder(); b.setMaterialId(material); b.setOrderQty(BigDecimal.ONE); Long second = workOrders.save(b);
        instructions.applyTemplate(second, template);
        var copied = instructions.get(second);
        assertEquals("original", copied.getBluetoothName()); assertNotEquals(attachment, copied.getProductImageId());
        assertEquals("test-template-key", instructions.attachment(second, copied.getProductImageId()).get("object_key"));
        PmmWorkOrder other = new PmmWorkOrder(); other.setMaterialId(123L); other.setOrderQty(BigDecimal.ONE); Long third = workOrders.save(other);
        assertThrows(ServiceException.class, () -> instructions.applyTemplate(third, template));
    }

    @Test @Transactional
    void batchSalesPriceAndCopyUseIndependentAmountsAndStatus() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-SALES-QUOTE','model',1) RETURNING material_id", Long.class);
        Long price = jdbc.queryForObject("INSERT INTO sal_customer_price(customer_id,material_id,unit_price,tax_rate,min_qty,valid_from,valid_to,status,currency) VALUES (1,?,113,13,1,CURRENT_DATE-1,CURRENT_DATE+1,2,'CNY') RETURNING price_id", Long.class, material);
        SalSalesOrder order = new SalSalesOrder(); order.setCustomerId(1L); order.setCustomerOrderNo("OLD-PO"); Long original = sales.save(order);
        var line = new com.pcba.erp.sal.entity.SalSalesOrderLine(); line.setSoId(original); line.setMaterialId(material); line.setOrderQty(BigDecimal.TEN); line.setUnitPrice(new BigDecimal("100")); line.setPriceSource("QUOTE"); line.setCustomerPriceId(price);
        Long lineId = salesLines.saveBatch(List.of(line)).get(0);
        assertEquals(0, new BigDecimal("1130").compareTo(salesLines.getDetail(lineId).getLineAmount()));
        Long copy = sales.copy(original, false, null, null);
        assertEquals(0, sales.getDetail(copy).getStatus()); assertNull(sales.getDetail(copy).getCustomerOrderNo());
        var copied = salesLines.selectPage(1,10,copy,null,null).getRecords().get(0);
        assertNotEquals(lineId, copied.getLineId()); assertEquals(0, BigDecimal.ZERO.compareTo(copied.getShippedQty()));
        assertEquals(0, new BigDecimal("1130").compareTo(sales.getDetail(copy).getTotalAmount()));
        line.setUnitPrice(new BigDecimal("1"));
        assertThrows(ServiceException.class, () -> salesLines.saveBatch(List.of(line)));
    }

    @Test @Transactional
    void salesKittingOnlyOffersEnabledBomsForOrderMaterialAndCustomer() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id) VALUES ('TEST-KIT-MAT','model',1) RETURNING material_id", Long.class);
        SalSalesOrder order = new SalSalesOrder(); order.setCustomerId(1L); Long so = sales.save(order);
        Long line = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,1,?,1,1) RETURNING line_id", Long.class, so, material);
        Long bom = jdbc.queryForObject("INSERT INTO pdm_bom(bom_code,bom_name,product_material_id,bom_type,status,customer_id) VALUES ('TEST-KIT-BOM','test',?,3,2,1) RETURNING bom_id", Long.class, material);
        assertEquals(1, kitting.bomOptions(so, line, "TEST-KIT").size());
        var doc = new com.pcba.erp.sal.entity.SalKitting(); doc.setSoId(so); doc.setSoLineId(line); doc.setBomId(bom); doc.setKitQty(BigDecimal.ONE);
        Long created = kitting.save(doc); assertEquals(1L, kitting.getDetail(created).getCustomerId());
        jdbc.update("UPDATE pdm_bom SET status=3 WHERE bom_id=?", bom);
        assertTrue(kitting.bomOptions(so, line, "").isEmpty());
        assertThrows(ServiceException.class, () -> kitting.save(doc));
    }

    @Test @Transactional
    void materialDuplicateCheckWorksForNewAndExistingMaterials() {
        Long id = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id) VALUES ('TEST-WORKFLOW-MATERIAL', 'test', 1) RETURNING material_id", Long.class);
        assertEquals(1, materials.countDuplicateCode("TEST-WORKFLOW-MATERIAL", null));
        assertEquals(0, materials.countDuplicateCode("TEST-WORKFLOW-MATERIAL", id));
        assertEquals(1, materials.countDuplicateCode("TEST-WORKFLOW-MATERIAL", id + 1));
    }

    @Test @Transactional
    void workOrderGetsANumberAndRejectsMismatchedSalesLine() {
        PmmWorkOrder order = new PmmWorkOrder();
        order.setMaterialId(123L); order.setOrderQty(BigDecimal.TEN);
        Long id = workOrders.save(order);
        assertNotNull(id);
        assertTrue(workOrders.getDetail(id).getOrderNo().startsWith("WO-"));
        PmmWorkOrder invalid = new PmmWorkOrder();
        invalid.setMaterialId(123L); invalid.setOrderQty(BigDecimal.ONE);
        invalid.setSaleOrderId(999999L); invalid.setSaleOrderLineId(999999L);
        assertThrows(ServiceException.class, () -> workOrders.save(invalid));
    }

    @Test @Transactional
    void workOrderInheritsCustomerOrderAndFactoryFromValidatedReferences() {
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no, customer_id, customer_name, customer_order_no, so_date, status) VALUES ('TEST-WO-SO',1,'test customer','CLIENT-PO-TEST',CURRENT_DATE,2) RETURNING so_id", Long.class);
        Long line = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,1,123,10,1) RETURNING line_id", Long.class, so);
        Long factory = jdbc.queryForObject("INSERT INTO company_factory(parent_id,factory_code,factory_name,node_type,status) VALUES (0,'TEST-WO-FACTORY','test factory','FACTORY',0) RETURNING factory_id", Long.class);
        PmmWorkOrder order = new PmmWorkOrder(); order.setMaterialId(123L); order.setOrderQty(BigDecimal.TEN);
        order.setSaleOrderId(so); order.setSaleOrderLineId(line); order.setFactoryId(factory);
        PmmWorkOrder saved = workOrders.getDetail(workOrders.save(order));
        assertEquals("CLIENT-PO-TEST", saved.getCustomerOrderNo());
        assertEquals("test factory", saved.getFactoryName());
        assertEquals(1, workOrders.selectPage(1, 10, null, null, null, null, null, null, "CLIENT-PO-TEST").getTotal());
    }

    @Test @Transactional
    void requisitionApprovalSynchronizesLinesAndCopyIsIndependent() {
        Long materialId = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id) VALUES ('TEST-REQ-APPROVAL', 'test', 1) RETURNING material_id", Long.class);
        ScmPurchaseRequisition req = new ScmPurchaseRequisition(); req.setTitle("workflow test");
        Long id = requisitions.save(req);
        ScmPurchaseRequisitionLine line = new ScmPurchaseRequisitionLine();
        line.setRequisitionId(id); line.setMaterialId(materialId); line.setQty(BigDecimal.TEN);
        Long lineId = reqLines.save(line);
        assertThrows(ServiceException.class, () -> requisitions.changeStatus(id, 2));
        requisitions.changeStatus(id, 1);
        assertEquals(1, reqLines.getDetail(lineId).getStatus());
        assertThrows(ServiceException.class, () -> reqLines.remove(lineId));
        requisitions.changeStatus(id, 2);
        assertEquals(2, reqLines.getDetail(lineId).getStatus());
        Long copyId = requisitions.copy(id);
        assertNotEquals(id, copyId);
        assertEquals(0, requisitions.getDetail(copyId).getStatus());
        var copy = reqLines.selectPage(1, 10, copyId, null, null).getRecords().get(0);
        assertEquals(0, copy.getStatus());
        assertNull(copy.getConvertedPoId());
        assertNotEquals(lineId, copy.getLineId());
        assertEquals(2, reqLines.getDetail(lineId).getStatus());
    }

    @Test @Transactional
    void salesOrderNumberIsShortAndCustomerReferencePersists() {
        SalSalesOrder order = new SalSalesOrder();
        order.setCustomerId(1L); order.setCustomerOrderNo("CUSTOMER-PO-42"); order.setSoDate(LocalDate.now());
        Long id = sales.save(order);
        SalSalesOrder saved = sales.getDetail(id);
        assertTrue(saved.getSoNo().matches("SO\\d{10,}"));
        assertEquals("CUSTOMER-PO-42", saved.getCustomerOrderNo());
    }

    @Test @Transactional
    void requisitionConvertsRemainingQuantityAndMissingSupplierCanBeRetried() {
        Long materialId = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id, reference_price, search_keywords) VALUES ('TEST-REQ-CONVERT', 'test', 1, 1.7, 'E27M-A1') RETURNING material_id", Long.class);
        Long supplierId = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code, supplier_name) VALUES ('TEST-REQ-SUPPLIER', 'test supplier') RETURNING supplier_id", Long.class);
        ScmPurchaseRequisition req = new ScmPurchaseRequisition(); req.setTitle("conversion test");
        Long id = requisitions.save(req);
        ScmPurchaseRequisitionLine line = new ScmPurchaseRequisitionLine();
        line.setRequisitionId(id); line.setMaterialId(materialId); line.setQty(BigDecimal.TEN);
        Long lineId = reqLines.save(line);
        assertEquals("REFERENCE", reqLines.getDetail(lineId).getEstimateSource());
        assertEquals(0, new BigDecimal("17").compareTo(requisitions.getDetail(id).getTotalAmount()));
        requisitions.changeStatus(id, 1); requisitions.changeStatus(id, 2);
        assertThrows(ServiceException.class, () -> requisitions.convertToPurchaseOrder(List.of(lineId), Map.of()));
        assertEquals(2, requisitions.getDetail(id).getStatus());
        jdbc.update("INSERT INTO scm_supplier_price(material_id, supplier_id, unit_price, min_qty, valid_from, valid_to, is_default, status) VALUES (?, ?, 2.2, 1, CURRENT_DATE-1, CURRENT_DATE+1, 1, 2)", materialId, supplierId);
        assertEquals("QUOTE", reqLines.estimate(materialId, null, BigDecimal.TEN).getEstimateSource());
        assertThrows(ServiceException.class, () -> requisitions.convertToPurchaseOrder(List.of(lineId), Map.of("expectedPrices", Map.of(lineId.toString(), Map.of("priceId", 99999, "unitPrice", 2.2)))));
        assertEquals(0, BigDecimal.ZERO.compareTo(reqLines.getDetail(lineId).getConvertedQty()));
        var partial = requisitions.convertToPurchaseOrder(List.of(lineId), Map.of("qtyOverrides", Map.of(lineId.toString(), 4)));
        assertEquals(1, ((List<?>) partial.get("poIds")).size());
        assertEquals(0, new BigDecimal("4").compareTo(reqLines.getDetail(lineId).getConvertedQty()));
        var rest = requisitions.convertToPurchaseOrder(List.of(lineId), Map.of());
        assertEquals(1, ((List<?>) rest.get("poIds")).size());
        assertNotEquals(((List<?>) partial.get("poIds")).get(0), ((List<?>) rest.get("poIds")).get(0));
        assertEquals(0, BigDecimal.TEN.compareTo(reqLines.getDetail(lineId).getConvertedQty()));
        assertEquals(3, requisitions.getDetail(id).getStatus());
    }

    @Test @Transactional
    void approvedUniqueSupplierQuoteIsUsedWithoutDefaultAndConvertKeepsPreviewPrice() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id,reference_price) VALUES ('TEST-QUOTE-MATCH','cable',1,2.2) RETURNING material_id", Long.class);
        Long supplier = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code,supplier_name) VALUES ('TEST-QUOTE-MATCH-SUP','supplier') RETURNING supplier_id", Long.class);
        ScmPurchaseRequisition req = new ScmPurchaseRequisition(); req.setTitle("quote match");
        Long reqId = requisitions.save(req);
        ScmPurchaseRequisitionLine line = new ScmPurchaseRequisitionLine(); line.setRequisitionId(reqId); line.setMaterialId(material); line.setQty(new BigDecimal("100"));
        Long lineId = reqLines.save(line);
        requisitions.changeStatus(reqId, 1); requisitions.changeStatus(reqId, 2);
        assertNull(reqLines.getDetail(lineId).getSupplierId());
        Long price = jdbc.queryForObject("INSERT INTO scm_supplier_price(material_id,supplier_id,unit_price,min_qty,valid_from,valid_to,is_default,status,currency) VALUES (?,?,2.2,1,CURRENT_DATE-1,CURRENT_DATE+1,0,2,'CNY') RETURNING price_id", Long.class, material, supplier);
        var estimate = reqLines.estimate(material, null, new BigDecimal("100"));
        assertEquals("QUOTE", estimate.getEstimateSource());
        assertEquals(price, estimate.getSupplierPriceId());
        assertEquals(supplier, estimate.getSupplierId());
        assertThrows(ServiceException.class, () -> requisitions.convertToPurchaseOrder(List.of(lineId), Map.of("expectedPrices", Map.of(lineId.toString(), Map.of("priceId", price, "unitPrice", 2.2, "supplierId", supplier + 1)))));
        assertThrows(ServiceException.class, () -> requisitions.convertToPurchaseOrder(List.of(lineId), Map.of("expectedPrices", Map.of(lineId.toString(), Map.of("priceId", price, "unitPrice", 2.2, "qty", 99)))));
        var result = requisitions.convertToPurchaseOrder(List.of(lineId), Map.of("expectedPrices", Map.of(lineId.toString(), Map.of("priceId", price, "unitPrice", 2.2))));
        assertEquals(1, ((List<?>) result.get("poIds")).size());
    }

    @Test @Transactional
    void quoteMatchingRespectsSupplierTierCurrencyAndApproval() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id,reference_price) VALUES ('TEST-QUOTE-RULES','cable',1,9) RETURNING material_id", Long.class);
        Long first = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code,supplier_name) VALUES ('TEST-QUOTE-RULES-1','one') RETURNING supplier_id", Long.class);
        Long second = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code,supplier_name) VALUES ('TEST-QUOTE-RULES-2','two') RETURNING supplier_id", Long.class);
        jdbc.update("INSERT INTO scm_supplier_price(material_id,supplier_id,unit_price,min_qty,valid_from,valid_to,is_default,status,currency) VALUES (?,?,3,1,CURRENT_DATE-1,CURRENT_DATE+1,1,2,'CNY'),(?,?,2,100,CURRENT_DATE-1,CURRENT_DATE+1,0,2,'CNY'),(?,?,4,1,CURRENT_DATE-1,CURRENT_DATE+1,0,2,'CNY')", material,first,material,first,material,second);
        var estimate = reqLines.estimate(material, null, new BigDecimal("100"), "CNY", false);
        assertEquals(first, estimate.getSupplierId());
        assertEquals(0, new BigDecimal("2").compareTo(estimate.getEstimatePrice()));
        jdbc.update("UPDATE scm_supplier_price SET is_default=0 WHERE material_id=?", material);
        var ambiguous = reqLines.estimate(material, null, new BigDecimal("100"), "CNY", false);
        assertEquals("REFERENCE", ambiguous.getEstimateSource());
        assertTrue(ambiguous.getErrorMsg().contains("多个可用供应商"));
        assertEquals(second, reqLines.estimate(material, second, BigDecimal.ONE, "CNY", false).getSupplierId());
        assertTrue(reqLines.estimate(material, second, BigDecimal.ONE, "USD", false).getErrorMsg().contains("币种"));
        jdbc.update("UPDATE scm_supplier_price SET status=1 WHERE material_id=?", material);
        assertTrue(reqLines.estimate(material, first, BigDecimal.ONE, "CNY", false).getErrorMsg().contains("尚未审核"));
        jdbc.update("UPDATE scm_supplier_price SET status=2,valid_to=CURRENT_DATE-1,valid_from=CURRENT_DATE-2 WHERE material_id=?", material);
        assertTrue(reqLines.estimate(material, first, BigDecimal.ONE, "CNY", false).getErrorMsg().contains("有效期"));
        jdbc.update("UPDATE scm_supplier_price SET valid_to=CURRENT_DATE+1,min_qty=100 WHERE material_id=?", material);
        assertTrue(reqLines.estimate(material, first, BigDecimal.ONE, "CNY", false).getErrorMsg().contains("最小订购量"));
    }

    @Test @Transactional
    void referencePriceNeverBecomesAnApprovedSupplierQuote() {
        Long material = jdbc.queryForObject("INSERT INTO pdm_material(material_code,material_name,category_id,reference_price) VALUES ('TEST-ONLY-REFERENCE','cable',1,2.2) RETURNING material_id", Long.class);
        var estimate = reqLines.estimate(material, null, BigDecimal.TEN, "CNY", false);
        assertEquals("REFERENCE", estimate.getEstimateSource());
        assertNull(estimate.getSupplierPriceId());
        assertTrue(estimate.getErrorMsg().contains("参考单价不能直接转单"));
    }

    @Test @Transactional
    void keywordSearchAndPriceSnapshotsRespectQuantityAndMissingPrices() {
        Long materialId = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id, reference_price, search_keywords) VALUES ('TEST-KEYWORD', 'test cable', 1, 1.7, 'E27M自发电-A1') RETURNING material_id", Long.class);
        Long supplierId = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code, supplier_name) VALUES ('TEST-KEY-SUPPLIER', 'supplier') RETURNING supplier_id", Long.class);
        assertTrue(materials.searchMaterials(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50), "e27m", "").getRecords().stream().anyMatch(m -> m.getMaterialId().equals(materialId)));
        jdbc.update("INSERT INTO scm_supplier_price(material_id, supplier_id, unit_price, min_qty, valid_from, valid_to, is_default, status) VALUES (?, ?, 3.14, 100, CURRENT_DATE-1, CURRENT_DATE+1, 1, 2)", materialId, supplierId);
        assertTrue(supplierPrices.selectGroupedPage(1, 20, null, null, null, null, null, null, null, "e27m").getRecords().stream().anyMatch(p -> p.getMaterialId().equals(materialId) && "E27M自发电-A1".equals(p.getSearchKeywords())));
        assertEquals("REFERENCE", reqLines.estimate(materialId, null, BigDecimal.TEN).getEstimateSource());
        var quote = reqLines.estimate(materialId, null, new BigDecimal("100"));
        assertEquals("QUOTE", quote.getEstimateSource());
        assertEquals(0, new BigDecimal("314").compareTo(quote.getEstimateAmount()));
        ScmPurchaseRequisition req = new ScmPurchaseRequisition(); req.setTitle("price snapshot");
        Long reqId = requisitions.save(req);
        ScmPurchaseRequisitionLine line = new ScmPurchaseRequisitionLine(); line.setRequisitionId(reqId); line.setMaterialId(materialId); line.setQty(new BigDecimal("100"));
        Long lineId = reqLines.save(line);
        assertEquals(0, new BigDecimal("314").compareTo(requisitions.getDetail(reqId).getTotalAmount()));
        line = reqLines.getDetail(lineId); line.setQty(new BigDecimal("200")); reqLines.update(line);
        assertEquals(0, new BigDecimal("628").compareTo(requisitions.getDetail(reqId).getTotalAmount()));
    }

    @Test @Transactional
    void materialExcelPriceAndKeywordsRoundTripAndBatchSalesAreValidated() {
        var input = new com.pcba.erp.pdm.excel.MaterialExcelVO();
        input.setMaterialCode("TEST-EXCEL"); input.setMaterialName("test"); input.setCategoryId(1L);
        input.setRefPrice(new BigDecimal("1.7000")); input.setSearchKeywords("E27M-A1"); input.setDefaultSupplierName("测试供应商"); input.setDefaultSupplierPn("ABC");
        var material = com.pcba.erp.pdm.excel.MaterialExcelConverter.toEntity(input);
        var output = com.pcba.erp.pdm.excel.MaterialExcelConverter.toVO(material, Map.of(1L, "test"));
        assertEquals(input.getRefPrice(), output.getRefPrice()); assertEquals(input.getSearchKeywords(), output.getSearchKeywords());
        assertEquals(input.getDefaultSupplierName(), output.getDefaultSupplierName()); assertEquals(input.getDefaultSupplierPn(), output.getDefaultSupplierPn());

        Long mid = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id) VALUES ('TEST-SALES-BATCH', 'test', 1) RETURNING material_id", Long.class);
        SalSalesOrder order = new SalSalesOrder(); order.setCustomerId(1L); order.setSoDate(LocalDate.now()); Long soId = sales.save(order);
        var valid = new com.pcba.erp.sal.entity.SalSalesOrderLine(); valid.setSoId(soId); valid.setMaterialId(mid); valid.setOrderQty(BigDecimal.TEN); valid.setUnitPrice(BigDecimal.ONE);
        var invalid = new com.pcba.erp.sal.entity.SalSalesOrderLine(); invalid.setSoId(soId); invalid.setMaterialId(mid); invalid.setOrderQty(BigDecimal.ZERO); invalid.setUnitPrice(BigDecimal.ONE);
        assertThrows(ServiceException.class, () -> salesLines.saveBatch(List.of(valid, invalid)));
        assertEquals(0, salesLines.selectPage(1, 10, soId, null, null).getTotal());
        assertEquals(1, salesLines.saveBatch(List.of(valid)).size());
    }

    @Test @Transactional
    void supplierQuotesRejectConflictsAndExplicitIds() {
        Long mid = jdbc.queryForObject("INSERT INTO pdm_material(material_code, material_name, category_id) VALUES ('TEST-QUOTE', 'test', 1) RETURNING material_id", Long.class);
        Long sid = jdbc.queryForObject("INSERT INTO scm_supplier(supplier_code, supplier_name) VALUES ('TEST-QUOTE-SUPPLIER', 'supplier') RETURNING supplier_id", Long.class);
        var quote = new com.pcba.erp.scm.entity.ScmSupplierPrice(); quote.setMaterialId(mid); quote.setSupplierId(sid); quote.setUnitPrice(BigDecimal.ONE);
        quote.setMinQty(BigDecimal.ONE); quote.setValidFrom(LocalDate.now()); quote.setValidTo(LocalDate.now().plusDays(30));
        Long id = supplierPrices.save(quote);
        var duplicate = new com.pcba.erp.scm.entity.ScmSupplierPrice(); duplicate.setMaterialId(mid); duplicate.setSupplierId(sid); duplicate.setUnitPrice(BigDecimal.TEN);
        duplicate.setMinQty(BigDecimal.ONE); duplicate.setValidFrom(LocalDate.now().plusDays(1)); duplicate.setValidTo(LocalDate.now().plusDays(31));
        assertThrows(ServiceException.class, () -> supplierPrices.save(duplicate));
        duplicate.setPriceId(id);
        assertThrows(ServiceException.class, () -> supplierPrices.save(duplicate));
        assertEquals(0, BigDecimal.ONE.compareTo(supplierPrices.getDetail(id).getUnitPrice()));
    }

    @Test @Transactional
    void salesShipmentIsTraceableAndCannotExceedOrderQuantity() {
        Long soId = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no, customer_id, customer_name, so_date, status) VALUES ('TEST-SHIPMENT', 1, 'test', CURRENT_DATE, 2) RETURNING so_id", Long.class);
        Long lineId = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id, line_no, material_id, order_qty, unit_price) VALUES (?, 1, 987654, 10, 2) RETURNING line_id", Long.class, soId);
        inventory.increaseInventory(90001L, 987654L, "", new BigDecimal("30"), "PCS");
        WmsStockOut doc = new WmsStockOut(); doc.setWarehouseId(90001L); doc.setOutType("SALES"); doc.setCustomerId(1L); doc.setStatus(2);
        WmsStockOutLine outLine = new WmsStockOutLine(); outLine.setSoId(soId); outLine.setSoLineId(lineId);
        outLine.setMaterialId(987654L); outLine.setQuantity(new BigDecimal("6")); outLine.setBatchNo(""); outLine.setUnit("PCS");
        doc.setLines(List.of(outLine));
        Long first = stockOut.saveStockOut(doc);
        assertTrue(stockOut.confirmStockOut(first));
        assertEquals(0, new BigDecimal("6").compareTo(jdbc.queryForObject("SELECT shipped_qty FROM sal_sales_order_line WHERE line_id=?", BigDecimal.class, lineId)));
        assertEquals(4, jdbc.queryForObject("SELECT status FROM sal_sales_order WHERE so_id=?", Integer.class, soId));
        var page = stockOut.selectStockOutPage(1, 10, null, "TEST-SHIPMENT", null, "SALES", null);
        assertEquals(first, page.getRecords().get(0).getStockOutId());
        assertEquals("TEST-SHIPMENT", page.getRecords().get(0).getSaleOrderNos());
        Long second = jdbc.queryForObject("INSERT INTO wms_stock_out(order_no, warehouse_id, out_type, customer_id, status) VALUES ('TEST-OUT-2',90001,'SALES',1,2) RETURNING stock_out_id", Long.class);
        jdbc.update("INSERT INTO wms_stock_out_line(stock_out_id, so_id, so_line_id, material_id, quantity, batch_no, unit) VALUES (?, ?, ?, 987654, 6, '', 'PCS')", second, soId, lineId);
        assertThrows(ServiceException.class, () -> stockOut.confirmStockOut(second));
        assertEquals(0, new BigDecimal("6").compareTo(jdbc.queryForObject("SELECT shipped_qty FROM sal_sales_order_line WHERE line_id=?", BigDecimal.class, lineId)));
    }

    @Test @Transactional
    void salesOrderGenerationRejectsPendingDocumentAndSupportsRemainingQuantity() {
        Long soId = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no, customer_id, customer_name, so_date, status, warehouse_id) VALUES ('TEST-GENERATE-SHIP', 1, 'test', CURRENT_DATE, 2, 90001) RETURNING so_id", Long.class);
        Long lineId = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id, line_no, material_id, order_qty, unit_price) VALUES (?, 1, 987655, 10, 2) RETURNING line_id", Long.class, soId);
        Long first = sales.generateStockOut(soId);
        assertThrows(ServiceException.class, () -> sales.generateStockOut(soId));
        jdbc.update("UPDATE wms_stock_out SET status=3 WHERE stock_out_id=?", first);
        jdbc.update("UPDATE sal_sales_order SET status=4 WHERE so_id=?", soId);
        jdbc.update("UPDATE sal_sales_order_line SET shipped_qty=4 WHERE line_id=?", lineId);
        sql.clearCache();
        Long second = sales.generateStockOut(soId);
        assertNotEquals(first, second);
        assertEquals(0, new BigDecimal("6").compareTo(stockOut.getDetail(second).getLines().get(0).getQuantity()));
    }

    @Test @Transactional
    void instructionPersistsPicturesFirmwareAndDetectsStaleOrForeignAttachments() {
        PmmWorkOrder order = new PmmWorkOrder(); order.setMaterialId(123L); order.setOrderQty(BigDecimal.ONE);
        Long id = workOrders.save(order);
        Long image = jdbc.queryForObject("INSERT INTO pmm_work_instruction_attachment(work_order_id,file_name,object_key,content_type,file_size,kind) VALUES (?,'part.png','test/image','image/png',10,'IMAGE') RETURNING attachment_id", Long.class, id);
        Long firmware = jdbc.queryForObject("INSERT INTO pmm_work_instruction_attachment(work_order_id,file_name,object_key,content_type,file_size,kind) VALUES (?,'firmware.bin','test/fw','application/octet-stream',10,'FIRMWARE') RETURNING attachment_id", Long.class, id);
        PmmWorkInstruction instruction = instructions.get(id);
        instruction.setBrandName("品牌"); instruction.setBluetoothName("BLE-001"); instruction.setFirmwareAttachmentId(firmware); instruction.setProductImageId(image);
        PmmWorkInstruction.Item item = new PmmWorkInstruction.Item(); item.setName("透明盖"); item.setImageId(image); item.setDescription("按图安装");
        instruction.setItems(List.of(item));
        instructions.save(id, instruction);
        var saved = instructions.get(id);
        assertEquals(1, saved.getVersion()); assertEquals("BLE-001", saved.getBluetoothName());
        assertEquals("按图安装", saved.getItems().get(0).getDescription());
        assertEquals(firmware, saved.getFirmwareAttachmentId());
        assertThrows(ServiceException.class, () -> instructions.save(id, instruction));
        saved.setProductImageId(firmware);
        assertThrows(ServiceException.class, () -> instructions.save(id, saved));
    }

    @Test
    void concurrentSalesShipmentsCannotExceedOrderQuantity() throws Exception {
        long token = System.nanoTime();
        Long so = jdbc.queryForObject("INSERT INTO sal_sales_order(so_no,customer_id,customer_name,so_date,status) VALUES (?,1,'test',CURRENT_DATE,2) RETURNING so_id", Long.class, "CONCURRENT-" + token);
        Long line = jdbc.queryForObject("INSERT INTO sal_sales_order_line(so_id,line_no,material_id,order_qty,unit_price) VALUES (?,1,?,10,1) RETURNING line_id", Long.class, so, token);
        List<Long> docs = new ArrayList<>();
        try {
            inventory.increaseInventory(90001L, token, "", new BigDecimal("20"), "PCS");
            for (int i=0; i<2; i++) {
                Long doc = jdbc.queryForObject("INSERT INTO wms_stock_out(order_no,warehouse_id,out_type,customer_id,status) VALUES (?,90001,'SALES',1,2) RETURNING stock_out_id", Long.class, "CONCURRENT-OUT-" + token + "-" + i);
                docs.add(doc);
                jdbc.update("INSERT INTO wms_stock_out_line(stock_out_id,so_id,so_line_id,material_id,quantity,batch_no,unit) VALUES (?,?,?,?,7,'','PCS')", doc, so, line, token);
            }
            var executor = Executors.newFixedThreadPool(2);
            int successes = 0;
            try {
                List<Future<Integer>> results = new ArrayList<>();
                for (Long doc : docs) results.add(executor.submit(() -> {
                    try { return stockOut.confirmStockOut(doc) ? 1 : 0; }
                    catch (ServiceException expected) { return 0; }
                }));
                for (var result : results) successes += result.get();
            } finally { executor.shutdownNow(); }
            assertEquals(1, successes);
            assertEquals(0, new BigDecimal("7").compareTo(jdbc.queryForObject("SELECT shipped_qty FROM sal_sales_order_line WHERE line_id=?", BigDecimal.class, line)));
            assertEquals(0, new BigDecimal("13").compareTo(jdbc.queryForObject("SELECT quantity FROM wms_inventory WHERE warehouse_id=90001 AND material_id=?", BigDecimal.class, token)));
        } finally {
            for (Long doc : docs) {
                jdbc.update("DELETE FROM sal_ar_receivable WHERE receivable_id IN (SELECT receivable_id FROM sal_ar_receivable_line WHERE stock_out_id=?)", doc);
                jdbc.update("DELETE FROM sal_ar_receivable_line WHERE stock_out_id=?", doc);
                jdbc.update("DELETE FROM wms_stock_out_line WHERE stock_out_id=?", doc);
                jdbc.update("DELETE FROM wms_stock_out WHERE stock_out_id=?", doc);
            }
            jdbc.update("DELETE FROM wms_inventory WHERE warehouse_id=90001 AND material_id=?", token);
            jdbc.update("DELETE FROM sal_sales_order_line WHERE so_id=?", so);
            jdbc.update("DELETE FROM sal_sales_order WHERE so_id=?", so);
        }
    }
}
