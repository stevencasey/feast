/*
 * Copyright 2018 The Feast Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package feast.serving.service;

import com.google.cloud.bigtable.hbase.BigtableConfiguration;
import com.google.cloud.bigtable.hbase.BigtableOptionsFactory;
import com.google.protobuf.Timestamp;
import feast.serving.model.FeatureValue;
import feast.serving.testutil.BigTablePopulator;
import feast.specs.FeatureSpecProto.FeatureSpec;
import feast.types.GranularityProto.Granularity;
import feast.types.GranularityProto.Granularity.Enum;
import feast.types.ValueProto.ValueType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Connection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BigTableFeatureStorageTestITCase {
  private static final String ENTITY_NAME = "test_entity";

  // The object under test
  BigTableFeatureStorage featureStorage;

  private BigTablePopulator bigTablePopulator;
  private List<String> entityIds;

  private Timestamp now;
  private Connection connection;

  @Before
  public void setUp() throws Exception {
    Configuration config = BigtableConfiguration.configure("dummyProject", "dummyInstance");
    config.set(BigtableOptionsFactory.BIGTABLE_EMULATOR_HOST_KEY, "localhost:8080");
    connection = BigtableConfiguration.connect(config);
    // ideally use bigtable emulator.
    bigTablePopulator = new BigTablePopulator(connection);
    featureStorage = new BigTableFeatureStorage(connection);

    entityIds = createEntityIds(10);
    now = Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build();
  }

  @After
  public void tearDown() throws Exception {
    connection.close();
  }

  @Test
  public void getFeatures_shouldWorkForGranularityNone() {
    FeatureSpec featureSpec1 =
        FeatureSpec.newBuilder()
            .setEntity(ENTITY_NAME)
            .setId("test_entity.none.feature_1")
            .setName("feature_1")
            .setGranularity(Granularity.Enum.NONE)
            .setValueType(ValueType.Enum.STRING)
            .build();

    FeatureSpec featureSpec2 =
        FeatureSpec.newBuilder()
            .setEntity(ENTITY_NAME)
            .setId("test_entity.none.feature_2")
            .setName("feature_2")
            .setGranularity(Granularity.Enum.NONE)
            .setValueType(ValueType.Enum.STRING)
            .build();

    List<FeatureSpec> featureSpecs = Arrays.asList(featureSpec1, featureSpec2);
    bigTablePopulator.populate(ENTITY_NAME, entityIds, featureSpecs, now);
    List<FeatureValue> results = featureStorage.getFeature(ENTITY_NAME, entityIds, featureSpecs);

    bigTablePopulator.validate(results, entityIds, featureSpecs);
  }

  @Test
  public void getFeatures_shouldGracefullyHandleMissingEntity() {
    FeatureSpec featureSpec1 =
        FeatureSpec.newBuilder()
            .setEntity(ENTITY_NAME)
            .setId("test_entity.none.feature_1")
            .setName("feature_granularity_none")
            .setGranularity(Granularity.Enum.NONE)
            .setValueType(ValueType.Enum.STRING)
            .build();

    FeatureSpec featureSpec2 =
        FeatureSpec.newBuilder()
            .setEntity(ENTITY_NAME)
            .setId("test_entity.none.feature_2")
            .setName("feature_2")
            .setGranularity(Granularity.Enum.NONE)
            .setValueType(ValueType.Enum.STRING)
            .build();

    List<FeatureSpec> featureSpecs = Arrays.asList(featureSpec1, featureSpec2);
    List<String> entityIdsWithMissingEntity = new ArrayList<>(entityIds);
    entityIdsWithMissingEntity.add("100");
    bigTablePopulator.populate(ENTITY_NAME, entityIds, featureSpecs, now);
    List<FeatureValue> results =
        featureStorage.getFeature(ENTITY_NAME, entityIdsWithMissingEntity, featureSpecs);
    bigTablePopulator.validate(results, entityIds, featureSpecs);
  }

  @Test
  public void getFeatures_shouldWorkForOtherGranularity() {
    for (Granularity.Enum granularity : Granularity.Enum.values()) {
      if (granularity.equals(Enum.NONE) || granularity.equals(Enum.UNRECOGNIZED)) {
        continue;
      }
      FeatureSpec spec1 = createFeatureSpec("feature_1", granularity, ValueType.Enum.STRING);
      FeatureSpec spec2 = createFeatureSpec("feature_2", granularity, ValueType.Enum.STRING);

      List<FeatureSpec> featureSpecs = Arrays.asList(spec1, spec2);
      bigTablePopulator.populate(ENTITY_NAME, entityIds, featureSpecs, now);

      List<FeatureValue> result = featureStorage.getFeature(ENTITY_NAME, entityIds, featureSpecs);

      bigTablePopulator.validate(result, entityIds, featureSpecs);
    }
  }

  private FeatureSpec createFeatureSpec(
      String featureName, Enum granularity, ValueType.Enum valType) {
    String entityName = ENTITY_NAME;
    String featureId =
        String.format("%s.%s.%s", entityName, granularity.toString().toLowerCase(), featureName);
    FeatureSpec spec =
        FeatureSpec.newBuilder()
            .setEntity(entityName)
            .setId(featureId)
            .setName(featureName)
            .setGranularity(granularity)
            .setValueType(valType)
            .build();

    return spec;
  }

  private List<String> createEntityIds(int count) {
    List<String> entityIds = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      entityIds.add(String.valueOf(i));
    }
    return entityIds;
  }
}
