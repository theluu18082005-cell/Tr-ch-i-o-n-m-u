# Color Duel 1.1

Giao diện mới có hình minh họa riêng, nền xanh đậm, avatar, các ô màu có hiệu ứng khối và đồng hồ vòng tròn. Ảnh đã nằm trong JAR. Nếu đang dùng bản cũ, đọc `CAP_NHAT_GIAO_DIEN.txt` để giữ tài khoản và điểm.

Game đoán dãy màu đối kháng online cho bài tập lớn Lập trình mạng. Java 17, TCP Socket, giao diện Swing. Server dùng MySQL khi có `data/mysql.properties`; xem [hướng dẫn MySQL](docs/MYSQL.md). Nếu không có file cấu hình này, server dùng dữ liệu file như bản trước.

**Bắt đầu bằng cách mở `HUONG_DAN.html`.** File này có hướng dẫn Windows, IP, firewall, chạy trên bốn máy, phân công nhóm, giải thích mã nguồn và kịch bản demo.

## Chạy nhanh

1. Cài JDK 17 trở lên và kiểm tra `java -version`.
2. Giải nén toàn bộ thư mục, không chạy trực tiếp trong ZIP.
3. Một máy mở `run-server.bat`.
   Với MySQL: điền đúng tài khoản trong `data/mysql.properties`, bật dịch vụ MySQL và giữ thư mục `dist/lib` cạnh JAR. Lần đầu kết nối, server tự tạo database `color_duel` và chuyển dữ liệu từ `state.bin` nếu database chưa có dữ liệu. Không chia sẻ file cấu hình MySQL cho client.
4. Các máy còn lại mở `run-client.bat`, nhập IPv4 của máy server, cổng `5000`.
5. Ở màn hình đăng nhập, bấm **Chưa có tài khoản? Đăng ký** để mở màn hình đăng ký riêng. Đã có tài khoản thì bấm **Đã có tài khoản? Đăng nhập** để quay lại. Sau khi đăng nhập hoặc đăng ký thành công, chọn đối thủ và mời thi đấu.

Để thử cả hệ thống trên một máy, mở `demo-1may.bat`; ba client giữ IP `127.0.0.1`. Nếu đã mở server thì chỉ mở thêm `run-client.bat`, không mở server thứ hai.

## Các file chính

| File | Công dụng |
| --- | --- |
| `CAP_NHAT_GIAO_DIEN.txt` | Cách nâng cấp và chuyển dữ liệu từ bản cũ |
| `dist/ColorDuel-MySQL.jar` | Bản đã biên dịch, chạy được cả chế độ server và client |
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
java -jar dist/ColorDuel-MySQL.jar server 5000 data
java -jar dist/ColorDuel-MySQL.jar client 127.0.0.1 5000
java -jar dist/ColorDuel-MySQL.jar export data reports
```

Linux/macOS: `bash build.sh`, sau đó `bash run.sh server` hoặc `bash run.sh client 127.0.0.1`. Client cần môi trường đồ họa. Kiểm thử: `bash test.sh`.

## Luật chính

Hai người luân phiên đoán một hoán vị của Đỏ, Xanh lá, Xanh dương, Vàng, Tím, Cam. Mỗi lượt 15 giây. Đủ sáu màu, không lặp màu. Server chỉ thông báo số vị trí đúng và chia sẻ lịch sử cho cả hai; không gửi dãy bí mật. Đúng 6/6 là thắng, cộng 1 điểm; thua 0 điểm. Thoát hoặc mất kết nối trong trận bị xử thua. Cả hai đồng ý mới chơi lại. Không giới hạn số lượt.

Bản này dành cho LAN/VPN tin cậy. Đường truyền TCP chưa có TLS; dùng tài khoản và mật khẩu riêng cho bài demo. Mật khẩu trên đĩa được băm PBKDF2 cùng salt. Không chia sẻ thư mục dữ liệu thật của server cho các client.
