# ColorDuel — Polish giao diện và audio / 08-10-2026

Sửa trực tiếp checkout ổ E trên nhánh `redesign/neon-arcade-arena`. Không tạo checkout ổ C, không commit/push tự động, giữ thay đổi chưa commit. Snapshot lúc bắt đầu ở `build/polish-baseline`; artifact hiện tại ở `build/ui-polish`.

## Đánh giá và thay đổi giao diện

Audit nhận thấy form bên phải bị kéo cao, CTA đăng ký chìm, header sảnh chiếm nhiều chỗ, bảng đối thủ/log giống công cụ quản lý và CTA sau trận thiếu phân cấp.

- Đăng nhập/đăng ký: form có chiều cao giới hạn, căn giữa theo chiều dọc; spacing gọn hơn; Đăng ký thành CTA secondary viền cyan rõ ràng.
- Sảnh: gộp chỉ số hồ sơ vào header thấp hơn. Đối thủ thành thẻ gồm avatar, tên, tài khoản, điểm/thắng/số trận và trạng thái; thẻ đã chọn có nền cyan và chấm chọn. Giữ tìm kiếm, chọn, double-click mời; đưa các chức năng sắp xếp vào menu riêng. Sidebar đối thủ gọn hơn, thông báo thành panel có khung; lời mời vẫn ghim trên cùng.
- Thi đấu: giới hạn chiều cao bàn ở màn lớn, tăng kích thước rack/orb; Gửi dự đoán lớn hơn, có gradient/viền sáng/chevron. Combat Log là feed gồm team color, sáu orb, điểm đúng và trạng thái. Vùng rỗng có hướng dẫn chờ lượt đoán. Thoát phòng tách bên trái; Trở về sảnh và Chơi lại bên phải. CTA giữ đủ chữ ở ba kích thước.
- Kết quả: glow, vòng năng lượng, badge MATCH COMPLETE, summary điểm/lượt, orb chiến thắng; confetti bắn từ hai bên, tồn tại 2.8 giây. Defeat dùng màu pink và thông báo rõ đối thủ đã giải mã. Không thêm dữ liệu giả vào summary.
- Giữ tất cả phím tắt, giảm hiệu ứng, số màu, timer 30 giây, scoring, rematch và giao thức hiện tại. Bảng/model dữ liệu vẫn nguyên dạng để các handler mạng dùng như cũ.

## Âm thanh

Thêm 13 cue và một ambient loop. Ba tùy chọn Âm thanh / Nhạc nền / Giảm hiệu ứng nằm trên thanh đầu màn, đồng bộ giữa các màn của cùng client. Hiệu ứng mặc định bật, nhạc mặc định tắt. Audio worker riêng, cache Clip, queue giới hạn và ưu tiên cue; lỗi thiết bị/asset không chặn UI. Chi tiết kiến trúc, toàn bộ cue và cách tái tạo WAV: [AUDIO.md](AUDIO.md).

## File sửa/thêm trong lần polish này

| File | Thay đổi |
| --- | --- |
| src/main/java/vn/ptit/colorgame/GameClient.java | Bố cục, settings và hook cue qua sự kiện UI/server hiện có |
| src/main/java/vn/ptit/colorgame/GameTheme.java | CTA, rack, form responsive, result/glow/confetti, trạng thái feed rỗng |
| src/main/java/vn/ptit/colorgame/LobbyStyle.java | Roster/feed renderer, card và header |
| src/main/java/vn/ptit/colorgame/AudioManager.java | Thêm quản lý Clip/cache/worker/settings/cleanup |
| src/main/resources/audio/*.wav | Thêm 14 WAV gốc, đóng gói trong JAR |
| tools/generate_audio.py | Thêm generator WAV tái tạo được, không dependency ngoài |
| src/test/java/vn/ptit/colorgame/AudioManagerTest.java | Thêm kiểm tra asset, lỗi mixer/asset, giới hạn cue, EDT và cleanup |
| src/test/java/vn/ptit/colorgame/ClientAudioTest.java | Thêm kiểm tra settings và cue qua handler client thật |
| src/test/java/vn/ptit/colorgame/UiSmokeTest.java | Giữ test cũ, thêm kiểm tra settings và chữ CTA không bị rút gọn |
| test.bat, test.sh | Thêm hai test audio vào chuỗi hiện có |
| README.md, docs/DESIGN.md | Cập nhật UI hiện tại và đường dẫn báo cáo |
| docs/AUDIO.md, docs/POLISH_AUDIO_REPORT.md | Thêm tài liệu audio và kết quả thực tế |
| .hallmark/preflight.json, .hallmark/log.json | Ghi audit/review và kết quả lần polish |
| dist/ColorDuel-30s.jar | Cập nhật bản build Java 17, đủ WAV |

Không sửa GameServer, Rules, Store, MySqlStore, Passwords, NetworkClient, Wire hoặc Main trong lần này. 8/8 hash nguồn backend khớp snapshot lúc bắt đầu. Không sửa cấu hình MySQL hoặc dữ liệu người chơi. Các file dirty khác trong Git có sẵn từ trước, không bị ghi đè.

## Kết quả đã chạy

Biên dịch bằng JDK 24 `--release 17`, class version 61. Test chạy từ gốc dự án trên Windows, dùng thư mục dữ liệu tạm `build/test-data`. Các test UI/audio/TCP và probe cuối dùng JAR mới làm classpath ưu tiên. Test tích hợp server và MySQL dùng classes đã biên dịch cùng nguồn; nguồn backend được kiểm tra hash không đổi.

| Kiểm thử | Kết quả thực tế |
| --- | --- |
| IntegrationTest | 57 kiểm tra qua; bao gồm timeout đủ 30 giây thật và đồng bộ hai client |
| MySqlIntegrationTest | 9 kiểm tra qua; database UUID `color_duel_test_…`, dọn trong finally |
| GuiNetworkTest | Qua: ba client TCP localhost, đăng ký, đăng nhập lại, mời/hủy/từ chối/chấp nhận, thắng/thua, log, rematch |
| UiSmokeTest | Qua: login/register/lobby/invitation/ranking/history/game/Victory/Defeat ở 1200×840, 1040×680, 800×640, giảm hiệu ứng, contrast, phím tắt, clipping, settings, toàn bộ chữ CTA |
| AudioManagerTest | 74 kiểm tra qua bằng thiết bị giả lập, không phụ thuộc mixer |
| ClientAudioTest | Qua: bốn màn đồng bộ tùy chọn; mute vẫn chơi được; cue đúng sự kiện, không lặp TICK/RESULT; cleanup |
| AudioManagerTest --device | 76 kiểm tra qua; máy mở 14/14 Clip, không có clip unavailable; frame position tiến, ambient loop chạy và tắt được |
| AnimationPerformanceTest | Qua headless 1200×840 và cửa sổ JFrame thật 800×640; giảm hiệu ứng và cleanup timer |

Log kiểm thử nằm trong `build/ui-polish` (ví dụ `ui-test.log`, `audio-test.log`, `gui-network-test.log`). Ảnh/log trong build là artifact cục bộ và bị Git ignore; source, WAV, tài liệu và JAR trong dist có thể đồng bộ lên Git. Lần chạy MySqlIntegrationTest đầu thiếu argument/classpath đã được sửa khi chạy lại; kết quả cuối 9/9 dùng `data/mysql.properties` và driver `dist/lib/mysql-connector-j-8.3.0.jar`. Không đổi code để né lỗi.

Số đo sau 12 frame warmup, 78 frame đo trên máy hiện tại (không cam kết FPS máy khác):

```text
mode=native size=800x640 frames=78 paint_median_ms=12.15 paint_p95_ms=14.27 edt_queue_p95_ms=26.15 orb_cache=18
mode=headless size=1200x840 frames=78 paint_median_ms=19.67 paint_p95_ms=24.31 edt_queue_p95_ms=0.12 orb_cache=18
```

Clip đã được mở/phát qua thiết bị thật; chưa nghe bằng tai để đánh giá timbre và âm lượng so với loa/tai nghe của người chơi. TCP được thử trên localhost với ba client; lần này không test lại kết nối giữa các máy LAN vật lý. Hai script test đã cập nhật; các lệnh biên dịch/chạy test tương đương được thực thi trực tiếp bằng JDK, không chạy pause tương tác của test.bat.

## Ảnh và JAR

`build/ui-polish` có ảnh Swing render thực của mọi màn/ba kích thước; `game-native-800x640.png` in component từ cửa sổ Swing đang hiển thị, không chụp ứng dụng khác. Fixture QA trong ảnh không phải tài khoản người dùng. `compare-login.png`, `compare-lobby.png`, `compare-game.png`, `compare-result.png` ghép ảnh baseline của lần này với bản polish.

- [Client đang chạy ở 800×640](../build/ui-polish/game-native-800x640.png)
- [Thi đấu 1200×840](../build/ui-polish/game.png)
- [Sảnh 800×640](../build/ui-polish/lobby-compact.png)
- [Victory 800×640](../build/ui-polish/result-compact.png)
- [Trước/sau sảnh](../build/ui-polish/compare-lobby.png)
- [JAR cập nhật](../dist/ColorDuel-30s.jar)

JAR build và dist có SHA-256 giống nhau, chứa 14 WAV và class AudioManager:

```text
a1205c42e1fb9dd172bf9dee0ade4a2067675eb6a9543a6301784974ecd29808
```

Mở lại `run-client.bat` sau khi đóng client cũ để dùng JAR mới. Trên máy khác tải đầy đủ dự án từ Git rồi mở script như trước; WAV đi cùng JAR. Nếu tự build, script đã copy toàn bộ resources. Không cần cài thêm thư viện audio hay thay thông tin kết nối database.

## Review theo Hallmark

Giữ theme Neon Arcade Arena và token chung; dùng cấu trúc native workbench, đối đầu HUD và feed lượt chơi, không thêm hero/feature/footer kiểu website. Các màn phục vụ thao tác thật, accent phân vai cyan cho hành động/người chơi, pink cho đối thủ/Defeat, gold cho Victory. Đọc ảnh thực, kiểm tra chữ/viền/tương phản, chọn/lời mời/rỗng/muted/reduced motion và CTA sau trận. Không tuyên bố gate CSS/DOM/SEO hoặc 58/58 web gates. Bố cục desktop rộng vẫn giữ khoảng nền đấu trường phía dưới bàn để tránh kéo giãn orb; history giữ chiều cao để xem nhiều lượt.
