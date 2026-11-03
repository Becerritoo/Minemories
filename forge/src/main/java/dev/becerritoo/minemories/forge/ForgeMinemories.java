package dev.becerritoo.minemories.forge;

import dev.becerritoo.minemories.client.MinemoriesClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(MinemoriesClient.MOD_ID)
public final class ForgeMinemories {
    public ForgeMinemories(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClient.initialize();
        }
    }
}
