import os
import mediapipe as mp
from mediapipe.tasks.python.genai import converter

# 절대 경로 구성
base_dir = os.path.dirname(os.path.abspath(__file__))
input_ckpt_dir = os.path.abspath(os.path.join(base_dir, "gemma-2-2b-it-dotnote"))
vocab_file = os.path.abspath(os.path.join(input_ckpt_dir, "tokenizer.model"))
output_dir = os.path.abspath(os.path.join(base_dir, "output"))
output_file = os.path.abspath(os.path.join(output_dir, "gemma-2-2b-it.task"))

# 출력 디렉토리 자동 생성
os.makedirs(output_dir, exist_ok=True)

print(f"[경로 검증]")
print(f"- 입력 가중치 경로: {input_ckpt_dir}")
print(f"- 토크나이저 경로  : {vocab_file}")
print(f"- 임시 출력 경로  : {output_dir}")
print(f"- 최종 생성 파일  : {output_file}\n")

config = converter.ConversionConfig(
    input_ckpt=input_ckpt_dir,
    ckpt_format="safetensors",
    model_type="GEMMA2_2B",                 # Gemma-2 2B 모델 규격 설정
    backend="gpu",                         # GPU 타겟 컴파일
    is_quantized=True,                     # 8-bit 양자화 활성화
    vocab_model_file=vocab_file,           # 토크나이저 모델 경로 지정
    output_tflite_file=output_file,        # 출력 모델 파일명 지정
    output_dir=output_dir                  # 결과 파일 저장 폴더
)

print("모델 컴파일 및 8-bit 양자화 수행 중 (수 분 이상 소요될 수 있습니다)...")
converter.convert_checkpoint(config)
print("완료! .task 파일이 성공적으로 생성되었습니다.")
