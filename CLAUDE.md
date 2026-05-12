# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Quick Build (Skip Tests)
```bash
./gradlew --parallel clean build -x test
# Or use the Makefile shortcut:
make
```

### Full Build (With Tests)

Most probably cannot run on laptop.

```bash
./gradlew syncSmalldata    # Download test data from S3
./gradlew build
```

### Run H2O Locally
```bash
java -jar build/h2o.jar
# Access UI at http://localhost:54321
```

## Testing

### Java Tests
H2O uses a custom multi-node testing framework (not standard JUnit runners):

- **Single-node cluster tests**: `./gradlew :h2o-algos:testSingleNode`
- **Multi-node cluster tests**: `./gradlew :h2o-algos:testMultiNode`
- **Single JVM tests**: `./gradlew :h2o-algos:testSingleNodeOneProc`

Test files are located in:
- `h2o-core/src/test/java/` - Platform tests
- `h2o-algos/src/test/java/` - Algorithm tests (e.g., `hex/tree/gbm/GBMTest.java`)

**Running a single test class**: Use the `test.single` property:
```bash
./gradlew :h2o-algos:testSingleNode -Dtest.single=GBMTest
```

## Module Architecture

### Core Dependencies
```
h2o-genmodel (standalone POJO/MOJO scoring)
    ↓
h2o-core (distributed computing engine, DKV, REST API framework)
    ↓
h2o-algos (ML algorithms: GBM, GLM, DL, RF, etc.)
    ↓
h2o-automl (AutoML functionality)
    ↓
h2o-app (assembly: aggregates core + algos + web UI)
```

### Key Modules
- **h2o-core**: Distributed key-value store (DKV), REST API infrastructure, Frame/Vec/Chunk data structures, MRTask framework
- **h2o-algos**: Machine learning algorithms (all extend `hex.ModelBuilder`)
- **h2o-web**: Flow web UI (Node.js-based, compiled into resources)
- **h2o-genmodel**: Standalone model scoring (no H2O runtime dependencies)
- **h2o-bindings**: Generates Python/R client bindings from REST schemas
- **h2o-persist-{hdfs,s3,gcs}**: Storage backends for distributed file systems

### Extension Modules
- **h2o-ext-xgboost**: XGBoost integration
- **h2o-ext-target-encoder**: Target encoding for categorical features
- **h2o-automl**: Automated machine learning

## Distributed Architecture

### Key Concepts

**DKV (Distributed Key-Value Store)**:
- Every object has a home node determined by consistent hashing of its `Key`
- Access via `DKV.put(key, value)` and `DKV.get(key)`
- The cloud "locks" via Paxos before first DKV write to prevent mid-computation node joins

**Vec/Chunk Data Distribution**:
- `Vec`: Distributed column of data (conceptually like a database column)
- `Chunk`: Contiguous subset of a Vec (typically 1K-1M rows)
- All Vecs in a `Frame` share a `VectorGroup` ensuring chunk alignment
- Same-numbered chunks across different Vecs have identical row ranges for efficient row-wise iteration

**MRTask (Map/Reduce)**:
- Extends `MRTask` and override `map(Chunk c)` and optionally `reduce(MRTask mrt)`
- Call `.doAll(frame)` or `.dfork(frame)` to execute
- Computation moves to data (not vice versa)
- Results reduce up a tree back to the initiating node

**Iced Serialization**:
- All distributed objects extend `Iced<T>` for auto-generated serialization
- `Keyed<T>` extends Iced and adds DKV key management
- Schemas extend Iced and provide versioned REST API DTOs

### Node Communication
- UDP for heartbeats and small messages
- TCP for bulk data transfer
- Nodes form a peer-to-peer cluster (no master node for data distribution)

## REST API Structure

### Handler-Route-Schema Pattern
1. **Routes** (`water.api.Route`): Map HTTP endpoints to handler methods
2. **Handlers** (`water.api.Handler`): Process requests with signature `(int version, Schema schema)`
3. **Schemas** (`water.api.Schema`): Versioned DTOs that translate between API and internal Iced objects
4. **RequestServer**: Central routing engine

### Algorithm Registration
Algorithms auto-register REST endpoints at startup:
- Each algorithm's constructor with `startup_once=true` creates a singleton prototype
- `RegisterAlgos.java` instantiates all algorithms during H2O initialization
- Each algorithm gets standardized endpoints: `/3/ModelBuilders/<algo>`, `/3/Grid`, etc.

### Adding New REST Endpoints
1. Create Schema class extending `water.api.Schema`
2. Create Handler class extending `water.api.Handler`
3. Register route in handler via `@Route` annotation or programmatically
4. Schema fields with `@API` annotation become public API parameters

## Algorithm Implementation

### ModelBuilder Pattern
All algorithms extend `hex.ModelBuilder<M, P, O>`:
- `M`: Model class (extends `hex.Model`)
- `P`: Parameters class (extends `hex.Model.Parameters`)
- `O`: Output class (extends `hex.Model.Output`)

### Key Methods to Override
- `init()`: Validate parameters, check data compatibility
- `trainModelImpl()`: Core training logic (runs on worker nodes)
- `compute2()`: Orchestrates distributed training via MRTask

### Example Structure
```java
public class MyAlgo extends ModelBuilder<MyAlgoModel, MyAlgoModel.MyAlgoParameters, MyAlgoModel.MyAlgoOutput> {
  @Override public void init(boolean expensive) {
    super.init(expensive);
    // Validate parameters
  }

  @Override public void trainModelImpl() {
    // Core training logic
  }
}
```

### Model Scoring
Models implement `score0(double[] data, double[] preds)` for row-by-row prediction.
For production deployment:
- **POJO**: Java code generated via `model.download_pojo()`
- **MOJO**: Binary format via `model.download_mojo()` (more compact, faster)

## Common Development Workflows

### Adding a New Algorithm
1. Create `MyAlgo.java` extending `ModelBuilder` in `h2o-algos/src/main/java/hex/`
2. Implement nested `MyAlgoModel`, `MyAlgoParameters`, `MyAlgoOutput` classes
3. Add algorithm instantiation to `water.api.RegisterAlgos` with `startup_once=true`
4. Add tests in `h2o-algos/src/test/java/hex/myalgo/`
5. Rebuild: `./gradlew :h2o-algos:build`

### Modifying Core Data Structures
Be cautious when modifying:
- `water.fvec.Vec`, `water.fvec.Chunk`: Core data structures used everywhere
- `water.DKV`: Distributed key-value operations
- `water.H2O`: Cluster management and lifecycle

These changes can have wide-reaching implications across all algorithms.

### Debugging Distributed Code
- H2O tests spawn multiple JVMs that form a cluster
- Logs are in `build/` with names like `h2o_<node_ip>_<port>.log`
- Enable verbose logging: `-Dlog.level=DEBUG` or `-Dlog.level=TRACE`
- Use `Log.info()`, `Log.warn()`, `Log.err()` (not `System.out`)

### Building Documentation
```bash
./gradlew clean && ./gradlew build -x test && (export DO_FAST=1; ./gradlew dist)
open target/docs-website/h2o-docs/index.html
```

## Git Workflow

### Branch Naming
If working on a GitHub issue, include the issue number and GitHub username if provided:
```bash
git checkout -b githubusername-gh-1234_add_new_feature
```

### Pull Requests
- New code requires unit tests (runits for R, pyunits for Python, JUnits for Java)
- PR title should include GitHub issue number: "GH-1234: Added new feature"
- PRs trigger Jenkins CI tests automatically
- All tests must pass before merge

#### prCheck Rules (enforced by `gradle/prCheck.gradle`)
1. Always have a GitHub issue before starting work (otherwise you cannot know which branch to start from).
2. The GitHub issue must have a milestone assigned. If it does not, consult the team for a suitable fix version.
3. If the milestone ends with `.1`, the change targets the `master` branch. All other changes target the current fix release branch, prefixed `rel-` (e.g. `rel-3.46.0`).
4. Assign the issue to the H2O-3 project and move it to the "In Progress" state.
5. Include the GitHub issue number (`GH-XXXX`) in at least one commit message. Not checked by CI, but expected.
6. The PR title must include the issue number, and the PR description must link the issue, e.g. `GH-4200: Adding support for Factorization Machines`. This is checked by CI.
7. If a PR is intentionally not tied to an issue (docs-only cleanups, tooling tweaks), append **`[nocheck]` at the end** of the title — e.g. `Document PR conventions in CLAUDE.md [nocheck]`. `gradle/prCheck.gradle` skips validation when it sees the marker anywhere in the title, but keep it at the end by convention so reviewers read the actual change first. Target the **current fix release branch** (e.g. `rel-3.46.0` today, `rel-3.48.0` once that line is cut) — not `master` — so the change ships in the upcoming release and flows forward into master on the next merge. Target `master` only when the change is inherently master-only.

#### Updating an open PR
- **Prefer new commits over force-push.** Once a PR is under review, add follow-up commits (`git commit`, `git push`) so reviewers see the incremental diff and GitHub preserves comment anchors.
- **Use force-push only for genuine history surgery** — rebasing onto a different base branch, resolving a merge conflict before first review, or dropping a committed secret. Not for squashing or amending typos that already landed on the remote.
- Squashing can happen at merge time via the GitHub "Squash and merge" button if the maintainer prefers a single commit.

#### PR description style
- **Be brief.** One short paragraph or a handful of bullets. A reviewer should understand the what and the why in under 30 seconds.
- **Focus on the change and its motivation** — what problem this solves, which CVE/bug it fixes, which user-visible behaviour shifts.
- **Link the issue** (`Closes #N` or similar) so GitHub auto-closes it on merge.
- **Do not include test-suite results or CI stats** (`All 42 tests passing`, `20/20 green`, runtime numbers, coverage deltas). Tests passing is assumed; CI reports them.
- **Do not include meaningless stats** (file counts, LOC changes, "refactored 5 classes"). The diff shows them.
- **Do not narrate the process** (`first I tried X, then Y`, `I investigated…`). Ship the conclusion.
- **Screenshots / logs only when they add information** the reviewer can't get from the diff (UI changes, runtime traces for a bug reproduction).

#### Issue hygiene
When creating or editing a GitHub issue:
- **Don't paste information that already lives elsewhere** (CVE advisories, long error logs, full stack traces). Summarise in a couple of sentences and link the source.
- **Fix version belongs in the milestone, not the description.** Never write `Fix version: 3.46.0.11` in the body — set the milestone field instead (`gh issue edit N --milestone "3.46.0.11"`).
- **Assignee is required** (checked by `prCheck`). Set it when you open the issue.
- **Project is required** (checked by `prCheck`). Add the issue to the `H2O OSS (H2O-3)` project (project number 112).
- **Labels**: add one when it's obvious (`bug`, `feature`, `docs`, `dependencies`, …). Skip if unclear. **CVE or security-related tasks must carry the `Security Vulnerability` label.**
- **Relationships**: if a PR already exists for the issue, link it from the Development section of the issue UI.
- **Project fields** (see `H2O OSS (H2O-3)` side panel): fill what you know — `Status` (set to `In Progress` once work starts), `Customer`, `Support ticket`, `CVEs fixed` (comma-separated CVE IDs), `Complexity` (Story Points 1-5, see scale below), `Private notes`. Leave blank rather than guessing.

#### Issue fields & triage
Applies to both new issues you open and existing ones you review.

**Before triaging, always read the project-side fields.** `gh issue view` shows labels / milestone / assignees but **not** custom project fields (`Customer`, `Support ticket`, `CVEs fixed`, `Complexity`, linked PRs, etc.). Those are only visible via `gh project item-list 112 --owner h2oai --format json | jq '.items | map(select(.content.number == N)) | .[0]'`. Skipping this step is how you wrongly conclude an issue is actionable-less when it actually has a support ticket, customer, or open PR attached.

**For existing issues — consider closing when:**
- You're ~80% sure it's outdated.
- Nobody can still understand what the issue is about.
- There's a closed support ticket referenced in the comments.
- Before closing: assign the project and link the support ticket in the project details.
- Default to closing rather than asking clarifying questions.
- **Never close an issue without explicit confirmation from the user first.** Closing is visible to others and reopening is annoying; always propose the close action and wait for a go-ahead.

**For any issue you keep open (or create):**
- **Assignee** — set a current owner, or clear it if the previous owner no longer works on H2O-3.
- **Labels** — add what fits:
  - algo family (`GBM`, `GLM`, `XGBoost`, …),
  - `Security Vulnerability` (required for any CVE / security task),
  - `question`, `good_first_issue`, `docs`, `dependencies`, `Build`, …
  - Optionally a type label: `bug`, `feature`, or a `Task` tag.
  - **Do not use customer-specific labels on the issue itself** — customer info belongs in the project `Customer` field.
- **Project** — add the issue to `H2O OSS (H2O-3)` (project number 112). Required by `prCheck`.
- **Complexity (Story Points)** — fill the project field on a 1–5 scale:
  - **1** — good first issue.
  - **2-3** — understandable requirement, just time and effort for an in-house developer (example: [#7518](https://github.com/h2oai/h2o-3/issues/7518)).
  - **4** — requires study, time, and effort, but no new infrastructure (example: new algorithm like HDBSCAN / KNN, or dropping support for a Python version).
  - **5** — requires study, time, effort, **and** new infrastructure (example: XGBoost on Windows, adding a new Java / R / Python version).

### Commit Messages
Follow the existing style seen in recent commits (concise, descriptive).

## Python/R Client Development

### Regenerating Bindings
After modifying REST API schemas:
```bash
./gradlew :h2o-bindings:build
```
This regenerates Python and R client code in `h2o-py/h2o/` and `h2o-r/h2o-package/R/`.

## Environment Setup Notes

### Required Software
- **JDK**: 1.8+ (Java 8 or later)
- **Node.js**: For building Flow UI
- **Python**: 3.6+ with `pip` (for Python bindings and build tools)
- **R**: 3.1+ with packages: RCurl, jsonlite, statmod, devtools, roxygen2, testthat
- **Gradle**: Via `gradlew` wrapper (auto-downloaded)

### Running Tests Requirements
- Minimum 8GB RAM (16GB recommended)
- Tests spawn 5 JVMs forming an H2O cluster
- Use `-x test` to skip during builds if system resources are limited

## Hadoop Builds

To build H2O-on-Hadoop assemblies:
```bash
export BUILD_HADOOP=1
./gradlew build -x test
```

Assemblies created in `target/` for various Hadoop distributions (CDH, HDP, MapR, EMR).

To build only specific distributions:
```bash
export BUILD_HADOOP=1
export H2O_TARGET=hdp2.6,cdh6.3
./gradlew build -x test
```

## Building H2O Assemblies

This project builds two main assemblies:

### Main Assembly -- part of DockerHub h2o-open-source-k8s and python backend. Should be same as build/h2o.jar
The **main assembly** (`h2o-assemblies/main`) is the full-featured H2O distribution with all standard components.

**Build command:**
```bash
./gradlew :h2o-assemblies:main:build
```

**Output location:**
- `h2o-assemblies/main/build/libs/h2o.jar`

**Key features:**
- Full H2O Application with all standard components
- Includes web UI, parsers (Avro, Parquet), persistence layers (S3, GCS, HDFS, HTTP)
- Contains Hadoop dependencies and Kubernetes support
- Uses Jetty 9 with full feature set
- Main class: `water.H2OApp`

### Steam Assembly
The **steam assembly** (`h2o-assemblies/steam`) is a secure, minimal H2O distribution for use with H2O Steam.

**Build command:**
```bash
./gradlew :h2o-assemblies:steam:build
```

**Output location:**
- `h2o-assemblies/steam/build/libs/h2o.jar`

**Key features:**
- Secure deployment optimized for H2O Steam integration
- Uses minimal Jetty 9 configuration
- Excludes h2o-jaas-pam module for security
- Includes persistence layers with reduced Hadoop footprint
- Main class: `water.H2OApp`

### Build Both Assemblies
To build both assemblies at once:
```bash
./gradlew :h2o-assemblies:main:build :h2o-assemblies:steam:build
```

**Notes:**
- Both assemblies use the Gradle Shadow plugin to create fat jars
- Both include security fixes and vulnerability patches via dependency constraints
- The standard build (`./gradlew build`) excludes these assemblies by default
- See `h2o-assemblies/main/build.gradle` and `h2o-assemblies/steam/build.gradle` for full dependency details

## Code Commenting Guidelines

When writing or modifying code, follow these commenting principles:

### DO:
- **Comment code blocks**: Add comments that explain the purpose, logic, or reasoning behind a block of code
- **Comment complex logic**: Explain non-obvious algorithms, business rules, or intricate operations
- **Comment "why" not "what"**: Focus on the reasoning and context rather than describing what the code literally does
- **Add context**: Explain assumptions, constraints, or important background information

### DON'T:
- **Avoid line-by-line comments**: Don't add comments after each line of code
- **Don't state the obvious**: Avoid commenting self-explanatory method names or trivial operations
- **Don't redundant comments**: If the code is clear and readable, additional comments may not be needed

### Examples:

#### ❌ Bad (over-commenting):
```java
int count = 0; // Initialize count to zero
for (int i = 0; i < items.size(); i++) { // Loop through items
    count++; // Increment count
}
return count; // Return the count
```

#### ✓ Good (block-level comment when needed):
```java
// Calculate total items for billing reconciliation.
// Note: This excludes cancelled orders per business rules in JIRA-1234
int count = 0;
for (int i = 0; i < items.size(); i++) {
    if (!items.get(i).isCancelled()) {
        count++;
    }
}
return count;
```

#### ✓ Good (no comment needed for obvious code):
```java
public void saveUser(User user) {
    userRepository.save(user);
}
```
