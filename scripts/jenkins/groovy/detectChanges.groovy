def call() {
  sh 'cd h2o-3 && git fetch --no-tags --progress https://github.com/h2oai/h2o-3 +refs/heads/master:refs/remotes/origin/master'
  def mergeBaseSHA = sh(script: "cd h2o-3 && git merge-base HEAD origin/master", returnStdout: true).trim()
  def changes = sh(script: "cd h2o-3 && git diff --name-only ${mergeBaseSHA}", returnStdout: true).trim().tokenize('\n')

  def changesMap = [
    py: false,
    r: false,
    js: false,
    java: false
  ]

  for (change in changes) {
    if (change.startsWith('h2o-py/') || change == 'h2o-bindings/bin/gen_python.py') {
      changesMap['py'] = true
    } else if (change.startsWith('h2o-r/') ||  change == 'h2o-bindings/bin/gen_R.py') {
      changesMap['r'] = true
    } else if (change.endsWith('.md')) {
      // no need to run any tests if only .md files are changed
    } else {
      markAllForRun(changesMap)
    }
  }

  return changesMap
}

def detectPythonChanges() {

}

def markAllForRun(map) {
  map.each { k,v ->
    map[k] = true
  }
}

return this
