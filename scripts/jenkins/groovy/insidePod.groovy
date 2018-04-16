def call(final customEnv, final timeoutValue, final mem, final cpu, final image, final Closure body) {
    final def defaultPodContainer = 'h2o-3-container'
    final def label = "h2o-3-pod-${mem}GB-${cpu}CPU-${UUID.randomUUID().toString()}"
    def podSpec = """
apiVersion: v1
kind: Pod
metadata:
  name: ${label}
spec:
  securityContext:
    runAsUser: 2117
    fsGroup: 2117
  containers:
  - args:
    - cat
    command:
    - /bin/sh
    - -c
    image: ${image}
    imagePullPolicy: Always
    name: ${defaultPodContainer}
    resources:
      limits:
        cpu: ${cpu}
        memory: ${mem}Gi
      requests:
        cpu: ${cpu}
        memory: ${mem}Gi
    securityContext:
      privileged: false
    terminationMessagePath: /dev/termination-log
    terminationMessagePolicy: File
    tty: true
    volumeMounts:
    - mountPath: /home/0xdiag
      name: datasets
  imagePullSecrets:
  - name: regcred
  volumes:
  - hostPath:
      path: /home/0xdiag
      type: ""
    name: datasets
"""
    podTemplate(label: label, name: label, yaml: podSpec) {
        node(label) {
            container(defaultPodContainer) {
                withEnv(customEnv) {
                    timeout(time: timeoutValue, unit: 'MINUTES') {
                        withCredentials([file(credentialsId: 'c096a055-bb45-4dac-ba5e-10e6e470f37e', variable: 'JUNIT_CORE_SITE_PATH'), [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AWS S3 Credentials', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                            sh """
                                id
                                printenv | sort
                            """
                            body()
                        }
                    }
                }
            }
        }
    }
}

return this
