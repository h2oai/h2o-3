class_init_setparams = """
elif pname == 'pre_trained':
    setattr(self, pname, pvalue)
    self._determine_vec_size();
    setattr(self, 'vec_size', self.vec_size)
"""

class_extras = """
def _requires_training_frame(self):
    \"""
    Determines if Word2Vec algorithm requires a training frame.
    :return: False.
    \"""
    return False

@staticmethod
def from_external(external=H2OFrame):
    \"""
    Creates new H2OWord2vecEstimator based on an external model.
    :param external: H2OFrame with an external model
    :return: H2OWord2vecEstimator instance representing the external model
    \"""
    w2v_model = H2OWord2vecEstimator(pre_trained=external)
    w2v_model.train()
    return w2v_model

def _determine_vec_size(self):
    \"""
    Determines vec_size for a pre-trained model after basic model verification.
    \"""
    first_column = self.pre_trained.types[self.pre_trained.columns[0]]

    if first_column != 'string':
        raise H2OValueError("First column of given pre_trained model %s is required to be a String",
                            self.pre_trained.frame_id)

    if list(self.pre_trained.types.values()).count('string') > 1:
        raise H2OValueError("There are multiple columns in given pre_trained model %s with a String type.",
                            self.pre_trained.frame_id)

    self.vec_size = self.pre_trained.dim[1] - 1;
"""
