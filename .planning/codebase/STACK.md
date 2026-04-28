# Technology Stack

**Analysis Date:** 2026-04-28

## Languages

**Primary:**
- Java 8 (1.8) - Core platform, algorithms, distributed computing engine
- Python 3.7+ - Client bindings and API (`h2o-py`)
- R 3.1+ - Statistical client bindings (`h2o-r`)

**Secondary:**
- Groovy - Build scripts and tasks (`buildSrc`, Gradle)
- CoffeeScript 2.3.2 - Web UI (`h2o-web`)
- JavaScript - Flow web interface components

**Special Compatibility:**
- Java 7 (1.7) - MOJO genmodel (`h2o-genmodel`) for maximum deployment portability
- Java 17 - Google Drive persistence module (`h2o-persist-drive`)

## Runtime

**Environment:**
- JDK 8+ (Java 8 or later)
- Requires minimum 8GB RAM (16GB recommended for tests)
- Multi-JVM cluster architecture (tests spawn 5 JVMs)

**Package Manager:**
- Gradle 7.2 (via wrapper)
- Lockfile: Not present (using dependency resolution)
- Python: pip with `setup.py` and `requirements.txt`
- Node.js: npm/bower for web components
- R: CRAN with custom package structure

## Frameworks

**Core:**
- H2O Custom Framework 3.47.0 - Distributed ML platform with custom MRTask map/reduce
- Jetty 9.4.57.v20241219 - HTTP server (main assembly)
- Jetty 9.4.11.v20180605 - Hadoop 3.0 packages
- Jetty 8.2.0.v20160908 - Hadoop 2.x builds
- Servlet API 3.1.0 - Web server interface

**Testing:**
- Custom multi-node testing framework - Specialized H2O cluster tests
- JUnit 4.12 - Java unit tests
- TestNG 6.8 - Additional Java testing
- Mockito 2.18.3 - Mocking framework

**Build/Dev:**
- Gradle Shadow Plugin 7.0.0 - Fat JAR assembly
- Gradle Nexus Plugin 0.3 - Publishing
- JMH 0.5.2 - Java microbenchmarks
- License Plugin 0.16.1 - License management
- Node.js tools - CoffeeScript compiler, Bower, Puppeteer 1.10.0

## Key Dependencies

**Critical:**
- `joda-time:2.10.13` - Date/time parsing and manipulation
- `com.google.code.gson:2.9.1` - JSON serialization/deserialization
- `org.apache.commons:commons-math3:3.6.1` - Mathematical operations
- `gov.nist.math:jama:1.0.3` - Linear algebra
- `org.javassist:javassist:3.28.0-GA` - Bytecode manipulation for Iced serialization
- `com.github.luben:zstd-jni:1.5.6-2` - Compression
- `ai.h2o:h2o-tree-api:0.3.17` - Tree model API

**Infrastructure:**
- `com.amazonaws:aws-java-sdk-s3:1.12.705` - S3 storage integration
- `com.google.cloud:google-cloud-storage:2.13.1` - GCS integration
- `org.apache.hadoop:hadoop-hdfs-client:3.3.6` - HDFS storage
- `org.apache.hadoop:hadoop-common:3.3.6` - Hadoop base libraries
- `org.apache.httpcomponents:httpclient:4.5.2` - HTTP communications
- `ai.h2o:xgboost4j_2.12:1.6.1.24` - XGBoost native integration

**Data Formats:**
- `org.apache.parquet:parquet-hadoop:1.12.3` - Parquet file format (synced with Spark)
- `org.apache.avro:avro:1.11.4` - Avro serialization
- `org.apache.hive:hive-exec:1.1.0` - ORC format via Hive
- `net.sf.opencsv:opencsv:2.3` - CSV parsing

**Numerical Computing:**
- `com.github.fommil.netlib:*:1.1.2` - Native BLAS/LAPACK (netlib-java/MTJ)
- `net.sourceforge.f2j:arpack_combined_all:0.1` - ARPACK eigenvalue solver
- `com.googlecode.matrix-toolkits-java:mtj:1.0.4` - Matrix Toolkit for Java
- `com.github.wendykierp:JTransforms:3.1` - FFT transforms

**Python Client:**
- `requests` - HTTP client (required)
- `tabulate` - Table formatting (required)
- `pykerberos` / `winkerberos` / `gssapi` - Kerberos authentication

**Security:**
- `h2o-jaas-pam` - PAM authentication
- `h2o-ext-krbstandalone` - Kerberos standalone
- `com.nimbusds:nimbus-jose-jwt:9.37.4` - JWT/JOSE support

**Optional Extensions:**
- `org.python:jython:2.7.3` - Python interpreter for custom functions
- `ai.h2o:mojo2:2.8.1` - MOJO2 integration (requires DAI license)

## Configuration

**Environment:**
- Gradle properties via `gradle.properties`
- Optional `.env*` files (not committed, noted for environment-specific config)
- System properties for HTTPS: `TLSv1, TLSv1.1, TLSv1.2`
- Timeouts: 300s connection/socket for unreliable networks
- JVM args: `-Xmx1024M` minimum for full builds

**Build:**
- `build.gradle` - Root build configuration
- `settings.gradle` - Multi-module project structure (60+ modules)
- `gradle.properties` - Version (3.47.0), profiles, feature flags
- Profile system: Java (default), Hadoop targets (CDH, HDP, MapR, EMR, CDP)
- Feature toggles: `doFindbugs`, `doAnimalSniffer`, `doIncludeMojoPipeline`, `doUBench`

**Hadoop Targets:**
- CDH: 5.4-5.16, 6.0-6.3
- CDP: 7.0-7.2
- MapR: 4.0-7.0
- EMR: 6.10
- IOP: 4.2

## Platform Requirements

**Development:**
- JDK 8+ (Java 8 or later)
- Node.js (for Flow UI build)
- Python 3.7-3.11 with pip
- R 3.1+ with packages: RCurl, jsonlite, statmod, devtools, roxygen2, testthat
- Minimum 8GB RAM (16GB recommended)
- Gradle via wrapper (auto-downloaded)

**Production:**
- JDK 8+ runtime
- Deployment targets: Standalone JAR, Hadoop clusters, Kubernetes, Docker
- Docker base: Ubuntu 24.04 with OpenJDK 8 and Python 3.11
- Kubernetes support via `h2o-k8s` and `h2o-k8s-int` modules
- Multi-node cluster: UDP for heartbeats, TCP for bulk data transfer
- Exposed ports: 54321 (HTTP), 54322 (additional services)

**CI/CD:**
- GitHub Actions workflows for nightly builds, releases, Hadoop tests
- AWS integration: ECR registry (353750902984.dkr.ecr.us-east-1.amazonaws.com)
- Artifact storage: S3 (h2o-release.s3.amazonaws.com)

---

*Stack analysis: 2026-04-28*
