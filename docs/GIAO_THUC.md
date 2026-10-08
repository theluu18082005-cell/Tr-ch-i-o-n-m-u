# Thiết kế và giao thức Color Duel

## Luồng dữ liệu

Mỗi client mở một kết nối TCP đến server, mặc định cổng 5000. Mọi lời mời, dự đoán và kết quả đều đi qua server; hai client không kết nối trực tiếp với nhau. Server dùng ServerSocket.accept để nhận kết nối, mỗi Session có một luồng đọc và một luồng ghi.

Các lệnh thay đổi trạng thái được đưa vào một event loop tuần tự của GameServer. Timer và thông điệp mạng cùng đi qua luồng này, tránh việc hết giờ và dự đoán đồng thời cùng ghi một lượt hoặc cộng điểm hai lần. PBKDF2 chạy trên hai worker riêng để việc đăng nhập không trực tiếp chặn đồng hồ game.

Client dùng NetworkClient để nhận/gửi trên các luồng nền. Mọi cập nhật Swing được đưa về EDT bằng SwingUtilities.invokeLater. Vì vậy giao diện không phải đợi socket.read để vẽ màn hình.

## Đóng khung thông điệp

TCP là luồng byte, không có ranh giới gói ứng dụng. Một thông điệp có dạng:

```text
COMMAND<TAB>BASE64(field1)<TAB>BASE64(field2)...<LF>
```

Tên lệnh là chữ hoa và dấu gạch dưới, các trường dùng UTF-8 trước khi Base64. LF xác định kết thúc thông điệp. Wire.read đọc cho đến LF và giới hạn 16 KiB/dòng. Việc đọc theo dòng xử lý được cả một thông điệp bị chia thành nhiều lần nhận và nhiều thông điệp đến chung một lần.

**Base64 là cách biểu diễn dữ liệu, không phải mã hóa bảo mật.** Kết nối hiện dùng TCP thuần, chưa TLS. Không dùng Java ObjectInputStream để đọc object không tin cậy từ mạng.

Trong các bảng dưới, trường được trình bày ở dạng đã giải mã Base64. Một dự đoán là chuỗi sáu chữ số từ 0 đến 5, mỗi chữ số xuất hiện đúng một lần.

| Mã trên wire | Màu | Số nhận biết trên giao diện |
| --- | --- | --- |
| 0 | Đỏ | 1 |
| 1 | Xanh lá | 2 |
| 2 | Xanh dương | 3 |
| 3 | Vàng | 4 |
| 4 | Tím | 5 |
| 5 | Cam | 6 |

Ví dụ dự đoán `012345` nghĩa là Đỏ → Xanh lá → Xanh dương → Vàng → Tím → Cam.

## Client gửi đến server

| Lệnh | Các trường theo thứ tự | Ý nghĩa |
| --- | --- | --- |
| REGISTER | username, password, displayName | Đăng ký, thành công thì đăng nhập luôn |
| LOGIN | username, password | Đăng nhập tài khoản tồn tại |
| PING | Không | Heartbeat, nhận lại PONG |
| LIST | Không | Yêu cầu danh sách online |
| RANK | Không | Yêu cầu bảng xếp hạng toàn hệ thống |
| HISTORY | Không | Yêu cầu tối đa 100 trận gần nhất của chính mình |
| CHALLENGE | targetUsername | Mời một người rỗi |
| RESPOND | invitationId, YES hoặc NO | Chấp nhận hoặc từ chối lời mời |
| CANCEL_INVITE | invitationId | Hủy lời mời còn hiệu lực |
| GUESS | matchId, turnNumber, sequence | Gửi dự đoán cho đúng trận và đúng lượt |
| REMATCH | matchId | Đồng ý chơi lại |
| BACK | matchId | Trở về sảnh sau trận |
| LEAVE | matchId | Rời phòng; đang chơi thì chịu thua |
| LOGOUT | Không | Ngắt phiên; đang chơi thì chịu thua |

Tài khoản chuẩn hóa về chữ thường, 3–20 ký tự a-z/0-9/_. Mật khẩu 6–128 ký tự. Tên hiển thị tối đa 40 ký tự, không chứa ký tự điều khiển. Mỗi tài khoản chỉ có một phiên online.

## Server gửi về client

| Lệnh | Các trường theo thứ tự | Ý nghĩa |
| --- | --- | --- |
| HELLO | protocolVersion, turnMillis, heartbeatMillis | Phiên bản giao thức và cấu hình thời gian |
| AUTH | username, displayName, points, wins, played | Xác thực thành công |
| PROFILE | username, displayName, points, wins, played | Cập nhật điểm/trận của tài khoản |
| PONG | Không | Trả lời heartbeat |
| ONLINE_BEGIN / ONLINE_END | Không | Bắt đầu / kết thúc một snapshot online |
| ONLINE_ROW | username, displayName, points, wins, played, status | Một người online |
| RANK_BEGIN / RANK_END | Không | Bắt đầu / kết thúc xếp hạng |
| RANK_ROW | username, displayName, points, wins, played | Một hàng theo thứ tự xếp hạng |
| HISTORY_BEGIN / HISTORY_END | Không | Bắt đầu / kết thúc danh sách trận |
| HISTORY_ROW | matchId, startedEpochMs, player1, player2, winner, reason, moveCount | Một trận của tài khoản |
| INVITE_SENT | invitationId, targetUsername, seconds | Đã gửi lời mời |
| INVITE | invitationId, fromUsername, seconds | Có lời mời đến |
| INVITE_CLOSED | invitationId, reasonText | Đóng lời mời |
| ROOM | matchId, player1, player2 | Bắt đầu ván; không chứa đáp án |
| TURN | matchId, turnNumber, currentPlayer, remainingMillis | Lượt hiện tại |
| TICK | matchId, turnNumber, currentPlayer, remainingMillis | Đồng bộ số thời gian còn lại |
| MOVE | matchId, turnNumber, player, sequence, correctCount, type | Lượt vừa hoàn thành, gửi giống nhau cho cả hai |
| RESULT | matchId, winnerUsername, reasonCode | Kết quả ván |
| REMATCH_STATUS | matchId, username | Người vừa đồng ý chơi lại |
| REMATCH_UNAVAILABLE | matchId, reasonText | Đối thủ đã rời, không thể chơi lại với cặp cũ |
| LOBBY | Không | Chuyển về sảnh |
| ERROR | originalCommand, message | Từ chối yêu cầu, kèm lý do |

MOVE có type GUESS hoặc TIMEOUT. TIMEOUT dùng sequence rỗng, correctCount = -1; client hiển thị dấu gạch thay vì điểm. Server không gửi SECRET ở bất kỳ thông điệp nào. Khi đoán đúng, chính lượt đoán 6/6 trong MOVE cho biết đáp án.

Mã kết thúc: SOLVED, LEFT, DISCONNECTED. Trận bị hủy vì server dùng SERVER_STOPPED hoặc SERVER_RESTARTED trong dữ liệu lịch sử, không có người thắng.

## Thứ tự xử lý một lượt

1. Client gửi GUESS với matchId và turnNumber đang hiển thị.
2. Server kiểm tra phiên đăng nhập, quyền tham gia phòng, trận còn diễn ra, đúng người, đúng số lượt, chưa quá deadline và dãy đủ sáu màu.
3. Rules.score so sánh từng vị trí. Không tính màu đúng nhưng ở sai vị trí.
4. Nếu chưa đủ 6/6, server ghi Move, tăng turnNumber, đổi currentPlayer, đặt deadline mới. Nếu đủ 6/6, server ghi Move và cập nhật kết quả/tài khoản.
5. Server lưu một snapshot dữ liệu thành công rồi gửi MOVE cho cả hai. Tiếp theo gửi TURN hoặc RESULT.

GUESS mang cả mã trận và số lượt để một yêu cầu cũ, gửi chậm hoặc gửi lặp không trở thành dự đoán hợp lệ cho lượt sau. Client khóa nút để hỗ trợ thao tác, còn server vẫn kiểm tra độc lập.

## Đồng hồ và mất kết nối

Server dùng System.nanoTime cho deadline 30 giây, không phụ thuộc thay đổi đồng hồ hệ điều hành. Vòng kiểm tra chạy mỗi 100 ms; thông báo chuyển lượt có thể trễ một khoảng nhỏ do lịch CPU/I/O. TICK cập nhật khoảng mỗi giây. Client đếm theo số millisecond server gửi; thời điểm nhận có thể trễ do mạng nên quyết định hợp lệ cuối cùng luôn thuộc server.

Client gửi PING mỗi 5 giây. Server kiểm tra 35 giây không nhận thông điệp và đóng phiên nếu quá hạn; socket còn có read timeout. Khi có TCP FIN/RST thì reader phát hiện sớm hơn. Nếu cả hai cùng mất mạng, sự kiện server xử lý trước là người bị tính rời trận trước; đây là giới hạn phân xử của phiên bản này.

## Lưu trữ và khôi phục

Store sử dụng định dạng nhị phân riêng qua DataInputStream/DataOutputStream, không dùng Java native serialization. File đầu có magic xác định định dạng, rồi các bản ghi Account, Match và Move. Mỗi lần lưu ghi vào file tạm, flush/sync, sau đó thay state.bin bằng ATOMIC_MOVE. Nếu không lưu được, server dừng và không thông báo thành công cho thay đổi chưa ghi.

Điểm và kết quả nằm trong cùng snapshot, giúp tránh trường hợp có kết quả nhưng điểm chưa cập nhật. File lock ngăn hai server ghi cùng thư mục. Khi khởi động, trận ACTIVE còn trong snapshot được chuyển sang SERVER_RESTARTED và không cộng điểm. Bản này lưu mọi dữ liệu trong RAM và ghi lại snapshot sau mỗi lượt, phù hợp quy mô bài tập nhóm; hệ thống lớn hơn có thể tách Store thành cơ sở dữ liệu giao dịch.

Mật khẩu lưu bằng PBKDF2WithHmacSHA256, salt ngẫu nhiên 16 byte và 210000 vòng. Client không được đọc file dữ liệu. Hàm export CSV không xuất salt hoặc hash.

## Tài liệu API chính thức

- [Java Socket](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html)
- [SwingUtilities và EDT](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/swing/SwingUtilities.html)
- [Maven compiler release](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)
