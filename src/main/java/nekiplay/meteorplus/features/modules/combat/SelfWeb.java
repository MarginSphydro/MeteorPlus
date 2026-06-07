package nekiplay.meteorplus.features.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class SelfWeb extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final SettingGroup sgRender  = settings.createGroup("Render");

	private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
		.name("delay")
		.description("放置间隔（Ticks）。")
		.defaultValue(2)
		.min(0)
		.sliderMax(20)
		.build()
	);

	private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
		.name("blocks-per-tick")
		.description("每 Tick 最多放置多少个蜘蛛网。")
		.defaultValue(1)
		.min(1)
		.sliderMax(4)
		.build()
	);

	private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
		.name("rotate")
		.description("放置时朝向目标位置。")
		.defaultValue(true)
		.build()
	);

	private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
		.name("silent-switch")
		.description("静默切换到蜘蛛网，无热键栏闪动。")
		.defaultValue(true)
		.build()
	);

	private final Setting<Boolean> eatingPause = sgGeneral.add(new BoolSetting.Builder()
		.name("pause-while-eating")
		.description("吃东西时暂停放置。")
		.defaultValue(false)
		.build()
	);

	private final Setting<Double> epsilon = sgGeneral.add(new DoubleSetting.Builder()
		.name("epsilon")
		.description("Hitbox 内缩量，避免边界精度问题。")
		.defaultValue(0.03)
		.min(0.0)
		.max(0.1)
		.sliderMax(0.1)
		.build()
	);

	private final Setting<Boolean> extraPlace = sgGeneral.add(new BoolSetting.Builder()
		.name("extra-place")
		.description("额外放置到命中角落方块。")
		.defaultValue(false)
		.build()
	);

	private final Setting<Boolean> onlyOneExtra = sgGeneral.add(new BoolSetting.Builder()
		.name("only-one-extra")
		.description("只对最靠近的那个角落放置。")
		.defaultValue(true)
		.visible(extraPlace::get)
		.build()
	);

	private final Setting<Boolean> extraPlaceII = sgGeneral.add(new BoolSetting.Builder()
		.name("extra-place-ii")
		.description("再在上述角落上方一格放置（需下面有方块或蜘蛛网）。")
		.defaultValue(false)
		.build()
	);

	private final Setting<Boolean> onlyOneExtraII = sgGeneral.add(new BoolSetting.Builder()
		.name("only-one-extra-ii")
		.description("只对最靠近的那个角落的上方放置。")
		.defaultValue(true)
		.visible(extraPlaceII::get)
		.build()
	);

	private final Setting<Boolean> renderPreview = sgRender.add(new BoolSetting.Builder()
		.name("render-preview")
		.description("渲染待放置位置预览。")
		.defaultValue(true)
		.build()
	);

	private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
		.name("side-color")
		.description("主放置侧面颜色。")
		.defaultValue(new SettingColor(150, 0, 150, 50))
		.visible(renderPreview::get)
		.build()
	);

	private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
		.name("line-color")
		.description("主放置线框颜色。")
		.defaultValue(new SettingColor(150, 0, 150))
		.visible(renderPreview::get)
		.build()
	);

	private final Setting<SettingColor> extraSideColor = sgRender.add(new ColorSetting.Builder()
		.name("extra-side-color")
		.description("Extra Place 侧面颜色。")
		.defaultValue(new SettingColor(0, 150, 150, 50))
		.visible(extraPlace::get)
		.build()
	);

	private final Setting<SettingColor> extraLineColor = sgRender.add(new ColorSetting.Builder()
		.name("extra-line-color")
		.description("Extra Place 线框颜色。")
		.defaultValue(new SettingColor(0, 150, 150))
		.visible(extraPlace::get)
		.build()
	);

	private final Setting<SettingColor> extraIIColor = sgRender.add(new ColorSetting.Builder()
		.name("extra-ii-color")
		.description("Extra Place II 预览颜色。")
		.defaultValue(new SettingColor(150, 150, 0, 50))
		.visible(extraPlaceII::get)
		.build()
	);

	private int timer;

	public SelfWeb() {
		super(Categories.Combat, "self-web", "在脚下及角落静默放置蜘蛛网，防止活塞推人。");
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		if (timer > 0) {
			timer--;
			return;
		}

		if (eatingPause.get() && mc.player.isUsingItem()) return;

		BlockPos foot = mc.player.getBlockPos();
		int placed = 0;

		// 1. Shrinked Hitbox for corner detection
		double eps = epsilon.get();
		Box box = mc.player.getBoundingBox();
		Box shrink = new Box(
			box.minX + eps, box.minY, box.minZ + eps,
			box.maxX - eps, box.maxY, box.maxZ - eps
		);

		// 2. 主放置：脚下
		if (mc.world.getBlockState(foot).isAir()) {
			previewAndPlace(foot, sideColor.get(), lineColor.get());
			placed++;
		}

		// 3. 检测最近角落
		BlockPos detected = null;
		cornerLoop:
		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				BlockPos corner = foot.add(dx, 0, dz);
				Box cbox = new Box(
					corner.getX(), corner.getY(), corner.getZ(),
					corner.getX() + 1, corner.getY() + 1, corner.getZ() + 1
				);
				if (shrink.intersects(cbox)) {
					detected = corner;
					break cornerLoop;
				}
			}
		}
		// 4. 兜底最近距离
		if (detected == null) {
			double best = Double.MAX_VALUE;
			Vec3d eye = mc.player.getEyePos();
			for (int dx = -1; dx <= 1; dx += 2) {
				for (int dz = -1; dz <= 1; dz += 2) {
					BlockPos c = foot.add(dx, 0, dz);
					if (!mc.world.getBlockState(c).isAir()) continue;
					double d = Vec3d.ofCenter(c).squaredDistanceTo(eye);
					if (d < best) {
						best = d;
						detected = c;
					}
				}
			}
		}

		// 5. Extra Place
		if (extraPlace.get() && placed < blocksPerTick.get() && detected != null) {
			if (onlyOneExtra.get()) {
				if (mc.world.getBlockState(detected).isAir()) {
					previewAndPlace(detected, extraSideColor.get(), extraLineColor.get());
					placed++;
				}
			} else {
				for (int dx = -1; dx <= 1 && placed < blocksPerTick.get(); dx += 2) {
					for (int dz = -1; dz <= 1 && placed < blocksPerTick.get(); dz += 2) {
						BlockPos c = foot.add(dx, 0, dz);
						if (mc.world.getBlockState(c).isAir()) {
							previewAndPlace(c, extraSideColor.get(), extraLineColor.get());
							placed++;
						}
					}
				}
			}
		}

		// 6. Extra Place II
		if (extraPlaceII.get() && placed < blocksPerTick.get() && detected != null) {
			if (onlyOneExtraII.get()) {
				BlockPos up = detected.up();
				if ((!mc.world.getBlockState(detected).isAir()
					|| mc.world.getBlockState(detected).getBlock() == Blocks.COBWEB)
					&& mc.world.getBlockState(up).isAir()) {
					previewAndPlace(up, extraIIColor.get(), extraIIColor.get());
					placed++;
				}
			} else {
				for (int dx = -1; dx <= 1 && placed < blocksPerTick.get(); dx += 2) {
					for (int dz = -1; dz <= 1 && placed < blocksPerTick.get(); dz += 2) {
						BlockPos c = foot.add(dx, 0, dz);
						BlockPos up = c.up();
						if ((!mc.world.getBlockState(c).isAir()
							|| mc.world.getBlockState(c).getBlock() == Blocks.COBWEB)
							&& mc.world.getBlockState(up).isAir()) {
							previewAndPlace(up, extraIIColor.get(), extraIIColor.get());
							placed++;
						}
					}
				}
			}
		}

		timer = delay.get();
	}

	private void previewAndPlace(BlockPos pos, SettingColor side, SettingColor line) {
		if (pos == null) return;
		// 预览
		if (renderPreview.get()) {
			RenderUtils.renderTickingBlock(pos, side, line, ShapeMode.Both, 0, delay.get(), true, true);
		}
		// 放置
		placeCobweb(pos);
	}

	private void placeCobweb(BlockPos pos) {
		if (mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB) return;
		FindItemResult slot = InvUtils.findInHotbar(i ->
			i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() == Blocks.COBWEB
		);
		if (!slot.found()) return;

		if (silentSwitch.get()) InvUtils.swap(slot.slot(), true);
		if (rotate.get()) placeWithLook(pos, slot);
		else BlockUtils.place(pos, slot, false, 0, true);
		if (silentSwitch.get()) InvUtils.swapBack();
	}

	private void placeWithLook(BlockPos pos, FindItemResult slot) {
		Vec3d center = Vec3d.ofCenter(pos);
		Vec3d eye    = mc.player.getEyePos();
		Vec3d diff   = center.subtract(eye);
		double d = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		float yaw   = (float)Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
		float pitch = (float)-Math.toDegrees(Math.atan2(diff.y, d));
		mc.getNetworkHandler().sendPacket(
			new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision)
		);
		BlockUtils.place(pos, slot, false, 0, true);
	}
}
