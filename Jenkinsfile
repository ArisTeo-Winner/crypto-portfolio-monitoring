pipeline {
	agent any
	tools {
		maven 'Maven 3.9.9'
		jdk 'jdk-21'
	}
	stages {
		
		stage('Checkout') {
			steps {
				git url: 'https://github.com/ArisTeo-Winner/crypto-portfolio-monitoring.git', credentialsId: 'github-creds'
			}
		}
		
		stage('Build'){
			steps {
				bat 'mvn clean package -DskipTests'
			}
		}
	}
}