# Technical Debt Record: TD-002

## Title
**Physical Folder-Based Test Separation (`src/test/java` vs `src/it/java`) via Maven Failsafe & Build-Helper Plugin**

## Status
**Recorded / Deferred** (Priority: Medium)

## Date
2026-09-06

---

## 📌 Problem Statement & Context
Currently, all test classes across microservices (`smartpay-common`, `smartpay-ledger-service`, `smartpay-invoice-service`) reside under a single physical source root: `src/test/java`.

To differentiate between fast in-memory unit tests and heavy containerized integration tests, the platform currently uses **JUnit 5 Tags** (`@Tag("unit")` vs `@Tag("integration")`) combined with Maven profiles (`-Punit` and `-Pintegration`).

While this approach works and allows selective execution (`mvn test -Punit` in ~6s vs `mvn test -Pintegration` in ~30s), it relies on developer diligence to manually annotate every new test class with `@Tag("unit")` or `@Tag("integration")`. Untagged tests will run during bare `mvn test`, but would be omitted when executing targeted profiles.

---

## ⚖️ Architectural Tradeoff Analysis

### 1. Tag-Based Separation (Current State)
* **Pros**:
  * Works out-of-the-box with standard Maven directory conventions (`src/test/java`).
  * Requires no additional Maven plugins (such as `build-helper-maven-plugin`).
  * Flexible multi-dimensional classification (e.g. `@Tag("unit")`, `@Tag("architecture")`, `@Tag("concurrency")`).
* **Cons**:
  * Requires explicit manual tagging on every test class.
  * Unit tests and heavy integration tests (Testcontainers, Spring Boot context, HikariCP pools) share the same physical filesystem directory.
  * IDEs (IntelliJ IDEA, Eclipse, VS Code) do not visually separate unit tests from integration tests in the project tree.

### 2. Physical Folder-Based Separation (Target State)
* **Structure**:
  ```
  smartpay-service/
  ├── src/
  │   ├── main/java/                # Production application code
  │   ├── test/java/                # Pure unit tests only (Mock, POJO, fast, zero Spring context)
  │   └── it/java/                  # Integration tests only (PostgreSQL Testcontainers, gRPC, MockMvc)
  ```
* **Pros**:
  * **Zero Annotation Overhead**: Test type is determined by physical directory location rather than manual annotations.
  * **Strict Isolation**: Physically impossible to accidentally put a containerized test inside the fast unit test suite.
  * **Maven Lifecycle Alignment**: Standard Maven lifecycle separation:
    * `mvn test` runs only unit tests (`maven-surefire-plugin`).
    * `mvn verify` (or `mvn failsafe:integration-test`) runs integration tests (`maven-failsafe-plugin`).
  * **IDE Clarity**: Unit tests and Integration tests appear as distinct module source roots in developer IDEs.
* **Cons / Migration Effort**:
  * Requires configuring `build-helper-maven-plugin` to register `src/it/java` as an additional test source directory.
  * Requires setting up `maven-failsafe-plugin` with execution goals (`integration-test`, `verify`).
  * Moving files across all modules requires reorganizing filesystem paths and adjusting git history.

---

## 🛠️ Proposed Implementation & Remediation Plan

When scheduling this technical debt item for remediation, execute the following steps:

### Step 1: Configure Root `pom.xml`
Add `build-helper-maven-plugin` and `maven-failsafe-plugin` in `pluginManagement`:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <id>add-integration-test-sources</id>
            <phase>generate-test-sources</phase>
            <goals>
                <goal>add-test-source</goal>
            </goals>
            <configuration>
                <sources>
                    <source>src/it/java</source>
                </sources>
            </configuration>
        </execution>
        <execution>
            <id>add-integration-test-resources</id>
            <phase>generate-test-resources</phase>
            <goals>
                <goal>add-test-resource</goal>
            </goals>
            <configuration>
                <resources>
                    <resource>
                        <directory>src/it/resources</directory>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven-surefire-plugin.version}</version>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
            <include>**/*IntegrationTest.java</include>
        </includes>
        <environmentVariables>
            <DOCKER_HOST>unix:///Users/hozgan/.colima/default/docker.sock</DOCKER_HOST>
            <DOCKER_API_VERSION>1.44</DOCKER_API_VERSION>
            <TESTCONTAINERS_RYUK_DISABLED>true</TESTCONTAINERS_RYUK_DISABLED>
        </environmentVariables>
        <systemPropertyVariables>
            <api.version>1.44</api.version>
            <testcontainers.reuse.enable>true</testcontainers.reuse.enable>
        </systemPropertyVariables>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Step 2: Migrate Files to Physical Folders
* **Move to `src/test/java` (Unit tests only)**:
  * `MoneyTest.java`
  * `DomainModelsTest.java`
  * `UuidV7Test.java`
  * `MoneyJsonComponentTest.java`
  * `ArchitectureTest.java`
  * `FreightPricingEngineTest.java`
  * `InvoiceEntityTest.java`
  * `VehicleTypeConverterTest.java`
  * `LedgerDomainServiceTest.java`
  * `LedgerGrpcServiceTest.java`

* **Move to `src/it/java` (Integration tests only)**:
  * `TestcontainersConfiguration.java`
  * `EpodControllerTest.java`
  * `InvoiceControllerTest.java`
  * `InvoiceEpodIntegrationTest.java`
  * `LedgerTransferControllerTest.java`
  * `LedgerTransferIntegrationTest.java`
  * `LedgerGrpcIntegrationTest.java`
  * `ConcurrencyIntegrationTest.java`
  * `*ApplicationTests.java`

### Step 3: Developer Command Interface
Once migrated, the developer workflow will be:
```bash
# Run unit tests only (instant, <5s, no containers):
mvn test

# Run integration tests only (PostgreSQL Testcontainers, gRPC):
mvn failsafe:integration-test

# Run full verification (both unit and integration tests):
mvn verify
```

---

## 🎯 Acceptance Criteria for Resolution
1. All modules (`common`, `ledger`, `invoice`, `payment`, `payout`, `recon`, `gateway`) have distinct `src/test/java` and `src/it/java` directories.
2. `mvn test` executes solely unit tests without spinning up Docker containers or Testcontainers.
3. `mvn verify` runs the complete verification pipeline (compile $\rightarrow$ unit test $\rightarrow$ package $\rightarrow$ integration test $\rightarrow$ verify).
4. Remove manual `@Tag("unit")` and `@Tag("integration")` annotations from test classes.
