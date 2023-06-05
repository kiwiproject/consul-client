[![build](https://github.com/kiwiproject/consul-client/actions/workflows/build.yml/badge.svg)](https://github.com/kiwiproject/consul-client/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_consul-client&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=kiwiproject_consul-client)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=kiwiproject_consul-client&metric=coverage)](https://sonarcloud.io/summary/new_code?id=kiwiproject_consul-client)
[![CodeQL](https://github.com/kiwiproject/consul-client/actions/workflows/codeql.yml/badge.svg)](https://github.com/kiwiproject/consul-client/actions/workflows/codeql.yml)
[![javadoc](https://javadoc.io/badge2/org.kiwiproject/consul-client/javadoc.svg)](https://javadoc.io/doc/org.kiwiproject/consul-client)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.kiwiproject/consul-client)](https://central.sonatype.com/artifact/org.kiwiproject/consul-client/)

---

🥝 _This repository is a work in progress as we transition consul-client from rickfast to kiwiproject._ 🥝

---

Consul Client for Java
======================

Simple client for the Consul HTTP API.  For more information about the Consul HTTP API, go [here](https://developer.hashicorp.com/consul/api-docs).

Background
----------
This library was imported from [rickfast/consul-client](https://github.com/rickfast/consul-client), which is no longer
being maintained per a [note](https://github.com/rickfast/consul-client#notes-from-the-maintainer) from the original
maintainer.

Since we are still using this library in our services which use Dropwizard and Consul, we decided to import the original
repository and continue maintaining it for our own use, and anyone else who might want to use it. We make no guarantees
whatsoever about how long we will maintain it, and also plan to make our own changes such as changing the base package
name to `org.kiwiproject` to be consistent with our other libraries.

All other [kiwiproject](https://github.com/kiwiproject/) projects are MIT-licensed. However, because the original
`consul-client` uses the Apache 2.0 license, we are keeping the Apache 2.0 license (otherwise to switch to MIT we
would have to gain consent of all contributors, which we do not want to do).

Another thing to note is that we _imported_ this repository from the original, so that it is a "disconnected fork". We
did not want a reference to the original repository since it is no longer maintained and no changes here will ever
be pushed back upstream. Thus, while we maintain the history that this is a fork , it is completely disconnected and is
now a standalone (normal) repository.

Migrating from rickfast/consul-client
--------------------------------------------
For the initial version 0.5.0, most likely the only thing you need to change in your POM is the group ID and the version number.

However, if you are using `PolicyResponse` and/or `PolicyListResponse`, then you will need to change your code, since `datacenters` changed from `Optional<String>` to `Optional<List<String>>`, so code using either of those will no longer compile. This change was not avoidable, since the original type was incorrect.

Installation
-----------

In 0.13.x, both shaded and non-shaded JARs are provided. The shaded JAR has a `shaded` classifier, while the non-shaded JAR has no classifier. Note that this is a change from 0.12 and 0.11.

In 0.11.X and 0.12.x, the Consul JAR is a shaded JAR, with most dependencies included. This was done because a number of issues being files were related to dependency conflicts. The JAR is a bit bigger, but the HTTP + JSON libraries are now internal to the JAR. Only Guava is still a transitive dependency.

### Gradle:

#### Groovy:

```groovy
dependencies {
    implementation 'org.kiwiproject:consul-client:[version]'
}
```

#### Kotlin:

```kotlin
dependencies {
    implementation("org.kiwiproject:consul-client:[version]")
}
```

### Maven:

```xml
<dependencies>
    <dependency>
        <groupId>org.kiwiproject</groupId>
        <artifactId>consul-client</artifactId>
        <version>[version]</version>
    </dependency>
</dependencies>
```


Basic Usage
-----------

### Example 1: Connect to Consul.

```java
Consul client = Consul.builder().build(); // connect on localhost to default port 8500
```

### Example 2: Register and check your service in with Consul.

```java
AgentClient agentClient = client.agentClient();

String serviceId = "1";
Registration service = ImmutableRegistration.builder()
        .id(serviceId)
        .name("myService")
        .port(8080)
        .check(Registration.RegCheck.ttl(3L)) // registers with a TTL of 3 seconds
        .tags(List.of("tag1"))
        .meta(Map.of("version", "1.0"))
        .build();

agentClient.register(service);

// Check in with Consul (serviceId required only).
// Client will prepend "service:" for service level checks.
// Note that you need to continually check in before the TTL expires, otherwise your service's state will be marked as "critical".
agentClient.pass(serviceId);
```

### Example 3: Find available (healthy) services.

```java
HealthClient healthClient = client.healthClient();

// Discover only "passing" nodes
List<ServiceHealth> nodes = healthClient.getHealthyServiceInstances("DataService").getResponse();
```

### Example 4: Store key/values.

```java
KeyValueClient kvClient = client.keyValueClient();

kvClient.putValue("foo","bar");
        String value=kvClient.getValueAsString("foo").orElseThrow(); // bar
```

### Example 5: Subscribe to value change.

You can use the ConsulCache implementations to easily subscribe to Key-Value changes.

```java
final KeyValueClient kvClient = client.keyValueClient();

kvClient.putValue("foo", "bar");

KVCache cache = KVCache.newCache(kvClient, "foo");
cache.addListener(newValues -> {
    // Cache notifies all paths with "foo" the root path
    // If you want to watch only "foo" value, you must filter other paths
    Optional<Value> newValue = newValues.values().stream()
            .filter(value -> value.getKey().equals("foo"))
            .findAny();

    newValue.ifPresent(value -> {
        // Values are encoded in key/value store, decode it if needed
        Optional<String> decodedValue=newValue.get().getValueAsString();
        decodedValue.ifPresent(v->System.out.printf("Value is: %s%n",v)); //prints "bar"
        });
});
cache.start();
// ...
cache.stop();
```

### Example 6: Subscribe to healthy services

You can also use the ConsulCache implementations to easily subscribe to healthy service changes.

```java
HealthClient healthClient = client.healthClient();
String serviceName = "my-service";

ServiceHealthCache svHealth = ServiceHealthCache.newCache(healthClient, serviceName);
svHealth.addListener((Map<ServiceHealthKey, ServiceHealth> newValues) -> {
    // do something with updated server map
});
svHealth.start();
// ...
svHealth.stop();
```

### Example 7: Find Raft peers.

```java
StatusClient statusClient = client.statusClient();
statusClient.getPeers().forEach(System.out::println);
```

### Example 8: Find Raft leader.

```java
StatusClient statusClient = client.statusClient();
System.out.println(statusClient.getLeader()); // 127.0.0.1:8300
```

Development Notes
-----------

`consul-client` makes use of [Immutables](https://immutables.github.io/) to generate code for many of the value classes.
This provides a lot of functionality and benefit for little code, but it does require some additional development setup.

Official instructions are [here](https://immutables.github.io/apt.html), although you may want to change the target directories to the more gradle-like "generated/source/apt/main" and  "generated/source/apt/test" targets.

### Integration Tests

Runs consul with [Testcontainers](https://www.testcontainers.org/)
