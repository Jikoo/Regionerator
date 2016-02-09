package com.github.jikoo.regionerator.listeners;

import java.util.Iterator;

import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Hook;
import com.github.jikoo.regionerator.Regionerator;

/**
 * Listener for hooked plugins being enabled or disabled.
 * 
 * @author Jikoo
 */
public class HookListener implements Listener {

	private final Regionerator plugin;

	public HookListener(Regionerator plugin) {
		this.plugin = plugin;
	}

	public void onPluginEnable(PluginEnableEvent event) {
		String pluginName = event.getPlugin().getName();
		if (!plugin.getConfig().getBoolean("hooks." + pluginName)) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.github.jikoo.regionerator.hooks." + pluginName + "Hook");
			if (!Hook.class.isAssignableFrom(clazz)) {
				// What.
				return;
			}
			Hook hook = (Hook) clazz.newInstance();
			// No need to check if the hook is usable, we know the plugin required is present
			plugin.getProtectionHooks().add(hook);
			if (plugin.debug(DebugLevel.LOW)) {
				plugin.debug("Enabled protection hook for " + pluginName);
			}
		} catch (ClassNotFoundException e) {
			plugin.getLogger().severe("No hook found for " + pluginName + "! Please request compatibility!");
		} catch (InstantiationException | IllegalAccessException e) {
			plugin.getLogger().severe("Unable to enable hook for " + pluginName + "!");
			e.printStackTrace();
		}
	}

	public void onPluginDisable(PluginDisableEvent event) {
		String pluginName = event.getPlugin().getName();
		Iterator<Hook> iterator = plugin.getProtectionHooks().iterator();
		while (iterator.hasNext()) {
			if (pluginName.equals(iterator.next().getPluginName())) {
				iterator.remove();
				if (plugin.debug(DebugLevel.LOW)) {
					plugin.debug("Disabled protection hook for " + pluginName);
				}
				return;
			}
		}
	}

}
