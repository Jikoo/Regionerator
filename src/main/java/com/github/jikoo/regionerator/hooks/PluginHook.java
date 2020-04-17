package com.github.jikoo.regionerator.hooks;

import org.bukkit.Bukkit;

/**
 * An extension of the {@link Hook} framework for depending on specific {@link org.bukkit.plugin.Plugin}s.
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

	/**
	 * Gets the name of the {@link org.bukkit.plugin.Plugin} being hooked.
	 *
	 * @return the name of the {@code Plugin}
	 */
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
