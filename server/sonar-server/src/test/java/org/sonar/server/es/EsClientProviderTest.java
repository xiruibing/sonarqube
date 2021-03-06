/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.es;

import java.net.InetAddress;
import org.assertj.core.api.Condition;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.TransportAddress;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.process.ProcessProperties;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class EsClientProviderTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public LogTester logTester = new LogTester();

  private Settings settings = new Settings();
  private EsClientProvider underTest = new EsClientProvider();
  private String localhost;

  @Before
  public void setUp() throws Exception {
    // mandatory property
    settings.setProperty(ProcessProperties.SEARCH_CLUSTER_NAME, "the_cluster_name");

    localhost = InetAddress.getLocalHost().getHostAddress();
  }

  @Test
  public void connection_to_local_es_when_cluster_mode_is_disabled() throws Exception {
    settings.setProperty(ProcessProperties.CLUSTER_ENABLED, false);
    settings.setProperty(ProcessProperties.SEARCH_HOST, localhost);
    settings.setProperty(ProcessProperties.SEARCH_PORT, 8080);

    EsClient client = underTest.provide(settings);
    TransportClient transportClient = (TransportClient) client.nativeClient();
    assertThat(transportClient.transportAddresses()).hasSize(1);
    TransportAddress address = transportClient.transportAddresses().get(0);
    assertThat(address.getAddress()).isEqualTo(localhost);
    assertThat(address.getPort()).isEqualTo(8080);
    assertThat(logTester.logs(LoggerLevel.INFO)).has(new Condition<>(s -> s.contains("Connected to local Elasticsearch: [" + localhost + ":8080]"), ""));

    // keep in cache
    assertThat(underTest.provide(settings)).isSameAs(client);
  }

  @Test
  public void connection_to_remote_es_nodes_when_cluster_mode_is_enabled_and_local_es_is_disabled() throws Exception {
    settings.setProperty(ProcessProperties.CLUSTER_ENABLED, true);
    settings.setProperty(ProcessProperties.CLUSTER_SEARCH_DISABLED, true);
    settings.setProperty(ProcessProperties.CLUSTER_SEARCH_HOSTS, format("%s:8080,%s:8081", localhost, localhost));

    EsClient client = underTest.provide(settings);
    TransportClient transportClient = (TransportClient) client.nativeClient();
    assertThat(transportClient.transportAddresses()).hasSize(2);
    TransportAddress address = transportClient.transportAddresses().get(0);
    assertThat(address.getAddress()).isEqualTo(localhost);
    assertThat(address.getPort()).isEqualTo(8080);
    address = transportClient.transportAddresses().get(1);
    assertThat(address.getAddress()).isEqualTo(localhost);
    assertThat(address.getPort()).isEqualTo(8081);
    assertThat(logTester.logs(LoggerLevel.INFO)).has(new Condition<>(s -> s.contains("Connected to remote Elasticsearch: [" + localhost + ":8080, " + localhost + ":8081]"), ""));

    // keep in cache
    assertThat(underTest.provide(settings)).isSameAs(client);
  }

  @Test
  public void fail_if_cluster_host_is_badly_formatted() throws Exception {
    settings.setProperty(ProcessProperties.CLUSTER_ENABLED, true);
    settings.setProperty(ProcessProperties.CLUSTER_SEARCH_DISABLED, true);
    settings.setProperty(ProcessProperties.CLUSTER_SEARCH_HOSTS, "missing_colon");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Badly formatted Elasticsearch host: missing_colon");
    underTest.provide(settings);
  }
}
