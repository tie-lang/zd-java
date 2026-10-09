# 发布指南 / Publishing Guide

zd-java 的发布目标为 **Maven Central**（经 Sonatype Central Portal）。发布配置全部收在
pom 的 `release` profile 里——日常 `mvn package` / `gradle probe` 不受影响。

zd-java publishes to **Maven Central** (via the Sonatype Central Portal). All release
configuration is confined to the pom's `release` profile — everyday `mvn package` /
`gradle probe` are unaffected.

## 坐标 / Coordinates

```xml
<dependency>
    <groupId>org.tielang</groupId>
    <artifactId>zd-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 一次性准备 / One-time Setup

1. **命名空间**：在 <https://central.sonatype.com> 以 GitHub 组织 `tie-lang` 登录并申请
   `org.tielang` 命名空间（用组织下的 DNS TXT 或 GitHub 仓库校验）。
   / Claim the `org.tielang` namespace on Central Portal signed in with the `tie-lang`
   GitHub organization (verified via a DNS TXT record or a GitHub repository).
2. **凭据**：在 Central Portal 生成 user token，写入 `~/.m2/settings.xml`：

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

   / Generate a user token on Central Portal and store it under server id `central`.
3. **GPG**：生成并发布公钥（Central 校验签名）：

   ```bash
   gpg --gen-key                 # 若尚无密钥 / if you have no key yet
   gpg --list-keys               # 记下 KEY_ID / note the KEY_ID
   gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
   ```

## 发布 / Release

```bash
# 升版本号：pom 的 <version>、README 坐标、CHANGELOG 三处同步
mvn -Prelease clean deploy      # source/javadoc jar + GPG 签名 + 上传 Central Portal
```

`autoPublish=false`：上传后进入 Central Portal 的 **Pending** 状态，需在网页上人工点击
Publish 才会同步到 Maven Central（留一道人工闸）。

With `autoPublish=false`, the upload lands in the Central Portal's **Pending** state and
requires a manual Publish click before it reaches Maven Central — a deliberate manual
gate.

## 替代通道 / Alternative Channels

在本组织完成 Central 校验之前，库可以经以下方式消费（这是当前 Subterra 的做法）：

Before the namespace is verified, consume the library as follows (what Subterra does
today):

* **Gradle composite build**（无需发布 / no publishing needed）：

  ```groovy
  // settings.gradle
  includeBuild('path/to/zd-java')
  ```
  ```groovy
  // build.gradle
  implementation 'org.tielang:zd-java:0.1.0'
  ```

* **本地 Maven 仓**：`mvn install` 后任何 Maven/Gradle 构建都可用坐标解析。
  / **Local Maven repo**: after `mvn install`, ordinary Maven/Gradle builds resolve the
  coordinates.

* **直接源码**：`javac -d out $(find src/main/java -name "*.java")`（零依赖，无需构建工具）。
  / **Straight sources**: zero dependencies, no build tool required.

## 发布检查单 / Release Checklist

* [ ] `ZdProbe` 全绿（`./gradlew probe` 或 `java -cp out org.tielang.zd.ZdProbe`）
* [ ] `scripts/tsha1f-kat.txt` 与 tiec `tests/tsha_probe/gen_tsha1_core.py` 重新对拍一致
* [ ] pom `<version>` / README 坐标 / CHANGELOG 三处版本号同步
* [ ] LICENSE 与 `TPL 仓本地副本 /<版本>` 逐字节一致（换版时双写）
* [ ] CHANGELOG 补齐本版条目（中英双语）
* [ ] 若字节布局有变：同步 tie-spec 仓的 KAT 向量集（见 docs/ROAD.md 设计纪律）
