package com.github.jikoo.regionerator.hooks;

import org.bukkit.World;

import com.github.jikoo.regionerator.Hook;

import com.jcdesimp.landlord.persistantData.OwnedLand;

/**
 * Hook for the protection plugin <a href=http://dev.bukkit.org/bukkit-plugins/landlord/>Landlord</a>.
 * 
 * @author Jikoo
 */
public class LandlordHook extends Hook {

	public LandlordHook() {
		super("Landlord");
	}

	@Override
	public boolean isChunkProtected(World chunkWorld, int chunkX, int chunkZ) {
		return OwnedLand.getLandFromDatabase(chunkX, chunkX, chunkWorld.getName()) != null;
	}

}
