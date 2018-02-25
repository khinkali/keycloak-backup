podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'curl', image: 'khinkali/jenkinstemplate:0.0.3', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'maven', image: 'maven:3.5.2-jdk-8', command: 'cat', ttyEnabled: true)
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
                pipelineTriggers([cron('1 30 * * *')])
        ])

        stage('create backup from test') {
            def kct = 'kubectl --namespace test'
            container('kubectl') {
                def keycloakPods = sh(
                        script: "${kct} get po -l app=keycloak",
                        returnStdout: true
                ).trim()
                def podNameLine = keycloakPods.split('\n')[1]
                def startIndex = podNameLine.indexOf(' ')
                if (startIndex == -1) {
                    return
                }
                def podName = podNameLine.substring(0, startIndex)
                sh "${kct} exec ${podName} -- /opt/jboss/keycloak/bin/standalone.sh -Dkeycloak.migration.action=export -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=keycloak-export.json -Djboss.http.port=5889 -Djboss.https.port=5998 -Djboss.management.http.port=5779 &"
                sleep 20
                withCredentials([usernamePassword(credentialsId: 'bitbucket', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                    git url: "https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/khinkali/keycloak_backup"
                }
                sh 'rm keycloak-export-test.json'
                sh "${kct} cp ${podName}:/opt/jboss/keycloak-export.json ./keycloak_backup/keycloak-export-test.json"
                withCredentials([usernamePassword(credentialsId: 'bitbucket', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                    commitAndPushRepo('keycloak_backup')
                }
            }
        }
    }
}

def commitAndPushRepo(String repo) {
    sh "git -C '${repo}' add --all"
    sh "git -C '${repo}' diff --quiet && git -C '${repo}' diff --staged --quiet || git -C '${repo}' commit -am 'new_version'"
    sh "git -C '${repo}' push https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/khinkali/${repo}"
}