# ShieldFlix – xem torrent kiểu Netflix trên NVIDIA Shield

App Android TV viết cho Shield: duyệt phim như Netflix, bấm là phát ngay (vừa tải vừa xem), tự chọn bản nhiều người chia sẻ (seed) nhất và tự gắn phụ đề tiếng Việt.

## Tính năng

- **Giao diện kiểu Netflix cho remote**: hero ảnh nền đổi theo phim đang chọn, các hàng phim (thịnh hành, phim lẻ, series, phim Hàn, phim Việt, theo thể loại…), hàng *Tiếp tục xem* có thanh tiến độ, trang chi tiết có mùa/tập, tìm kiếm tiếng Việt.
- **Thông tin phim tiếng Việt** từ TMDB (tên, mô tả, poster, diễn viên).
- **Phát torrent trực tiếp**: engine libtorrent, tải ưu tiên đúng đoạn đang xem, tua tới đâu tải tới đó. Màn hình chờ hiện % tải trước, số kết nối, số seed, tốc độ.
- **Tự chọn nguồn tốt nhất**: xếp theo seed, chất lượng ưu tiên (1080p/2160p/720p), ưu tiên bản Vietsub, loại bản quay rạp CAM/TS, tránh bản remux quá nặng. Nguồn không phản hồi thì tự thử nguồn kế tiếp. Có nút *Chọn nguồn* để tự chọn (hiện chất lượng, HDR/DV, HEVC, âm thanh, dung lượng, seed).
- **Phụ đề tiếng Việt tự động**: ưu tiên phụ đề Việt có sẵn trong torrent, sau đó tìm trên Subdl và OpenSubtitles, chọn bản khớp tên phát hành nhất; tự chuyển mã Windows-1258/UTF-16 về UTF-8 (hết lỗi font). Bấm **▲** khi xem để đổi phụ đề, chỉnh lệch thời gian ±0,5 giây, đổi cỡ chữ.
- **Series**: chọn mùa/tập, hết tập tự đếm ngược sang tập sau (dùng lại gói cả mùa nếu có).
- **Cấu hình bằng điện thoại**: mở `http://<IP Shield>:8090` để dán khoá API, nguồn, và **dán magnet để phát ngay trên TV**. Có mã PIN.
- Mở magnet link từ app khác trên Shield.

## Phím remote khi xem

| Phím | Tác dụng |
|---|---|
| OK | Tạm dừng / tiếp tục |
| ◀ ▶ | Tua lùi / tới 10 giây |
| ▲ | Bảng phụ đề tiếng Việt (đổi bản, lệch thời gian, cỡ chữ) |
| OK rồi chọn biểu tượng phụ đề/bánh răng | Phụ đề và âm thanh có sẵn trong file |
| Quay lại | Ẩn thanh điều khiển / thoát |

---

## 1. Build ra file APK

Môi trường của Claude bị chặn kho thư viện Android nên chưa build sẵn. Chọn một trong hai cách.

### Cách A – Android Studio trên Mac (khoảng 15 phút lần đầu)

1. Cài Android Studio: tải tại developer.android.com/studio (bản Apple Silicon) hoặc `brew install --cask android-studio`. Mở lên, chọn cài đặt mặc định (nó tự tải Android SDK).
2. **File → Open…** chọn thư mục `ShieldFlix`. Chờ thanh dưới cùng báo *Gradle sync finished* (lần đầu tải thư viện vài phút).
3. Menu **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. Xong bấm *locate* trong thông báo, file nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Hoặc trong Terminal:
```bash
cd ShieldFlix
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Nếu build báo lỗi, copy nguyên phần lỗi gửi Claude để sửa.

### Cách B – GitHub tự build (không cần cài gì)

1. Tạo repo trên GitHub (private cũng được), đẩy cả thư mục `ShieldFlix` lên.
2. Vào tab **Actions**, workflow *Build APK* tự chạy (có sẵn trong `.github/workflows/build.yml`).
3. Chạy xong, tải file trong mục **Artifacts → ShieldFlix-apk**.

## 2. Cài lên Shield

1. Trên Shield: **Cài đặt → Tuỳ chọn thiết bị → Giới thiệu**, bấm vào **Bản dựng** 7 lần để bật chế độ nhà phát triển.
2. **Cài đặt → Tuỳ chọn thiết bị → Tuỳ chọn nhà phát triển**, bật **Gỡ lỗi qua mạng** (Network debugging). Ghi lại IP của Shield (trong **Mạng & Internet**).
3. Trên Mac:
   ```bash
   brew install --cask android-platform-tools   # cài adb
   adb connect 192.168.1.xx:5555                # IP của Shield; trên TV bấm "Cho phép"
   adb install -r app-release.apk               # hoặc app-debug.apk
   ```
4. App **ShieldFlix** hiện trên màn hình chính của Shield.

## 3. Cấu hình (làm một lần)

Mở app, màn hình đầu tiên hiện địa chỉ dạng `http://192.168.1.xx:8090` và mã PIN. Mở địa chỉ đó trên điện thoại/Mac cùng Wi-Fi rồi điền:

1. **Khoá TMDB (bắt buộc, miễn phí)**: đăng ký tài khoản themoviedb.org → *Settings → API* → tạo API key → dán *API Key* hoặc *API Read Access Token*.
2. **Nguồn torrent (Torznab)**: app đọc kết quả từ Jackett hoặc Prowlarr do anh tự chạy và tự chọn indexer. Ví dụ Jackett trên Mac:
   ```bash
   brew install jackett
   brew services start jackett      # mở http://localhost:9117, thêm indexer anh muốn dùng
   ```
   Trong Jackett, bấm **Copy Torznab Feed** của mục *All* (hoặc từng indexer), thay `localhost` bằng IP của Mac, ví dụ:
   `http://192.168.1.10:9117/api/v2.0/indexers/all/results/torznab/api?apikey=XXXX`
   Mỗi dòng một nguồn. Máy chạy Jackett phải bật khi tìm phim (chỉ cần lúc tìm; lúc xem thì Shield tự tải).
3. **Phụ đề tiếng Việt** (nên có ít nhất một):
   - **Subdl**: tạo tài khoản subdl.com → *Panel → API* → copy key.
   - **OpenSubtitles**: tạo tài khoản opensubtitles.com → *API consumers* → tạo consumer lấy API key; nhập thêm tên đăng nhập/mật khẩu để được nhiều lượt tải mỗi ngày hơn.
4. Nhập mã PIN, bấm **Lưu**. TV tự tải danh sách phim.

Cũng có thể gõ trực tiếp trên TV ở mục **Cài đặt** (cột phải), nhưng gõ bằng remote chậm hơn nhiều.

## 4. Mẹo dùng

- **Mạng**: Shield nên cắm dây LAN hoặc Wi-Fi 5GHz. Phim 1080p cần khoảng 1–2 MB/s ổn định, 4K cần 4–8 MB/s.
- **Chất lượng**: đổi ở **Cài đặt → Chất lượng ưu tiên**. Bản 4K HDR/Dolby Vision phát tốt trên Shield Pro nếu TV hỗ trợ.
- **Âm thanh DTS/TrueHD/Atmos**: Shield chỉ phát được nếu loa/amply nhận passthrough qua HDMI; nếu không có tiếng hoặc báo lỗi, chọn bản có âm thanh AAC/AC3/DD+ trong *Chọn nguồn*.
- **Phụ đề lệch**: bấm ▲, chỉnh *Lệch thời gian* từng 0,5 giây (số dương = chữ hiện muộn hơn). Nếu lệch nhiều, chọn bản phụ đề khác có ghi *khớp bản phát hành*.
- **Bộ nhớ**: mặc định xoá file sau khi xem xong (≥ 92%); xem dở thì giữ để xem tiếp nhanh. Xoá tay ở **Cài đặt → Xoá bộ nhớ đệm torrent**.
- **Phát magnet bất kỳ**: dán vào ô *Phát magnet trên TV* ở trang cấu hình; app tự nhận diện tên phim để tìm phụ đề.

## 5. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| "Chưa cấu hình nguồn Torznab" | Thêm URL Jackett/Prowlarr ở trang cấu hình |
| Tìm nguồn báo lỗi kết nối | Kiểm tra Mac chạy Jackett đang bật, URL dùng IP chứ không phải `localhost` |
| Kẹt ở "Đang lấy thông tin torrent" | Ít người chia sẻ; app tự thử nguồn khác, hoặc vào *Chọn nguồn* chọn bản nhiều seed hơn |
| Đang xem hay dừng để tải | Mạng chậm hoặc ít seed; chọn bản nhẹ hơn (720p/1080p x265) |
| "Không phát được video" | Định dạng hình/tiếng không hợp; bấm *Thử nguồn khác* |
| Không có phụ đề | Kiểm tra khoá Subdl/OpenSubtitles; OpenSubtitles miễn phí giới hạn số lượt tải mỗi ngày |

## Cấu trúc mã nguồn

```
app/src/main/java/vn/son/shieldflix/
  App.kt, MainActivity.kt
  data/      Prefs (cài đặt, tiến độ xem), Tmdb, Torznab (tìm + xếp hạng nguồn), Subtitles (Subdl/OpenSubtitles, mã hoá, lệch giờ)
  torrent/   TorrentEngine (libtorrent4j, ưu tiên piece, tải trước), StreamServer (HTTP Range cho ExoPlayer)
  web/       ConfigServer (trang cấu hình :8090)
  ui/        Home, Detail, Search, Settings, Player (Jetpack Compose + Media3 ExoPlayer)
```

Lưu ý: app là trình phát torrent và đọc nguồn do người dùng tự cấu hình; người dùng tự chịu trách nhiệm về nội dung mình xem theo luật bản quyền.
