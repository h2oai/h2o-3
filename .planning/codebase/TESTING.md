# Testing Patterns

**Analysis Date:** 2026-04-28

## Test Framework

**Runner:**
- JUnit 4.x
- Custom H2O runner: `@RunWith(H2ORunner.class)` for cluster tests
- Parameterized tests: `@RunWith(Parameterized.class)`
- Cloud size annotation: `@CloudSize(1)` specifies nodes required

**Assertion Library:**
- JUnit assertions: `assertEquals()`, `assertTrue()`, `assertFalse()`, `assertNotNull()`
- Hamcrest matchers: `assertThat()` with `OrderingComparison` matchers
- Python: native `assert` statements
- R: `expect_equal()`, `expect_true()` from testthat

**Run Commands:**
```bash
./gradlew :h2o-algos:testSingleNode           # Single-node cluster tests
./gradlew :h2o-algos:testMultiNode            # Multi-node cluster tests (5 JVMs)
./gradlew :h2o-algos:testSingleNodeOneProc    # Single JVM tests
./gradlew :h2o-core:test                       # Standard JUnit tests
./gradlew test -Dtest.single=GBMTest          # Run specific test class
```

## Test File Organization

**Location:**
- Java: Co-located in module test directories
  - Core tests: `h2o-core/src/test/java/`
  - Algorithm tests: `h2o-algos/src/test/java/hex/<algo>/`
  - Test support: `h2o-test-support/src/main/java/water/TestUtil.java`
- Python: `h2o-py/tests/testdir_<category>/`
- R: `h2o-r/tests/testdir_algos/<algo>/`

**Naming:**
- Java: `*Test.java` (e.g., `GBMTest.java`, `ModelBuilderTest.java`)
- Python: `pyunit_*.py` (e.g., `pyunit_h2oH2OFrame.py`)
- R: `runit_*.R` (e.g., `runit_GBM_KDD_trees_large.R`)
- Test methods: descriptive names (e.g., `testGBMRegressionGaussian()`, `testRebalancePubDev5400()`)

**Structure:**
```
h2o-algos/src/test/java/hex/
├── tree/
│   ├── gbm/
│   │   └── GBMTest.java          # GBM algorithm tests
│   └── CompressedTreeTest.java
├── glm/
│   └── GLMTest.java
└── ModelBuilderTest.java          # Framework tests
```

## Test Structure

**Suite Organization:**
```java
@RunWith(Parameterized.class)
public class GBMTest extends TestUtil {

  @Rule
  public transient ExpectedException expectedException = ExpectedException.none();

  @Rule
  public transient TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void stall() {
    stall_till_cloudsize(1);
  }

  @Parameterized.Parameters(name = "{index}: gbm({0})")
  public static Iterable<?> data() {
    return Arrays.asList("Default", "EmulateConstraints");
  }

  @Parameterized.Parameter
  public String test_type;

  @Test
  public void testGBMRegressionGaussian() {
    GBMModel gbm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
      // ... test logic
    } finally {
      if (fr != null) fr.remove();
      if (fr2 != null) fr2.remove();
      if (gbm != null) gbm.remove();
    }
  }
}
```

**Python test structure:**
```python
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def test_function():
    # Test implementation
    fr = h2o.import_file(path=pyunit_utils.locate("smalldata/data.csv"))
    # ... assertions
    assert condition, "descriptive error message"

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_function)
else:
    test_function()
```

**R test structure:**
```r
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.test_name <- function(){
    Log.info("Test description")

    data <- h2o.uploadFile(path = locate("smalldata/data.csv"))
    model <- h2o.gbm(x = 1:2, y = 3, training_frame = data)

    expect_equal(model@model$mse_train, expected, tolerance = 0.0001)
}

doTest("Test Name", check.test_name)
```

**Patterns:**
- Setup: Load data via `parseTestFile()` or `h2o.import_file()`
- Teardown: Always clean up in finally block
- Assertions: Compare model metrics, predictions, or internal state
- Scope tracking: Use `Scope.enter()` / `Scope.exit()` for automatic cleanup

## Mocking

**Framework:** Not heavily used (integration tests preferred)

**Patterns:**
- Test doubles: `DummyModel`, `DummyModelBuilder` in `h2o-test-support`
- Message interception: `MessageInstallAction` for testing distributed messages
- Minimal mocking due to distributed nature of system

**What to Mock:**
- Rarely needed - most tests use real H2O cluster
- Custom model builders for framework testing

**What NOT to Mock:**
- DKV operations (test with real distributed store)
- MRTask execution (test with real cluster)
- Frame/Vec/Chunk data structures

## Fixtures and Factories

**Test Data:**
```java
// Parse from smalldata directory
Frame fr = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");

// Build synthetic data
Frame train = new TestFrameBuilder()
    .withName("testFrame")
    .withColNames("ColA", "Response")
    .withVecTypes(Vec.T_NUM, Vec.T_CAT)
    .withDataForCol(0, colA)
    .withDataForCol(1, resp)
    .withChunkLayout(layout)
    .build();
```

**Python test data:**
```python
# Locate data via utility
data = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

# Inline data
data = [[1, .4, "a"], [2, 5, "b"]]
fr = h2o.H2OFrame(data, column_names=["col1", "col2", "col3"])
```

**Location:**
- Test data: `smalldata/` and `bigdata/` directories (downloaded via `./gradlew syncSmalldata`)
- Synthetic data: Generated in tests via `TestFrameBuilder`, `CreateFrame`
- No centralized fixtures directory

## Coverage

**Requirements:** No strict coverage target enforced

**View Coverage:**
```bash
./gradlew test -PjacocoCoverage
# Coverage reports in build/reports/jacoco/
```

**Notes:**
- Jacoco integration available but optional
- Coverage increases test execution time 10x
- Focus on integration tests over unit test coverage

## Test Types

**Unit Tests:**
- Scope: Individual methods, utilities, data structures
- Example: `AUCBuilderTest`, `ConfusionMatrixTest`, `ArrayUtilsTest`
- Run in single JVM via standard JUnit runner

**Integration Tests:**
- Scope: Full algorithm execution, distributed operations
- Example: `GBMTest`, `DeepLearningTest`, `GLMTest`
- Run with multi-node H2O cluster (default 5 JVMs)
- Suffix: `_large.R` for tests requiring substantial data/compute

**Multi-Node Tests:**
- Scope: Cluster behavior, distributed task coordination
- Execution: `testMultiNode.sh` spawns 5 JVMs forming cluster
- Cloud size: Controlled via `MINCLOUDSIZE` property
- Logs: Separate log file per node in `build/`

**Single-Node Tests:**
- Scope: Algorithm correctness without multi-node complexity
- Execution: `testSingleNode.sh` spawns cluster with 1 node
- Faster execution than multi-node

**Single-Process Tests:**
- Scope: Framework tests, utilities, serialization
- Execution: `testSingleNodeOneProc.sh` runs in single JVM
- Fastest execution, no cluster overhead

## Common Patterns

**Async Testing:**
```java
@Test
public void testAsyncOperation() {
  Job<Model> job = new Job<>(_result, "Job name", "algo");
  Model model = job.trainModel().get(); // Block for completion
  Assert.assertTrue(job.isStopped());
}
```

**Error Testing:**
```java
@Test
public void testInvalidParameters() {
  expectedException.expect(H2OModelBuilderIllegalArgumentException.class);
  GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
  parms._ntrees = -1; // Invalid
  new GBM(parms).trainModel().get();
}

// Or with annotation
@Test(expected = IllegalArgumentException.class)
public void testBadInput() {
  // ... code that should throw
}
```

**Parameterized Tests:**
```java
@RunWith(Parameterized.class)
public class GBMTest extends TestUtil {
  @Parameterized.Parameters(name = "{index}: gbm({0})")
  public static Iterable<?> data() {
    return Arrays.asList("Default", "EmulateConstraints");
  }

  @Parameterized.Parameter
  public String test_type;

  @Test
  public void test() {
    // Test runs once per parameter
    if ("EmulateConstraints".equals(test_type)) {
      // Special logic
    }
  }
}
```

**Resource Cleanup Pattern:**
```java
@Test
public void testWithResources() {
  try {
    Scope.enter();
    Frame train = Scope.track(parseTestFile("data.csv"));
    Frame valid = Scope.track(parseTestFile("valid.csv"));
    Model model = trainModel(train, valid);
    // ... assertions
  } finally {
    Scope.exit(); // Automatic cleanup of tracked objects
  }
}
```

**Cloud Size Testing:**
```java
@Test
public void testMultiNodeOnly() {
  org.junit.Assume.assumeTrue(H2O.getCloudSize() > 1);
  // Test only runs on multi-node clusters
}
```

## Test Execution Framework

**Custom H2O Test Framework:**
- Tests spawn multiple JVMs that form H2O cluster
- Not standard JUnit runners (custom test infrastructure)
- Shell scripts: `testSingleNode.sh`, `testMultiNode.sh`, `testSingleNodeOneProc.sh`
- Environment variables control execution:
  - `ROOT_DIR`: Project root
  - `PROJECT_NAME`: Module name
  - `BUILD_DIR`: Build output directory
  - `JVM_CLASSPATH`: Runtime classpath

**Cloud Initialization:**
```java
@BeforeClass
public static void stall() {
  stall_till_cloudsize(1); // Block until cluster size = 1
}

// Multi-node tests
stall_till_cloudsize(5, timeout); // Wait for 5-node cluster
```

**Leak Detection:**
```java
@AfterClass
public static void checkLeakedKeys() {
  int leaked_keys = H2O.store_size() - _initial_keycnt;
  // Fails test if keys leaked (excluding expected types)
}
```

## Test Categories by Naming

**Prefixes indicate test characteristics:**
- `pyunit_` - Python unit tests
- `runit_` - R unit tests
- `INTERNAL_` - Internal/long-running tests
- `NOPASS_` - Known failing tests (disabled)
- `PUBDEV_` - Tests for specific JIRA issues (e.g., `PUBDEV_8088`)
- `GH_` - Tests for GitHub issues (e.g., `GH_16312`)

**Suffixes indicate scope:**
- `_large` - Large dataset tests
- `_xlarge` - Extra large tests (cluster/data intensive)
- `_medium` - Medium-sized tests

## Python/R Test Utilities

**Python:**
- Import: `from tests import pyunit_utils`
- Utilities: `pyunit_utils.locate()` finds data files, `pyunit_utils.standalone_test()` wraps test execution
- Setup: `h2o-py-test-setup.py` initializes H2O connection

**R:**
- Source: `source("../../../scripts/h2o-r-test-setup.R")`
- Utilities: `locate()` finds data, `Log.info()` logs messages, `doTest()` runs test with name
- Functions: `h2o.uploadFile()`, `h2o.importFile()` for data loading

## Requirements for New Tests

**Per project guidelines:**
- New code requires unit tests
- Python: pyunits in `h2o-py/tests/`
- R: runits in `h2o-r/tests/`
- Java: JUnits co-located with source
- PRs require passing CI tests (Jenkins)
- Test naming must follow conventions

**Minimum requirements:**
- 8GB RAM for running tests (16GB recommended)
- Tests spawn 5 JVMs for multi-node execution
- Use `-x test` to skip during builds on limited systems

---

*Testing analysis: 2026-04-28*
