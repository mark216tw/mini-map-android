# 授權與第三方服務

## 本專案的 MIT License

mini地圖的自有程式碼與專案文件採用 [MIT License](../LICENSE)，著作權聲明為 `Copyright (c) 2026 mark216tw`。MIT 允許使用、修改、散布與再授權，但散布副本時須保留原始著作權與授權聲明，且軟體依「現狀」提供、不提供保證。以根目錄的**英文 LICENSE 正式條文**為準；本段是繁體中文摘要，不取代授權全文。

MIT 授權**不涵蓋** OpenStreetMap 的資料、地圖服務、Nominatim 服務及第三方函式庫；這些來源各有其條款。

## 地圖與搜尋

- 地圖資料 © [OpenStreetMap 貢獻者](https://www.openstreetmap.org/copyright)，依 OpenStreetMap 資料授權條件使用，APP 地圖畫面持續顯示來源署名。
- 地圖圖磚使用 OpenStreetMap 公用圖磚服務。請遵守[官方圖磚使用政策](https://operations.osmfoundation.org/policies/tiles/)：維持明確 User-Agent、合理快取、可見署名，**不得批次預先下載或製作離線地圖包**。
- 地名查詢使用 [Nominatim 公用服務](https://operations.osmfoundation.org/policies/nominatim/)；APP 僅在使用者按下搜尋時發出請求，加入速率限制與短期記憶體快取。公開服務有用量限制，不保證永久可用。
- 使用的 AndroidX、Room、Compose、osmdroid 與其他 Gradle 相依套件遵循其各自的授權。若需重新散布第三方元件，請檢查該元件的授權聲明與相依清單。

## 個人資料

個人地標儲存在手機上的 APP 私有 Room 資料庫，僅在使用者操作時匯入或匯出；地圖視角與首次提示狀態以 SharedPreferences 儲存。按下定位或分享按鈕才會要求定位權限並取得一次位置；分享文字只會交給使用者選擇的系統分享目標。輸入線上地點查詢時，查詢內容會送至 Nominatim。地圖載入時裝置會向圖磚服務請求圖磚。APP 目前沒有帳號或自行實作的雲端同步機制；Manifest 允許 Android 系統備份，是否執行系統備份依裝置與使用者設定而定。
