# Codebase Structure

**Analysis Date:** 2026-04-28

## Directory Layout

```
h2o-3-enterprise/
├── h2o-core/                 # Distributed computing core (DKV, MRTask, Frame/Vec)
├── h2o-genmodel/             # Standalone model scoring (no H2O dependencies)
├── h2o-algos/                # ML algorithms (GBM, GLM, DeepLearning, etc.)
├── h2o-automl/               # Automated machine learning
├── h2o-admissibleml/         # Admissible ML (explainability, fairness)
├── h2o-app/                  # Application runner (aggregates core + algos)
├── h2o-assemblies/           # Distribution assemblies (main, steam, minimal, genmodel)
├── h2o-web/                  # Flow web UI (CoffeeScript/Node.js)
├── h2o-bindings/             # Python/R client code generation
├── h2o-py/                   # Python client library
├── h2o-r/                    # R client library
├── h2o-parsers/              # Data format parsers (Avro, Parquet, ORC)
│   ├── h2o-avro-parser/
│   ├── h2o-parquet-parser/
│   └── h2o-orc-parser/
├── h2o-persist-*/            # Storage backends
│   ├── h2o-persist-s3/       # AWS S3 persistence
│   ├── h2o-persist-gcs/      # Google Cloud Storage
│   ├── h2o-persist-hdfs/     # Hadoop HDFS
│   ├── h2o-persist-http/     # HTTP/HTTPS file access
│   └── h2o-persist-drive/    # Google Drive
├── h2o-extensions/           # Extension modules
│   ├── xgboost/              # XGBoost integration
│   ├── target-encoder/       # Target encoding
│   ├── mojo-pipeline/        # MOJO pipeline support
│   ├── steam/                # H2O Steam integration
│   ├── krbstandalone/        # Kerberos authentication
│   └── jython-cfunc/         # Custom functions via Jython
├── h2o-genmodel-extensions/  # GenModel extensions
│   ├── xgboost/              # XGBoost MOJO scoring
│   ├── mojo-pipeline/        # Pipeline MOJO scoring
│   └── jgrapht/              # Graph algorithms for MOJOs
├── h2o-hadoop-*/             # Hadoop distribution assemblies
│   ├── h2o-hadoop-common/    # Common Hadoop code
│   ├── h2o-hadoop-2/         # Hadoop 2.x (CDH, MapR, HDP, etc.)
│   └── h2o-hadoop-3/         # Hadoop 3.x (CDP, EMR)
├── h2o-k8s/                  # Kubernetes integration
├── h2o-k8s-int/              # Kubernetes internal components
├── h2o-k8s-comp/             # Kubernetes component library
├── h2o-jetty-8/              # Jetty 8 web server integration
├── h2o-jetty-9/              # Jetty 9 web server integration
├── h2o-jetty-9-minimal/      # Minimal Jetty 9 (for Steam)
├── h2o-webserver-iface/      # Web server interface abstraction
├── h2o-security/             # Security framework
├── h2o-jaas-pam/             # PAM authentication
├── h2o-logger/               # Logging facade
├── h2o-logging/              # Logging implementations
│   ├── impl-classic/         # Logback implementation
│   ├── impl-log4j2/          # Log4j2 implementation
│   └── safe4j/               # Safe logging utilities
├── h2o-test-support/         # Test utilities and frameworks
├── h2o-test-accuracy/        # Accuracy benchmark tests
├── h2o-docs/                 # Documentation source
├── h2o-docs-theme/           # Documentation theme
├── h2o-dist/                 # Distribution packaging scripts
├── gradle/                   # Gradle build configuration
├── buildSrc/                 # Custom Gradle plugins and build logic
├── scripts/                  # Build and deployment scripts
├── docker/                   # Docker configurations
├── ec2/                      # EC2 deployment scripts
└── tests/                    # Integration test suites
```

## Directory Purposes

**h2o-core:**
- Purpose: Distributed computing engine and data structures
- Contains: DKV, MRTask, Frame/Vec/Chunk, REST API framework, Job management, Iced serialization, ModelBuilder base class
- Key files: `src/main/java/water/H2O.java` (bootstrap), `water/DKV.java` (key-value store), `water/MRTask.java` (map/reduce), `water/fvec/Frame.java` (data frame)

**h2o-genmodel:**
- Purpose: Standalone POJO/MOJO model scoring without H2O runtime dependencies
- Contains: Model prediction interfaces, tree traversal logic, MOJO reader
- Key files: `src/main/java/hex/genmodel/MojoModel.java`, `hex/genmodel/easy/EasyPredictModelWrapper.java`

**h2o-algos:**
- Purpose: Machine learning algorithm implementations
- Contains: GBM, GLM, DeepLearning, RandomForest, K-Means, PCA, XGBoost wrapper, etc.
- Key files: `src/main/java/hex/tree/gbm/GBM.java`, `hex/glm/GLM.java`, `hex/deeplearning/DeepLearning.java`

**h2o-automl:**
- Purpose: Automated machine learning with hyperparameter search
- Contains: AutoML orchestration, model selection, leaderboard
- Key files: `src/main/java/ai/h2o/automl/AutoML.java`

**h2o-app:**
- Purpose: Application assembly combining core + algorithms
- Contains: Minimal glue code, mostly dependency declarations
- Key files: `build.gradle` (dependency aggregation)

**h2o-assemblies:**
- Purpose: Build complete distribution JARs for different use cases
- Contains: `main/` (full featured), `steam/` (secure minimal), `minimal/` (lightweight), `genmodel/` (scoring only)
- Key files: `main/build.gradle`, `steam/build.gradle`

**h2o-web:**
- Purpose: Flow web UI (interactive notebook-style interface)
- Contains: CoffeeScript source, Node.js build, web assets
- Key files: `src/main/coffee/`, `build.gradle` (Node.js build integration)

**h2o-parsers:**
- Purpose: Data format ingestion (CSV, Avro, Parquet, ORC)
- Contains: Parser implementations for various file formats
- Key files: `h2o-avro-parser/src/main/java/`, `h2o-parquet-parser/src/main/java/`

**h2o-persist-*:**
- Purpose: Storage backend implementations
- Contains: S3, GCS, HDFS, HTTP persistence layers
- Key files: `h2o-persist-s3/src/main/java/water/persist/PersistS3.java`

**h2o-extensions:**
- Purpose: Optional extension modules loaded at runtime
- Contains: XGBoost, target encoder, Steam integration, Kerberos auth
- Key files: `xgboost/src/main/java/`, `target-encoder/src/main/java/`

**h2o-hadoop-*:**
- Purpose: Hadoop distribution-specific builds
- Contains: MapReduce driver, YARN integration, distribution assemblies
- Key files: `h2o-hadoop-common/src/main/java/water/hadoop/H2OMapperTask.java`

**h2o-k8s:**
- Purpose: Kubernetes deployment and discovery
- Contains: K8s API client, pod discovery, headless service integration
- Key files: `src/main/java/water/k8s/H2OCluster.java`

**h2o-py / h2o-r:**
- Purpose: Python and R client libraries
- Contains: Client bindings, estimator wrappers, connection management
- Key files: `h2o-py/h2o/`, `h2o-r/h2o-package/R/`

## Key File Locations

**Entry Points:**
- `h2o-core/src/main/java/water/H2O.java`: Main application entry point
- `h2o-hadoop-common/src/main/java/water/hadoop/H2OMapperTask.java`: Hadoop MapReduce entry
- `h2o-app/src/main/java/water/H2OApp.java`: Application wrapper (if exists)

**Configuration:**
- `gradle.properties`: Gradle build configuration (versions, flags)
- `settings.gradle`: Multi-module project structure
- `build.gradle`: Root build configuration
- `h2o-core/src/main/resources/`: Default configs

**Core Logic:**
- `h2o-core/src/main/java/water/`: Distributed computing primitives
- `h2o-core/src/main/java/water/fvec/`: Data frame implementation
- `h2o-core/src/main/java/water/api/`: REST API handlers and schemas
- `h2o-core/src/main/java/hex/`: ModelBuilder base classes
- `h2o-algos/src/main/java/hex/`: Algorithm implementations

**Testing:**
- `h2o-core/src/test/java/`: Core platform tests
- `h2o-algos/src/test/java/`: Algorithm unit tests
- `h2o-test-support/src/main/java/`: Test utilities and multi-node test framework
- `h2o-test-accuracy/src/test/java/`: Accuracy benchmarks
- `tests/`: Cross-language integration tests

## Naming Conventions

**Files:**
- Java classes: PascalCase (e.g., `ModelBuilder.java`, `GBMModel.java`)
- Test classes: `*Test.java` suffix (e.g., `GBMTest.java`)
- Schema classes: `*V3.java`, `*V4.java`, `*V99.java` for API version
- Handler classes: `*Handler.java` suffix (e.g., `ModelBuilderHandler.java`)

**Directories:**
- Modules: `h2o-<name>` prefix (e.g., `h2o-core`, `h2o-algos`)
- Extensions: `h2o-ext-<name>` in settings.gradle, maps to `h2o-extensions/<name>/`
- GenModel extensions: `h2o-genmodel-ext-<name>` maps to `h2o-genmodel-extensions/<name>/`
- Parsers: `h2o-<format>-parser` maps to `h2o-parsers/h2o-<format>-parser/`

**Packages:**
- Core: `water.*` (e.g., `water.fvec`, `water.api`, `water.util`)
- Algorithms: `hex.*` (e.g., `hex.tree.gbm`, `hex.glm`, `hex.deeplearning`)
- GenModel: `hex.genmodel.*` (e.g., `hex.genmodel.algos.gbm`)
- Tests: Mirror source package structure with `Test` suffix

## Where to Add New Code

**New ML Algorithm:**
- Primary code: `h2o-algos/src/main/java/hex/<algoname>/`
- Main class: Extend `ModelBuilder<M,P,O>` (e.g., `MyAlgo extends ModelBuilder`)
- Tests: `h2o-algos/src/test/java/hex/<algoname>/MyAlgoTest.java`
- Schema: `h2o-algos/src/main/java/hex/schemas/MyAlgoV3.java`
- Registration: Constructor with `startup_once=true` (auto-registered)

**New REST API Endpoint:**
- Handler: `h2o-core/src/main/java/water/api/MyFeatureHandler.java`
- Schema: `h2o-core/src/main/java/water/api/schemas3/MyFeatureV3.java`
- Registration: Add to `RegisterV3Api.java` or use `@Route` annotation
- Tests: `h2o-core/src/test/java/water/api/MyFeatureHandlerTest.java`

**New Data Parser:**
- Implementation: `h2o-parsers/h2o-<format>-parser/src/main/java/`
- Extend: `water.parser.Parser` or `water.parser.ParserProvider`
- Service registration: `META-INF/services/water.parser.ParserProvider`
- Tests: `h2o-parsers/h2o-<format>-parser/src/test/java/`

**New Storage Backend:**
- Implementation: `h2o-persist-<name>/src/main/java/water/persist/Persist<Name>.java`
- Extend: `water.persist.Persist`
- Service registration: `META-INF/services/water.persist.PersistProvider`
- Tests: `h2o-persist-<name>/src/test/java/`

**New Extension Module:**
- Create: `h2o-extensions/<name>/` directory
- Add to: `settings.gradle` as `include 'h2o-ext-<name>'`
- Main class: Extend `water.AbstractH2OExtension`
- Service registration: `META-INF/services/water.AbstractH2OExtension`

**Utilities / Shared Helpers:**
- Core utilities: `h2o-core/src/main/java/water/util/`
- Algorithm utilities: `h2o-algos/src/main/java/hex/util/`
- Test utilities: `h2o-test-support/src/main/java/water/`

**Client Bindings (Python/R):**
- Auto-generated: Run `./gradlew :h2o-bindings:build` after REST API changes
- Python: `h2o-py/h2o/estimators/` (auto-generated from schemas)
- R: `h2o-r/h2o-package/R/` (auto-generated from schemas)
- Manual extensions: `h2o-py/h2o/utils/`, `h2o-r/h2o-package/R/h2o_custom.R`

## Special Directories

**build/**
- Purpose: Gradle build outputs (compiled classes, JARs, test results)
- Generated: Yes
- Committed: No (in .gitignore)

**target/**
- Purpose: Distribution assembly outputs (Hadoop JARs, docs, zips)
- Generated: Yes (via `make-dist.sh`)
- Committed: No

**.gradle/**
- Purpose: Gradle cache and daemon files
- Generated: Yes
- Committed: No

**h2o-genmodel/src/main/java/hex/genmodel/**
- Purpose: MOJO scoring runtime (must have zero dependencies)
- Generated: Partially (some classes generated during build)
- Committed: Yes (source), No (generated)
- **Critical constraint:** Java 7 compatible, no external dependencies

**h2o-core/src/main/java/water/nbhm/**
- Purpose: Non-blocking hash map implementation (custom concurrent data structure)
- Generated: No
- Committed: Yes
- Note: Third-party code, minimal modifications

**h2o-web/lib/**
- Purpose: Node.js dependencies for Flow UI build
- Generated: Yes (via npm install)
- Committed: No

**gradle/wrapper/**
- Purpose: Gradle wrapper for reproducible builds
- Generated: No (manually updated)
- Committed: Yes

**buildSrc/**
- Purpose: Custom Gradle plugins and build logic
- Generated: No
- Committed: Yes
- Key files: `H2OBuildVersion.groovy`, custom task definitions

**h2o-assemblies/*/build/libs/**
- Purpose: Final distribution JAR files
- Generated: Yes (via `./gradlew :h2o-assemblies:main:build`)
- Committed: No
- Output: `h2o.jar` (main), `h2o-genmodel.jar` (scoring only)

**scripts/**
- Purpose: Build automation, deployment, benchmarking
- Generated: No
- Committed: Yes
- Key files: `jenkins/`, `benchmark/`, `run.py`

**h2o-py/h2o/backend/server/**
- Purpose: Embedded H2O server JAR for Python client
- Generated: Yes (copied from h2o-assemblies/main during build)
- Committed: No (large binary)

## Module Dependency Structure

```
h2o-genmodel  (standalone, no dependencies)
    ↓
h2o-logger  (logging facade)
    ↓
h2o-core  (depends on: h2o-logger, h2o-genmodel, h2o-jaas-pam)
    ↓
h2o-algos  (depends on: h2o-core)
    ↓
h2o-automl  (depends on: h2o-core, h2o-algos)
    ↓
h2o-app  (depends on: h2o-core, h2o-algos, h2o-automl, h2o-ext-target-encoder)
    ↓
h2o-assemblies/main  (fat JAR: app + parsers + persistence + webserver)
```

**Parallel branches (peer dependencies on h2o-core):**
- `h2o-parsers/*` → h2o-core
- `h2o-persist-*` → h2o-core
- `h2o-extensions/*` → h2o-core (and sometimes h2o-algos)
- `h2o-jetty-*` → h2o-webserver-iface
- `h2o-k8s` → h2o-core

**Test dependencies:**
- All test code depends on `h2o-test-support`
- Runtime tests need `h2o-jetty-9` (or jetty-8) for web server

## Build Output Locations

**Module JARs:**
- `h2o-core/build/libs/h2o-core.jar`
- `h2o-algos/build/libs/h2o-algos.jar`
- `h2o-genmodel/build/libs/h2o-genmodel.jar`

**Assembly JARs:**
- `h2o-assemblies/main/build/libs/h2o.jar` (full distribution)
- `h2o-assemblies/steam/build/libs/h2o.jar` (secure minimal)
- `h2o-assemblies/genmodel/build/libs/h2o-genmodel.jar` (scoring only)

**Hadoop Distribution JARs:**
- `h2o-hadoop-2/h2o-<dist>-assembly/build/libs/h2odriver.jar` (per Hadoop distro)
- `h2o-hadoop-3/h2o-<dist>-assembly/build/libs/h2odriver.jar`

**Test Results:**
- `h2o-core/build/test-results/testMultiNode/` (JUnit XML)
- `h2o-algos/build/test-results/testSingleNode/`
- Logs: `build/h2o_<ip>_<port>*.log`

**Documentation:**
- `target/docs-website/h2o-docs/index.html` (generated docs)
- `h2o-docs/build/` (intermediate Sphinx build)

## Testing Structure

**Test Types:**
- `testSingleNode`: Single-node cluster (fast, most tests)
- `testMultiNode`: 5-node cluster (integration tests)
- `testSingleNodeOneProc`: Single JVM, no cluster (unit tests)

**Test Location Pattern:**
- Source: `src/main/java/hex/tree/gbm/GBM.java`
- Test: `src/test/java/hex/tree/gbm/GBMTest.java`

**Running Specific Tests:**
```bash
./gradlew :h2o-algos:testSingleNode -Dtest.single=GBMTest
```

**Test Data:**
- Small data: `smalldata/` (in repo or downloaded via `syncSmalldata`)
- Big data: `bigdata/` (separate repository, not in build)

---

*Structure analysis: 2026-04-28*
