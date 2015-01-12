##############################################################################
#
# Simple DeepLearning Wrapper
#
class H2ODeepLearning(object):
    def __init__(self,dataset,x,validation_dataset=None,**kwargs):
        if not isinstance(dataset,H2OFrame):  raise ValueError("dataset must be a H2OFrame not "+str(type(dataset)))
        self.dataset = dataset

        if not dataset[x]: raise ValueError(x+" must be column in "+str(dataset))
        self.x = x

        # Send over the frame
        fr = _send_frame(dataset)
        # And Validationm if any
        if validation_dataset:
            vfr = _send_frame(validation_dataset)
        # Do the big job
        self._model = H2OCONN.DeepLearning(x,fr,vfr,**kwargs)
        H2OCONN.Remove(fr)