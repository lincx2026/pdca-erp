package com.pcba.erp.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pcba.erp.common.event.OperLogEvent;
import com.pcba.erp.system.entity.SysOperLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {

    @Select("<script>" +
            "SELECT * FROM sys_oper_log " +
            "<where>" +
            "<if test='title != null and title != \"\"'> AND title LIKE CONCAT('%', CAST(#{title} AS TEXT), '%') </if>" +
            "<if test='businessType != null'> AND business_type = #{businessType} </if>" +
            "<if test='status != null'> AND status = #{status} </if>" +
            "<if test='operName != null and operName != \"\"'> AND oper_name LIKE CONCAT('%', CAST(#{operName} AS TEXT), '%') </if>" +
            "<if test='beginTime != null'> AND oper_time &gt;= #{beginTime} </if>" +
            "<if test='endTime != null'> AND oper_time &lt;= #{endTime} </if>" +
            "</where> ORDER BY oper_time DESC" +
            "</script>")
    IPage<SysOperLog> selectPage(IPage<SysOperLog> page,
                                  @Param("title") String title,
                                  @Param("businessType") Integer businessType,
                                  @Param("status") Integer status,
                                  @Param("operName") String operName,
                                  @Param("beginTime") String beginTime,
                                  @Param("endTime") String endTime);

    /** 操作日志从事件对象直接插入 (无需走 entity, 提升性能) */
    @Insert("INSERT INTO sys_oper_log(title,business_type,method,request_method,operator_type,oper_name,dept_name," +
            "oper_url,oper_ip,oper_location,oper_param,json_result,status,error_msg,oper_time,cost_time) " +
            "VALUES(#{e.title},#{e.businessType},#{e.method},#{e.requestMethod},1,#{e.operName},#{e.deptName}," +
            "#{e.operUrl},#{e.operIp},#{e.operLocation},#{e.operParam},#{e.jsonResult},#{e.status},#{e.errorMsg}," +
            "#{e.operTime},#{e.costTime})")
    int insertByEvent(@Param("e") OperLogEvent e);
}
