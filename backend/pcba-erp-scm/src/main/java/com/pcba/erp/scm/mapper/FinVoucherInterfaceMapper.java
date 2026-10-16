package com.pcba.erp.scm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.scm.entity.FinVoucherInterface;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FinVoucherInterfaceMapper extends BaseMapper<FinVoucherInterface> {

    @Select("<script>" +
            "SELECT * FROM fin_voucher_interface <where>" +
            "  <if test='voucherNo != null and voucherNo != \"\"'>AND voucher_no LIKE CONCAT('%',CAST(#{voucherNo} AS TEXT),'%')</if>" +
            "  <if test='voucherType != null and voucherType != \"\"'>AND voucher_type = #{voucherType}</if>" +
            "  <if test='bizSource != null and bizSource != \"\"'>AND biz_source = #{bizSource}</if>" +
            "  <if test='bizId != null'>AND biz_id = #{bizId}</if>" +
            "  <if test='pushStatus != null'>AND push_status = #{pushStatus}</if>" +
            "  <if test='voucherDate != null and voucherDate != \"\"'>AND voucher_date = #{voucherDate}</if>" +
            "</where> ORDER BY voucher_date DESC, voucher_id DESC</script>")
    IPage<FinVoucherInterface> selectPage(IPage<FinVoucherInterface> page,
                                          @Param("voucherNo") String voucherNo,
                                          @Param("voucherType") String voucherType,
                                          @Param("bizSource") String bizSource,
                                          @Param("bizId") Long bizId,
                                          @Param("pushStatus") Integer pushStatus,
                                          @Param("voucherDate") String voucherDate);

    @Select("SELECT COUNT(*) FROM fin_voucher_interface WHERE voucher_no = #{no} AND voucher_id <> #{id}")
    long countDuplicate(@Param("no") String no, @Param("id") Long id);
}
