def call(final String result, emailBody, recipients) {
    echo "RECIPIENTS: ${recipients}"
    echo emailBody
    emailext (
        subject: "${env.JOB_NAME.split('/')[0]}: ${result}",
        body: emailBody,
        to: recipients.join(' ')
    )
}

return this