
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        BACKEND_IMAGE = 'ghcr.io/stock-inc/audio-saas-backend'
        KEYCLOAK_IMAGE = 'ghcr.io/stock-inc/audio-saas-keycloak'
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
                sh 'mvn -B clean test'
            }
        }

        stage('Build Backend') {
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
                      -t "$BACKEND_IMAGE:$IMAGE_TAG" \
                      .
                '''
            }
        }

        stage('Build Keycloak') {
            when {
                branch 'develop'
            }

            steps {
                sh '''
                    docker build \
                      -f Dockerfile.keycloak \
                      -t "$KEYCLOAK_IMAGE:dev" \
                      .
                '''
            }
        }

        stage('Push Images to GHCR') {
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
                        set -eu
                        set +x

                        printf '%s' "$GH_TOKEN" | \
                          docker login ghcr.io \
                            -u "$GH_USER" \
                            --password-stdin

                        trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT

                        echo "Pushing backend..."
                        docker push "$BACKEND_IMAGE:$IMAGE_TAG"

                        if [ "$BRANCH_NAME" = "develop" ]; then
                            echo "Pushing Keycloak..."
                            docker push "$KEYCLOAK_IMAGE:dev"
                        fi
                    '''
                }
            }
        }
    }
}
