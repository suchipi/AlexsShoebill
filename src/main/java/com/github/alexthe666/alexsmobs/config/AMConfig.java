package com.github.alexthe666.alexsmobs.config;

import com.github.alexthe666.alexsmobs.AlexsMobs;

import net.minecraftforge.fml.config.ModConfig;


public class AMConfig {
    public static int shoebillSpawnWeight = 10;
    public static int shoebillSpawnRolls = 0;

    public static void bake(ModConfig config) {
        try {
            shoebillSpawnWeight = ConfigHolder.COMMON.shoebillSpawnWeight.get();
            shoebillSpawnRolls = ConfigHolder.COMMON.shoebillSpawnRolls.get();
        } catch (Exception e) {
            AlexsMobs.LOGGER.warn("An exception was caused trying to load the config for Alex's Mobs.");
            e.printStackTrace();
        }
    }

}
