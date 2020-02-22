package com.github.jikoo.regionerator.hooks;

import com.jcdesimp.landlord.persistantData.OwnedLand;

import org.bukkit.World;

/**
 * PluginHook for <a href=http://dev.bukkit.org/bukkit-plugins/landlord/>Landlord</a>.
 *
 * @author Jikoo
 */
public class LandlordHook extends PluginHook {

	public LandlordHook() {
		super("Landlord");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return OwnedLand.getLandFromDatabase(chunkX, chunkZ, chunkWorld.getName()) != null;
	}

	@Override
	public boolean isAsyncCapable() {
		return true;
	}

}
