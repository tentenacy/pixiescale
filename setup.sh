set -e

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # 색상 리셋

echo -e "${GREEN}PixieScale 환경 설정을 시작합니다...${NC}"

# Docker 권한 확인 및 설정
if ! docker info > /dev/null 2>&1; then
  echo -e "${YELLOW}Docker 권한 설정이 필요합니다...${NC}"
  # Docker 그룹에 사용자 추가
  sudo usermod -aG docker $USER
  # Docker 서비스 재시작
  sudo service docker restart || true
  echo -e "${YELLOW}설정 완료. 권한 적용을 위해 터미널을 재시작한 후 다시 스크립트를 실행해주세요.${NC}"
  echo -e "${YELLOW}또는 다음 명령을 실행해 현재 세션에 적용하세요: 'newgrp docker'${NC}"
  exit 1
fi

# Minikube 클러스터 중지 및 삭제
echo -e "${YELLOW}기존 Minikube 클러스터를 정리합니다...${NC}"
minikube stop || true
minikube delete || true

# 새 Minikube 클러스터 시작 (GPU 없이)
echo -e "${YELLOW}새 Minikube 클러스터를 생성합니다 (GPU 없이)...${NC}"
minikube start --driver=docker --cpus=8 --memory=12g --kubernetes-version=v1.28.0

# metrics-server 활성화
echo -e "${YELLOW}Kubernetes Metrics Server를 활성화합니다...${NC}"
minikube addons enable metrics-server

# 필요한 디렉토리 생성
echo -e "${YELLOW}필요한 디렉토리를 생성합니다...${NC}"
mkdir -p /tmp/minikube-pixiescale/media/uploads
mkdir -p /tmp/minikube-pixiescale/media/output
mkdir -p /tmp/minikube-pixiescale/media/temp
chmod -R 777 /tmp/minikube-pixiescale

echo -e "${GREEN}환경 설정이 완료되었습니다. 이제 './pxctl.sh -b -d'를 실행하여 서비스를 빌드하고 배포하세요.${NC}"