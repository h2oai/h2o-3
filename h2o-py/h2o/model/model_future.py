# DELETE ME I'm useless !!!

class H2OModelFuture(object):
    """
    A class representing a future H2O model (a model that may, or may not, be in the process of being built).
    """
    def __init__(self, job, x):
        self.job = job
        self.x = x

    def poll(self):
        self.job.poll()
        self.x = None
