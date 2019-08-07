class_doc = """
Build a Deep Neural Network model using CPUs
Builds a feed-forward multilayer artificial neural network on an H2OFrame
"""

class_examples = """
>>> import h2o
>>> from h2o.estimators.deeplearning import H2ODeepLearningEstimator
>>> h2o.connect()
>>> rows = [[1,2,3,4,0], [2,1,2,4,1], [2,1,4,2,1], [0,1,2,34,1], [2,3,4,1,0]] * 50
>>> fr = h2o.H2OFrame(rows)
>>> fr[4] = fr[4].asfactor()
>>> model = H2ODeepLearningEstimator()
>>> model.train(x=range(4), y=4, training_frame=fr)
"""

class_init_extra = """
if isinstance(self, H2OAutoEncoderEstimator): self._parms['autoencoder'] = True
"""

properties = dict(
    initial_biases=dict(
        setter="""
assert_is_type({pname}, None, [H2OFrame, None])
self._parms["{sname}"] = {pname}
"""
    ),

    initial_weights=dict(
        setter="""
assert_is_type({pname}, None, [H2OFrame, None])
self._parms["{sname}"] = {pname}
"""
    ),
)

module_extras = """
class H2OAutoEncoderEstimator(H2ODeepLearningEstimator):
    \"""
    :examples:
    
    >>> import h2o as ml
    >>> from h2o.estimators.deeplearning import H2OAutoEncoderEstimator
    >>> ml.init()
    >>> rows = [[1,2,3,4,0]*50, [2,1,2,4,1]*50, [2,1,4,2,1]*50, [0,1,2,34,1]*50, [2,3,4,1,0]*50]
    >>> fr = ml.H2OFrame(rows)
    >>> fr[4] = fr[4].asfactor()
    >>> model = H2OAutoEncoderEstimator()
    >>> model.train(x=range(4), training_frame=fr)
    \"""
    pass
"""
