package cn.popcraft.villagerpro.config;

import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptionTest {
    @Test
    void pluginDescriptorDeclaresExpandedCommandsAndPermissions() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(input);
            PluginDescriptionFile description = new PluginDescriptionFile(input);
            assertTrue(description.getCommands().containsKey("village"));
            assertTrue(hasPermission(description, "villagerpro.village.building"));
            assertTrue(hasPermission(description, "villagerpro.village.market"));
            assertTrue(hasPermission(description, "villagerpro.village.policy"));
            assertTrue(hasPermission(description, "villagerpro.village.crisis"));
            assertTrue(hasPermission(description, "villagerpro.village.crisis.trigger"));
            assertTrue(hasPermission(description, "villagerpro.village.caravan"));
            assertTrue(hasPermission(description, "villagerpro.village.orders.resolve"));
            assertTrue(hasPermission(description, "villagerpro.database.migrate"));
        }
    }

    private static boolean hasPermission(PluginDescriptionFile description, String name) {
        return description.getPermissions().stream()
                .map(Permission::getName)
                .anyMatch(name::equals);
    }
}
