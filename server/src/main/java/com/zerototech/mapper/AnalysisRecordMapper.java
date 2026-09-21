package com.zerototech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zerototech.entity.AnalysisRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分析记录数据访问接口（继承 MyBatis-Plus 基础 BaseMapper）
 */
@Mapper
public interface AnalysisRecordMapper extends BaseMapper<AnalysisRecord> {
}
