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
package com.oceanbase.odc.service.session.factory;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.oceanbase.odc.common.event.AbstractEventListener;
import com.oceanbase.odc.common.event.EventPublisher;
import com.oceanbase.odc.common.event.LocalEventPublisher;
import com.oceanbase.odc.common.util.StringUtils;
import com.oceanbase.odc.core.datasource.ConnectionResetEvent;
import com.oceanbase.odc.core.session.ConnectionSession;
import com.oceanbase.odc.core.session.ConnectionSessionConstants;
import com.oceanbase.odc.core.session.ConnectionSessionFactory;
import com.oceanbase.odc.core.session.ConnectionSessionUtil;
import com.oceanbase.odc.core.session.DefaultConnectionSession;
import com.oceanbase.odc.core.shared.constant.ConnectionAccountType;
import com.oceanbase.odc.core.shared.constant.DialectType;
import com.oceanbase.odc.core.sql.execute.task.SqlExecuteTaskManager;
import com.oceanbase.odc.core.task.TaskManagerFactory;
import com.oceanbase.odc.service.connection.model.ConnectionConfig;
import com.oceanbase.odc.service.connection.util.ConnectionInfoUtil;
import com.oceanbase.odc.service.connection.util.DefaultConnectionExtensionExecutor;
import com.oceanbase.odc.service.datasecurity.accessor.DatasourceColumnAccessor;
import com.oceanbase.odc.service.session.initializer.SwitchSchemaInitializer;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A database session factory specifically for oceanbase, used to generate customized database
 * sessions
 *
 * @author yh263208
 * @date 2021-11-17 13:56
 * @see ConnectionSessionFactory
 * @since ODC_release_3.2.2
 */
@Slf4j
public class DefaultConnectSessionFactory implements ConnectionSessionFactory {

    private final ConnectionConfig connectionConfig;
    private final TaskManagerFactory<SqlExecuteTaskManager> taskManagerFactory;
    private final Boolean autoCommit;
    private final EventPublisher eventPublisher;
    private final ConnectionAccountType accountType;
    @Setter
    private long sessionTimeoutMillis;

    public DefaultConnectSessionFactory(@NonNull ConnectionConfig connectionConfig,
            ConnectionAccountType type, Boolean autoCommit,
            TaskManagerFactory<SqlExecuteTaskManager> taskManagerFactory) {
        this.sessionTimeoutMillis = TimeUnit.MILLISECONDS.convert(
                ConnectionSessionConstants.SESSION_EXPIRATION_TIME_SECONDS, TimeUnit.SECONDS);
        this.connectionConfig = connectionConfig;
        this.taskManagerFactory = taskManagerFactory;
        this.autoCommit = autoCommit == null || autoCommit;
        this.eventPublisher = new LocalEventPublisher();
        this.accountType = type == null ? ConnectionAccountType.MAIN : type;
    }

    public DefaultConnectSessionFactory(@NonNull ConnectionConfig connectionConfig) {
        this(connectionConfig, null, null, null);
    }

    @Override
    public ConnectionSession generateSession() {
        ConnectionSession session = createSession();
        registerSysDataSource(session);
        registerConsoleDataSource(session);
        registerBackendDataSource(session);
        initSession(session);
        if (StringUtils.isNotBlank(connectionConfig.defaultSchema())) {
            ConnectionSessionUtil.setCurrentSchema(session, connectionConfig.defaultSchema());
        }
        if (StringUtils.isNotBlank(connectionConfig.getTenantName())) {
            ConnectionSessionUtil.setTenantName(session, connectionConfig.getTenantName());
        }
        if (StringUtils.isNotBlank(connectionConfig.getClusterName())) {
            ConnectionSessionUtil.setClusterName(session, connectionConfig.getClusterName());
        }
        return session;
    }

    private void registerConsoleDataSource(ConnectionSession session) {
        OBConsoleDataSourceFactory dataSourceFactory =
                new OBConsoleDataSourceFactory(connectionConfig, accountType, autoCommit);
        dataSourceFactory.setEventPublisher(eventPublisher);
        ProxyDataSourceFactory proxyFactory = new ProxyDataSourceFactory(dataSourceFactory);
        session.register(ConnectionSessionConstants.CONSOLE_DS_KEY, proxyFactory);
        proxyFactory.setInitializer(new SwitchSchemaInitializer(session));
    }

    private void registerBackendDataSource(ConnectionSession session) {
        DruidDataSourceFactory dataSourceFactory =
                new DruidDataSourceFactory(connectionConfig, accountType);
        ProxyDataSourceFactory proxyFactory = new ProxyDataSourceFactory(dataSourceFactory);
        session.register(ConnectionSessionConstants.BACKEND_DS_KEY, proxyFactory);
        proxyFactory.setInitializer(new SwitchSchemaInitializer(session));
    }

    private void registerSysDataSource(ConnectionSession session) {
        OBSysUserDataSourceFactory dataSourceFactory = new OBSysUserDataSourceFactory(connectionConfig);
        session.register(ConnectionSessionConstants.SYS_DS_KEY, dataSourceFactory);
    }

    private ConnectionSession createSession() {
        try {
            return new DefaultConnectionSession(new DefaultSessionIdGenerator(connectionConfig),
                    taskManagerFactory, sessionTimeoutMillis, connectionConfig.getType(), autoCommit,
                    new DefaultConnectionExtensionExecutor(connectionConfig.getDialectType()));
        } catch (Exception e) {
            log.warn("Failed to create connection session", e);
            throw new IllegalStateException(e);
        }
    }

    private void initSession(ConnectionSession session) {
        this.eventPublisher.addEventListener(new ConsoleConnectionResetListener(session));
        ConnectionSessionUtil.setEventPublisher(session, eventPublisher);
        ConnectionSessionUtil.initArchitecture(session);
        ConnectionInfoUtil.initSessionVersion(session);
        ConnectionSessionUtil.setConsoleSessionResetFlag(session, false);
        ConnectionInfoUtil.initConsoleConnectionId(session);
        ConnectionSessionUtil.setConnectionConfig(session, connectionConfig);
        ConnectionSessionUtil.setConnectionAccountType(session, accountType);
        ConnectionSessionUtil.setColumnAccessor(session, new DatasourceColumnAccessor(session));
        setNlsFormat(session);
    }

    private static void setNlsFormat(ConnectionSession session) {
        if (session.getDialectType() != DialectType.OB_ORACLE) {
            return;
        }
        Map<String, String> sessionVariables = new HashMap<>();
        try {
            sessionVariables = ConnectionSessionUtil.queryAllSessionVariables(session);
        } catch (Exception e) {
            log.warn("Failed to query all session variables", e);
        }
        try {
            String format = sessionVariables.get("nls_date_format");
            if (format == null) {
                format = ConnectionSessionUtil.queryNlsDateFormat(session);
            }
            ConnectionSessionUtil.setNlsDateFormat(session, format);
        } catch (Exception e) {
            log.warn("Failed to query nls_date_format, use default instead", e);
            ConnectionSessionUtil.setNlsDateFormat(session, "DD-MON-RR");
        }
        try {
            String format = sessionVariables.get("nls_timestamp_format");
            if (format == null) {
                format = ConnectionSessionUtil.queryNlsTimestampFormat(session);
            }
            ConnectionSessionUtil.setNlsTimestampFormat(session, format);
        } catch (Exception e) {
            log.warn("Failed to query nls_timestamp_format, use default instead", e);
            ConnectionSessionUtil.setNlsTimestampFormat(session, "DD-MON-RR");
        }
        try {
            String format = sessionVariables.get("nls_timestamp_tz_format");
            if (format == null) {
                format = ConnectionSessionUtil.queryNlsTimestampTZFormat(session);
            }
            ConnectionSessionUtil.setNlsTimestampTZFormat(session, format);
        } catch (Exception e) {
            log.warn("Failed to query nls_timestamp_tz_format, use default instead", e);
            ConnectionSessionUtil.setNlsTimestampTZFormat(session, "DD-MON-RR");
        }
        log.info("Set nls format completed.");
    }

    /**
     * {@link ConsoleConnectionResetListener}
     *
     * @author yh263208
     * @date 2022-10-10 22:20
     * @since ODC_release_3.5.0
     * @see AbstractEventListener
     */
    @Slf4j
    static class ConsoleConnectionResetListener extends AbstractEventListener<ConnectionResetEvent> {

        private final ConnectionSession connectionSession;

        public ConsoleConnectionResetListener(@NonNull ConnectionSession connectionSession) {
            this.connectionSession = connectionSession;
        }

        @Override
        public void onEvent(ConnectionResetEvent event) {
            ConnectionSessionUtil.setConsoleSessionResetFlag(connectionSession, true);
            try (Statement statement = ((Connection) event.getSource()).createStatement()) {
                ConnectionInfoUtil.initConnectionId(statement, connectionSession);
            } catch (Exception e) {
                log.warn("Failed to init connection id", e);
            }
        }
    }

}
