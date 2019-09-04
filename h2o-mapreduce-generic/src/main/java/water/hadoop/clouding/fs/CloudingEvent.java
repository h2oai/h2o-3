package water.hadoop.clouding.fs;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;

public class CloudingEvent implements Comparable<CloudingEvent> {
  private final CloudingEventType _type;
  private final long _timestamp;
  private final String _ip;
  private final int _port;
  private final FileSystem _f;
  private final Path _path;

  CloudingEvent(CloudingEventType type, long timestamp, String ip, int port, FileSystem f, Path path) {
    _type = type;
    _timestamp = timestamp;
    _ip = ip;
    _port = port;
    _f = f;
    _path = path;
  }

  public CloudingEventType getType() {
    return _type;
  }

  public String getIp() {
    return _ip;
  }

  public int getPort() {
    return _port;
  }

  String getName() {
    return _path.getName();
  }
  
  public String readPayload() throws IOException {
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

    return new CloudingEvent(
            type,
            fs.getModificationTime(),
            ipPort[0],
            Integer.parseInt(ipPort[1]),
            f,
            fs.getPath());
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
