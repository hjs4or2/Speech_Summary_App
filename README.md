# Speech Summary Android

Kotlin/Jetpack Compose 앱. 녹음 또는 오디오·영상 파일에서 음성 트랙을 가져와 16 kHz mono PCM으로 변환한 뒤 파일별로 로컬 Whisper STT를 실행한다.

## 로컬 모델 준비

`app/src/main/assets/ggml-base.bin`에 [whisper.cpp의 ggml-base 모델](https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin)을 넣어야 빌드된 앱에서 STT가 동작한다. 이 모델은 약 148MB로 GitHub 일반 파일 크기 제한을 넘어 Git에는 포함하지 않는다.

기존 PC의 모델은 `D:\_Develop\_Project\Speech_Summary\models\ggml-base.bin`에 있다. 다음 PowerShell 명령으로 복사할 수 있다.

```powershell
Copy-Item 'D:\_Develop\_Project\Speech_Summary\models\ggml-base.bin' 'app\src\main\assets\ggml-base.bin'
```

앱은 Android 시스템의 `MediaExtractor`/`MediaCodec`으로 오디오와 영상의 음성 트랙을 처리한다. 지원되는 입력 형식은 기기에 설치된 코덱에 따라 달라진다. Whisper JNI 소스는 whisper.cpp 1.9.4 기반이다.
