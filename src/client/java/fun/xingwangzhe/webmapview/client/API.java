package fun.xingwangzhe.webmapview.client;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.MinecraftClient;

public class API {

    private static HttpServer server;
    private static List<PlayerInformation> localPlayers;

    public static void startLocalHttpServer(int port, List<PlayerInformation> players) {
        try {
            // 如果没有提供玩家列表，则创建一个新的列表
            if (players == null) {
                localPlayers = new ArrayList<>();
            } else {
                localPlayers = new ArrayList<>(players);
            }
            
            // 检查是否需要添加当前玩家信息
            addCurrentPlayerInfo();
            
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/local-players.json", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try {
                        String response = PlayerInformation.generatePlayerJson(localPlayers, 100, true);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            server.start();
            System.out.println("Local HTTP server started on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopLocalHttpServer() {
        if (server != null) {
            server.stop(0);
            System.out.println("Local HTTP server stopped.");
        }
    }

    public static void updateLocalPlayers(List<PlayerInformation> players) {
        if (players == null) {
            localPlayers = new ArrayList<>();
        } else {
            localPlayers = new ArrayList<>(players);
        }
        addCurrentPlayerInfo();
    }
    
    /**
     * 添加当前玩家信息到本地玩家列表（如果列表中还没有当前玩家）
     */
    private static void addCurrentPlayerInfo() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && client.world != null) {
                String currentPlayerName = client.player.getName().getString();
                
                // 检查当前玩家是否已经在列表中
                boolean playerExists = localPlayers.stream()
                    .anyMatch(player -> player.getName().equals(currentPlayerName));
                
                // 如果当前玩家不在列表中，则添加
                if (!playerExists) {
                    PlayerInformation currentPlayer = new PlayerInformation(
                        client.player.getUuid().toString(),
                        currentPlayerName,
                        client.player.getDisplayName().getString(),
                        client.player.getX(),
                        client.player.getZ(),
                        client.world.getRegistryKey().getValue().toString().replace(":", "_"),
                        client.player.getYaw(),
                        (int) client.player.getHealth(),
                        (int) client.player.getArmor(),
                        "", // headUrl
                        false, // isVirtual
                        "" // icon
                    );
                    localPlayers.add(currentPlayer);
                    System.out.println("Added current player to local player list: " + currentPlayerName);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to add current player info: " + e.getMessage());
        }
    }
}