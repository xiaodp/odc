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

package com.oceanbase.odc.service.task.schedule;

import com.oceanbase.odc.service.task.enums.SourceType;

import lombok.Data;

/**
 * Identity a unique job
 * 
 * @author yaobin
 * @date 2023-11-23
 * @since 4.2.4
 */
@Data
public class JobIdentity {

    /**
     * source id, eg: task id,schedule id
     */
    private Long sourceId;
    /**
     * source typ, eg: TASK_TASK,SCHEDULE_TASK
     */
    private SourceType sourceType;
    /**
     * source sub type, eg: ASYNC,IMPORT,EXPORT,MOCKDATA,DATA_ARCHIVE
     */
    private String sourceSubType;

    public static JobIdentity of(Long sourceId, SourceType sourceType, String sourceSubType) {
        JobIdentity identity = new JobIdentity();
        identity.setSourceId(sourceId);
        identity.setSourceType(sourceType);
        identity.setSourceSubType(sourceSubType);
        return identity;
    }

}
