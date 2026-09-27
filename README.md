# mini地圖

**mini地圖**是一款免費的 Android 世界地圖 APP，以 OpenStreetMap 資料顯示線上地圖，並將個人地標保存在手機上。支援單指移動、雙指縮放、按需定位、分享目前位置、線上地名搜尋、長按新增地標，以及 GeoJSON 匯入／匯出。地標資料和地標清單搜尋可離線使用；地圖與線上地名查詢需要網路。

> 此專案的 `1.0.0-prerelease` 是測試發行版本，並非正式上線版本。APK 使用 R8 壓縮，且以 Android Debug 金鑰簽署；正式發行應另行使用專屬的發行金鑰。

**下載測試版：**[GitHub Pre-release `v1.0.0-prerelease`](https://github.com/mark216tw/mini-map-android/releases/tag/v1.0.0-prerelease)（資產 `app-prerelease.apk`）。

## 開始使用

1. 在 Android Studio 開啟本專案，使用 JDK 17 和 Android SDK 35。
2. 執行 `./gradlew :app:assembleDebug`（Windows：`gradlew.bat :app:assembleDebug`）。
3. 安裝 `app/build/outputs/apk/debug/app-debug.apk` 至 Android 8.0（API 26）以上裝置。

建立測試發行版：`./gradlew :app:assemblePrerelease`，產物位於 `app/build/outputs/apk/prerelease/app-prerelease.apk`。詳細指令與簽署說明請見[建置與發行](docs/BUILD_AND_RELEASE.md)。

## 文件導覽

- [使用指南](docs/USER_GUIDE.md)：地圖、定位、個人地標、匯入匯出及常見狀況。
- [系統架構與技術文件](docs/ARCHITECTURE.md)：模組、資料流、服務、權限及技術選擇。
- [系統設計文件](docs/SYSTEM_DESIGN.md)：畫面、互動、資料模型與功能規則。
- [建置與發行](docs/BUILD_AND_RELEASE.md)：環境、prerelease 設定、驗證方式與發行流程。
- [授權與第三方服務](docs/LICENSE_AND_SERVICES.md)：MIT 授權說明與地圖資料／服務使用原則。
- [MIT License](LICENSE)：本專案程式碼的正式授權條款。

## 資料與授權

地圖資料 © [OpenStreetMap 貢獻者](https://www.openstreetmap.org/copyright)。地圖圖磚來自 OpenStreetMap 公用服務；線上地名搜尋來自 Nominatim。請遵守[圖磚使用政策](https://operations.osmfoundation.org/policies/tiles/)與[Nominatim 使用政策](https://operations.osmfoundation.org/policies/nominatim/)。本 APP 不提供離線地圖下載；公用圖磚不得用於批次預先下載。本專案**程式碼**採用 [MIT License](LICENSE)，不改變 OpenStreetMap 資料及其他第三方元件各自的授權。
