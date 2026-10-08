# Thiết kế giao diện Color Duel

Ứng dụng Java Swing giữ nguyên luồng tài khoản, danh sách online, lời mời, lịch sử, thi đấu và giao thức TCP. Mỗi lượt vẫn là 30 giây do server quản lý.

## Audit và hướng thiết kế

Bản giao diện trung tính trước đó có bố cục thích ứng nhưng nền phẳng, bảng và khối màu mang cảm giác ứng dụng quản lý. Quả cầu màu còn phẳng; chuyển sáng/tối không phục vụ trải nghiệm chơi. Theo yêu cầu mới, chuyển sang một đấu trường vũ trụ thống nhất, có chiều sâu và hiệu ứng phản hồi thao tác.

Dùng các nguyên tắc audit, phân cấp, tương phản, spacing và pre-flight của `design-taste-frontend`. Đây là game desktop Swing; các yêu cầu React/CSS/SEO không áp dụng. Người dùng yêu cầu nền game và hiệu ứng nên ánh sáng và chuyển động có chủ đích được ưu tiên. Mức khác biệt 7/10, chuyển động 6/10, mật độ 4/10.

## Hệ thống hình ảnh

- Nền `#080F20`, surface `#101B32`, raised `#1C2C48`, chữ `#F0F5FF`, chữ phụ `#B7C5E0`.
- Nhấn cyan `#80D9FF`; vàng `#FFD380` cho chiến thắng/đếm ngược; đỏ `#FF9AA4` cho lỗi và cảnh báo.
- Dùng ảnh `images/color-arena.png` có sẵn làm nền toàn màn hình, vùng giới thiệu và banner sảnh. Ảnh được đóng gói trong JAR.
- Nền có lớp tối giữ chữ dễ đọc. Các panel dùng gradient tối và viền sáng; không có nút hoặc cấu hình chuyển sáng/tối.
- Font Segoe UI trên Windows, SansSerif trên nền tảng khác; monospace cho điểm, vị trí và đồng hồ. Panel 16px, control 10px, avatar 12px.
- Sáu màu game giữ nguyên ý nghĩa và thứ tự; quả cầu dùng radial gradient, phản sáng và halo.

## Hiệu ứng và vòng đời

- Hạt sáng chậm và quỹ đạo trong vùng ảnh tạo không khí đấu trường.
- Hover/click phản hồi trên nút; đặt/đổi/chọn màu có pulse ngắn. Cập nhật đồng hồ không khởi động lại pulse.
- Lời mời và thẻ đến lượt có viền sáng ngắn. Đồng hồ cảnh báo nhẹ trong 5 giây cuối.
- Thắng trận có pháo giấy 2,8 giây, vẽ trực tiếp trên client và không chặn chuột. Hiệu ứng được xóa khi vào ván mới hoặc rời phòng.
- Một timer Swing chung 33ms chỉ repaint client đang hiển thị. Timer dừng khi mọi client ẩn/đóng hoặc bật Giảm hiệu ứng. Timer hover dừng khi component bị tháo.
- Nền toàn màn hình được cache theo kích thước, tránh scale lại ảnh nền mỗi frame. Hiệu ứng dùng đồng hồ monotonic, không thay đổi dữ liệu hoặc thời hạn do server gửi.

## Bố cục thích ứng và kiểm tra

Đăng nhập ưu tiên biểu mẫu khi nội dung hẹp hơn 870px. Sảnh xếp dọc danh sách/sidebar dưới 870px. Màn chơi đưa lịch sử xuống dưới bàn màu dưới 1090px; đồng hồ tối thiểu 96px, nút thoát/chơi lại nằm ngoài vùng cuộn. Hỗ trợ cửa sổ từ 800x640.

`UiSmokeTest` render đăng nhập/đăng ký/sảnh/bảng xếp hạng/lịch sử/lời mời/thi đấu/kết quả ở 1200x840, 1040x680 và 800x640, kiểm tra trạng thái khi giảm hiệu ứng, giải phóng timer và hiệu ứng chiến thắng. Tương phản chữ chính >= 7:1, chữ phụ/nút/nhấn/lỗi >= 4.5:1, viền input >= 3:1 và số trên màu >= 4.5:1.

`GuiNetworkTest` nối ba client vào server TCP thật, kiểm tra đăng ký, mời/hủy/từ chối/chấp nhận, gửi dự đoán, thắng thua, lịch sử và chơi lại. Ảnh QA ở `build/ui-game`, dữ liệu thử ở `build/test-data`; không dùng tài khoản thật.


## Neon Arcade Arena — Bản redesign 08/10/2026

<!-- Hallmark · pre-emit critique: P5 H4 E4 S5 R4 V4 -->

Phần này cập nhật và thay thế mô tả bố cục/tokens của bản trước ở trên. Triển khai ngay trong checkout ổ E trên nhánh `redesign/neon-arcade-arena`. Đã giữ bản đối chiếu của working tree chưa commit trong `build/neon-baseline`; không reset, stash hoặc tạo checkout khác.

### Hệ thống thiết kế

Hallmark là guideline chính, kết hợp audit và phân cấp của design-taste-frontend. Giữ Java 17, Swing và Java2D. Không thêm framework web hoặc JavaFX. Hệ thống dùng chassis cắt góc, hai sắc đội đối đầu và energy deck; các màn hình dùng cùng component thay vì mỗi màn một theme.

| Vai trò | Token |
| --- | --- |
| Nền / panel / mặt nâng | `#080B18` / `#121A31` / `#1B2846` |
| Chữ / chữ phụ / viền | `#F2F5FF` / `#AAB8D3` / `#6984AD` |
| Hành động chính / đội cyan | `#39E7FF` |
| Đội đối thủ / thất bại | `#FF70B6` |
| Chiều sâu điện xanh / chiến thắng | `#477BFF` / `#FFD380` |

Màu khai báo theo vai trò bằng hằng `final` trong GameTheme; sáu màu game giữ nguyên thứ tự và ý nghĩa. Font display Bahnschrift nếu hỗ trợ tiếng Việt, Segoe UI cho nội dung, monospace cho số và đồng hồ. Không dùng chữ tiêu đề nghiêng. Có fallback cho môi trường khác Windows.

### Bố cục và trải nghiệm

- Thi đấu: hai thẻ người chơi cyan/pink, vòng 30 segment ở giữa; trạng thái YOUR TURN / OPPONENT TURN, vùng sắp xếp sáu orb, bảng màu/phím 1–6, thao tác xóa/bỏ ô/gửi. Chọn hai ô đổi chỗ vẫn dùng cùng luật kiểm tra lượt.
- Màn lớn: bàn đấu trái, combat log phải. Dưới 1050px nội dung: bàn màu ở trên, lịch sử riêng ở dưới; không có outer scroll che nút gửi hoặc nút rời/chơi lại. Các lượt cũ cuộn bên trong log.
- Kết quả: Victory/Defeat là panel riêng trong arena; điểm từ luật hiện có, số lượt từ model. Dãy thắng chỉ hiển thị từ MOVE đã công bố khi SOLVED, không đọc hoặc tiết lộ secret khác. Chơi lại trở về bàn màu, xóa kết quả ván trước.
- Sảnh: thẻ hồ sơ và số liệu thật từ server, danh sách chọn đối thủ, lời mời ghim trên tab. Ở cửa sổ hẹp, danh sách online ưu tiên tên/điểm/trạng thái; thẻ đối thủ giữ đủ thắng/đã chơi.
- Đăng nhập/đăng ký: ảnh đấu trường có sẵn làm poster khởi động; biểu mẫu gọn và thông báo xác thực nằm trong viewport ở kích thước nhỏ.
- Ranking: Top 1 vàng, Top 2 cyan, Top 3 pink. Lịch sử gom thông tin trận/đối thủ/thời gian/mã và thắng-thua/số lượt/lý do thành hai cột; tooltip giữ nội dung đầy đủ.

### Hiệu ứng và hiệu năng

Một timer nền 33ms chỉ repaint root đang hiển thị; dừng khi đóng/ẩn toàn bộ root hoặc bật Giảm hiệu ứng. Hover dừng khi đạt trạng thái; animation chọn/đổi màu có thời hạn và chỉ bắt đầu khi trạng thái đổi. Gửi dự đoán có pulse; thắng có confetti 2,8 giây. Không I/O ảnh hoặc mạng trong timer animation. Clock/expiry vẫn theo deadline server khi giảm hiệu ứng.

Ảnh nền, poster, banner và bàn đấu cache theo kích thước. Orb dùng sprite 2x cache tối đa 60 entry (6 màu × 10 kích thước); mỗi component chỉ giữ ảnh scaled hiện tại. Các Graphics2D tạo ra đều được dispose.

### Kết quả kiểm tra thực tế

Build bằng JDK 24 với `--release 17`; class file version 61. Đã cập nhật `dist/ColorDuel-30s.jar` và so hash với JAR build. Đối chiếu SHA-256: 8/8 file backend giữ nguyên so với lúc bắt đầu, gồm server, rules, store, MySQL, passwords, network client, wire và main. Không chỉnh `data/mysql.properties`; MySQL test tạo và dọn database riêng có prefix `color_duel_test_`.

| Kiểm thử | Kết quả |
| --- | --- |
| IntegrationTest | 59 kiểm tra qua; có timeout 30 giây thực tế |
| MySqlIntegrationTest | 9 kiểm tra qua; database tạm độc lập |
| GuiNetworkTest | Qua với ba client TCP localhost: đăng ký, đăng nhập lại, mời/hủy/từ chối/chấp nhận, gửi đoán, thắng-thua, history và rematch |
| UiSmokeTest | Qua: contrast, phím tắt, giảm hiệu ứng, trạng thái server, bounds/ancestor clipping và render ba kích thước |
| AnimationPerformanceTest | Qua: 90 frame theo timer, cache có giới hạn, giảm hiệu ứng và cleanup; native deck không co dưới 76×96 |

Ảnh ở `build/ui-neon`: các màn chính ở 1200×840, 1040×680, 800×640; ảnh chế độ giảm hiệu ứng; `game-native-800x640.png` render component từ cửa sổ Swing thật; `compare-game.png`, `compare-login.png`, `compare-lobby.png` đối chiếu trước/sau. Dữ liệu trong ảnh là fixture QA, không phải tài khoản thật. Log kiểm thử cùng thư mục.

Số đo trên máy hiện tại (sau 12 frame warmup, 78 frame đo; không cam kết FPS trên máy khác):

```text
mode=native size=800x640 frames=78 paint_median_ms=15.05 paint_p95_ms=22.89 edt_queue_p95_ms=27.14 orb_cache=18
mode=headless size=1200x840 frames=78 paint_median_ms=23.40 paint_p95_ms=30.50 edt_queue_p95_ms=0.18 orb_cache=18
```

Có thể chạy lại sau `test.bat`:

```text
java -Djava.awt.headless=true -Djava.io.tmpdir=build/test-data -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.UiSmokeTest build/ui-neon
java -Djava.awt.headless=true -Djava.io.tmpdir=build/test-data -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.AnimationPerformanceTest build/ui-neon
java -Djava.io.tmpdir=build/test-data -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.AnimationPerformanceTest build/ui-neon --native
java -Djava.io.tmpdir=build/test-data -cp "dist/ColorDuel-30s.jar;build/test-classes;dist/lib/mysql-connector-j-8.3.0.jar" vn.ptit.colorgame.MySqlIntegrationTest data/mysql.properties
```

Hallmark review áp dụng cho native app: đọc ảnh thật, tương phản, typography, semantic tokens, trạng thái tương tác, reduced motion, bố cục và scope preservation. Các gate CSS/DOM/SEO, mobile web 320px và nav/footer marketing không áp dụng vì yêu cầu Java Swing và kích thước desktop là ràng buộc chính. Không tuyên bố 58/58 web gate. Metadata lưu ở `.hallmark/preflight.json` và `.hallmark/log.json`.


## Polish giao diện và âm thanh — 08/10/2026

Form giới hạn chiều cao và căn giữa, CTA đăng ký dùng secondary cyan. Header sảnh gộp metric; roster chuyển thành thẻ có trạng thái/chọn, menu giữ chức năng sắp xếp. Combat Log thành battle feed gồm team, orb, điểm đúng và trạng thái; action/result CTA có đủ chữ ở ba kích thước. Victory/Defeat thêm badge, summary, glow/vòng năng lượng; confetti từ hai bên. Giữ token và native Swing design system của phần Neon Arcade Arena bên trên.

Thêm AudioManager với worker riêng, preload/cache 14 WAV gốc, hai checkbox audio gần Giảm hiệu ứng; các lỗi asset/device chỉ làm im lặng. Nguồn backend 8/8 hash giữ nguyên. Kết quả **lần polish này**: IntegrationTest 57 kiểm tra, MySQL 9, AudioManagerTest 74, probe thiết bị 76; UI/GUI TCP/ClientAudio/performance đều qua. Các con số ở phần redesign trước là ghi chép lần trước; xem [báo cáo lần polish](POLISH_AUDIO_REPORT.md) cho kết quả và số đo mới nhất. Hướng dẫn cue/cấu hình/lifecycle ở [AUDIO.md](AUDIO.md).


### Chỉnh settings và banner theo phản hồi

Chuyển ba checkbox đầu thanh thành một popup Cài đặt chung cho client, có mức âm lượng, preview và feedback trạng thái mixer. Banner Matchmaking cover bitmap toàn chiều rộng, scrim tối liên tục; bỏ giới hạn 420 px bên phải. Nhạc nền dùng loop gốc có arpeggio dải giữa, mặc định 45% khi được bật. Test popup thực ở JFrame 800×640 xác nhận toggle/volume hoạt động; xem phần mới nhất của [AUDIO.md](AUDIO.md#sửa-theo-phản-hồi-về-settings-và-nền-banner) và ảnh build/ui-settings. Không đổi theme/token, game rules hoặc backend. Component-scope Hallmark: giữ system hiện có, kiểm tra default/focus/active/disabled/loading/error/success bằng native controls và test, không đổi macrostructure hoặc ghi lượt theme mới.
