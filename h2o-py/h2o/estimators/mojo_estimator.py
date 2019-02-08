import h2o
from h2o.estimators.estimator_base import H2OEstimator
from h2o.exceptions import H2OValueError
from h2o.job import H2OJob

class H2OMojoDelegatingEstimator(H2OEstimator):
    """
    Mojo delegating estimator
    """

    algo = "mojodelegating"
    
    def __init__(self, mojo_file_path = None):
        if(mojo_file_path is None): return
        super(H2OMojoDelegatingEstimator, self).__init__()
        if(mojo_file_path is None): raise H2OValueError("MOJO file path is empty")
        
        self._parms['mojo_file_path'] = mojo_file_path

    def train(self, x=None, y=None, training_frame=None, offset_column=None, fold_column=None, weights_column=None,
              validation_frame=None, max_runtime_secs=None, ignored_columns=None, model_id=None, verbose=False):
        
        mojo_key = h2o.lazy_import(self._parms['mojo_file_path']);
        
        parms = {'mojo_key': mojo_key[0]}
        parms = {k: H2OEstimator._keyify_if_h2oframe(parms[k]) for k in parms}
        rest_ver = 3

        model_builder_json = h2o.api("POST /%d/ModelBuilders/%s" % (rest_ver, self.algo), data=parms)
        model = H2OJob(model_builder_json, job_type=(self.algo + " Model Build"))

        if self._future:
            self._job = model
            self._rest_version = rest_ver
            return

        model.poll(verbose_model_scoring_history=verbose)
        model_json = h2o.api("GET /%d/Models/%s" % (rest_ver, model.dest_key))["models"][0]
        self._resolve_model(model.dest_key, model_json)
        

    def _requires_training_frame(self):
        return False
