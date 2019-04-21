import water.udf.CFunc2 as Func

class TestCFunc2(Func):
    """
    Compute sum of actual + predict
    """

    def apply(self, rowActual, rowPredict):
        return sum(rowActual.readDoubles()) + sum(rowPredict.readDoubles())

