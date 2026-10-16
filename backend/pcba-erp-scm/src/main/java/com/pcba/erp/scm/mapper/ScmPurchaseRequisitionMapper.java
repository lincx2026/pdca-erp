package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pcba.erp.scm.entity.ScmPurchaseRequisition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScmPurchaseRequisitionMapper extends BaseMapper<ScmPurchaseRequisition> {

    @Select("<script>" +
            "SELECT * FROM scm_purchase_requisition " +
            "<where>" +
            "  <if test=\"requisitionNo != null and requisitionNo != ''\">" +
            "    AND requisition_no LIKE CONCAT('%', CAST(#{requisitionNo} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"title != null and title != ''\">" +
            "    AND title LIKE CONCAT('%', CAST(#{title} AS TEXT), '%')" +
            "  </if>" +
            "  <if test=\"applicantName != null and applicantName != ''\">" +
            "    AND applicant_name LIKE CONCAT('%', CAST(#{applicantName} AS TEXT), '%')" +
            "  </if>" +
            "  <if test='deptId != null'>" +
            "    AND dept_id = #{deptId}" +
            "  </if>" +
            "  <if test='status != null'>" +
            "    AND status = #{status}" +
            "  </if>" +
            "  <if test=\"requiredDate != null and requiredDate != ''\">" +
            "    AND CAST(required_date AS TEXT) LIKE CONCAT('%', CAST(#{requiredDate} AS TEXT), '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY requisition_id DESC" +
            "</script>")
    IPage<ScmPurchaseRequisition> selectPage(Page<ScmPurchaseRequisition> page, @Param("requisitionNo") String requisitionNo, @Param("title") String title, @Param("applicantName") String applicantName, @Param("deptId") Long deptId, @Param("status") Integer status, @Param("requiredDate") String requiredDate);

    @Select("SELECT COUNT(*) FROM scm_purchase_requisition WHERE requisition_no = #{noVal} " +
            "AND (CAST(#{idVal} AS BIGINT) IS NULL OR requisition_id != #{idVal})")
    int countDuplicate(@Param("noVal") String noVal, @Param("idVal") Long idVal);
}
