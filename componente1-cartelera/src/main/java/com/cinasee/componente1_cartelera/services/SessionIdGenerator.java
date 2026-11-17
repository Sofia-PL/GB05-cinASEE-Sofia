package com.cinasee.componente1_cartelera.services;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionIdGenerator {

    private static final String COUNTER_COLLECTION = "id_counters";
    private static final String COUNTER_ID = "sessions";

    private final MongoTemplate mongoTemplate;

    public long nextId() {
        Document counter = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(COUNTER_ID)),
                new Update().inc("value", 1),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class,
                COUNTER_COLLECTION);

        Object value = counter == null ? null : counter.get("value");
        if (!(value instanceof Number sequence)) {
            throw new IllegalStateException("No se pudo generar el ID de la sesión");
        }
        return sequence.longValue();
    }
}
