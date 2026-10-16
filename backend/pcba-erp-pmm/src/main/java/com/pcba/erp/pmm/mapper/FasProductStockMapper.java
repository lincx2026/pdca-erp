package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.pmm.entity.FasProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface FasProductStockMapper extends BaseMapper<FasProductStock> {

    @Select("SELECT * FROM fas_product_stock WHERE stock_id = #{id} FOR UPDATE")
    FasProductStock selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM fas_product_stock WHERE product_id = #{productId} AND warehouse = #{warehouse} " +
            "AND stock_type = #{stockType} AND qty_available > 0 " +
            "AND (COALESCE(#{batchNo}, '') = '' OR COALESCE(batch_no, '') = #{batchNo}) " +
            "ORDER BY stock_id FOR UPDATE")
    List<FasProductStock> selectAvailableForUpdate(@Param("productId") Long productId,
            @Param("warehouse") String warehouse, @Param("stockType") String stockType,
            @Param("batchNo") String batchNo);

    @Select("<script>" +
            "SELECT * FROM fas_product_stock " +
            "<where>" +
            "  <if test='stockType != null and stockType != \"\"'>" +
            "    AND stock_type = #{stockType}" +
            "  </if>" +
            "  <if test='productName != null and productName != \"\"'>" +
            "    AND product_name LIKE CONCAT('%', CAST(#{productName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='batchNo != null and batchNo != \"\"'>" +
            "    AND batch_no LIKE CONCAT('%', CAST(#{batchNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='warehouse != null and warehouse != \"\"'>" +
            "    AND warehouse LIKE CONCAT('%', CAST(#{warehouse} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<FasProductStock> selectPage(Page<FasProductStock> page,
                                      @Param("stockType") String stockType,
                                      @Param("productName") String productName,
                                      @Param("batchNo") String batchNo,
                                      @Param("warehouse") String warehouse);

    /** 完工入库单/成品出库单 分页：按单据类型 + 状态过滤 */
    @Select("<script>" +
            "SELECT * FROM fas_product_stock " +
            "<where>" +
            "  <if test='docType != null and docType != \"\"'>" +
            "    AND doc_type = #{docType}" +
            "  </if>" +
            "  <if test='docStatus != null'>" +
            "    AND doc_status = #{docStatus}" +
            "  </if>" +
            "  <if test='docNo != null and docNo != \"\"'>" +
            "    AND doc_no LIKE CONCAT('%', CAST(#{docNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='productName != null and productName != \"\"'>" +
            "    AND product_name LIKE CONCAT('%', CAST(#{productName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='batchNo != null and batchNo != \"\"'>" +
            "    AND batch_no LIKE CONCAT('%', CAST(#{batchNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='warehouse != null and warehouse != \"\"'>" +
            "    AND warehouse LIKE CONCAT('%', CAST(#{warehouse} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY create_time DESC" +
            "</script>")
    IPage<FasProductStock> selectDocPage(Page<FasProductStock> page,
                                         @Param("docType") String docType,
                                         @Param("docStatus") Integer docStatus,
                                         @Param("docNo") String docNo,
                                         @Param("productName") String productName,
                                         @Param("batchNo") String batchNo,
                                         @Param("warehouse") String warehouse);

    /** 出入库流水 分页：按时间/产品/仓库/业务类型过滤 */
    @Select("<script>" +
            "SELECT * FROM fas_product_stock " +
            "<where>" +
            "  doc_status IN (2,3)" +
            "  <if test='docType != null and docType != \"\"'>" +
            "    AND doc_type = #{docType}" +
            "  </if>" +
            "  <if test='sourceType != null and sourceType != \"\"'>" +
            "    AND source_type = #{sourceType}" +
            "  </if>" +
            "  <if test='productName != null and productName != \"\"'>" +
            "    AND product_name LIKE CONCAT('%', CAST(#{productName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='warehouse != null and warehouse != \"\"'>" +
            "    AND warehouse LIKE CONCAT('%', CAST(#{warehouse} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='beginTime != null and beginTime != \"\"'>" +
            "    AND audit_time &gt;= #{beginTime}" +
            "  </if>" +
            "  <if test='endTime != null and endTime != \"\"'>" +
            "    AND audit_time &lt;= CAST(#{endTime} AS DATE) + INTERVAL '1 day' - INTERVAL '1 microsecond'" +
            "  </if>" +
            "</where>" +
            " ORDER BY audit_time DESC" +
            "</script>")
    IPage<FasProductStock> selectFlowPage(Page<FasProductStock> page,
                                          @Param("docType") String docType,
                                          @Param("sourceType") String sourceType,
                                          @Param("productName") String productName,
                                          @Param("warehouse") String warehouse,
                                          @Param("beginTime") String beginTime,
                                          @Param("endTime") String endTime);

    /** 按前缀取最大单号 (WFI-yyMMdd- / WFO-yyMMdd-) */
    @Select("SELECT MAX(doc_no) FROM fas_product_stock WHERE doc_no LIKE CONCAT(CAST(#{prefix} AS TEXT), '%')")
    String selectMaxDocNoByPrefix(@Param("prefix") String prefix);
}
