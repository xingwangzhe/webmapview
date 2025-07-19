package fun.xingwangzhe.webmapview.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static fun.xingwangzhe.webmapview.client.UrlManager.sendFeedback;

public class WebmapviewClient implements ClientModInitializer {

    private static CompletableFuture<Suggestions> suggestUrls(CommandContext<?> context, SuggestionsBuilder builder) {
        List<String> urls = UrlManager.getUrlList();
        urls.forEach(builder::suggest);
        return builder.buildFuture();
    }

    private KeyBinding keyBinding;
    private static long lastResourcePackCheckTime = 0;

    /**
     * 检测资源包是否完全加载
     */
    private static boolean isResourcePackFullyLoaded() {
        try {
            MinecraftClient minecraft = MinecraftClient.getInstance();

            // 基本组件检查
            if (minecraft.getResourceManager() == null) {
                return false;
            }

            if (minecraft.getTextureManager() == null) {
                return false;
            }

            if (minecraft.textRenderer == null) {
                return false;
            }

            if (minecraft.getWindow() == null || minecraft.getWindow().getHandle() == 0) {
                return false;
            }

            // 检查最近是否发生过资源重新加载
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResourcePackCheckTime < 3000) { // 3秒冷却时间
                return false;
            }

            // 更新检查时间
            lastResourcePackCheckTime = currentTime;
            return true;

        } catch (Exception e) {
            System.err.println("资源包状态检测失败: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onInitializeClient() {
//        ClientTickEvents.START_CLIENT_TICK.register((client) -> onTick());
        // 注册“addturl”命令，用于添加新的URL
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("urladd")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string())
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.addUrl(url); // 添加URL
                                        return 1; // 命令成功执行
                                    })
                            )
            );

            // 注册“removeurl”命令，用于删除URL
            dispatcher.register(
                    ClientCommandManager.literal("urlremove")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string()).suggests(WebmapviewClient::suggestUrls)
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.removeUrl(url); // 删除URL
                                        return 1; // 命令成功执行
                                    })
                            )
            );

            // 注册“urllist”命令，用于列出所有已添加的URL
            dispatcher.register(
                    ClientCommandManager.literal("urllist")
                            .executes(context -> {
                                List<String> urls = UrlManager.getUrlList(); // 获取所有URL
                                StringBuilder listMessage = new StringBuilder("Available URLs:\n");
                                for (int i = 0; i < urls.size(); i++) {
                                    listMessage.append(i + 1).append(": ").append(urls.get(i)).append("\n"); // 格式化输出
                                }
                                context.getSource().sendFeedback(Text.of(listMessage.toString())); // 向玩家展示结果
                                return 1; // 命令成功执行
                            })
            );
            // 注册“urlset”命令，用于设置默认URL
            dispatcher.register(
                    ClientCommandManager.literal("urlset")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string()).suggests(WebmapviewClient::suggestUrls)
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.setDefaultUrl(url); // 设置默认URL
                                        return 1; // 命令成功执行
                                    })
                            )
            );
            dispatcher.register(
                    ClientCommandManager.literal("webmapviewoption")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string())
                                    .executes(context -> {
                                        UrlManager.webmapview = !UrlManager.webmapview;
                                        if (UrlManager.webmapview) {
                                            sendFeedback("webmapview is enabled");
                                        } else {
                                            sendFeedback("webmapview is not enabled");
                                        }

                                   return 1; })
                            )
            );
            dispatcher.register(
                    ClientCommandManager.literal("webmapview")
                            .then(ClientCommandManager.literal("help")
                                    .executes(context -> {
                                        String helpMessage = "/urladd: " + Text.translatable("command.urladd.description").getString() + "\n" +
                                                "/urlremove: " + Text.translatable("command.urlremove.description").getString() + "\n" +
                                                "/urllist: " + Text.translatable("command.urllist.description").getString() + "\n" +
                                                "/urlset: " + Text.translatable("command.urlset.description").getString() + "\n" +
                                                "/webmapviewoption: " + Text.translatable("command.webmapviewoption.description").getString() + "\n";
                                        sendFeedback(helpMessage);
                                        return 1;
                                    })
                            )
            );
        });

        // 初始化KeyBinding
        keyBinding = new KeyBinding(
                "key.webmapview.open_basic_browser",  // 使用唯一标识符
                GLFW.GLFW_KEY_H,                      // 默认按键
                "category.webmapview"                       // 分类
        );

        // 注册KeyBinding
        KeyBindingHelper.registerKeyBinding(keyBinding);

        final MinecraftClient minecraft = MinecraftClient.getInstance();
        // 监听客户端tick事件，处理按键输入
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.wasPressed()) {
                if (!(minecraft.currentScreen instanceof BasicBrowser)) {
                    // 检查资源包是否完全加载
                    if (!isResourcePackFullyLoaded()) {
                        // 如果资源包未完全加载，给玩家提示
                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.literal("资源包正在加载中，请稍后再试..."), false);
                        }
                        System.out.println("资源包未完全加载，拒绝打开浏览器");
                        return;
                    }

                    // 资源包已完全加载，允许打开浏览器
                    try {
                        System.out.println("资源包已加载完成，打开浏览器");
                        minecraft.setScreen(new BasicBrowser(Text.literal("Basic Browser")));

                    } catch (Exception e) {
                        System.err.println("按键处理时浏览器创建失败: " + e.getMessage());
                        // 发送错误反馈给玩家
                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.literal("浏览器打开失败，请重试"), false);
                        }
                    }
                }
            }
        });
    }
    }

    // H key to open a BasicBrowser screen
//    public static final KeyBinding KEY_MAPPING = new KeyBinding(
//            "Open Basic Browser", InputUtil.Type.KEYSYM,
//            GLFW.GLFW_KEY_H, "key.categories.misc"
//    );

//    public void onTick() {
//        // Check if our key was pressed
//        if (KEY_MAPPING.wasPressed() && !(minecraft.currentScreen instanceof BasicBrowser)) {
//            //Display the web browser UI.
//            minecraft.setScreen(new BasicBrowser(
//                    Text.literal("Basic Browser")
//            ));
//        }
//    }
