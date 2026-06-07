package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BedrockEdgePhaseRewritten extends Module {
	private final SettingGroup sg = settings.getDefaultGroup();
	private final Setting<Double> epsilon = sg.add(new DoubleSetting.Builder()
		.name("epsilon")
		.description("内缩量，避免精确触碰床岩边界")
		.defaultValue(0.001)
		.min(0.0).max(0.1).build()
	);

	// 最新的 teleportId，用于瞬移确认
	private int lastTeleportId = 0;

	public BedrockEdgePhaseRewritten() {
		super(Categories.Movement, "bedrock-edge-phase-Rewritten", "Legacy 版：每 Tick 瞬移回弹，100% 绕过量化和非法移动检测");
	}

	// 监听服务器瞬移包，保存 teleportId
	@EventHandler
	private void onReceive(PacketEvent.Receive event) {
		if (event.packet instanceof PlayerPositionLookS2CPacket pkt) {
			lastTeleportId = pkt.teleportId();
		}
	}

	// 每帧检查并瞬移回弹
	@EventHandler
	private void onTick(TickEvent.Post event) {
		PlayerEntity p = mc.player;
		World w = mc.world;
		if (p == null || w == null) return;

		Vec3d pos = p.getEntityPos();
		double px = pos.x, py = pos.y, pz = pos.z, eps = epsilon.get();

		// 计算玩家脚下方块中心
		BlockPos c = new BlockPos((int)Math.floor(px), (int)Math.floor(py), (int)Math.floor(pz));

		double minX = Double.NEGATIVE_INFINITY, maxX = Double.POSITIVE_INFINITY;
		double minZ = Double.NEGATIVE_INFINITY, maxZ = Double.POSITIVE_INFINITY;

		// 扫描 8 个基岩方块，更新阈值
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos bp = c.add(dx, 0, dz);
				if (w.getBlockState(bp).getBlock() == Blocks.BEDROCK) {
					if (bp.getX() > c.getX()) maxX = Math.min(maxX, bp.getX() - eps);
					else if (bp.getX() < c.getX()) minX = Math.max(minX, bp.getX() + 1 + eps);
					if (bp.getZ() > c.getZ()) maxZ = Math.min(maxZ, bp.getZ() - eps);
					else if (bp.getZ() < c.getZ()) minZ = Math.max(minZ, bp.getZ() + 1 + eps);
				}
			}
		}

		double targetX = px, targetZ = pz;

		// 如果越界，直接用瞬移确认+setPos 回弹
		if (px < minX) targetX = minX;
		else if (px > maxX) targetX = maxX;
		if (pz < minZ) targetZ = minZ;
		else if (pz > maxZ) targetZ = maxZ;

		if (targetX != px || targetZ != pz) {
			// 1. 先发送瞬移确认包
			mc.getNetworkHandler().sendPacket(new TeleportConfirmC2SPacket(lastTeleportId));
			// 2. 本地瞬移回弹
			p.setVelocity(0, p.getVelocity().y, 0);
			p.setPos(targetX, py, targetZ);
		}
	}
}
