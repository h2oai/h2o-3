package water.parser.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import water.fvec.HDFSFileVec;
import water.fvec.Vec;
import water.persist.PersistHdfs;
import water.persist.VecFileSystem;

import static org.apache.parquet.hadoop.ParquetFileReader.PARQUET_READ_PARALLELISM;

class VecReaderEnv {

  private final Configuration _conf;
  private final Path _path;

  private VecReaderEnv(Configuration conf, Path path) {
    _conf = conf;
    _path = path;

    _conf.setInt(PARQUET_READ_PARALLELISM, 1); // disable parallelism (just one virtual file!)
  }

  Configuration getConf() {
    return _conf;
  }

  Path getPath() {
    return _path;
  }

  static VecReaderEnv make(Vec vec) {
    if (vec instanceof HDFSFileVec) {
      // We prefer direct read from HDFS over H2O in-memory caching, saves resources and prevents overloading a single node with data
      Path path = new Path(((HDFSFileVec) vec).getPath());
      return new VecReaderEnv(PersistHdfs.CONF, path);
    } else {
      return new VecReaderEnv(VecFileSystem.makeConfiguration(vec), VecFileSystem.VEC_PATH);
    }

  }

}
