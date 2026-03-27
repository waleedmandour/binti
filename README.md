# 🚗 Binti (بنتي) - Egyptian Arabic Voice Assistant for BYD DiLink

<div align="center">
  
  **Egyptian Arabic Voice Assistant | BYD DiLink Integration | HMS Optimized**
  
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Android](https://img.shields.io/badge/Platform-Android_8.0+-green.svg)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Language-Kotlin_1.9-blue.svg)](https://kotlinlang.org)
  [![Huawei HMS](https://img.shields.io/badge/HMS-Enabled-red.svg)](https://developer.huawei.com/consumer/en/hms)
</div>

---

## 📖 About

**Binti** (بنتي - "my daughter") is a professional-grade Egyptian Arabic voice assistant designed specifically for BYD DiLink infotainment systems. It enables drivers to control vehicle functions using natural Egyptian dialect, responding to the wake word **"يا بنتي"** (Ya Binti).

Binti is optimized for the hardware and software environment of BYD vehicles, featuring a landscape-native UI, deep accessibility integration, and offline-first AI processing.

### ✨ Key Features

| Feature | Description | Technology |
|---------|-------------|------------|
| 🎤 **Wake Word Detection** | Optimized for "يا بنتي" with high noise immunity | Vosk Local Grammars / TFLite |
| 🗣️ **Egyptian Arabic ASR** | Robust offline speech-to-text for Egyptian dialect | Vosk MGB2 / Huawei ML Kit |
| 🧠 **Intent Classification** | Local NLU processing for Egyptian colloquialisms | EgyBERT-tiny + Rule Engine |
| 🔊 **Egyptian TTS** | Natural female voice responses with Egyptian tone | Huawei ML Kit / Android TTS |
| 🚙 **DiLink Control** | Control AC, Navigation, Media, and Phone calls | AccessibilityService & DiLink APIs |
| 📱 **Quick Actions Widget**| Home screen widget for one-tap car control | Android AppWidgets |
| 📐 **Car Display UX** | Optimized for 10.1", 12.8", and 15.6" BYD screens | Landscape-First UI |

---

## 🚙 Deep DiLink Integration

Binti goes beyond simple voice commands by integrating deeply with the BYD DiLink ecosystem:

- **AC & Climate**: Control temperature, fan speed, and modes ("يا بنتي، خلي الحرارة ٢٢").
- **Navigation**: Start guidance to home, work, or local POIs ("يا بنتي، وديني أقرب محطة شحن").
- **Media System**: Manage music playback across the system ("يا بنتي، المقطع التالي").
- **Telephony**: Hands-free calling and call management ("يا بنتي، كلمي أحمد").
- **Vehicle Info**: Ask about battery level, range, and temperature ("يا بنتي، البطارية فيها كام؟").
- **System Settings**: Control brightness and volume ("يا بنتي، وطي الإضاءة شوية").

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     User Voice Input                         │
│                   "يا بنتي، شغلي التكييف"                     │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Wake Word Detector (Vosk/TFLite)                           │
│  Continuous local monitoring for "Ya Binti"                 │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  ASR Processor (Vosk / Huawei ML Kit)                       │
│  Offline-first Arabic Speech Recognition                    │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Intent Classifier (NLU Engine)                             │
│  Egyptian Dialect → Structured Vehicle Commands             │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  DiLink Command Executor                                    │
│  UI Automation & System API Interfacing                     │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Egyptian TTS Engine                                        │
│  Localized feedback: "عنيا حاضر، شغلتلك التكييف"             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🚀 Installation & Permissions

Binti requires specific permissions to function as a full car assistant:

1. **Microphone**: For wake word detection and command listening.
2. **Location**: For navigation and finding nearby charging stations.
3. **Phone & Contacts**: For managing calls through the car's hands-free system.
4. **Accessibility Service**: **(Crucial)** Required for Binti to "press buttons" on the DiLink UI.
5. **Display Over Apps**: For the voice interaction overlay.
6. **Write Settings**: To manage screen brightness and system volume.

---

## 💬 Sample Commands

- *يا بنتي، شغلي التكييف* (Turn on AC)
- *يا بنتي، خدينا للبيت* (Take us home)
- *يا بنتي، أقرب محطة شحن* (Nearest charging station)
- *يا بنتي، الساعة كام دلوقتي؟* (What time is it?)
- *يا بنتي، وطي الصوت شوية* (Lower the volume)
- *يا بنتي، البطارية فيها كام؟* (Check battery status)

---

## 📄 License & Credits

Developed by **Dr. Waleed Mandour**. Licensed under the MIT License.
Optimization for BYD DiLink systems is a core focus of this project.
