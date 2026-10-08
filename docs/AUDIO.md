# Âm thanh ColorDuel

Bản client Java 17 dùng `javax.sound.sampled.Clip`; mọi WAV có sẵn trong JAR, không tải tài nguyên lúc chơi.

## Bật/tắt trong game

Góc phải thanh trên cùng của đăng nhập, đăng ký, sảnh và thi đấu có nút **Cài đặt**. Bảng dùng chung chứa **Hiệu ứng âm thanh**, **Nhạc nền**, **Giảm hiệu ứng**, hai thanh âm lượng và nút **Thử hiệu ứng / Nghe nhạc nền**. Bấm ngoài bảng hoặc Esc để đóng.

- Hiệu ứng âm thanh mặc định bật. Tắt sẽ dừng các hiệu ứng đang phát và bỏ qua cue mới.
- Nhạc nền mặc định tắt. Bật để nghe nhạc nhẹ ở đăng nhập/đăng ký/sảnh và nhạc thi đấu 120 BPM có kick, bass, synth khi vào phòng. Cả hai loop tám giây; kết quả, trở về sảnh hoặc đăng xuất chuyển về bản nhẹ. Chơi lại chuyển sang bản thi đấu. Bấm Nghe nhạc nền sẽ bật bản của màn hình hiện tại theo mức đang chọn. Tắt Hiệu ứng âm thanh vẫn giữ nhạc nếu Nhạc nền đang bật.
- Bốn màn mở cùng một bảng Cài đặt nên lựa chọn audio dùng chung trong client. Mỗi cửa sổ có lựa chọn riêng; mở lại chương trình dùng mặc định. Giảm hiệu ứng giữ cơ chế cũ và không đổi tùy chọn audio.
- Khi toàn bộ component client bị ẩn, nhạc tạm dừng; khi hiện lại sẽ tiếp tục nếu lựa chọn Nhạc nền còn bật.

## Cue và tài nguyên

Thư mục `src/main/resources/audio`, WAV PCM mono 44.1 kHz, signed 16-bit little-endian.

| WAV | Sự kiện | Độ dài |
| --- | --- | --- |
| click.wav | Nút điều hướng, làm mới, xóa… | 75 ms |
| orb.wav | Chọn một màu hợp lệ | 150 ms |
| swap.wav | Chọn ô, bỏ màu hoặc đổi vị trí qua ô | 170 ms |
| submit.wav | Gửi một dự đoán đủ sáu màu trong lượt | 300 ms |
| invite.wav | Nhận một lời mời mới | 460 ms |
| accept.wav | Chấp nhận lời mời hợp lệ | 340 ms |
| reject.wav | Từ chối lời mời hợp lệ | 300 ms |
| turn.wav | Bắt đầu lượt của bạn | 430 ms |
| warning.wav | Mỗi giây còn lại từ 5 xuống 1, nếu chưa gửi | 130 ms |
| timeout.wav | Server xác nhận hết thời gian | 500 ms |
| victory.wav | Chiến thắng | 1.18 s |
| defeat.wav | Thất bại | 880 ms |
| peer-left.wav | Đối thủ rời hoặc mất kết nối, server kết thúc trận | 400 ms |
| ambient.wav | Đăng nhập, đăng ký, sảnh và kết quả: pad/arpeggio nhẹ | Loop 8 s |
| battle.wav | Thi đấu: nhịp 120 BPM, bass, kick/backbeat và synth nhanh | Loop 8 s |

WAV là âm thanh gốc được tổng hợp bằng `tools/generate_audio.py` (Python chuẩn: math, struct, wave). Không dùng tài nguyên bên thứ ba, sample lấy từ Internet hoặc dịch vụ runtime. Tái tạo từ gốc dự án: `python tools/generate_audio.py`. Python chỉ phục vụ tái tạo asset, không phải dependency để build hoặc chạy Java. Chỉnh generator rồi tạo lại WAV và build để thay timbre/âm lượng/độ dài.

## Vòng đời và hiệu năng

`AudioManager` riêng cho mỗi client. Worker daemon duy nhất preload/cache tối đa 15 clip (13 hiệu ứng và 2 bản nhạc), decode và gọi mixer trên worker. `play`, mute và `close` chỉ enqueue/lưu cờ, không chờ trên EDT. Queue tối đa 32 tác vụ, không có CallerRunsPolicy. Cue cũ/nhấn liên tục bị bỏ, tối đa hai hiệu ứng cùng lúc; click nhường cue quan trọng. Âm lượng mặc định: hiệu ứng 70%, nhạc 45%; MASTER_GAIN khi hỗ trợ dùng 20×log10(mức/100). Mức 0 luôn dừng/bỏ qua cue hoặc loop, kể cả khi mixer không có gain control. Bảng Cài đặt hiển thị trạng thái thực tế sau khi worker xử lý, gồm nhạc đang phát/tắt hoặc thiết bị chưa sẵn sàng.

`MusicScene.LOBBY/BATTLE` chọn một clip đã cache. Worker dừng bản cũ trước khi bắt đầu bản mới, không phát hai bản chồng nhau. Chuyển màn không bật nhạc nếu người dùng đã tắt, đặt âm lượng 0 hoặc client bị ẩn. Gain áp dụng cho cả hai bản; tắt hiệu ứng không dừng nhạc thi đấu. Cùng scene và cập nhật volume không khởi động lại loop.

TURN và TICK cùng lượt không lặp chuông đến lượt; countdown không phát lại khi vẫn trong cùng một giây; RESULT lặp không lặp âm kết quả. Cue không quyết định deadline, điểm hoặc trạng thái trận. Hết giờ chỉ dùng MOVE/TIMEOUT từ server. Khi client đóng, worker dừng/đóng clip rồi kết thúc. Thiếu WAV, WAV lỗi hoặc không có mixer thì cue đó im lặng; game tiếp tục. Không tự retry/load lại trên từng lần bấm.

Môi trường headless và `-Dcolorduel.audio.disabled=true` dùng audio im lặng. Có thể dùng cờ này để chẩn đoán môi trường, không cần bỏ asset hoặc sửa code.

## Kiểm thử

`test.bat` và `test.sh` đã thêm AudioManagerTest, ClientAudioTest và SettingsTest bên cạnh các test hiện có. Chạy thủ công từ gốc dự án sau khi build/test-classes đã biên dịch:

```text
java -Djava.awt.headless=true -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.AudioManagerTest
java -Djava.awt.headless=true -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.ClientAudioTest
java -Djava.awt.headless=true -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.SettingsTest build/ui-settings
java -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.SettingsTest build/ui-settings --native
java -cp "dist/ColorDuel-30s.jar;build/test-classes" vn.ptit.colorgame.AudioManagerTest --device
```

Trên Linux/macOS thay dấu phân cách classpath `;` bằng `:`. Test tự động mặc định dùng thiết bị giả lập, không cần sound card. Probe `--device` là tùy chọn: thông báo số clip mở được; nếu không có mixer thì SKIP phần phần cứng. Test asset vẫn kiểm tra decode PCM, headroom, độ dài và đường nối cả hai bản nhạc. Probe phần cứng kiểm tra frame playback/loop/stop, không thay thế việc nghe bằng tai. Xem kết quả thực tế và ảnh trong [POLISH_AUDIO_REPORT.md](POLISH_AUDIO_REPORT.md).


## Sửa theo phản hồi về settings và nền banner

Bản trước có nhạc nền mặc định tắt, nguồn pad chủ yếu ở dải thấp và gain −20 dB nên khó nghe; kiểm tra phát clip riêng trước đó không chứng minh trải nghiệm tùy chọn trong cửa sổ client. Bản hiện tại thay loop bằng pad/arpeggio dải giữa, đặt nhạc 45%, thêm điều chỉnh âm lượng và nút nghe thử. Không kết luận checkbox bản cũ bị lỗi dựa riêng trên phản hồi; bản mới đã kiểm tra trực tiếp toggle trong cửa sổ Swing thật và trạng thái mixer.

ArenaBanner trước giới hạn bitmap vào 420 px bên phải. Hiện dùng bitmap cover toàn kích thước banner, cache theo kích thước, scrim tối liên tục và viền chassis để giữ chữ dễ đọc. Test so ảnh với panel phẳng ở cả bên trái/giữa/phải để bắt hồi quy giới hạn 420 px.

File sửa trong lượt này: GameClient.java, GameTheme.java, AudioManager.java, ambient.wav, tools/generate_audio.py; AudioManagerTest.java, ClientAudioTest.java, UiSmokeTest.java; test.bat/test.sh, README.md, docs/AUDIO.md, docs/DESIGN.md, .hallmark/preflight.json và dist/ColorDuel-30s.jar. Thêm SettingsTest.java. Không đổi nguồn backend hay cấu hình/dữ liệu MySQL.

Kết quả lần sửa settings/banner (ảnh/log trong build/ui-settings, artifact cục bộ bị Git ignore):

| Kiểm tra | Kết quả |
| --- | --- |
| AudioManagerTest | 79 kiểm tra qua: preload, mute, volume, missing asset/mixer, EDT và cleanup |
| AudioManagerTest --device | 81 kiểm tra qua; thiết bị mở 14/14 clip, frame playback tiến, loop chạy/dừng |
| ClientAudioTest | Qua: một settings owner cho bốn màn, volume 0/resume và các cue game cũ |
| SettingsTest headless/native | Qua: popup hiện thật, controls/caption vừa 340×410, bật/tắt/volume/preview/status và chuyển login→lobby vẫn phát nhạc |
| UiSmokeTest | Qua: toàn bộ màn ở 1200×840, 1040×680 và 800×640, phím tắt/reduced effects/CTA/clipping |
| GuiNetworkTest | Qua: ba client TCP, mời/hủy/từ chối/chấp nhận, trận thắng/thua và rematch |
| IntegrationTest | Log lần này ghi 61 kiểm tra qua, gồm timeout 30 giây thực |
| MySqlIntegrationTest | 9 kiểm tra qua trên database tạm riêng |
| AnimationPerformanceTest native | Qua; cửa sổ 800×640, cache bounded, reduced motion và cleanup |

Đã kiểm tra đường phát và thao tác trên thiết bị thật; chưa nghe bằng tai để đánh giá timbre/âm lượng theo loa người dùng. Ảnh popup thực: [settings-native.png](../build/ui-settings/settings-native.png). Banner: [lobby-compact.png](../build/ui-settings/lobby-compact.png). Nguồn backend 8/8 hash giữ nguyên. JAR build/dist khớp hash và WAV trong JAR khớp source:

```text
92e25cf320f5ecc7b5a1214fd7b37d7d458883b66f5630910ed2e3336f0a4f9f
```


## Tách nhạc sảnh và thi đấu — 08/10/2026

Giữ ambient.wav ngoài trận; thêm battle.wav gốc 120 BPM với bass, kick/backbeat và synth nhanh. Worker preload hai bản, chỉ chạy một loop; ROOM chọn BATTLE, RESULT/LOBBY/AUTH/disconnect chọn LOBBY. TURN/TICK không khởi động lại nhạc. Settings thông báo bản đang phát; nút nghe thử bật bản của màn hiện tại. Mặc định nhạc vẫn tắt, bật qua Cài đặt → Nhạc nền.

File sửa: AudioManager.java, GameClient.java, tools/generate_audio.py, AudioManagerTest.java, ClientAudioTest.java, SettingsTest.java, README.md, docs/AUDIO.md và dist/ColorDuel-30s.jar. Thêm src/main/resources/audio/battle.wav. Toàn bộ sửa trực tiếp ở checkout ổ E, giữ nhánh hiện có và các thay đổi chưa commit. Đối chiếu snapshot đầu lượt xác nhận 10 file Java còn lại và 14 WAV cũ không đổi.

Kiểm tra thực tế trên JAR mới (log và render trong build/ui-music):

| Kiểm tra | Kết quả |
| --- | --- |
| Build Java 17 | Qua: compiler --release 17, class major 61; JAR chứa đủ 15 WAV khớp source |
| AudioManagerTest headless | 97 kiểm tra qua, gồm chuyển scene, không chồng nhạc, cache, mute/volume/suspend, missing track và EDT không bị chặn khi preload chậm |
| AudioManagerTest --device | 102 kiểm tra qua; thiết bị mở 15/15 clip; hai loop phát, frame tiến, chuyển về sảnh và dừng khi tắt |
| ClientAudioTest | Qua: ROOM/TICK/RESULT/new ROOM/lobby/logout, cue game, phím/thao tác giữ nguyên và settings không tự bật lại |
| SettingsTest headless/native | Qua: cửa sổ Swing thật, nhạc thi đấu, toggle/volume/preview, kết quả/rematch, status theo scene và cleanup |
| GuiNetworkTest | Qua: ba client TCP, đăng nhập, lời mời, trận thắng/thua và chơi lại |
| IntegrationTest | 61 kiểm tra qua, gồm timeout 30 giây thực |
| UiSmokeTest | Qua, render các màn ở 1200×840, 1040×680, 800×640; clipping/CTA/phím tắt/reduced motion |

Không chạy lại MySQL test hoặc benchmark animation trong lượt chỉ tách nhạc; nguồn backend và theme giữ nguyên theo hash. Đã kiểm tra đường phát qua mixer thật và thao tác trên client; chưa đánh giá chất âm bằng tai. Battle WAV có RMS 1294.4 và peak 7752 trên thang PCM 16-bit, âm lượng nhạc mặc định giữ 45%. Không tải asset lúc chơi. Build/dist JAR có cùng SHA-256:

```text
e7635eaa8907e02f5c360b821a29fa5f258022bd0a4e6e6a67679dad31af71d3
```
