"""
A job is an object with states: CREATED, RUNNING, DONE, FAILED, CANCELLED
A job can be polled for completion and reports the progress so far if it is still RUNNING.
"""

from connection import H2OConnection
import time
import sys


class H2OJob:
  """
  A class representing an H2O Job.
  """
  __PROGRESS_BAR__ = True  # display & update progress bar while polling
  def __init__(self, jobs, job_type):
    if "jobs" in jobs:  job = jobs["jobs"][0]
    elif "job" in jobs: job = jobs["job"]
    else:               job = jobs

    self.jobs = jobs
    self.job = job
    self.status = job["status"]
    self.job_key = job['key']['name']
    self.dest_key = job['dest']['name']
    self.progress = 0
    self._100_percent = False
    self._progress_bar_width = 50
    self._job_type = job_type
    self.exception = job['exception'] if 'exception' in job else None

  def poll(self):
    sleep = 0.1
    running = True
    if H2OJob.__PROGRESS_BAR__: print  # create a new line for distinguished progress bar
    while running:
      self._update_progress()
      time.sleep(sleep)
      if sleep < 1.0: sleep += 0.1
      self._refresh_job_view()
      running = self._is_running()
    self._update_progress()
    if H2OJob.__PROGRESS_BAR__: print

    # check if failed... and politely print relevant message
    if self.status == "CANCELLED":
      raise EnvironmentError("Job with key {} was cancelled by the user.".format(self.job_key))
    if self.status == "FAILED":
      raise EnvironmentError("Job with key {} failed with an exception: {}".format(self.job_key, self.exception))
    return self

  def poll_once(self):
    print
    self._refresh_job_view()
    self._update_progress()
    print

    # check if failed... and politely print relevant message
    if self.status == "CANCELLED": raise EnvironmentError("Job with key {} was cancelled by the user.".format(self.job_key))
    if self.status == "FAILED":    raise EnvironmentError("Job with key {} failed with an exception: {}".format(self.job_key, self.exception))
    return self

  def _refresh_job_view(self):
      jobs = H2OConnection.get_json(url_suffix="Jobs/" + self.job_key)
      self.job = jobs["jobs"][0] if "jobs" in jobs else jobs["job"][0]
      self.status = self.job["status"]
      self.progress = self.job["progress"]
      self.exception = self.job["exception"]

  def _is_running(self):
      return self.status == "RUNNING" or self.status == "CREATED"

  def _update_progress(self):
    if self._100_percent:  return
    progress = min(self.progress, 1)
    if progress == 1:
      self._100_percent = True

    if H2OJob.__PROGRESS_BAR__:  # or self._100_percent:
      p = int(self._progress_bar_width * progress)
      sys.stdout.write("\r" + self._job_type + " Progress: [%s%s] %02d%%" %
                       ("#" * p, " " * (self._progress_bar_width - p), 100 * progress))
      sys.stdout.flush()
