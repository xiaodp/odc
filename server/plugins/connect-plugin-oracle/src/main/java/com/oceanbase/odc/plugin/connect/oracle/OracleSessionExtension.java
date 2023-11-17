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

package com.oceanbase.odc.plugin.connect.oracle;

import java.sql.Connection;
import java.util.Objects;

import org.pf4j.Extension;

import com.oceanbase.odc.common.util.JdbcOperationsUtil;
import com.oceanbase.odc.core.shared.exception.UnsupportedException;
import com.oceanbase.odc.plugin.connect.oboracle.OBOracleSessionExtension;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jingtian
 * @date 2023/11/8
 * @since ODC_release_4.2.4
 */
@Slf4j
@Extension
public class OracleSessionExtension extends OBOracleSessionExtension {
    @Override
    public void killQuery(Connection connection, String connectionId) {
        throw new UnsupportedOperationException("Not supported for oracle mode");
    }

    @Override
    public String getConnectionId(Connection connection) {
        String querySql = "select userenv('sessionid') from dual";
        try {
            return JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            throw new UnsupportedException("Failed to get session id from oracle, message=" + e);
        }
    }

    @Override
    public String getVariable(@NonNull Connection connection, @NonNull String variableName) {
        String querySql = "SELECT VALUE FROM SYS.V$PARAMETER WHERE NAME = '" + variableName.toLowerCase() + "'";
        String value = null;
        try {
            value = JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
        } catch (Exception e) {
            log.warn("Failed to get variable {}, message={}", variableName, e.getMessage());
        }
        /**
         * nls parameters maybe null in V$PARAMETER, we need to query from V$NLS_PARAMETERS
         */
        if (Objects.isNull(value) && variableName.toLowerCase().startsWith("nls_")) {
            querySql = "SELECT VALUE FROM SYS.V$NLS_PARAMETERS WHERE PARAMETER = '" + variableName.toUpperCase() + "'";
            try {
                value = JdbcOperationsUtil.getJdbcOperations(connection).queryForObject(querySql, String.class);
            } catch (Exception e) {
                log.warn("Failed to get variable {}, message={}", variableName, e.getMessage());
            }
        }
        return value;
    }
}
