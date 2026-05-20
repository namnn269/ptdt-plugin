package com.fds.flexdata.plugin.ptdt.service;

import com.fds.flexdata.plugin.ptdt.domain.dto.ChiTietLoi;
import com.fds.flexdata.pluginapi.exception.AppException;
import com.fds.flexdata.pluginapi.object_value.DetailError;
import com.fds.flexdata.pluginapi.object_value.MessageCode;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class EntityRelationService {

    public void checkBelongsToTinhThanh(MongoDatabase database, ClientSession session, String collection, String codeField, String tinhField, String tinhMa, String tinhTen, String errorField, Map<String, String> codeToName) {
        if (ObjectUtils.isEmpty(tinhMa) || ObjectUtils.isEmpty(codeToName)) {
            return;
        }

        MongoCollection<Document> coll = database.getCollection(collection);

        Bson filter = Filters.and(
                Filters.in(codeField, codeToName.keySet()),
                Filters.eq(tinhField, tinhMa)
        );

        Set<String> validCodes = (session != null
                ? coll.find(session, filter)
                : coll.find(filter))
                .projection(Projections.include(codeField))
                .into(new ArrayList<>())
                .stream()
                .map(doc -> doc.getString(codeField))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<ChiTietLoi> errors = codeToName.entrySet()
                .stream()
                .filter(e -> !validCodes.contains(e.getKey()))
                .map(e -> new ChiTietLoi(
                        errorField,
                        String.format("%s (%s) không trực thuộc %s (%s)!", e.getValue(), e.getKey(), tinhTen, tinhMa))).toList();

        if (!errors.isEmpty()) {
            throw new AppException(MessageCode.LOI_DU_LIEU, MessageCode.LOI_DU_LIEU.getValue(), DetailError.E4, errors);
        }
    }
}