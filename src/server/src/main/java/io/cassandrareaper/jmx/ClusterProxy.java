/*
 * Copyright 2017-2017 Spotify AB
 * Copyright 2017-2019 The Last Pickle Ltd
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
import io.cassandrareaper.ReaperApplicationConfiguration.DatacenterAvailability;
import io.cassandrareaper.ReaperException;
import io.cassandrareaper.core.Cluster;
import io.cassandrareaper.core.Compaction;
import io.cassandrareaper.core.Node;
import io.cassandrareaper.core.Segment;
import io.cassandrareaper.resources.view.NodesStatus;
import io.cassandrareaper.service.RingRange;
import io.cassandrareaper.storage.IDistributedStorage;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;

import com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author adejanovski
 *
 */
/**
 * @author adejanovski
 *
 */
public final class ClusterProxy {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterProxy.class);
  private static final String LOCALHOST = "127.0.0.1";
  private final AppContext context;

  private ClusterProxy(AppContext context) {
    this.context = context;
  }

  public static ClusterProxy create(AppContext context) {
    return new ClusterProxy(context);
  }

  private JmxProxy connectAnyNode(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    return context.jmxConnectionFactory.connectAny(
        endpoints
            .stream()
            .map(host -> Node.builder().withCluster(cluster).withHostname(host).build())
            .collect(Collectors.toList()));
  }

  private JmxProxy connectNode(Node node) throws ReaperException, InterruptedException {
    return context.jmxConnectionFactory.connect(node);
  }

  /**
   * Pre-heats JMX connections to all provided endpoints.
   * In EACH, LOCAL and ALL : connect directly to any available node
   * In SIDECAR : We skip that code path as we don’t need to pre-heat connections
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return a JmxProxy object
   * @throws ReaperException any runtime exception we catch
   */
  public JmxProxy preHeatJmxConnections(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    Preconditions.checkArgument(!context.config.isInSidecarMode());
    return connectAnyNode(cluster, endpoints);
  }

  /**
   * Connect to any of the provided endpoints and allow enforcing to localhost for sidecar mode.
   * In EACH, LOCAL and ALL : connect directly to any available node
   * In SIDECAR : We skip that code path as we don’t need to pre-heat connections
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return a JmxProxy object
   * @throws ReaperException any runtime exception we catch
   */
  public JmxProxy connectAndAllowSidecar(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    return connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
  }

  /**
   * Get the cluster name from any of the provided endpoints.
   * In EACH, LOCAL and ALL : connect directly to any available node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return the cluster name
   * @throws ReaperException any runtime exception we catch
   */
  public String getClusterName(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
    return jmxProxy.getClusterName();
  }

  /**
   * Get the cluster name that the node belongs to.
   *
   * @param node the node to connect to
   * @return the cluster name
   * @throws ReaperException any runtime exception we catch in the process
   * @throws InterruptedException if the JMX connection gets interrupted
   */
  public String getClusterName(Node node) throws ReaperException, InterruptedException {
    JmxProxy jmxProxy = connectNode(node);
    return jmxProxy.getClusterName();
  }

  /**
   * Get the partitioner in use from any of the provided endpoints.
   * In EACH, LOCAL and ALL : connect directly to any available node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return the partitioner in use on the cluster
   * @throws ReaperException any runtime exception we catch
   */
  public String getPartitioner(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
    return jmxProxy.getPartitioner();
  }

  /**
   * Get the list of live nodes in the cluster.
   * In EACH, LOCAL and ALL : connect directly to any available node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @return the list of live endpoints in the cluster
   * @throws ReaperException any runtime exception we catch
   */
  public List<String> getLiveNodes(Cluster cluster) throws ReaperException {
    return getLiveNodes(cluster, cluster.getSeedHosts());
  }

  /**
   * Get the list of live nodes in the cluster from any of the provided endpoints.
   * In EACH, LOCAL and ALL : connect directly to any available node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return the list of live endpoints in the cluster
   * @throws ReaperException any runtime exception we catch
   */
  public List<String> getLiveNodes(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
    return jmxProxy.getLiveNodes();
  }

  /**
   * Get the status of all nodes in the cluster.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return a NodeStatus object with all nodes state
   * @throws ReaperException any runtime exception we catch
   */
  public NodesStatus getNodesStatus(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
    FailureDetectorProxy proxy = FailureDetectorProxy.create(jmxProxy);

    return new NodesStatus(
        jmxProxy.getHost(), proxy.getAllEndpointsState(), proxy.getSimpleStates());
  }

  /**
   * Get the version of Cassandra in use in the cluster.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @return the version of Cassandra used
   * @throws ReaperException any runtime exception we catch
   */
  public String getCassandraVersion(Cluster cluster) throws ReaperException {
    return getCassandraVersion(cluster, cluster.getSeedHosts());
  }

  /**
   * Get the version of Cassandra in use in the cluster.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param endpoints the list of endpoints to connect to
   * @return the version of Cassandra used
   * @throws ReaperException any runtime exception we catch
   */
  public String getCassandraVersion(Cluster cluster, Collection<String> endpoints) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(endpoints));
    return jmxProxy.getCassandraVersion();
  }

  /**
   * Get the list of tokens of the cluster.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @return the list of tokens as BigInteger
   * @throws ReaperException any runtime exception we catch
   */
  public List<BigInteger> getTokens(Cluster cluster) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(cluster.getSeedHosts()));
    return jmxProxy.getTokens();
  }

  /**
   * Get a map of all the token ranges with the list of replicas. In EACH, LOCAL and ALL : connect
   * directly to any provided node to get the information In SIDECAR : Enforce connecting to the
   * local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param keyspaceName the ks to get a map of token ranges for
   * @return a map of token ranges with endpoints as items
   * @throws ReaperException any runtime exception we catch
   */
  public Map<List<String>, List<String>> getRangeToEndpointMap(Cluster cluster, String keyspaceName)
      throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(cluster.getSeedHosts()));
    return jmxProxy.getRangeToEndpointMap(keyspaceName);
  }

  /**
   * Get a list of table names for a specific keyspace.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @param keyspaceName a keyspace name
   * @return a list of table names
   * @throws ReaperException any runtime exception we catch
   */
  public Set<String> getTableNamesForKeyspace(Cluster cluster, String keyspaceName) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(cluster.getSeedHosts()));
    return jmxProxy.getTableNamesForKeyspace(keyspaceName);
  }

  /**
   * List the keyspaces of the cluster.
   *
   * @param cluster the cluster to connect to
   * @return a list of keyspace names
   * @throws ReaperException any runtime exception
   */
  public List<String> getKeyspaces(Cluster cluster) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(cluster.getSeedHosts()));
    return jmxProxy.getKeyspaces();
  }

  /**
   * Get a map of endpoints with the associated host id.
   * In EACH, LOCAL and ALL : connect directly to any provided node to get the information
   * In SIDECAR : Enforce connecting to the local node to get the information
   *
   * @param cluster the cluster to connect to
   * @return the map of endpoints to host id
   * @throws ReaperException any runtime exception we catch
   */
  public Map<String, String> getEndpointToHostId(Cluster cluster) throws ReaperException {
    JmxProxy jmxProxy = connectAnyNode(cluster, enforceLocalNodeForSidecar(cluster.getSeedHosts()));
    return jmxProxy.getEndpointToHostId();
  }

  /**
   * Get the list of replicas for a token range.
   *
   * @param cluster the cluster to connect to
   * @param keyspace the keyspace to get the replicas for
   * @param segment the token range for which we want the list of replicas
   * @return a list of endpoints
   */
  public List<String> tokenRangeToEndpoint(Cluster cluster, String keyspace, Segment segment) {
    Set<Map.Entry<List<String>, List<String>>> entries;
    try {
      entries = getRangeToEndpointMap(cluster, keyspace).entrySet();
    } catch (ReaperException e) {
      LOG.error("[tokenRangeToEndpoint] no replicas found for token range {}", segment, e);
      return Lists.newArrayList();
    }

    for (Map.Entry<List<String>, List<String>> entry : entries) {
      BigInteger rangeStart = new BigInteger(entry.getKey().get(0));
      BigInteger rangeEnd = new BigInteger(entry.getKey().get(1));
      if (new RingRange(rangeStart, rangeEnd).encloses(segment.getTokenRanges().get(0))) {
        return entry.getValue();
      }
    }
    LOG.error("[tokenRangeToEndpoint] no replicas found for token range {}", segment);
    LOG.debug("[tokenRangeToEndpoint] checked token ranges were {}", entries);
    return Lists.newArrayList();
  }

  /**
   * Get the ranges for the local node (only for sidecar mode).
   *
   * @param cluster the cluster to connect to
   * @param keyspace the keyspace we're getting the ranges for.
   * @return the list of local token ranges
   * @throws ReaperException any runtime exception we catch in the process
   */
  public List<RingRange> getRangesForLocalEndpoint(Cluster cluster, String keyspace) throws ReaperException {
    Preconditions.checkArgument(context.config.isInSidecarMode(), "This method is only allowed in sidecar mode");
    List<RingRange> localRanges = Lists.newArrayList();
    try {
      Map<List<String>, List<String>> ranges = getRangeToEndpointMap(cluster, keyspace);
      JmxProxy jmxProxy = connectAndAllowSidecar(cluster, Arrays.asList(LOCALHOST));
      String localEndpoint = jmxProxy.getLocalEndpoint();
      // Filtering ranges for which the local node is a replica
      // For local mode
      ranges
          .entrySet()
          .stream()
          .forEach(entry -> {
            if (entry.getValue().contains(localEndpoint)) {
              localRanges.add(
                  new RingRange(new BigInteger(entry.getKey().get(0)), new BigInteger(entry.getKey().get(1))));
            }
          });

      LOG.info("LOCAL RANGES {}", localRanges);
      return localRanges;
    } catch (RuntimeException e) {
      LOG.error(e.getMessage());
      throw new ReaperException(e.getMessage(), e);
    }
  }

  /**
   * Get the datacenter of a specific endpoint.
   *
   * @param cluster the cluster to connect to
   * @param endpoint the node which we're trying to locate in the topology
   * @return the datacenter this endpoint belongs to
   * @throws ReaperException any runtime exception we catch in the process
   */
  public String getDatacenter(Cluster cluster, String endpoint) throws ReaperException {
    JmxProxy jmxProxy = connectAndAllowSidecar(cluster, cluster.getSeedHosts());
    return EndpointSnitchInfoProxy.create(jmxProxy).getDataCenter(endpoint);
  }

  /**
   * Get the datacenter of a specific endpoint.
   *
   * @param node the node to connect to
   * @return the datacenter this endpoint belongs to
   * @throws ReaperException any runtime exception we catch in the process
   * @throws InterruptedException in case the JMX connection gets interrupted
   */
  public String getDatacenter(Node node) throws ReaperException, InterruptedException {
    JmxProxy jmxProxy = connectNode(node);
    return EndpointSnitchInfoProxy.create(jmxProxy).getDataCenter();
  }

  /**
   * Get the endpoint name/ip indentifying the node in the cluster.
   *
   * @param node the node to connect to
   * @return the endpoint as a string
   * @throws ReaperException any runtime exception we catch in the process
   * @throws InterruptedException if the JMX connection gets interrupted
   */
  public String getLocalEndpoint(Node node) throws ReaperException, InterruptedException {
    JmxProxy jmxProxy = connectNode(node);
    return jmxProxy.getLocalEndpoint();
  }

  /**
   * Retrieve a map of endpoints with the associated tokens.
   *
   * @param cluster the cluster we want to retrieve the tokens from
   * @return a map of nodes with the list of tokens as items
   * @throws ReaperException any runtime exception we catch in the process
   */
  public Map<String, List<String>> getTokensByNode(Cluster cluster) throws ReaperException {
    JmxProxy jmxProxy = connectAndAllowSidecar(cluster, cluster.getSeedHosts());
    return StorageServiceProxy.create(jmxProxy).getTokensByNode();
  }

  /**
   * List running compactions on a specific node either through JMX or through the backend.
   *
   * @param node the node to get the compactions from.
   * @return a list of compactions
   * @throws MalformedObjectNameException ¯\_(ツ)_/¯
   * @throws ReflectionException ¯\_(ツ)_/¯
   * @throws ReaperException any runtime exception we catch in the process
   * @throws InterruptedException in case the JMX connection gets interrupted
   * @throws IOException errors in parsing JSON encoded compaction objects
   */
  public List<Compaction> listActiveCompactions(Node node)
      throws MalformedObjectNameException, ReflectionException, ReaperException,
          InterruptedException, IOException {
    String nodeDc = getDatacenter(node);
    if (nodeIsAccessibleThroughJmx(nodeDc, node.getHostname())) {
      // We have direct JMX access to the node
      return listActiveCompactionsDirect(node);
    } else {
      // We don't have access to the node through JMX, so we'll get data from the database
      LOG.info("Node {} in DC {} is not accessible through JMX", node.getHostname(), nodeDc);
      return ((IDistributedStorage)context.storage).listCompactions(node.getCluster().getName(), node.getHostname());
    }
  }

  /**
   * List running compactions on a specific node by connecting directly to it through JMX.
   *
   * @param node the node to get the compactions from.
   * @return a list of compactions
   * @throws MalformedObjectNameException ¯\_(ツ)_/¯
   * @throws ReflectionException ¯\_(ツ)_/¯
   * @throws ReaperException any runtime exception we catch in the process
   * @throws InterruptedException in case the JMX connection gets interrupted
   */
  public List<Compaction> listActiveCompactionsDirect(Node node)
      throws ReaperException, InterruptedException, MalformedObjectNameException,
          ReflectionException {
    JmxProxy jmxProxy = connectAndAllowSidecar(node.getCluster(), Arrays.asList(node.getHostname()));
    return CompactionProxy.create(jmxProxy, context.metricRegistry).listActiveCompactions();
  }

  /**
   * Replaces the list of endpoints with LOCALHOST if we're in sidecar mode.
   *
   * @param endpoints the list of nodes to connect to
   * @return a list of endpoints possibly replaced by LOCALHOST only
   */
  private List<String> enforceLocalNodeForSidecar(Collection<String> endpoints) {
    List<String> actualEndpoints = new ArrayList<String>(endpoints);
    if (context.config.isInSidecarMode()) {
      actualEndpoints = Arrays.asList(context.config.getEnforcedLocalNode().orElse(LOCALHOST));
    }
    return actualEndpoints;
  }

  /**
   * Checks if Reaper can access the target node through JMX directly.
   * The result will depend on the chosen datacenterAvailability setting and the datacenter the node belongs to.
   *
   * @param nodeDc datacenter of the target node
   * @param node the target node
   * @return true if the node is supposedly accessible through JMX, otherwise false
   */
  public boolean nodeIsAccessibleThroughJmx(String nodeDc, String node) {
    return DatacenterAvailability.ALL == context.config.getDatacenterAvailability()
        || DatacenterAvailability.LOCAL == context.config.getDatacenterAvailability()
        || (DatacenterAvailability.EACH == context.config.getDatacenterAvailability()
            && context.accessibleDatacenters.contains(nodeDc))
        || (DatacenterAvailability.SIDECAR == context.config.getDatacenterAvailability()
            && node.equals(context.localNodeAddress));
  }
}
