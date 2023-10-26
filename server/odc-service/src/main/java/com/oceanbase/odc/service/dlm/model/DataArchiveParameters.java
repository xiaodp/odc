/*
 * Copyright (c) 2023 OceanBase.
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
package com.oceanbase.odc.service.dlm.model;

import java.util.List;

import com.oceanbase.odc.service.flow.model.TaskParameters;
import com.oceanbase.tools.migrator.common.enums.MigrationInsertAction;

import lombok.Data;

/**
 * @Author：tinker
 * @Date: 2023/5/10 20:05
 * @Descripition:
 */
@Data
public class DataArchiveParameters implements TaskParameters {

    private String name;

    private Long sourceDatabaseId;

    private Long targetDataBaseId;

    private String sourceDatabaseName;

    private String targetDatabaseName;

    private String sourceDataSourceName;

    private String targetDataSourceName;

    private List<OffsetConfig> variables;

    private List<DataArchiveTableConfig> tables;

    private boolean deleteAfterMigration = false;

    private MigrationInsertAction migrationInsertAction = MigrationInsertAction.INSERT_NORMAL;

    private RateLimitConfiguration rateLimit;
}
