package hex.genmodel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

@RunWith(MockitoJUnitRunner.class)
public class ModelMojoReaderTest {

  private static final String MODEL_INFO = 
          "[info]\n" + 
          "algorithm=mock\n" +
          "n_columns=1\n" +
          "supervised=false\n" +
          "category=Unknown\n" +
          "n_features=1\n" +
          "n_classes=1\n" +
          "balance_classes=false\n" +
          "default_threshold=0.0\n" +
          "mojo_version=1.00\n" +
          "\n" +
          "[columns]\n" +
          "C1\n" +

          "\n" +
          "[domains]\n";

  @Mock
  private MojoReaderBackend readerBackend;
  
  @Before
  public void setupMocks() throws IOException {
    BufferedReader br = new BufferedReader(new StringReader(MODEL_INFO));
    when(readerBackend.getTextFile(eq("model.ini"))).thenReturn(br);
  }
  
  @Test
  public void readFromIgnoresMetadataByDefault() throws IOException {
    MockMojoReader.MockMojoModel mojo = (MockMojoReader.MockMojoModel) ModelMojoReader.readFrom(readerBackend);
    ModelMojoReader reader = mojo.getReader();
    
    verify(reader, never()).readModelSpecificAttributes();
  }

  @Test
  public void readFromReadsMetadataWhenRequested() throws IOException {
    MockMojoReader.MockMojoModel mojo = (MockMojoReader.MockMojoModel) ModelMojoReader.readFrom(readerBackend, true);
    ModelMojoReader reader = mojo.getReader();

    verify(reader).readModelSpecificAttributes();
  }

}
