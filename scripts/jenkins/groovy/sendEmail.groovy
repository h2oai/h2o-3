def call(emailContent) {
    def FAILURE_RECIPIENTS = 'michalk@h2o.ai'
    def ALWAYS_RECIPIENTS = 'michalr@h2o.ai'

    def recipients = ALWAYS_RECIPIENTS
    def result = currentBuild.currentResult
//    if (result != 'SUCCESS') {
//        recipients = "${ALWAYS_RECIPIENTS},${FAILURE_RECIPIENTS}"
//    }
    def subject = "${env.JOB_NAME.split('/')[0]}: ${result}"

    emailext (
        subject: subject,
        body: emailContent(),
        to: recipients
    )
}

return this