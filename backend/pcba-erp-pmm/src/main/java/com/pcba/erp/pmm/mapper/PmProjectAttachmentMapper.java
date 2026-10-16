package com.pcba.erp.pmm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pcba.erp.pmm.entity.PmProjectAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PmProjectAttachmentMapper extends BaseMapper<PmProjectAttachment> {

    @Select("SELECT * FROM pm_project_attachment WHERE project_id = #{projectId} ORDER BY create_time DESC")
    List<PmProjectAttachment> selectByProjectId(Long projectId);
}
