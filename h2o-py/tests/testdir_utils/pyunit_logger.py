from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import logging
from future.utils import PY2
from tests import pyunit_utils as pu


class LoggingContext:
    def __init__(self, logger, level=None, handler=None, close=True):
        self.logger = logger
        self.level = level
        self.handler = handler
        self.close = close

    def __enter__(self):
        if self.level is not None:
            self.old_level = self.logger.level
            self.logger.setLevel(self.level)
        if self.handler:
            self.logger.addHandler(self.handler)

    def __exit__(self, et, ev, tb):
        if self.level is not None:
            self.logger.setLevel(self.old_level)
        if self.handler:
            self.logger.removeHandler(self.handler)
        if self.handler and self.close:
            self.handler.close()
 
 
def _has_handlers(logger):
    if PY2:
        l = logger
        while l:
            if l.handlers:
                return True
            l = l.parent if l.propagate else None
        return False
    else:
        return logger.hasHandlers()


def test_h2o_logger_has_no_handler_by_default():
    # as a library, h2o should not define handlers for its loggers 
    from h2o.utils.config import H2OConfigReader
    H2OConfigReader.get_config()  # this module uses h2o logger
    
    logger = logging.getLogger('h2o')
    assert not _has_handlers(logger)
    
 
def test_h2o_logger_inherits_root_logger():
    from h2o.utils.config import H2OConfigReader
    H2OConfigReader.get_config()  # this module uses h2o logger
    
    root = logging.getLogger()
    logger = logging.getLogger('h2o')
    console = logging.StreamHandler()
    
    assert not _has_handlers(root)
    assert not _has_handlers(logger)
    
    with LoggingContext(root, handler=console, level=logging.INFO):
        assert _has_handlers(root)
        assert _has_handlers(logger)
        logging.info("list root handlers: %s", root.handlers)
        logging.info("list h2o handlers: %s", logger.handlers)
        

pu.run_tests([
    test_h2o_logger_has_no_handler_by_default,
    test_h2o_logger_inherits_root_logger
])
