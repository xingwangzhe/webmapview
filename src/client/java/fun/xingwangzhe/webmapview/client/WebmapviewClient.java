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
    private static WebmapviewClient instance;
    private static long lastResourcePackCheckTime = 0;

    public static KeyBinding getToggleKeyBinding() {
        return instance != null ? instance.keyBinding : null;
    }

    public static boolean isToggleKey(int keyCode) {
        KeyBinding binding = getToggleKeyBinding();
        if (binding != null) {
            return binding.matchesKey(keyCode, 0);
        }
        return false;
    }

    private static boolean isResourcePackFullyLoaded() {
        try {
            MinecraftClient minecraft = MinecraftClient.getInstance();

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

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResourcePackCheckTime < 3000) {
                return false;
            }

            lastResourcePackCheckTime = currentTime;
            return true;

        } catch (Exception e) {
            System.err.println(Text.translatable("debug.resource_pack.check_failed", e.getMessage()).getString());
            return false;
        }
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("urladd")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string())
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.addUrl(url);
                                        return 1;
                                    })
                            )
            );

            dispatcher.register(
                    ClientCommandManager.literal("urlremove")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string()).suggests(WebmapviewClient::suggestUrls)
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.removeUrl(url);
                                        return 1;
                                    })
                            )
            );

            dispatcher.register(
                    ClientCommandManager.literal("urllist")
                            .executes(context -> {
                                List<String> urls = UrlManager.getUrlList();
                                StringBuilder listMessage = new StringBuilder("Available URLs:\n");
                                for (int i = 0; i < urls.size(); i++) {
                                    listMessage.append(i + 1).append(": ").append(urls.get(i)).append("\n");
                                }
                                context.getSource().sendFeedback(Text.of(listMessage.toString()));
                                return 1;
                            })
            );
            dispatcher.register(
                    ClientCommandManager.literal("urlset")
                            .then(ClientCommandManager.argument("url", StringArgumentType.string()).suggests(WebmapviewClient::suggestUrls)
                                    .executes(context -> {
                                        String url = StringArgumentType.getString(context, "url");
                                        UrlManager.setDefaultUrl(url);
                                        return 1;
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

        keyBinding = new KeyBinding(
                "key.webmapview.open_basic_browser",
                GLFW.GLFW_KEY_H,
                "category.webmapview"
        );

        KeyBindingHelper.registerKeyBinding(keyBinding);

        final MinecraftClient minecraft = MinecraftClient.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyBinding.wasPressed()) {
                System.out.println("[DEBUG] KeyBinding detected! Current screen: " + (minecraft.currentScreen == null ? "null" : minecraft.currentScreen.getClass().getSimpleName()));

                if(minecraft.currentScreen instanceof BasicBrowser) {
                    System.out.println("[DEBUG] Closing BasicBrowser");
                    minecraft.setScreen(null);
                }
                else {
                    System.out.println("[DEBUG] Opening BasicBrowser");
                    if (!isResourcePackFullyLoaded()) {
                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.translatable("browser.resource_pack.not_loaded"), false);
                        }
                        System.out.println(Text.translatable("debug.key_handler.resource_not_ready").getString());
                        return;
                    }

                    try {
                        System.out.println(Text.translatable("browser.resource_pack.ready").getString());
                        minecraft.setScreen(new BasicBrowser(Text.translatable("browser.title")));

                    } catch (Exception e) {
                        System.err.println(Text.translatable("debug.key_handler.creation_failed", e.getMessage()).getString());
                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.translatable("browser.creation.failed"), false);
                        }
                    }
                }
            }
        });
    }
}