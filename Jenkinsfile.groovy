@Library('semantic_releasing') _

podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true)
],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {
    node('mypod') {
        properties([
                buildDiscarder(
                        logRotator(artifactDaysToKeepStr: '',
                                artifactNumToKeepStr: '',
                                daysToKeepStr: '',
                                numToKeepStr: '30'
                        )
                ),
                pipelineTriggers([cron('0 1 * * *')])
        ])

        stage('create backup from test') {
            def now = new Date()
            currentBuild.displayName = now.format("yyyyMMdd-HH:mm:ss")

            def kc = 'kubectl --namespace test'
            def containerPath = '/opt/jboss/keycloak/standalone/data'
            def containerName = 'keycloak'
            def podLabel = 'app=keycloak'
            def repositoryUrl = 'bitbucket.org/khinkali/keycloak_backup_test'
            container('kubectl') {
                backup(podLabel, containerName, containerPath, repositoryUrl, kc)
            }
        }

    }
}