# Coding Conventions

**Analysis Date:** 2026-04-28

## Naming Patterns

**Files:**
- Java: PascalCase matching the primary class name (e.g., `GBMTest.java`, `ModelBuilder.java`)
- Python tests: `pyunit_*.py` for unit tests (e.g., `pyunit_h2oH2OFrame.py`)
- R tests: `runit_*.R` for unit tests (e.g., `runit_gbm_basic.R`)
- Test files end with `Test.java` for JUnit tests

**Functions:**
- Java: camelCase (e.g., `trainModelImpl()`, `stall_till_cloudsize()`, `init()`)
- Python: snake_case (e.g., `check_frame()`, `standalone_test()`)
- R: snake_case or dot.case (e.g., `check.test_KDD_trees()`)

**Variables:**
- Java: camelCase for local variables (e.g., `leaked_keys`, `numRows`)
- Java: _prefixed for protected/private fields (e.g., `_parms`, `_train`, `_job`)
- Java: ALL_CAPS for constants (e.g., `MINCLOUDSIZE`, `DEFAULT_TIME_FOR_CLOUDING`)
- Python: snake_case (e.g., `col_names`, `na_str`)

**Types:**
- Java: PascalCase for classes (e.g., `ModelBuilder`, `GBMModel`, `DKV`)
- Java: Type parameters use single uppercase letters (e.g., `<M extends Model<M,P,O>, P extends Model.Parameters, O extends Model.Output>`)
- Generic type parameters: M (Model), P (Parameters), O (Output), K (Key), V (Value)

## Code Style

**Formatting:**
- Tool: Google Checkstyle (version 6.7)
- Config: `gradle/config/checkstyle/google_checks.xml`
- Line length: 100 characters (enforced but violations logged as warnings)
- Indentation: 2 spaces (inferred from style guide)
- No tab characters allowed
- UTF-8 encoding for all source files

**Linting:**
- Java: Checkstyle with Google Java Style checks
- Enforcement: `ignoreFailures = true` (violations logged but don't break build)
- Key rules enforced:
  - No star imports (`AvoidStarImport`)
  - One top-level class per file (`OneTopLevelClass`)
  - Braces required for all blocks (`NeedBraces`)
  - One statement per line (`OneStatementPerLine`)
  - Array style: `String[]` not `String arr[]` (`ArrayTypeStyle`)

**Bracing:**
- Left curly on same line: `if (condition) {`
- Right curly alone for methods, classes, for/while loops
- Empty blocks: `{}` allowed for constructors, methods, types, loops

## Import Organization

**Order:**
1. JSR166y imports (concurrency framework)
2. Hex package imports (ML algorithms)
3. Water package imports (core H2O)
4. Third-party libraries (Apache, Jama, etc.)
5. Java standard library

**Examples from codebase:**
```java
import jsr166y.CountedCompleter;
import hex.*;
import water.*;
import water.fvec.*;
import java.util.*;
```

**Path Aliases:**
- Not used in Java (full package paths preferred)
- Python: relative imports from test directories (e.g., `from tests import pyunit_utils`)

**Patterns:**
- Avoid star imports in production code
- Group related imports from same package
- Separate groups with blank lines

## Error Handling

**Patterns:**
- Use H2O custom exceptions: `H2OIllegalArgumentException`, `H2OModelBuilderIllegalArgumentException`
- Distributed errors: `DistributedException` for failures in distributed tasks
- Validation errors: throw during `init()` method with descriptive messages
- Finally blocks: always clean up resources (Frames, Models) in test teardown

**Examples:**
```java
if (error_count() > 0)
  throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GBM.this);
```

**Try-finally pattern for resource cleanup:**
```java
try {
  fr = parseTestFile("./smalldata/test.csv");
  // ... use frame
} finally {
  if (fr != null) fr.remove();
}
```

## Logging

**Framework:** Log4j (accessed via `water.util.Log`)

**Patterns:**
- Use `Log.info()`, `Log.warn()`, `Log.err()` (never `System.out` or `System.err`)
- Algorithm loggers: `private static final Logger LOG = Logger.getLogger(GBM.class);`
- Verbose logging available via `-Dlog.level=DEBUG` or `-Dlog.level=TRACE`
- Multi-node test logs: `build/h2o_<node_ip>_<port>.log`

**Examples:**
```java
private static final Logger LOG = Logger.getLogger(GBM.class);
Log.info(paste("Test1 MSEs:", test1@model$mse_train))
```

## Comments

**When to Comment:**
- Explain purpose and logic of code blocks (not line-by-line)
- Document "why" rather than "what"
- Add context for non-obvious algorithms or business rules
- Mark known issues: `TODO`, `FIXME`, `HACK` (currently 30+ instances in core)
- Reference external resources (e.g., "Based on Elements of Statistical Learning, page 387")

**JSDoc/TSDoc:**
- Javadoc required for public APIs
- Class-level docs describe purpose and usage
- Method-level docs describe parameters, return values, exceptions
- Use `@param`, `@return`, `@throws` tags

**Examples from codebase:**
```java
/** A Distributed Key/Value Store.
 *  <p>
 *  Functions to Get and Put Values into the K/V store by Key.
 *  <p>
 *  The <em>Java Memory Model</em> is observed for all operations.
 */
public abstract class DKV { ... }
```

**Anti-pattern - avoid:**
```java
int count = 0; // Initialize count to zero
count++; // Increment count
```

**Preferred - block comments:**
```java
// Calculate total items for billing reconciliation.
// Note: This excludes cancelled orders per business rules in JIRA-1234
int count = 0;
for (Item item : items) {
  if (!item.isCancelled()) count++;
}
```

## Function Design

**Size:** No strict limit, but algorithms broken into helper methods (e.g., `trainModelImpl()`, `compute2()`, `init()`)

**Parameters:**
- Use Parameters objects for algorithm configuration (e.g., `GBMModel.GBMParameters`)
- Builder pattern for complex configuration
- Varargs allowed for utilities (e.g., `String[] xcols`)

**Return Values:**
- Explicit types preferred over `var`
- Return null only when documented (prefer Optional for new code)
- Distributed operations return Futures for async control

**Visibility:**
- Use protected/private with `_` prefix for fields
- Public methods for API surface
- Package-private for internal framework use

## Module Design

**Exports:**
- Each algorithm extends `ModelBuilder<M, P, O>`
- Nested static classes for Parameters, Output (e.g., `GBMModel.GBMParameters`)
- Iced serialization via inheritance (`extends Iced<T>`)

**Barrel Files:**
- Not used (Java package structure used instead)

**Registration:**
- Algorithms auto-register at startup via `startup_once=true` constructor
- `RegisterAlgos.java` orchestrates initialization
- Factory pattern via `ModelBuilder.make(algoName)`

## Testing Conventions

**Naming:**
- Java: `*Test.java` classes with `@Test` methods
- Python: `pyunit_*.py` with function-based tests
- R: `runit_*.R` with function-based tests

**Annotations:**
- `@Test` for test methods
- `@BeforeClass` for one-time setup (e.g., cloud initialization)
- `@Rule` for test rules (e.g., `ExpectedException`, `TemporaryFolder`)
- `@Ignore` for disabled tests with reason

**Assertions:**
- JUnit: `assertEquals()`, `assertTrue()`, `assertFalse()`, `assertNotNull()`
- Tolerance for floating-point: `assertEquals(expected, actual, 0.1)`
- Python: `assert` statements with descriptive messages

## Distributed Code Patterns

**DKV Operations:**
- `DKV.put(key, value)` for storing distributed objects
- `DKV.get(key)` for retrieving (blocks until available)
- All distributed objects extend `Iced<T>` or `Keyed<T>`

**MRTask Pattern:**
```java
new MRTask() {
  @Override public void map(Chunk c) {
    // Process chunk
  }
}.doAll(frame);
```

**Futures for Async:**
```java
Futures fs = new Futures();
DKV.put(key, value, fs);
fs.blockForPending(); // Wait for completion
```

## Project-Specific Rules

**Serialization:**
- All distributed objects must extend `Iced<T>` for auto-serialization
- Transient fields: mark with `transient` if not serialized
- Custom serialization: override `read(AutoBuffer)` and `write(AutoBuffer)`

**Resource Cleanup:**
- Always remove Frames and Models in finally blocks
- Use `Scope.track()` for automatic cleanup in tests
- Check for leaked keys via `H2O.store_size()` in `@AfterClass`

**Startup Once Pattern:**
- Algorithm singletons created with `startup_once=true`
- Only called once during H2O initialization
- Registers REST endpoints and algorithm metadata

---

*Convention analysis: 2026-04-28*
