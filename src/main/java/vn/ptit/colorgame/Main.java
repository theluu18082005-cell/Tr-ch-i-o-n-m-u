package vn.ptit.colorgame;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        try {
            String mode = args.length == 0 ? "client" : args[0];
            switch (mode) {
                case "server" -> {
                    int port = args.length > 1 ? Integer.parseInt(args[1]) : Rules.PORT;
                    Path data = Path.of(args.length > 2 ? args[2] : "data");
                    GameServer server = new GameServer(port, data);
                    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
                    server.start(); server.awaitTermination();
                    if (server.failed()) System.exit(1);
                }
                case "client" -> GameClient.launch(args.length > 1 ? args[1] : "127.0.0.1",
                        args.length > 2 ? Integer.parseInt(args[2]) : Rules.PORT);
                case "export" -> {
                    Path source = Path.of(args.length > 1 ? args[1] : "data");
                    Path output = Path.of(args.length > 2 ? args[2] : "reports");
                    new Store(source).exportCsv(output);
                    System.out.println("Đã xuất players.csv, matches.csv, turns.csv tại " + output.toAbsolutePath());
                }
                default -> System.out.println("Cách dùng: java -jar ColorDuel.jar [server [port [data]] | client [host [port]] | export [data [reports]]]");
            }
        } catch (Exception e) {
            System.err.println("Không chạy được chương trình: " + e.getMessage()); System.exit(1);
        }
    }
}
