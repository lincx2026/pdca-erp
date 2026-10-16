package com.pcba.erp.pdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pdm.entity.PdmMaterialSupplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PdmMaterialSupplierMapper extends BaseMapper<PdmMaterialSupplier> {

    /**
     * 按物料查询供应商报价 (按 priority ASC, id ASC)
     * 支持 dataScope 拼接
     */
    @Select("<script>" +
            "SELECT s.* FROM pdm_material_supplier s " +
            "<where>" +
            "<if test='materialId != null'> AND s.material_id = #{materialId} </if>" +
            "<if test='supplierId != null'> AND s.supplier_id = #{supplierId} </if>" +
            "<if test='status != null'> AND s.status = #{status} </if>" +
            "<if test='dataScope != null and dataScope != \"\"'> AND ${dataScope} </if>" +
            "</where> ORDER BY s.priority ASC, s.id ASC" +
            "</script>")
    List<PdmMaterialSupplier> listByMaterial(@Param("materialId") Long materialId,
                                             @Param("supplierId") Long supplierId,
                                             @Param("status") Integer status,
                                             @Param("dataScope") String dataScope);

    /** 联合唯一(物料+供应商)重数 */
    @Select("SELECT COUNT(*) FROM pdm_material_supplier " +
            "WHERE material_id=#{materialId} AND supplier_id=#{supplierId} " +
            "AND (#{id} IS NULL OR id != #{id})")
    int countDuplicateKey(@Param("materialId") Long materialId,
                          @Param("supplierId") Long supplierId,
                          @Param("id") Long id);

    /** 同物料只能一个 is_default=1 */
    @Select("SELECT COUNT(*) FROM pdm_material_supplier " +
            "WHERE material_id=#{materialId} AND is_default=1 " +
            "AND (#{id} IS NULL OR id != #{id})")
    int countAnotherDefault(@Param("materialId") Long materialId, @Param("id") Long id);

    /** 设置本物料其它供应商都不是默认 */
    @Update("UPDATE pdm_material_supplier SET is_default=0 WHERE material_id=#{materialId} AND (#{id} IS NULL OR id != #{id})")
    void clearOtherDefault(@Param("materialId") Long materialId, @Param("id") Long id);
}
