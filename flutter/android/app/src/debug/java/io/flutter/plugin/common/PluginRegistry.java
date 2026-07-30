package io.flutter.plugin.common;

/**
 * Compatibility stub for v1 embedding removal in Flutter 3.29+.
 * Old plugins reference PluginRegistry.Registrar which was removed.
 * The pinned Flutter 3.24.5 release engine already provides the real class,
 * so this stub must remain debug-only to avoid duplicate release dex entries.
 */
public class PluginRegistry {
    public interface Registrar {
        // stub - v1 embedding compatibility
    }
}
