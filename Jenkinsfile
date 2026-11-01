
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
                echo "Branch: ${env.BRANCH_NAME}"
            }
        }

        stage('Tests') {
            steps {
                sh '''
                    docker build --progress=plain \
                      --target build \
                      -t "audio-saas-test:$(git rev-parse --short=12 HEAD)" \
                      .
                '''
            }
        }

        stage('Build Docker Image') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }

            steps {
                script {
                    env.IMAGE_TAG = env.BRANCH_NAME == 'develop'
                        ? 'dev'
                        : 'latest'
                }

                sh '''
                    docker build \
                      -t "$IMAGE:$IMAGE_TAG" \
                      .
                '''
            }
        }

        stage('Push to GHCR') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }

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

                        docker push "$IMAGE:$IMAGE_TAG"
                        docker logout ghcr.io
                    '''
                }
            }
        }
    }
}
