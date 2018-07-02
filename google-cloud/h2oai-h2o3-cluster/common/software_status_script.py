# Copyright 2016 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""A DM template that provides a startup script to check software status.

  See software_status.py for an example.

  Input properties to this template:
  - checkScript: optional. A small script that checks solution-specific status,
      such as verifying the response code of a local URL. The function should
      return 0 if the solution is ready, 1 on a retryable failure, and anything
      greater than 1 if an unrecoverable failure is detected. Any message
      printed along with a failure response will be relayed as the failure
      variable's value.
"""
import jinja2
import yaml


def _CheckScript(context):
  """Returns the checkScript property or a successful no-op if unspecified."""
  return context.properties.get('checkScript', 'return 0')


def _InitScript(context):
  """Returns the initScript property or a successful no-op if unspecified."""
  return context.properties.get('initScript', 'return 0')


def _StartupScript(context):
  """Generates and returns a startup script."""
  params = {
      'check_script': _CheckScript(context),
      'init_script': _InitScript(context),
  }
  template = context.imports['software_status.sh.tmpl']
  return jinja2.Environment().from_string(template).render(**params)


def GenerateConfig(context):
  """Entry function to generate the DM config."""
  content = {
      'resources': [],
      'outputs': [
          {
              'name': 'startup-script',
              'value': _StartupScript(context)
          },
      ]
  }
  return yaml.safe_dump(content)
