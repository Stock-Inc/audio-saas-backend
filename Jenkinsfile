
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        IMAGE = 'ghcr.io/stock-inc/audio-saas-backend'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Check Docker') {
            steps {
                sh 'docker version'
            }
        }

        stage('Build Image') {
            steps {
                sh '''
                    docker build \
                      --label "org.opencontainers.image.source=https://github.com/Stock-Inc/audio-saas-backend" \
                      -t "$IMAGE:dev" \
                      -t "$IMAGE:build-$BUILD_NUMBER" \
                      .
                '''
            }
        }

        stage('Push Image') {
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

                        printf '%s' "$GH_TOKEN" |
                          docker login ghcr.io \
                            -u "$GH_USER" \
                            --password-stdin

                        docker push "$IMAGE:dev"
                        docker push "$IMAGE:build-$BUILD_NUMBER"
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker logout ghcr.io || true'
        }
    }
}
