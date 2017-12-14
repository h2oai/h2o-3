def call(final String result, emailBody) {
    def FAILURE_RECIPIENTS = 'michalk@h2o.ai'
    def ALWAYS_RECIPIENTS = 'michalr@h2o.ai'

    def recipients = ALWAYS_RECIPIENTS
    if (result.toLowerCase() != 'success') {
        recipients = "${ALWAYS_RECIPIENTS},${FAILURE_RECIPIENTS}"
    }

    emailext (
        subject: "${env.JOB_NAME.split('/')[0]}: ${result}",
        body: emailBody,
        to: recipients
    )
}

return this