package com.github.jikoo.regionerator.hooks;

import com.jcdesimp.landlord.persistantData.OwnedLand;

import com.github.jikoo.regionerator.Hook;

import org.bukkit.World;

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
