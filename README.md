# Speech Summary Android

Kotlin/Jetpack Compose 앱. 녹음 또는 오디오·영상 파일에서 음성 트랙을 가져와 16 kHz mono PCM으로 변환한 뒤 파일별로 로컬 Whisper STT를 실행한다.

## 로컬 모델 준비

APK에는 모델 바이너리가 들어가지 않는다. 첫 실행에서 앱은 다국어 Whisper base Q5_1, 기존 sherpa 화자 모델 2개, Qwen2.5 1.5B Q4_K_M을 고정 리비전에서 내려받는다. 기본 선택의 총 크기는 1,218,162,628바이트다. 설정에서 Nemotron 3 화자 구분을 선택하면 sherpa 모델 대신 공식 107,012,128바이트 GGUF를 필요할 때 내려받는다. Wi-Fi를 권장하고 모바일 데이터는 화면에서 별도로 동의해야 한다. 진행률, 중단, 이어받기와 재시도를 제공한다. 설치 전에도 녹음·가져오기·재생은 가능하다.

다운로드 파일과 `.part` 임시 파일은 모두 앱 전용 `noBackupFilesDir/models`에 저장된다. 크기와 SHA-256을 검증한 파일만 설치되고, 다음 실행에는 검증된 파일을 재사용한다. 이전 버전에서 `filesDir/models`로 가져온 정상 Qwen 모델은 검증 후 이 위치로 이동한다. 모델은 클라우드 백업·기기 이전에서 제외된다. **앱을 삭제하면 다운로드한 모델도 삭제되므로 재설치 시 다시 받아야 한다.** 앱 데이터 삭제나 제거 없이 APK를 업데이트하면 모델은 유지된다.

빌드에는 `app/libs/sherpa-onnx-1.13.8.aar`가 필요하다. 아래 모델 파일은 개발용 로컬 참조이며 소스 트리에 남아 있어도 APK에 포함되지 않는다.

```powershell
New-Item -ItemType Directory -Force app\libs | Out-Null
New-Item -ItemType Directory -Force local-models | Out-Null
curl.exe -L --fail -o local-models\ggml-base-q5_1.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin
curl.exe -L --fail -o app\libs\sherpa-onnx-1.13.8.aar https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
curl.exe -L --fail -o app\src\main\assets\speaker-eres2net.onnx https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/8be2a75c9ed7a590538b268e46fbb65e1aa9d208/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
curl.exe -L --fail -o app\src\main\assets\speaker-segmentation.onnx https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/9403a6902bb58e3d5ae8c7e77c3422de279db2e0/model.int8.onnx
```

Whisper STT는 한국어를 포함하는 다국어 `ggml-base-q5_1.bin`(59,707,625바이트, SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`)을 사용한다. 기존 다운로드 `ggml-medium.bin`은 지우거나 덮어쓰지 않는다. base는 medium보다 작아서 휴대폰의 저장·메모리 부담을 줄이지만, 잡음이 많은 녹음과 긴 회의에서 인식 정확도가 떨어질 수 있고 Q5_1 양자화도 오차를 더할 수 있다. 실제 기기 속도와 한국어 정확도는 측정하지 않았다.
`sherpa-onnx-1.13.8.aar` SHA-256: `633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`.
모델 바이너리를 `app/src/main/assets`에 두더라도 빌드의 assets source set에서 제외된다. 공개 파일의 고정 커밋 URL, 정확한 크기, SHA-256은 `ModelCatalog`에 기록했다.

각 파일 카드의 **화자 수**에서 기본값인 **자동 감지** 또는 **직접 지정(1~99명)**을 선택한다. 2명이 대화한 파일은 2명으로 지정한 뒤 문서 아이콘으로 텍스트를 다시 추출하면 된다. 설정은 파일별로 저장되며 녹음·가져온 오디오·영상에 동일하게 적용된다. 추출 대기/진행/취소 중에는 변경할 수 없다. 변경은 다음 추출부터 적용되며 기존 결과를 자동으로 바꾸지는 않는다.

**설정 → 화자 구분 엔진**에서 기존 sherpa 또는 NVIDIA Nemotron 3를 선택할 수 있다. 선택은 앱 재시작 후에도 유지된다. Nemotron 3는 로컬 NeMo-Speech.cpp Sortformer 추론을 실행하며 최대 8명이다. 9명 이상을 직접 지정한 파일은 Whisper 실행 전에 거부한다. 큰 회의에는 기존 엔진을 선택한다. 음성 파일은 기기 밖으로 전송되지 않는다. 모델과 네이티브 소스의 고정 버전은 `ModelCatalog`와 `app/src/main/jni/nemo_speech/PIN.md`에 기록했다.
Nemotron에서 1~8명을 직접 지정해도 모델이 실제 화자 수를 자동 감지한다. 지정값은 8명 초과 여부를 검사하는 데만 사용한다.

자동 감지는 녹음 음성에서 인원수를 추정한다. 직접 지정은 해당 인원수를 기준으로 화자를 묶으며, 1명은 모두 A로 표시한다. 화자 이름 A·B·C는 파일별 임시 구분이고 인물의 실제 신원을 뜻하지 않는다. 음성이 부족하면 지정 인원보다 적게 검출될 수 있고, 겹친 발화나 아주 짧은 말은 잘못 배정될 수 있다. 짧은 음성에도 지정 인원이 적용되도록 화자 모델의 단일 구간 처리 경로를 보완했다.

앱은 Android `MediaExtractor`/`MediaCodec`으로 오디오·영상 음성 트랙을 처리한다. 지원 형식은 기기 코덱에 따라 다르다. Whisper JNI 소스는 whisper.cpp 1.9.4 기반이다. [Whisper 모델](https://huggingface.co/ggerganov/whisper.cpp), [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [pyannote segmentation](https://huggingface.co/pyannote/segmentation-3.0), [3D-Speaker](https://github.com/modelscope/3D-Speaker)의 라이선스·배포 조건은 공개 배포 전에 확인해야 한다.

## 오프라인 회의록

음성 파일의 텍스트 추출이 끝나면 **전체 텍스트**와 **문서화** 탭을 사용할 수 있다. 문서화는 주제 한 줄, 발화 순서에 따른 논의, 원문에서 확인한 결정과 할 일을 만든다. 각 항목에 원문 근거 문구를 표시한다. 녹음과 가져온 파일의 원문·문서는 앱 내부 저장소에 저장된다. 재추출이 실패하거나 취소되면 이전 원문을 보존하며, 새 추출이 성공하면 이전 문서를 stale로 표시한다. 문서화 실패·취소·출력 토큰 한도 도달 때에는 기존 문서를 덮어쓰지 않는다.

문서화는 기기 안에서만 실행되며 녹취문을 외부 서버로 보내지 않는다. Whisper 작업이 모두 끝나고 모델이 해제된 뒤 LLM을 로드한다. 문서화 동안 새 음성 인식은 대기하지만 녹음은 계속할 수 있다. 원문이 12,800자를 넘으면 길이 제한 오류를 보여주며 일부만 조용히 처리하지 않는다. 그 이하 원문은 최대 1,600자 구간으로 나누어 순서대로 처리한다.

로컬 모델의 분류와 줄 근거 선택에는 오류가 있을 수 있다. 문서를 공유하거나 결정·할 일을 실행하기 전에 전체 텍스트와 표시된 원문 근거를 직접 확인한다. Qwen3 0.6B는 초기 실험에서 주제 및 분류 품질이 부족해 Qwen2.5 1.5B Instruct 양자화 모델로 교체했다.

### Qwen 모델 설치

Qwen도 첫 실행 설정에서 자동 다운로드한다. 기존 **설정 → GGUF 파일 가져오기** 기능은 계속 사용할 수 있다. APK에는 Whisper와 Qwen을 포함한 모델 바이너리가 없다.

- 다운로드: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/dd26da440ef0330c47919d1ecae0966d24022222/qwen2.5-1.5b-instruct-q4_k_m.gguf
- 정확한 크기: 1,117,320,736바이트 (약 1.12 GB)
- SHA-256: `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`
- 모델 라이선스: Apache-2.0

앱은 SAF로 가져온 파일을 임시 파일에 복사하고 크기와 SHA-256을 확인한 후 같은 디렉터리에서 원자적으로 교체한다. 검증에 실패하면 기존 정상 모델을 유지한다. 시작할 때 설치된 모든 모델을 백그라운드에서 다시 검증한다. 가져오기에는 모델 파일 외에 설치 중 임시 파일 약 1.12 GB가 추가로 필요하다.

### 네이티브 런타임

`app/src/main/jni/llamacpp`는 MIT 라이선스의 공식 llama.cpp 태그 `b9878`(커밋 `2da6686`)에서 루트 CMake, `cmake/`, `src/`, `include/`, `ggml/`, LICENSE만 포함한다. 원본 tarball: https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/b9878 , SHA-256 `ecae095aebba0ed1fb6e9437d66df57a8f19e8a9979ee0ec7992455b226fcb37`. 상세 pin은 `app/src/main/jni/llamacpp/PIN.md`에 기록했다. 앱 빌드는 Android SDK CMake 3.22.1, NDK 28.2.13676358을 사용하여 CPU 전용 `arm64-v8a`와 `x86_64` 라이브러리를 만든다. 자체 JNI adapter는 모델 로드/추론 중단 callback과 UTF-16↔표준 UTF-8 변환을 제공한다. Whisper의 정적 ggml 심볼은 `--exclude-libs,ALL`로 노출을 막고, llama의 ggml은 별도 공유 라이브러리로 묶는다.

Nemotron은 NVIDIA NeMo-Speech.cpp의 Sortformer 추론 코드와 해당 프로젝트가 고정한 ggml을 별도 `libnemotron_diar.so`에 정적으로 묶는다. `arm64-v8a`와 `x86_64`를 빌드하며, 모델은 앱 저장소에 다운로드한 GGUF 파일로 로드한다. 전체 ASR 빌드에 필요한 SentencePiece는 화자 구분 전용 빌드에 포함하지 않는다. 상세 커밋·라이선스는 `app/src/main/jni/nemo_speech/PIN.md`에 있다.

### 선택 실행 테스트

일반 Android 테스트에서는 요약 모델 추론이 자동으로 실행되지 않는다. 앱에서 모델을 내려받아 내부 `no_backup/models/qwen2.5-1.5b-instruct-q4_k_m.gguf`에 설치한 후 다음을 명시 실행할 수 있다. 이 프로젝트에서는 `connectedDebugAndroidTest` 실행 때 앱 데이터가 지워진 이력이 있으므로, 설치 모델을 유지할 검증에는 APK를 `adb install -r`로 설치하고 instrumentation을 직접 실행한다.

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.app.speechsummary.LocalLlamaSmokeTest -e llmSmoke true com.app.speechsummary.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.app.speechsummary.MeetingDocumenterIntegrationTest -e llmDocumentSmoke true com.app.speechsummary.test/androidx.test.runner.AndroidJUnitRunner
```

첫 테스트는 실제 한국어 추론과 한글·이모지 JNI 왕복을, 두 번째는 합성 한국어 회의의 문서화와 native abort를 확인한다. 원문·문서 저장, 재추출 stale, 손상 파일 격리 테스트는 일반 Android 테스트에 포함된다.

모델을 설치한 에뮬레이터에서는 `adb shell am instrument -w -e class com.app.speechsummary.NemotronInferenceTest com.app.speechsummary.test/androidx.test.runner.AndroidJUnitRunner`로 로컬 Nemotron GGUF 추론을, `WhisperIntegrationTest`로 base Q5_1 모델 로딩과 음성 인식을 각각 확인할 수 있다. 실제 휴대폰의 속도나 한국어 회의 정확도는 별도로 측정해야 한다.
