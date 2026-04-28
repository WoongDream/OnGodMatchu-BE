# Deployment Guide

OnGodMatchu 백엔드의 배포 인프라와 운영 가이드.

## 인프라 구성

```
[GitHub] ──push──> [GitHub Actions] ──build──> [GHCR]
                                                  │
                                                  │ pull
                                                  ▼
[Cloudflare DNS] ──> [EC2 t3.micro]
                      ├── Nginx (443/80)
                      │     └── Let's Encrypt 인증서
                      ├── Spring Boot (Docker, 8080)
                      └── PostgreSQL 16 (Docker, 5432)
```

| 영역 | 구성 |
|------|------|
| Frontend | S3 + CloudFront (별도 저장소) |
| Backend | 단일 EC2 인스턴스에 Spring Boot + PostgreSQL 컨테이너 |
| TLS | Let's Encrypt (Certbot 자동 갱신) |
| Reverse Proxy | Nginx (443/80 → Spring Boot 8080) |
| Image Registry | GHCR (`ghcr.io/woongdream/ongodmatchu-be`) |

## 도메인 / 인증서

| 도메인 | 용도 | TLS |
|--------|------|-----|
| `ongodmatchu.com` | 프론트엔드 | CloudFront + ACM |
| `www.ongodmatchu.com` | 프론트엔드 (alias) | CloudFront + ACM |
| `api.ongodmatchu.com` | 백엔드 API | Nginx + Let's Encrypt |

Let's Encrypt 인증서는 90일마다 Certbot이 자동 갱신 (`crontab` 등록).

## CI/CD 흐름

`main` 브랜치에 push 하면 GitHub Actions가 자동으로 빌드 → 푸시 → 배포한다.

```
git push origin main
        │
        ▼
GitHub Actions
  ├─ Gradle build
  ├─ Docker 이미지 빌드 (linux/amd64, multi-stage)
  ├─ GHCR 푸시 (ghcr.io/woongdream/ongodmatchu-be:latest)
  └─ EC2에 SSM Send Command
        │
        ▼
EC2
  ├─ docker pull (새 이미지)
  ├─ 기존 컨테이너 중단
  ├─ 새 컨테이너 실행 (환경변수 주입)
  └─ 헬스체크 (/api/health)
```

워크플로우 정의: [`.github/workflows/deploy.yml`](./.github/workflows/deploy.yml)

## 운영 환경 변수

운영 환경은 `prod` 프로파일을 사용하며, 모든 시크릿은 GitHub Secrets에서 관리한다.

| 변수 | 설명 |
|------|------|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ongodmatchu` |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (256bit 이상) |
| `MAIL_USERNAME` | Gmail 계정 |
| `MAIL_PASSWORD` | Gmail 앱 비밀번호 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth |
| `NAVER_CLIENT_ID` / `NAVER_CLIENT_SECRET` | Naver OAuth |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | Kakao OAuth |
| `AWS_ACCESS_KEY` / `AWS_SECRET_KEY` | 운영용 IAM (S3 접근) |
| `AWS_S3_BUCKET` | S3 버킷 이름 |
| `CLAUDE_API_KEY` | Anthropic Claude API Key |
| `FRONTEND_URL` | `https://ongodmatchu.com` |

## EC2 접속

EC2에는 SSH가 닫혀 있으며 **AWS Systems Manager Session Manager** 로만 접속한다.

```
AWS Console → EC2 → 인스턴스 선택 → 연결 → SSM Session Manager
```

> SSH 키 관리 불필요, IAM 권한으로 접속 제어. 모든 세션은 CloudTrail에 기록됨.

## 컨테이너 운영

EC2에서 사용하는 자주 쓰는 명령어.

### 상태 확인

```bash
sudo docker ps                              # 컨테이너 상태
sudo docker logs -f ongodmatchu-backend     # 백엔드 로그
sudo docker logs -f ongodmatchu-postgres    # DB 로그
sudo docker stats                           # 리소스 사용량
```

### DB 접속

```bash
sudo docker exec -it ongodmatchu-postgres psql -U ongodmatchu -d ongodmatchu
```

### 컨테이너 재시작

```bash
sudo docker restart ongodmatchu-backend     # 백엔드만 재시작
sudo docker restart ongodmatchu-postgres    # DB 재시작
```

### 수동 배포 (긴급 시)

```bash
sudo docker pull ghcr.io/woongdream/ongodmatchu-be:latest
sudo docker stop ongodmatchu-backend
sudo docker rm ongodmatchu-backend
# (워크플로우의 docker run 명령 참고하여 재실행)
```

## Nginx 운영

```bash
sudo nginx -t                          # 설정 문법 검사
sudo systemctl reload nginx            # 설정 리로드 (무중단)
sudo systemctl restart nginx           # 재시작
sudo tail -f /var/log/nginx/access.log # 접근 로그
sudo tail -f /var/log/nginx/error.log  # 에러 로그
```

설정 파일: `/etc/nginx/conf.d/api.conf`

## 시스템 리소스

t3.micro는 RAM 1GB로 빠듯하므로 모니터링 필수.

```bash
free -h            # 메모리 (Swap 포함)
df -h              # 디스크
top                # CPU / 메모리 실시간
sudo docker stats  # 컨테이너별 리소스
```

OOM 방지를 위해 2GB swap 활성화되어 있음 (`/swapfile`).

## 트러블슈팅

### 배포는 성공했지만 502 Bad Gateway

1. 백엔드 컨테이너 상태 확인
```bash
   sudo docker ps -a | grep ongodmatchu-backend
```
2. 컨테이너가 죽어있다면 로그 확인
```bash
   sudo docker logs ongodmatchu-backend
```
3. 흔한 원인:
    - 환경변수 누락 (DB_PASSWORD 등)
    - DB 연결 실패 (PostgreSQL 컨테이너가 안 떠있음)
    - 포트 충돌

### DB 비밀번호 변경

1. 새 비밀번호 생성: `openssl rand -base64 32`
2. PostgreSQL에서 변경
```bash
   sudo docker exec -it ongodmatchu-postgres psql -U ongodmatchu -d ongodmatchu
   ALTER USER ongodmatchu WITH PASSWORD 'new_password';
   \q
```
3. GitHub Secrets의 `DB_PASSWORD` 업데이트
4. 재배포 (push 또는 수동 워크플로우 실행)

### 인증서 갱신 실패

```bash
sudo certbot renew --dry-run    # 갱신 시뮬레이션
sudo certbot renew              # 강제 갱신
sudo systemctl reload nginx     # 갱신 후 Nginx 재시작
```

### 디스크 부족

```bash
sudo docker system prune -a     # 미사용 이미지/컨테이너 정리
sudo journalctl --vacuum-time=7d  # 오래된 시스템 로그 삭제
```

## 비용 구조 (월 기준)

| 항목 | 비용 (USD) | 비고 |
|------|-----------|------|
| EC2 t3.micro | $0 | 프리티어 1년차 |
| EBS 30GB | $0 | 프리티어 |
| 데이터 전송 (1GB) | $0 | 프리티어 |
| Route 53 / Cloudflare DNS | $0 | Cloudflare 사용 |
| ACM 인증서 (CloudFront) | $0 | AWS 무료 |
| Let's Encrypt (EC2) | $0 | 무료 |
| GHCR (Private) | $0 | GitHub 무료 한도 |
| **합계 (1년차)** | **~$0** | |
| **합계 (1년 이후)** | **~$8** | EC2 온디맨드 요금 |

도메인 (`ongodmatchu.com`) 은 별도, 연 약 $10 (Cloudflare).