#!/usr/bin/env python
from IPython.terminal.ipapp import launch_new_instance
import warnings
import sys

import jpy

dp = jpy.get_type("main.hex.deepwater.DeepWater")

warnings.filterwarnings("ignore", module = "zmq.*")
sys.argv.append("notebook")
sys.argv.append("--IPKernelApp.pylab='inline'")
#sys.argv.append("--NotebookApp.ip=" + gethostname())
sys.argv.append("--NotebookApp.open_browser=False")
#sys.argv.append("--NotebookApp.password=" + passwd())
launch_new_instance()