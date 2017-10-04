@Library('h2o3-shared-lib') _

import ai.h2o3.ci.Globals

def SIZE_MEDIUM_LARGE = 'medium-large'
def SIZE_SMALL = 'small'

def SMOKE_JOBS = [
  [
    stageName: 'Py2.7 Smoke', target: 'test-py-smoke', pythonVersion: '2.7',
    timeoutValue: 8, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'R3.4 Smoke', target: 'test-r-smoke', rVersion: '3.4.1',
    timeoutValue: 8, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'r', pipInstall: false,
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ]
]

def SMALL_JOBS = [
  [
    stageName: 'Py2.7 Booklets', target: 'test-py-booklets', pythonVersion: '2.7',
    timeoutValue: 40, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'Py2.7 Demos', target: 'test-py-demos', pythonVersion: '2.7',
    timeoutValue: 15, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'Py2.7 Init', target: 'test-py-init', pythonVersion: '2.7',
    timeoutValue: 5, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'Py2.7 Small', target: 'test-pyunit-small', pythonVersion: '2.7',
    timeoutValue: 45, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'Py3.5 Small', target: 'test-pyunit-small', pythonVersion: '3.5',
    timeoutValue: 45, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'Py3.6 Small', target: 'test-pyunit-small', pythonVersion: '3.6',
    timeoutValue: 45, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt'
  ],
  [
    stageName: 'R3.4 Small', target: 'test-r-small', rVersion: '3.4.1',
    timeoutValue: 90, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'r', pipInstall: false,
    filesToArchive: '**/results/*, **/*tmp_model*, **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt'
  ],
  [
    stageName: 'R3.4 Small Client Mode', target: 'test-r-small-client-mode', rVersion: '3.4.1',
    timeoutValue: 2, timeoutUnit: 'HOURS', numToKeep: '25', hasJUnit: true, lang: 'r', pipInstall: false,
    filesToArchive: '**/results/*, **/*tmp_model*, **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt'
  ],
  [
    stageName: 'R3.4 Datatable', target: 'test-r-datatable', rVersion: '3.4.1',
    timeoutValue: 20, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'r', pipInstall: false,
    filesToArchive: '**/results/*, **/*tmp_model*, **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt'
  ]
]

def MEDIUM_LARGE_JOBS = [
  [
    stageName: 'Py2.7 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '2.7',
    timeoutValue: 90, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt, h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv'
  ],
  [
    stageName: 'Py3.5 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.5',
    timeoutValue: 90, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt, h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv'
  ],
  [
    stageName: 'Py3.6 Medium-large', target: 'test-pyunit-medium-large', pythonVersion: '3.6',
    timeoutValue: 90, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'py',
    filesToArchive: '**/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/*ipynb.out.txt, h2o-py/tests/testdir_dynamic_tests/testdir_algos/glm/Rsandbox*/*.csv'
  ],
  [
    stageName: 'R3.4 Medium-large', target: 'test-r-medium-large', rVersion: '3.4.1',
    timeoutValue: 70, timeoutUnit: 'MINUTES', numToKeep: '25', hasJUnit: true, lang: 'r', pipInstall: false,
    filesToArchive: '**/results/*, **/*tmp_model*, **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt'
  ]
]

properties(
  [
      parameters(
          [
            choice(name: 'rVersion', description: 'R version used to compile H2O-3', choices: '3.4.1\n3.3.3\n3.2.5\n3.1.3\n3.0.3'),
            choice(name: 'pythonVersion', description: 'Python version used to compile H2O-3', choices: "3.5\n3.6\n3.7"),
            string(name: 'customMakefileURL', defaultValue: '', description: 'Makefile used to build and test H2O-3. Leave empty to use docker/Makefile.jenkins from master'),
            choice(name: 'testsSize', description:'Choose small for smoke and small tests only. Medium-large runs medium-large test as well.', choices: "${SIZE_SMALL}\n${SIZE_MEDIUM_LARGE}")
          ]
      ),
      buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '25', numToKeepStr: ''))
  ]
)

def customEnv = [
  "JAVA_VERSION=8",
  "BUILD_HADOOP=false",
  "GRADLE_USER_HOME=../gradle-user-home",
  "GRADLE_OPTS=-Dorg.gradle.daemon=false"
]

if (env.CHANGE_BRANCH != null && env.CHANGE_BRANCH != '') {
  cancelPreviousBuilds()
}

node (getRootNodeLabel()) {
  node (Globals.DEFAULT_NODE_LABEL) {
    withDockerEnvironment(customEnv, 4, 'HOURS') {

      stage ('Checkout Sources') {
        currentBuild.displayName = "${params.testsSize} #${currentBuild.id}"
        checkoutH2O()
        setJobDescription()
      }

      stage ('Build H2O-3') {
        withEnv(["PYTHON_VERSION=${params.pythonVersion}", "R_VERSION=${params.rVersion}"]) {
          try {
            buildTarget {
              target = 'build-h2o-3'
              hasJUnit = false
              archiveFiles = false
            }
            buildTarget {
              target = 'test-package-py'
              hasJUnit = false
              archiveFiles = false
            }
            buildTarget {
              target = 'test-package-r'
              hasJUnit = false
              archiveFiles = false
            }
          } finally {
            archiveArtifacts """
              h2o-3/docker/Makefile.jenkins,
              h2o-3/h2o-py/dist/*.whl,
              h2o-3/build/h2o.jar,
              h2o-3/h2o-3/src/contrib/h2o_*.tar.gz,
              h2o-3/h2o-assemblies/genmodel/build/libs/genmodel.jar,
              h2o-3/test-package-*.zip,
              **/*.log, **/out.*, **/*py.out.txt, **/java*out.txt, **/tests.txt, **/status.*
            """
          }
        }
      }
    }
  }
  executeInParallel(SMOKE_JOBS, customEnv, params.customMakefileURL)

  def jobs = SMALL_JOBS
  if (params.testsSize.toLowerCase() == SIZE_MEDIUM_LARGE.toLowerCase()) {
    jobs += MEDIUM_LARGE_JOBS
  }
  executeInParallel(jobs, customEnv, params.customMakefileURL)
}

def executeInParallel(jobs, customEnv, customMakefileURL) {
  parallel(jobs.collectEntries { c ->
    [
      c['stageName'], {
        withEnv(customEnv) {
          defaultTestPipeline {
            stageName = c['stageName']
            target = c['target']
            pythonVersion = c['pythonVersion']
            rVersion = c['rVersion']
            timeoutValue = c['timeoutValue']
            timeoutUnit = c['timeoutUnit']
            numToKeep = c['numToKeep']
            hasJUnit = c['hasJUnit']
            filesToArchive = c['filesToArchive']
            pipInstall = c['pipInstall']
            lang = c['lang']
          }
        }
      }
    ]
  })
}

def setJobDescription() {
  def MAX_MESSAGE_LENGTH = 30
  def gitSHA = sh(returnStdout: true, script: 'cd h2o-3 && git rev-parse HEAD').trim()
  def gitMessage = sh(returnStdout: true, script: 'cd h2o-3 && git log -1 --pretty=%B').trim()
  if (gitMessage.length() >= MAX_MESSAGE_LENGTH) {
    gitMessage = gitMessage.substring(0, MAX_MESSAGE_LENGTH) + '...'
  }
  def gitAuthor = sh(returnStdout: true, script: 'cd h2o-3 && git log -1 --format=\'%an <%ae>\'').trim()

  currentBuild.description = "MSG: ${gitMessage}\nAUT: ${gitAuthor}\nSHA: ${gitSHA}"
}
