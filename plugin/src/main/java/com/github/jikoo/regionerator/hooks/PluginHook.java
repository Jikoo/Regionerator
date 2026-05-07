/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.hooks;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * An extension of the {@link Hook} framework for depending on specific {@link org.bukkit.plugin.Plugin Plugins}.
 */
public abstract class PluginHook extends Hook {

	public PluginHook(@NotNull String pluginName) {
		super(pluginName);
	}

	@Override
	public @NotNull String getProtectionName() {
		return "Plugin:" + super.getProtectionName();
	}

	/**
	 * Gets the name of the {@link org.bukkit.plugin.Plugin} being hooked.
	 *
	 * @return the name of the {@code Plugin}
	 */
	public @NotNull String getPluginName() {
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
