package net.minecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class Screenshot {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String SCREENSHOT_DIR = "screenshots";
    private int rowHeight;
    private final DataOutputStream outputStream;
    private final byte[] bytes;
    private final int width;
    private final int height;
    private File file;

    /**
     * Saves a screenshot in the game directory with a time-stamped filename.
     */
    public static void grab(File gameDirectory, RenderTarget buffer, Consumer<Component> messageConsumer) {
        grab(gameDirectory, null, buffer, messageConsumer);
    }

    /**
     * Saves a screenshot in the game directory with the given file name (or null to generate a time-stamped name).
     */
    public static void grab(File gameDirectory, @Nullable String screenshotName, RenderTarget buffer, Consumer<Component> messageConsumer) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> _grab(gameDirectory, screenshotName, buffer, messageConsumer));
        } else {
            _grab(gameDirectory, screenshotName, buffer, messageConsumer);
        }
    }

    private static void _grab(File gameDirectory, @Nullable String screenshotName, RenderTarget buffer, Consumer<Component> messageConsumer) {
        NativeImage nativeimage = takeScreenshot(buffer);
        File file1 = new File(gameDirectory, "screenshots");
        file1.mkdir();
        File file2;
        if (screenshotName == null) {
            file2 = getFile(file1);
        } else {
            file2 = new File(file1, screenshotName);
        }

        net.neoforged.neoforge.client.event.ScreenshotEvent event = net.neoforged.neoforge.client.ClientHooks.onScreenshot(nativeimage, file2);
        if (event.isCanceled()) {
            messageConsumer.accept(event.getCancelMessage());
            return;
        }
        final File target = event.getScreenshotFile();

        Util.ioPool()
            .execute(
                () -> {
                    try {
                        nativeimage.writeToFile(target);
                        Component component = Component.literal(target.getName())
                            .withStyle(ChatFormatting.UNDERLINE)
                            .withStyle(p_168608_ -> p_168608_.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, target.getAbsolutePath())));
                        if (event.getResultMessage() != null)
                            messageConsumer.accept(event.getResultMessage());
                        else
                            messageConsumer.accept(Component.translatable("screenshot.success", component));
                    } catch (Exception exception) {
                        LOGGER.warn("Couldn't save screenshot", (Throwable)exception);
                        messageConsumer.accept(Component.translatable("screenshot.failure", exception.getMessage()));
                    } finally {
                        nativeimage.close();
                    }
                }
            );
    }

    public static NativeImage takeScreenshot(RenderTarget framebuffer) {
        int i = framebuffer.width;
        int j = framebuffer.height;
        NativeImage nativeimage = new NativeImage(i, j, false);
        RenderSystem.bindTexture(framebuffer.getColorTextureId());
        nativeimage.downloadTexture(0, true);
        nativeimage.flipY();
        return nativeimage;
    }

    /**
     * Creates a unique PNG file in the given directory named by a timestamp. Handles cases where the timestamp alone is not enough to create a uniquely named file, though it still might suffer from an unlikely race condition where the filename was unique when this method was called, but another process or thread created a file at the same path immediately after this method returned.
     */
    private static File getFile(File gameDirectory) {
        String s = Util.getFilenameFormattedDateTime();
        int i = 1;

        while (true) {
            File file1 = new File(gameDirectory, s + (i == 1 ? "" : "_" + i) + ".png");
            if (!file1.exists()) {
                return file1;
            }

            i++;
        }
    }

    public Screenshot(File gameDirectory, int width, int height, int rowHeight) throws IOException {
        this.width = width;
        this.height = height;
        this.rowHeight = rowHeight;
        File file1 = new File(gameDirectory, "screenshots");
        file1.mkdir();
        String s = "huge_" + Util.getFilenameFormattedDateTime();
        int i = 1;

        while ((this.file = new File(file1, s + (i == 1 ? "" : "_" + i) + ".tga")).exists()) {
            i++;
        }

        byte[] abyte = new byte[18];
        abyte[2] = 2;
        abyte[12] = (byte)(width % 256);
        abyte[13] = (byte)(width / 256);
        abyte[14] = (byte)(height % 256);
        abyte[15] = (byte)(height / 256);
        abyte[16] = 24;
        this.bytes = new byte[width * rowHeight * 3];
        this.outputStream = new DataOutputStream(new FileOutputStream(this.file));
        this.outputStream.write(abyte);
    }

    public void addRegion(ByteBuffer buffer, int width, int height, int rowWidth, int rowHeight) {
        int i = rowWidth;
        int j = rowHeight;
        if (rowWidth > this.width - width) {
            i = this.width - width;
        }

        if (rowHeight > this.height - height) {
            j = this.height - height;
        }

        this.rowHeight = j;

        for (int k = 0; k < j; k++) {
            buffer.position((rowHeight - j) * rowWidth * 3 + k * rowWidth * 3);
            int l = (width + k * this.width) * 3;
            buffer.get(this.bytes, l, i * 3);
        }
    }

    public void saveRow() throws IOException {
        this.outputStream.write(this.bytes, 0, this.width * 3 * this.rowHeight);
    }

    public File close() throws IOException {
        this.outputStream.close();
        return this.file;
    }
}
