package water.automl.api;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.TimedH2OJob;
import water.DKV;
import water.Key;
import water.api.Handler;
import water.api.schemas3.JobV3;
import water.automl.api.schemas3.AutoMLBuildSpecV3;
import water.fvec.Frame;


public class AutoMLBuilderHandler extends Handler {
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public AutoMLBuildSpecV3 build(int version, AutoMLBuildSpecV3 args) {

    Frame frame =
            (null == args.input_spec.import_files ?
                    null : (Frame)DKV.getGet(args.input_spec.import_files.path));
    AutoML aml;
    if( null==frame )
      aml = AutoML.makeAutoML(Key.<AutoML>make(),
              args.input_spec.import_files.path,
              /* args.datasets_to_join, */ null,
              args.input_spec.response_column.column_name,
              args.loss,
              args.max_time,
              -1,
              args.ensemble,
              args.exclude,
              args.try_mutations);
    else throw new IllegalArgumentException("error: data already parsed");
//      aml = new AutoML(
//            Key.<AutoML>make(),
//            args.dataset,
//            frame,
//            args.target_name,
//            args.loss,
//            args.max_time,
//            -1,     // min accuracy or stopping crit ... "loss threshold"
//            args.ensemble,
//            args.exclude,
//            args.try_mutations);
    DKV.put(aml);
    args.job = new JobV3().fillFromImpl(new TimedH2OJob(aml,aml._key).start());
    return args;
  }
}
