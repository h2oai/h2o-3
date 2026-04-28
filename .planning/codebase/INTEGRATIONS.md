# External Integrations

**Analysis Date:** 2026-04-28

## APIs & External Services

**Cloud Storage:**
- Amazon S3 - Data persistence and model storage
  - SDK/Client: `aws-java-sdk-s3:1.12.705`
  - Module: `h2o-persist-s3`
  - Auth: AWS credentials via environment or IAM roles
  - Additional: `aws-java-sdk-sts` for WebIdentityTokenCredentialsProvider
  - Test mock: `s3mock:2.1.28` (Adobe)

- Google Cloud Storage - Data persistence
  - SDK/Client: `google-cloud-storage:2.13.1`
  - Module: `h2o-persist-gcs`
  - Auth: GCP service account credentials
  - CVE patches: protobuf-java 3.25.5 (fixes CVE-2024-7254)

- Google Drive - File persistence
  - Module: `h2o-persist-drive`
  - Requires: Java 17 (newer than platform default)

**HTTP/Web Services:**
- HTTP/HTTPS file access - Generic HTTP data sources
  - Module: `h2o-persist-http`
  - Client: `httpclient:4.5.2`

**Machine Learning Extensions:**
- XGBoost - Gradient boosting library
  - Native integration: `ai.h2o:xgboost4j_2.12:1.6.1.24`
  - GPU support: `xgboost4j-linux-gpuv4`
  - Platforms: Linux (minimal/GPU), macOS (minimal)
  - Module: `h2o-ext-xgboost`
  - Genmodel: `h2o-genmodel-ext-xgboost`
  - Scala dependencies excluded (2.12 libraries, Akka, ScalaTest)

- H2O Steam - Enterprise deployment platform
  - Module: `h2o-ext-steam`
  - Assembly: `h2o-assemblies:steam` (minimal Jetty configuration)

- MOJO2 Pipeline - DAI integration
  - Version: `mojo2version:2.8.1`
  - Module: `h2o-ext-mojo-pipeline`
  - Requires: DAI license
  - Genmodel: `h2o-genmodel-ext-mojo-pipeline`

- JGraphT - Graph algorithms
  - Module: `h2o-genmodel-ext-jgrapht`

- DeepWater - Deep learning integration
  - Module: `h2o-genmodel-ext-deepwater`

**Build & Development Services:**
- Minio - S3-compatible storage for builds
  - Client: `io.minio:minio:3.0.3`
  - Location: `buildSrc` dependency

## Data Storage

**Databases:**
- HDFS (Hadoop Distributed File System)
  - Connection: Hadoop configuration files (`core-site.xml`, `hdfs-site.xml`)
  - Client: `hadoop-hdfs-client:3.3.6`
  - Module: `h2o-persist-hdfs`
  - Supports: S3A via `hadoop-aws` with AWS SDK integration
  - DynamoDB: `aws-java-sdk-dynamodb` for S3A metadata

- Hive - SQL on Hadoop
  - Integration: `h2o-hive` module
  - Import: Direct table import via `ImportHiveTableHandler`
  - Dependencies: Hadoop 2.8.4, hive-exec 1.1.0 (for ORC parser)
  - Shims: `hive-shims-common:2.3.9` for Hadoop 3 compatibility

**File Storage:**
- Local filesystem - Default persistence
- Distributed storage - HDFS, S3, GCS, HTTP
- Chunk-based storage - Via H2O DKV (Distributed Key-Value store)

**Caching:**
- In-memory DKV - Custom distributed key-value store
  - Consistent hashing for key distribution
  - Paxos-based locking
  - UDP for small messages, TCP for bulk data

## Authentication & Identity

**Auth Providers:**
- PAM (Pluggable Authentication Modules)
  - Module: `h2o-jaas-pam`
  - Platform: Linux/Unix systems

- Kerberos
  - Standalone: `h2o-ext-krbstandalone`
  - Python clients: `pykerberos` (Unix), `winkerberos` (Windows), `gssapi`
  - JWT/JOSE: `nimbus-jose-jwt:9.37.4`

- JAAS (Java Authentication and Authorization Service)
  - Module: `h2o-jaas-pam`
  - Excluded from Steam assembly for security

**Security Modules:**
- `h2o-security` - Base security utilities
- `h2o-logging-safe4j` - Safe logging (redact sensitive data)

## Monitoring & Observability

**Error Tracking:**
- Custom logging framework via `h2o-logger`
- Implementations:
  - Classic: `h2o-logging-impl-classic` (default)
  - Log4j2: `h2o-logging-impl-log4j2`
- Apache Log4j transitively included (legacy)

**Logs:**
- File-based: `build/h2o_<node_ip>_<port>.log`
- API: `water.util.Log` (Log.info, Log.warn, Log.err)
- Metrics: `SteamMetricsHandler`, `WaterMeterCpuTicksHandler`, `WaterMeterIoHandler`

**Metrics & Monitoring:**
- JMH benchmarks - Via `jmh-gradle-plugin:0.5.2`
  - Modules: `h2o-core`, `h2o-algos`, `h2o-ext-target-encoder`
  - Upload: S3 bucket for shared results (requires AWS credentials)
- Timeline API - `TimelineHandler` for execution tracking
- System monitoring: `oshi-core:5.7.4` (OS and Hardware Information)

## CI/CD & Deployment

**Hosting:**
- AWS - Primary infrastructure
  - ECR: `353750902984.dkr.ecr.us-east-1.amazonaws.com`
  - Region: `us-east-1`
  - IAM Role: `arn:aws:iam::353750902984:role/GitHub-OIDC-Role`

- Docker Hub - Public images
  - Base: Ubuntu 24.04 + OpenJDK 8 + Python 3.11
  - Assembly: `h2o-open-source-k8s` (uses main assembly)

- Kubernetes - Container orchestration
  - Modules: `h2o-k8s`, `h2o-k8s-int`, `h2o-k8s-comp`
  - Helm charts: `h2o-helm/` directory

**CI Pipeline:**
- GitHub Actions
  - Workflows: Nightly builds, Hadoop multi-node tests, releases
  - Files: `.github/workflows/nightly-build.yml`, `nightly-hadoop-multinode.yml`
  - Publishing: PyPI (`release-publish-pypi.yml`), Nexus (`release-publish-nexus.yml`)

**Artifact Repositories:**
- Maven Central - Primary public repository
- Nexus - Internal/staging repository (commented out in current config)
  - Location: `http://nexus.h2o.ai:8081/repository` (disabled)
- Sonatype OSS - Staging repository (optional)
- PyPI - Python package distribution
- CRAN - R package distribution

**Build Artifacts:**
- Main assembly: `h2o-assemblies/main/build/libs/h2o.jar`
- Steam assembly: `h2o-assemblies/steam/build/libs/h2o.jar`
- Minimal assembly: `h2o-assemblies/minimal/`
- Genmodel assembly: `h2o-assemblies/genmodel/`
- Hadoop assemblies: `h2o-hadoop-2/`, `h2o-hadoop-3/` (multiple distributions)

## Environment Configuration

**Required env vars:**
- `BUILD_HADOOP` - Enable Hadoop assembly builds
- `H2O_TARGET` - Filter specific Hadoop targets (e.g., "cdh6.3,hdp2.6")
- `JUNIT_CORE_SITE_PATH` - Path to `core-site.xml` for S3 tests
- `CI` - Detect CI environment
- `AWS_REGION` - AWS region for builds (us-east-1)

**Optional env vars:**
- `enableMavenLocal` - Use local Maven cache
- `doCI` - Force CI mode
- `doRelease` - Release build mode
- `doFindbugs` - Enable FindBugs analysis
- `doAnimalSniffer` - Verify Java API compatibility
- `doIncludeMojoPipeline` - Include MOJO Pipeline in default jar
- `doUBench` - Include micro benchmarks
- `doUploadUBenchResults` - Upload benchmark results to S3

**Secrets location:**
- AWS credentials - Environment or IAM instance profile
- GCP service account - JSON key file (path not hardcoded)
- S3 config - `core-site.xml` (path via `JUNIT_CORE_SITE_PATH`)
- `.env*` files - Local only, gitignored

**Gradle properties:**
- `systemProp.https.protocols=TLSv1,TLSv1.1,TLSv1.2`
- `systemProp.org.gradle.internal.http.connectionTimeout=300000`
- `systemProp.org.gradle.internal.http.socketTimeout=300000`
- `systemProp.org.gradle.jvmargs=-Xmx1024M`

## Webhooks & Callbacks

**Incoming:**
- None detected - H2O acts as server, not webhook consumer

**Outgoing:**
- None detected - No external webhook integrations
- REST API: Custom `RequestServer` framework
  - Version: API v3 (`h2oRESTApiVersion = '3'`)
  - Handler pattern: `water.api.Handler` + `water.api.Schema`
  - Auto-registration: Algorithms register endpoints at startup

## Data Format Integrations

**Parsers:**
- CSV - Built-in via `opencsv:2.3`
- Parquet - `h2o-parquet-parser` via `parquet-hadoop:1.12.3`
- Avro - `h2o-avro-parser` via `avro:1.11.4`
- ORC - `h2o-orc-parser` via `hive-exec:1.1.0`
- ARFF, SVMLight - Built-in parsers

**Model Formats:**
- POJO - Plain Old Java Object (generated Java code)
- MOJO - Model Object, Optimized (binary format, H2O proprietary)
- MOJO2 - DAI integration format

## Hadoop Ecosystem

**Distributions Supported:**
- Cloudera: CDH 5.4-5.16, CDH 6.0-6.3, CDP 7.0-7.2
- MapR: 4.0-7.0
- AWS: EMR 6.10
- IBM: IOP 4.2

**Integration Points:**
- MapReduce - `h2o-mapreduce-generic` for job submission
- HDFS - Data persistence and distributed storage
- Hive - Table import and ORC format support
- YARN - Cluster resource management (via Hadoop assemblies)

**Assembly Structure:**
- `h2o-hadoop-2/` - Hadoop 2.x compatible builds (Jetty 8)
- `h2o-hadoop-3/` - Hadoop 3.x compatible builds (Jetty 9)
- Each distribution gets dedicated assembly: `h2o-{dist}-assembly`

---

*Integration audit: 2026-04-28*
