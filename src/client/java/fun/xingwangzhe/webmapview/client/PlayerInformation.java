package fun.xingwangzhe.webmapview.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerInformation {

    private String uuid;
    private String name;
    private String displayName;
    private double x;
    private double z;
    private String world;
    private double yaw;
    private int health;
    private int armor;
    private String headUrl;
    private boolean isVirtual;
    private String icon;

    public PlayerInformation(String uuid, String name, String displayName, double x, double z, String world, double yaw, int health, int armor, String headUrl, boolean isVirtual, String icon) {
        this.uuid = uuid;
        this.name = name;
        this.displayName = displayName;
        this.x = x;
        this.z = z;
        this.world = world;
        this.yaw = yaw;
        this.health = health;
        this.armor = armor;
        this.headUrl = headUrl;
        this.isVirtual = isVirtual;
        this.icon = icon;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public double getYaw() {
        return yaw;
    }

    public void setYaw(double yaw) {
        this.yaw = yaw;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public int getArmor() {
        return armor;
    }

    public void setArmor(int armor) {
        this.armor = armor;
    }

    public String getHeadUrl() {
        return headUrl;
    }

    public void setHeadUrl(String headUrl) {
        this.headUrl = headUrl;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public void setVirtual(boolean virtual) {
        isVirtual = virtual;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public static String generatePlayerJson(List<PlayerInformation> players, int maxPlayers) {
        Gson gson = new Gson();
        JsonObject root = new JsonObject();
        root.addProperty("max", maxPlayers);

        JsonArray playersArray = new JsonArray();
        for (PlayerInformation player : players) {
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("uuid", player.getUuid());
            playerJson.addProperty("name", player.getName());
            playerJson.addProperty("display_name", player.getDisplayName());
            playerJson.addProperty("x", player.getX());
            playerJson.addProperty("y", 66); // 假设固定高度
            playerJson.addProperty("z", player.getZ());
            playerJson.addProperty("world", player.getWorld());
            playerJson.addProperty("yaw", player.getYaw());
            playerJson.addProperty("health", player.getHealth());
            playerJson.addProperty("armor", player.getArmor());
            playersArray.add(playerJson);
        }

        root.add("players", playersArray);
        return gson.toJson(root);
    }

    public static void startPlayerJsonGeneration(List<PlayerInformation> players, int maxPlayers, long intervalMillis) {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String json = generatePlayerJson(players, maxPlayers);
                System.out.println(json); // 这里可以替换为保存到文件或发送到服务器
            }
        }, 0, intervalMillis);
    }

    /**
     * 生成本地玩家数据的 JSON 字符串。
     * @return JSON 格式的玩家数据
     */
    public static String generateLocalPlayerJson() {
        // 示例玩家数据，可以替换为动态生成的内容
        List<PlayerInformation> players = List.of(
            new PlayerInformation(
                "289877384c6c30c1aa11d6d8ff63d871",
                "BVVD",
                "BVVD",
                230150,
                -60139,
                "minecraft_overworld",
                62,
                40,
                0,
                "https://cravatar.eu/helmavatar/BVVD/16",
                true,
                "arrow"
            )
        );
        return generatePlayerJson(players, 200);
    }
}
