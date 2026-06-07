package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BedrockEdgePhaseLegacy extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();

	// 微小值，用于坐标内缩，防止接触床岩边界
	private final Setting<Double> epsilon = sgGeneral.add(
		new DoubleSetting.Builder()
			.name("epsilon")
			.description("内缩量，避免精确触碰床岩边界")
			.defaultValue(0.029)
			.min(0.0)
			.max(0.1)
			.build()
	);

	public BedrockEdgePhaseLegacy() {
		super(Categories.Movement, "bedrock-edge-phase-Legacy", "草泥马傻逼ash看你爹写的东西有多牛逼");
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		PlayerEntity player = mc.player;
		if (player == null) return;

		Vec3d pos = player.getEntityPos();
		double px = pos.x;
		double py = pos.y;
		double pz = pos.z;

		// 玩家脚下的区块中心整点坐标
		BlockPos center = new BlockPos(
			(int) Math.floor(px),
			(int) Math.floor(py),
			(int) Math.floor(pz)
		);
		World world = mc.world;
		double eps = epsilon.get();

		// 初始化允许坐标范围
		double minX = Double.NEGATIVE_INFINITY;
		double maxX = Double.POSITIVE_INFINITY;
		double minZ = Double.NEGATIVE_INFINITY;
		double maxZ = Double.POSITIVE_INFINITY;

		// 检测玩家下半身周围8个方块
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;

				BlockPos checkPos = center.add(dx, 0, dz);
				if (world.getBlockState(checkPos).getBlock() == Blocks.BEDROCK) {
					// 床岩在玩家X正方向
					if (checkPos.getX() > center.getX()) {
						maxX = Math.min(maxX, checkPos.getX() - eps);
					}
					// 床岩在玩家X负方向
					if (checkPos.getX() < center.getX()) {
						minX = Math.max(minX, checkPos.getX() + 1 + eps);
					}
					// 床岩在玩家Z正方向
					if (checkPos.getZ() > center.getZ()) {
						maxZ = Math.min(maxZ, checkPos.getZ() - eps);
					}
					// 床岩在玩家Z负方向
					if (checkPos.getZ() < center.getZ()) {
						minZ = Math.max(minZ, checkPos.getZ() + 1 + eps);
					}
				}
			}
		}

		double newX = px;
		double newZ = pz;

		// 阻止进入床岩内部，允许从内部回到边界
		if (px < minX) newX = minX;
		if (px > maxX) newX = maxX;
		if (pz < minZ) newZ = minZ;
		if (pz > maxZ) newZ = maxZ;

		// 如果位置被修改，则更新
		if (newX != px || newZ != pz) {
			player.setVelocity(0, player.getVelocity().y, 0);
			player.setPos(newX, py, newZ);
		}
	}
}
