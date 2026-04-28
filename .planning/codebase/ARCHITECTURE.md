<!-- refreshed: 2026-04-28 -->
# Architecture

**Analysis Date:** 2026-04-28

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────┐
│                      Client Layer (REST API)                        │
│  RequestServer + Handler Classes + Schema DTOs                      │
│  `h2o-core/src/main/java/water/api/`                                │
└────────────────┬────────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Application Layer                                │
├──────────────────┬──────────────────┬───────────────────────────────┤
│  ML Algorithms   │   AutoML         │  Standalone Scoring           │
│  `h2o-algos/`    │  `h2o-automl/`   │  `h2o-genmodel/`              │
│  GBM, DL, GLM    │  Grid Search     │  (No H2O deps)                │
└────────┬─────────┴────────┬─────────┴──────────┬────────────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    Distributed Core Layer                           │
│  DKV (Key-Value Store) + MRTask (Map/Reduce) + Frame/Vec/Chunk      │
│  `h2o-core/src/main/java/water/`                                    │
│  `h2o-core/src/main/java/water/fvec/`                               │
└────────┬────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│             Infrastructure & Persistence                            │
│  Parsers (Avro, Parquet, ORC) | Storage (S3, GCS, HDFS, HTTP)       │
│  `h2o-parsers/` | `h2o-persist-*/`                                  │
└─────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Runtime Environment (Standalone, Hadoop, Kubernetes)               │
│  `h2o-hadoop-*/` | `h2o-k8s/` | `h2o-jetty-*/`                      │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| H2O | Cluster bootstrap, node discovery, lifecycle management | `h2o-core/src/main/java/water/H2O.java` |
| DKV | Distributed key-value store with Paxos-based locking | `h2o-core/src/main/java/water/DKV.java` |
| MRTask | Map/Reduce framework for distributed computation | `h2o-core/src/main/java/water/MRTask.java` |
| Frame | Distributed 2D table (named columns) | `h2o-core/src/main/java/water/fvec/Frame.java` |
| Vec | Distributed column of data | `h2o-core/src/main/java/water/fvec/Vec.java` |
| Chunk | Contiguous subset of Vec (typically 1K-1M rows) | `h2o-core/src/main/java/water/fvec/Chunk.java` |
| ModelBuilder | Abstract base for all ML algorithms | `h2o-core/src/main/java/hex/ModelBuilder.java` |
| RequestServer | HTTP routing and handler dispatch | `h2o-core/src/main/java/water/api/RequestServer.java` |
| Schema | Versioned REST API DTOs (v3, v4, v99) | `h2o-core/src/main/java/water/api/schemas3/` |
| Handler | REST endpoint implementation | `h2o-core/src/main/java/water/api/*Handler.java` |
| Iced | Serialization framework for all distributed objects | `h2o-core/src/main/java/water/Iced.java` |
| PersistManager | Pluggable storage backend abstraction | `h2o-core/src/main/java/water/persist/PersistManager.java` |
| Job | Asynchronous task tracking and cancellation | `h2o-core/src/main/java/water/Job.java` |

## Pattern Overview

**Overall:** Distributed Data-Parallel Computing with Peer-to-Peer Architecture

**Key Characteristics:**
- Peer-to-peer cluster (no master node for data distribution)
- Data locality: computation moves to data, not vice versa
- Distributed key-value store (DKV) with consistent hashing for key placement
- Map/Reduce execution model (MRTask) with tree-based reduction
- Columnar data storage (Vec/Chunk) aligned across frames
- Custom serialization (Iced) for all distributed objects
- REST API with versioned schemas for client stability

## Layers

**Client Layer (REST API):**
- Purpose: HTTP interface for external clients (Python, R, Flow UI)
- Location: `h2o-core/src/main/java/water/api/`
- Contains: RequestServer, Handler classes, Schema DTOs, route registration
- Depends on: Application Layer (ModelBuilder), Core Layer (DKV, Frame)
- Used by: External clients via HTTP, Flow web UI (`h2o-web/`)

**Application Layer (ML Algorithms):**
- Purpose: Machine learning algorithm implementations
- Location: `h2o-algos/`, `h2o-automl/`, `h2o-extensions/`
- Contains: GBM, GLM, DeepLearning, Random Forest, AutoML, Grid Search
- Depends on: Core Layer (ModelBuilder, Frame, MRTask, DKV)
- Used by: REST API handlers, direct Job invocation

**Distributed Core Layer:**
- Purpose: Distributed computing primitives and data structures
- Location: `h2o-core/src/main/java/water/`, `h2o-core/src/main/java/water/fvec/`
- Contains: DKV, MRTask, Frame/Vec/Chunk, Iced serialization, Job, Paxos
- Depends on: Infrastructure Layer (parsers, persistence), h2o-genmodel
- Used by: All layers above

**Infrastructure Layer:**
- Purpose: Data ingestion and storage backends
- Location: `h2o-parsers/`, `h2o-persist-*/`
- Contains: CSV/Avro/Parquet/ORC parsers, S3/GCS/HDFS/HTTP persistence
- Depends on: Core Layer (Frame, Vec, DKV)
- Used by: Core Layer for data loading and saving

**Standalone Scoring Layer:**
- Purpose: Production model deployment without H2O runtime
- Location: `h2o-genmodel/`
- Contains: POJO/MOJO model scoring, tree traversal, prediction logic
- Depends on: Nothing (intentionally zero dependencies on h2o-core)
- Used by: Production applications, embedded scoring

**Assembly Layer:**
- Purpose: Package complete distributions for different deployment targets
- Location: `h2o-assemblies/main/`, `h2o-assemblies/steam/`, `h2o-assemblies/minimal/`
- Contains: Fat JAR builds with selected components and dependencies
- Depends on: All layers (assembly-specific subset)
- Used by: End users, deployment platforms (Docker, K8s, Hadoop)

## Data Flow

### Primary Request Path (Supervised Learning)

1. **HTTP Request arrives** (`h2o-core/src/main/java/water/api/RequestServer.java:56`)
2. **Route lookup and Handler dispatch** (`h2o-core/src/main/java/water/api/RequestServer.java:80`)
3. **Schema populated from HTTP params** (`h2o-core/src/main/java/water/api/Handler.java`)
4. **ModelBuilder created with Parameters** (`h2o-core/src/main/java/hex/ModelBuilder.java:74`)
5. **Job launched for async execution** (`h2o-core/src/main/java/water/Job.java`)
6. **Algorithm-specific trainModelImpl()** (`h2o-algos/src/main/java/hex/tree/gbm/GBM.java:61`)
7. **MRTask distributes work across chunks** (`h2o-core/src/main/java/water/MRTask.java:65`)
8. **Results stored in DKV** (`h2o-core/src/main/java/water/DKV.java:54`)
9. **Model returned to client as Schema** (`h2o-core/src/main/java/water/api/schemas3/ModelBuilderV3.java`)

### Data Ingestion Flow

1. **Parse request via ImportFilesHandler** (`h2o-core/src/main/java/water/api/ImportFilesHandler.java`)
2. **PersistManager resolves storage backend** (`h2o-persist-s3/`, `h2o-persist-hdfs/`, etc.)
3. **ParserService detects file format** (`h2o-core/src/main/java/water/parser/ParserService.java`)
4. **Format-specific parser invoked** (`h2o-parsers/h2o-avro-parser/`, `h2o-parsers/h2o-parquet-parser/`)
5. **Data written to Vec/Chunk** (`h2o-core/src/main/java/water/fvec/Vec.java`)
6. **Frame registered in DKV** (`h2o-core/src/main/java/water/fvec/Frame.java:65`)

### Distributed Computation Flow (MRTask)

1. **User calls doAll(frame)** (`h2o-core/src/main/java/water/MRTask.java`)
2. **Work split across nodes via RPC** (`h2o-core/src/main/java/water/RPC.java`)
3. **Each node subdivides to ForkJoin threads** (`jsr166y.ForkJoinPool`)
4. **map(Chunk) called per chunk** (user-defined logic)
5. **reduce() called in tree pattern** (user-defined aggregation)
6. **Final result returned to caller** (via MRTask fields)

**State Management:**
- Global state in DKV (distributed, eventually consistent)
- Node-local state in static H2O fields (cluster membership, config)
- Per-chunk state transient during MRTask execution
- Model state persisted as Iced objects in DKV

## Key Abstractions

**Iced:**
- Purpose: Serialization base class for all distributed objects
- Examples: `h2o-core/src/main/java/water/Iced.java`, `water.fvec.Frame`, `hex.Model`
- Pattern: Auto-generated serialization via code generation (Weaver)

**Keyed:**
- Purpose: Iced subclass with DKV key management
- Examples: `h2o-core/src/main/java/water/Keyed.java`, `water.fvec.Vec`, `hex.Model`
- Pattern: Objects that live in DKV extend Keyed<T>

**MRTask:**
- Purpose: Map/Reduce abstraction for parallel distributed computation
- Examples: All algorithm implementations, scoring, data transformations
- Pattern: Extend MRTask, override map() and optionally reduce()

**ModelBuilder:**
- Purpose: Template pattern for ML algorithm lifecycle
- Examples: `h2o-algos/src/main/java/hex/tree/gbm/GBM.java`, `hex.glm.GLM`, `hex.deeplearning.DeepLearning`
- Pattern: Extend ModelBuilder<M,P,O>, implement init() and trainModelImpl()

**Schema:**
- Purpose: Stable versioned API DTOs decoupled from internal classes
- Examples: `h2o-core/src/main/java/water/api/schemas3/FrameV3.java`, `ModelBuilderV3`
- Pattern: Schema extends Iced, translates to/from internal Iced objects

**VectorGroup:**
- Purpose: Ensures chunk alignment across Vecs in a Frame
- Examples: All Frames share a VectorGroup for their Vecs
- Pattern: Same-numbered chunks across Vecs have identical row ranges

## Entry Points

**Main Application:**
- Location: `h2o-core/src/main/java/water/H2O.java`
- Triggers: `java -jar h2o.jar`
- Responsibilities: Cluster bootstrap, node discovery, REST API startup, extension loading

**Hadoop MapReduce:**
- Location: `h2o-hadoop-common/src/main/java/water/hadoop/H2OMapperTask.java`
- Triggers: `hadoop jar h2o-hadoop.jar`
- Responsibilities: Launch H2O nodes inside YARN containers

**Kubernetes:**
- Location: `h2o-k8s/src/main/java/water/k8s/H2OCluster.java`
- Triggers: Helm chart or K8s manifest deployment
- Responsibilities: Pod discovery, StatefulSet management

**Web Server:**
- Location: `h2o-jetty-9/src/main/java/water/webserver/jetty9/Jetty9Helper.java`
- Triggers: Started by H2O.main() after cluster formation
- Responsibilities: HTTP server, servlet registration, Flow UI serving

## Architectural Constraints

- **Threading:** Multi-threaded with ForkJoinPool for MRTask execution; main H2O loop is single-threaded event-driven for network I/O
- **Global state:**
  - `water.H2O.CLOUD` - cluster membership (all nodes)
  - `water.H2O.SELF` - current node identity
  - `water.H2O.STORE` - local DKV cache
  - `water.TypeMap` - class ID registry for Iced serialization
- **Circular imports:** Vec <-> Frame (resolved via VectorGroup indirection)
- **No circular dependencies between modules:** Gradle enforces DAG (h2o-genmodel → h2o-core → h2o-algos → h2o-app)
- **Paxos locking:** Cloud "locks" before first DKV write to prevent mid-computation node joins (`h2o-core/src/main/java/water/Paxos.java`)
- **Data locality requirement:** MRTask assumes chunks are local; cross-node chunk access is extremely expensive
- **Java 7 compatibility for h2o-genmodel:** Production scoring must work on legacy JVMs

## Anti-Patterns

### Modifying Vec/Chunk in-place during MRTask

**What happens:** Code attempts to modify chunk data during map() phase of MRTask
**Why it's wrong:** Chunks are immutable during reads; modifications won't be visible to other nodes and violate the distributed memory model
**Do this instead:** Use MRTask with NewChunk output parameter to create new Vecs (`h2o-core/src/main/java/water/MRTask.java` - call `.doAll(nOutputs, inputFrame)`)

### Using DKV.get() in tight loops

**What happens:** Calling `DKV.get(key)` repeatedly inside per-row or per-chunk processing
**Why it's wrong:** Each DKV.get() can trigger network I/O and deserialization; performance degrades by 1000x
**Do this instead:** Cache DKV objects before loop, or restructure to access via Frame/Vec references (`h2o-core/src/main/java/water/fvec/Frame.java:65`)

### Storing large objects in ModelBuilder fields

**What happens:** Large arrays or temporary data structures stored in ModelBuilder instance variables
**Why it's wrong:** ModelBuilder is serialized and sent to all nodes; large fields cause massive network overhead
**Do this instead:** Store in DKV with temporary keys, or use transient fields (`h2o-core/src/main/java/hex/ModelBuilder.java:25`)

### Ignoring VectorGroup alignment

**What happens:** Creating Vecs with different VectorGroups and adding them to same Frame
**Why it's wrong:** Chunks won't align row-wise; MRTask assumes alignment for correctness
**Do this instead:** Use `frame.anyVec().group()` when creating new Vecs for the frame (`h2o-core/src/main/java/water/fvec/Vec.java`)

## Error Handling

**Strategy:** Fail-fast with distributed exception propagation

**Patterns:**
- `H2OIllegalArgumentException` for parameter validation errors (thrown in `init()`)
- `H2OModelBuilderIllegalArgumentException` for algorithm-specific constraint violations
- `DistributedException` for wrapping exceptions from remote nodes during MRTask
- `Job.stop()` for user-requested cancellation (checked via `stop_requested()`)
- REST API errors mapped to HTTP status codes via `H2OErrorV3` schema
- Uncaught exceptions logged and propagate to Job failure state

## Cross-Cutting Concerns

**Logging:**
- `water.util.Log` facade over log4j/logback
- Per-node log files: `h2o_<ip>_<port>.log` in working directory
- Use `Log.info()`, `Log.warn()`, `Log.err()` - never `System.out`
- Location: `h2o-logging/` provides logging abstraction

**Validation:**
- Two-phase: cheap validation in `init(false)`, expensive in `init(true)`
- `ModelBuilder.init()` validates parameters before training starts
- Schema field annotations (`@API`) provide client-side validation hints

**Authentication:**
- PAM integration via `h2o-jaas-pam/`
- LDAP/Kerberos via `h2o-extensions/krbstandalone/`
- Hash file authentication for basic auth
- Steam integration via `h2o-extensions/steam/`

**Monitoring:**
- Timeline tracking via `water.TimeLine` for performance profiling
- Job progress via `Job.update()` and `_progressKey`
- Water meters for CPU/IO tracking (`h2o-core/src/main/java/water/api/WaterMeter*Handler.java`)

**Security:**
- SSL/TLS support via Jetty configuration
- Encrypted communication for inter-node traffic (optional)
- H2OSecurityManager for restricting System.exit() calls
- Flow UI security headers in `h2o-core/src/main/java/water/api/RequestServer.java`

---

*Architecture analysis: 2026-04-28*
