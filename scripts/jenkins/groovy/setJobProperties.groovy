def call() {
  properties(
    [
        parameters(
            [
              choice(name: 'rVersion', description: 'R version used to compile H2O-3', choices: '3.4.1\n3.3.3'),
              choice(name: 'pythonVersion', description: 'Python version used to compile H2O-3', choices: "3.5\n3.6\n2.7"),
            ]
        ),
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25'))
    ]
  )
}

return this
