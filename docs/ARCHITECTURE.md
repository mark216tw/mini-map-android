# 系統架構與技術文件

## 技術組成

| 元件 | 實作 | 用途 |
| --- | --- | --- |
| Android UI | Kotlin、Jetpack Compose、Material 3 | 地圖操作介面、清單與對話框 |
| 地圖 | osmdroid、OpenStreetMap 線上圖磚 | 地圖顯示、拖曳、縮放與標記 |
| 定位 | Android `LocationManager` | 按需取得單次位置 |
| 線上地名搜尋 | Nominatim HTTPS API、`HttpURLConnection` | 送出式地名查詢，最多五筆結果 |
| 本機資料 | Room（SQLite）、Flow | 個人地標持久化及列表更新 |
| 本機偏好 | SharedPreferences | 上次地圖視角與首次提示狀態 |
| 檔案交換 | Android Storage Access Framework、`org.json` | GeoJSON 匯入與匯出 |

建置基於 Gradle、Android Gradle Plugin、JDK 17；`compileSdk`、`targetSdk` 為 35，最低 API 為 26。所有請求均由裝置直接發出，專案不包含自建後端。

## 程式結構

```text
app/src/main/java/com/example/minimap/
├── MainActivity.kt       UI、地圖生命週期、權限與檔案選取器
├── OnlineServices.kt    線上搜尋、請求節流與單次定位
├── LocationShare.kt     分享時間、座標及度分秒 Google 地圖網址格式
├── Places.kt            Room Place、DAO 與資料庫
└── GeoJson.kt           GeoJSON 編碼及匯入驗證
app/src/main/res/         圖示、主題、APP 名稱及自適應圖示
app/src/test/             GeoJSON 單元測試
```

## 資料與事件流

1. `MainActivity` 建立 `MapView`，由 osmdroid 載入可見範圍內的地圖圖磚。`MapCanvas` 在 Compose `AndroidView` 中顯示地圖、管理標記與地圖生命週期。
2. `PlaceDao.observeAll()` 以 Flow 發送地標資料；Compose 更新標記及「我的地標」清單。本機清單搜尋只篩選已載入的地標，不呼叫線上 API。
3. 長按地圖或選取線上搜尋結果時，先建立待儲存的地標；儲存後由 Room 資料庫發出更新。點地圖個人標記只顯示資訊，點編輯才修改。
4. 分享按鈕在取得定位權限後重新請求單次定位，`LocationShare` 以裝置當地時間產生三行文字；`Intent.ACTION_SEND` 交由系統分享選單處理，不把舊定位或地圖中心當成目前位置。
5. Nominatim 搜尋僅在使用者送出時執行，兩次請求至少相隔約 1.1 秒；當次執行期間快取最多 20 組查詢。請求、匯入、匯出與 Room 作業在 IO 協程處理。
6. 地圖視角於畫面暫停／結束時存至 SharedPreferences；下次啟動驗證座標與縮放後還原。

## 網路、權限及儲存

Manifest 宣告 `INTERNET`、`ACCESS_NETWORK_STATE`、粗略與精確定位權限。只有按定位或分享按鈕才要求定位權限；檔案透過 Android 系統選取器操作，未要求一般檔案系統讀寫權限。地圖使用 OpenStreetMap 公用圖磚；查詢使用 Nominatim 公用 API。地標留在 APP 私有資料庫；GeoJSON 只在使用者主動選擇匯入／匯出時經檔案選取器讀寫。

APP 本身不提供離線地圖、帳號同步或離線地名查詢。不同手機系統對系統列透明度、定位來源和背景網路有不同處理，應在目標裝置實測。
