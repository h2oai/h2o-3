from __future__ import print_function
from builtins import range
import sys


sys.path.insert(1,"../../")
import h2o
from h2o.job import H2OJob
from h2o.utils.progressbar import ProgressBar, ProgressBarWidget, PBWBar, PBWString, RenderResult
from tests import pyunit_utils as pu

job_request_counter = 0


class MyWidget(ProgressBarWidget):

    out = pu.StringIO()

    def render(self, progress, width=None, status=None):
        if status != 'init':
            self.out.write("%s\n" % progress)
        if status is not None:
            self.out.write("%s\n" % status)
        return RenderResult()


class MyPWBar(PBWBar):

    def set_encoding(self, encoding):
        self._bar_ends = ("", "")
        self._bar_symbols = ">"


class MyWidgetFactory(object):

    def __get__(self, job, owner=None):
        return [PBWString("my widget for %s -> " % job._job_type), MyPWBar(), MyWidget()]


def test_job_with_custom_widget():
    old = H2OJob.__PROGRESS_WIDGETS__
    try:
        H2OJob.__PROGRESS_WIDGETS__ = MyWidgetFactory()
        ProgressBar.MIN_PROGRESS_CHECK_INTERVAL = 0.01
        h2o.import_file(path=pu.locate("smalldata/prostate/prostate.uuid.csv.zip"))
        lines = MyWidget.out.getvalue().splitlines()
        print(lines)
        assert len(lines) > 2
        assert lines[0] == "init"
        assert float(lines[1]) < 1
        assert lines[-1] == "done"
        assert float(lines[-2]) == 1.0
    finally:
        H2OJob.__PROGRESS_WIDGETS__ = old


pu.run_tests([test_job_with_custom_widget])
