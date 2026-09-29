# Nemotron native source pin

- NVIDIA/NeMo-Speech.cpp: `97a15afa5caa9bce5baaa86c1184103877af4101`, Apache-2.0. Selected `src/asr/diar`, encoder, frontend, common, and runtime files plus public headers were copied unchanged. The selected files were SHA-256 compared with this exact commit (zero mismatches).
- Source archive SHA-256: `f1fdefc70fc810f70f5cdf86eb31f27a990005506cb9c9938941b53bda53677c`.
- NVIDIA's pinned `ggml` submodule: `c03b4e2bcece5134827881af90242086daf75be5`. Source archive SHA-256: `dd9bc340931eb7d12b3b8fd946cbf545c4430b1594835f36c3ac368a0a085c49`.
- Only the speaker diarization dependency set is compiled. Its ggml is linked statically into `libnemotron_diar.so` with static symbols hidden. The app's Whisper and llama builds retain their own ggml versions.
- Android SDK CMake 3.22.1 and NDK 28.2.13676358 build CPU targets for `arm64-v8a` and `x86_64`. The upstream top-level build requires CMake 3.26 and SentencePiece for the unrelated ASR runtime.
- Runtime model: `nvidia/Nemotron-3-Diarization`, revision `f667ed73aee57d40cc39428eb768b4fd87a0a29e`, `Nemotron-3-Diarization.q8_0.gguf`, 107,012,128 bytes, SHA-256 `08456d9e22cd9a323c0364d98375f3746d6e68507ebb705cd46438c534c7a3a1`, OpenMDW-1.1. Downloaded on demand and not bundled in the APK.
