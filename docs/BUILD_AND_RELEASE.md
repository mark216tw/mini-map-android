# 建置與測試發行

## 環境

- JDK 17、Android SDK Platform 35、Android Studio 或 Android SDK 命令列工具。
- 使用專案內的 Gradle Wrapper；不需在系統安裝全域 Gradle。
- 專案根目錄的 `local.properties`、建置輸出及簽署金鑰均不納入 Git。

Windows 使用 `gradlew.bat`；macOS／Linux 使用 `./gradlew`。以下以 Windows 為例：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assemblePrerelease
```

## 建置變體

| Build Type | 版本名稱 | 用途 | 簽署／壓縮 |
| --- | --- | --- | --- |
| `debug` | `1.0.0` | 開發測試 | Android Debug 金鑰；不啟用 R8 |
| `prerelease` | `1.0.0-prerelease` | 對外測試的 Pre-release | Android Debug 金鑰；啟用 R8 程式碼縮減及資源縮減 |
| `release` | `1.0.0` | 預留正式發行變體 | 尚未設定正式發行簽署 |

`prerelease` 設定於 `app/build.gradle.kts`，沿用 release 的預設 ProGuard 設定，再開啟 `isMinifyEnabled` 與 `isShrinkResources`，指定 `signingConfigs.debug`。APK 產物：

```text
app/build/outputs/apk/prerelease/app-prerelease.apk
```

## 驗證 APK

檢查檔案、版本名稱、簽章及 R8 對應檔（請將 `<SDK>` 與 `<版本>` 換成已安裝路徑）：

```powershell
& "<SDK>\build-tools\<版本>\aapt.exe" dump badging "app\build\outputs\apk\prerelease\app-prerelease.apk"
& "<SDK>\build-tools\<版本>\apksigner.bat" verify --verbose --print-certs "app\build\outputs\apk\prerelease\app-prerelease.apk"
```

R8 產生的對應檔位於 `app/build/outputs/mapping/prerelease/mapping.txt`；Gradle 的 `:app:assemblePrerelease` 完成代表已執行壓縮建置。Android Debug 簽章是本機測試用金鑰，**不得作為正式版簽章**。不同電腦上的 Debug 金鑰可能不同；若 APK 無法覆蓋安裝，請先匯出個人地標備份，再處理既有安裝。

## GitHub Pre-release

版本標籤使用 `v1.0.0-prerelease`。在已推送原始碼後建立 GitHub Release，勾選 **Set as a pre-release**，將 `app-prerelease.apk` 上傳為 Release 資產，說明標明「Pre-release 版本」。APK 放在 Release 資產，不提交至 Git 原始碼。公開發布時仍須保留地圖／搜尋來源署名並遵守服務使用政策。
