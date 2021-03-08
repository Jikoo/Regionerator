/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.listeners;

import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.hooks.PluginHook;
import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * Listener for hooked plugins being enabled or disabled.
 */
public class HookListener implements Listener {

	private final Regionerator plugin;

	public HookListener(Regionerator plugin) {
		this.plugin = plugin;
	}

	/**
	 * Dynamically enable {@link PluginHook}s if they are configured to be used.
	 *
	 * @param event the {@link PluginEnableEvent}
	 */
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
			plugin.getLogger().log(Level.SEVERE, "Unable to enable hook for " + pluginName + "!", e);
		}
	}

	/**
	 * Dynamically disable {@link PluginHook}s if they are in use.
	 *
	 * @param event the {@link PluginDisableEvent}
	 */
	@EventHandler
	public void onPluginDisable(PluginDisableEvent event) {
		String pluginName = event.getPlugin().getName();
		for (Hook hook : this.plugin.getProtectionHooks()) {
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
