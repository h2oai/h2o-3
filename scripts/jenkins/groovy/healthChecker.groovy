def call(final insideDockerScript) {
    return new HealthChecker(insideDockerScript)
}

class HealthChecker {

    private static final int ROOT_THRESHOLD = SPACE_THRESHOLD
    private static final int HOME_THRESHOLD = SPACE_THRESHOLD

    private static final int SPACE_THRESHOLD = 87


    private List<HealthProblem> healthProblems = []
    private insideDockerScript
    
    private HealthChecker(final insideDockerScript) {
        this.insideDockerScript = insideDockerScript
    }

    boolean checkHealth(final context, final String node, final String dockerImage, final String dockerRegistry, final buildConfig) {
        boolean healthy = true
        String cause = ''
        final String checkRootSpaceCmd = createSpaceCheckCmd(ROOT_THRESHOLD, '/')
        final int rootSpaceCheckResult = context.sh(script: checkRootSpaceCmd, returnStatus: true)
        if (rootSpaceCheckResult != 0) {
            cause = "Free space check of / failed"
            healthy = false
        }

        final String checkHomeSpaceCmd = createSpaceCheckCmd(HOME_THRESHOLD, '${HOME}')
        final int homeSpaceCheckResult = context.sh(script: checkHomeSpaceCmd, returnStatus: true)
        if (homeSpaceCheckResult != 0) {
            cause = "Free space check of \${HOME} failed"
            healthy = false
        }

        try {
            insideDockerScript.call([], dockerImage, dockerRegistry, buildConfig, 90, 'SECONDS') {
                context.echo 'Docker health check passed'
            }
        } catch (Exception e) {
            context.echo "${e}"
            context.echo "${e}"
            cause = "Docker health check failed"
            healthy = false
        }

        if (!healthy) {
            final boolean nodeReported = healthProblems.find {it.getNode() == node} != null
            if (!nodeReported) {
                healthProblems += new HealthProblem(cause, node)
            }
        }
        return healthy
    }

    String getHealthyNodesLabel(final String defaultNodesLabel) {
        String result = defaultNodesLabel
        for (HealthProblem healthProblem : healthProblems) {
            result += " && !${healthProblem.getNode()}"
        }
        return result
    }

    List<HealthProblem> getHealthProblems() {
        return healthProblems
    }

    String toEmail(final context, final pipelineContext) {
        final def benchmarksSummary = pipelineContext.getBuildSummary().newInstance(false)

        String rowsHTML = ''
        for (HealthProblem healthProblem in healthProblems) {
            rowsHTML += """
                <tr>
                    <td style="${benchmarksSummary.TD_STYLE}">${healthProblem.getNode()}</td>
                    <td style="${benchmarksSummary.TD_STYLE}">${healthProblem.getCause()}</td>
                </tr>
            """
        }
        final String warningsTable = """
            <table style="${benchmarksSummary.TABLE_STYLE}">
                <thead>
                    <tr>
                        <th style=\"${benchmarksSummary.TH_STYLE}\">Node</th>
                        <th style=\"${benchmarksSummary.TH_STYLE}\">Cause</th>
                    </tr>
                </thead>
                <tbody>
                    ${rowsHTML}
                </tbody>
            </table>
        """
        benchmarksSummary.addSection(this, 'warnings', 'Unhealthy nodes', warningsTable)
        return benchmarksSummary.getSummaryHTML(context)
    }

    private String createSpaceCheckCmd(final int threshold, final String path) {
        return """
            used=\$(df -h --output=pcent ${path} | tail -1 | tr -d % | tr -d [:space:])
            if [ \$used -gt ${threshold} ]; then 
                echo "Usage limit for ${path} reached -> used \${used}%, limit is ${threshold}%";
                exit 1 
            else 
                echo "${path} space check passed"
            fi
        """
    }

    static class HealthProblem {
        private final String cause
        private final String node

        HealthProblem(String cause, String node) {
            this.cause = cause
            this.node = node
        }

        String getCause() {
            return cause
        }

        String getNode() {
            return node
        }
    }

}

return this
