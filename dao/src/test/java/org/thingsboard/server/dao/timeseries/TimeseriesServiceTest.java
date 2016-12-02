/**
 * Copyright © 2016 The Thingsboard Authors
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
package org.thingsboard.server.dao.timeseries;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.utils.UUIDs;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.dao.service.AbstractServiceTest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thingsboard.server.common.data.kv.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Andrew Shvayka
 */

@Slf4j
public class TimeseriesServiceTest extends AbstractServiceTest {

    private static final String STRING_KEY = "stringKey";
    private static final String LONG_KEY = "longKey";
    private static final String DOUBLE_KEY = "doubleKey";
    private static final String BOOLEAN_KEY = "booleanKey";

    public static final int PARTITION_MINUTES = 1100;

    private static final long TS = 42L;

    KvEntry stringKvEntry = new StringDataEntry(STRING_KEY, "value");
    KvEntry longKvEntry = new LongDataEntry(LONG_KEY, Long.MAX_VALUE);
    KvEntry doubleKvEntry = new DoubleDataEntry(DOUBLE_KEY, Double.MAX_VALUE);
    KvEntry booleanKvEntry = new BooleanDataEntry(BOOLEAN_KEY, Boolean.TRUE);

    @Test
    public void testFindAllLatest() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());

        saveEntries(deviceId, TS - 2);
        saveEntries(deviceId, TS - 1);
        saveEntries(deviceId, TS);

        ResultSetFuture rsFuture = tsService.findAllLatest(DataConstants.DEVICE, deviceId);
        List<TsKvEntry> tsList = tsService.convertResultSetToTsKvEntryList(rsFuture.get());

        assertNotNull(tsList);
        assertEquals(4, tsList.size());
        for (int i = 0; i < tsList.size(); i++) {
            assertEquals(TS, tsList.get(i).getTs());
        }

        Collections.sort(tsList, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

        List<TsKvEntry> expected = Arrays.asList(
                toTsEntry(TS, stringKvEntry),
                toTsEntry(TS, longKvEntry),
                toTsEntry(TS, doubleKvEntry),
                toTsEntry(TS, booleanKvEntry));
        Collections.sort(expected, (o1, o2) -> o1.getKey().compareTo(o2.getKey()));

        assertEquals(expected, tsList);
    }

    @Test
    public void testFindLatest() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());

        saveEntries(deviceId, TS - 2);
        saveEntries(deviceId, TS - 1);
        saveEntries(deviceId, TS);

        List<ResultSet> rs = tsService.findLatest(DataConstants.DEVICE, deviceId, Collections.singleton(STRING_KEY)).get();
        Assert.assertEquals(1, rs.size());
        Assert.assertEquals(toTsEntry(TS, stringKvEntry), tsService.convertResultToTsKvEntry(rs.get(0).one()));
    }

    @Test
    public void testFindDeviceTsDataByQuery() throws Exception {
        DeviceId deviceId = new DeviceId(UUIDs.timeBased());
        LocalDateTime localDateTime = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(PARTITION_MINUTES);
        log.debug("Start event time is {}", localDateTime);
        List<TsKvEntry> entries = new ArrayList<>(PARTITION_MINUTES);

        for (int i = 0; i < PARTITION_MINUTES; i++) {
            long time = localDateTime.plusMinutes(i).toInstant(ZoneOffset.UTC).toEpochMilli();
            BasicTsKvEntry tsKvEntry = new BasicTsKvEntry(time, stringKvEntry);
            tsService.save(DataConstants.DEVICE, deviceId, tsKvEntry).get();
            entries.add(tsKvEntry);
        }
        log.debug("Saved all records {}", localDateTime);
        List<TsKvEntry> list = tsService.find(DataConstants.DEVICE, deviceId, new BaseTsKvQuery(STRING_KEY, entries.get(599).getTs(),
                LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli()));
        log.debug("Fetched records {}", localDateTime);
        List<TsKvEntry> expected = entries.subList(600, PARTITION_MINUTES);
        assertEquals(expected.size(), list.size());
        assertEquals(expected, list);
    }


    private void saveEntries(DeviceId deviceId, long ts) throws ExecutionException, InterruptedException {
        tsService.save(DataConstants.DEVICE, deviceId, toTsEntry(ts, stringKvEntry)).get();
        tsService.save(DataConstants.DEVICE, deviceId, toTsEntry(ts, longKvEntry)).get();
        tsService.save(DataConstants.DEVICE, deviceId, toTsEntry(ts, doubleKvEntry)).get();
        tsService.save(DataConstants.DEVICE, deviceId, toTsEntry(ts, booleanKvEntry)).get();
    }

    private static TsKvEntry toTsEntry(long ts, KvEntry entry) {
        return new BasicTsKvEntry(ts, entry);
    }


}