/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hertzbeat.alert.service;

import com.google.common.collect.Lists;
import org.apache.hertzbeat.alert.calculate.PeriodicAlertRuleScheduler;
import org.apache.hertzbeat.alert.dao.AlertDefineDao;
import org.apache.hertzbeat.alert.service.impl.AlertDefineServiceImpl;
import org.apache.hertzbeat.common.entity.alerter.AlertDefine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.hertzbeat.common.constants.CommonConstants.ALERT_THRESHOLD_TYPE_PERIODIC;
import static org.apache.hertzbeat.common.constants.CommonConstants.ALERT_THRESHOLD_TYPE_REALTIME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test case for {@link AlertDefineService}
 */
@ExtendWith(MockitoExtension.class)
class AlertDefineServiceTest {

    private AlertDefine alertDefine;

    @Mock
    private AlertDefineDao alertDefineDao;
    
    @Mock
    private PeriodicAlertRuleScheduler periodicAlertRuleScheduler;

    @Mock
    private List<AlertDefineImExportService> alertDefineImExportServiceList;

    @Mock
    private DataSourceService dataSourceService;

    @InjectMocks
    private AlertDefineServiceImpl alertDefineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(this.alertDefineService, "alertDefineDao", alertDefineDao);
        ReflectionTestUtils.setField(this.alertDefineService, "periodicAlertRuleScheduler", periodicAlertRuleScheduler);

        this.alertDefine = AlertDefine.builder()
                .id(1L)
                .expr("1 > 0")
                .times(1)
                .template("template")
                .creator("tom")
                .modifier("tom")
                .build();
    }

    @Test
    void validate() {
        assertDoesNotThrow(() -> alertDefineService.validate(alertDefine, true));
        assertDoesNotThrow(() -> alertDefineService.validate(alertDefine, false));
    }

    @Test
    void addAlertDefine() {
        assertDoesNotThrow(() -> alertDefineService.addAlertDefine(alertDefine));
        when(alertDefineDao.save(alertDefine)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> alertDefineService.addAlertDefine(alertDefine));
    }

    @Test
    void modifyAlertDefine() {
        AlertDefine alertDefine = AlertDefine.builder().id(1L).build();
        when(alertDefineDao.save(alertDefine)).thenReturn(alertDefine);
        assertDoesNotThrow(() -> alertDefineService.modifyAlertDefine(alertDefine));
        reset();
        when(alertDefineDao.save(alertDefine)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> alertDefineService.modifyAlertDefine(alertDefine));
    }

    @Test
    void deleteAlertDefine() {
        long id = 1L;
        doNothing().doThrow(new RuntimeException()).when(alertDefineDao).deleteById(id);
        assertDoesNotThrow(() -> alertDefineService.deleteAlertDefine(id));
        assertThrows(RuntimeException.class, () -> alertDefineService.deleteAlertDefine(id));
    }

    @Test
    void getAlertDefine() {
        long id = 1L;
        AlertDefine alertDefine = AlertDefine.builder().id(id).build();
        when(alertDefineDao.findById(id)).thenReturn(Optional.of(alertDefine));
        assertDoesNotThrow(() -> alertDefineService.getAlertDefine(id));
    }

    @Test
    void deleteAlertDefines() {
        doNothing().when(alertDefineDao).deleteAlertDefinesByIdIn(anySet());
        assertDoesNotThrow(() -> alertDefineService.deleteAlertDefines(new HashSet<>(1)));
    }

    @Test
    void getAlertDefines() {
        when(alertDefineDao.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());
        assertNotNull(alertDefineService.getAlertDefines(null, null, "id", "desc", 1, 10));
        verify(alertDefineDao, times(1)).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void getDefinePreview() {
        String expr = "http_server_requests_seconds_count > 10";

        Map<String, Object> countValue1 = new HashMap<>() {
            {
                put("exception", "none");
                put("instance", "host.docker.internal:8989");
                put("__value__", 1307);
                put("method", "GET");
                put("__name__", "http_server_requests_seconds_count");
                put("__timestamp__", "1.750320922467E9");
                put("error", "none");
                put("job", "spring-boot-app");
                put("uri", "/actuator/prometheus");
                put("outcome", "SUCCESS");
                put("status", "200");
            }
        };
        when(dataSourceService.calculate(eq("promql"), eq(expr))).thenReturn(Lists.newArrayList(countValue1));
        List<Map<String, Object>> result = alertDefineService.getDefinePreview("promql", ALERT_THRESHOLD_TYPE_PERIODIC, expr);
        assertNotNull(result);
        assertEquals(1307, result.get(0).get("__value__"));

        result = alertDefineService.getDefinePreview("promql", ALERT_THRESHOLD_TYPE_PERIODIC, null);
        assertEquals(0, result.size());

        result = alertDefineService.getDefinePreview("promql", ALERT_THRESHOLD_TYPE_REALTIME, null);
        assertEquals(0, result.size());
    }

}
