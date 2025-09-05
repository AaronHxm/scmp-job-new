package org.example.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.example.scheduled.module.ContractInfo;
import org.example.scheduled.module.QueryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class CollectionService {

    @Autowired
    private RestTemplate restTemplate;

    // 优化线程池配置
    private final ExecutorService processingExecutor = new ThreadPoolExecutor(
            14,                     // 核心线程数
            20,                     // 最大线程数
            30, TimeUnit.SECONDS,   // 空闲线程存活时间
            new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // 通过构造函数注入
    public CollectionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public QueryResponse<ContractInfo> fetchAllPreGrabCases() {
        // 先获取第一页数据以确定总页数
        QueryResponse<ContractInfo> firstPageResponse = fetchPreGrabCases(1);

        if (firstPageResponse == null ) {
            log.error("获取第一页数据失败");
            return firstPageResponse;
        }

        int totalPages = firstPageResponse.getPagecount();
        if (totalPages <= 1) {
            return firstPageResponse; // 只有一页直接返回
        }

        // 收集所有数据
        List<ContractInfo> allData = new ArrayList<>(firstPageResponse.getRows());

        // 并行获取剩余页面的数据
        List<CompletableFuture<QueryResponse<ContractInfo>>> futures = IntStream.range(2, totalPages + 1)
                .mapToObj(page -> CompletableFuture.supplyAsync(() -> fetchPreGrabCases(page), processingExecutor))
                .collect(Collectors.toList());

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 合并结果
        for (CompletableFuture<QueryResponse<ContractInfo>> future : futures) {
            try {
                QueryResponse<ContractInfo> pageResponse = future.get();
                if (pageResponse != null  && pageResponse.getRows() != null) {
                    allData.addAll(pageResponse.getRows());
                }
            } catch (Exception e) {
                log.error("获取某页数据失败: {}", e.getMessage());
            }
        }

        // 构建最终响应对象
        QueryResponse<ContractInfo> finalResponse = new QueryResponse<>();
        finalResponse.setRows(allData);
        finalResponse.setPagecount(totalPages);
        // 设置其他必要的字段...

        return finalResponse;
    }


    public QueryResponse<ContractInfo> fetchPreGrabCases(int pageIndex) {


        return fetchPreGrabCases(pageIndex,CollectionServiceConstants.AUTH_TOKEN);

    }

    public QueryResponse<ContractInfo> fetchPreGrabCases(int pageIndex,String token) {
        // 构建请求URL
        String url = CollectionServiceConstants.BASE_URL +
                "/gateway/collectionservice/dfcw/outSrc/preGrabCaseList" +
                "?tenancyId=" + CollectionServiceConstants.TENANCY_ID +
                "&menuId=" + CollectionServiceConstants.MENU_ID +
                "&menuName=" + CollectionServiceConstants.MENU_NAME +
                "&orgTemplateId=" + CollectionServiceConstants.ORG_TEMPLATE_ID +
                "&ClientServer=https:%2F%2Fcsmp.df-finance.com.cn";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
////        headers.set("Cookie", "user-code=" + CollectionServiceConstants.USER_CODE_COOKIE);

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.ALL));
        headers.set("Authorization",token);
        headers.set("Expires", "0");
        headers.set("Pragma", "no-cache");
        headers.set("clientId", "mplanyou");
        headers.set("gversion", "");
        headers.set("lang", "ZH_CN");
        headers.set("noncestr", "16636a46-792d-8513-83bb-db0b51f4db54");
        headers.set("range", "1");
        headers.set("sign", "fbc0842bab517a84c3b36a6dc5139fa5a4a0a3105bedf169b5da8514789383c35f706085f4374f5e61c6788b6b52df598be17143c889d4a2b80e435e38c78d65");
        headers.set("timestamp",String.valueOf( System.currentTimeMillis()));
        headers.set("Cookie", "user-code=3qZmZLDReedsYtGBGpdipM8JQCBHhzETyN0wGJl5mfK7qvcM+2jeWvqPP20jZuhA");
        headers.set("User-Agent", "Apifox/1.0.0 (https://apifox.com)");
        headers.set("Host", "csmp.df-finance.com.cn");
        headers.set("Connection", "keep-alive");

        // 构建请求体
        String requestBody = String.format(
                "{\"pageIndex\":%d,\"pageSize\":%d,\"dcaStatus\":\"%s\",\"dcaType\":\"%s\",\"orderAsc\":\"%s\",\"orderColumn\":\"%s\"}",
                pageIndex,
                CollectionServiceConstants.PAGE_SIZE,
                CollectionServiceConstants.DCA_STATUS,
                CollectionServiceConstants.DCA_TYPE,
                CollectionServiceConstants.ORDER_ASC,
                CollectionServiceConstants.ORDER_COLUMN
        );

        // 创建请求实体
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        // 发送请求并返回响应
        // 5. 发送请求并获取泛型响应
        ResponseEntity<QueryResponse<ContractInfo>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<QueryResponse<ContractInfo>>() {}
        );

        return response.getBody();

    }
}
