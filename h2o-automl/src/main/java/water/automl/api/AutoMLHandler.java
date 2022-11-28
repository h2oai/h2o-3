package water.automl.api;

import ai.h2o.automl.AutoML;
import hex.Model;
import hex.ModelExportOption;
import water.*;
import water.api.*;
import water.api.schemas3.ModelExportV3;
import water.automl.api.schemas3.AutoMLExportV99;
import water.automl.api.schemas3.AutoMLImportV99;
import water.automl.api.schemas3.AutoMLV99;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OKeyNotFoundArgumentException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

public class AutoMLHandler extends Handler {

  @SuppressWarnings("unused") // called through reflection by RequestServer
  /** Return an AutoML object by ID. */
  public AutoMLV99 fetch(int version, AutoMLV99 autoMLV99) {
    AutoML autoML = DKV.getGet(autoMLV99.automl_id.name);
    if (autoML == null) {
      AutoML[] amls = fetchAllForProject(autoMLV99.automl_id.name);
      if (amls.length > 0) {
        autoML = Stream.of(amls).max(AutoML.byStartTime).get();
      }
    }
    return autoMLV99.fillFromImpl(autoML);
  }

  private static AutoML[] fetchAllForProject(final String project_name) {
    final Key[] automlKeys = KeySnapshot.globalSnapshot().filter(new KeySnapshot.KVFilter() {
      @Override
      public boolean filter(KeySnapshot.KeyInfo k) {
        return Value.isSubclassOf(k._type, AutoML.class) && k._key.toString().startsWith(project_name+AutoML.keySeparator);
      }
    }).keys();
    AutoML[] amls = new AutoML[automlKeys.length];
    for (int i = 0; i < automlKeys.length; i++) {
      AutoML aml = getFromDKV(automlKeys[i]);
      amls[i] = aml;
    }
    return amls;
  }

  private static AutoML getFromDKV(Key key) {
    Value v = DKV.get(key);
    if (v == null)
      throw new H2OKeyNotFoundArgumentException(key.toString());
    return (AutoML) v.get();
  }

  @SuppressWarnings("unused")
  public StreamingSchema fetchBinaryAutoML(int version, AutoMLExportV99 aexport) {
    AutoML autoML = DKV.getGet(aexport.automl_id.name);
    if (autoML == null) {
      AutoML[] amls = fetchAllForProject(aexport.automl_id.name);
      if (amls.length > 0) {
        autoML = Stream.of(amls).max(AutoML.byStartTime).get();
      }
    }
    assert autoML != null;
    return new StreamingSchema(autoML, autoML.projectName());
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLExportV99 exportBinaryAutoML(int version, AutoMLExportV99 aexport) {
    AutoML autoML = DKV.getGet(aexport.automl_id.name);
    if (autoML == null) {
      AutoML[] amls = fetchAllForProject(aexport.automl_id.name);
      if (amls.length > 0) {
        autoML = Stream.of(amls).max(AutoML.byStartTime).get();
      }
    }
    assert autoML != null;
    try {
      URI targetUri = autoML.exportBinaryAutoML(aexport.dir, aexport.force); // aexport.dir: Really file, not dir
      // Send back
      aexport.dir = "file".equals(targetUri.getScheme()) ? new File(targetUri).getCanonicalPath() : targetUri.toString();
    } catch (IOException | FSIOException e) {
      throw new H2OIllegalArgumentException("dir", "exportModel", e);
    }
    return aexport;
  }


  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLV99 uploadBinaryAutoML(int version, AutoMLImportV99 aimport) {
    AutoMLV99 s = Schema.newInstance(AutoMLV99.class);
    try {
      AutoML aml = AutoML.uploadBinaryAutoML(aimport.dir);
      s.fillFromImpl(aml);
    } catch (IOException | FSIOException e) {
      throw new H2OIllegalArgumentException("dir", "importModel", e);
    }
    return s;
  }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLV99 importBinaryAutoML(int version, AutoMLImportV99 aimport) {
    AutoMLV99 s = Schema.newInstance(AutoMLV99.class);
    try {
      AutoML aml = AutoML.importBinaryAutoML(aimport.dir);
      s.fillFromImpl(aml);
    } catch (IOException | FSIOException e) {
      throw new H2OIllegalArgumentException("dir", "importModel", e);
    }
    return s;
  }


}
