package ru.levin.util.render.providers;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * 1.21.1 replacement for the 1.21.4 ShaderProgramKey/Defines core-shader pipeline.
 * Custom GLSL core shaders are registered via NeoForge's RegisterShadersEvent (mod bus) and
 * held as static {@link ShaderInstance} fields. Call sites do:
 *   ShaderInstance s = ShaderRegistry.RECTANGLE;
 *   s.getUniform("Size").set(w, h); ...
 *   RenderSystem.setShader(() -> s);
 *   // then build + draw the buffer
 * Resource namespace stays "exosware"; the mod id is "onetap".
 */
@EventBusSubscriber(modid = "onetap", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShaderRegistry {
    public static ShaderInstance TEXTURE;
    public static ShaderInstance RECTANGLE;
    public static ShaderInstance BLUR;
    public static ShaderInstance BORDER;
    public static ShaderInstance GLASS;

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("texture"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> TEXTURE = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("rectangle"), DefaultVertexFormat.POSITION_COLOR), s -> RECTANGLE = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("blur"), DefaultVertexFormat.POSITION_COLOR), s -> BLUR = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("border"), DefaultVertexFormat.POSITION_COLOR), s -> BORDER = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("glass/data"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> GLASS = s);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("exosware", path);
    }
}
