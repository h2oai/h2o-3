package water.hadoop.clouding.fs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CloudingEventTest {

  @Mock
  FileStatus fileStatus;

  @Mock
  Path path;

  @Mock
  FileSystem fileSystem;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();
  
  @Test
  public void readPayload() throws IOException {
    File f = temporaryFolder.newFile("test.txt");
    Files.write(Paths.get(f.toURI()), "test-payload".getBytes());

    FileSystem fs = FileSystem.get(new Configuration());

    CloudingEvent expected = new CloudingEvent(
            CloudingEventType.NODE_CLOUDED,
            System.currentTimeMillis(),
            "192.168.0.17",
            54321,
            fs,
            new Path(f.getAbsolutePath())
    );

    String payload = expected.readPayload();
    assertEquals("test-payload", payload);
  }

  @Test
  public void fromFileStatus() {
    final long now = System.currentTimeMillis();
    
    when(fileStatus.getPath()).thenReturn(path);
    when(fileStatus.getModificationTime()).thenReturn(now);
    when(path.getName()).thenReturn("192.168.0.17_54321.leader");
    
    CloudingEvent event = CloudingEvent.fromFileStatus(fileSystem, fileStatus);

    CloudingEvent expected = new CloudingEvent(
            CloudingEventType.NODE_CLOUDED,
            now,
            "192.168.0.17",
            54321,
            fileSystem,
            path
    );
    
    assertEquals(expected, event);
  }
  
}
