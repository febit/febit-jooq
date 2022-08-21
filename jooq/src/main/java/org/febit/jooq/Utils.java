/*
 * Copyright 2022-present febit.org (support@febit.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.febit.jooq;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultRecordListener;
import org.jooq.impl.DefaultRecordListenerProvider;

import javax.annotation.Nullable;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@UtilityClass
class Utils {

    static Name name(Column.Name anno) {
        var col = anno.value();
        var table = anno.value();
        if (table.isEmpty()) {
            return DSL.name(col);
        }
        return DSL.name(table, col);
    }

    static Name name(@Nullable Column column, String fieldName) {
        if (column == null || column.value().isEmpty()) {
            return DSL.name(resolveSqlName(fieldName));
        }

        var col = column.value();
        var table = column.value();
        if (table.isEmpty()) {
            return DSL.name(col);
        }
        return DSL.name(table, col);
    }

    static <R extends UpdatableRecord<R>, P, T extends ITable<R, I>, I> List<R> records(
            Configuration conf,
            T table,
            Collection<P> pos,
            boolean forUpdate
    ) {
        List<R> result = new ArrayList<>(pos.size());
        DSLContext dsl;

        // Record -> POJO mapping
        IdentityHashMap<R, Object> mapping = null;

        // Copy back returned values to the relevant POJO using the RecordListener SPI
        if (!isNotReturnRecordToPojo(conf)) {
            mapping = new IdentityHashMap<>();
            dsl = conf
                    .derive(providersForPojo(conf, mapping))
                    .dsl();
        } else {
            dsl = conf.dsl();
        }

        val idField = table.pkField();

        for (P po : pos) {
            R record = dsl.newRecord(table, po);

            if (mapping != null) {
                mapping.put(record, po);
            }

            if (forUpdate) {
                record.changed(idField, false);
            }

            resetChangedOnNotNull(record);
            result.add(record);
        }
        return result;
    }

    static boolean isNotReturnRecordToPojo(Configuration conf) {
        return FALSE.equals(conf.settings().isReturnRecordToPojo());
    }

    private static <R> RecordListenerProvider[] providersForPojo(
            Configuration conf, IdentityHashMap<R, Object> mapping) {
        return ArrayUtils.add(conf.recordListenerProviders(),
                new DefaultRecordListenerProvider(new BaseRecordListener() {
                    @Override
                    void end(RecordContext ctx) {
                        Record record = ctx.record();
                        record.into(mapping.get(record));
                    }
                })
        );
    }

    private static void resetChangedOnNotNull(Record record) {
        int size = record.size();

        for (int i = 0; i < size; i++) {
            if (record.get(i) != null) {
                continue;
            }
            var f = record.field(i);
            if (f != null
                    && !f.getDataType().nullable()) {
                record.changed(i, false);
            }
        }
    }

    static <R extends UpdatableRecord<R>, P, T extends ITable<R, I>, I> R record(
            Configuration conf,
            T table,
            P po,
            boolean forUpdate
    ) {
        DSLContext dsl;

        boolean returnValuesToPojo = !isNotReturnRecordToPojo(conf);

        // Copy back returned values to the relevant POJO using the RecordListener SPI
        if (returnValuesToPojo) {
            dsl = conf
                    .derive(providersForPojo(conf, po))
                    .dsl();
        } else {
            dsl = conf.dsl();
        }

        val idField = table.pkField();
        R record = dsl.newRecord(table, po);
        if (forUpdate) {
            record.changed(idField, false);
        }
        resetChangedOnNotNull(record);
        return record;
    }

    private static RecordListenerProvider[] providersForPojo(Configuration conf, Object pojo) {
        return ArrayUtils.add(conf.recordListenerProviders(),
                new DefaultRecordListenerProvider(new BaseRecordListener() {
                    @Override
                    void end(RecordContext ctx) {
                        ctx.record().into(pojo);
                    }
                })
        );
    }

    static boolean isNotReturnAllOnUpdatableRecord(Configuration conf) {
        return !TRUE.equals(conf.settings().isReturnAllOnUpdatableRecord());
    }


    private static Name qualifyName(String name) {
        return DSL.name(StringUtils.split(name, '.'));
    }

    static <T> Field<T> field(String qualified, Class<T> type) {
        return DSL.field(qualifyName(qualified), type);
    }

    static Stream<java.lang.reflect.Field> declaredFields(Class<?> formType) {
        return Arrays.stream(formType.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()));
    }

    static String resolveSqlName(String name) {
        var buf = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                buf.append('_')
                        .append(Character.toLowerCase(c));
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }

    private abstract static class BaseRecordListener extends DefaultRecordListener {

        @Override
        public final void storeEnd(RecordContext ctx) {
            end(ctx);
        }

        abstract void end(RecordContext ctx);

        @Override
        public final void insertEnd(RecordContext ctx) {
            end(ctx);
        }

        @Override
        public final void updateEnd(RecordContext ctx) {
            end(ctx);
        }

        @Override
        public final void deleteEnd(RecordContext ctx) {
            end(ctx);
        }
    }
}