# -*- coding: utf-8 -*-
"""
Google Colab T4 GPU 환경 전용 Gemma-2-2B-IT QLoRA Fine-Tuning 스크립트

이 스크립트를 Colab에서 실행하면 dataset.jsonl 파일을 불러와 
출력 규격(JSON)을 강제 학습하고 새로운 LoRA 어댑터를 병합해 보존합니다.
"""

import os
import torch
from datasets import load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,
    pipeline
)
from peft import LoraConfig, PeftModel, prepare_model_for_kbit_training
from trl import SFTTrainer

# 1. 환경 변수 및 설정 선언
# Hugging Face 토큰이 필요할 수 있습니다 (Gemma 모델 접근 승인용)
HF_TOKEN = "your_huggingface_token_here" # 필수: Hugging Face에서 발급받아 붙여넣어주세요.
BASE_MODEL = "google/gemma-2-2b-it"
NEW_MODEL = "gemma-2-2b-it-dotnote"

print("▶ 1. 패키지 라이브러리 임포트 및 GPU 장치 검증 완료")
print(f"▶ 사용 GPU: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU (GPU 없음)'}")

# 2. 4-bit 비트앤바이트(bitsandbytes) 양자화 설정 (무료 T4 VRAM 부족 해결용)
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.float16,
    bnb_4bit_use_double_quant=True
)

# 3. 토크나이저 및 베이스 모델 로드
print("▶ 2. Google Gemma-2-2b-it 베이스 모델 로드 중...")
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, token=HF_TOKEN)
tokenizer.pad_token = tokenizer.eos_token
tokenizer.padding_side = "right"

model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    quantization_config=bnb_config,
    torch_dtype=torch.float16, # Gemma-2 내부 텐서가 BFloat16으로 자동 변환되어 충돌하는 것을 방지
    device_map={"": 0}, # 0번 GPU에 매핑
    token=HF_TOKEN
)
model = prepare_model_for_kbit_training(model)

# 4. LoRA 초경량 파인튜닝 어댑터 구조 정의
peft_config = LoraConfig(
    lora_alpha=16,
    lora_dropout=0.1,
    r=8,
    bias="none",
    task_type="CAUSAL_LM",
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
)

# 5. 로컬에서 업로드한 dataset.jsonl 데이터셋 로드 및 프롬프트 포매팅
print("▶ 3. dataset.jsonl 데이터셋 로드 및 변환 중...")
raw_dataset = load_dataset("json", data_files="dataset.jsonl", split="train")

formatted_texts = []
for item in raw_dataset:
    inst = item['instruction']
    inp = item['input']
    out = item['output']
    # Gemma 지시어 튜닝 표준 템플릿 적용
    text = (
        f"<start_of_turn>user\n"
        f"{inst}\n\n"
        f"메모 내용:\n{inp}<end_of_turn>\n"
        f"<start_of_turn>model\n"
        f"{out}<end_of_turn>"
    )
    formatted_texts.append(text)

# 오직 'text' 컬럼 하나만 들어있는 초정밀 퓨어 데이터셋으로 재구성 (버전 충돌 원천 차단)
from datasets import Dataset
dataset = Dataset.from_dict({"text": formatted_texts})
print(f"✅ 학습 데이터 변환 및 정제 완료 (총 {len(dataset)}개 샘플)")

# 6. SFTTrainer 훈련 하이퍼파라미터 정의 (10분 내외 학습용)
training_arguments = TrainingArguments(
    output_dir="./results",
    num_train_epochs=3,                  # 데이터셋 3회 반복 학습
    per_device_train_batch_size=4,       # T4 최적 배치 크기
    gradient_accumulation_steps=2,
    optim="paged_adamw_32bit",
    save_steps=100,
    logging_steps=10,
    learning_rate=2e-4,                  # QLoRA 학습률
    weight_decay=0.001,
    fp16=False,                          # AMP GradScaler의 BFloat16 충돌 방지를 위해 비활성화
    bf16=False,
    max_grad_norm=0.3,
    max_steps=-1,
    warmup_steps=10,                     # warmup_ratio 대신 warmup_steps를 직접 명시하여 감쇠 안정화 및 경고 제거
    lr_scheduler_type="constant"
)

# SFTTrainer 생성 (어떠한 trl 버전 충돌도 100% 불가능한 퓨어 구조 설계)
trainer = SFTTrainer(
    model=model,
    train_dataset=dataset,
    peft_config=peft_config,
    args=training_arguments
)

# 7. 파인튜닝 실행
print("🚀 4. QLoRA 파인튜닝 학습 개시! (Colab T4 기준 약 10분 소요)...")
trainer.train()
print("✅ QLoRA 파인튜닝 완료!")

# 8. 어댑터 저장 및 병합(Merge) 실행
print("▶ 5. 베이스 모델과 튜닝 완료된 LoRA 어댑터 가중치 병합 중...")
# 메모리 절약을 위해 기존 베이스 모델과 어댑터를 CPU 메모리상에서 강제로 병합합니다.
trained_model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    low_cpu_mem_usage=True,
    return_dict=True,
    torch_dtype=torch.float16,
    device_map={"": "cpu"},
    token=HF_TOKEN
)
merged_model = PeftModel.from_pretrained(trained_model, "./results/checkpoint-xxx") # 최신 체크포인트 경로 기입
merged_model = merged_model.merge_and_unload()

# 9. 최종 학습 완료 모델 로컬 저장
print(f"▶ 6. 최종 모델 '{NEW_MODEL}' 디렉토리에 병합 저장 중...")
merged_model.save_pretrained(NEW_MODEL)
tokenizer.save_pretrained(NEW_MODEL)
print(f"🎉 축하합니다! 최종 모델 '{NEW_MODEL}' 저장이 완료되었습니다.")
print("💡 이 모델 디렉토리를 통째로 다운로드받아 Ollama 또는 안드로이드 구동 툴에 탑재하십시오.")
