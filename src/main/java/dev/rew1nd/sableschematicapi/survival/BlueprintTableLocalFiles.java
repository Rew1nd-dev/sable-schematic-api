package dev.rew1nd.sableschematicapi.survival;

import java.util.List;

/**
 * Common bridge for local blueprint files shown by the blueprint table UI.
 *
 * <p>The server can construct the table UI, so the common UI must not directly
 * load client-only file or Minecraft classes. The client registers a provider
 * during client setup; dedicated servers keep the empty default provider.</p>
 */
public final class BlueprintTableLocalFiles {
    private static Provider provider = Provider.EMPTY;

    private BlueprintTableLocalFiles() {
    }

    public static void setProvider(final Provider provider) {
        BlueprintTableLocalFiles.provider = provider == null ? Provider.EMPTY : provider;
    }

    public static List<LocalFile> localFiles() {
        return provider.localFiles();
    }

    public static int revision() {
        return provider.revision();
    }

    public static void refresh() {
        provider.refresh();
    }

    public static void requestUpload(final String name) {
        provider.requestUpload(name);
    }

    public record LocalFile(String name) {
    }

    public interface Provider {
        Provider EMPTY = new Provider() {
            @Override
            public List<LocalFile> localFiles() {
                return List.of();
            }

            @Override
            public int revision() {
                return 0;
            }

            @Override
            public void refresh() {
            }

            @Override
            public void requestUpload(final String name) {
            }
        };

        List<LocalFile> localFiles();

        int revision();

        void refresh();

        void requestUpload(String name);
    }
}
