package com.github.jikoo.regionerator.listeners;

import java.util.Iterator;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Hook;
import com.github.jikoo.regionerator.PluginHook;
import com.github.jikoo.regionerator.Regionerator;

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
			PluginHook pluginHook = (PluginHook) clazz.newInstance();
			// No need to check if the hook is usable, we know the plugin required is present
			plugin.addHook(pluginHook);
			if (plugin.debug(DebugLevel.LOW)) {
				plugin.debug("Enabled protection hook for " + pluginName);
			}
		} catch (IllegalStateException e) {
			plugin.getLogger().severe("Tried to add hook for " + pluginName + ", but it was already enabled!");
		} catch (ClassNotFoundException e) {
			plugin.getLogger().severe("No hook found for " + pluginName + "! Please request compatibility!");
		} catch (InstantiationException | IllegalAccessException e) {
			plugin.getLogger().severe("Unable to enable hook for " + pluginName + "!");
			e.printStackTrace();
		}
	}

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
				if (this.plugin.debug(DebugLevel.LOW)) {
					this.plugin.debug("Disabled protection hook for " + pluginName);
				}
				return;
			}
		}
	}

}
