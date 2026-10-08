# Color Duel 1.1

**Neon Arcade Arena**: Battle HUD đối đầu cyan/pink, đồng hồ vòng năng lượng 30 giây, sáu orb có chiều sâu và bàn sắp xếp làm tâm điểm. Kết quả có màn Victory/Defeat riêng; sảnh ghim lời mời ở vùng ưu tiên; bảng xếp hạng đánh dấu Top 3; lịch sử phân biệt thắng/thua ngay trong danh sách. Giữ **Giảm hiệu ứng**, phím **1–6**, chọn hai ô để đổi vị trí và toàn bộ luật hiện có. Ảnh đấu trường được đóng gói trong JAR.

Bản polish có form đăng nhập gọn hơn, thẻ đối thủ arcade, Combat Log bằng orb, CTA và màn kết quả rõ hơn. Nút **Cài đặt** ở góc phải mở **Hiệu ứng âm thanh / Nhạc nền / Giảm hiệu ứng**, thanh âm lượng và hai nút nghe thử. Có 13 hiệu ứng WAV và hai bản nhạc đóng gói sẵn: nhẹ ở đăng nhập/sảnh, sôi động 120 BPM khi thi đấu; tự chuyển theo màn hình và nhạc mặc định tắt. Xem [hướng dẫn âm thanh](docs/AUDIO.md) và [kết quả polish, kiểm thử, ảnh thực](docs/POLISH_AUDIO_REPORT.md).

Mở lại `run-client.bat` để dùng bản đã cập nhật. Chi tiết thiết kế, kiểm thử và ảnh trước/sau: [docs/DESIGN.md](docs/DESIGN.md#neon-arcade-arena--bản-redesign-08102026).

Game đoán dãy màu đối kháng online cho bài tập lớn Lập trình mạng. Java 17, TCP Socket, giao diện Swing. Server dùng MySQL khi có `data/mysql.properties`; xem [hướng dẫn MySQL](docs/MYSQL.md). Nếu không có file cấu hình này, server dùng dữ liệu file như bản trước.

**Bắt đầu bằng cách mở `HUONG_DAN.html`.** File này có hướng dẫn Windows, IP, firewall, chạy trên bốn máy, phân công nhóm, giải thích mã nguồn và kịch bản demo.

## Chạy nhanh

1. Cài JDK 17 trở lên và kiểm tra `java -version`.
2. Giải nén toàn bộ thư mục, không chạy trực tiếp trong ZIP.
3. Một máy mở `run-server.bat`.
   Với MySQL: điền đúng tài khoản trong `data/mysql.properties`, bật dịch vụ MySQL và giữ thư mục `dist/lib` cạnh JAR. Lần đầu kết nối, server tự tạo database `color_duel` và chuyển dữ liệu từ `state.bin` nếu database chưa có dữ liệu. Không chia sẻ file cấu hình MySQL cho client.
4. Các máy còn lại mở `run-client.bat`, nhập IPv4 của máy server, cổng `5000`.
5. Ở màn hình đăng nhập, bấm **Đăng ký người chơi mới** để mở màn hình đăng ký riêng. Đã có tài khoản thì bấm **Trở về đăng nhập** để quay lại. Sau khi đăng nhập hoặc đăng ký thành công, chọn đối thủ và mời thi đấu.

Để thử cả hệ thống trên một máy, mở `demo-1may.bat`; ba client giữ IP `127.0.0.1`. Nếu đã mở server thì chỉ mở thêm `run-client.bat`, không mở server thứ hai.

Trong tab **Thách đấu**, tìm người chơi theo tên hoặc tài khoản, rồi chọn đối thủ có nhãn **Sẵn sàng**. Thẻ bên phải hiển thị điểm, số trận thắng và số trận đã chơi. Bấm **Mời thi đấu** để gửi lời mời; người gửi có thể hủy, người nhận có thể chấp nhận hoặc từ chối. Thẻ lời mời hiển thị thời gian phản hồi còn lại và tự khóa thao tác khi hết hạn.

## Các file chính

| File | Công dụng |
| --- | --- |
| `CAP_NHAT_GIAO_DIEN.txt` | Cách nâng cấp và chuyển dữ liệu từ bản cũ |
| `dist/ColorDuel-30s.jar` | Bản đã biên dịch, chạy được cả chế độ server và client |
| `run-server.bat` | Chạy server Windows tại cổng 5000 |
| `run-client.bat` | Mở một cửa sổ client Windows |
| `demo-1may.bat` | Mở một server và ba client trên cùng máy |
| `build.bat` | Biên dịch lại sau khi sửa mã nguồn |
| `test.bat` | Chạy kiểm thử tích hợp và giao diện; dùng dữ liệu tạm riêng |
| `export.bat` | Xuất dữ liệu server ra ba file CSV trong `reports` |
| `src/main/java` | Toàn bộ mã nguồn ứng dụng |
| `src/test/java` | Bộ kiểm thử bằng socket thật và giao diện Swing |
| `pom.xml` | Mở dự án Java Maven trong IDE |
| `HUONG_DAN.html` | Hướng dẫn đầy đủ, mở bằng trình duyệt |
| `HUONG_DAN.md` | Bản văn bản có thể chỉnh sửa của hướng dẫn |
| `docs/GIAO_THUC.md` | Đặc tả thông điệp TCP và thiết kế xử lý |
| `docs/KIEM_THU.md` | Kết quả đã kiểm tra và các bước demo trên máy thật |
| `data` | Dữ liệu bền vững của server, tự tạo khi chạy |

## Lệnh tương đương

Chạy từ thư mục dự án:

```text
java -jar dist/ColorDuel-30s.jar server 5000 data
java -jar dist/ColorDuel-30s.jar client 127.0.0.1 5000
java -jar dist/ColorDuel-30s.jar export data reports
```

Linux/macOS: `bash build.sh`, sau đó `bash run.sh server` hoặc `bash run.sh client 127.0.0.1`. Client cần môi trường đồ họa. Kiểm thử: `bash test.sh`.

## Luật chính

Hai người luân phiên đoán một hoán vị của Đỏ, Xanh lá, Xanh dương, Vàng, Tím, Cam. Mỗi lượt 30 giây. Đủ sáu màu, không lặp màu. Server chỉ thông báo số vị trí đúng và chia sẻ lịch sử cho cả hai; không gửi dãy bí mật. Đúng 6/6 là thắng, cộng 1 điểm; thua 0 điểm. Thoát hoặc mất kết nối trong trận bị xử thua. Cả hai đồng ý mới chơi lại. Không giới hạn số lượt.

Bản này dành cho LAN/VPN tin cậy. Đường truyền TCP chưa có TLS; dùng tài khoản và mật khẩu riêng cho bài demo. Mật khẩu trên đĩa được băm PBKDF2 cùng salt. Không chia sẻ thư mục dữ liệu thật của server cho các client.
