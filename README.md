# Speech Summary Android

Kotlin/Jetpack Compose 앱. 녹음 또는 오디오·영상 파일에서 음성 트랙을 가져와 16 kHz mono PCM으로 변환한 뒤 파일별로 로컬 Whisper STT를 실행한다.

## 로컬 모델 준비

앱은 다음 파일을 로컬에서 읽는다. 큰 바이너리는 Git에 넣지 않으므로 새 PC에서는 먼저 내려받아야 한다.

```powershell
New-Item -ItemType Directory -Force app\libs | Out-Null
New-Item -ItemType Directory -Force local-models | Out-Null
curl.exe -L --fail -o app\src\main\assets\ggml-medium.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.bin
curl.exe -L --fail -o app\libs\sherpa-onnx-1.13.8.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
curl.exe -L --fail -o app\src\main\assets\speaker-eres2net.onnx https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
curl.exe -L --fail -o local-models\speaker-segmentation.tar.bz2 https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2
tar -xf local-models\speaker-segmentation.tar.bz2 -C local-models
Copy-Item local-models\sherpa-onnx-pyannote-segmentation-3-0\model.int8.onnx app\src\main\assets\speaker-segmentation.onnx
```

Whisper STT는 다국어 `ggml-medium.bin`을 사용한다. small 모델보다 파일 크기가 크고 실행 시 메모리 사용량과 처리 시간이 늘 수 있다.
`ggml-medium.bin` SHA-256: `6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208`.
`sherpa-onnx-1.13.8.aar` SHA-256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
옛 `ggml-base.bin`이나 `ggml-small.bin`을 `assets`에 함께 두면 APK가 불필요하게 커진다. 이전 모델은 `local-models`처럼 APK에 포함되지 않는 폴더에 보관한다.

각 파일 카드의 **화자 수**에서 기본값인 **자동 감지** 또는 **직접 지정(1~99명)**을 선택한다. 2명이 대화한 파일은 2명으로 지정한 뒤 문서 아이콘으로 텍스트를 다시 추출하면 된다. 설정은 파일별로 저장되며 녹음·가져온 오디오·영상에 동일하게 적용된다. 추출 대기/진행/취소 중에는 변경할 수 없다. 변경은 다음 추출부터 적용되며 기존 결과를 자동으로 바꾸지는 않는다.

자동 감지는 녹음 음성에서 인원수를 추정한다. 직접 지정은 해당 인원수를 기준으로 화자를 묶으며, 1명은 모두 A로 표시한다. 화자 이름 A·B·C는 파일별 임시 구분이고 인물의 실제 신원을 뜻하지 않는다. 음성이 부족하면 지정 인원보다 적게 검출될 수 있고, 겹친 발화나 아주 짧은 말은 잘못 배정될 수 있다. 짧은 음성에도 지정 인원이 적용되도록 번들 화자 모델의 단일 구간 처리 경로를 보완했다.

앱은 Android `MediaExtractor`/`MediaCodec`으로 오디오·영상 음성 트랙을 처리한다. 지원 형식은 기기 코덱에 따라 다르다. Whisper JNI 소스는 whisper.cpp 1.9.4 기반이다. [Whisper 모델](https://huggingface.co/ggerganov/whisper.cpp), [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [pyannote segmentation](https://huggingface.co/pyannote/segmentation-3.0), [3D-Speaker](https://github.com/modelscope/3D-Speaker)의 라이선스·배포 조건은 공개 배포 전에 확인해야 한다.
