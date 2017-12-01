import groovy.json.JsonSlurper

def call(scm, final String credentialsId, final String context, closure) {

  def STATE_NAME_PENDING = 'pending'
  def STATE_NAME_SUCCESS = 'success'
  def STATE_NAME_FAILURE = 'failure'
  def STATE_NAME_ERROR = 'error'

  def STATES = [
    [state: STATE_NAME_PENDING, description: 'The build is pending'],
    [state: STATE_NAME_SUCCESS, description: 'This commit looks good'],
    [state: STATE_NAME_FAILURE, description: 'An error or test failure occurred'],
    [state: STATE_NAME_ERROR, description: 'This commit cannot be built']
  ]

  def PENDING_STATE = STATES.find{it['state'] == STATE_NAME_PENDING}
  def SUCCESS_STATE = STATES.find{it['state'] == STATE_NAME_SUCCESS}
  def FAILURE_STATE = STATES.find{it['state'] == STATE_NAME_FAILURE}

  def currentState = FAILURE_STATE
  def currentHeadSHA = getHeadSHA(scm, credentialsId)
  try {
    setCommitState(scm, currentHeadSHA, credentialsId, PENDING_STATE['state'], PENDING_STATE['description'], context)
    closure()
    currentState = SUCCESS_STATE
  } finally {
    setCommitState(scm, currentHeadSHA, credentialsId, currentState['state'], currentState['description'], context)
  }
}

def getHeadSHA(scm, credentialsId) {
  withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
    def relevantHEADRef = scm.getUserRemoteConfigs().get(0).getRefspec().split(':')[0].replaceAll('\\+', '')
    def commitSHARequest = ['curl', '-XGET', '-v', '-H', "Authorization: token ${GITHUB_TOKEN}", "https://api.github.com/repos/h2oai/${getRepoName(scm)}/git/${relevantHEADRef}"]
    echo "${commitSHARequest}"
    def commitSHAresponse = commitSHARequest.execute().text
    echo commitSHAresponse
    return new JsonSlurper().parseText(commitSHAresponse).object.sha
  }
}

def getRepoName(scm) {
  return scm.getUserRemoteConfigs().get(0).getUrl().tokenize('/')[3].split("\\.")[0]
}

def setCommitState(scm, final String commitSHA, final String credentialsId, final String state, final String description, final String context) {
  withCredentials([string(credentialsId: credentialsId, variable: 'GITHUB_TOKEN')]) {
    def params = """
      {
        "state": "${state}",
        "target_url": "${BUILD_URL}",
        "description": "${description}",
        "context": "${context}"
      }
    """
    def url = "https://api.github.com/repos/h2oai/${getRepoName(scm)}/statuses/${commitSHA}"
    def commitStatusRequest = ['curl', '-XPOST', '-v', '-H', "Authorization: token ${GITHUB_TOKEN}", url, '-d', params]
    echo "${commitStatusRequest}"
    def commitStatusResponse = commitStatusRequest.execute().text
    echo commitStatusResponse
  }
}

return this
