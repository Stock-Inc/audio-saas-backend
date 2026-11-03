
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
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
                        -t audio-saas-tests:local \
                        .
                '''
            }
        }

        stage('SonarQube Analysis') {
            when {
                branch 'main'
            }

            steps {
                withCredentials([
                        string(
                            credentialsId: 'sonarqube-token',
                            variable: 'SONAR_TOKEN'
                        )
                    ]) {
                    sh '''
                        set +x
                        set -eu

                        CID=$(docker create \
                            --network host \
                            -v sonarqube_maven_cache:/root/.m2 \
                            -e SONAR_TOKEN \
                            -w /tmp \
                            maven:3.9-eclipse-temurin-25 \
                            sh -c 'mvn -B -DskipTests package org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211:sonar -Dsonar.projectKey=audio-saas-backend -Dsonar.host.url=http://sonarqube:9000 -Dsonar.qualitygate.wait=true -Dsonar.scanner.skipJreProvisioning=true' \
                        )

                        trap 'docker rm -f "$CID" >/dev/null 2>&1 || true' EXIT

                        docker cp pom.xml "$CID:/tmp/pom.xml"
                        docker cp src "$CID:/tmp/src"
                        docker cp .git "$CID:/tmp/.git"

                        docker start "$CID" >/dev/null

                        STATUS=$(docker wait "$CID")
                        docker logs "$CID"

                        test "$STATUS" -eq 0
                    '''
                }
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
                branch 'main'
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
