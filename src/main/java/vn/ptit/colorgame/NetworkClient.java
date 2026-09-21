package vn.ptit.colorgame;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Không đọc/ghi socket trên Swing EDT để giao diện luôn phản hồi. */
public final class NetworkClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final BlockingQueue<String> outgoing = new ArrayBlockingQueue<>(128);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "client-heartbeat"); t.setDaemon(true); return t;
    });
    private final Consumer<String[]> onMessage;
    private final Consumer<String> onFailure;

    public NetworkClient(Consumer<String[]> onMessage, Consumer<String> onFailure) {
        this.onMessage = onMessage; this.onFailure = onFailure;
    }

    public void connect(String host, int port) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5000);
        socket.setTcpNoDelay(true); socket.setKeepAlive(true); socket.setSoTimeout(40_000);
        if (closed.get()) throw new IOException("Kết nối đã đóng");
        Thread reader = new Thread(() -> {
            try (InputStream in = new BufferedInputStream(socket.getInputStream())) {
                String[] message;
                while (!closed.get() && (message = Wire.read(in)) != null) onMessage.accept(message);
                fail("Server đã đóng kết nối. Hãy kiểm tra mạng rồi đăng nhập lại.");
            } catch (IOException e) { fail("Mất kết nối với server. Hãy kiểm tra mạng rồi đăng nhập lại."); }
        }, "client-reader");
        Thread writer = new Thread(() -> {
            try (OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
                while (!closed.get()) {
                    String message = outgoing.poll(1, TimeUnit.SECONDS);
                    if (message != null) { out.write(message.getBytes(StandardCharsets.US_ASCII)); out.flush(); }
                }
            } catch (IOException | InterruptedException e) { fail("Không gửi được dữ liệu đến server."); }
        }, "client-writer");
        reader.setDaemon(true); writer.setDaemon(true); reader.start(); writer.start();
        heartbeat.scheduleAtFixedRate(() -> send("PING"), 1, 5, TimeUnit.SECONDS);
    }

    public void send(String command, String... fields) {
        if (!closed.get() && !outgoing.offer(Wire.encode(command, fields))) fail("Hàng đợi gửi bị đầy; hãy kết nối lại.");
    }

    private void fail(String message) {
        if (closed.compareAndSet(false, true)) { stop(); onFailure.accept(message); }
    }

    private void stop() {
        heartbeat.shutdownNow();
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public void close() { if (closed.compareAndSet(false, true)) stop(); }
}
