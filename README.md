# 💊 Hachicare (Backend)
> **JunctionX Korea (Upstage Studio 트랙) 출품작** <br/>
> 외국인 유학생을 위한 AI 기반 한글 처방전 번역 및 맞춤형 의료 정보 제공 서비스 

## 🛠 Tech Stack
- **Framework/Language:** Spring Boot, Java
- **Database & Cache:** TiDB, Redis
- **Infrastructure:** Oracle Cloud, Docker
- **AI / External API:** Upstage Studio API

## 💡 Key Features
- **AI 기반 비정형 데이터 파싱 (Upstage OCR)**
  - 처방전 이미지의 복잡한 한글 의료 데이터를 서버에서 파싱하고 영문 데이터로 매핑하는 비즈니스 로직 구현.
- **프론트엔드 친화적 API 설계**
  - 클라이언트가 처방전 및 주의사항을 직관적인 UI로 렌더링하기 쉽도록 추출된 비정형 데이터를 최적화된 JSON 구조로 가공하여 반환.
- **안전한 인증 아키텍처 및 인프라 구축**
  - **Redis**를 도입하여 JWT(Refresh Token 등)를 인메모리 환경에서 빠르고 안전하게 관리하는 인증/인가 로직 구현.
  - 제한된 해커톤 시간(48시간) 내에 **Docker** 컨테이너 기반으로 **Oracle Cloud**에 서버를 배포하고 **TiDB**를 연동함.

## ⚙️ Role
- 백엔드 API 설계 및 개발
- Upstage OCR API 연동 및 데이터 가공 로직 구현
- 클라우드 인프라(Oracle Cloud, Docker) 배포 및 DB 구축
