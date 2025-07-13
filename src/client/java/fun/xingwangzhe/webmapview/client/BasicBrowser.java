package fun.xingwangzhe.webmapview.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

import static fun.xingwangzhe.webmapview.client.UrlManager.sendFeedback;

//copy from https://github.com/CinemaMod/mcef-fabric-example-mod and change by xingwangzhe

public class BasicBrowser extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 20;

    private MCEFBrowser browser;
    private boolean isInitializing = false;
    private boolean initializationFailed = false;

    private final MinecraftClient minecraft = MinecraftClient.getInstance();

    public BasicBrowser(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();

        // 更安全的鼠标聚焦清除方式，防止OpenGL错误
        try {
            if (minecraft.mouse != null) {
                // 确保窗口处于正确状态
                if (minecraft.getWindow() != null && minecraft.getWindow().getHandle() != 0) {
                    // 重置鼠标状态
                    minecraft.mouse.unlockCursor();
                }
            }
        } catch (Exception e) {
            System.err.println("鼠标聚焦清除失败: " + e.getMessage());
        }

        if (browser == null && !isInitializing) {
            initializeBrowserAsync();
        }
    }

    private void initializeBrowserAsync() {
        isInitializing = true;
        initializationFailed = false;

        // 异步初始化浏览器
        CompletableFuture.runAsync(() -> {
            try {
                // 添加延迟，确保窗口完全初始化
                Thread.sleep(100);

                String url = UrlManager.fullUrl(UrlManager.defaultUrl);
                sendFeedback(url);
                boolean transparent = false;

                // 在主线程中创建浏览器
                minecraft.execute(() -> {
                    try {
                        // 再次确保鼠标状态正确
                        if (minecraft.mouse != null && minecraft.getWindow() != null) {
                            minecraft.mouse.unlockCursor();
                        }

                        browser = MCEF.createBrowser(url, transparent);
                        if (browser != null) {
                            resizeBrowser();
                            browser.setFocus(true);
                            isInitializing = false;
                        } else {
                            initializationFailed = true;
                            isInitializing = false;
                        }
                    } catch (Exception e) {
                        System.err.println("浏览器初始化失败 (主线程): " + e.getMessage());
                        initializationFailed = true;
                        isInitializing = false;
                    }
                });
            } catch (Exception e) {
                System.err.println("浏览器初始化失败 (异步线程): " + e.getMessage());
                initializationFailed = true;
                isInitializing = false;
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
        // 关闭浏览器前清理鼠标状态
        try {
            if (minecraft.mouse != null) {
                minecraft.mouse.unlockCursor();
            }
        } catch (Exception e) {
            System.err.println("关闭时鼠标状态清理失败: " + e.getMessage());
        }

        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                System.err.println("浏览器关闭失败: " + e.getMessage());
            }
        }
        super.close();
    }

    @Override
    public void render(DrawContext guiGraphics, int i, int j, float f) {
        // 在每次渲染前确保鼠标状态正确
        try {
            if (minecraft.mouse != null && minecraft.getWindow() != null) {
                // 确保光标处于正确状态，避免OpenGL错误
                if (minecraft.mouse.isCursorLocked()) {
                    minecraft.mouse.unlockCursor();
                }
            }
        } catch (Exception e) {
            // 静默处理光标错误，避免日志污染
        }

        super.render(guiGraphics, i, j, f);

        // 如果正在初始化，显示加载信息
        if (isInitializing) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                           width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                           0xFF333333);
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "正在异步加载浏览器...",
                                                 width / 2, height / 2, 0xFFFFFF);
            return;
        }

        // 如果初始化失败，显示错误信息
        if (initializationFailed) {
            guiGraphics.fill(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET,
                           width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET,
                           0xFF333333);
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "浏览器初始化失败",
                                                 width / 2, height / 2 - 10, 0xFF0000);
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "请重试或检查MCEF配置",
                                                 width / 2, height / 2 + 10, 0xFFFF00);
            return;
        }

        // 确保浏览器已初始化
        if (browser == null || browser.getRenderer() == null) {
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "浏览器未初始化",
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
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "正在加载浏览器...",
                                                 width / 2, height / 2 - 10, 0xFFFFFF);
            guiGraphics.drawCenteredTextWithShadow(textRenderer, "纹理ID: " + textureId,
                                                 width / 2, height / 2 + 10, 0xFFFF00);
            return;
        }

        // 显示调试信息
        guiGraphics.drawTextWithShadow(textRenderer, "纹理ID: " + textureId, 10, 10, 0xFFFFFF);

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
            // 忽略浏览器交互错误��避免崩溃
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
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendKeyPress(keyCode, scanCode, modifiers);
                browser.setFocus(true);
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendKeyRelease(keyCode, scanCode, modifiers);
                browser.setFocus(true);
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == (char) 0) return false;
        if (browser != null && browser.getRenderer() != null) {
            try {
                browser.sendKeyTyped(codePoint, modifiers);
                browser.setFocus(true);
            } catch (Exception e) {
                // 忽略浏览器交互错误，避免崩溃
            }
        }
        return super.charTyped(codePoint, modifiers);
    }
}
