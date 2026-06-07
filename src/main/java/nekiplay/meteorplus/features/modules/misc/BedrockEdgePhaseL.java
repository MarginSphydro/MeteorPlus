package nekiplay.meteorplus.features.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BedrockEdgePhaseL extends Module {
	private final SettingGroup sgGeneral = settings.getDefaultGroup();
	private final Setting<Double> epsilon = sgGeneral.add(
		new DoubleSetting.Builder()
			.name("epsilon")
			.description("内缩量，绕过量化限制")
			.defaultValue(0.029)
			.min(0.0)
			.max(0.1)
			.build()
	);

	// 缓存边界
	private double minX = Double.NEGATIVE_INFINITY, maxX = Double.POSITIVE_INFINITY;
	private double minZ = Double.NEGATIVE_INFINITY, maxZ = Double.POSITIVE_INFINITY;
	// 避免递归发送包
	private boolean modifyingPacket = false;

	public BedrockEdgePhaseL() {
		super(Categories.Movement, "bedrock-edge-phase-Legacy2", "草泥马傻逼ash看你爹写的东西有多牛逼");
	}

	@EventHandler
	private void onTick(TickEvent.Post event) {
		PlayerEntity player = mc.player;
		if (player == null || mc.world == null) return;

		Vec3d pos = player.getEntityPos();
		double px = pos.x, pz = pos.z;
		double eps = epsilon.get();
		BlockPos center = new BlockPos((int)Math.floor(px), (int)Math.floor(pos.y), (int)Math.floor(pz));
		World world = mc.world;

		double newMinX = Double.NEGATIVE_INFINITY, newMaxX = Double.POSITIVE_INFINITY;
		double newMinZ = Double.NEGATIVE_INFINITY, newMaxZ = Double.POSITIVE_INFINITY;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos check = center.add(dx, 0, dz);
				if (world.getBlockState(check).getBlock() == Blocks.BEDROCK) {
					if (check.getX() > center.getX()) newMaxX = Math.min(newMaxX, check.getX() - eps);
					if (check.getX() < center.getX()) newMinX = Math.max(newMinX, check.getX() + 1 + eps);
					if (check.getZ() > center.getZ()) newMaxZ = Math.min(newMaxZ, check.getZ() - eps);
					if (check.getZ() < center.getZ()) newMinZ = Math.max(newMinZ, check.getZ() + 1 + eps);
				}
			}
		}

		minX = newMinX; maxX = newMaxX;
		minZ = newMinZ; maxZ = newMaxZ;
	}

	@EventHandler
	private void onPacketSend(PacketEvent.Send event) {
		if (modifyingPacket) return;
		if (!(event.packet instanceof PlayerMoveC2SPacket)) return;

		PlayerEntity player = mc.player;
		if (player == null) return;

		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		boolean onGround = player.isOnGround();

		double newX = x, newZ = z;
		if (x < minX) newX = minX;
		if (x > maxX) newX = maxX;
		if (z < minZ) newZ = minZ;
		if (z > maxZ) newZ = maxZ;

		if (newX != x || newZ != z) {
			event.cancel();
			ClientPlayNetworkHandler handler = mc.getNetworkHandler();
			if (handler != null) {
				modifyingPacket = true;
				handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(newX, y, newZ, onGround, player.horizontalCollision));
				modifyingPacket = false;
			}
			player.setPos(newX, y, newZ);
			player.setVelocity(0, player.getVelocity().y, 0);
		}
	}
}
