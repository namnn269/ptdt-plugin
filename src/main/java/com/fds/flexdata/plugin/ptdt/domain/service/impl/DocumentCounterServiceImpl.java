package com.fds.flexdata.plugin.ptdt.domain.service.impl;

import com.fds.flex.user.context.UserContext;
import com.fds.flex.user.context.UserContextHolder;
import com.fds.flex.user.context.UserContextHolderStrategy;
import com.fds.flexdata.plugin.ptdt.application.http.request.DocumentCounterV2Request;
import com.fds.flexdata.plugin.ptdt.application.http.response.DocumentCounterResponse;
import com.fds.flexdata.plugin.ptdt.domain.service.DocumentCounterService;
import com.fds.flexdata.pluginapi.CommonFunctionHandler;
import com.fds.flexdata.pluginapi.FlexDigitalCommonService;
import com.fds.flexdata.pluginapi.object_value.TrangThaiDuLieu;
import com.fds.flexdata.pluginapi.query.DataSourceRequest;
import com.mongodb.client.model.Filters;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentCounterServiceImpl implements DocumentCounterService {

    private final ExecutorService executorService;
    private final CommonFunctionHandler commonFunctionHandler;
    private final FlexDigitalCommonService flexDigitalCommonService;

    @Override
    public Map<String, Long> count(String ns, String doiTuongDuLieu) {
        List<ResultFuture> futures = Arrays.stream(doiTuongDuLieu.split(","))
                .filter(StringUtils::isNotBlank)
                .map(item -> new DataSourceRequest(ns, item))
                .map(item -> new ResultFuture(
                        item.getOriginalCollectionName(),
                        item.getOriginalCollectionName(),
                        CompletableFuture.supplyAsync(() -> commonFunctionHandler.count(item, new Document()))
                ))
                .toList();
        CompletableFuture.allOf(futures.stream().map(ResultFuture::getFuture).toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .collect(Collectors.toMap(
                        ResultFuture::getCollectionName,
                        item -> item.getFuture().join()
                ));
    }

    @Override
    public Map<String, Long> count(String ns, List<String> doiTuongDuLieuList) {
        List<ResultFuture> futures = doiTuongDuLieuList
                .stream()
                .filter(StringUtils::isNotBlank)
                .map(item -> new DataSourceRequest(ns, item))
                .map(item -> new ResultFuture(
                        item.getOriginalCollectionName(),
                        item.getCollectionName(),
                        CompletableFuture.supplyAsync(() -> commonFunctionHandler.count(item, Filters.eq("Metadata.TrangThaiDuLieu", TrangThaiDuLieu.ACTIVE.getCode())))))
                .toList();
        CompletableFuture.allOf(futures.stream().map(ResultFuture::getFuture).toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .collect(Collectors.toMap(
                        ResultFuture::getCollectionName,
                        item -> item.getFuture().join()
                ));
    }

    @Override
    public List<DocumentCounterResponse> count(String ns, DocumentCounterV2Request request) {
        UserContextHolderStrategy contextHolderStrategy = UserContextHolder.getContextHolderStrategy();
        UserContext context = UserContextHolder.getContext();
        List<ResultFuture> futures = request.getCollections()
                .stream()
                .filter(item -> StringUtils.isNotBlank(item.getCollectionName()))
                .map(item -> new ResultFuture(
                        item.getCollectionName(),
                        StringUtils.getIfEmpty(item.getAlias(), item::getCollectionName),
                        CompletableFuture.supplyAsync(() -> {
                            setThreadData(contextHolderStrategy, context);
                            long count = flexDigitalCommonService.count(new DataSourceRequest(ns, item.getCollectionName()), item.getFilterExpression());
                            clearThreadData();
                            return count;
                        }, executorService)
                ))
                .toList();
        CompletableFuture.allOf(futures.stream().map(ResultFuture::getFuture).toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .map(item -> new DocumentCounterResponse(item.getCollectionName(), item.getAlias(), item.getFuture().join()))
                .toList();
    }

    private void setThreadData(UserContextHolderStrategy contextHolderStrategy, UserContext context) {
        UserContextHolder.setContextHolderStrategy(contextHolderStrategy);
        UserContextHolder.setContext(context);
    }

    private void clearThreadData() {
        UserContextHolder.clearContext();
    }

    @AllArgsConstructor
    @Getter
    private static class ResultFuture {
        private String collectionName;
        private String alias;
        private CompletableFuture<Long> future;
    }
}
