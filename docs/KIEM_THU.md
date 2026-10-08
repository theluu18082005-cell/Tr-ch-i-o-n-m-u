# Kiểm thử bản bàn giao

Cập nhật giao diện 21/09/2026: đã kiểm tra chính JAR mới với 7 kiểm tra giao diện và 7 kiểm tra GUI qua TCP thật. Đã xác nhận ảnh nằm trong JAR và xem bố cục ở 1200 × 840 cùng 1040 × 680. Các file server, giao thức và định dạng dữ liệu được đối chiếu và giữ nguyên so với nguồn bản trước.

Ngày thực hiện: 20/09/2026. Môi trường: OpenJDK 17 trên Linux. Bản JAR được biên dịch với --release 17, không có dependency ngoài.

## Kết quả đã kiểm tra

| Nhóm | Phép kiểm tra | Kết quả |
| --- | --- | --- |
| Biên dịch | Toàn bộ mã nguồn ứng dụng và bộ test | Đạt |
| Giao thức | UTF-8, các trường rỗng, nhiều thông điệp trên TCP, giới hạn độ dài | Đạt |
| Tài khoản | Ba kết nối thật đăng ký; mật khẩu băm; chặn đăng nhập trùng/sai | Đạt |
| Lời mời | Mời, từ chối, chặn lời mời chồng chéo, chấp nhận cùng phòng | Đạt |
| Luật | Sáu màu không lặp, chấm đúng ví dụ 2 vị trí trong đề | Đạt |
| Thẩm quyền server | Chặn gửi sai phòng, sai lượt, lặp màu, gửi trùng | Đạt |
| Đồng bộ | Hai máy nhận cùng ROOM, TURN, MOVE và RESULT | Đạt |
| Hết giờ | Chờ một lượt 30 giây thật; tự lưu TIMEOUT và chuyển lượt | Đạt |
| Thắng trận | Bộ giải chọn hoán vị theo kết quả công khai và thắng đúng 6/6 | Đạt |
| Điểm | Thắng +1, số trận hai người tăng đúng một lần | Đạt |
| Xếp hạng | Đúng thứ tự điểm, thắng, số trận | Đạt |
| Chơi lại | Một người đồng ý chưa bắt đầu; hai người đồng ý mới tạo ván | Đạt |
| Rời trận | Chủ động thoát và đóng socket bị tính thua | Đạt |
| Đứt mạng im lặng | Ngừng heartbeat dù socket chưa đóng; server xử mất kết nối | Đạt với thời gian heartbeat rút ngắn riêng trong test |
| Cạnh tranh | Dự đoán thắng và thoát phòng gửi gần đồng thời, không cộng kết quả hai lần | Đạt |
| Lưu trữ | Đọc lại tài khoản, lịch sử, xuất CSV, chặn hai server chung dữ liệu | Đạt |
| Khôi phục | Nạp snapshot có trận dở, hủy trận đúng, đăng nhập lại được | Đạt |
| Giao diện | Nút gửi chỉ bật đúng lúc; đổi ô; khóa khi chờ; xóa lịch sử khi chơi lại | Đạt |
| GUI với TCP thật | Ba GameClient dùng NetworkClient thật; đăng ký, mời, chơi, thắng, chơi lại | Đạt |
| Bố cục | Render và xem ảnh đăng nhập, sảnh, phòng và kết quả | Đạt ở kích thước 1200 × 840 và 1040 × 680 |

Lần chạy IntegrationTest ghi nhận **61 kiểm tra thành công**. Số dòng kiểm tra có thể khác khi chạy lại vì dãy bí mật ngẫu nhiên làm bộ giải cần số lượt khác nhau. UiSmokeTest có 7 kiểm tra trạng thái; GuiNetworkTest có 7 kiểm tra kết nối giao diện. Log kiểm thử đi kèm trong thư mục docs.

Bản chạy thật dùng 30 giây mỗi lượt theo cấu hình Rules.TURN_MILLIS. Chỉ ngưỡng heartbeat trong IntegrationTest được rút từ 35 giây xuống 1,8 giây để kiểm thử ngắt mạng nhanh; GuiNetworkTest và chương trình phân phối dùng ngưỡng mặc định 35 giây.

## Cách chạy lại

Windows: mở `test.bat`. Linux/macOS: `bash test.sh`. Cần JDK 17 trở lên. Thời gian thường dưới một phút, trong đó có một lượt chờ hết 30 giây. Bộ test tạo thư mục tạm riêng, không đọc/ghi data đang dùng của nhóm.

Ba lớp kiểm thử đều có main riêng, không phụ thuộc JUnit. Lệnh Maven test mặc định không chạy các kịch bản main này; dùng script test để thực hiện đầy đủ.

## Nhóm cần xác nhận trên máy thật

Các phép kiểm tra trên chạy bằng loopback trong một môi trường Linux. Chưa xác minh bốn máy Windows vật lý, thiết lập Wi-Fi của nhóm, DPI màn hình Windows hoặc firewall cụ thể. Trước khi nộp cần ghi nhận kết quả thực tế cho các mục dưới đây.

| Tình huống trên Windows/LAN | Kỳ vọng | Nhóm ghi kết quả |
| --- | --- | --- |
| Mở server trên Máy 1, client trên Máy 2–4 | Cả ba thấy nhau online | Chưa thực hiện |
| Thay 127.0.0.1 bằng IPv4 server | Kết nối được từ máy khác | Chưa thực hiện |
| Gửi một lượt | Cả hai hiện cùng dãy và số vị trí đúng | Chưa thực hiện |
| Đợi hết 30 giây | Tự chuyển lượt, lịch sử ghi hết giờ | Chưa thực hiện |
| Thoát phòng có xác nhận | Đối thủ thắng, dữ liệu được lưu | Chưa thực hiện |
| Tắt Wi-Fi một client giữa trận | Server xử thua sau khi phát hiện mất kết nối | Chưa thực hiện |
| Chơi lại | Hai bên cùng đồng ý thì ván mới bắt đầu | Chưa thực hiện |
| Dừng và mở lại server | Đăng nhập được, dữ liệu trận hoàn thành còn giữ | Chưa thực hiện |
| Màn hình độ phân giải/DPI thực tế | Đủ nút, đủ cột, đọc được chữ có dấu | Chưa thực hiện |

Giữ ảnh chụp/log các lần chạy thật để đưa vào báo cáo. Không ghi các mục chưa chạy thành kết quả đã đạt.
