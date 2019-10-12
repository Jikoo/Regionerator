package com.github.jikoo.regionerator.hooks;

import org.bukkit.Bukkit;

/**
 * Framework for plugin hooks.
 *
 * @author Jikoo
 */
public abstract class PluginHook extends Hook {

	public PluginHook(String pluginName) {
		super(pluginName);
	}

	@Override
	public String getProtectionName() {
		return "Plugin:" + super.getProtectionName();
	}

	public String getPluginName() {
		return super.getProtectionName();
	}

	@Override
	public boolean isHookUsable() {
		// TODO rework for autopause if present but disabled
		return Bukkit.getPluginManager().getPlugin(getPluginName()) != null && super.isHookUsable();
	}

}
