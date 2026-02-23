# Running RedisAllInOne.java

## Prerequisites
1. **Java 11+** installed
2. **Redis** running on localhost:6379

### Start Redis
```bash
docker run -d --name redis-learning -p 6379:6379 redis:latest
```

## Option 1: Quick Run with Maven (Recommended)

Create a temporary `pom.xml`:
```bash
mkdir redis-java && cd redis-java
```

Save `pom.xml`:
```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>learn</groupId>
  <artifactId>redis-java</artifactId>
  <version>1.0</version>
  <dependencies>
    <dependency>
      <groupId>redis.clients</groupId>
      <artifactId>jedis</artifactId>
      <version>5.1.0</version>
    </dependency>
  </dependencies>
</project>
```

Then:
```bash
cp ../RedisAllInOne.java src/main/java/RedisAllInOne.java
mvn compile exec:java -Dexec.mainClass="RedisAllInOne"
```

## Option 2: Manual JAR Download

```bash
# Download JARs
curl -O https://repo1.maven.org/maven2/redis/clients/jedis/5.1.0/jedis-5.1.0.jar
curl -O https://repo1.maven.org/maven2/org/apache/commons/commons-pool2/2.12.0/commons-pool2-2.12.0.jar
curl -O https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar
curl -O https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar

# Compile
javac -cp "jedis-5.1.0.jar:commons-pool2-2.12.0.jar:slf4j-api-2.0.9.jar:." RedisAllInOne.java

# Run
java -cp "jedis-5.1.0.jar:commons-pool2-2.12.0.jar:slf4j-api-2.0.9.jar:slf4j-simple-2.0.9.jar:." RedisAllInOne
```

> On Windows, replace `:` with `;` in classpath.

## Python vs Java API Mapping

| Python (redis-py)         | Java (Jedis)                    |
|--------------------------|--------------------------------|
| `r.set('key', 'val')`   | `redis.set("key", "val")`     |
| `r.get('key')`          | `redis.get("key")`            |
| `r.setex('k', 60, 'v')` | `redis.setex("k", 60, "v")`  |
| `r.setnx('k', 'v')`    | `redis.setnx("k", "v")`      |
| `r.incr('k')`           | `redis.incr("k")`            |
| `r.lpush('k', 'v')`     | `redis.lpush("k", "v")`      |
| `r.blpop('k', timeout)` | `redis.blpop(timeout, "k")`  |
| `r.sadd('k', 'v1')`     | `redis.sadd("k", "v1")`      |
| `r.hset('k', mapping={})` | `redis.hset("k", map)`     |
| `r.hgetall('k')`        | `redis.hgetAll("k")`         |
| `r.zadd('k', {'m': 1})` | `redis.zadd("k", 1, "m")`    |
| `r.zrevrange('k',0,2, withscores=True)` | `redis.zrevrangeWithScores("k",0,2)` |

## What's Covered

| Data Structure | Examples | Interview Use Cases |
|---|---|---|
| **Strings** | SET, GET, SETEX, SETNX, MSET, INCR | Caching, sessions, counters, distributed locks |
| **Lists** | LPUSH, RPUSH, LPOP, BLPOP, LTRIM | Task queues, activity feeds, recent items |
| **Sets** | SADD, SMEMBERS, SINTER, SUNION, SDIFF | Unique visitors, tags, social graph |
| **Hashes** | HSET, HGET, HGETALL, HINCRBY | User profiles, shopping carts, feature flags |
| **Sorted Sets** | ZADD, ZSCORE, ZREVRANGE, ZPOPMIN | Leaderboards, priority queues, rate limiting |
