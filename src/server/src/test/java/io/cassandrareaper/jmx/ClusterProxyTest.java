/*
 * Copyright 2018-2018 The Last Pickle Ltd
 *
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

package io.cassandrareaper.jmx;

import io.cassandrareaper.AppContext;
import io.cassandrareaper.ReaperApplicationConfiguration;
import io.cassandrareaper.ReaperApplicationConfiguration.DatacenterAvailability;
import io.cassandrareaper.ReaperException;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClusterProxyTest {


  @Test
  public void nodeIsAccessibleThroughJmxSidecarTest() throws ReaperException {
    final AppContext context = new AppContext();
    context.config = new ReaperApplicationConfiguration();
    context.localNodeAddress = "127.0.0.1";
    context.localDatacenter = "dc1";
    context.localClusterName = "Test";

    context.config.setDatacenterAvailability(DatacenterAvailability.SIDECAR);
    JmxConnectionFactory jmxConnectionFactory = mock(JmxConnectionFactory.class);
    when(jmxConnectionFactory.getAccessibleDatacenters()).thenReturn(new HashSet<String>(Arrays.asList("dc1")));
    context.jmxConnectionFactory = jmxConnectionFactory;
    ClusterFacade clusterFacade = ClusterFacade.create(context);
    assertTrue(clusterFacade.nodeIsAccessibleThroughJmx(context.localDatacenter, context.localNodeAddress));
    assertFalse(clusterFacade.nodeIsAccessibleThroughJmx(context.localDatacenter, "127.0.0.2"));
  }

  @Test
  public void nodeIsAccessibleThroughJmxAllTest() throws ReaperException {
    final AppContext context = new AppContext();
    context.config = new ReaperApplicationConfiguration();
    context.localNodeAddress = "127.0.0.1";
    context.localDatacenter = "dc1";
    context.localClusterName = "Test";
    JmxConnectionFactory jmxConnectionFactory = mock(JmxConnectionFactory.class);
    when(jmxConnectionFactory.getAccessibleDatacenters()).thenReturn(new HashSet<String>(Arrays.asList("dc1")));
    context.jmxConnectionFactory = jmxConnectionFactory;

    context.config.setDatacenterAvailability(DatacenterAvailability.ALL);
    ClusterFacade clusterFacade = ClusterFacade.create(context);
    assertTrue(clusterFacade.nodeIsAccessibleThroughJmx(context.localDatacenter, context.localNodeAddress));
    assertTrue(clusterFacade.nodeIsAccessibleThroughJmx("dc2", "127.0.0.2"));
  }

  @Test
  public void nodeIsAccessibleThroughJmxLocalTest() throws ReaperException {
    final AppContext context = new AppContext();
    context.config = new ReaperApplicationConfiguration();
    context.localNodeAddress = "127.0.0.1";
    context.localDatacenter = "dc1";
    context.localClusterName = "Test";
    JmxConnectionFactory jmxConnectionFactory = mock(JmxConnectionFactory.class);
    when(jmxConnectionFactory.getAccessibleDatacenters()).thenReturn(new HashSet<String>(Arrays.asList("dc1")));
    context.jmxConnectionFactory = jmxConnectionFactory;

    context.config.setDatacenterAvailability(DatacenterAvailability.LOCAL);
    ClusterFacade clusterFacade = ClusterFacade.create(context);
    assertTrue(
        clusterFacade.nodeIsAccessibleThroughJmx(
            context.localDatacenter, context.localNodeAddress));
    assertTrue(
        clusterFacade.nodeIsAccessibleThroughJmx(
            "dc2", "127.0.0.2")); // it's in another DC but LOCAL allows attempting it
    assertTrue(clusterFacade.nodeIsAccessibleThroughJmx("dc1", "127.0.0.2")); // Should be accessible, same DC
  }

  @Test
  public void nodeIsAccessibleThroughJmxEachTest() throws ReaperException {
    final AppContext context = new AppContext();
    context.config = new ReaperApplicationConfiguration();
    context.localNodeAddress = "127.0.0.1";
    context.localDatacenter = "dc1";
    context.localClusterName = "Test";
    JmxConnectionFactory jmxConnectionFactory = mock(JmxConnectionFactory.class);
    when(jmxConnectionFactory.getAccessibleDatacenters()).thenReturn(new HashSet<String>(Arrays.asList("dc1")));
    context.jmxConnectionFactory = jmxConnectionFactory;

    context.config.setDatacenterAvailability(DatacenterAvailability.EACH);
    ClusterFacade clusterFacade = ClusterFacade.create(context);
    assertTrue(
        clusterFacade.nodeIsAccessibleThroughJmx(
            context.localDatacenter, context.localNodeAddress));
    assertFalse(
        clusterFacade.nodeIsAccessibleThroughJmx(
            "dc2", "127.0.0.2")); // Should not be accessible as it's in another DC
    assertTrue(clusterFacade.nodeIsAccessibleThroughJmx("dc1", "127.0.0.2")); // Should be accessible, same DC
  }
}