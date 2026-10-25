package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/** Offline, scrollable Swing guide. It has no network or match-state dependencies. */
final class GameGuide extends JPanel {
    private final JButton close=new GameTheme.ActionButton("Đã hiểu · Đóng").primary();
    private final JTextPane text=new JTextPane();
    private final JScrollPane scroll;

    GameGuide(Runnable dismiss) {
        super(new BorderLayout(0,16)); setName("game-guide"); setBackground(GameTheme.PANEL); setBorder(new EmptyBorder(20,22,18,22));
        JPanel heading=new JPanel(new BorderLayout(0,6)); heading.setOpaque(false);
        JLabel title=new JLabel("HƯỚNG DẪN CHƠI"); title.setForeground(GameTheme.TEXT); title.setFont(GameTheme.display(24)); heading.add(title,BorderLayout.NORTH);
        JLabel subtitle=new JLabel("6 MÀU  /  "+Rules.TURN_MILLIS/1000+" GIÂY MỖI LƯỢT  /  ĐỐI KHÁNG 1v1"); subtitle.setFont(GameTheme.font(11,true)); subtitle.setForeground(GameTheme.ACCENT); heading.add(subtitle,BorderLayout.SOUTH); add(heading,BorderLayout.NORTH);
        text.setName("guide-content"); text.setEditable(false); text.setBackground(GameTheme.PANEL); text.setForeground(GameTheme.TEXT); text.setFont(GameTheme.font(14,false)); text.setBorder(new EmptyBorder(0,0,0,12));
        text.getAccessibleContext().setAccessibleName("Luật và cách chơi ColorDuel");
        section("01  Mục tiêu", "Tìm đúng thứ tự dãy 6 màu bí mật do server tạo: Đỏ, Xanh lá, Xanh dương, Vàng, Tím, Cam. Mỗi màu xuất hiện đúng một lần. Ai đoán đúng cả 6 vị trí trước sẽ thắng.");
        section("02  Mời đối thủ vào trận", "Tại sảnh, chọn người chơi có trạng thái Sẵn sàng rồi bấm Mời thi đấu. Người nhận có 30 giây để Chấp nhận hoặc Từ chối; người gửi có thể hủy lời mời. Cả hai vào phòng sau khi lời mời được chấp nhận.");
        section("03  Sắp xếp và gửi dự đoán", "Kéo màu từ thanh màu vào bất kỳ ô nào, không cần điền từ vị trí 1. Kéo một quả cầu sang ô khác để đổi chỗ hoặc chuyển vào ô trống. Kéo màu mới đè lên ô đã có sẽ thay màu tại ô đó. Bạn vẫn có thể bấm màu hoặc phím 1–6 để điền vào ô trống đầu tiên. Chọn hai ô để đổi vị trí; bấm lại cùng một ô để bỏ màu đó. Xóa tất cả chỉ xóa dãy đang đoán. Khi đủ 6 màu khác nhau và đến lượt mình, bấm Gửi dự đoán.");
        section("04  Đọc kết quả và tiếp tục", "Combat Log ghi dự đoán của cả hai người. Kết quả 2/6 nghĩa là có 2 màu nằm đúng vị trí, không cho biết cụ thể vị trí nào. Đúng 6/6 sẽ thắng ngay. Khi đến lượt mình, bấm một lượt đoán hợp lệ trong Combat Log hoặc chọn dòng rồi nhấn Enter để nạp lại dãy của bạn hay đối thủ. Sau đó có thể chỉnh sửa và bấm Gửi dự đoán; chọn lại lịch sử không tự gửi, không đổi ghi chú riêng. Lượt hết giờ không có dãy để nạp lại. Khi đối thủ vừa gửi dự đoán, bàn của bạn tự điền theo dãy đó để tiếp tục chỉnh sửa ở lượt kế tiếp.");
        section("05  Ghi chú riêng để dễ nhớ", "Hàng Ghi chú riêng có 6 vị trí. Bấm từng ô để chọn màu bạn nghĩ là đúng, hoặc chọn Chưa rõ để bỏ ghi chú. Đây là suy luận của bạn, không phải vị trí đã được game xác nhận. Chỉ bạn thấy ghi chú; có thể sửa cả khi chờ đối thủ. Xóa ghi chú không xóa dãy dự đoán. Ghi chú giữ qua các lượt và tự xóa khi sang ván mới.");
        section("06  Thời gian, điểm và chơi lại", "Mỗi lượt có "+Rules.TURN_MILLIS/1000+" giây. Hết giờ sẽ mất lượt; nếu đối thủ hết giờ mà chưa gửi dự đoán, bàn giữ dãy hiện tại. Không giới hạn số lượt. Người thắng được +1 điểm; người thua được +0 điểm. Rời phòng hoặc mất kết nối khi đang đấu sẽ bị tính thua. Sau trận, một người bấm Chơi lại để gửi lời mời. Đối thủ nhận hộp Đồng ý chơi tiếp / Trở về sảnh; cả hai đồng ý thì server bắt đầu ván mới. Người gửi có thể Trở về sảnh để hủy chờ. Nếu đối thủ rời phòng, hộp báo không thể chơi lại và cho bạn về sảnh.");
        section("07  Âm thanh và hiệu ứng", "Mở Cài đặt ở thanh trên để bật/tắt hiệu ứng âm thanh, nhạc nền, chỉnh âm lượng hoặc chọn Giảm hiệu ứng. Nhạc nhẹ phát ngoài trận; nhạc thi đấu phát khi vào phòng nếu bạn đã bật Nhạc nền.");
        text.setCaretPosition(0); scroll=GameTheme.scroll(text); scroll.setName("guide-scroll"); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); scroll.getVerticalScrollBar().setUnitIncrement(28); add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(12,0)); footer.setOpaque(false);
        JLabel hint=new JLabel("Esc để đóng · Cuộn để đọc tiếp"); hint.setFont(GameTheme.font(11,false)); hint.setForeground(GameTheme.MUTED); footer.add(hint,BorderLayout.CENTER);
        close.setName("guide-close"); close.setFont(GameTheme.font(13,true)); close.setPreferredSize(new Dimension(168,40)); close.addActionListener(e -> dismiss.run()); footer.add(close,BorderLayout.EAST); add(footer,BorderLayout.SOUTH);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ESCAPE"),"close-guide");
        getActionMap().put("close-guide",new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { dismiss.run(); } });
    }
    private void section(String heading,String body) {
        StyledDocument document=text.getStyledDocument();
        SimpleAttributeSet title=new SimpleAttributeSet(); StyleConstants.setFontFamily(title,GameTheme.font(16,true).getFamily()); StyleConstants.setFontSize(title,16); StyleConstants.setBold(title,true); StyleConstants.setForeground(title,GameTheme.ACCENT);
        SimpleAttributeSet copy=new SimpleAttributeSet(); StyleConstants.setFontFamily(copy,GameTheme.font(14,false).getFamily()); StyleConstants.setFontSize(copy,14); StyleConstants.setForeground(copy,GameTheme.TEXT);
        try {
            document.insertString(document.getLength(),heading+"\n",title);
            int start=document.getLength(); document.insertString(start,body+"\n\n",copy);
            SimpleAttributeSet paragraph=new SimpleAttributeSet(); StyleConstants.setLineSpacing(paragraph,.15f); StyleConstants.setSpaceAbove(paragraph,5); StyleConstants.setSpaceBelow(paragraph,8);
            document.setParagraphAttributes(start,body.length()+1,paragraph,false);
        } catch(BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }
    JButton closeButton() { return close; }
    String content() { return text.getText(); }
    JScrollPane scrollPane() { return scroll; }
}
