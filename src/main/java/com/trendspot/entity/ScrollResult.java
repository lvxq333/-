package com.trendspot.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrollResult {
    // 数据列表
    private List<?> list;
    // 时间戳
    private Long minTime;
    // 偏移量
    private Integer offset;
}
