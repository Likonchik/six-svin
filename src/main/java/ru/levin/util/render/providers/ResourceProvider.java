package ru.levin.util.render.providers;

import net.minecraft.resources.ResourceLocation;

// Image/texture resource locations. The custom core shaders moved to ShaderRegistry
// (1.21.1 has no ShaderProgramKey/Defines). Resource namespace stays "exosware".
public final class ResourceProvider {

	public static final ResourceLocation firefly = id("images/particles/firefly.png");
	public static final ResourceLocation bloom = id("images/particles/bloom.png");
	public static final ResourceLocation snowflake = id("images/particles/snowflake.png");
	public static final ResourceLocation dollar = id("images/particles/dollar.png");
	public static final ResourceLocation heart = id("images/particles/heart.png");
	public static final ResourceLocation star = id("images/particles/star.png");
	public static final ResourceLocation spark = id("images/particles/spark.png");
	public static final ResourceLocation crown = id("images/particles/crown.png");
	public static final ResourceLocation lightning = id("images/particles/lightning.png");
	public static final ResourceLocation line = id("images/particles/line.png");
	public static final ResourceLocation point = id("images/particles/point.png");
	public static final ResourceLocation rhombus = id("images/particles/rhombus.png");

	public static final ResourceLocation marker = id("images/targetesp/target.png");
	public static final ResourceLocation marker2 = id("images/targetesp/target2.png");

	public static final ResourceLocation CUSTOM_CAPE = id("cape/cape.png");
	public static final ResourceLocation CUSTOM_ELYTRA = id("cape/elytra.png");

	public static final ResourceLocation container = id("images/hud/container.png");

	public static final ResourceLocation color_image = id("images/gui/pick.png");

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("exosware", path);
	}
}
