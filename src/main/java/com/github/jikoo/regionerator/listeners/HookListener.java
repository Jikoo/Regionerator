package com.github.jikoo.regionerator.listeners;

import java.util.Iterator;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.hooks.PluginHook;
import com.github.jikoo.regionerator.Regionerator;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

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

	@EventHandler
	public void onPluginEnable(PluginEnableEvent event) {
		String pluginName = event.getPlugin().getName();
		if (!plugin.getConfig().getBoolean("hooks." + pluginName)) {
			return;
		}
		try {
			Class<?> clazz = Class.forName("com.github.jikoo.regionerator.hooks." + pluginName + "Hook");
			if (!PluginHook.class.isAssignableFrom(clazz)) {
				// What.
				return;
			}
			PluginHook pluginHook = (PluginHook) clazz.getDeclaredConstructor().newInstance();
			if (!pluginHook.isHookUsable()) {
				plugin.getLogger().severe("Hook for " + pluginName + " failed usability check and could not be enabled!");
				return;
			}
			plugin.addHook(pluginHook);
			plugin.debug(DebugLevel.LOW, () -> "Enabled protection hook for " + pluginName);
		} catch (IllegalStateException e) {
			plugin.getLogger().severe("Tried to add hook for " + pluginName + ", but it was already enabled!");
		} catch (ClassNotFoundException e) {
			plugin.getLogger().severe("No hook found for " + pluginName + "! Please request compatibility!");
		} catch (ReflectiveOperationException e) {
			plugin.getLogger().severe("Unable to enable hook for " + pluginName + "!");
			e.printStackTrace();
		}
	}

	@EventHandler
	public void onPluginDisable(PluginDisableEvent event) {
		String pluginName = event.getPlugin().getName();
		Iterator<Hook> iterator = this.plugin.getProtectionHooks().iterator();
		while (iterator.hasNext()) {
			Hook hook = iterator.next();
			if (!(hook instanceof PluginHook)) {
				continue;
			}
			if (pluginName.equals(((PluginHook) hook).getPluginName())) {
				// Won't CME; we return after modification.
				this.plugin.removeHook(hook);
				plugin.debug(DebugLevel.LOW, () -> "Disabled protection hook for " + pluginName);
				return;
			}
		}
	}

}
