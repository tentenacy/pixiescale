EEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # 색상 리셋

# 기본 파라미터
FILE_PATH="sample.mp4"
NUM_JOBS=10
DELAY_SECONDS=2

# 파라미터 처리
if [ "$1" != "" ]; then
	  FILE_PATH=$1
fi

if [ "$2" != "" ]; then
	  NUM_JOBS=$2
fi

if [ "$3" != "" ]; then
	  DELAY_SECONDS=$3
fi

echo -e "${YELLOW}스트레스 테스트 시작: 파일=${FILE_PATH}, 작업 수=${NUM_JOBS}, 딜레이=${DELAY_SECONDS}초${NC}"

# 파일 존재 확인
if [ ! -f "$FILE_PATH" ]; then
	  echo -e "${RED}오류: 파일을 찾을 수 없습니다 - ${FILE_PATH}${NC}"
	    exit 1
fi

# 필요한 도구 확인
if ! command -v curl &> /dev/null; then
	  echo -e "${RED}오류: curl이 설치되어 있지 않습니다${NC}"
	    exit 1
fi

if ! command -v jq &> /dev/null; then
	  echo -e "${RED}오류: jq가 설치되어 있지 않습니다. 'sudo apt-get install jq'로 설치하세요${NC}"
	    exit 1
fi

# 메인 루프
for ((i=1; i<=NUM_JOBS; i++)); do
	  echo -e "${YELLOW}작업 $i/$NUM_JOBS 시작 중...${NC}"
	    
	    # 파일 업로드
	      UPLOAD_RESULT=$(curl -s -X POST -F "file=@$FILE_PATH" http://localhost:8080/api/v1/media)
	        
	        if [ $? -ne 0 ]; then
			    echo -e "${RED}파일 업로드 실패${NC}"
			        continue
				  fi
				    
				    # mediaId 추출
				      MEDIA_ID=$(echo $UPLOAD_RESULT | jq -r '.mediaId')
				        
				        if [ "$MEDIA_ID" == "null" ] || [ -z "$MEDIA_ID" ]; then
						    echo -e "${RED}미디어 ID를 추출할 수 없습니다: $UPLOAD_RESULT${NC}"
						        continue
							  fi
							    
							    echo -e "${GREEN}파일 업로드 성공, 미디어 ID: $MEDIA_ID${NC}"
							      
							      # 트랜스코딩 작업 생성
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
																	          "bitrate": 2500
																		          },
																			          {
																					            "name": "480p",
																						              "width": 854,
																							                "height": 480,
																									          "bitrate": 1500
																										          },
																											          {
																													            "name": "360p",
																														              "width": 640,
																															                "height": 360,
																																	          "bitrate": 800
																																		          }
																																			        ]
																																				    }
																																				      }' http://localhost:8080/api/v1/jobs)
																																				        
																																				        if [ $? -ne 0 ]; then
																																						    echo -e "${RED}트랜스코딩 작업 생성 실패${NC}"
																																						        continue
																																							  fi
																																							    
																																							    # 작업 ID 추출
																																							      JOB_ID=$(echo $JOB_RESULT | jq -r '.id')
																																							        
																																							        if [ "$JOB_ID" == "null" ] || [ -z "$JOB_ID" ]; then
																																									    echo -e "${RED}작업 ID를 추출할 수 없습니다: $JOB_RESULT${NC}"
																																									        continue
																																										  fi
																																										    
																																										    echo -e "${GREEN}트랜스코딩 작업 생성 성공, 작업 ID: $JOB_ID${NC}"
																																										      
																																										      # 잠시 대기
																																										        if [ $i -lt $NUM_JOBS ]; then
																																												    echo -e "${YELLOW}${DELAY_SECONDS}초 대기 중...${NC}"
																																												        sleep $DELAY_SECONDS
																																													  fi
																																												  done

																																												  echo -e "${GREEN}스트레스 테스트 완료! 총 ${NUM_JOBS}개의 작업이 제출되었습니다.${NC}"
																																												  echo -e "${YELLOW}HPA 상태를 확인하려면 다음 명령어를 실행하세요: kubectl get hpa -n pixiescale${NC}"
