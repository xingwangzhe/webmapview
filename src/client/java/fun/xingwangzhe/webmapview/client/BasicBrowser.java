package fun.xingwangzhe.webmapview.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefLoadHandler;
import org.cef.network.CefRequest;
import org.cef.browser.CefFrame;

import java.util.concurrent.CompletableFuture;

import static fun.xingwangzhe.webmapview.client.UrlManager.sendFeedback;

//copy from https://github.com/CinemaMod/mcef-fabric-example-mod and change by xingwangzhe

public class BasicBrowser extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 20;

    private MCEFBrowser browser;
    private boolean isInitializing = false;
    private boolean initializationFailed = false;

    // 添加资源包状态检测
    private boolean resourcesReloading = false;
    private long lastResourceReloadTime = 0;

    // 添加自动恢复机制
    private static int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 2;
    private boolean isAutoRecovering = false;

    private final MinecraftClient minecraft = MinecraftClient.getInstance();

    public BasicBrowser(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();

        if (browser == null && !isInitializing) {
            // 检测资源包状态后再初始化浏览器
            if (isResourcePackReady()) {
                initializeBrowserAsync();
            } else {
                // 如果资源包未准备好，延迟初始化
                waitForResourcePackAndInitialize();
            }
        }

        // 移除所有与 JavaScript 注入相关的代码

        // 删除玩家数据拦截和注入
        // 删除自动注入本地玩家数据
        // 删除 JavaScript 到 Java 的日志桥接
    }

    /**
     * 检测资源包是否已完全加载
     */
    private boolean isResourcePackReady() {
        try {
            // 检查基本资源管理器状态
            if (minecraft.getResourceManager() == null) {
                return false;
            }

            // 检查纹理管理器状态
            if (minecraft.getTextureManager() == null) {
                return false;
            }

            // 检查字体管理器状态
            if (minecraft.textRenderer == null) {
                return false;
            }

            // 检��窗口状态
            if (minecraft.getWindow() == null || minecraft.getWindow().getHandle() == 0) {
                return false;
            }

            // 检查最近是否发生过资源重新加载
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResourceReloadTime < 2000) { // 增加等待时间到2秒
                return false;
            }

            // 检查资源包是否正在重新加载
            if (resourcesReloading) {
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println(Text.translatable("debug.resource_pack.check_failed", e.getMessage()).getString());
            return false;
        }
    }

    /**
     * 等待资源包完全加载后初始化浏览器
     */
    private void waitForResourcePackAndInitialize() {
        resourcesReloading = true;
        System.out.println(Text.translatable("debug.resource_pack.waiting").getString());

        CompletableFuture.runAsync(() -> {
            try {
                int maxRetries = 30; // 最多等待15秒 (30 * 500ms)
                int retryCount = 0;

                while (retryCount < maxRetries && !isResourcePackReady()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    retryCount++;
                }

                // 额外等待确保稳定
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(1000); // 额外等待1秒确保完全稳定
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                minecraft.execute(() -> {
                    resourcesReloading = false;
                    lastResourceReloadTime = System.currentTimeMillis();

                    if (isResourcePackReady()) {
                        System.out.println(Text.translatable("debug.resource_pack.loaded").getString());
                        initializeBrowserAsync();
                    } else {
                        System.err.println(Text.translatable("debug.resource_pack.timeout").getString());
                        initializationFailed = true;
                    }
                });

            } catch (Exception e) {
                resourcesReloading = false;
                initializationFailed = true;
                System.err.println(Text.translatable("debug.resource_pack.error", e.getMessage()).getString());
            }
        });
    }

    private void initializeBrowserAsync() {
        isInitializing = true;
        initializationFailed = false;

        // 异步初始化浏览器 - 移除所有光标操作
        CompletableFuture.runAsync(() -> {
            try {
                // 添加延迟，确保窗口完全初始化
                Thread.sleep(100);

                String url = UrlManager.fullUrl(UrlManager.defaultUrl);
                sendFeedback(url);
                boolean transparent = false;

                // 在主线程中创建浏览器 - 不操作光标
                minecraft.execute(() -> {
                    try {
                        browser = MCEF.createBrowser(url, transparent);
                        if (browser != null) {
                            resizeBrowser();
                            isInitializing = false;
                            retryCount = 0;
                            System.out.println(Text.translatable("debug.browser.initialization.success").getString());

                            // 添加加载完成事件监听器
                            browser.getClient().addLoadHandler(new CefLoadHandler() {
                                @Override
                                public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                                    System.out.println("[BasicBrowser] 网页加载完成，开始注入脚本。");
                                    injectJavaScript(cefBrowser, frame);
                                }

                                @Override
                                public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
                                    System.out.println("[BasicBrowser] 加载状态改变: " + isLoading);
                                }

                                @Override
                                public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
                                    System.out.println("[BasicBrowser] 网页开始加载。");
                                }

                                @Override
                                public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
                                    System.err.println("[BasicBrowser] 网页加载错误: " + errorText);
                                }
                            });
                        } else {
                            System.err.println(Text.translatable("debug.browser.mcef_null").getString());
                            handleInitializationFailure();
                        }
                    } catch (Exception e) {
                        System.err.println(Text.translatable("debug.browser.init_failed_main", e.getMessage()).getString());
                        handleInitializationFailure();
                    }
                });
            } catch (Exception e) {
                System.err.println(Text.translatable("debug.browser.init_failed_async", e.getMessage()).getString());
                minecraft.execute(this::handleInitializationFailure);
            }
        });
    }

    private void injectJavaScript(CefBrowser cefBrowser, CefFrame frame) {
        System.out.println("[BasicBrowser] 开始注入 JavaScript。");
        String script = "(() => {" +
            "    document.body.style.backgroundColor = '#f0f0f0';" +
            "    document.title = 'MCEF 注入示例';" +
            "    const button = document.createElement('button');" +
            "    button.textContent = 'MCEF 注入按钮';" +
            "    button.style.position = 'fixed';" +
            "    button.style.top = '20px';" +
            "    button.style.right = '20px';" +
            "    button.style.padding = '10px';" +
            "    button.style.zIndex = '9999';" +
            "    button.onclick = () => alert('按钮被点击！来自MCEF注入的JS');" +
            "    document.body.appendChild(button);" +
            "})();";
        cefBrowser.executeJavaScript(script, frame.getURL(), 0);
        System.out.println("[BasicBrowser] JavaScript 注入完成。");
    }

    /**
     * 处理初始化失败，决定是否进行自动恢复
     */
    private void handleInitializationFailure() {
        isInitializing = false;

        // 立即尝试自动恢复，不管是第几次失败
        if (retryCount < MAX_RETRY_COUNT && !isAutoRecovering) {
            retryCount++;
            System.out.println(Text.translatable("debug.browser.auto_recovery_starting", retryCount, MAX_RETRY_COUNT).getString());

            // 发送提示消息给玩家
            if (minecraft.player != null) {
                minecraft.player.sendMessage(Text.translatable("browser.initialization.failed.auto_recovery", retryCount, MAX_RETRY_COUNT), false);
            }

            // 立即开始自动恢复���不等待
            startAutoRecovery();
        } else {
            // 达到最大重试次数或已经在恢复中，标记为最终失败
            initializationFailed = true;
            isAutoRecovering = false;

            if (retryCount >= MAX_RETRY_COUNT) {
                System.err.println(Text.translatable("debug.browser.max_retries", MAX_RETRY_COUNT).getString());
                if (minecraft.player != null) {
                    minecraft.player.sendMessage(Text.translatable("browser.initialization.final_failure", MAX_RETRY_COUNT), false);
                    minecraft.player.sendMessage(Text.translatable("browser.initialization.manual_fix_required"), false);
                }
            } else {
                System.err.println(Text.translatable("debug.browser.initialization.failed").getString());
                if (minecraft.player != null) {
                    minecraft.player.sendMessage(Text.translatable("browser.initialization.mcef_check"), false);
                }
            }
        }
    }

    /**
     * 开始自动恢复流程：模拟按下F3+T来刷新资源包
     */
    private void startAutoRecovery() {
        isAutoRecovering = true;

        System.out.println(Text.translatable("debug.browser.auto_recovery_begin").getString());

        CompletableFuture.runAsync(() -> {
            try {
                // 等待一小段时间确保状态稳定
                Thread.sleep(500);

                minecraft.execute(() -> {
                    try {
                        // 模拟按下F3+T组合键来重新加载资源包
                        triggerResourcePackReload();

                        // 等待资源包重新加载完成后重试初始化
                        waitForRecoveryAndRetry();

                    } catch (Exception e) {
                        System.err.println(Text.translatable("debug.browser.auto_recovery_failed", e.getMessage()).getString());
                        isAutoRecovering = false;
                        initializationFailed = true;
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isAutoRecovering = false;
                initializationFailed = true;
            }
        });
    }

    /**
     * 触发资源包重新加载 (模拟F3+T)
     */
    private void triggerResourcePackReload() {
        try {
            System.out.println(Text.translatable("debug.browser.resource_reload_trigger").getString());

            // 设置资源包重新加载状态
            resourcesReloading = true;
            lastResourceReloadTime = System.currentTimeMillis();

            // 调用Minecraft的资源包重新加载方法
            if (minecraft.getResourcePackManager() != null) {
                minecraft.reloadResources();
                System.out.println(Text.translatable("debug.browser.resource_reload_success").getString());
            } else {
                System.err.println(Text.translatable("debug.browser.resource_manager_unavailable").getString());
                throw new RuntimeException("资源包管理器不可用");
            }

        } catch (Exception e) {
            System.err.println(Text.translatable("debug.browser.resource_reload_failed", e.getMessage()).getString());
            throw e;
        }
    }

    /**
     * 等待恢复完成后重新尝试初始化���览器
     */
    private void waitForRecoveryAndRetry() {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println(Text.translatable("debug.browser.waiting_recovery").getString());

                // 等待资源包重新加载完成
                int maxWaitTime = 60; // 最多等待30秒 (60 * 500ms)
                int waitCount = 0;

                while (waitCount < maxWaitTime && (resourcesReloading || !isResourcePackReady())) {
                    try {
                        Thread.sleep(500);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 额外等待确保稳定
                if (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }

                minecraft.execute(() -> {
                    isAutoRecovering = false;
                    resourcesReloading = false;
                    lastResourceReloadTime = System.currentTimeMillis();

                    if (isResourcePackReady()) {
                        System.out.println(Text.translatable("debug.browser.recovery_completed").getString());
                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.translatable("browser.auto_recovery.completed"), false);
                        }

                        // 清理之前失败的状态
                        initializationFailed = false;

                        // 重新尝试初始化
                        initializeBrowserAsync();

                    } else {
                        System.err.println(Text.translatable("debug.browser.recovery_still_failed").getString());
                        initializationFailed = true;

                        if (minecraft.player != null) {
                            minecraft.player.sendMessage(Text.translatable("browser.auto_recovery.failed"), false);
                        }
                    }
                });

            } catch (Exception e) {
                System.err.println(Text.translatable("debug.browser.recovery_error", e.getMessage()).getString());
                minecraft.execute(() -> {
                    isAutoRecovering = false;
                    initializationFailed = true;
                });
            }
        });
    }

    private int mouseX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getScaleFactor());
    }

    private int mouseY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getScaleFactor());
    }

    private int scaleX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getScaleFactor());
    }

    private int scaleY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getScaleFactor());
    }

    private void resizeBrowser() {
        if (browser != null && width > 100 && height > 100) {
            browser.resize(scaleX(width), scaleY(height));
        }
    }

    @Override
    public void resize(MinecraftClient minecraft, int i, int j) {
        super.resize(minecraft, i, j);
        if (browser != null) {
            resizeBrowser();
        }
    }

    @Override
    public void close() {
        // 完全移除光标恢复操作
        // restoreCursorState();

        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                System.err.println(Text.translatable("debug.browser.close_failed", e.getMessage()).getString());
            }
        }
        super.close();
    }

    @Override
    public void render(DrawContext guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);

        // 如果正在自动恢复，显示恢复状态
        if (isAutoRecovering) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                    width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                    0xFF004400);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.auto_recovery.starting").getString(),
                    width / 2, height / 2 - 30, 0xFFFFFF);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.auto_recovery.reloading_resources").getString(),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.auto_recovery.attempt", retryCount, MAX_RETRY_COUNT).getString(),
                    width / 2, height / 2 + 10, 0xFFAAAA);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.auto_recovery.please_wait").getString(),
                    width / 2, height / 2 + 30, 0xFFAAAA);
            return;
        }

        // 如果资源包正在重新加载，显示等待信息
        if (resourcesReloading) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                    width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                    0xFF444444);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.waiting_resource_pack").getString(),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.waiting_resource_pack.then_init").getString(),
                    width / 2, height / 2 + 10, 0xFFAAAA);
            return;
        }

        // 如果正在初始化，显示加载信息
        if (isInitializing) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                           width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                           0xFF333333);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.initializing").getString(),
                                                 width / 2, height / 2, 0xFFFFFF);
            return;
        }

        // 如果初始化失败，显示错误信息
        if (initializationFailed) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                           width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                           0xFF333333);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.failed.title").getString(),
                                                 width / 2, height / 2 - 20, 0xFF0000);
            if (retryCount >= MAX_RETRY_COUNT) {
                guiGraphics.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("browser.failed.tried_times", MAX_RETRY_COUNT).getString(),
                                                     width / 2, height / 2, 0xFFFF00);
                guiGraphics.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("browser.failed.manual_f3t").getString(),
                                                     width / 2, height / 2 + 20, 0xFFFFFF);
            } else {
                guiGraphics.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("browser.failed.check_config").getString(),
                                                     width / 2, height / 2 + 10, 0xFFFF00);
            }
            return;
        }

        // 确保浏览器已初始化
        if (browser == null || browser.getRenderer() == null) {
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.not_initialized").getString(),
                                                 width / 2, height / 2, 0xFF0000);
            return;
        }

        // 获取纹理ID
        int textureId = browser.getRenderer().getTextureID();
        if (textureId <= 0) {
            // 显示加载提示和调试信息
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                           width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                           0xFF333333);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.loading").getString(),
                                                 width / 2, height / 2 - 10, 0xFFFFFF);
            guiGraphics.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("browser.texture_id", textureId).getString(),
                                                 width / 2, height / 2 + 10, 0xFFFF00);
            return;
        }

        // 显示调试信息
        guiGraphics.drawTextWithShadow(textRenderer,
                Text.translatable("browser.texture_id", textureId).getString(), 10, 10, 0xFFFFFF);

        // 使用原始MCEF示例的渲染方式
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        var matrix = guiGraphics.getMatrices().peek().getPositionMatrix();

        float x1 = BROWSER_DRAW_OFFSET;
        float y1 = BROWSER_DRAW_OFFSET;
        float x2 = width - BROWSER_DRAW_OFFSET;
        float y2 = height - BROWSER_DRAW_OFFSET;

        // 使用原始MCEF示例的纹理坐标和颜色
        buffer.vertex(matrix, x1, y2, 0).texture(0.0f, 1.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x2, y2, 0).texture(1.0f, 1.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x2, y1, 0).texture(1.0f, 0.0f).color(255, 255, 255, 255);
        buffer.vertex(matrix, x1, y1, 0).texture(0.0f, 0.0f).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /**
     * 向页面注入玩家数据
     */
    public void injectPlayerData(String playerDataJson) {
        if (browser != null) {
            String script = String.format("window.updatePlayerData(%s);", playerDataJson);
            browser.executeJavaScript(script,"",0);
        }
    }

    /**
     * 拦截并注入本地生成的玩家数据到指定的 URL。
     */
    private void interceptAndInjectPlayerData() {
        if (browser != null) {
            String script = "(() => {" +
                "    const originalFetch = window.fetch;" +
                "    window.fetch = async function(resource, config) {" +
                "        if (typeof resource === 'string' && resource.includes('tiles/players.json')) {" +
                "            console.log('[Intercept] Fetching players.json:', resource);" +
                "            const localData = " + generatePlayerJsonScript() + ";" +
                "            return new Response(JSON.stringify(localData), {" +
                "                status: 200," +
                "                headers: { 'Content-Type': 'application/json' }" +
                "            });" +
                "        }" +
                "        return originalFetch.apply(this, arguments);" +
                "    };" +
                "    console.log('[Intercept] Fetch override installed.');" +
                "})();";

            browser.executeJavaScript(script, "", 0);
        }
    }

    /**
     * 生成用于注入的玩家数据的 JSON 脚本。
     * @return JavaScript 格式的 JSON 数据
     */
    private String generatePlayerJsonScript() {
        // 示例 JSON 数据，可以替换为动态生成的内容
        return "{" +
            "\"max\": 200," +
            "\"players\": [" +
            "    {\"world\": \"minecraft_overworld\", \"armor\": 0, \"name\": \"BVVD\", \"x\": 230150, \"y\": 66, \"health\": 40, \"z\": -60139, \"display_name\": \"BVVD\", \"uuid\": \"289877384c6c30c1aa11d6d8ff63d871\", \"yaw\": 62}" +
            "]}";
    }

    /**
     * 调用 PlayerInformation 的本地 JSON 生成方法，并注入到浏览器。
     */
    private void injectLocalPlayerData() {
        if (browser != null) {
            String localPlayerJson = PlayerInformation.generateLocalPlayerJson();
            String script = String.format(
                "(() => {" +
                "    const localData = %s;" +
                "    window.updatePlayerData(localData);" +
                "    console.log('[Inject] Local player data injected:', localData);" +
                "})();",
                localPlayerJson
            );
            browser.executeJavaScript(script, "", 0);
        }
    }

    /**
     * 添加 JavaScript 到 Java 的日志桥接
     */
    private void addJavaScriptToJavaLogging() {
        if (browser != null) {
            String script = "(() => {" +
                "    window.java = {" +
                "        log: (message) => {" +
                "            console.log('[JavaScript -> Java] ' + message);" +
                "            fetch('http://localhost:8080/log', {" +
                "                method: 'POST'," +
                "                body: JSON.stringify({message})" +
                "            });" +
                "        }" +
                "    };" +
                "})();";
            browser.executeJavaScript(script, "", 0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser == null || browser.getRenderer() == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        try {
            browser.sendMousePress(mouseX(mouseX), mouseY(mouseY), button);
            browser.setFocus(true);
        } catch (Exception e) {
            // 忽略浏览器交互错误，避免崩溃
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser == null || browser.getRenderer() == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        try {
            browser.sendMouseRelease(mouseX(mouseX), mouseY(mouseY), button);
            browser.setFocus(true);
        } catch (Exception e) {
            // 忽略浏览器交互错误，避免崩溃
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY));
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY));
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), verticalAmount, 0);
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 处理ESC键关闭浏览器
        if (keyCode == 256) { // GLFW.GLFW_KEY_ESCAPE = 256
            this.close();
            return true;
        }

        // 动态检测切换按键关闭浏览器（支持玩家自定义按键绑定）
        if (WebmapviewClient.isToggleKey(keyCode)) {
            System.out.println("[DEBUG] Toggle key detected in BasicBrowser, closing...");
            this.close();
            return true;
        }

        // 其他所有按键都不处理，直接让游戏系统处理
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // 不处理任何按键释放事件，让游戏系统处理
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // 不处理任何字符输入，让游戏系统处理
        return super.charTyped(codePoint, modifiers);
    }

    private void addLoadHandlerForJavaScriptInjection() {
        browser.getClient().addLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                String script = "(() => {" +
                    "    document.body.style.backgroundColor = '#f0f0f0';" +
                    "    document.title = 'MCEF 注入示例';" +
                    "    const button = document.createElement('button');" +
                    "    button.textContent = 'MCEF 注入按钮';" +
                    "    button.style.position = 'fixed';" +
                    "    button.style.top = '20px';" +
                    "    button.style.right = '20px';" +
                    "    button.style.padding = '10px';" +
                    "    button.style.zIndex = '9999';" +
                    "    button.onclick = () => alert('按钮被点击！来自MCEF注入的JS');" +
                    "    document.body.appendChild(button);" +
                    "})();";
                cefBrowser.executeJavaScript(script, frame.getURL(), 0);
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {}

            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {}

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {}
        });
    }
}
