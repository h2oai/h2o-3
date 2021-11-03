# -*- encoding: utf-8 -*-

class H2OPlotResult(object):

    def __init__(self, *args, figure=None):
        self.figure = figure
        if len(args) == 1 and type(args[0]) is list: # pdp case
            self._tuple = args[0]
        else:    
            self._tuple = args

    def __iter__(self):
        return iter(self._tuple)

    def __getitem__(self, idx):
        return self._tuple[idx]

