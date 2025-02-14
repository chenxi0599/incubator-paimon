/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.table.system;

import org.apache.paimon.Snapshot;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.data.BinaryString;
import org.apache.paimon.data.GenericRow;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.data.Timestamp;
import org.apache.paimon.manifest.ManifestCommittable;
import org.apache.paimon.schema.Schema;
import org.apache.paimon.table.FileStoreTable;
import org.apache.paimon.table.TableTestBase;
import org.apache.paimon.table.sink.TableCommitImpl;
import org.apache.paimon.types.DataTypes;
import org.apache.paimon.utils.DateTimeUtils;
import org.apache.paimon.utils.TagManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link TagsTable}. */
public class TagsTableTest extends TableTestBase {

    private static final String tableName = "MyTable";
    private TagsTable tagsTable;
    private TagManager tagManager;

    @BeforeEach
    public void before() throws Exception {
        Identifier identifier = identifier(tableName);
        Schema schema =
                Schema.newBuilder()
                        .column("product_id", DataTypes.INT())
                        .column("price", DataTypes.INT())
                        .column("sales", DataTypes.INT())
                        .primaryKey("product_id")
                        .option("tag.automatic-creation", "watermark")
                        .option("tag.creation-period", "daily")
                        .option("tag.num-retained-max", "3")
                        .build();
        catalog.createTable(identifier, schema, true);
        FileStoreTable table = (FileStoreTable) catalog.getTable(identifier);
        TableCommitImpl commit = table.newCommit(commitUser).ignoreEmptyCommit(false);
        commit.commit(
                new ManifestCommittable(
                        0,
                        Timestamp.fromLocalDateTime(LocalDateTime.parse("2023-07-18T12:00:01"))
                                .getMillisecond()));
        commit.commit(
                new ManifestCommittable(
                        1,
                        Timestamp.fromLocalDateTime(LocalDateTime.parse("2023-07-19T12:00:01"))
                                .getMillisecond()));
        tagsTable = (TagsTable) catalog.getTable(identifier(tableName + "$tags"));
        tagManager = table.store().newTagManager();
        table.createTag("many-tags-test");
    }

    @Test
    public void testTagsTable() throws Exception {
        List<InternalRow> expectRow = getExceptedResult();
        List<InternalRow> result = read(tagsTable);
        assertThat(result).containsExactlyElementsOf(expectRow);
    }

    private List<InternalRow> getExceptedResult() {
        List<InternalRow> internalRows = new ArrayList<>();
        for (Map.Entry<Snapshot, List<String>> tag : tagManager.tags().entrySet()) {
            Snapshot snapshot = tag.getKey();
            for (String tagName : tag.getValue()) {
                internalRows.add(
                        GenericRow.of(
                                BinaryString.fromString(tagName),
                                snapshot.id(),
                                snapshot.schemaId(),
                                Timestamp.fromLocalDateTime(
                                        DateTimeUtils.toLocalDateTime(snapshot.timeMillis())),
                                snapshot.totalRecordCount()));
            }
        }
        return internalRows;
    }
}
