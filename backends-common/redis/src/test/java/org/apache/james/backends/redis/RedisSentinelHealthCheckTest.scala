/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.backends.redis

import java.util.concurrent.TimeUnit

import org.apache.james.backends.redis.RedisSentinelExtension.RedisSentinelCluster
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import reactor.core.scala.publisher.SMono

@ExtendWith(Array(classOf[RedisSentinelExtension]))
class RedisSentinelHealthCheckTest {
  var redisHealthCheck: RedisHealthCheck = _

  @BeforeEach
  def setup(redis: RedisSentinelCluster): Unit = {
    redisHealthCheck = new RedisHealthCheck(redis.redisSentinelContainerList.getRedisConfiguration)
  }

  @AfterEach
  def afterEach(redis: RedisSentinelCluster): Unit = {
    redis.redisMasterReplicaContainerList.unPauseMasterNode();
  }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def checkShouldReturnDegradedWhenRedisIsDown(redis: RedisSentinelCluster): Unit = {
    redis.redisMasterReplicaContainerList.pauseMasterNode()

    Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() => assertThat(SMono.fromPublisher(redisHealthCheck.check()).block().isDegraded).isTrue)
  }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRecovered(redis: RedisSentinelCluster): Unit = {
    redis.redisMasterReplicaContainerList.pauseMasterNode()
    redis.redisMasterReplicaContainerList.unPauseMasterNode()

    Awaitility.await()
      .pollInterval(2, TimeUnit.SECONDS)
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() => assertThat(SMono.fromPublisher(redisHealthCheck.check()).block().isHealthy).isTrue)
  }
}
