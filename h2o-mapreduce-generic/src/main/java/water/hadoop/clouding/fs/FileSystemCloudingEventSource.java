package water.hadoop.clouding.fs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FileSystemCloudingEventSource {

  private final FileSystem _fs;
  private final Path _path;

  private final Set<String> _discovered = new HashSet<>();
  
  public FileSystemCloudingEventSource(Configuration conf, String cloudingDir) throws IOException {
    _fs = FileSystem.get(conf);
    _path = makeQualifiedPath(conf, new Path(cloudingDir));
  }

  public boolean isEmpty() throws IOException {
    return !_fs.exists(_path);
  }

  public Path getPath() {
    return _path;
  }

  private static Path makeQualifiedPath(Configuration conf, Path cloudingPath) throws IOException {
    return cloudingPath.getFileSystem(conf).makeQualified(cloudingPath);
  }
  
  public Iterable<CloudingEvent> fetchNewEvents() {
    try {
      if (isEmpty())
        return Collections.emptyList();
      List<CloudingEvent> newEvents = Arrays.stream(_fs
              .listStatus(_path, path -> !_discovered.contains(path.getName())))
              .map(it -> CloudingEvent.fromFileStatus(_fs, it))
              .filter(Objects::nonNull)
              .sorted(Collections.reverseOrder())
              .collect(Collectors.toList());
      newEvents.forEach(it -> _discovered.add(it.getName()));
      return newEvents;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read events", e);
    }
  }

}
