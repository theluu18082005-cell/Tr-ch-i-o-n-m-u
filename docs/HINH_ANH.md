# Hình ảnh và giao diện Color Duel 1.1

Hình minh họa được tạo riêng bằng công cụ tạo ảnh tích hợp, dùng làm hình nền đăng nhập, banner sảnh và hình trang trí tại phòng chơi. Không tải hình ảnh từ Internet khi chạy game.

Đường dẫn nguồn: `src/main/resources/images/color-arena.png`. Khi build, tài nguyên này được đóng gói vào JAR tại `/images/color-arena.png` và được đọc bằng `getResourceAsStream` trong GameTheme.

Các ô màu, avatar chữ cái, logo và đồng hồ tròn được vẽ bằng Java2D để co giãn, giữ chữ và màu rõ nét. Màu sắc và số lượng vị trí thực tế vẫn được quy định trong Rules; hình minh họa chỉ trang trí.

Mã bố cục nằm trong GameClient.java. Bộ thành phần dùng lại nằm trong GameTheme.java. Ảnh xem trước trong docs/images được render trực tiếp từ giao diện Swing với dữ liệu mẫu của UiSmokeTest.

## Prompt tạo hình minh họa

Use case: stylized-concept. Asset type: polished 3D hero illustration for a Vietnamese desktop color-guessing duel game called Color Duel, to be embedded as a decorative game background, not a screenshot or UI. Primary request: a beautiful playful cosmic puzzle arena with luminous colored game marbles. Landscape composition 3:2. A small floating circular midnight-blue arena, with six large individually separated polished translucent spheres in red, emerald green, royal blue, golden yellow, violet and orange arranged dynamically in a rising arc above it. Soft glossy material, rounded toy-like geometry, cyan rim light, warm golden reflections, tiny glowing stars and orbital curves, a few floating geometric fragments. Deep midnight navy backdrop (#0C1426) that fades to almost plain navy at the edges. Main arrangement centered toward the right half, generous dark negative space on the left for interface text added later in code. Premium stylized 3D game key art, elegant, luminous, richly shaded, crisp silhouettes, atmospheric depth. No text, no letters, no numbers, no logos, no watermark, no UI widgets, no borders, no humans or characters. The entire arena should fit inside the frame with breathing room.
