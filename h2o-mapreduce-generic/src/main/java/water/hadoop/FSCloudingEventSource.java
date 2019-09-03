package water.hadoop;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

class FSCloudingEventSource {

  private final FileSystem _fs;
  private final Path _path;

  private final Set<String> _discovered = new HashSet<>();
  
  FSCloudingEventSource(Configuration conf, String cloudingDir) throws IOException  {
    _fs = FileSystem.get(conf);
    _path = makeQualifiedPath(conf, new Path(cloudingDir));
  }

  boolean isEmpty() throws IOException {
    return !_fs.exists(_path);
  }

  Path getPath() {
    return _path;
  }

  private static Path makeQualifiedPath(Configuration conf, Path cloudingPath) throws IOException {
    return cloudingPath.getFileSystem(conf).makeQualified(cloudingPath);
  }
  
  Iterable<CloudingEvent> fetchNewEvents() {
    try {
      if (isEmpty())
        return Collections.emptyList();
      List<CloudingEvent> newEvents = Arrays.stream(_fs
              .listStatus(_path, path -> !_discovered.contains(path.getName())))
              .map(it -> CloudingEvent.fromFileStatus(_fs, it))
              .filter(Objects::nonNull)
              .sorted(Collections.reverseOrder())
              .collect(Collectors.toList());
      newEvents.forEach(it -> _discovered.add(it._path.getName()));
      return newEvents;
    } catch (IOException e) {
      throw new RuntimeException("Failed to read events", e);
    }
  }

}

class CloudingEvent implements Comparable<CloudingEvent> {
  CloudingEventType _type;
  long _timestamp;
  String _ip;
  int _port;

  FileSystem _f;
  Path _path;

  String readPayload() throws IOException {
    try (BufferedReader r = new BufferedReader(new InputStreamReader(_f.open(_path)))) {
      return r.readLine();
    }
  }

  @Override
  public int compareTo(CloudingEvent o) {
    int typeComp = Integer.compare(_type._priority, o._type._priority);
    return typeComp != 0 ? typeComp : Long.compare(_timestamp, o._timestamp);
  }

  static CloudingEvent fromFileStatus(FileSystem f, FileStatus fs) {
    final String name = fs.getPath().getName();

    int typeCodeIdx = name.lastIndexOf('.');
    if (typeCodeIdx < 0)
      return null;

    CloudingEventType type = CloudingEventType.fromCode(name.substring(typeCodeIdx + 1));
    if (type == null)
      return null;

    String[] ipPort = name.substring(0, typeCodeIdx).split("_");
    if (ipPort.length != 2) {
      throw new IllegalStateException("Unable to parse filename: " + name);
    }
    
    CloudingEvent event = new CloudingEvent();
    event._type = type;
    event._timestamp = fs.getModificationTime();
    event._ip = ipPort[0];
    event._port = Integer.parseInt(ipPort[1]);
    event._path = fs.getPath();
    event._f = f;
    
    return event;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CloudingEvent that = (CloudingEvent) o;
    return _timestamp == that._timestamp &&
            _port == that._port &&
            _type == that._type &&
            Objects.equals(_ip, that._ip) &&
            Objects.equals(_f, that._f) &&
            Objects.equals(_path, that._path);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_path);
  }
}

enum CloudingEventType {
  NODE_STARTED("node", 0),
  NODE_CLOUDED("leader", 1),
  NODE_FAILED("exit", 3, true);

  String _code;
  int _priority;
  boolean _fatal;
  
  CloudingEventType(String code, int priority, boolean isFatal) {
    _code = code;
    _fatal = isFatal;
    _priority = priority;
  }

  CloudingEventType(String code, int priority) {
    this(code, priority, false);
  }

  static CloudingEventType fromCode(String code) {
    for (CloudingEventType v : CloudingEventType.values()) {
      if (v._code.equals(code)) {
        return v;
      }
    }
    return null;
  }

}
