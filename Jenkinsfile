pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        skipDefaultCheckout()
    }

    environment {
        BACKEND_IMAGE  = 'ghcr.io/stock-inc/audio-saas-backend'
        KEYCLOAK_IMAGE = 'ghcr.io/stock-inc/audio-saas-keycloak'
        IMAGE_TAG      = 'dev'
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
            docker build \
                --target build \
                -t audio-saas-backend-tests:dev \
                .
        '''
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
                sh '''
                    docker build \
                        -t "$BACKEND_IMAGE:$IMAGE_TAG" \
                        .
                '''
            }
        }

        stage('Build Keycloak') {
            when {
                anyOf {
                    branch 'develop'
                    branch 'main'
                }
            }

            steps {
                sh '''
                    docker build \
                        -f Dockerfile.keycloak \
                        -t "$KEYCLOAK_IMAGE:$IMAGE_TAG" \
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

                        docker push "$BACKEND_IMAGE:$IMAGE_TAG"
                        docker push "$KEYCLOAK_IMAGE:$IMAGE_TAG"
                    '''
                }
            }
        }
    }
}
