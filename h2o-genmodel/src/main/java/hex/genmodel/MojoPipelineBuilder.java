package hex.genmodel;

import hex.genmodel.utils.IOUtils;

import java.io.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MojoPipelineBuilder {

  private final Map<String, File> _files = new HashMap<>();
  private final Map<String, MojoModel> _models = new HashMap<>();
  private final Map<String, String> _mapping = new HashMap<>();
  private String _mainModelAlias;

  public MojoPipelineBuilder addModel(String modelAlias, File mojoFile) throws IOException {
    MojoModel model = MojoModel.load(mojoFile.getAbsolutePath());
    _files.put(modelAlias, mojoFile);
    _models.put(modelAlias, model);
    return this;
  }

  public MojoPipelineBuilder addMainModel(String modelAlias, File mojoFile) throws IOException {
    addModel(modelAlias, mojoFile);
    _mainModelAlias = modelAlias;
    return this;
  }

  public MojoPipelineBuilder addMappings(List<MappingSpec> specs) {
    for (MappingSpec spec : specs) {
      addMapping(spec);
    }
    return this;
  }

  public MojoPipelineBuilder addMapping(MappingSpec spec) {
    return addMapping(spec._columnName, spec._modelAlias, spec._predsIndex);
  }

  public MojoPipelineBuilder addMapping(String columnName, String sourceModelAlias, int sourceModelPredictionIndex) {
    _mapping.put(columnName, sourceModelAlias + ":" + sourceModelPredictionIndex);
    return this;
  }

  public void buildPipeline(File pipelineFile) throws IOException {
    MojoPipelineWriter w = new MojoPipelineWriter(_models, _mapping, _mainModelAlias);

    try (FileOutputStream fos = new FileOutputStream(pipelineFile);
         ZipOutputStream zos = new ZipOutputStream(fos)) {
      w.writeTo(zos);
      for (Map.Entry<String, File> mojoFile : _files.entrySet()) {
        try (ZipFile zf = new ZipFile(mojoFile.getValue())) {
          Enumeration<? extends ZipEntry> entries = zf.entries();
          while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();

            ZipEntry copy = new ZipEntry("models/" + mojoFile.getKey() + "/" + ze.getName());
            if (copy.getSize() >= 0) {
              copy.setSize(copy.getSize());
            }
            copy.setTime(copy.getTime());
            zos.putNextEntry(copy);
            try (InputStream input = zf.getInputStream(zf.getEntry(ze.getName()))) {
              IOUtils.copyStream(input, zos);
            }
            zos.closeEntry();
          }
        }
      }
    }
  }

  public static class MappingSpec {
    public String _columnName;
    public String _modelAlias;
    public int _predsIndex;

    public static MappingSpec parse(String spec) throws NumberFormatException, IndexOutOfBoundsException {
      MappingSpec ms = new MappingSpec();
      String[] parts = spec.split("=", 2);
      ms._columnName = parts[0];
      parts = parts[1].split(":", 2);
      ms._modelAlias = parts[0];
      ms._predsIndex = Integer.valueOf(parts[1]);
      return ms;
    }
  }

}
