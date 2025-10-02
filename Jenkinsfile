import groovy.json.JsonOutput


pipeline {
    agent any
    tools {
        maven 'Default Maven'
        jdk 'Zulu17'
        git 'Default Git'
        nodejs 'defaultNodeJS'
    }
    environment {
        STAGE_NAME = ''
        
        // Docker 설정
        DOCKER_USER = 'team2room'
        IMAGE_NAME = 'keywi'
        GIT_COMMIT_SHORT = ''
        DOCKER_TAG = ''
        
        // 깃 정보
        COMMIT_MSG = ''
        COMMIT_HASH = ''
        AUTHOR = ''
        BRANCH_NAME = ''
        SERVICE_PATH = ''
        SERVICES = ''
        ERROR_MSG = "false"
        
        // 서버 정보
        SERVER_USER = 'keywi'
        PROD_SERVER = 'keywi.poloceleste.site'
        GITHUB_BASE_URL = 'https://github.com/team2room/KeyWi'
        DISCORD_WEBHOOK_URL = credentials('discord_webhooks')
        SERVER_WORK_DIR = 'FE/keywi'
    }
    stages {
        stage('Checkout and Update') {
            steps {
                script {
                    // 변수 안전 확인
                    def refVar = ""
                    def repositoryVar = ""
                    def pusherVar = ""
                    def commitMessageVar = ""
                    
                    try {
                        refVar = ref ?: ""
                    } catch(Exception e) {
                        echo "ref 변수 없음: ${e.message}"
                        refVar = ""
                    }
                    
                    try {
                        repositoryVar = repository ?: ""
                    } catch(Exception e) {
                        echo "repository 변수 없음: ${e.message}"
                        repositoryVar = ""
                    }
                    
                    try {
                        pusherVar = pusher ?: ""
                    } catch(Exception e) {
                        echo "pusher 변수 없음: ${e.message}"
                        pusherVar = ""
                    }
                    
                    try {
                        commitMessageVar = commit_message ?: ""
                    } catch(Exception e) {
                        echo "commit_message 변수 없음: ${e.message}"
                        commitMessageVar = ""
                    }
                    
                    echo "- ref: ${refVar}"
                    echo "- repository: ${repositoryVar}"
                    echo "- pusher: ${pusherVar}"
                    echo "- commit_message: ${commitMessageVar}"
                    
                    // Generic Webhook Trigger에서 설정된 변수들 사용
                    def branchName = (refVar ?: "refs/heads/master").replaceFirst("refs/heads/", "")
                    BRANCH_NAME = branchName
                    echo "- resolved branch: ${BRANCH_NAME}"

                    def stageName = "Checkout and Update (1/6)"
                    STAGE_NAME = stageName
                    def repoExists = fileExists('.git')
                    if (repoExists) {
                        echo "Repository exists. Updating..."
                        try {
                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: "*/${BRANCH_NAME}"]],
                                userRemoteConfigs: [[
                                    url: "${GITHUB_BASE_URL}.git",
                                    credentialsId: 'github_credentials'
                                ]],
                                extensions: [
                                    [$class: 'CleanBeforeCheckout'],
                                    [$class: 'PruneStaleBranch']
                                ]
                            ])
                            withCredentials([gitUsernamePassword(credentialsId: 'github_credentials')]) {
                                sh "git fetch --all --prune"
                                sh "git checkout -B ${BRANCH_NAME} origin/${BRANCH_NAME} --force"
                                sh "git pull origin ${BRANCH_NAME}"
                                
                                GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                                DOCKER_TAG = "${env.BUILD_NUMBER}-${GIT_COMMIT_SHORT}"
                                
                                // MSA 서비스 경로 설정
                                SERVICE_PATH = BRANCH_NAME.contains('feature/BE/') ? BRANCH_NAME.replace("feature/BE/", "") : ""
                                
                                // 서비스 목록 설정
                                if (BRANCH_NAME == "master") {
                                    SERVICES = "config,eureka,gateway,auth,product,feed,mypage,board,chat,search"
                                } else if (BRANCH_NAME == "feature/BE/gateway") {
                                    SERVICES = "eureka,gateway"
                                } else {
                                    SERVICES = SERVICE_PATH ? "${SERVICE_PATH}" : "FE only - No BE services to build"
                                }
                            
                                echo "branch: ${BRANCH_NAME}"
                                echo "services to build: ${SERVICES}"
                                echo "docker tag: ${DOCKER_TAG}"
                            }
                        } catch (Exception e) {
                            echo "Error during update: ${e.message}"
                            ERROR_MSG = "Failed to update repository"
                            error ERROR_MSG
                        }
                    } else {
                        echo "Repository does not exist. Cloning..."
                        try {
                            withCredentials([gitUsernamePassword(credentialsId: 'github_credentials')]) {
                                sh "git clone ${GITHUB_BASE_URL}.git ."

                                sh "git checkout ${BRANCH_NAME}"

                                GIT_COMMIT_SHORT = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                                DOCKER_TAG = "${env.BUILD_NUMBER}-${GIT_COMMIT_SHORT}"
                                
                                // MSA 서비스 경로 설정
                                SERVICE_PATH = BRANCH_NAME.contains('feature/BE/') ? BRANCH_NAME.replace("feature/BE/", "") : ""
                                
                                // 서비스 목록 설정
                                if (BRANCH_NAME == "master") {
                                    SERVICES = "config,eureka,gateway,auth,product,feed,mypage,board,chat,search"
                                } else if (BRANCH_NAME == "feature/BE/gateway") {
                                    SERVICES = "eureka,gateway"
                                } else {
                                    SERVICES = SERVICE_PATH ? "${SERVICE_PATH}" : "FE only - No BE services to build"
                                }
                                
                                echo "branch: ${BRANCH_NAME}"
                                echo "services to build: ${SERVICES}"
                                echo "docker tag: ${DOCKER_TAG}"
                            }
                        } catch (Exception e) {
                            echo "Error during clone: ${e.message}"
                            ERROR_MSG = "Failed to clone repository"
                            error ERROR_MSG
                        }
                    }
                    AUTHOR = sh(script: "git log -1 --pretty=format:%an", returnStdout: true).trim()
                    COMMIT_MSG = sh(script: 'git log -1 --pretty=%B | tr "\\n" " "', returnStdout: true).trim()
                    COMMIT_HASH = sh(script: "git log -1 --pretty=format:%H", returnStdout: true).trim()
                }
            }
        }
        // BE 세팅
        stage('BE Inject Config') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    def servicesList = SERVICES.split(',')
                    STAGE_NAME = "BE Inject Config (2/6)"
                    
                    servicesList.each { SERVICE ->
                        echo "Config Searching..."
                        
                        if (SERVICE == "config") {
                            echo "Injecting config for ${SERVICE}..."
                            withCredentials([
                                file(credentialsId: 'keywi_config_yml', variable: 'CONFIG_FILE')
                            ]) {
                                sh """
                                    mkdir -p BE/${SERVICE}/src/main/resources
                                    cp \$CONFIG_FILE BE/${SERVICE}/src/main/resources/application.yml
                                """
                            }
                        }
                    }
                }
            }
        }
        // fe 세팅
        stage('FE Inject Config') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[fe]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/fe')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "FE Inject Config (2/5)"
                }
                withCredentials([
                    file(credentialsId: 'keywi_react_env', variable: 'CONFIG_FILE')
                ]) {
                    sh """
                        rm ${SERVER_WORK_DIR}/.env || true
                        cp \$CONFIG_FILE ${SERVER_WORK_DIR}/.env
                    """
                }
            }
        }
        stage('BE Build') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "BE Build (3/6)"
                    def servicesList = SERVICES.split(',')
                    servicesList.each { SERVICE ->
                        echo "Building ${SERVICE}..."
                        dir("BE/${SERVICE}") {
                            try {
                                // Maven 빌드 사용
                                sh "mvn clean package -DskipTests"
                            } catch(Exception e) {
                                ERROR_MSG = e.getMessage()
                                error "Build failed for ${SERVICE}: ${ERROR_MSG}"
                            }
                        }
                    }
                }
            }
            post {
                failure {
                    cleanWs()
                    script {
                        ERROR_MSG += "\nBE Build failed"
                        error ERROR_MSG
                    }
                }
            }
        }
        // FE 빌드
        stage('FE build') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[fe]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/fe')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "FE Build (3/5)"
                }
                dir(SERVER_WORK_DIR) {
                    script{
                        try {
                            sh """#!/bin/bash -l
                                set -euo pipefail
                                node -v
                                npm -v
                                npm ci || npm install
                                npm run build
                                tar -czvf dist.tar.gz dist/
                            """
                        } catch(Exception e) {
                            ERROR_MSG = e.getMessage()
                            error ERROR_MSG
                        }
                    }
                }
            }
            post {
                failure {
                    cleanWs()
                    script {
                        ERROR_MSG += "\nFE Build failed"
                        error ERROR_MSG
                    }
                }
            }
        }
        stage('Docker Build') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "Docker Build (4/6)"
                    def servicesList = SERVICES.split(',')
                    servicesList.each { SERVICE ->
                        echo "Docker building ${SERVICE}..."
                        def jarFile = sh(script: "ls BE/${SERVICE}/target/*.jar", returnStdout: true).trim()
                        
                        try {
                            // docker.build("${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}-test", 
                            //     "--no-cache --build-arg JAR_FILE=${jarFile} -f BE/${SERVICE}/Dockerfile .")
                            
                            docker.build("${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}", 
                                "--no-cache --build-arg JAR_FILE=${jarFile} -f BE/${SERVICE}/Dockerfile .")
                            sh "docker tag ${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG} ${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:latest"
                            // sh "docker save ${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}-test | gzip > ${SERVICE}-image.tar.gz"
                        } catch(Exception e) {
                            ERROR_MSG = e.getMessage()
                            error "Docker build failed for ${SERVICE}: ${ERROR_MSG}"
                        }
                    }
                }
            }
            post {
                failure {
                    cleanWs()
                    script {
                        ERROR_MSG += "\nDocker Build failed"
                        error ERROR_MSG
                    }
                }
            }
        }
        // FE 배포
        stage('FE Deploy') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[fe]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/fe')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "FE Deploy (4/5)"
                }
                dir(SERVER_WORK_DIR) {
                    script {
                        try {
                            echo "FE deploy"
                            sshagent(['keywi-server']) {
                                def RELEASE_DIR = "/var/www/keywi_releases/${new Date().format('yyyyMMddHHmmss')}"
                                withEnv(["RELEASE_DIR=${RELEASE_DIR}"]) {
                                    sh '''#!/bin/bash -l
                                        set -euo pipefail
                                        
                                        echo "[FE Deploy] 1/4 rsync dist.tar.gz"
                                        rsync -av --progress -e 'ssh -o StrictHostKeyChecking=no' -W dist.tar.gz ${SERVER_USER}@${PROD_SERVER}:/tmp/
                                        
                                        echo "[FE Deploy] 2/4 verify size"
                                        local_size=\$(stat -c%s dist.tar.gz)
                                        remote_size=\$(ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${PROD_SERVER} "stat -c%s /tmp/dist.tar.gz")
                                        
                                        if [ "\$local_size" -ne "\$remote_size" ]; then
                                            echo "ERROR: File size mismatch (Local: \$local_size, Remote: \$remote_size)"
                                            exit 1
                                        fi
                                        
                                        echo "[FE Deploy] 3/4 remote release to $RELEASE_DIR"
                                        ssh -o StrictHostKeyChecking=no "${SERVER_USER}@${PROD_SERVER}" "bash -lc '
                                            set -euo pipefail
                                            RELEASE_DIR=\"${RELEASE_DIR}\"
                                            sudo mkdir -p \"$RELEASE_DIR\"
                                            cd /tmp
                                            tar -xzf dist.tar.gz
                                            sudo rsync -a --delete dist/ \"$RELEASE_DIR\"/
                                            sudo ln -sfn \"$RELEASE_DIR\" /var/www/keywi
                                            sudo chown -R www-data:www-data /var/www/keywi \"$RELEASE_DIR\"
                                            rm -rf dist dist.tar.gz
                                            sudo systemctl reload nginx
                                        '"
                                        
                                        echo "[FE Deploy] 4/4 cleanup local"
                                        rm -rf dist dist.tar.gz
                                    '''
                                }
                            }
                        } catch(Exception e) {
                            ERROR_MSG = e.getMessage()
                            error ERROR_MSG
                        }
                    }
                }
            }
            post {
                failure {
                    cleanWs()
                    script {
                        ERROR_MSG += "\nFE Deploy failed"
                        error ERROR_MSG
                    }
                }
            }
        }
        stage('Push to Docker Hub') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "Push to Docker Hub (5/6)"
                    def servicesList = SERVICES.split(',')
                    servicesList.each { SERVICE ->
                        echo "Pushing ${SERVICE} to Docker Hub..."
                        
                        docker.withRegistry('https://index.docker.io/v1/', 'docker_credentials') {
                                docker.image("${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}").push()
                        }
                        echo "Push ${SERVICE} Docker Image."
                    }
                }
            }
            post {
                failure {
                    cleanWs()
                    script {
                        ERROR_MSG = "Docker Push failed"
                        error ERROR_MSG
                    }
                }
            }
        }
        // fe 배포 완료
        stage('FE Deploy Complete') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[fe]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/fe')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "FE Deploy Complete (5/5)"
                }
            }
        }
        // BE 배포 하기
        stage('BE Deploy to Prod') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "Deploy to Prod (6/6)"
                    def servicesList = SERVICES.split(',')
                    servicesList.each { SERVICE ->
                        echo "Deploying ${SERVICE} to production server..."
                        def memoryl=""
                        if (SERVICE == 'search'){
                            memoryl = " --memory=2g --memory-swap=2g"
                        } else if (SERVICE == 'config'){
                            memoryl = " --memory=768m --memory-swap=768m"
                        } else if (SERVICE == 'chat' || SERVICE == 'feed'){
                            memoryl = " --memory=2.5g --memory-swap=2.5g"
                        } else if (SERVICE == 'auth'){
                            memoryl = " --memory=1.5g --memory-swap=1.5g"
                        } else {
                            memoryl = " --memory=1g --memory-swap=1g"
                        }
                        withCredentials([usernamePassword(credentialsId: 'docker_credentials', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                            sshagent(['keywi-server']) {
                                sh """#!/bin/bash -l
                                    ssh -o StrictHostKeyChecking=no ${SERVER_USER}@${PROD_SERVER} "
                                        docker pull ${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}
                                        docker stop ${SERVICE} || true
                                        docker rm ${SERVICE} || true
                                        docker run${memoryl} -d --network host --name ${SERVICE} ${DOCKER_USER}/${IMAGE_NAME}-${SERVICE}:${DOCKER_TAG}
                                    "
                                """
                            }
                        }
                    }
                    echo "Success Production deployment."
                }
            }
            post {
                failure {
                    script {
                        ERROR_MSG = "Production deployment failed"
                        error ERROR_MSG
                    }
                }
            }
        } 
        stage('BE Deploy Completed') {
            when {
                expression {
                    return (COMMIT_MSG.toLowerCase().contains('[be]') || (COMMIT_MSG.toLowerCase().contains('merge') && COMMIT_MSG.toLowerCase().contains('feature/be')))
                }
            }
            steps {
                script {
                    STAGE_NAME = "BE Deploy Completed"
                }
            }
        }
    }
    post {
        always {
            script {
                def emoji = currentBuild.currentResult == 'SUCCESS' ? '✅' : 
                        (currentBuild.currentResult == 'ABORTED' ? '⚠️' : '❌')
                
                def color = currentBuild.currentResult == 'SUCCESS' ? 0x00ff00 : 
                        (currentBuild.currentResult == 'ABORTED' ? 0xffff00 : 0xff0000)
                
                def commitHash = ""
                def commitMsg = ""
                def author = ""
                def branchName = BRANCH_NAME ?: "Unknown"
                def stageName = STAGE_NAME ?: "Unknown"
                def gitCommitShort = ""
                
                // 기본값 설정
                try {
                    commitHash = COMMIT_HASH ?: ""
                } catch(Exception e) {
                    commitHash = ""
                }
                
                try {
                    commitMsg = COMMIT_MSG ?: "Manual Build"
                } catch(Exception e) {
                    commitMsg = "Manual Build"
                }
                
                try {
                    author = AUTHOR ?: "Unknown"
                } catch(Exception e) {
                    author = "Unknown"
                }
                
                try {
                    gitCommitShort = GIT_COMMIT_SHORT ?: ""
                } catch(Exception e) {
                    gitCommitShort = ""
                }
                def services = SERVICES ?: ""
                
                def commitUrl = "${GITHUB_BASE_URL}/commit/${commitHash}"
                def buildUrl = "${env.BUILD_URL}"
                
                def discordMessage = [
                    embeds: [[
                        title: "${emoji} ${env.JOB_NAME} - #${env.BUILD_NUMBER}",
                        description: "**결과:** ${currentBuild.currentResult}\n" +
                                "**브랜치:** `${branchName}`\n" +
                                (services ? "**서비스:** ${services}\n" : "") +
                                "**커밋:** [${commitMsg}](${commitUrl}) (${gitCommitShort})\n" +
                                "**작성자:** ${author}\n" +
                                "**실행 시간:** ${currentBuild.durationString}\n" +
                                "**최종 스테이지:** ${stageName}" +
                                ((ERROR_MSG != "false") ? "\n**에러:**\n`${ERROR_MSG}`\n" : "") +
                                (currentBuild.currentResult == 'ABORTED' ? "**사용자 취소**\n" : ""),
                        color: color,
                        timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                        footer: [
                            text: "KeyWi CI/CD",
                            icon_url: "https://www.jenkins.io/images/logos/jenkins/jenkins.png"
                        ],
                        fields: [
                            [
                                name: "링크",
                                value: "[Jenkins 빌드](${buildUrl})" + (commitHash ? " | [GitHub 커밋](${commitUrl})" : ""),
                                inline: false
                            ]
                        ]
                    ]]
                ]
                
                httpRequest(
                    httpMode: 'POST',
                    contentType: 'APPLICATION_JSON',
                    requestBody: JsonOutput.toJson(discordMessage),
                    url: DISCORD_WEBHOOK_URL
                )
                echo "Discord 알림 전송 완료"
            }
            script {
                cleanWs(cleanWhenNotBuilt: false,
                    deleteDirs: true,
                    disableDeferredWipeout: true,
                    notFailBuild: true)
            }
        }
        failure {
            script {
                cleanWs(cleanWhenNotBuilt: false,
                    deleteDirs: true,
                    disableDeferredWipeout: true,
                    notFailBuild: true)
            }
        }
    }
}
