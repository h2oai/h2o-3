def call(final String result, emailContent) {
    def FAILURE_RECIPIENTS = 'michalk@h2o.ai'
    def ALWAYS_RECIPIENTS = 'michalr@h2o.ai'

    def recipients = ALWAYS_RECIPIENTS
    if (result.toLowerCase() != 'success') {
        recipients = "${ALWAYS_RECIPIENTS},${FAILURE_RECIPIENTS}"
    }
    def subject = "${env.JOB_NAME.split('/')[0]}: ${result}"

    emailext (
        subject: subject,
        body: emailContent(result),
        to: recipients
    )
}

return this