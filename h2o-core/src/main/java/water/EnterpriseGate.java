package water;

import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;

/**
 * Server-side H2O-3 Enterprise gate for production-only capabilities.
 *
 * The OSS build enforces the paywall where the artifacts are actually produced, so it holds
 * regardless of how the caller got there - REST, Flow, a patched client, an embedded H2O
 * node, or a client-mode node that joined the cluster and pulled the model out of the DKV:
 *
 * <ul>
 *   <li>Model MOJO: {@code ModelMojoWriter.writeTo(OutputStream, StreamWriteOption...)}, the
 *       single funnel every <em>model</em> MOJO byte passes through, including the nested
 *       writers used for Stacked Ensembles.</li>
 *   <li>Model POJO: {@code Model.toJava(OutputStream, boolean, boolean)}, which both public
 *       POJO generators route through.</li>
 *   <li>Assembly MOJO 2 pipeline: {@code ProtobufPipelineWriter.writeTo(...)}. An
 *       {@code water.rapids.Assembly} carries only munging steps, never a model, but the
 *       artifact is still a {@code .mojo} zip handed to the caller.</li>
 * </ul>
 *
 * The REST endpoints additionally call {@link #block(String)} directly - {@code ModelsHandler}
 * ({@code fetchJavaCode}, {@code fetchMojo}, {@code exportMojo}),
 * {@code AssemblyToMojoPipelineExportHandler.fetchMojoPipeline} and
 * {@code AssemblyHandler.toJava}. That is not the enforcement - the writers above are - but it
 * fails the request at the API boundary with a message naming the operation the caller asked
 * for. {@code AssemblyHandler.toJava} is the one case where the handler <em>is</em> the only
 * enforcement point, because the Java text is generated afterwards by {@code RequestServer}
 * rather than by a {@code StreamWriter}. The client-side notices in the Python/R packages are
 * only the friendly message.
 *
 * Binary model export stays open by design: a model can still be moved to an Enterprise
 * cluster, which is the intended upgrade path. What OSS will not do is convert one into a
 * deployable scoring artifact.
 */
public class EnterpriseGate {

  public static final String ENTERPRISE_EMAIL = "enterprise@h2o.ai";
  public static final String LEARN_MORE = "h2o.ai/h2o-3/oss-vs-enterprise";

  /**
   * True when this JVM is running the H2O test harness. The multi-node JUnit suites
   * legitimately round-trip models through MOJOs to assert that in-cluster scoring and
   * MOJO scoring agree, so gating {@code hex.Model} unconditionally would fail a few
   * dozen assertions that have nothing to do with artifact extraction.
   *
   * h2o-test-support is a test-only dependency and is not part of any h2o.jar assembly,
   * so a shipped cluster never has this class - including the ones the Python and R
   * suites launch, which is why those keep exercising the block end to end.
   */
  private static final boolean TEST_HARNESS = isClassAvailable("water.runner.H2ORunner");

  private EnterpriseGate() {}

  /** Visible for testing: whether the {@link #blockUnlessTestHarness} exemption applies here. */
  static boolean isTestHarness() {
    return TEST_HARNESS;
  }

  private static boolean isClassAvailable(String className) {
    try {
      Class.forName(className, false, EnterpriseGate.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  /**
   * Log the OSS-vs-Enterprise notice at cluster startup. Emitted via {@link Log#info}
   * (not System.out) so it flows through the standard logging pipeline.
   */
  public static void logStartupBanner() {
    Log.info("You are running the community edition of H2O-3 OSS.");
    Log.info("For commercial use, H2O-3 Enterprise is now recommended.");
    Log.info("This includes production support, CVE fixes, multi-node scaling, " +
             "model artifact extraction, and more.");
    Log.info("See " + LEARN_MORE + " for additional details.");
    Log.info("Contact " + ENTERPRISE_EMAIL + " to upgrade.");
  }

  /**
   * Enforcement point for the {@code hex.Model} artifact-producing methods, which the
   * test harness itself relies on. Identical to {@link #block(String)} for every shipped
   * cluster; a no-op under the JUnit suites. The REST layer deliberately does not use
   * this variant - every request from a client is blocked unconditionally.
   */
  public static void blockUnlessTestHarness(String operation) {
    if (TEST_HARNESS) return;
    block(operation);
  }

  /**
   * Block a MOJO-related capability. {@code operation} is the user-facing action
   * (e.g. "MOJO export", "MOJO import"). Always throws.
   */
  public static void block(String operation) {
    throw new H2OIllegalArgumentException(
        "H2O-3 ENTERPRISE REQUIRED - " + operation + " is blocked. " +
        operation + " is a production capability available only in H2O-3 Enterprise, " +
        "the commercially supported tier of H2O-3. You are running H2O-3 OSS, built for " +
        "experimentation and research, not production. Upgrade to H2O-3 Enterprise for " +
        "multi-node production deployment (Hadoop, Spark, Kubernetes), audit-ready governance " +
        "(SOC 2, ISO 27001, ISO 42001), prioritized CVE patching, and premium support with SLAs. " +
        "H2O-3 Enterprise is a drop-in replacement - your existing code, APIs, and pipelines run " +
        "unchanged. Learn more: " + LEARN_MORE + ". Contact: " + ENTERPRISE_EMAIL);
  }
}
