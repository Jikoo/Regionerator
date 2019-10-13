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
	public boolean areDependenciesPresent() {
		return Bukkit.getPluginManager().getPlugin(getPluginName()) != null;
	}

	@Override
	public boolean isHookUsable() {
		return Bukkit.getPluginManager().isPluginEnabled(getPluginName()) && super.isHookUsable();
	}

}
