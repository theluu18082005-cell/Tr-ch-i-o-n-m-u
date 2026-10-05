Khi có mysql.properties: server lưu tài khoản, điểm, trận đấu và lượt đoán trong MySQL.
Điền đúng db.user và db.password, bật dịch vụ MySQL80 trước khi chạy run-server.bat.
Lần đầu kết nối database mới, dữ liệu state.bin được nhập vào MySQL trong một transaction.
state.bin được giữ nguyên làm bản dữ liệu cũ; sau khi chuyển, file này không cập nhật nữa.
Sao lưu database MySQL để giữ dữ liệu phát sinh sau khi chuyển.
Nếu không có mysql.properties, server dùng state.bin như trước.
Không chia sẻ state.bin hoặc mysql.properties cho client. Xem docs/MYSQL.md.
