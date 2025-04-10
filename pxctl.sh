#!/bin/bash

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # 색상 리셋

# 기본 설정
NAMESPACE="pixiescale"
CLEAN=false
DEPLOY=false
BUILD_IMAGES=false
BUILD_SERVICES=()

# 도움말 표시
function show_help {
    echo -e "${BLUE}PixieScale 관리 도구${NC}"
    echo "사용법: $0 [옵션] [서비스명...]"
    echo "옵션:"
    echo "  -h, --help        도움말 표시"
    echo "  -c, --clean       환경 완전 정리 (데이터 및 쿠버네티스 리소스 삭제)"
    echo "  -b, --build       도커 이미지 빌드 (docker-compose 사용)"
    echo "                    서비스명을 지정하면 해당 서비스만 빌드 (예: -b apigateway mediaingestion)"
    echo "  -d, --deploy      전체 배포 실행"
    echo "  -a, --all         이미지 빌드 및 배포 모두 수행"
    echo ""
    echo "일반적인 사용 시나리오:"
    echo "  $0 -c             환경 완전 정리 (프로젝트 리셋)"
    echo "  $0 -d             새 배포 실행 (기존 이미지 사용)"
    echo "  $0 -b             모든 서비스 이미지 빌드"
    echo "  $0 -b apigateway  apigateway 서비스만 빌드"
    echo "  $0 -b -d          이미지 빌드 및 배포 (코드 변경 시)"
    echo "  $0 -a             이미지 빌드 및 배포 수행 (-b -d와 동일)"
}

# 인자 파싱
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -h|--help) show_help; exit 0 ;;
        -c|--clean) CLEAN=true; shift ;;
        -b|--build)
            BUILD_IMAGES=true
            shift
            # 다음 인자가 다른 옵션이 아니라면 서비스 이름으로 처리
            while [[ "$#" -gt 0 && ! "$1" =~ ^- ]]; do
                BUILD_SERVICES+=("$1")
                shift
            done
            ;;
        -d|--deploy) DEPLOY=true; shift ;;
        -a|--all) BUILD_IMAGES=true; DEPLOY=true; shift ;;
        *) echo "알 수 없는 옵션: $1"; show_help; exit 1 ;;
    esac
done

# 환경 정리 함수
function clean_environment {
    echo -e "${YELLOW}PixieScale 환경을 완전히 정리합니다...${NC}"

    # 기존 포트 포워딩 종료
    echo -e "${YELLOW}기존 포트 포워딩 종료 중...${NC}"
    pkill -f "kubectl port-forward" 2>/dev/null || true

    # 네임스페이스가 존재하는지 확인
    if kubectl get namespace $NAMESPACE &> /dev/null; then
        # 데이터 정리 (네임스페이스가 존재하는 경우에만)
        echo -e "${YELLOW}데이터 정리 중...${NC}"

        # mediaingestion 파드 찾기 및 파일 삭제
        MEDIA_POD=$(kubectl get pods -n $NAMESPACE -l app=mediaingestion -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
        if [ -n "$MEDIA_POD" ]; then
            echo -e "${YELLOW}미디어 업로드 파일 삭제 중...${NC}"
            kubectl exec -n $NAMESPACE $MEDIA_POD -- sh -c "rm -rf /app/media/uploads/* 2>/dev/null || true" &> /dev/null
        fi

        # transcodingworker 파드 찾기 및 파일 삭제
        WORKER_POD=$(kubectl get pods -n $NAMESPACE -l app=transcodingworker -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
        if [ -n "$WORKER_POD" ]; then
            echo -e "${YELLOW}트랜스코딩 임시 파일 삭제 중...${NC}"
            kubectl exec -n $NAMESPACE $WORKER_POD -- sh -c "rm -rf /app/output/* 2>/dev/null || true" &> /dev/null
        fi

        # mediastorage 파드 찾기 및 파일 삭제
        STORAGE_POD=$(kubectl get pods -n $NAMESPACE -l app=mediastorage -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
        if [ -n "$STORAGE_POD" ]; then
            echo -e "${YELLOW}미디어 스토리지 파일 삭제 중...${NC}"
            kubectl exec -n $NAMESPACE $STORAGE_POD -- sh -c "rm -rf /app/media/transcoded/* 2>/dev/null || true" &> /dev/null
        fi

        # 모든 리소스 삭제
        echo -e "${YELLOW}쿠버네티스 리소스 삭제 중...${NC}"

        # 모든 디플로이먼트 삭제
        kubectl delete deployment --all -n $NAMESPACE

        # 모든 서비스 삭제
        kubectl delete service --all -n $NAMESPACE

        # 모든 PVC 삭제
        kubectl delete pvc --all -n $NAMESPACE

        # 모든 ConfigMap 삭제
        kubectl delete configmap --all -n $NAMESPACE

        # 네임스페이스 삭제
        echo -e "${YELLOW}네임스페이스 $NAMESPACE 삭제 중...${NC}"
        kubectl delete namespace $NAMESPACE

        # 네임스페이스가 완전히 삭제될 때까지 대기
        echo -e "${YELLOW}네임스페이스 삭제 완료 대기 중...${NC}"
        while kubectl get namespace $NAMESPACE &> /dev/null; do
            echo -n "."
            sleep 1
        done
        echo ""
    else
        echo "네임스페이스 $NAMESPACE가 존재하지 않습니다. 정리할 리소스가 없습니다."
    fi

    # 도커 이미지 정리 (선택 사항)
    read -p "도커 이미지도 정리하시겠습니까? (y/n): " CLEAN_IMAGES
    if [[ "$CLEAN_IMAGES" =~ ^[Yy]$ ]]; then
        echo "PixieScale 관련 도커 이미지 정리 중..."
        docker images | grep pixiescale | awk '{print $3}' | xargs -r docker rmi -f
        echo "도커 이미지 정리 완료"
    fi

    # 미니쿠베 정리 (선택 사항)
    read -p "미니쿠베를 재시작하시겠습니까? (y/n): " RESTART_MINIKUBE
    if [[ "$RESTART_MINIKUBE" =~ ^[Yy]$ ]]; then
        echo "미니쿠베 재시작 중..."
        minikube stop
        minikube start
        echo "미니쿠베 재시작 완료"
    fi

    echo -e "${GREEN}환경 정리가 완료되었습니다!${NC}"
}

# 이미지 빌드 함수
function build_images {
    echo -e "${YELLOW}도커 이미지를 빌드합니다...${NC}"

    # Minikube Docker 환경으로 전환
    echo -e "${YELLOW}Minikube Docker 환경으로 전환합니다...${NC}"
    eval $(minikube docker-env)

    # 공통 라이브러리 항상 빌드 (다른 서비스의 의존성)
    if [ -d "./pixiescale-common" ]; then
        echo "공통 라이브러리 빌드 중..."
        docker compose build common
    fi

    # 선택적 서비스 빌드 또는 전체 빌드
    if [ ${#BUILD_SERVICES[@]} -gt 0 ]; then
        echo "선택한 서비스를 빌드합니다: ${BUILD_SERVICES[*]}"
        for SERVICE in "${BUILD_SERVICES[@]}"; do
            if docker compose config --services | grep -q "^$SERVICE$"; then
                echo "서비스 빌드 중: $SERVICE"
                docker compose build $SERVICE
            else
                echo -e "${RED}서비스를 찾을 수 없습니다: $SERVICE${NC}"
                echo "사용 가능한 서비스: $(docker compose config --services | tr '\n' ' ')"
            fi
        done
    else
        echo "모든 마이크로서비스 빌드 중..."
        docker compose build $(docker compose config --services | grep -v "common")
    fi

    echo -e "${GREEN}도커 이미지 빌드가 완료되었습니다!${NC}"

    # 로컬 Docker 환경으로 돌아가기
    echo -e "${YELLOW}로컬 Docker 환경으로 돌아갑니다...${NC}"
    eval $(minikube docker-env -u)

    echo -e "${GREEN}이미지 빌드 과정이 완료되었습니다!${NC}"
}

# 포트 포워딩 시작 함수
function start_port_forwarding {
    echo -e "${YELLOW}모니터링 서비스 포트 포워딩을 시작합니다...${NC}"

    # API 게이트웨이 서비스 찾기
    API_SERVICE=$(kubectl get services -n $NAMESPACE | grep -i api | head -1 | awk '{print $1}')
    if [ -z "$API_SERVICE" ]; then
        API_SERVICE=$(kubectl get services -n $NAMESPACE | grep -i gateway | head -1 | awk '{print $1}')
    fi

    if [ -z "$API_SERVICE" ]; then
        echo -e "${RED}API 게이트웨이 서비스를 찾을 수 없습니다!${NC}"
        return 1
    fi

    # Prometheus 서비스 찾기
    PROMETHEUS_SERVICE=$(kubectl get services -n $NAMESPACE | grep -i prometheus | head -1 | awk '{print $1}')
    if [ -z "$PROMETHEUS_SERVICE" ]; then
        echo -e "${RED}Prometheus 서비스를 찾을 수 없습니다!${NC}"
        return 1
    fi

    # Grafana 서비스 찾기
    GRAFANA_SERVICE=$(kubectl get services -n $NAMESPACE | grep -i grafana | head -1 | awk '{print $1}')
    if [ -z "$GRAFANA_SERVICE" ]; then
        echo -e "${RED}Grafana 서비스를 찾을 수 없습니다!${NC}"
        return 1
    fi

    # API 게이트웨이 포트 포워딩 시작
    kubectl port-forward service/$API_SERVICE 8080:8080 -n $NAMESPACE > /dev/null 2>&1 &
    API_PF_PID=$!

    # Prometheus 포트 포워딩 시작
    kubectl port-forward service/$PROMETHEUS_SERVICE 9090:9090 -n $NAMESPACE > /dev/null 2>&1 &
    PROMETHEUS_PF_PID=$!

    # Grafana 포트 포워딩 시작
    kubectl port-forward service/$GRAFANA_SERVICE 3000:3000 -n $NAMESPACE > /dev/null 2>&1 &
    GRAFANA_PF_PID=$!

    # 연결 확인
    sleep 2
    API_RUNNING=true
    PROMETHEUS_RUNNING=true
    GRAFANA_RUNNING=true

    if ! ps -p $API_PF_PID > /dev/null; then
        echo -e "${RED}API 게이트웨이 포트 포워딩 시작 실패${NC}"
        API_RUNNING=false
    fi

    if ! ps -p $PROMETHEUS_PF_PID > /dev/null; then
        echo -e "${RED}Prometheus 포트 포워딩 시작 실패${NC}"
        PROMETHEUS_RUNNING=false
    fi

    if ! ps -p $GRAFANA_PF_PID > /dev/null; then
        echo -e "${RED}Grafana 포트 포워딩 시작 실패${NC}"
        GRAFANA_RUNNING=false
    fi

    echo -e "${GREEN}포트 포워딩 상태:${NC}"
    if [ "$API_RUNNING" = true ]; then
        echo -e "API 게이트웨이: ${GREEN}http://localhost:8080${NC} (PID: $API_PF_PID)"
    else
        echo -e "API 게이트웨이: ${RED}실패${NC}"
    fi

    if [ "$PROMETHEUS_RUNNING" = true ]; then
        echo -e "Prometheus: ${GREEN}http://localhost:9090${NC} (PID: $PROMETHEUS_PF_PID)"
    else
        echo -e "Prometheus: ${RED}실패${NC}"
    fi

    if [ "$GRAFANA_RUNNING" = true ]; then
        echo -e "Grafana: ${GREEN}http://localhost:3000${NC} (PID: $GRAFANA_PF_PID) (사용자명/비밀번호: admin/admin)"
    else
        echo -e "Grafana: ${RED}실패${NC}"
    fi

    if [ "$API_RUNNING" = false ] || [ "$PROMETHEUS_RUNNING" = false ] || [ "$GRAFANA_RUNNING" = false ]; then
        echo -e "${YELLOW}일부 포트 포워딩이 실패했습니다. 수동으로 포트 포워딩을 설정하세요:${NC}"
        if [ "$API_RUNNING" = false ]; then
            echo -e "${YELLOW}kubectl port-forward service/$API_SERVICE 8080:8080 -n $NAMESPACE${NC}"
        fi
        if [ "$PROMETHEUS_RUNNING" = false ]; then
            echo -e "${YELLOW}kubectl port-forward service/$PROMETHEUS_SERVICE 9090:9090 -n $NAMESPACE${NC}"
        fi
        if [ "$GRAFANA_RUNNING" = false ]; then
            echo -e "${YELLOW}kubectl port-forward service/$GRAFANA_SERVICE 3000:3000 -n $NAMESPACE${NC}"
        fi
        return 1
    fi

    return 0
}

# 배포 함수
function deploy_services {
    echo -e "${YELLOW}PixieScale 서비스를 배포합니다...${NC}"

    # 기존 포트 포워딩 종료
    echo -e "${YELLOW}기존 포트 포워딩 종료 중...${NC}"
    pkill -f "kubectl port-forward" 2>/dev/null || true

    # 네임스페이스 생성 (존재하지 않는 경우)
    if ! kubectl get namespace $NAMESPACE &> /dev/null; then
        echo -e "${YELLOW}네임스페이스 $NAMESPACE 생성 중...${NC}"
        kubectl create namespace $NAMESPACE
    fi

    # 기본 인프라 구성 요소 적용
    echo -e "${YELLOW}기본 인프라 구성 요소 적용 중...${NC}"

    # Secrets 적용
    if [ -f "k8s/secrets.yml" ]; then
        kubectl apply -f k8s/secrets.yml -n $NAMESPACE
    fi

    # ConfigMap 적용
    if [ -f "k8s/configmap.yml" ]; then
        kubectl apply -f k8s/configmap.yml -n $NAMESPACE
    fi

    # 볼륨 및 스토리지 적용
    if [ -f "k8s/volumes.yml" ]; then
        kubectl apply -f k8s/volumes.yml -n $NAMESPACE
    fi

    # RBAC 적용
    if [ -f "k8s/prometheus-rbac.yml" ]; then
      kubectl apply -f k8s/prometheus-rbac.yml -n $NAMESPACE
    fi

    # kube-state-metrics 배포 (추가된 부분)
    echo -e "${YELLOW}kube-state-metrics 배포 중...${NC}"
    if ! kubectl get deployment kube-state-metrics -n kube-system &> /dev/null; then
        if [ -d "kube-state-metrics/examples/standard" ]; then
            kubectl apply -f kube-state-metrics/examples/standard/
            echo -e "${GREEN}kube-state-metrics 배포 완료${NC}"
        else
            echo -e "${RED}kube-state-metrics 디렉토리를 찾을 수 없습니다.${NC}"
            echo -e "${YELLOW}다음 명령어로 설치할 수 있습니다: kubectl apply -f https://github.com/kubernetes/kube-state-metrics/releases/download/v2.8.0/kube-state-metrics.yaml${NC}"
        fi
    else
        echo -e "${GREEN}kube-state-metrics가 이미 배포되어 있습니다.${NC}"
    fi

    # 서비스 및 디플로이먼트 적용
    echo -e "${YELLOW}서비스 배포 중...${NC}"
    kubectl apply -f k8s/kafka.yml -n $NAMESPACE
    kubectl apply -f k8s/redis.yml -n $NAMESPACE
    kubectl apply -f k8s/apigateway.yml -n $NAMESPACE
    kubectl apply -f k8s/mediaingestion.yml -n $NAMESPACE
    kubectl apply -f k8s/jobmanagement.yml -n $NAMESPACE
    kubectl apply -f k8s/mediastorage.yml -n $NAMESPACE
    kubectl apply -f k8s/transcodingworker.yml -n $NAMESPACE
    kubectl apply -f k8s/prometheus.yml -n $NAMESPACE
    kubectl apply -f k8s/grafana.yml -n $NAMESPACE

    # 배포 재시작
    echo -e "${YELLOW}마이크로서비스 재시작 중...${NC}"
    kubectl rollout restart deployment kafka -n $NAMESPACE
    kubectl rollout restart deployment redis -n $NAMESPACE
    kubectl rollout restart deployment apigateway -n $NAMESPACE
    kubectl rollout restart deployment mediaingestion -n $NAMESPACE
    kubectl rollout restart deployment jobmanagement -n $NAMESPACE
    kubectl rollout restart deployment mediastorage -n $NAMESPACE
    kubectl rollout restart deployment transcodingworker -n $NAMESPACE
    kubectl rollout restart deployment prometheus -n $NAMESPACE
    kubectl rollout restart deployment grafana -n $NAMESPACE

    # 배포 상태 확인
    echo -e "${YELLOW}배포 상태 확인 중...${NC}"
    kubectl rollout status deployment -n $NAMESPACE --timeout=120s || true

    # 추가 대기
    echo -e "${YELLOW}서비스 안정화를 위해 5초 대기 중...${NC}"
    sleep 5

    # 포트 포워딩 시작
    start_port_forwarding

    # 파드 상태 표시
    echo -e "${YELLOW}파드 상태:${NC}"
    kubectl get pods -n $NAMESPACE

    echo -e "${GREEN}PixieScale 서비스 배포가 완료되었습니다!${NC}"
}

# 메인 로직
if [ "$CLEAN" = true ]; then
    clean_environment
    exit 0  # 정리 후 종료
fi

if [ "$BUILD_IMAGES" = true ]; then
    build_images
fi

if [ "$DEPLOY" = true ]; then
    deploy_services
elif [ "$BUILD_IMAGES" = true ]; then
    # 이미지 빌드만 했을 경우 배포 여부 확인
    echo -e "${YELLOW}이미지를 빌드했습니다. 배포도 수행하시겠습니까?${NC}"
    read -p "배포를 수행하시겠습니까? (y/n): " DEPLOY_CONFIRM
    if [[ "$DEPLOY_CONFIRM" =~ ^[Yy]$ ]]; then
        deploy_services
    else
        echo -e "${YELLOW}나중에 다음 명령을 실행하여 배포를 수행하세요: $0 -d${NC}"
    fi
else
    # 아무 옵션도 지정하지 않은 경우 도움말 표시
    echo -e "${YELLOW}옵션을 지정하지 않았습니다. 아래 도움말을 참고하세요:${NC}"
    show_help
fi

echo -e "${GREEN}모든 작업이 완료되었습니다!${NC}"