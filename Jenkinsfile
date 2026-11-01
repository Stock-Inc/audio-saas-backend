pipeline {
    agent any

    environment {
        IMAGE = 'ghcr.io/stock-inc/audio-saas-backend'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B test'
            }
        }

        stage('Build and Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'ghcr-credentials',
                        usernameVariable: 'GH_USER',
                        passwordVariable: 'GH_TOKEN'
                    )
                ]) {
                    sh '''
                        set +x
                        echo "$GH_TOKEN" |
                          docker login ghcr.io \
                            -u "$GH_USER" --password-stdin

                        docker build -t "$IMAGE:dev" .
                        docker push "$IMAGE:dev"

                        docker logout ghcr.io
                    '''
                }
            }
        }
    }
}
