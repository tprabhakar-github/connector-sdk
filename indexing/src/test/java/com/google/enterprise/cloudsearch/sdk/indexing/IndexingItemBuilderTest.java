/*
 * Copyright © 2017 Google Inc.
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
package com.google.enterprise.cloudsearch.sdk.indexing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import com.google.api.services.cloudsearch.v1.model.Item;
import com.google.api.services.cloudsearch.v1.model.ItemMetadata;
import com.google.api.services.cloudsearch.v1.model.ItemStructuredData;
import com.google.api.services.cloudsearch.v1.model.ObjectDefinition;
import com.google.api.services.cloudsearch.v1.model.Schema;
import com.google.api.services.cloudsearch.v1.model.SearchQualityMetadata;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.enterprise.cloudsearch.sdk.config.Configuration.ResetConfigRule;
import com.google.enterprise.cloudsearch.sdk.config.Configuration.SetupConfigRule;
import com.google.enterprise.cloudsearch.sdk.indexing.IndexingItemBuilder.FieldOrValue;
import com.google.enterprise.cloudsearch.sdk.indexing.StructuredData.ResetStructuredDataRule;
import java.util.Collections;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for IndexingItemBuilder. */
@RunWith(MockitoJUnitRunner.class)
public class IndexingItemBuilderTest {
  @Rule public ResetConfigRule resetConfig = new ResetConfigRule();
  @Rule public SetupConfigRule setupConfig = SetupConfigRule.uninitialized();
  @Rule public ResetStructuredDataRule resetStructuredData = new ResetStructuredDataRule();

  @Test
  public void testItemName() {
    Item subject = new IndexingItemBuilder("foo")
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(new ItemMetadata())));
  }

  @Test
  public void testItem() {
    Item subject = new IndexingItemBuilder("foo")
        .setItemType(IndexingItemBuilder.ItemType.CONTAINER_ITEM)
        .setQueue("myqueue")
        .setVersion("myversion".getBytes(UTF_8))
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(new ItemMetadata())
            .setItemType("CONTAINER_ITEM")
            .setQueue("myqueue")
            .encodeVersion("myversion".getBytes(UTF_8))));
  }

  @Test
  public void testStructuredData() {
    Schema schema = new Schema();
    schema.setObjectDefinitions(
        Collections.singletonList(
            new ObjectDefinition()
                .setName("myObject")
                .setPropertyDefinitions(Collections.emptyList())));
    StructuredData.init(schema);

    Multimap<String, Object> values = ArrayListMultimap.create();
    Item subject = new IndexingItemBuilder("foo")
        .setObjectType("myObject")
        .setValues(values)
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(new ItemMetadata().setObjectType("myObject"))
            .setStructuredData(
                new ItemStructuredData()
                    .setObject(StructuredData.getStructuredData("myObject", values)))));
  }

  @Test
  public void testStructuredData_fromConfiguration() {
    Schema schema = new Schema();
    schema.setObjectDefinitions(
        Collections.singletonList(
            new ObjectDefinition()
                .setName("myObject")
                .setPropertyDefinitions(Collections.emptyList())));
    StructuredData.init(schema);

    Properties config = new Properties();
    config.put(IndexingItemBuilder.OBJECT_TYPE, "myObject");
    setupConfig.initConfig(config);

    Multimap<String, Object> values = ArrayListMultimap.create();
    Item subject = IndexingItemBuilder.fromConfiguration("foo")
        .setValues(values)
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(new ItemMetadata().setObjectType("myObject"))
            .setStructuredData(
                new ItemStructuredData()
                    .setObject(StructuredData.getStructuredData("myObject", values)))));
  }

  @Test
  public void testItemMetadata() {
    StructuredData.init(new Schema());

    Multimap<String, Object> values = ArrayListMultimap.create();
    values.put("beta", "two");
    values.put("beta", "early release");
    values.put("date", "Wed, 08 Aug 2018 15:48:17 +0000");

    Item subject = new IndexingItemBuilder("foo")
        .setValues(values)
        .setContainerName(null)
        .setHash("")
        .setMimeType("text/plain")
        .setTitle(FieldOrValue.withValue(""))
        .setSourceRepositoryUrl(FieldOrValue.withValue("beta"))
        .setContentLanguage(FieldOrValue.withField("beta"))
        .setUpdateTime(FieldOrValue.withField("date"))
        .setCreateTime(null)
        .setSearchQualityMetadata(new SearchQualityMetadata().setQuality(0.5d))
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(
                new ItemMetadata()
                .setMimeType("text/plain")
                .setSourceRepositoryUrl("beta")
                .setContentLanguage("two")
                .setUpdateTime("2018-08-08T15:48:17.000Z")
                .setSearchQualityMetadata(new SearchQualityMetadata().setQuality(0.5d))
              )));
  }

  /**
   * Tests that setters beat config, and non-empty fields beat default values.
   */
  @Test
  public void testFromConfiguration() {
    // Mix of fields and default values.
    Properties config = new Properties();
    config.put(IndexingItemBuilder.TITLE_VALUE, "Uncle Sam");
    config.put(IndexingItemBuilder.SOURCE_REPOSITORY_URL_FIELD, "url");
    config.put(IndexingItemBuilder.CONTENT_LANGUAGE_FIELD, "language");
    config.put(IndexingItemBuilder.UPDATE_TIME_FIELD, "publishDate");
    config.put(IndexingItemBuilder.UPDATE_TIME_VALUE, "2001-01-01T00:00:00Z");
    config.put(IndexingItemBuilder.CREATE_TIME_FIELD, "invalid");
    config.put(IndexingItemBuilder.CREATE_TIME_VALUE, "2001-01-01T00:00:00Z");
    setupConfig.initConfig(config);
    StructuredData.init(new Schema());

    // No values are given for "url" or "modifyDate".
    Multimap<String, Object> values = ArrayListMultimap.create();
    values.put("name", "My Name is Sam");
    values.put("publishDate", "Wed, 08 Aug 2018 15:48:17 +0000");
    values.put("language", "en-US");

    Item subject = IndexingItemBuilder.fromConfiguration("foo")
        .setValues(values)
        .setTitle(FieldOrValue.withField("name"))
        .setSourceRepositoryUrl(FieldOrValue.withValue("http://example.com/?id=42"))
        .setContentLanguage(FieldOrValue.withField("language"))
        .setUpdateTime(FieldOrValue.withField("modifyDate"))
        .setCreateTime(FieldOrValue.withValue(null))
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(
                new ItemMetadata()
                .setTitle("My Name is Sam")
                .setSourceRepositoryUrl("http://example.com/?id=42")
                .setContentLanguage("en-US")
                .setUpdateTime("2018-08-08T15:48:17.000Z")
                .setCreateTime("2001-01-01T00:00:00.000Z")
              )));
  }

  @Test
  public void testFromConfiguration_empty() {
    setupConfig.initConfig(new Properties());

    Item subject = IndexingItemBuilder.fromConfiguration("foo")
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(new ItemMetadata())));
  }

  @Test
  public void testFromConfiguration_fields() {
    Properties config = new Properties();
    config.put(IndexingItemBuilder.TITLE_FIELD, "name");
    config.put(IndexingItemBuilder.SOURCE_REPOSITORY_URL_FIELD, "url");
    config.put(IndexingItemBuilder.CONTENT_LANGUAGE_FIELD, "language");
    config.put(IndexingItemBuilder.UPDATE_TIME_FIELD, "publishDate");
    config.put(IndexingItemBuilder.CREATE_TIME_FIELD, "originDate");
    config.put(IndexingItemBuilder.CONTENT_LANGUAGE_FIELD, "lang");
    setupConfig.initConfig(config);
    StructuredData.init(new Schema());

    Multimap<String, Object> values = ArrayListMultimap.create();
    values.put("name", "My Name is Sam");
    values.put("url", "http://example.com/?id=42");
    values.put("publishDate", "Wed, 08 Aug 2018 15:48:17 +0000");
    values.put("originDate", "Fri, 07 Jul 2017 01:02:03 +0000");
    values.put("lang", "en-US");

    Item subject = IndexingItemBuilder.fromConfiguration("foo")
        .setValues(values)
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(
                new ItemMetadata()
                .setTitle("My Name is Sam")
                .setSourceRepositoryUrl("http://example.com/?id=42")
                .setUpdateTime("2018-08-08T15:48:17.000Z")
                .setCreateTime("2017-07-07T01:02:03.000Z")
                .setContentLanguage("en-US")
              )));
  }

  @Test
  public void testFromConfiguration_values() {
    Properties config = new Properties();
    config.put(IndexingItemBuilder.TITLE_VALUE, "name");
    config.put(IndexingItemBuilder.SOURCE_REPOSITORY_URL_VALUE, "url");
    config.put(IndexingItemBuilder.CONTENT_LANGUAGE_VALUE, "language");
    config.put(IndexingItemBuilder.UPDATE_TIME_VALUE, "2010-10-10T10:10:10-10:00");
    config.put(IndexingItemBuilder.CREATE_TIME_VALUE, "2001-01-01T00:00:00Z");
    config.put(IndexingItemBuilder.CONTENT_LANGUAGE_VALUE, "lang");
    setupConfig.initConfig(config);
    StructuredData.init(new Schema());

    Multimap<String, Object> values = ArrayListMultimap.create();
    values.put("name", "My Name is Sam");
    values.put("url", "http://example.com/?id=42");
    values.put("publishDate", "Wed, 08 Aug 2018 15:48:17 +0000");
    values.put("originDate", "Mon, 07 Aug 2017 01:02:03 +0000");
    values.put("lang", "en-US");

    Item subject = IndexingItemBuilder.fromConfiguration("foo")
        .setValues(values)
        .build();
    assertThat(subject,
        equalTo(
            new Item()
            .setName("foo")
            .setMetadata(
                new ItemMetadata()
                .setTitle("name")
                .setSourceRepositoryUrl("url")
                .setUpdateTime("2010-10-10T10:10:10.000-10:00")
                .setCreateTime("2001-01-01T00:00:00.000Z")
                .setContentLanguage("lang")
              )));
  }
}
