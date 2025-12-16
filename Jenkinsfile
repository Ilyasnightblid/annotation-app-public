pipeline {
    agent any

    environment {
        // Adresse du registre Nexus accessible par le daemon Docker (ici localhost:8082 si mappé sur le host)
        DOCKER_REGISTRY = 'localhost:8082'
        IMAGE_NAME = 'annotation-app'
        IMAGE_TAG = 'latest'
    }

    stages {
        stage('Checkout') {
            steps {
                // Récupère le code depuis le SCM configuré dans le Job Jenkins
                checkout scm
                // Rendre le wrapper Maven exécutable pour l'environnement Linux
                sh 'chmod +x mvnw'
            }
        }

        stage('Test') {
            steps {
                // Exécute les tests unitaires (utilise H2 via le profil test)
                // Assurez-vous que Maven est configuré dans le PATH ou via 'tools'
                // Exécute les tests unitaires via le wrapper Maven
                sh './mvnw test'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                // 'SonarQube' doit correspondre au nom du serveur configuré dans Jenkins
                // Cette étape injecte les variables de connexion SonarQube
                withSonarQubeEnv('SonarQube') {
                    sh './mvnw sonar:sonar -Dsonar.projectKey=annotation-app'
                }
            }
        }

        stage('Build Docker') {
            steps {
                // Construction de l'image en utilisant le Dockerfile à la racine
                sh "docker build -t ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} ."
            }
        }

        stage('Push to Nexus') {
            steps {
                // Utilisation des credentials stockés dans Jenkins (ID: nexus-creds)
                withCredentials([usernamePassword(credentialsId: 'nexus-creds', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
                    // Connexion au registre privé
                    sh "docker login -u ${NEXUS_USER} -p ${NEXUS_PASS} ${DOCKER_REGISTRY}"
                    // Envoi de l'image
                    sh "docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Deploy to K8s') {
            steps {
                // Application des manifestes Kubernetes
                // Le cluster doit être accessible (kubeconfig présent ou ServiceAccount)
                sh 'kubectl apply -f k8s/'
            }
        }
    }
}
