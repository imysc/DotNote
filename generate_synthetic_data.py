# -*- coding: utf-8 -*-
import json
import random
import os

# 1. 다양한 도메인별 템플릿 및 단어 풀 선언
themes = {
    "IT/개발": {
        "nouns": ["안드로이드 UI", "Room 데이터베이스 마이그레이션", "zsh CLI 플러그인", "Git 브랜치 충돌", "Kotlin 코루틴 비동기", "Jetpack Compose 테마"],
        "actions": ["에러가 발생해서 머리가 아프다", "최적화 설정을 적용했다", "최신 가이드대로 마이그레이션을 마쳤다", "동적 레이아웃 구성을 추가했다", "디버깅 로그를 분석하여 원인을 찾았다"],
        "references": ["마이그레이션 에러 해결 가이드", "zsh 커스텀 최적화 팁", "안드로이드 아키텍처 규칙", "Compose 공식 레퍼런스"],
        "relation_types": ["문제해결", "가이드참조", "최적화", "정보보완"]
    },
    "맛집/요리": {
        "nouns": ["로컬 파스타 맛집", "감바스 알 아히요 조리법", "스테이크 굽기 예산", "꾸덕한 크림 리조또", "주말 가족 식사 후보지", "브런치 카페 리스트"],
        "actions": ["직접 만들어서 아주 맛있었다", "블로그 후기들을 취합해 방문했다", "장보기 금액 예산을 대강 계산했다", "신선한 마늘과 올리브유를 샀다", "가족들이 아주 만족스럽게 먹었다"],
        "references": ["지난달 마트 파티 예산 영수증", "제주 로컬 숨은 맛집 리스트", "집들이 요리 레시피북"],
        "relation_types": ["예산참조", "정보보완", "메뉴선정", "장소추천"]
    },
    "독서/학업": {
        "nouns": ["디스토피아 소설 라인업", "올더스 헉슬리의 멋진 신세계", "어학시험 토익 목표 성적", "독서 토론 모임 발제문", "디지털 드로잉 단축키 가이드", "학회 발표 PPT 디자인"],
        "actions": ["도서관에서 책을 대여해 왔다", "내일부터 매일 2시간씩 공부하기로 했다", "발표 자료 슬라이드를 최종 점검했다", "노트에 유용한 핵심 단어들을 받아 적었다", "시간 스케줄을 대폭 당겨 잡았다"],
        "references": ["디스토피아 소설 추천 리스트", "디지털 일러스트 독학 노트", "영어 단어 목표 3000선"],
        "relation_types": ["도서비교", "계획변경", "정보보완", "스케줄참조"]
    },
    "운동/건강": {
        "nouns": ["야간 한강 러닝 루틴", "하체 웨이트 트레이닝 루틴", "닭가슴살 샐러드 식단 예산", "수면 개선 수칙 리스트", "동물병원 예방접종 일정", "가족 종합 건강검진 보고서"],
        "actions": ["페이스를 무리하지 않고 완주했다", "단백질 섭취 탄단지를 엄격히 맞추었다", "스마트폰 금지 습관을 정했다", "몸무게와 건강 지표를 기록해 두었다", "의사 선생님께 상세 의견을 전해드렸다"],
        "references": ["트레이너 코치 홈트 가이드", "수면 장애 극복 생활 가이드", "반려견 건강 성장기록"],
        "relation_types": ["기록비교", "습관교정", "의료상담", "수치분석"]
    }
}

tags_pool = {
    "IT/개발": ["안드로이드", "개발기록", "DB마이그레이션", "zsh설정", "Compose", "디버깅"],
    "맛집/요리": ["맛집탐방", "요리일기", "식사기록", "장보기", "홈스토랑", "카페추천"],
    "독서/학업": ["독서리뷰", "학업계획", "시험공부", "아이패드드로잉", "기획안작성", "자기개발"],
    "운동/건강": ["운동기록", "피트니스식단", "수면관리", "동물병원", "가족건강", "러닝크루"]
}

def generate_synthetic_item():
    # 1. 랜덤 주제 선택
    theme_name = random.choice(list(themes.keys()))
    theme = themes[theme_name]
    
    # 2. 본문 및 키워드 랜덤 조합
    noun = random.choice(theme["nouns"])
    action = random.choice(theme["actions"])
    ref = random.choice(theme["references"])
    
    # 한국어 자연스러운 접속사 결합
    connectors = [" 이를 위해 ", " 지난번에 작성해 둔 ", " 문득 예전에 메모해 둔 ", " 작업 능률을 위해 "]
    connector = random.choice(connectors)
    
    final_actions = [
        f"{noun} 공부를 하다가 {action}.{connector}'{ref}' 메모를 찾아 참고했다.",
        f"오늘 {noun} 준비를 위해 {action}.{connector}'{ref}' 내용을 복기하여 결합했다.",
        f"어제 작성한 {noun} 기록을 복사하여 {action}.{connector}'{ref}' 가이드와 크로스 체크했다."
    ]
    memo_content = random.choice(final_actions)
    
    # 3. 태그 추출 (주제 전용 태그 풀에서 2~3개 랜덤 조합)
    selected_tags = random.sample(tags_pool[theme_name], k=random.randint(2, 3))
    # 명사 자체도 태그 후보로 1개 추가
    noun_tag = noun.split(" ")[0].replace("데이터베이스", "DB").replace("트레이닝", "운동")
    if noun_tag not in selected_tags:
        selected_tags.append(noun_tag)
        
    # 4. 관계 및 관계유형 매칭
    relation_type = random.choice(theme["relation_types"])
    
    # 5. JSON 포맷 패키징
    instruction = "메모 내용을 분석하여 연관 태그 리스트와 논리적 연결 관계를 JSON 규격으로만 출력하세요."
    
    output_dict = {
        "tags": selected_tags,
        "relations": [
            {
                "target_memo_keyword": ref,
                "relation_type": relation_type
            }
        ]
    }
    
    return {
        "instruction": instruction,
        "input": memo_content,
        "output": json.dumps(output_dict, ensure_ascii=False)
    }

def main(total_count=200):
    output_file = "dataset.jsonl"
    print(f"▶ 합성 데이터 대량 생성 파이프라인 가동...")
    print(f"▶ 타깃 생성 갯수: {total_count}개")
    
    # 기존 파일이 있으면 이어서 추가
    file_mode = "a" if os.path.exists(output_file) else "w"
    
    generated_count = 0
    with open(output_file, file_mode, encoding="utf-8") as f:
        for _ in range(total_count):
            item = generate_synthetic_item()
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
            generated_count += 1
            
    print(f"✅ 대량 데이터 합성 성공!")
    print(f"✅ 파일 '{output_file}'에 총 {generated_count}개의 고유 훈련 데이터가 성공적으로 보강되었습니다.")
    print(f"💡 이 파일을 Google Colab에 업로드하여 QLoRA 학습 데이터로 즉시 사용하십시오.")

if __name__ == "__main__":
    # 원하는 갯수를 입력하여 대량 생성 가능 (기본 200개)
    main(1000)
