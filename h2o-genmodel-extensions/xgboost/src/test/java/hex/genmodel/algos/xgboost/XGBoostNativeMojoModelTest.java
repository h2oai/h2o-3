package hex.genmodel.algos.xgboost;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class XGBoostNativeMojoModelTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void getBoosterDump_main() throws Exception {
    final File mojoFile = tmp.newFile("xgboost.zip");
    try (InputStream is = XGBoostNativeMojoModelTest.class.getResourceAsStream("xgboost.zip")){
      Files.copy(is, Paths.get(mojoFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
    }
    XGBoostNativeMojoModel.main(new String[]{"--dump", mojoFile.getAbsolutePath(), "false", "json"}); // expect no smoke
  }

}