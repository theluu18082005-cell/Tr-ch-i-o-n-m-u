# Kết nối ColorDuel với MySQL trên máy server

MySQL Server lưu dữ liệu; MySQL Workbench là ứng dụng để xem và quản lý database đó. Client game chỉ kết nối server TCP, không cần tài khoản MySQL.

1. Bật dịch vụ **MySQL80** trong Windows Services.
2. Điền thông tin vào `data/mysql.properties` (có mẫu `mysql.properties.example`): host `127.0.0.1`, port `3306`, database `color_duel`, tài khoản và mật khẩu MySQL của bạn. Mật khẩu này là mật khẩu MySQL, không phải mật khẩu đăng nhập game.
3. Giữ file `dist/lib/mysql-connector-j-8.3.0.jar` cạnh `dist/ColorDuel-30s.jar`. Chạy `build.bat` nếu đã sửa code rồi mở `run-server.bat`.
4. Server in dòng `Lưu trữ: jdbc:mysql://127.0.0.1:3306/color_duel` khi kết nối thành công.
5. Mở kết nối tương ứng trong Workbench, bấm Refresh trong **SCHEMAS**, chọn `color_duel`. Có thể chạy:

```sql
USE color_duel;
SELECT username, display_name, points, wins, played FROM players;
SELECT match_id, player1, player2, winner, reason, started_ms, ended_ms FROM matches;
SELECT match_id, turn_number, player, guess, correct_positions, move_type FROM turns;
```

| Bảng | Nội dung |
| --- | --- |
| `players` | Tài khoản, tên hiển thị, băm PBKDF2, salt, điểm và số trận |
| `matches` | Trận đấu, hai người chơi, kết quả và thời gian |
| `turns` | Từng lượt đoán hoặc hết giờ |
| `color_duel_meta` | Đánh dấu database đã được khởi tạo |

Lần đầu dùng database chưa có dữ liệu, server nhập `state.bin` vào MySQL. Việc nhập và mỗi lần lưu game dùng transaction InnoDB. File gốc được giữ nguyên; sau khi chuyển, MySQL là nguồn dữ liệu chính và `state.bin` không được cập nhật. Những lần chạy sau đọc từ MySQL, không nhập lại file cũ. Database đã có dữ liệu không bị ghi đè bằng `state.bin`. Dừng server trước khi chuyển và chỉ chạy một server cho một database.

Khi đã có cấu hình MySQL mà kết nối lỗi, server báo lỗi và dừng để tránh ghi dữ liệu vào nơi khác. `Access denied` thường do sai tài khoản/mật khẩu. Tài khoản phải có quyền SELECT, INSERT, UPDATE và CREATE trên database; nếu database chưa tồn tại thì cần quyền tạo database. Các bảng phải là InnoDB. Không dùng một database có sẵn các bảng cùng tên của ứng dụng khác.

`sslMode=DISABLED` và `allowPublicKeyRetrieval=true` trong mẫu dành cho MySQL trên chính máy này. Với MySQL từ xa, cấu hình TLS phù hợp; xem [tài liệu Connector/J chính thức](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html).

Không commit `mysql.properties` hoặc gửi file này cho client. Sao lưu dữ liệu mới bằng MySQL Workbench (Server → Data Export). `export.bat` vẫn xuất CSV nhưng đọc từ MySQL khi có cấu hình.

Nếu build trên máy khác, chép thư mục `dist/lib` cùng dự án, hoặc chạy `mvn package` để tạo `target/ColorDuel.jar` và `target/lib`. `build.bat`/`build.sh` cũng có thể lấy Connector/J 8.3.0 đã tải trong cache Maven của máy.

`test.bat` chạy các kiểm thử cũ với dữ liệu file tạm. Kiểm thử MySQL riêng sau khi build và biên dịch test:

```text
java -cp "build/classes;build/test-classes;dist/lib/*" vn.ptit.colorgame.MySqlIntegrationTest data/mysql.properties
```

Kiểm thử MySQL tạo database riêng có tên bắt đầu bằng `color_duel_test_` rồi xóa chính database đó khi kết thúc; tài khoản kiểm thử cần quyền CREATE/DROP database. Không dùng tên database thật cho dữ liệu kiểm thử.
