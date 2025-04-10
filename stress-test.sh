#!/bin/bash

# 색상 정의
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # 색상 리셋

# 기본 파라미터
FILE_PATH="sample.mp4"
DURATION=15         # 테스트 지속 시간 (분)
API_ENDPOINT="http://localhost:8080"

# 필요한 도구 확인
if ! command -v curl &> /dev/null; then
    echo -e "${RED}오류: curl이 설치되어 있지 않습니다${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${RED}오류: jq가 설치되어 있지 않습니다. 'sudo apt-get install jq'로 설치하세요${NC}"
    exit 1
fi

# 파일 존재 확인
if [ ! -f "$FILE_PATH" ]; then
    echo -e "${RED}오류: 파일을 찾을 수 없습니다 - ${FILE_PATH}${NC}"
    exit 1
fi

# 완만한 부하 패턴 설정
JOBS_PER_MINUTE=(1 1 2 3 5 8 13 21 34 55)

# 테스트 결과 디렉토리 생성
RESULTS_DIR="test_results_$(date +%Y%m%d_%H%M%S)"
mkdir -p $RESULTS_DIR

# 테스트 정보 기록
echo "테스트 시작 시간: $(date)" > $RESULTS_DIR/info.txt
echo "파일: $FILE_PATH" >> $RESULTS_DIR/info.txt
echo "지속 시간: $DURATION 분" >> $RESULTS_DIR/info.txt
echo "API 엔드포인트: $API_ENDPOINT" >> $RESULTS_DIR/info.txt

# 결과 기록 초기화
echo "시간,작업수,성공,실패" > $RESULTS_DIR/results.csv

# 테스트 실행
TOTAL_JOBS=0
SUCCESS_JOBS=0
FAILED_JOBS=0

echo -e "${GREEN}HPA 테스트를 시작합니다...${NC}"
echo -e "${YELLOW}완만한 부하 증가 패턴으로 쿠버네티스 오토스케일링을 테스트합니다${NC}"

for ((min=1; min<=DURATION; min++)); do
    # 현재 분에 실행할 작업 수 계산
    if [ $min -le ${#JOBS_PER_MINUTE[@]} ]; then
        JOBS_THIS_MINUTE=${JOBS_PER_MINUTE[$min-1]}
    else
        JOBS_THIS_MINUTE=${JOBS_PER_MINUTE[$((min % ${#JOBS_PER_MINUTE[@]}))]}
    fi

    echo -e "${YELLOW}분 $min/$DURATION: $JOBS_THIS_MINUTE 개 작업 제출 중...${NC}"

    MINUTE_SUCCESS=0
    MINUTE_FAILED=0

    # 작업 제출
    for ((i=1; i<=JOBS_THIS_MINUTE; i++)); do
        echo -e "작업 $i/$JOBS_THIS_MINUTE 제출 중..."

        # 파일 업로드
        UPLOAD_RESULT=$(curl -s -X POST -F "file=@$FILE_PATH" $API_ENDPOINT/api/v1/media)

        if [ $? -ne 0 ]; then
            echo -e "${RED}파일 업로드 실패${NC}"
            MINUTE_FAILED=$((MINUTE_FAILED+1))
            continue
        fi

        # mediaId 추출
        MEDIA_ID=$(echo $UPLOAD_RESULT | jq -r '.mediaId')

        if [ "$MEDIA_ID" == "null" ] || [ -z "$MEDIA_ID" ]; then
            echo -e "${RED}미디어 ID를 추출할 수 없습니다: $UPLOAD_RESULT${NC}"
            MINUTE_FAILED=$((MINUTE_FAILED+1))
            continue
        fi

        # 트랜스코딩 작업 생성 - 해상도 축소 (360p 제외)
        JOB_RESULT=$(curl -s -X POST -H "Content-Type: application/json" -d '{
            "mediaFileId": "'"$MEDIA_ID"'",
            "config": {
                "targetCodec": "H.264",
                "targetFormat": "MP4",
                "resolutions": [
                    {
                        "name": "720p",
                        "width": 1280,
                        "height": 720,
                        "bitrate": 2000
                    },
                    {
                        "name": "480p",
                        "width": 854,
                        "height": 480,
                        "bitrate": 1200
                    }
                ]
            }
        }' $API_ENDPOINT/api/v1/jobs)

        if [ $? -ne 0 ]; then
            echo -e "${RED}트랜스코딩 작업 생성 실패${NC}"
            MINUTE_FAILED=$((MINUTE_FAILED+1))
            continue
        fi

        # 작업 ID 추출
        JOB_ID=$(echo $JOB_RESULT | jq -r '.id')

        if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
            echo -e "${RED}작업 ID를 추출할 수 없습니다: $JOB_RESULT${NC}"
            MINUTE_FAILED=$((MINUTE_FAILED+1))
            continue
        fi

        echo -e "${GREEN}작업 생성 성공: $JOB_ID${NC}"
        MINUTE_SUCCESS=$((MINUTE_SUCCESS+1))

        # 작업 간 대기 시간 증가
        sleep 2
    done

    # 분 단위 결과 기록
    echo "$(date +%H:%M:%S),$JOBS_THIS_MINUTE,$MINUTE_SUCCESS,$MINUTE_FAILED" >> $RESULTS_DIR/results.csv

    # 총계 업데이트
    TOTAL_JOBS=$((TOTAL_JOBS + JOBS_THIS_MINUTE))
    SUCCESS_JOBS=$((SUCCESS_JOBS + MINUTE_SUCCESS))
    FAILED_JOBS=$((FAILED_JOBS + MINUTE_FAILED))

    # 진행 상황 표시
    echo -e "${GREEN}$min 분 경과: 총 $TOTAL_JOBS 작업 중 $SUCCESS_JOBS 성공, $FAILED_JOBS 실패${NC}"

    # 다음 분까지 대기 (작업 제출에 소요된 시간 고려)
    ELAPSED=$SECONDS
    WAIT_TIME=$((60 - (ELAPSED % 60)))
    if [ $WAIT_TIME -lt 60 ] && [ $WAIT_TIME -gt 0 ] && [ $min -lt $DURATION ]; then
        echo -e "${YELLOW}다음 분까지 $WAIT_TIME 초 대기...${NC}"
        sleep $WAIT_TIME
    fi
done

# 테스트 결과 요약
echo -e "${GREEN}테스트 완료!${NC}"
echo "총 작업: $TOTAL_JOBS"
echo "성공: $SUCCESS_JOBS"
echo "실패: $FAILED_JOBS"
echo "성공률: $(( (SUCCESS_JOBS * 100) / (TOTAL_JOBS > 0 ? TOTAL_JOBS : 1) ))%"

# 결과 저장
echo "총 작업: $TOTAL_JOBS" >> $RESULTS_DIR/summary.txt
echo "성공: $SUCCESS_JOBS" >> $RESULTS_DIR/summary.txt
echo "실패: $FAILED_JOBS" >> $RESULTS_DIR/summary.txt
echo "성공률: $(( (SUCCESS_JOBS * 100) / (TOTAL_JOBS > 0 ? TOTAL_JOBS : 1) ))%" >> $RESULTS_DIR/summary.txt
echo "테스트 종료 시간: $(date)" >> $RESULTS_DIR/summary.txt

echo -e "${GREEN}결과가 ${RESULTS_DIR} 디렉토리에 저장되었습니다${NC}"
