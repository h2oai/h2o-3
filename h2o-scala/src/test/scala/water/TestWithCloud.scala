package water

import org.junit.Assert._
import org.scalatest.BeforeAndAfterAll

/**
  * Test fixture to control cloud
  * from TestUtil in H2O module
  */
class TestWithCloud(size: Int, args: String*) extends TestBase with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    super.beforeAll()
    stallTillCloudSize()
  }

  private lazy val isInitialized: Boolean = try {
    H2O.main(args.toArray)
    H2O.registerRestApis(System.getProperty("user.dir"))
    true
  } catch {
    case x: Exception => false
  }

  protected lazy val initialKeyCount: Int = H2O.store_size

  // Stall test until we see at least X members of the Cloud
  def stallTillCloudSize() {
    assertTrue("Failed to init H2O", isInitialized)
    H2O.waitForCloudSize(size, 30000)
    assertTrue("Weird number of entries in KVS", initialKeyCount >= 0)
  }
}
