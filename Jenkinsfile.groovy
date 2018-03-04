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
                pipelineTriggers([cron('0 1 * * *')])
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
                sleep 60
                git(
                        url: 'https://bitbucket.org/khinkali/keycloak_backup',
                        credentialsId: 'bitbucket')
                try {
                    sh 'rm keycloak-export-test.json'
                } catch (Exception e) {
                    echo 'no keycloak-export-text.json found'
                }
                sh "${kct} cp ${podName}:/opt/jboss/keycloak-export.json ./keycloak-export-test.json"
            }
            sh 'git config user.email "jenkins@khinkali.ch"'
            sh 'git config user.name "Jenkins"'
            withCredentials([usernamePassword(credentialsId: 'bitbucket', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                commitAndPushRepo()
            }
        }

        stage('create backup from production') {
            def kc = 'kubectl'
            container('kubectl') {
                def keycloakPods = sh(
                        script: "${kc} get po -l app=keycloak",
                        returnStdout: true
                ).trim()
                def podNameLine = keycloakPods.split('\n')[1]
                def startIndex = podNameLine.indexOf(' ')
                if (startIndex == -1) {
                    return
                }
                def podName = podNameLine.substring(0, startIndex)
                sh "${kc} exec ${podName} -- /opt/jboss/keycloak/bin/standalone.sh -Dkeycloak.migration.action=export -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=keycloak-export.json -Djboss.http.port=5889 -Djboss.https.port=5998 -Djboss.management.http.port=5779 &"
                sleep 60
                git(
                        url: 'https://bitbucket.org/khinkali/keycloak_backup',
                        credentialsId: 'bitbucket')
                sh 'rm keycloak-export-prod.json'
                sh "${kc} cp ${podName}:/opt/jboss/keycloak-export.json ./keycloak-export-prod.json"
            }
            sh 'git config user.email "jenkins@khinkali.ch"'
            sh 'git config user.name "Jenkins"'
            withCredentials([usernamePassword(credentialsId: 'bitbucket', passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                commitAndPushRepo()
            }
        }
    }
}

def commitAndPushRepo() {
    sh "git add --all"
    sh "git diff --quiet && git diff --staged --quiet || git commit -am 'new_version'"
    sh "git push https://${GIT_USERNAME}:${GIT_PASSWORD}@bitbucket.org/khinkali/keycloak_backup"
}