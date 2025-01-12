package fun.xingwangzhe.webmapview.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UrlManager {
    private static final String URL_FILE_NAME = "urls.txt"; // 存储URL列表的文件名
    private static final String DEFAULT_URL_FILE_NAME = "default_url.txt"; // 默认URL文件名
    private static List<String> urlList = new ArrayList<>(); // 存储URL的列表
    public static String defaultUrl="squaremap-demo.jpenilla.xyz"; // 默认URL
    public static boolean webmapview=true;
    static {
        loadUrls();
        loadDefaultUrl();
    }

    /**
     * 添加一个URL到列表中并保存到文件。
     * @param url 要添加的URL字符串
     */
    public static void addUrl(String url) {
        if (!urlList.contains(url)) { // 确保不重复添加相同的URL
            urlList.add(url);
            saveUrls(); // 保存更新后的URL列表到文件
            sendFeedback(Text.translatable("feedback.url.added", url)); // 向玩家发送反馈
        } else {
            sendFeedback(Text.translatable("feedback.url.exists", url)); // 如果URL已存在，则通知玩家
        }
    }

    /**
     * 设置默认URL。
     * @param url 要设置为默认的URL字符串
     */
    public static void setDefaultUrl(String url) {
        if (urlList.contains(url)) { // 确保URL已经存在于列表中
            defaultUrl = url;
            saveDefaultUrl(); // 更新默认URL到文件
            sendFeedback(Text.translatable("feedback.default.url.updated", url)); // 向玩家发送反馈
        } else {
            sendFeedback(Text.translatable("feedback.url.not_found", url)); // 如果URL不存在于列表中，则通知玩家
        }
    }

    /**
     * 获取当前的URL列表。
     * @return 包含所有URL的列表
     */
    public static List<String> getUrlList() {
        return urlList;
    }

    /**
     * 获取默认URL。
     * @return 默认URL字符串
     */
    public static String getDefaultUrl() {
        return defaultUrl;
    }

    /**
     * 将当前的URL列表保存到文件中。
     */
    private static void saveUrls() {
        Path configPath = getConfigDirectory().resolve(URL_FILE_NAME); // 获取配置文件路径
        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) { // 使用try-with-resources确保资源关闭
            for (String url : urlList) {
                writer.write(url); // 写入单个URL
                writer.newLine(); // 换行以便每个URL占一行
            }
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常信息
        }
    }

    /**
     * 从文件加载URL列表。
     */
    private static void loadUrls() {
        Path configPath = getConfigDirectory().resolve(URL_FILE_NAME); // 获取配置文件路径
        urlList.clear(); // 清空现有列表以准备加载新数据
        if (Files.exists(configPath)) { // 如果文件存在，则读取它
            try (BufferedReader reader = Files.newBufferedReader(configPath)) { // 使用try-with-resources确保资源关闭
                String line;
                while ((line = reader.readLine()) != null) { // 循环读取每一行
                    urlList.add(line); // 将每行作为一个URL添加到列表中
                }
            } catch (IOException e) {
                e.printStackTrace(); // 打印异常信息
            }
        }
    }

    /**
     * 保存默认URL到文件。
     */
    private static void saveDefaultUrl() {
        Path defaultUrlPath = getConfigDirectory().resolve(DEFAULT_URL_FILE_NAME); // 获取默认URL文件路径
        try (BufferedWriter writer = Files.newBufferedWriter(defaultUrlPath)) { // 使用try-with-resources确保资源关闭
            if (defaultUrl != null && !defaultUrl.trim().isEmpty()) {
                writer.write(defaultUrl); // 写入默认URL
            } else {
                // 如果没有有效的默认URL，则删除默认URL文件（如果存在）
                Files.deleteIfExists(defaultUrlPath);
            }
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常信息
        }
    }

    /**
     * 从文件加载默认URL。
     */
    private static void loadDefaultUrl() {
        Path defaultUrlPath = getConfigDirectory().resolve(DEFAULT_URL_FILE_NAME); // 获取默认URL文件路径
        if (Files.exists(defaultUrlPath)) { // 如果文件存在，则读取它
            try (BufferedReader reader = Files.newBufferedReader(defaultUrlPath)) { // 使用try-with-resources确保资源关闭
                String line;
                if ((line = reader.readLine()) != null) { // 只读取第一行
                    defaultUrl = line; // 设置默认URL
                }
            } catch (IOException e) {
                e.printStackTrace(); // 打印异常信息
            }
        }
    }

    /**
     * 获取配置目录路径。
     * @return Minecraft配置目录下的路径
     */
    private static Path getConfigDirectory() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        try {
            Files.createDirectories(configDir); // 如果目录不存在，则创建之
        } catch (IOException e) {
            e.printStackTrace();
        }
        return configDir;
    }

    /**
     * 发送反馈信息到聊天栏。
     * @param message 要发送的消息
     */
    public static void sendFeedback(String message) {
        MinecraftClient.getInstance().player.sendMessage(Text.of(message), false);
    }
    public static void sendFeedback(Text textMessage) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(textMessage, false);
        }
    }
    public static void removeUrl(String url) {
        if (urlList.remove(url)) { // 如果成功移除URL
            saveUrls(); // 保存更新后的URL列表到文件

            // 如果被删除的URL是默认URL，则清除默认URL设置
            if (defaultUrl != null && defaultUrl.equals(url)) {
                clearDefaultUrl();
            }

            sendFeedback(Text.translatable("feedback.url.removed", url)); // 向玩家发送反馈
        } else {
            sendFeedback(Text.translatable("feedback.url.not_found", url)); // 如果URL不存在，则通知玩家
        }
    }

    /**
     * 清除默认URL设置。
     */
    private static void clearDefaultUrl() {
        defaultUrl = null;
        Path defaultUrlPath = getConfigDirectory().resolve(DEFAULT_URL_FILE_NAME); // 获取默认URL文件路径
        try {
            Files.deleteIfExists(defaultUrlPath); // 删除默认URL文件（如果存在）
        } catch (IOException e) {
            e.printStackTrace(); // 打印异常信息
        }
    }

    public static String fullUrl(String baseUrl) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null|| webmapview==false ) {
            sendFeedback(Text.translatable("feedback.player_or_world_not_available")); // 使用翻译文本
            return baseUrl; // 如果无法获取玩家或世界信息，则返回原始URL
        }

        // 获取玩家坐标并转换为整数
        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();

        // 获取玩家所在的世界名称
        String worldName = client.world.getRegistryKey().getValue().toString();
        if(Objects.equals(worldName, "minecraft:overworld")){
            worldName = "world";
        } else if (Objects.equals(worldName, "minecraft:the_nether")){
            worldName = "world_nether";
        } else if (Objects.equals(worldName, "minecraft:the_end")){
            worldName = "world_the_end";
        }

        // 构建完整的URL
        StringBuilder fullUrlBuilder = new StringBuilder("https://").append(baseUrl).append("/"); // 添加协议头
        if (!baseUrl.contains("?")) { // 检查是否已有参数
            fullUrlBuilder.append("?");
        } else {
            fullUrlBuilder.append("&"); // 如果已经有参数，则使用&连接
        }
        fullUrlBuilder.append("x=").append(playerX)
                .append("&z=").append(playerZ)
                .append("&zoom=").append("4")
                .append("&world=").append(worldName);

        return fullUrlBuilder.toString();
    }
}