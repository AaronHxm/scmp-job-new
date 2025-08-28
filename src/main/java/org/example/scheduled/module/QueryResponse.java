package org.example.scheduled.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse<T> {

    private String result;      // 响应结果码
    private String msg;        // 响应消息
    private Object extInfo;    // 扩展信息，使用Object类型以便通用
    private List<T> rows;
    private Integer pagecount;

}
